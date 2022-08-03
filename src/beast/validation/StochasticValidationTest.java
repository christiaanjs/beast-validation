package beast.validation;

import beast.base.core.Input;
import beast.base.inference.Logger;
import beast.base.inference.Runnable;
import beast.simulation.Sampler;
import beast.validation.statistics.Statistics;
import beast.validation.tests.StatisticalTest;
import beast.validation.tests.StatisticalTestType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class StochasticValidationTest extends Runnable {

    private static final double DEFAULT_ALPHA = 1.0 - 1e-3;
    private static final int DEFAULT_N_SAMPLES = 1000;
    private static final int DEFAULT_PRINT_EVERY = 100;

    public Input<Double> alphaInput = new Input<>("alpha", "1 - significance level of test", DEFAULT_ALPHA);
    public Input<Integer> nSamplesInput = new Input<>("nSamples", "Number of samples to use", DEFAULT_N_SAMPLES, Input.Validate.OPTIONAL);
    public Input<Integer> printEveryInput = new Input<>("printEvery", "How regularly sampling progress is reported (-1 for never)", DEFAULT_PRINT_EVERY, Input.Validate.OPTIONAL);

    public Input<List<Sampler>> samplersInput  = new Input<>("sampler", "Tree samplers to use in testing", new ArrayList<>());
    public Input<List<Statistics>> statisticsInput = new Input<>("statistic", "Statistics from trees to perform test on", new ArrayList<>());
    public Input<StatisticalTest> testInput = new Input<>("test", "Hypothesis test to perform on statistics", Input.Validate.REQUIRED);
    // Result

    public Input<List<Logger>> sampleLoggersInput = new Input<>("sampleLogger", "Loggers run during sampling", new ArrayList<>());
    public Input<List<Logger>> resultLoggersInput = new Input<>("resultLogger", "Logger run after testing", new ArrayList<>());

    private double alpha;
    private int nSamples;
    private int printEvery;

    private List<Sampler> samplers;
    private List<Statistics> statistics;
    private StatisticalTest test;

    private List<Logger> sampleLoggers;
    private List<Logger> resultLoggers;

    private List<double[][]> samples;

    public void initAndValidate(){
        alpha = alphaInput.get();
        if(alpha <= 0.0 || alpha >= 1.0) throw new IllegalArgumentException("alpha must be between 0 and 1");

        nSamples = nSamplesInput.get();
        printEvery = printEveryInput.get();

        samplers = samplersInput.get();
        if(samplers.size() < 1) throw new IllegalArgumentException("There must be at least one sampler");

        statistics = statisticsInput.get();
        if(statistics.size() < 1) throw new IllegalArgumentException("There must be at least one statistic");

        test = testInput.get();
        if(test.getType() == StatisticalTestType.OneSample && statistics.size() != 1){
            throw new IllegalArgumentException(String.format("Test is for 1 sample but %s are given", statistics.size()));
        } else if(test.getType() == StatisticalTestType.TwoSample && statistics.size() != 2) {
            throw new IllegalArgumentException(String.format("Test is for 2 samples but %s are given", statistics.size()));
        }

        sampleLoggers = sampleLoggersInput.get();
        resultLoggers = resultLoggersInput.get();

        nSamples = nSamplesInput.get();

        samples = new ArrayList<>(statistics.size());
        for(int i = 0; i < statistics.size(); i++){
            samples.add(i, new double[nSamples][statistics.get(0).getDimension()]);
        }

    }

    @Override
    public void run() throws IOException {
        System.out.println("Stochastic validation test");

        for(Logger logger: sampleLoggers) logger.init();

        System.out.println("Sampling...");

        for(int i = 0; i < nSamples; i++){
            if(printEvery != -1 && i % printEvery == 0){
                System.out.println("Sample " + (i + 1));
            }

            for(Sampler sampler: samplers) sampler.nextState(i);

            for(int statisticIndex = 0; statisticIndex < statistics.size(); statisticIndex++){
                Statistics statistic = statistics.get(statisticIndex);
                statistic.updateStatistics(i);
                double[][] sampleArray = samples.get(statisticIndex);
                System.arraycopy(statistic.getArrayValues(), 0, sampleArray[i], 0, statistic.getDimension());
            }

            for(Logger logger: sampleLoggers) logger.log(i);
        }

        for(Logger logger: sampleLoggers) logger.close();

        System.out.println("Performing test...");

        test.performTest(samples);

        if(test.getPValue() < alpha){
            System.out.println("Test FAILED");
        } else {
            System.out.println("Test PASSED");
        }
        System.out.println(String.format("p value: %f", test.getPValue()));

        for(Logger logger: resultLoggers){
            logger.init();
            logger.log(0);
            logger.close();
        }



    }

}
