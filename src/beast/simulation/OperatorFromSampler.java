package beast.simulation;

import beast.core.Input;
import beast.core.Operator;
import beast.core.StateNode;

public class OperatorFromSampler extends Operator {

    public Input<StateNode> operandInput = new Input<>("operand", "StateNode that operator acts on", Input.Validate.REQUIRED);
    public Input<StateNodeSampler> samplerInput = new Input<>("sampler", "Sampler used for proposal", Input.Validate.REQUIRED);

    private StateNodeSampler sampler;
    private StateNode samplerState;
    private StateNode operand;

    @Override
    public double proposal() {
        sampler.nextState(0);
        operand.assignFromFragile(samplerState);
        return 0; // Global operator is symmetric
    }

    @Override
    public void initAndValidate() {
        sampler = samplerInput.get();
        samplerState = sampler.getStateNode();
        operand = operandInput.get();
    }
}