package beast.experimenter;


import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Map;

import beast.app.beauti.BeautiDoc;
import beast.app.util.Application;
import beast.app.util.OutFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.Runnable;
import beast.core.util.Log;
import beast.math.Binomial;
import beast.util.LogAnalyser;

@Description("Calculate how many times entries in log file are covered in an estimated 95% HPD interval")
public class CoverageCalculator extends Runnable {
	final public Input<File> logFileInput = new Input<>("log", "log file containing actual values", Validate.REQUIRED);
	final public Input<Integer> skipLogLinesInput = new Input<>("skip", "numer of log file lines to skip", 1);
	final public Input<File> logAnalyserFileInput = new Input<>("logAnalyser", "file produced by loganalyser tool using the -oneline option, containing estimated values", Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out", "output directory for tsv files with truth and mean estimates. Not produced if not specified -- directory is also used to generate svg bargraphs and html report");
	final public Input<File> typesInput = new Input<>("typeFile", "if specified, the type file is a tab delimited file with first column containing entry names as they appear in the trace log file, and second column "
			+ "variable type, d for double, b for binary, c for categorical, for example:\n"
			+ "variable\ttype\n"
			+ "birthRate\td\n"
			+ "kappa\td\n"
			+ "hasGamma\tb\n"
			+ "modelIndicator\tc\n"
			+ "Items that are not specified are considered to be of type double");

	final static String space = "                                                ";
	NumberFormat formatter = new DecimalFormat("#0.00");
	NumberFormat formatter2 = new DecimalFormat("#0");

	Map<String, String> typeMap;
	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		typeMap = processTypes();
		
		LogAnalyser truth = new LogAnalyser(logFileInput.get().getAbsolutePath(), 0, true, false);
		LogAnalyser estimated = new LogAnalyser(logAnalyserFileInput.get().getAbsolutePath(), 0, true, false);
		int skip = skipLogLinesInput.get();

		PrintStream html = null;
		File svgdir = null;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			svgdir = outputInput.get();
			if (!svgdir.isDirectory()) {
				svgdir = svgdir.getParentFile();
			}
			Log.warning("Writing to file " + svgdir.getPath()+"/coverage.html");
			html = new PrintStream(svgdir.getPath()+"/coverage.html");
			html.println("<!doctype html>\n"+
					"<html>\n"+
					"<head><title>Coverage calculations</title></head>\n"+
					"<body>\n");
			html.println("<h2>Coverage calculations</h2>");
			html.println("<li>prior sample: " + logFileInput.get().getPath()+"</li>");
			int n = estimated.getTrace(0).length;
			int [] hpd = get95PercentBinomialHPD(n);
			html.println("<li>posterior samples: " + logAnalyserFileInput.get().getPath() + " with " + n + " runs so coverage should be from " + hpd[0] + " to " + hpd[1] +"</li>");
			html.println("<table>");
		}		
		
		if (truth.getTrace(0).length - skip != estimated.getTrace(0).length) {
			Log.warning("WARNING: traces are of different lengths: "
					+ (truth.getTrace(0).length - skip) + "!=" + estimated.getTrace(0).length);
		}
		
		
		Log.info(space + "coverage Mean ESS Min ESS");
		
		int [] coverage = new int[truth.getLabels().size()];
		int [] meanOver_ = new int[truth.getLabels().size()];
		double [] meanESS_ = new double[truth.getLabels().size()];
		double [] minESS_ = new double[truth.getLabels().size()];
		boolean [] invalidESSReported_ = new boolean[truth.getLabels().size()];
		
		calcStats(truth, estimated, skip, coverage, meanOver_, meanESS_, minESS_, invalidESSReported_);


		if (outputInput.get() != null) {
			formatter = new DecimalFormat("#0.0000");
			int k = 0;
        	for (int i = 0; i < truth.getLabels().size(); i++) {
    			String label = truth.getLabels().get(i);
    			try {
    				if (!(label.equals("prior") || label.equals("likelihood") || label.equals("posterior"))) {
    					output(i, k, label, truth, estimated, svgdir, skip, html,
    							coverage, meanOver_, meanESS_, minESS_, invalidESSReported_);
    					k++;
        			}
    			} catch (ArrayIndexOutOfBoundsException e) {
    				// we get here if some item in the true log is not available in the summary log
    				Log.err("Skipping " + label);
    			}
			}

			html.println("</table>\n" +
					"</p><p>* marked ESSs indicate one or more ESS estimates are invalid. Unmarked ESSs indicate all estimates are valid.</p>"
					+ "</body>\n</html>");
			html.close();
			
			try {
				Application.openUrl("file://" + svgdir.getPath()+"/coverage.html");
			} catch (IOException e) {
				e.printStackTrace();
				Log.warning("Output in " + svgdir.getPath()+"/coverage.html");
			}
		}

