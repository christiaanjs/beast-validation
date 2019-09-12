package beast.experimenter;

import java.io.File;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;

import org.apache.commons.math.distribution.BinomialDistribution;
import org.apache.commons.math.distribution.BinomialDistributionImpl;

import beast.app.util.Application;
import beast.core.Citation;
import beast.core.Description;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.Input.Validate;
import beast.core.util.Log;
import beast.util.LogAnalyser;

@Description(value="Validating  Bayesian  Inference Algorithms  with  Simulation-Based Calibration")
@Citation(value="Sean Talts, Michael Betancourt, Daniel Simpson, Aki Vehtari, Andrew Gelman, Validating  Bayesian  Inference Algorithms  with  Simulation-Based Calibration, 2018, arXiv:1804.06788v1", DOI="arXiv:1804.06788v1")
public class SBCAnalyser extends Runnable {
	final public Input<File> logFileInput = new Input<>("log", "log file containing actual values", Validate.REQUIRED);
	final public Input<Integer> skipLogLinesInput = new Input<>("skip", "numer of log file lines to skip", 1);
	final public Input<File> logAnalyserFileInput = new Input<>("logAnalyser", "file produced by loganalyser tool using the -oneline option, containing estimated values", Validate.REQUIRED);
	final public Input<Integer> binCountInput = new Input<>("bins", "number of bins to represent prior distribution", 20);

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		LogAnalyser truth = new LogAnalyser(logFileInput.get().getAbsolutePath(), 0, true, false);
		LogAnalyser estimated = new LogAnalyser(logAnalyserFileInput.get().getAbsolutePath(), 0, true, false);
		int skip = skipLogLinesInput.get();
//		if (truth.getTrace(0).length - skip != estimated.getTrace(0).length) {
//			Log.warning("WARNING: traces are of different lengths: "
//					+ (truth.getTrace(0).length - skip) + "!=" + estimated.getTrace(0).length);
//		}
		
//		NumberFormat formatter = new DecimalFormat("#0.00");
		
		int binCount = binCountInput.get();
		StringBuilder b = new StringBuilder();
		for (int j = 0; j < binCount; j++) {
			b.append("bin" + j + "\t");
		}
		Log.info(CoverageCalculator.space + "\tmissed"
//				+ "\tMean ESS\tMin ESS\t" 
				+ b.toString());
		BinomialDistribution binom = new BinomialDistributionImpl(estimated.getTrace(0).length, 1.0/binCount);
		int pLow = binom.inverseCumulativeProbability(0.025);
		int pUp = binom.inverseCumulativeProbability(0.975);
		for (int i = 0; i < truth.getLabels().size(); i++) {
			String label = truth.getLabels().get(i);
			Double [] trueValues = truth.getTrace(label);
						
			Double [] means = estimated.getTrace(label);
//			Double [] ess = estimated.getTrace(label+".ESS");
			if (means == null) {
				Log.warning("Skipping " + label);
			} else {
				if (skip > 0) {
					Double [] tmp = new Double[trueValues.length - skip];
					System.arraycopy(trueValues, skip, tmp, 0, tmp.length);
					trueValues = tmp;
				}

				int  [] bins = new int[binCount];
				Arrays.sort(trueValues);
//				double [] binBoundaries = new double[binCount - 1];
//				for (int k = 0; k < binCount-1; k++) {
//					int j = (int) (trueValues.length * (k+1.0)/binCount);
//					binBoundaries[k] = (trueValues[j] + trueValues[j+1]) / 2.0;
//				}

//				double minESS = Double.POSITIVE_INFINITY;
//				double meanESS = 0;
				for (int j = 0; j < trueValues.length - skip; j++) {
//					int bin = Arrays.binarySearch(binBoundaries, means[j]);
//					if (bin < 0) {
//						bin = 1-bin;
//					}
//					bins[bin]++;
					

					int rank = Arrays.binarySearch(trueValues, means[j]);
					if (rank < 0) {
						rank = 1-rank;
					}
					int bin = rank * (binCount-1) / trueValues.length;
					bins[bin]++;
					
//					minESS = Math.min(minESS, ess[j]);
//					meanESS += ess[j];
				}
//				meanESS /= (trueValues.length - skip);
				
				b = new StringBuilder();
				int missed = 0;
				for (int j = 0; j < binCount; j++) {
					b.append(bins[j] + "\t");
					if (pLow > bins[j] || pUp < bins[j]) {
						missed++;
					}
				}
				
				Log.info(label + (label.length() < CoverageCalculator.space.length() ? CoverageCalculator.space.substring(label.length()) : "") + "\t" + 
						missed + "\t" + 
//						formatter.format(meanESS) + "\t" + 
//						formatter.format(minESS) + "\t" +
						b.toString());
			}
		}
		Log.info("Expected number of misses: " + 0.05 * binom.getNumberOfTrials());
		

	
	}

	public static void main(String[] args) throws Exception {
		new Application(new SBCAnalyser(), "Simulation-Based Calibration Analyser", args);
	}

}
