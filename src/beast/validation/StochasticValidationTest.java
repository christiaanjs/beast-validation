package beast.validation;

import beast.core.Input;
import beast.core.Runnable;
import beast.core.parameter.RealParameter;

public abstract class StochasticValidationTest extends Runnable {

    private static final double DEFAULT_ALPHA = 1.0 - 1e-3;

    public Input<Double> alphaInput = new Input<>("alpha", "1 - significance level of test", DEFAULT_ALPHA);

    private double alpha;

    public abstract double performTest();

    public abstract String getDescription();

    public void initAndValidate(){
        alpha = alphaInput.get();

        if(alpha <= 0.0 || alpha >= 1.0) throw new IllegalArgumentException("alpha must be between 0 and 1");
    }

    @Override
    public void run(){
        System.out.println("Stochastic validation test");
        System.out.println(getDescription());
        System.out.println("Performing test...");
        double pValue = performTest();
        System.out.println("Test complete");
        System.out.println(String.format("p value: %f", pValue));
        System.out.println(String.format("%s at significance level %f", pValue < alpha ? "FAILED" : "PASSED", 1 - alpha));
    }

}