		Log.warning("Done!");	
	}

	private int [] get95PercentBinomialHPD(int n) {
		double [] binomial = new double[n+1];
		for (int i = 0; i <= n; i++) {
			binomial[n-i] = Binomial.logChoose(n, i);
			binomial[n-i] += Math.log(0.95) * (n-i) + Math.log(0.05) * i; 
		}
		double max = binomial[0];
		for (double d : binomial) {
			max = Math.max(max, d);
		}

		for (int i = 0; i <= n; i++) {
			binomial[i] = Math.exp(binomial[i] - max);
		}
		double sum = 0;
		for (double d : binomial) {
			sum += d;
		}
		for (int i = 0; i <= n; i++) {
			binomial[i] /= sum;
		}
		int lo = (int)(0.95 * n);
		int hi = lo;
		double cumProb = binomial[lo];
		while (cumProb < 0.95) {
			if (hi == n || binomial[lo-1]> binomial[hi+1]) {
				lo--;
				cumProb += binomial[lo];
			} else {
				hi++;
				cumProb += binomial[hi];
			}
		}
		int [] hpd = new int[]{lo,hi};
		return hpd;
	}

	private void calcStats(LogAnalyser truth, LogAnalyser estimated, int skip,
			int[] coverage, int[] meanOver_, double[] meanESS_, double[] minESS_, boolean[] invalidESSReported_) {
		for (int i = 0; i < truth.getLabels().size(); i++) {
			String label = truth.getLabels().get(i);
			try {
				Double [] trueValues = truth.getTrace(label);
				Double [] meanValues = estimated.getTrace(label+".mean");
				Double [] lows = estimated.getTrace(label+".95%HPDlo");
				Double [] upps = estimated.getTrace(label+".95%HPDup");
				Double [] ess = estimated.getTrace(label+".ESS");
				if (lows == null || upps == null) {
					Log.warning("Skipping " + label + " due to lack of upper/lower bound data");
				} else {
					int covered = 0, meanOver = 0;
					double minESS = Double.POSITIVE_INFINITY;
					double meanESS = 0;
					int ESScount = 0;
					boolean invalidESSReported = false;
					switch (getType(label)) {
					case "b" :
						// boolean trait, identified by labels starting with "has" or "use" or "is"
						for (int j = 0; j < trueValues.length - skip && j < meanValues.length; j++) {
							if (trueValues[j + skip] == 0) {
								if (meanValues[j] < 0.95) {
									covered++;
								}
							} else {
								if (meanValues[j] > 0.05) {
									covered++;
								}
							}
							if (!Double.isNaN(ess[j])) {
								minESS = Math.min(minESS, ess[j]);
								meanESS += ess[j];
								ESScount++;
							} else {
								if (!invalidESSReported) {
									Log.warning("Invalid ESS estimate encountered for " + label);
									invalidESSReported = true;
								} 
							}
						}
						break;
					case "d":
					case "c":
						// real valued trait
						for (int j = 0; j < trueValues.length - skip && j < meanValues.length; j++) {
							if (lows[j] <= trueValues[j + skip] && trueValues[j + skip] <= upps[j]) {
								covered++;
								// System.out.println(lows[j] +"<=" + trueValues[j + skip] +"&&" + trueValues[j + skip] +" <=" + upps[j]);
							}
							if (trueValues[j + skip] > meanValues[j]) {
								meanOver++;
							}
							if (!Double.isNaN(ess[j])) {
								minESS = Math.min(minESS, ess[j]);
								meanESS += ess[j];
								ESScount++;
							} else {
								if (!invalidESSReported) {
									Log.warning("Invalid ESS estimate encountered for " + label);
									invalidESSReported = true;
								} 
							}
						}
						break;
					default:
						throw new IllegalArgumentException("type should be b,c or d, not " + getType(label));
					}
					meanESS /= ESScount;
					meanESS_[i] = meanESS;
					minESS_[i] = minESS;
					coverage[i] = covered;
					meanOver_[i] = meanOver;
					invalidESSReported_[i] = invalidESSReported;
					Log.info(label + (label.length() < space.length() ? space.substring(label.length()) : " ") + 
							formatter2.format(covered) + "\t   " + 
							formatter.format(meanESS) + "  " + formatter.format(minESS));
				}
			} catch (ArrayIndexOutOfBoundsException e) {
				// we get here if some item in the true log is not available in the summary log
				Log.err("Skipping " + label);
			}
		}
	}

	private int output(int i, int k, String label, LogAnalyser truth, LogAnalyser estimated, File svgdir, int skip, PrintStream html,
			int [] coverage,
			int [] meanOver_,
			double [] meanESS_,
			double [] minESS_,
			boolean [] invalidESSReported_
			) throws IOException {
		Double [] trueValues = truth.getTrace(label);
		Double [] estimates = estimated.getTrace(label+".mean");
		Double [] lows = estimated.getTrace(label+".95%HPDlo");
		Double [] upps = estimated.getTrace(label+".95%HPDup");
		// out.print(trueValues[skip + k] + "\t" + estimates[k] + "\t");

	
	
	
		Log.warning("Writing to file " + svgdir.getPath()+"/" + label + ".tsv");
		PrintStream tsv = new PrintStream(svgdir.getPath() +"/" + label + ".tsv");
		tsv.println("truth\testimates\t95HPDlow\t95HPDup");
		for (int j = 0; j < estimates.length - skip; j++) {
			tsv.println(trueValues[j + skip] + "\t" + estimates[j] + "\t" + lows[j] + "\t" + upps[j]);
		}
		tsv.close();
	

		Log.warning("Writing to file " + svgdir.getPath()+"/" + label + ".svg");
		PrintStream svg = new PrintStream(svgdir.getPath() +"/" + label + ".svg");
		svg.println("<svg class=\"chart\" width=\"1080\" height=\"760\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\">");
		// define clip path for graph
		svg.println("<defs>");
		svg.println("<clipPath id=\"cut-off-graph\">");
		svg.println("<rect x='15' width=\"1000\" height=\"700\" />");
		svg.println("</clipPath>");
		svg.println("</defs>");
		
		// axes
		svg.println("<rect x='15' width=\"1000\" height=\"700\" style=\"fill:none;stroke-width:1;stroke:rgb(0,0,0)\"/>");
		
		// keep everything sticking out of graph hidden
		svg.println("<g clip-path=\"url(#cut-off-graph)\">");

		double min = estimates[0];
		double max = estimates[0];
		double minx = trueValues[skip];
		double maxx = trueValues[skip];
		for (int j = 0; j < estimates.length - skip; j++) {
			min = Math.min(min, lows[j]);
			max = Math.max(max, upps[j]);
			minx = Math.min(minx, trueValues[j + skip]);
			maxx = Math.max(maxx, trueValues[j + skip]);
		}
		double range = max - min;
		double rangex = maxx - minx;
		double w = rangex / 100.0;
		
		
		double dx = 990 / rangex;
		double dy= 700 / range;

		
		
		
		switch (getType(label)) {
		case "b" :
		{
			svg.println("<g transform=\"translate(0,700) scale(1,-1)\">");
			
			// boolean trait, identified by labels starting with "has" or "use" or "is"
			int [] bins = new int[20];
			for (int j = 0; j < trueValues.length - skip; j++) {
				bins[(int)((estimates[j] + 0.5/bins.length) * (bins.length - 1))]++;
			}
			int y = 0;
			for (int j = 0; j < bins.length; j++) {
				double x = 15+j * 1000 / bins.length;
				double h = 700.0 * bins[j] / estimates.length;
				String fill = "#5099ff";
				if (trueValues[j + skip] == 0) {
					if (j < 19) {
						fill = "#5099ff";
					} else {
						fill = "#fa5753";
					}
				} else {
					if (j > 1) {
						fill = "#5099ff";
					} else {
						fill = "#fa5753";
					}
				}
				svg.println("  <rect x=\""+x+"\" y=\"" + y+ "\" width=\""+(1000/bins.length)+"\" height=\""+h+"\" style=\"fill:" + fill + ";stroke-width:1;stroke:#8b3d37;opacity:0.5\"/>");
			}
			svg.println("</g>");
			svg.println("</g>");
			svg.println("<text x='0' y='0' transform='rotate(90 0 0) translate(0,-1025)' style='font-size:46px'>1.0</text>");
			svg.println("<text x='0' y='0' transform='rotate(90 0 0) translate(640,-1025)' style='font-size:46px'>0.0</text>");
			svg.println("<text x='10' y='735' style='font-size:46px'>0.0</text>");
			svg.println("<text x='950' y='735' style='font-size:46px'>1.0</text>");
		}
			break;
		case "c":
		case "d":
		{
			// permissable area
			svg.println("<g transform=\"translate(" + (15-minx*dx) + "," + (700+min*dy) +") scale("+dx+",-"+dy+")\">");

			// x == y line
			svg.println("<line x1='"+minx+"' y1='"+minx+"' x2=\""+maxx+"\" y2=\""+maxx+"\" style=\"fill:none;stroke-width:"+w/3+";stroke:rgb(0,0,0)\"/>");

			for (int j = 0; j < estimates.length; j++) {
				double y = lows[j];
				double h = upps[j] - lows[j];
				double x = trueValues[j + skip]; 
				if (lows[j] <= trueValues[j + skip] && trueValues[j + skip] <= upps[j]) {
					svg.println("  <rect x=\""+x+"\" y=\"" + y+ "\" width=\""+w+"\" height=\""+h+"\" style=\"fill:#5099ff;stroke-width:"+ w/10+";stroke:#8b3d37;opacity:0.5\"/>");
				} else {
					svg.println("  <rect x=\""+x+"\" y=\"" + y+ "\" width=\""+w+"\" height=\""+h+"\" style=\"fill:#fa5753;stroke-width:"+ w/10+";stroke:#373d8b;opacity:0.85\"/>");
				}
			}
			for (int j = 0; j < estimates.length; j++) {
				double y = estimates[j];
				double x = trueValues[j + skip] + w/2; 
				svg.println("<circle cx='"+x+"' cy='"+y+"' r=\""+w/3+"\" stroke=\"black\" stroke-width=\""+w/3+"\" fill=\"black\"/>");
			}
			svg.println("</g>");
			svg.println("</g>");
			svg.println("<text x='0' y='0' transform='rotate(90 0 0) translate(0,-1025)' style='font-size:46px'>" + formatter.format(max) + "</text>");
			svg.println("<text x='0' y='0' transform='rotate(90 0 0) translate(600,-1025)' style='font-size:46px'>" + formatter.format(min) + "</text>");
			svg.println("<text x='10' y='735' style='font-size:46px'>" + formatter.format(minx) + "</text>");
			svg.println("<text x='870' y='735' style='font-size:46px'>" + formatter.format(maxx) + "</text>");
			break;
		}
		default:
			// should never get here
		}
		svg.println("</svg>");
		svg.close();
		
		if (k % 4 == 0) {
			html.println("<tr>");						
		}
		html.println("<td>");
		html.println("<h3>" + label + "</h3>");
		html.println("<p>Coverage: " + coverage[i] + 
				" Mean: "  + meanOver_[i] + 
				" ESS (mean/min): " + formatter2.format(meanESS_[i]) + 
				"/" + formatter2.format(minESS_[i]) + (invalidESSReported_[i] ? "*" : "")
				+ "</p><p>");
		html.println("<img width=\"350px\" src=\"" + label + ".svg\">");
		html.println("</td>");
		if ((k+1) % 4 == 0) {
			html.println("</tr>");
		}
		k++;
		return k;
	}

	private String getType(String label) {
		if (typeMap.containsKey(label)) {
			return typeMap.get(label);
		}
		// default to "double"
		return "d";
	}

	private Map<String, String> processTypes() throws IOException {
		Map<String,String> typeMap = new HashMap<>();
		if (typesInput.get() != null && !typesInput.get().equals("[[None]]")) {
			String types = BeautiDoc.load(typesInput.get());
			String [] strs = types.split("\n");
			for (String str : strs) {
				String [] strs2 = str.split("\t");
				if (strs2.length == 2) {
					typeMap.put(strs2[0], strs2[1]);
				}
			}
		}
		return typeMap;
	}

	public static void main(String[] args) throws Exception {
		new Application(new CoverageCalculator(), "Coverage Calculator", args);
	}
}
