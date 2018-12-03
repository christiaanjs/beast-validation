package beast.validation.tests;

import org.apache.commons.math3.stat.descriptive.MultivariateSummaryStatistics;
import org.junit.Test;

import static org.junit.Assert.*;

public class MultivariateNormalZeroMeanTestTest {

    private static final double[][] DATA = new double[][]{
            new double[]{ 1.06307305, -0.69238605, -0.1043074 },
            new double[]{ 0.79800287,  2.37851236, -1.08479476 },
            new double[]{ 0.79800287,  2.37851236, -1.08479476 },
            new double[]{ 2.30339478, -0.30098666, -0.0768391 }
    };

    @Test
    public void testCalcSampleMean(){
        MultivariateNormalZeroMeanTest test = new MultivariateNormalZeroMeanTest();
        test.initSampleStats(DATA);

        MultivariateSummaryStatistics stats = new MultivariateSummaryStatistics(DATA[0].length, false);
        for(int i = 0; i < DATA.length; i++) stats.addValue(DATA[i]);
        assertArrayEquals(stats.getMean(), test.getSampleMean(), 1e-32);
    }

    @Test
    public void testCalcSampleCov(){
        MultivariateNormalZeroMeanTest test = new MultivariateNormalZeroMeanTest();
        test.initSampleStats(DATA);

        MultivariateSummaryStatistics stats = new MultivariateSummaryStatistics(DATA[0].length, false);
        for(int i = 0; i < DATA.length; i++) stats.addValue(DATA[i]);

        double[][] expected = stats.getCovariance().getData();
        double[][] actual  = test.getSampleCovariance();

        for(int i = 0; i < expected.length; i++){
            assertArrayEquals(expected[i], actual[i], 1e-32);
        }
    }

}