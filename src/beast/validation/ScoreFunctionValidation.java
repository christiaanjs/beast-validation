package beast.validation;

import beast.TreeSimulator;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.parameter.RealParameter;
import beast.evolution.speciation.SpeciesTreeDistribution;
import beast.evolution.tree.Tree;
import beast.evolution.tree.TreeInterface;
import javafx.util.Pair;
import org.omg.SendingContext.RunTime;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ScoreFunctionValidation extends StochasticValidationTest {

    public ScoreFunctionValidation(){}

    public ScoreFunctionValidation(SpeciesTreeDistribution likelihood, TreeSimulator simulator, List<RealParameter> parameters){
        setInputValue(likelihoodInput.getName(), likelihood);
        setInputValue(simulatorInput.getName(), simulator);
        setInputValue(parametersInput.getName(), parameters);
    }

    private static final int DEFAULT_N_SAMPLES = 1000000;
    private static final double DEFAULT_H = 1e-16;

    public Input<SpeciesTreeDistribution> likelihoodInput = new Input<>("likelihood", "Likelihood to be tested", Input.Validate.REQUIRED);
    public Input<TreeSimulator> simulatorInput = new Input<>("treeSimulation", "Simulator to be tested", Input.Validate.REQUIRED);
    public Input<List<RealParameter>> parametersInput = new Input<>("parameters", "Parameters to be included in validation", Input.Validate.REQUIRED);

    public Input<Integer> nSamplesInput = new Input<>("nSamples", "Number of samples to use", DEFAULT_N_SAMPLES, Input.Validate.OPTIONAL);
    public Input<Double> hInput = new Input<>("h", "Step size to use when calculating gradient", DEFAULT_H, Input.Validate.OPTIONAL);

    private SpeciesTreeDistribution likelihood;
    private TreeSimulator simulator;
    private List<RealParameter> parameters;

    private int nSamples;
    private double h;

    private int paramDim;
    private List<String> paramNames;
    private List<Double> paramValues;

    @Override
    public double performTest() {
        throw new RuntimeException("Not implemented");
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

        nSamples = nSamplesInput.get();
        h = hInput.get();

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
                p.setValue(j, paramValues.get(i) - h);
                double fxmh = likelihood.calculateTreeLogLikelihood(tree);
                p.setValue(j, paramValues.get(i) + h);
                double fxph = likelihood.calculateTreeLogLikelihood(tree);
                grad[i] = (fxph - fxmh)/(2*h);

                p.setValue(j, paramValues.get(i));
                i++;
            }
        }

        return grad;
    }
}
