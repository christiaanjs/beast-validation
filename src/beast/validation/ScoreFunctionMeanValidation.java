package beast.validation;

import beast.TreeSampler;
import beast.core.Input;
import beast.core.Logger;
import beast.core.parameter.RealParameter;
import beast.evolution.speciation.SpeciesTreeDistribution;
import beast.evolution.tree.TreeInterface;
import beast.util.MultivariateNormalDistribution;
import javafx.util.Pair;
import org.apache.commons.math.MathException;
import org.apache.commons.math.distribution.ChiSquaredDistribution;
import org.apache.commons.math.distribution.ChiSquaredDistributionImpl;
import org.apache.commons.math3.exception.*;
import org.apache.commons.math3.linear.*;
import org.apache.commons.math3.stat.descriptive.MultivariateSummaryStatistics;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ScoreFunctionMeanValidation extends StochasticValidationTest {

    private static final int DEFAULT_N_SAMPLES = 100000;

    public Input<SpeciesTreeDistribution> likelihoodInput = new Input<>("likelihood", "Likelihood to be tested", Input.Validate.REQUIRED);
    public Input<TreeSampler> simulatorInput = new Input<>("treeSimulation", "Simulator to be tested", Input.Validate.REQUIRED);
    public Input<List<RealParameter>> parametersInput = new Input<>("parameters", "Parameters to be included in validation", new ArrayList<>(), Input.Validate.REQUIRED);

    public Input<Integer> nSamplesInput = new Input<>("nSamples", "Number of samples to use", DEFAULT_N_SAMPLES, Input.Validate.OPTIONAL);
    public Input<Double> stepSizeInput = new Input<>("stepSize", "Step size to use when calculating gradient", Math.sqrt(Math.ulp(1.0)), Input.Validate.OPTIONAL);
    public Input<Boolean> relativeStepInput = new Input<>("relativeStep", "Whether to use relative step when calculating gradient", true, Input.Validate.OPTIONAL);

    public Input<String> gradFileNameInput = new Input<>("gradFileName", "name of file to log gradients to", "grad.txt");

    private SpeciesTreeDistribution likelihood;
    private TreeSampler simulator;
    private List<RealParameter> parameters;

    private int nSamples;
    private double stepSize;
    private boolean relativeStep;

    private int paramDim;
    private List<String> paramNames;
    private List<Double> paramValues;

    @Override
    public double performTest() {

        Gradient gradWrap = new Gradient();
        gradWrap.setInputValue(gradWrap.valuesInput.getName(), "0.0");
        gradWrap.setInputValue(gradWrap.dimensionInput.getName(), paramDim);
        gradWrap.initAndValidate();

        Logger gradLogger = new Logger();
        gradLogger.setInputValue(gradLogger.fileNameInput.getName(), gradFileNameInput.get() );
        gradLogger.setInputValue(gradLogger.loggersInput.getName(), Arrays.asList(gradWrap));
        gradLogger.initAndValidate();
        try {
            gradLogger.init();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        MultivariateSummaryStatistics stats = new MultivariateSummaryStatistics(paramDim, false);

        double[][] grads = new double[nSamples][paramDim];

        for(int i = 0; i < nSamples; i++){
            TreeInterface tree = simulator.getNextTree();
            double[] grad = calculateScoreFunction(tree);
            for(int j = 0; j < paramDim; j++) gradWrap.setValue(j, grad[j]);
            gradLogger.log(i);
            grads[i] = grad;
            stats.addValue(grad);

            if(i % 1000 == 0){
                System.out.println(i);
            }
        }

        double[] sampleMean = stats.getMean();
        double[][] cov = stats.getCovariance().getData();

        RealMatrix dataMatrix = MatrixUtils.createRealMatrix(grads);
        RealMatrix rCovMatrix = dataMatrix.transpose().multiply(dataMatrix).scalarMultiply(1.0 / nSamples);
        double[][] rCov = rCovMatrix.getData();

        MultivariateNormalDistribution h0 = new MultivariateNormalDistribution(new double[paramDim], cov);
        MultivariateNormalDistribution h1 = new MultivariateNormalDistribution(sampleMean, rCov);

        double d = 0;

        for(double[] grad: grads){
            double h0LogLikelihood = h0.logDensity(grad);
            double h1LogLikelihood = h1.logDensity(grad);
            d += 2*(h1LogLikelihood - h0LogLikelihood);
        }

        ChiSquaredDistribution chiSq = new ChiSquaredDistributionImpl(paramDim);

        System.out.println("Sample mean");
        System.out.println(Arrays.toString(sampleMean));

        System.out.println("Sample covariance");
        System.out.println(stats.getCovariance().toString());

        System.out.println("Restricted covariance estimate");
        System.out.println(rCovMatrix.toString());

        System.out.println("Statistic");
        System.out.println(d);

        System.out.println("Degrees of freedom");
        System.out.println(chiSq.getDegreesOfFreedom());

        try {
            return 1.0 - chiSq.cumulativeProbability(d);
        } catch (MathException e) {
            System.out.println("Chi-squared p-value calculation failed");
            throw new RuntimeException(e);
        }

    }

    @Override
    public String getDescription() {
        return String.format("Score function validation test: %s and %s", simulator.getClass().getName(), likelihood.getClass().getName());
    }

    private Map<RealParameter, String> getParamInputNames(){
        return likelihood.getInputs().entrySet().stream()
                .filter(e -> e.getValue().get() instanceof RealParameter)
                .map(e -> new Pair<>((RealParameter) e.getValue().get(), e.getKey()))
                .filter(p -> parameters.contains(p.getKey()))
                .collect(Collectors.toMap(p -> p.getKey(), p -> p.getValue()));
    }

    @Override
    public void initAndValidate() {
        super.initAndValidate();

        likelihood = likelihoodInput.get();
        simulator = simulatorInput.get();
        parameters = parametersInput.get();

        if(parameters.size() < 1) throw new IllegalArgumentException("Empty parameter list");

        nSamples = nSamplesInput.get();
        stepSize = stepSizeInput.get();
        relativeStep = relativeStepInput.get();

        paramDim = 0;
        paramNames = new ArrayList<>();
        paramValues = new ArrayList<>();

        Map<RealParameter, String> paramInputNames = getParamInputNames();
        if(!paramInputNames.keySet().containsAll(parameters)) throw new IllegalArgumentException("Some parameters are not inputs of the likelihood");

        for(RealParameter p: parameters){
            if(p.getDimension() == 1){
                paramNames.add(paramInputNames.get(p));
                paramValues.add(p.getValue());
                paramDim++;
            } else {
                int dim = p.getDimension();
                String name = paramInputNames.get(p);
                for(int i = 0; i < dim; i++){
                    paramNames.add(String.format("%s_%d", name, i));
                    paramValues.add(p.getValue());
                }
                paramDim += dim;
            }
        }

    }

    private double[] calculateScoreFunction(TreeInterface tree){
        double[] grad = new double[paramDim];

        int i = 0;
        for(RealParameter p: parameters){

            for(int j = 0; j < p.getDimension(); j++){

                double step = relativeStep ? stepSize * paramValues.get(i) : stepSize;

                p.setValue(j, paramValues.get(i) - step);
                double fxmh = likelihood.calculateTreeLogLikelihood(tree);
                p.setValue(j, paramValues.get(i) + step);
                double fxph = likelihood.calculateTreeLogLikelihood(tree);
                grad[i] = (fxph - fxmh)/(2*step);

                p.setValue(j, paramValues.get(i));
                i++;
            }
        }

        return grad;
    }

    class Gradient extends RealParameter {
        @Override
        public void init(final PrintStream out) {
            for(int i = 0; i < paramDim; i++){
                out.print(paramNames.get(i) + "\t");
            }
        }
    }
}
