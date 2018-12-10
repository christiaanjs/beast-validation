package beast.validation.tests;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class BootstrapMultivariateDistributionTestTest {

    private static final double[][] X1 = new double[][]{
            new double[]{1.1784698551862782, -2.3124471612084503},
            new double[]{-0.36234927231385305, -2.3761811485237327},
            new double[]{1.2919743996399626, -0.9816599079411318},
            new double[]{0.35836246322823406, -2.700574225814877},
            new double[]{2.1385939067489397, -0.5108625066030221},
            new double[]{0.5031228377272023, -2.017979794557402},
            new double[]{2.087930305850217, -0.8463441749188402},
            new double[]{1.4076888219649062, -0.3964649124991051},
            new double[]{2.6911952038946643, 0.7863866303962171},
            new double[]{0.3465685848888953, -1.1390940178640143}
    };

    private static final double[][] X2 = new double[][]{
            new double[]{2.7880399133444733, -1.2469845169534777},
            new double[]{1.4656000217893057, -1.4811476997847572},
            new double[]{1.2782968101293324, -0.11902133975748364},
            new double[]{0.7308819329467251, -0.980366507508189},
            new double[]{0.736679442744509, -1.3970079861089237},
            new double[]{0.7572528339560847, -0.6235226581322982},
            new double[]{0.18104250922687393, -0.3939688288517684},
            new double[]{3.7684203943370744, 0.07765136514092985},
            new double[]{1.376538833772882, -1.4364220400788033},
            new double[]{0.6591921281243815, 0.9606451079673878}
    };

    @Test
    public void testKs(){
        BootstrapMultivariateDistributionTest test = new BootstrapMultivariateDistributionTest();
        test.setInputValue(test.criterionInput.getName(), "ks");
        test.setInputValue(test.nBootsInput.getName(), 1);
        test.initAndValidate();
        test.performTest(Arrays.asList(X1, X2));
        assertEquals(0.5, test.getStatistic(), 1e-12);
    }

    @Test
    public void testCvm(){
        BootstrapMultivariateDistributionTest test = new BootstrapMultivariateDistributionTest();
        test.setInputValue(test.criterionInput.getName(), "cvm");
        test.setInputValue(test.nBootsInput.getName(), 1);
        test.initAndValidate();
        test.performTest(Arrays.asList(X1, X2));
        assertEquals(1.21, test.getStatistic(), 1e-12);
    }
}