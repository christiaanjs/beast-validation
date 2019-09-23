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
	final public Input<OutFile> outputInput = new Input<>("out", "output directory for trace log with truth and mean estimates. Not produced if not specified");

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
			html = new PrintStream(svgdir.getPath()+"/coverage.html");
			html.println("<!doctype html>\n"+
					"<html>\n"+
					"<head><title>Coverage calculations</title></head>\n"+
					"<body>\n");
			html.println("<h2>Coverage calculations</h2>");
			html.println("<li>prior sample: " + logFileInput.get().getPath()+"</li>");
			html.println("<li>posterior samples: " + logAnalyserFileInput.get().getPath()+"</li>");
		}		
		
		if (truth.getTrace(0).length - skip != estimated.getTrace(0).length) {
			Log.warning("WARNING: traces are of different lengths: "
					+ (truth.getTrace(0).length - skip) + "!=" + estimated.getTrace(0).length);
		}
		
		NumberFormat formatter = new DecimalFormat("#0.00");
		NumberFormat formatter2 = new DecimalFormat("#0");
		
		Log.info(space + "coverage Mean ESS Min ESS");
		
		for (int i = 0; i < truth.getLabels().size(); i++) {
			String label = truth.getLabels().get(i);
			Double [] trueValues = truth.getTrace(label);
			Double [] lows = estimated.getTrace(label+".95%HPDlo");
			Double [] upps = estimated.getTrace(label+".95%HPDup");
			Double [] ess = estimated.getTrace(label+".ESS");
			if (lows == null || upps == null) {
				Log.warning("Skipping " + label);
			} else {
				int covered = 0;
				double minESS = Double.POSITIVE_INFINITY;
				double meanESS = 0;
				for (int j = 0; j < trueValues.length - skip; j++) {
					if (lows[j] <= trueValues[j + skip] && trueValues[j + skip] <= upps[j]) {
						covered++;
					} else {
						// System.out.println(lows[j] +"<=" + trueValues[j + skip] +"&&" + trueValues[j + skip] +" <=" + upps[j]);
					}
					minESS = Math.min(minESS, ess[j]);
					meanESS += ess[j];
				}
				meanESS /= (trueValues.length - skip);
				Log.info(label + (label.length() < space.length() ? space.substring(label.length()) : " ") + 
						formatter2.format(covered) + "\t   " + 
						formatter.format(meanESS) + "  " + formatter.format(minESS));
			}
		}

		if (outputInput.get() != null) {
			Log.warning("Writing to file " + outputInput.get().getPath());
        	PrintStream out = new PrintStream(outputInput.get());
        	out.print("sample\t");
        	for (int i = 0; i < truth.getLabels().size(); i++) {
    			String label = truth.getLabels().get(i);
        		out.print(label + "\t" + label + ".mean\t");
        	}
			int n = truth.getTrace(0).length - skip;

			

			//for (int k = 0; k < n; k++) {
            //				out.print(k+"\t");
	        	for (int i = 0; i < truth.getLabels().size(); i++) {
	    			String label = truth.getLabels().get(i);
	    			Double [] trueValues = truth.getTrace(label);
	    			Double [] estimates = estimated.getTrace(label+".mean");
	    			Double [] lows = estimated.getTrace(label+".95%HPDlo");
	    			Double [] upps = estimated.getTrace(label+".95%HPDup");
	        		// out.print(trueValues[skip + k] + "\t" + estimates[k] + "\t");

	        	
	        	
	        	
					PrintStream tsv = new PrintStream(svgdir.getPath() +"/" + label + ".tsv");
					tsv.println("truth\testimates\t95HPDlow\t95HPDup");
					for (int j = 0; j < estimates.length - skip; j++) {
						tsv.println(trueValues[j + skip] + "\t" + estimates[j] + "\t" + lows[j] + "\t" + upps[j]);
					}
					tsv.close();
	        	

				if (html != null) {
					PrintStream svg = new PrintStream(svgdir.getPath() +"/" + label + ".svg");
					svg.println("<svg class=\"chart\" width=\"1040\" height=\"760\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\">");
					
					// axes
					svg.println("<rect x='15' width=\"1000\" height=\"700\" style=\"fill:none;stroke-width:1;stroke:rgb(0,0,0)\"/>");
					


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
					for (int j = 0; j < estimates.length - skip; j++) {
						double y = lows[j];
						double h = upps[j] - lows[j];
						double x = trueValues[j + skip]; 
						if (lows[j] <= trueValues[j + skip] && trueValues[j + skip] <= upps[j]) {
							svg.println("  <rect x=\""+x+"\" y=\"" + y+ "\" width=\""+w+"\" height=\""+h+"\" style=\"fill:#57539a;stroke-width:"+ w/10+";stroke:#8b3d37\"/>");
							covered++;
						} else {
							svg.println("  <rect x=\""+x+"\" y=\"" + y+ "\" width=\""+w+"\" height=\""+h+"\" style=\"fill:#9a5753;stroke-width:"+ w/10+";stroke:#373d8b\"/>");
						}
					}
					for (int j = 0; j < estimates.length - skip; j++) {
						double y = estimates[j];
						double x = trueValues[j + skip] + w/2; 
						svg.println("<circle cx='"+x+"' cy='"+y+"' r=\""+w/3+"\" stroke=\"black\" stroke-width=\""+w/3+"\" fill=\"black\"/>");
					}
					svg.println("</g>");
//					svg.println("<text x='0' y='10'>" + max + "</text>");
//					svg.println("<text x='0' y='700'>0</text>");
//					svg.println("<text x='"+(10+500/binCount)+"' y='720'>" + 1 + "</text>");
//					for (int j = 10; j <= binCount; j+=10) {
//						int x = 10 + j*1000/binCount - 500/binCount;
//						int y = 720;
//						svg.println("<text x='"+x+"' y='"+y+"'>" + j + "</text>");
//					}
	
					svg.println("</svg>");
					svg.close();
					
					html.println("<h3>" + label + "</h3>");
					html.println("<p>Coverage: " + covered + "</p><p>");
					html.println("<img src=\"" + label + ".svg\">");
				}
			}
			
        		out.println();
			//}
			out.close();
		}
		
		if (html != null) {
			//html.println("Expected number of misses: " + 0.05 * binom.getNumberOfTrials());
			html.println("</body>\n</html>");
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
