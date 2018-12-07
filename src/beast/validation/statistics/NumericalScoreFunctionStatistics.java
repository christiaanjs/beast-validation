package beast.validation.statistics;

import beast.core.Distribution;
import beast.core.Input;
import beast.core.parameter.RealParameter;
import beast.evolution.speciation.SpeciesTreeDistribution;
import javafx.util.Pair;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NumericalScoreFunctionStatistics extends Statistics {

    public Input<Double> stepSizeInput = new Input<>("stepSize", "Step size to use when calculating gradient", Math.sqrt(Math.ulp(1.0)), Input.Validate.OPTIONAL);
    public Input<Boolean> relativeStepInput = new Input<>("relativeStep", "Whether to use relative step when calculating gradient", true, Input.Validate.OPTIONAL);
    public Input<Distribution> likelihoodInput = new Input<>("likelihood", "Likelihood to be tested", Input.Validate.REQUIRED);
    public Input<List<RealParameter>> parametersInput = new Input<>("parameter", "Parameters to be included in validation", new ArrayList<>(), Input.Validate.REQUIRED);

    private Distribution likelihood;
    private List<RealParameter> parameters;

    private double stepSize;
    private boolean relativeStep;

    private int paramDim;
    private List<String> paramNames;
    private List<Double> paramValues;

    @Override
    public void updateStatistics(int sampleNr) {
        int i = 0;
        for(RealParameter p: parameters){

            for(int j = 0; j < p.getDimension(); j++){

                double step = relativeStep ? stepSize * paramValues.get(i) : stepSize;

                p.setValue(j, paramValues.get(i) - step);
                double fxmh = likelihood.calculateLogP();
                p.setValue(j, paramValues.get(i) + step);
                double fxph = likelihood.calculateLogP();
                values[i] = (fxph - fxmh)/(2*step);

                p.setValue(j, paramValues.get(i));
                i++;
            }
        }
    }

    @Override
    public void init(PrintStream out) {
        for(int i = 0; i < paramDim; i++){
            out.print(paramNames.get(i) + "\t");
        }
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

        likelihood = likelihoodInput.get();
        parameters = parametersInput.get();

        if(parameters.size() < 1) throw new IllegalArgumentException("Empty parameter list");

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

        super.initAndValidate();
    }

    @Override
    public int getDimension() {
        return paramDim;
    }
}
