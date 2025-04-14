package beastvalidation.experimenter;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.math.MathException;
import org.apache.commons.math.distribution.BinomialDistribution;
import org.apache.commons.math.distribution.BinomialDistributionImpl;

import beastfx.app.tools.Application;
import beast.base.core.Citation;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.inference.Runnable;
import beast.base.util.Randomizer;
import beast.base.core.Input.Validate;
import beast.base.core.Log;
import beastfx.app.tools.LogAnalyser;
import beastfx.app.util.OutFile;

@Description(value="Validating  Bayesian  Inference Algorithms  with  Simulation-Based Calibration")
@Citation(value="Sean Talts, Michael Betancourt, Daniel Simpson, Aki Vehtari, Andrew Gelman, Validating  Bayesian  Inference Algorithms  with  Simulation-Based Calibration, 2018, arXiv:1804.06788v1", DOI="arXiv:1804.06788v1")
public class SBCAnalyser extends Runnable {
	final public Input<File> logFileInput = new Input<>("log", "log file containing actual values", Validate.REQUIRED);
	final public Input<Integer> skipLogLinesInput = new Input<>("skip", "numer of log file lines to skip", 1);
	final public Input<File> logAnalyserFileInput = new Input<>("logAnalyser", "file produced by logcombiner, combining the trace log files associated with entries in the 'log' file", Validate.REQUIRED);
	final public Input<Integer> binCountInput = new Input<>("bins", "number of bins to represent prior distribution. "
			+ "If not specified (or not positive) use number of samples from posterior + 1 (L+1 in the paper)", -1);

	final public Input<OutFile> outputInput = new Input<>("outputDir", "output directory for SVG bar charts",
			new OutFile("[[none]]"));
	
	final public Input<Boolean> useRankedBinsInput = new Input<>("useRankedBins", "if true use ranking wrt prior to find bins."
			+ "if false, use empirical bins based on prior.", true);

	final public Input<String> excludeInput = new Input<>("exclude", "comma separated list of entries to exclude from the analysis", "");

	final public Input<String> htmlTitleLabelInput = new Input<>("label", "label for html title (only used if html is generated, for which "
			+ "'out' needs to be specified). Can also be set by the COVERAGE_LABEL environment variable or the `coveragelabel` java directive "
			+ "(through java -d coveragelabel=MyLabel)");

	
	@Override
	public void initAndValidate() {
	}

	Set<String> exclude;
	
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
		
