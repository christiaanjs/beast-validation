package beast.experimenter;


import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import beast.app.util.Application;
import beast.app.util.OutFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.Runnable;
import beast.core.util.Log;
import beast.util.LogAnalyser;

@Description("Calculate how many times entries in log file are covered in an estimated 95% HPD interval")
public class CoverageCalculator extends Runnable {
	final public Input<File> logFileInput = new Input<>("log", "log file containing actual values", Validate.REQUIRED);
	final public Input<Integer> skipLogLinesInput = new Input<>("skip", "numer of log file lines to skip", 1);
	final public Input<File> logAnalyserFileInput = new Input<>("logAnalyser", "file produced by loganalyser tool using the -oneline option, containing estimated values", Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out", "output directory for tsv files with truth and mean estimates. Not produced if not specified -- directory is also used to generate svg bargraphs and html report");
	final public Input<Boolean> recogniseBooleansInput = new Input<>("recogniseBooleans", "if true, entries starting with \"has\", \"use\" and \"is\" are treated as booleans when calculaing 95%HPD coverage", true);

	final static String space = "                                                ";
	
	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
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
			html.println("<li>posterior samples: " + logAnalyserFileInput.get().getPath()+"</li>");
			html.println("<table>");
		}		
		
		if (truth.getTrace(0).length - skip != estimated.getTrace(0).length) {
			Log.warning("WARNING: traces are of different lengths: "
					+ (truth.getTrace(0).length - skip) + "!=" + estimated.getTrace(0).length);
		}
		
		NumberFormat formatter = new DecimalFormat("#0.00");
		NumberFormat formatter2 = new DecimalFormat("#0");
		
		Log.info(space + "coverage Mean ESS Min ESS");
		
		int [] coverage = new int[truth.getLabels().size()];
		double [] meanESS_ = new double[truth.getLabels().size()];
		double [] minESS_ = new double[truth.getLabels().size()];
		
		for (int i = 0; i < truth.getLabels().size(); i++) {
			String label = truth.getLabels().get(i);
			try {
				Double [] trueValues = truth.getTrace(label);
				Double [] meanValues = estimated.getTrace(label+".mean");
				Double [] lows = estimated.getTrace(label+".95%HPDlo");
				Double [] upps = estimated.getTrace(label+".95%HPDup");
				Double [] ess = estimated.getTrace(label+".ESS");
				if (lows == null || upps == null) {
					Log.warning("Skipping " + label);
				} else {
					int covered = 0;
					double minESS = Double.POSITIVE_INFINITY;
					double meanESS = 0;
					if ((label.startsWith("has") || label.startsWith("use") || label.startsWith("is")) && recogniseBooleansInput.get()) {
						// boolean trait, identified by labels starting with "has" or "use" or "is"
						for (int j = 0; j < trueValues.length - skip; j++) {
							if (trueValues[j + skip] == 0) {
								if (meanValues[j] < 0.95) {
									covered++;
								}
							} else {
								if (meanValues[j] > 0.05) {
									covered++;
								}
							}
							minESS = Math.min(minESS, ess[j]);
							meanESS += ess[j];
						}						
					} else {
						// real valued trait
						for (int j = 0; j < trueValues.length - skip; j++) {
							if (lows[j] <= trueValues[j + skip] && trueValues[j + skip] <= upps[j]) {
								covered++;
								// System.out.println(lows[j] +"<=" + trueValues[j + skip] +"&&" + trueValues[j + skip] +" <=" + upps[j]);
							}
							minESS = Math.min(minESS, ess[j]);
							meanESS += ess[j];
						}
					}
					meanESS /= (trueValues.length - skip);
					meanESS_[i] = meanESS;
					minESS_[i] = minESS;
					coverage[i] = covered;
					Log.info(label + (label.length() < space.length() ? space.substring(label.length()) : " ") + 
							formatter2.format(covered) + "\t   " + 
							formatter.format(meanESS) + "  " + formatter.format(minESS));
				}
			} catch (ArrayIndexOutOfBoundsException e) {
				// we get here if some item in the true log is not available in the summary log
				Log.err("Skipping " + label);
			}
		}

		if (outputInput.get() != null) {
			formatter = new DecimalFormat("#0.0000");
			int k = 0;
        	for (int i = 0; i < truth.getLabels().size(); i++) {
    			String label = truth.getLabels().get(i);
    			try {
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
	
					
					
					// permissable area
					svg.println("<g transform=\"translate(" + (15-minx*dx) + "," + (700+min*dy) +") scale("+dx+",-"+dy+")\">");
	
					// x == y line
					svg.println("<line x1='"+minx+"' y1='"+minx+"' x2=\""+maxx+"\" y2=\""+maxx+"\" style=\"fill:none;stroke-width:"+w/3+";stroke:rgb(0,0,0)\"/>");
					
					int covered = 0;
					for (int j = 0; j < estimates.length; j++) {
						double y = lows[j];
						double h = upps[j] - lows[j];
						double x = trueValues[j + skip]; 
						if (lows[j] <= trueValues[j + skip] && trueValues[j + skip] <= upps[j]) {
							svg.println("  <rect x=\""+x+"\" y=\"" + y+ "\" width=\""+w+"\" height=\""+h+"\" style=\"fill:#5099ff;stroke-width:"+ w/10+";stroke:#8b3d37;opacity:0.5\"/>");
							covered++;
						} else {
							svg.println("  <rect x=\""+x+"\" y=\"" + y+ "\" width=\""+w+"\" height=\""+h+"\" style=\"fill:#fa5753;stroke-width:"+ w/10+";stroke:#373d8b;opacity:0.85\"/>");
						}
					}
					for (int j = 0; j < estimates.length; j++) {
						double y = estimates[j];
						double x = trueValues[j + skip] + w/2; 
						svg.println("<circle cx='"+x+"' cy='"+y+"' r=\""+w/3+"\" stroke=\"black\" stroke-width=\""+w/3+"\" fill=\"black\"/>");
					}
					svg.println("</g>\n</g>");
					svg.println("<text x='1020' y='15'>" + formatter.format(max) + "</text>");
					svg.println("<text x='1020' y='700'>" + formatter.format(min) + "</text>");
					svg.println("<text x='10' y='712'>" + formatter.format(minx) + "</text>");
					svg.println("<text x='980' y='712'>" + formatter.format(maxx) + "</text>");
					svg.println("</svg>");
					svg.close();
					
					if (k % 4 == 0) {
						html.println("<tr>");						
					}
					html.println("<td>");
					html.println("<h3>" + label + "</h3>");
					html.println("<p>Coverage: " + coverage[i] + 
							" mean ESS: " + formatter2.format(meanESS_[i]) + 
							" minESS: " + formatter2.format(minESS_[i]) + "</p><p>");
					html.println("<img width=\"350px\" src=\"" + label + ".svg\">");
					html.println("</td>");
					if ((k+1) % 4 == 0) {
						html.println("</tr>");
					}
					k++;
    			} catch (ArrayIndexOutOfBoundsException e) {
    				// we get here if some item in the true log is not available in the summary log
    				Log.err("Skipping " + label);
    			}
			}

			html.println("</table>\n</body>\n</html>");
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


	public static void main(String[] args) throws Exception {
		new Application(new CoverageCalculator(), "Coverage Calculator", args);
	}
}
