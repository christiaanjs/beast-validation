package beast.util;

import org.junit.Test;

import beastvalidation.util.MultivariateNormalDistribution;

import static org.junit.Assert.*;

public class MultivariateNormalDistributionTest {

    @Test
    public void testLogDensity() {
        double[] means = new double[]{ 0.20153336, 0.58806799, 0.59036867 };
        double[][] cov = new double[][]{
                new double[]{ 0.61723109, 0.49920416, 0.0202322 },
                new double[]{ 0.49920416, 3.24306295, 0.51723184 },
                new double[]{ 0.0202322 , 0.51723184, 0.34749632 }
        };
        double[] x = new double[]{ 0.12241642, 0.91228336, 1.01138388 };

        MultivariateNormalDistribution actual = new MultivariateNormalDistribution(means, cov);
        double expected = -2.6364360042659536;
        assertEquals(expected, actual.logDensity(x), 1e-6);
    }
}