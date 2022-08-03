package beastvalidation.validation.tests;

import org.apache.commons.math.MathException;
import org.apache.commons.math.distribution.ChiSquaredDistribution;
import org.apache.commons.math.distribution.ChiSquaredDistributionImpl;

import beastvalidation.util.MultivariateNormalDistribution;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MultivariateNormalZeroMeanTest extends StatisticalTest {

    private int n;
    private int d;

    private double[] sampleMean;
    private double[][] sampleSquaredMean;
    private double[][] sampleCov;

    private double h0LogLikelihood;
    private double h1LogLikelihood;

    private double testStatistic;

    protected void initSampleStats(double[][] values){
        n = values.length;
        d = values[0].length;

        sampleMean = new double[d];
        sampleSquaredMean = new double[d][d];
        sampleCov = new double[d][d];

        for(int i = 0; i < n; i++){
            for(int j = 0; j < d; j++){
                sampleMean[j] += values[i][j] / n;
                for(int k = 0; k < d; k++){
                    sampleSquaredMean[j][k] += values[i][j] * values[i][k] / n;
                }
            }
        }

        for(int j = 0; j < d; j++){
            for(int k = 0; k < d; k++){
                sampleCov[j][k] = sampleSquaredMean[j][k] - sampleMean[j]*sampleMean[k];
            }
        }
    }

    @Override
    public void performTest(List<double[][]> valuesList) {
        if(valuesList.size() != 1) throw new IllegalArgumentException("Only one sample must be provided");

        double[][] values = valuesList.get(0);
        if(values.length < 2) throw new IllegalArgumentException("At least two values must be provided");
        if(values[0].length < 1) throw new IllegalArgumentException("Data must have at least one dimension");

        initSampleStats(values);

        MultivariateNormalDistribution h0 = new MultivariateNormalDistribution(new double[d], sampleSquaredMean);
        MultivariateNormalDistribution h1 = new MultivariateNormalDistribution(sampleMean, sampleCov);

        h0LogLikelihood = 0;
        h1LogLikelihood = 0;

        for(int i = 0; i < n; i++){
            h0LogLikelihood += h0.logDensity(values[i]);
            h1LogLikelihood += h1.logDensity(values[i]);
        }

        testStatistic = 2 * (h1LogLikelihood - h0LogLikelihood);
        ChiSquaredDistribution chiSq = new ChiSquaredDistributionImpl(d);

        try {
            pValue = 1.0 - chiSq.cumulativeProbability(testStatistic);
        } catch (MathException e) {
            System.out.println("Chi-squared p-value calculation failed");
            throw new RuntimeException(e);
        }
    }

    public double[] getSampleMean(){
        return sampleMean; // TODO: Test
    }

    public double[][] getSampleCovariance(){
        return sampleCov;// TODO: Test
    }

    @Override
    public StatisticalTestType getType() {
        return StatisticalTestType.OneSample;
    }

    @Override
    public Map<String, String> getSummary() {
        Map<String, String> summary = new HashMap<>();
        summary.put("sampleMean", Arrays.toString(sampleMean));
        summary.put("sampleCov", Arrays.deepToString(sampleCov));
        summary.put("nullCov", Arrays.deepToString(sampleSquaredMean));
        summary.put("df", Integer.toString(d));
        summary.put("h0LogLikelihood", Double.toString(h0LogLikelihood));
        summary.put("h1LogLikelihood", Double.toString(h1LogLikelihood));
        summary.put("testStatistic", Double.toString(testStatistic));
        return summary;
    }
}
