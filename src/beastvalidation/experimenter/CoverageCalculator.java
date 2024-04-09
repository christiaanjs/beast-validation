package beastvalidation.experimenter;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import beastfx.app.inputeditor.BeautiDoc;
import beastfx.app.tools.Application;
import beastfx.app.util.OutFile;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Input.Validate;
import beast.base.inference.Runnable;
import beast.base.core.Log;
import beast.base.util.Binomial;
import beastfx.app.tools.LogAnalyser;

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
	final public Input<Boolean> guessFileOrderInput = new Input<>("guessFileOrder", "guess order of entries in logAnalyser file based on file name, otherwise assume order is the same as in log-file with actual values", true);
	final public Input<Boolean> showESSInput = new Input<>("showESS", "show information about ESSs", true);
	final public Input<Boolean> showRhoInput = new Input<>("showRho", "show information about correlation (rho)", false);
	final public Input<Boolean> showMeanInput = new Input<>("showMean", "show how many means are below the true value", true);
	final public Input<String> excludeInput = new Input<>("exclude", "comma separated list of entries to exclude from the analysis", "");
			
	
	final static String space = "                                                ";
	NumberFormat formatter = new DecimalFormat("#0.00");
	NumberFormat formatter2 = new DecimalFormat("#0");
	Set<String> exclude;

	Map<String, String> typeMap;
	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		exclude = new HashSet<>();
		if (excludeInput.get() != null && excludeInput.get().trim().length() > 0) {
			for (String s : excludeInput.get().split(",")) {
				exclude.add(s.trim());
			}
		}
		exclude.add("posterior");
		exclude.add("prior");
		exclude.add("likelihood");

		typeMap = processTypes();
		
		LogAnalyser truth = new LogAnalyser(logFileInput.get().getAbsolutePath(), 0, true, false);
		LogAnalyser estimated = new LogAnalyser(logAnalyserFileInput.get().getAbsolutePath(), 0, true, false);
		int skip = skipLogLinesInput.get();
		int n = estimated.getTrace(0).length;
		int [] hpd = get95PercentBinomialHPD(n);

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
			html.println("<li>posterior samples: " + logAnalyserFileInput.get().getPath() + " with " + n + " runs so coverage should be from " + hpd[0] + " to " + hpd[1] +"</li>");
			html.println("<table>");
		}		
		
		if (truth.getTrace(0).length - skip != estimated.getTrace(0).length) {
			Log.warning("WARNING: traces are of different lengths: "
					+ (truth.getTrace(0).length - skip) + "!=" + estimated.getTrace(0).length);
		}
		
		
		Log.info("posterior samples: " + logAnalyserFileInput.get().getPath() + " with " + n + " runs so coverage should be from " + hpd[0] + " to " + hpd[1]);
		Log.info(space + "coverage  Mean ESS\tMin ESS\tmissmatches");

		
		int [] coverage = new int[truth.getLabels().size()];
		int [] meanOver_ = new int[truth.getLabels().size()];
		double [] meanESS_ = new double[truth.getLabels().size()];
		double [] minESS_ = new double[truth.getLabels().size()];
		boolean [] invalidESSReported_ = new boolean[truth.getLabels().size()];
		String [] covered_ = new String[truth.getLabels().size()];
		
		int [] map = guessFileOrder(estimated, skip);

		calcStats(truth, estimated, coverage, meanOver_, meanESS_, minESS_, invalidESSReported_, map, covered_, hpd);


		if (outputInput.get() != null) {
			formatter = new DecimalFormat("#0.0000");
			int k = 0;
        	for (int i = 0; i < truth.getLabels().size(); i++) {
    			String label = truth.getLabels().get(i);
    			try {
    				if (!(exclude.contains(label) || 
    						Double.isNaN(truth.getTrace(i+1)[0]) || Double.isNaN(meanESS_[i]))) {
    					output(i, k, label, truth, estimated, svgdir, html,
    							coverage, meanOver_, meanESS_, minESS_, invalidESSReported_,
    							map);
    					k++;
        			}
    			} catch (ArrayIndexOutOfBoundsException e) {
    				// we get here if some item in the true log is not available in the summary log
    				Log.err("Skipping " + label);
    			}
			}

			html.println("</table>\n" +
					(showESSInput.get()?
					"</p><p>* marked ESSs indicate one or more ESS estimates are invalid. Unmarked ESSs indicate all estimates are valid.</p>":"")
					+ "<p>working directory: " + System.getProperty("user.dir")+ "</p>"
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

	private void calcStats(LogAnalyser truth, LogAnalyser estimated,
			int[] coverage, int[] meanOver_, double[] meanESS_, double[] minESS_, boolean[] invalidESSReported_,
			int [] map, String[]covered_, int[] hpd) throws IOException {
		
		for (int i = 0; i < truth.getLabels().size(); i++) {
			String label = truth.getLabels().get(i);
			try {
				Double [] trueValues = truth.getTrace(label);
				Double [] meanValues = estimated.getTrace(label+".mean");
				Double [] lows = estimated.getTrace(label+".95%HPDlo");
				Double [] upps = estimated.getTrace(label+".95%HPDup");
				Double [] ess = estimated.getTrace(label+".ESS");
				String coveredString = "", missmatchIDS = "";
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
						for (int j = 0; j < meanValues.length && map[j] < trueValues.length; j++) {
							if (trueValues[map[j]] == 0) {
								if (meanValues[j] < 0.95) {
									covered++;
									coveredString += ".";
								} else {
									coveredString += "x";
									missmatchIDS += map[j] + " ";
								}
							} else {
								if (meanValues[j] > 0.05) {
									covered++;
									coveredString += ".";
								} else {
									coveredString += "x";
									missmatchIDS += map[j] + " ";
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
						for (int j = 0; j < meanValues.length && map[j] < trueValues.length; j++) {
							if (lows[j] <= trueValues[map[j]] && trueValues[map[j]] <= upps[j]) {
								covered++;
								// System.out.println(lows[j] +"<=" + trueValues[j + skip] +"&&" + trueValues[j + skip] +" <=" + upps[j]);
								coveredString += ".";
							} else {
								coveredString += "x";
								missmatchIDS += map[j] + " ";
							}
							if (trueValues[map[j]] > meanValues[j]) {
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
					covered_[i] = coveredString;
					invalidESSReported_[i] = invalidESSReported;
					Log.info(label + (label.length() < space.length() ? space.substring(label.length()) : " ") + 
							formatter2.format(covered) + (covered < hpd[0] || covered > hpd[1] ? "*":"") + "\t   " + 
							formatter.format(meanESS) + "\t" + formatter.format(minESS) + "\t" + coveredString + " " + missmatchIDS);
				}
			} catch (ArrayIndexOutOfBoundsException e) {
				// we get here if some item in the true log is not available in the summary log
				Log.err("Skipping " + label);
			}
		}
	}

	/** maps file names to entries in true-values based on numbering in file name **/
	private int[] guessFileOrder(LogAnalyser estimated, int skip) throws IOException {
		int [] map = new int[estimated.getTrace(0).length];
		
		if (guessFileOrderInput.get() && estimated.getLabels().get(0).equals("filename")) {
			
        	Log.warning("Guessing file order -- if this fails missarably, try setting guessFileOrder to false");
	        BufferedReader fin = new BufferedReader(new FileReader(logAnalyserFileInput.get().getAbsolutePath()));
	        fin.readLine();
	        int max = 0;
			for (int i = 0; i < map.length; i++) {
	            String [] str = fin.readLine().split("\t");
	            String name = str[1];
	            if (name.lastIndexOf(".") > 0) {
	            	name = name.substring(0, name.lastIndexOf("."));
	            }
	            int j = name.length()-1;
	            while (j >= 0 && Character.isDigit(name.charAt(j))) {
	            	j--;
	            }
	            map[i] = Integer.parseInt(name.substring(j+1));
	            max = Math.max(map[i], max);
	        }
	        fin.close();
	        
	        // check for duplicates
	        boolean [] done = new boolean[max+1];
	        for (int d : map) {
	        	if (done[d]) {
	        		Log.warning("Duplicate file number found -- aborting file order guessing and assume log and logAnalyser file are in same order");
	    			for (int i = 0; i < map.length; i++) {
	    				map[i] = i +  skip;
	    			}
	    			return map;
	        	}
	        }
	        // try to establish whether started at 0 or 1
	        boolean hasZero = false;
	        for (int d : map) {
	        	if (d == 0) {
	        		hasZero = true;
	        		break;
	        	}
	        }
	        if (!hasZero) {
	        	Log.warning("Assume first file starts at number 1");
				for (int i = 0; i < map.length; i++) {
					map[i]--;
				}
	        }

	        // adjust for skip
			for (int i = 0; i < map.length; i++) {
				map[i] +=  skip;
			}
		} else {
			for (int i = 0; i < map.length; i++) {
				map[i] = i +  skip;
			}
		}
		return map;
	}

	private int output(int i, int k, String label, LogAnalyser truth, LogAnalyser estimated, File svgdir, PrintStream html,
			int [] coverage,
			int [] meanOver_,
			double [] meanESS_,
			double [] minESS_,
			boolean [] invalidESSReported_,
			int [] map
			) throws IOException {
		Double [] trueValues = truth.getTrace(label);
		Double [] estimates = estimated.getTrace(label+".mean");
		Double [] lows = estimated.getTrace(label+".95%HPDlo");
		Double [] upps = estimated.getTrace(label+".95%HPDup");
		// out.print(trueValues[skip + k] + "\t" + estimates[k] + "\t");

	
	
		String cleanLabel = label.replaceAll(":", "");
		Log.warning("Writing to file " + svgdir.getPath()+"/" + cleanLabel + ".tsv");
		PrintStream tsv = new PrintStream(svgdir.getPath() +"/" + cleanLabel + ".tsv");
		tsv.println("truth\testimates\t95HPDlow\t95HPDup");
		for (int j = 0; j < estimates.length; j++) {
			tsv.println(trueValues[map[j]] + "\t" + estimates[j] + "\t" + lows[j] + "\t" + upps[j]);
		}
		tsv.close();
	

		Log.warning("Writing to file " + svgdir.getPath()+"/" + cleanLabel + ".svg");
		PrintStream svg = new PrintStream(svgdir.getPath() +"/" + cleanLabel + ".svg");
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

		double minx = trueValues[map[0]];
		double maxx = trueValues[map[0]];
		for (int j = 0; j < estimates.length; j++) {
			minx = Math.min(minx, trueValues[map[j]]);
			maxx = Math.max(maxx, trueValues[map[j]]);
		}
		double min = estimates[0];
		double max = estimates[0];
		for (String label0 : truth.getLabels()) {
			if (matches(label0, label)) {
				min = Math.min(min(estimated, label0), min);
				max = Math.max(max(estimated, label0), max);
			}
		}
		min = rounddown(min, max-min);
		max = roundup(max, max-min);
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
			for (int j = 0; j < estimates.length && map[j] < trueValues.length; j++) {
				bins[(int)((estimates[j] + 0.5/bins.length) * (bins.length - 1))]++;
			}
			int y = 0;
			for (int j = 0; j < bins.length; j++) {
				double x = 15+j * 1000 / bins.length;
				double h = 700.0 * bins[j] / estimates.length;
				String fill = "#5099ff";
				if (trueValues[map[j]] == 0) {
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

			// regression line
			if (showRhoInput.get()) {
				drawRegressionLine(svg, trueValues, estimates, map, minx, maxx, w);
			}
			
			for (int j = 0; j < estimates.length; j++) {
				double y = lows[j];
				double h = upps[j] - lows[j];
				double x = trueValues[map[j]]; 
				if (lows[j] <= trueValues[map[j]] && trueValues[map[j]] <= upps[j]) {
					svg.println("  <rect x=\""+x+"\" y=\"" + y+ "\" width=\""+w+"\" height=\""+h+"\" style=\"fill:#5099ff;stroke-width:"+ w/10+";stroke:#8b3d37;opacity:0.5\"/>");
				} else {
					svg.println("  <rect x=\""+x+"\" y=\"" + y+ "\" width=\""+w+"\" height=\""+h+"\" style=\"fill:#fa5753;stroke-width:"+ w/10+";stroke:#373d8b;opacity:0.85\"/>");
				}
			}
			for (int j = 0; j < estimates.length; j++) {
				double y = estimates[j];
				double x = trueValues[map[j]] + w/2; 
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
				(showMeanInput.get()? " Mean: "  + meanOver_[i]: "") + 
				(showESSInput.get()?
				" ESS (mean/min): " + formatter2.format(meanESS_[i]) + 
				"/" + formatter2.format(minESS_[i]) + (invalidESSReported_[i] ? "*" : ""):"")
				+ (showRhoInput.get()?
				", y = " + corr(trueValues, estimates, map) : "") + ")" 
				+ "</p><p>");
		html.println("<img width=\"350px\" src=\"" + cleanLabel + ".svg\">");
		html.println("</td>");
		if ((k+1) % 4 == 0) {
			html.println("</tr>");
		}
		k++;
		return k;
	}

	
	private void drawRegressionLine(PrintStream svg, Double[] trueValues, Double[] estimates, int[] map, double minx, double maxx, double w) {
		int n = estimates.length;
		double [] x = new double[n];
		double [] y = new double[n];
		for (int i = 0; i < n; i++) {
			x[i] = trueValues[map[i]];
			y[i] = estimates[i];
		}
		
		Regression r2 = new Regression(x, y);
		double intercept = r2.getIntercept();
		double gradient = r2.getGradient();
		double y1 = intercept + minx * gradient;
		double y2 = intercept + maxx * gradient;
		
		svg.println("<line x1='"+minx+"' y1='"+y1+"' x2=\""+maxx+"\" y2=\""+y2+"\" style=\"fill:none;stroke-width:"+w/3+";stroke:#c0c0c0\"/>");
		
	}

	private String corr(Double[] trueValues, Double[] estimates, int[] map) {
		int n = estimates.length;
		double [] x = new double[n];
		double [] y = new double[n];
		for (int i = 0; i < n; i++) {
			x[i] = trueValues[map[i]];
			y[i] = estimates[i];
		}
		
		Regression r2 = new Regression(x, y);
		String str = r2.toString();
		return " " + str;
	}

	private double max(LogAnalyser estimated, String label0) {
		try {
			Double [] upps = estimated.getTrace(label0+".95%HPDup");
			double max = upps[0];
			for (double d : upps) {
				max = Math.max(max, d);
			}		
			return max;
		} catch (ArrayIndexOutOfBoundsException e) {
			return Double.NEGATIVE_INFINITY;
		}
	}

	private double min(LogAnalyser estimated, String label0) {
		try {
			Double [] lows = estimated.getTrace(label0+".95%HPDlo");
			double min = lows[0];
			for (double d : lows) {
				min = Math.min(min, d);
			}
			return min;
		} catch (ArrayIndexOutOfBoundsException e) {
			return Double.POSITIVE_INFINITY;
		}
	}

	private boolean matches(String label0, String label) {
		if (label0.equals(label)) {
			return true;
		}
		if (label.matches(".*\\.[0-9]+$") && label0.matches(".*\\.[0-9]+$")) {
			String str = label.substring(0, label.lastIndexOf("."));
			String str0 = label0.substring(0, label0.lastIndexOf("."));
			if (str0.equals(str)) {
				return true;
			}
		}
		return false;
	}

	private double roundup(double max, double range) {
		if (range > 10) {
			return max;
		}
		if (range > 1) {
			max = ((int)(max+1));
		} else if (range > 0.1) {
			max = ((int)(max*10+1))/10.0;
		} else {
			max = ((int)(max*100+1))/100.0;
		}
		return max;
	}

	private double rounddown(double min, double range) {
		if (range > 10) {
			return min;
		}
		if (range > 1) {
			min = ((int)(min-1));
		} else if (range > 0.1) {
			min = ((int)(min*10-1))/10.0;
		} else {
			min = ((int)(min*100-1))/100.0;
		}
		return min;
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
