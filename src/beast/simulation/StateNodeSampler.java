package beast.simulation;

import beast.base.inference.StateNode;

public interface StateNodeSampler extends Sampler {
    public StateNode getStateNode();
}
