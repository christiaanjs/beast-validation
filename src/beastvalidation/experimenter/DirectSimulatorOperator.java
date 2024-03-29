package beastvalidation.experimenter;

import java.util.ArrayList;
import java.util.List;

import beast.base.core.Description;
import beast.base.inference.DirectSimulator;
import beast.base.core.Input;
import beast.base.core.Input.Validate;
import beast.base.inference.Operator;
import beast.base.inference.State;
import beast.base.inference.StateNode;

@Description("Do proposal using DirectSimulator")
public class DirectSimulatorOperator extends Operator {
	final public Input<DirectSimulator> simulatorInput = new Input<>("simulator", "simulator that produces direct samples from the targe distribution", Validate.REQUIRED);
	final public Input<State> stateInput = new Input<>("state", "state to sample from", Validate.REQUIRED);

	DirectSimulator simulator;
	State state;
	
	@Override
	public void initAndValidate() {
		simulator = simulatorInput.get();
		state = stateInput.get();
		state.initialise();
		int nSamples = simulator.nSamplesInput.get();
		if (nSamples != 1) {
			throw new IllegalArgumentException("simulator should have nSamples=1 (not " + nSamples + ")");
		}
	}

	@Override
	public double proposal() {
		double logP = state.robustlyCalcPosterior(simulator.distributionInput.get());
		try {
			simulator.run();
		} catch (Exception e) {
			e.printStackTrace();
			return Double.NEGATIVE_INFINITY;
		}
		double logP2 = state.robustlyCalcPosterior(simulator.distributionInput.get());
		return logP - logP2;
		// return 0.0;
	}
	
	@Override
	public List<StateNode> listStateNodes() {
        final List<StateNode> list = new ArrayList<>();
        list.addAll(stateInput.get().stateNodeInput.get());
        return list;
	}

}