		File svgdir = null;
		PrintStream html = null;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			String label = htmlTitleLabelInput.get() == null ? "" : htmlTitleLabelInput.get();
			if (System.getProperty("coveragelabel") != null) {
				label = " " + System.getProperty("coveragelabel");
			} 
			if (System.getenv("COVERAGE_LABEL") != null) {
				label = " " + System.getenv("COVERAGE_LABEL");
			}
			svgdir = outputInput.get();
			if (!svgdir.isDirectory()) {
				throw new IllegalArgumentException(svgdir.getPath() + " is not a directory. Specify an existing directory for outputDir");
			}
			html = new PrintStream(svgdir.getPath()+"/SBC.html");
			html.println("<!doctype html>\n"+
					"<html>\n"+
					"<head><title>Simulation Based Calibration" + label + "</title></head>\n"+
					"<body>\n");
			html.println("<h2>Simulation Based Calibration" + label + "</h2>");
			html.println("<li>prior sample: " + logFileInput.get().getPath()+"</li>");
			html.println("<li>posterior samples: " + logAnalyserFileInput.get().getPath()+"</li>");
			html.println("<li>Use "+ (useRankedBinsInput.get() ? "ranking" : "empirical bins") + " for bins</li>");
			html.println("<table>");
		}
		

		LogAnalyser truth = new LogAnalyser(logFileInput.get().getAbsolutePath(), 0, true, false);
		LogAnalyser estimated = new LogAnalyser(logAnalyserFileInput.get().getAbsolutePath(), 0, true, false);
		int skip = skipLogLinesInput.get();

		
		int L = estimated.getTrace(0).length / (truth.getTrace(0).length - skip);

		int binCount = binCountInput.get();
		if (binCount <= 0) {
			binCount = L + 1;
		}
		if (binCount > truth.getTrace(0).length - skip) {
			throw new IllegalArgumentException("Number of bins (or samples per posterior) should be less than number of prior samples");
		}
		
		BinomialDistribution binom = new BinomialDistributionImpl(truth.getTrace(0).length, 1.0/binCount);
		int pLow = binom.inverseCumulativeProbability(0.005);
		int pUp = binom.inverseCumulativeProbability(0.995);
		int pLow95 = binom.inverseCumulativeProbability(0.025);
		int pUp95 = binom.inverseCumulativeProbability(0.975);
		int pExp = truth.getTrace(0).length/binCount;
		Log.info("99%lo << mean << 99%up = " + pLow + " << " + pExp + " << " + pUp);
		
		StringBuilder b = new StringBuilder();
		for (int j = 0; j < binCount; j++) {
			b.append("bin" + j + "\t");
		}
		Log.info(CoverageCalculator.space + "\tmissed\t" + b.toString());
		
		int k = 0;
		StringBuilder [] html2 = new StringBuilder[2];
		html2[0] = new StringBuilder();
		html2[1] = new StringBuilder();
		html2[0].append("<table>\n");
		html2[1].append("<table>\n");
		for (int i = 0; i < truth.getLabels().size(); i++) {
			String label = truth.getLabels().get(i);
			if (!(exclude.contains(label) ||
					Double.isNaN(truth.getTrace(i+1)[0]))) {
				output(i, k, label, truth, estimated, svgdir, skip, html, html2,
						binCount, L, pLow, pUp, pLow95, pUp95, pExp);
				k++;
			}						
		}
		html2[0].append("</table>\n");
		html2[1].append("</table>\n");
		//Log.info("Expected number of misses: " + 0.05 * binom.getNumberOfTrials());
		
		if (html != null) {
			//html.println("Expected number of misses: " + 0.05 * binom.getNumberOfTrials());
			html.println("</table>");
			html.println();
			html.println(html2[0].toString());
			html.println(html2[1].toString());
			html.println("</body>\n</html>");
			html.close();
			
			try {
				Application.openUrl("file://" + svgdir.getPath()+"/SBC.html");
			} catch (IOException e) {
				e.printStackTrace();
				Log.warning("Output in " + svgdir.getPath()+"/SBC.html");
			}
		}

		Log.warning("Done!");	
	}

	double [][] bounds = null;
	private void output(int i, int k2, String label, LogAnalyser truth, LogAnalyser estimated, File svgdir, int skip,
			PrintStream html, StringBuilder []html2, int binCount, int L, int pLow, int pUp, int pLow95, int pUp95, int pExp) 
					throws IOException, MathException {
		Double [] trueValues = truth.getTrace(label);
		Double [] estimates = null;
		try {
			estimates = estimated.getTrace(label);
		} catch (IndexOutOfBoundsException e) {
			Log.warning("Label " + label + " not found in estimates");
		}
		if (estimates == null) {
			Log.warning("Skipping " + label);
		} else {
			if (skip > 0) {
				Double [] tmp = new Double[trueValues.length - skip];
				System.arraycopy(trueValues, skip, tmp, 0, tmp.length);
				trueValues = tmp;
			}

			int  [] bins = new int[binCount];
			
//			double [] binBoundaries = new double[binCount - 1];
//			for (int k = 0; k < binCount-1; k++) {
//				int j = (int) (trueValues.length * (k+1.0)/binCount);
//				binBoundaries[k] = (trueValues[j] + trueValues[j+1]) / 2.0;
//			}

			boolean empiricalBins = useRankedBinsInput.get();

			// int L = estimates.length / trueValues.length;
			for (int j = 0; j < trueValues.length; j++) {
				double [] estimatesX = new double[L];
				for (int k = 0; k < L; k++) {
					estimatesX[k] = estimates[j * L + k];
				}
				Arrays.sort(estimatesX);
				
				if (empiricalBins) {
					double [] binBoundaries = new double[binCount - 1];
					if (empiricalBins) {
						if (binCountInput.get() <= 0) {
							binBoundaries = estimatesX;
						} else { 
							for (int k = 0; k < binCount-1; k++) {
								int m = (int) (estimatesX.length * (k+1.0)/binCount);
								binBoundaries[k] = (estimatesX[m] + estimatesX[m+1]) / 2.0;
							}
						}
					}

					int bin = Arrays.binarySearch(binBoundaries, trueValues[j]);
					if (bin < 0) {
						bin = -bin-1;
					}
					bins[bin]++;
				} else {
					int rank = Arrays.binarySearch(estimatesX, trueValues[j]);
					if (rank < 0) {
						rank = 1-rank;
					}
					int bin = rank * binCount / L;
					if (bin == bins.length) {
						bin--;
					}
					bins[bin]++;
				}
				
			}

			
			StringBuilder b = new StringBuilder();
			int missed = 0;
			for (int j = 0; j < binCount; j++) {
				b.append(bins[j] + "\t");
				if (pLow > bins[j] || pUp < bins[j]) {
					missed++;
				}
			}
			
			int n = bins.length;
			double [] binomHi = new double[n];
			double [] binomLo = new double[n];
			int [] cumBins = new int[n];
			
			for (int j = 0; j < n; j++) {
				cumBins[j] = bins[j] + (j>0?cumBins[j-1]:0);
			}
			for (int j = 0; j < n; j++) {
				BinomialDistribution binom = new BinomialDistributionImpl(cumBins[n-1], (j+0.5) / n);
				binomLo[j] = binom.inverseCumulativeProbability(0.025);
				binomHi[j] = binom.inverseCumulativeProbability(0.975);
			}
			
			
			Log.info(label + (label.length() < CoverageCalculator.space.length() ? CoverageCalculator.space.substring(label.length()) : "") + "\t" + 
					missed + "\t" + 
					b.toString());

			if (html != null) {
				if (bounds == null) {
					bounds = new double[2][binCount + 1];
					simulateBounds(100000, trueValues.length, binCount, bounds);
				}
				outputHTML(k2, label, svgdir, skip, html, html2, binCount, L, pLow, pUp, pLow95, pUp95, pExp, bins, missed,
						cumBins, binomLo, binomHi, bounds 
						);
			}
		}		
	}

	
	/** run a number of trials, and empirically determine bounds for N experiments and `bins` bins **/
	public static void simulateBounds(int trials, int N, int bins, double[][] bounds) {
		double deltaBin = (double)1.0/(double)bins;
		
		int [][] counts = new int[bins][trials];
		for (int i = 0; i < trials; i++) {
			int [] count = new int[bins];
			for (int j = 0; j < N; j++) {
				double r = Randomizer.nextDouble();
				int bin = (int)(r / deltaBin);
				count[bin]++;
			}
			int sum = 0;
			for (int k = 0;k < bins; k++) {
				counts[k][i] = sum + count[k];
				sum += count[k];
			}
		}
		for (int k = 0;k < bins; k++) {
			Arrays.sort(counts[k]);
		}
		
		// Take 95% **symmetric** confidence interval for bounds
		// Note, these are not HPDs.
		for (int k = 0; k < bins; k++) {
			bounds[0][k+1] = (double)counts[k][(int)( 2.5 * trials/100.0)]/(double)N;
			bounds[1][k+1] = (double)counts[k][(int)(97.5 * trials/100.0)]/(double)N;
		}
		
		// get rid of noise
		double [] tmp = new double[bounds[0].length];
		int w = 3;

		System.arraycopy(bounds[0], 0, tmp, 0, bounds[0].length);
		for (int k = w; k < bins-w; k++) {
			double sum = 0;
			for (int i = -w; i <= w; i++) {
				sum += tmp[k + i];
			}
			bounds[0][k] = sum /= (2*w+1);
		}
		
		System.arraycopy(bounds[1], 0, tmp, 0, bounds[0].length);
		for (int k = w; k < bins-w; k++) {
			double sum = 0;
			for (int i = -w; i <= w; i++) {
				sum += tmp[k + i];
			}
			bounds[1][k] = sum /= (2*w+1);
		}
	}

	private void outputHTML(int k2, String label, File svgdir, int skip,
			PrintStream html, 
			StringBuilder [] html2, int binCount, int L, int pLow, int pUp, int pLow95, int pUp95, int pExp, int [] bins, int missed,
			int [] cumBins, double [] binomLo, double [] binomHi,
			double [][] bounds
			) throws IOException {
		int max = pUp;
		for (int d : bins) {
			max = Math.max(d,  max);
		}
		if (max < 100) {
			max = max + 9 - (max+9) % 10;
		} else if (max < 250) {
			max = max + 49 - (max+49) % 50;
		} else {
			max = max + 99 - (max+99) % 100;
		}
		
		outputSVGGraph(label, svgdir, binCount, pLow, pUp, pLow95, pUp95, pExp, bins, max);
		
		outputECDFGraph(label, svgdir, binCount, cumBins, binomLo, binomHi, bounds);
		
		outputECDFDiffGraph(label, svgdir, binCount, cumBins, binomLo, binomHi, bounds);
		
		
		
		if (k2 % 4 == 0) {
			html.println("<tr>");
			html2[0].append("<tr>\n");
			html2[1].append("<tr>\n");
		}
		html.println("<td>");
		html.println("<h3>" + label + "</h3>");
		html.println("<p>Missed: " + missed + "</p><p>");
		html.println("<img width=\"350px\" src=\"" + label + ".svg\">");
		html.println("</td>");

		html2[0].append("<td>");
		html2[0].append("<h3>" + label + "</h3>");
		html2[0].append("<p>Missed: " + missed + "</p><p>");
		html2[0].append("<img width=\"350px\" src=\"" + label + ".ECDF.svg\">");
		html2[0].append("</td>");
		
		html2[1].append("<td>");
		html2[1].append("<h3>" + label + "</h3>");
		html2[1].append("<p>Missed: " + missed + "</p><p>");
		html2[1].append("<img width=\"350px\" src=\"" + label + "Diff.ECDF.svg\">");
		html2[1].append("</td>");

		if ((k2+1) % 4 == 0) {
			html.println("</tr>");
			html2[1].append("</tr>\n");
			html2[1].append("</tr>\n");
		}
	}

	private void outputECDFGraph(String label, File svgdir, int binCount, int[] cumBins, 
			double[] binomLo,
			double[] binomHi,
			double[][] bounds) throws IOException {
		double max = cumBins[cumBins.length - 1];
		
		StringBuilder bHi = new StringBuilder();
		bHi.append("0,0 ");
		StringBuilder bLo = new StringBuilder();
		bLo.append("0,0 ");
		StringBuilder cdf = new StringBuilder();
		cdf.append("0,0 ");
		for (int i = 0; i < binCount; i++) {
			double x = (i+0.0)/binCount;
			x = (i+0.5)/binCount;
			//cdf.append(x + "," + (i>0?(cumBins[i-1]/max):0) + " ");
			cdf.append(x + "," + (cumBins[i]/max) + " ");
		}
		for (int i = 0; i < bounds[0].length-1; i++) {
			double x = (i+0.0)/(bounds[0].length-1);
			bHi.append(x + "," + (binomHi[i]/max) + " ");
			x = (i+0.5)/(bounds[0].length-1);
			bLo.append(x + "," + (binomLo[i]/max) + " ");
		}
		bLo.append("1,1");
		bHi.append("1,1");
		cdf.append("1,1 ");

		StringBuilder bHi2 = new StringBuilder();
		StringBuilder bLo2 = new StringBuilder();
		for (int i = 0; i < bounds[0].length; i++) {
			double x = (i+0.0)/(bounds[0].length-1);
			bLo2.append(x + "," + bounds[0][i] + " ");
			bHi2.append(x + "," + bounds[1][i] + " ");
		}

		outputSVG(label, svgdir, binCount, bHi, bLo, cdf, bHi2, bLo2);
	}
		
	private void outputECDFDiffGraph(String label, File svgdir, int binCount, int[] cumBins, 
			double[] binomLo,
			double[] binomHi,
			double[][] bounds) throws IOException {
		double max = cumBins[cumBins.length - 1];
		
		StringBuilder bHi = new StringBuilder();
		bHi.append("0,0.5 ");
		StringBuilder bLo = new StringBuilder();
		bLo.append("0,0.5 ");
		StringBuilder cdf = new StringBuilder();
		cdf.append("0,0.5 ");
		for (int i = 0; i < binCount; i++) {
			double x = (i+0.0)/binCount;
			bHi.append(x + "," + diff(x, binomHi[i]/max) + " ");
			x = (i+0.5)/binCount;
			bLo.append(x + "," + diff(x, binomLo[i]/max) + " ");
			//cdf.append(x + "," + (i>0?(cumBins[i-1]/max):0) + " ");
			cdf.append(x + "," + diff(x, (cumBins[i]/max)) + " ");
		}
		bLo.append("1,0.5");
		bHi.append("1,0.5");
		cdf.append("1,0.5 ");

		StringBuilder bHi2 = new StringBuilder();
		StringBuilder bLo2 = new StringBuilder();
		for (int i = 0; i < bounds[0].length; i++) {
			double x = (i+0.0)/(bounds[0].length-1);
			bLo2.append(x + "," + diff(x, bounds[0][i]) + " ");
			bHi2.append(x + "," + diff(x, bounds[1][i]) + " ");
		}

		outputSVG(label+"Diff", svgdir, binCount, bHi, bLo, cdf, bHi2, bLo2);
	}

	
	private double diff(double x, double y) {
		y = 0.5 + y - x;
		y = 0.5 + (y-0.5) * 4.0;
		return y;
	}

	private void outputSVG(String label, File svgdir, int binCount, StringBuilder bHi, StringBuilder bLo, StringBuilder cdf, StringBuilder bHi2, StringBuilder bLo2) throws IOException  {
		// bars
		double dx = 1000.0;
		double dy = 740.0;
	
		PrintStream svg = new PrintStream(svgdir.getPath() +"/" + label + ".ECDF.svg");
		svg.println("<svg class=\"chart\" width=\"1080\" height=\"780\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\">");

		svg.println("<g transform=\"translate(30,700) scale("+dx+",-"+700+")\">");
		// grid
		svg.println("<rect x='0.2' y='0' width=\"0.2\" height=\"1\" style=\"fill:none;stroke-width:0.001;stroke:rgb(0,0,0);opacity: 0.5;\"/>");
		svg.println("<rect x='0.6' y='0' width=\"0.2\" height=\"1\" style=\"fill:none;stroke-width:0.001;stroke:rgb(0,0,0);opacity: 0.5;\"/>");
		svg.println("<rect x='0' y='0.2' width=\"1\" height=\"0.2\" style=\"fill:none;stroke-width:0.001;stroke:rgb(0,0,0);opacity: 0.5;\"/>");
		svg.println("<rect x='0' y='0.6' width=\"1\" height=\"0.2\" style=\"fill:none;stroke-width:0.001;stroke:rgb(0,0,0);opacity: 0.5;\"/>");
		svg.println("<rect x='0' y='0' width=\"1\" height=\"1\" style=\"fill:none;stroke-width:0.0025;stroke:rgb(0,0,0);opacity: 0.5;\"/>");
		if (label.endsWith("Diff")) {
			svg.println("  <polyline points=\"0,0.5 1,0.5\" style=\"fill:none;stroke-width:0.005;stroke:rgb(0,0,0);opacity: 0.5;\"/>");
		} else {
			svg.println("  <polyline points=\"0,0 1,1\" style=\"fill:none;stroke-width:0.005;stroke:rgb(0,0,0);opacity: 0.5;\"/>");
		}

		
		// ECDF graph + bounds
//		svg.println("  <polyline points=\"" + bHi.toString() + "\" style=\"fill:none;stroke-width:0.01;stroke:rgb(0,0,200);opacity: 0.5;\"/>");
//		svg.println("  <polyline points=\"" + bLo.toString() + "\" style=\"fill:none;stroke-width:0.01;stroke:rgb(0,0,200);opacity: 0.5;\"/>");
		
		svg.println("  <polyline points=\"" + bHi2.toString() + "\" style=\"fill:none;stroke-width:0.01;stroke:rgb(0,200,200);opacity: 0.5;\"/>");
		svg.println("  <polyline points=\"" + bLo2.toString() + "\" style=\"fill:none;stroke-width:0.01;stroke:rgb(0,200,200);opacity: 0.5;\"/>");

		svg.println("  <polyline points=\"" + cdf.toString() + "\" style=\"fill:none;stroke-width:0.01;stroke:rgb(0,0,0);opacity: 1.0;\"/>");

		svg.println("</g>");
		svg.println("<text style='font-size:20pt' x='0' y='20'>1.0</text>");
		svg.println("<text style='font-size:20pt' x='0' y='710'>0</text>");
		svg.println("<text style='font-size:20pt' x='"+(10+500/binCount)+"' y='720'>" + 0 + "</text>");
		DecimalFormat f = new DecimalFormat("#.#");
		for (int j = 10; j <= binCount; j+=10) {
			int x = 10 + j*1000/binCount - 500/binCount;
			int y = 720;
			svg.println("<text style='font-size:20pt' x='"+x+"' y='"+y+"'>" + f.format((j+0.0)/binCount) + "</text>");
		}
		svg.println("</svg>");
		svg.close();	
		
	}

	private void outputSVGGraph(String label, File svgdir, 
			int binCount, int pLow, int pUp, int pLow95, int pUp95, int pExp, int [] bins, int max) throws IOException{
		PrintStream svg = new PrintStream(svgdir.getPath() +"/" + label + ".svg");
		svg.println("<svg class=\"chart\" width=\"1080\" height=\"780\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\">");
		
		// axes
		svg.println("<rect x='25' width=\"1000\" height=\"700\" style=\"fill:none;stroke-width:1;stroke:rgb(0,0,0)\"/>");
		
		// bars
		double dx = 1000.0 / binCount;
		double dy = 740.0 / max;
		// permissable area
		svg.println("<g transform=\"translate(30,700) scale("+dx+",-"+dy+")\">");
		svg.println("  <polygon points=\"0,"+pExp+" "+binCount+","+pExp+" "+(binCount+1)+","+pUp+" -1,"+pUp+"\" style=\"fill:#eee;stroke-width:0;stroke:rgb(0,0,0);opacity: 0.5;\"/>");
		svg.println("  <polygon points=\"0,"+pExp+" "+binCount+","+pExp+" "+(binCount+1)+","+pLow+" -1,"+pLow+"\" style=\"fill:#eee;stroke-width:0;stroke:rgb(0,0,0);opacity: 0.5;\"/>");
		svg.println("  <polygon points=\"0,"+pExp+" "+binCount+","+pExp+" "+(binCount+1)+","+pUp95+" -1,"+pUp95+"\" style=\"fill:#ccc;stroke-width:0;stroke:rgb(0,0,0);opacity: 0.5;\"/>");
		svg.println("  <polygon points=\"0,"+pExp+" "+binCount+","+pExp+" "+(binCount+1)+","+pLow95+" -1,"+pLow95+"\" style=\"fill:#ccc;stroke-width:0;stroke:rgb(0,0,0);opacity: 0.5;\"/>");

		
		for (int j = 0; j < binCount; j++) {
			svg.println("  <rect x=\""+j+"\" y=\"0\" width=\"0.95\" height=\""+bins[j]+"\" style=\"fill:#00afd7;stroke-width:0.05;stroke:#3d37db\"/>");
			if (j % 10 == 0) {
			// ticks
			//	svg.println("<line y1='0' y2='-1' x1='" + (j+0.45) + "' x2='" + (j+0.45) + "' style='stroke-width:0.1;stroke:rgb(0,0,0)'/>");
			}
		}
		svg.println("</g>");
		svg.println("<text style='font-size:20pt' x='0' y='20'>" + max + "</text>");
		svg.println("<text style='font-size:20pt' x='0' y='710'>0</text>");
		svg.println("<text style='font-size:20pt' x='"+(10+500/binCount)+"' y='720'>" + 1 + "</text>");
		for (int j = 10; j <= binCount; j+=10) {
			int x = 10 + j*1000/binCount - 500/binCount;
			int y = 720;
			svg.println("<text style='font-size:20pt' x='"+x+"' y='"+y+"'>" + j + "</text>");
		}
		svg.println("<text style='font-size:20pt' x='1030' y='"+(705-dy * pUp)+"'>" + pUp + "</text>");
		svg.println("<text style='font-size:20pt' x='1030' y='"+(705-dy * pUp95)+"'>" + pUp95 + "</text>");
		svg.println("<text style='font-size:20pt' x='1030' y='"+(705-dy * pExp)+"'>" + pExp + "</text>");
		svg.println("<text style='font-size:20pt' x='1030' y='"+(705-dy * pLow95)+"'>" + pLow95 + "</text>");
		svg.println("<text style='font-size:20pt' x='1030' y='"+(705-dy * pLow)+"'>" + pLow + "</text>");

		svg.println("</svg>");
		svg.close();	
	}

	public static void main(String[] args) throws Exception {
		new Application(new SBCAnalyser(), "Simulation-Based Calibration Analyser", args);
	}

}
