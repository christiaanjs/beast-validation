package beastvalidation.simulation;

import beast.base.core.BEASTInterface;
import beast.base.core.Input;
import beast.base.inference.Operator;
import beast.base.inference.StateNode;

import java.util.ArrayList;
import java.util.List;

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

    public List<StateNode> listStateNodes() {
        List<StateNode> stateNodes = super.listStateNodes();
        stateNodes.remove(sampler);
        return stateNodes;
    }
}
