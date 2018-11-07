package beast.validation;

import beast.TreeSimulator;
import beast.core.Input;
import beast.core.Runnable;
import beast.core.parameter.RealParameter;
import beast.evolution.speciation.SpeciesTreeDistribution;

import java.util.List;

public class ScoreFunctionValidation extends Runnable {

    private static final int DEFAULT_N_SAMPLES = 1000000;
    private static final double DEFAULT_H = 1e-16;

    public Input<List<RealParameter>> parametersInput = new Input<>("parameters", "Parameters to be included in validation", Input.Validate.REQUIRED);
    public Input<SpeciesTreeDistribution> likelihoodInput = new Input<>("likelihood", "Likelihood to be tested", Input.Validate.REQUIRED);
    public Input<TreeSimulator> simulatorInput = new Input<>("treeSimulation", "Simulator to be tested", Input.Validate.REQUIRED);

    public Input<Integer> nSamplesInput = new Input<>("nSamples", "Number of samples to use", DEFAULT_N_SAMPLES, Input.Validate.OPTIONAL);
    public Input<Double> hInput = new Input<>("h", "Step size to use when calculating gradient", DEFAULT_H, Input.Validate.OPTIONAL);

    @Override
    public void run() throws Exception {
        
    }

    @Override
    public void initAndValidate() {

    }
}
