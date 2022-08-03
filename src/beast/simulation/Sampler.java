package beast.simulation;

import beast.base.inference.StateNode;

public interface Sampler {
    public void nextState(int sampleNr);
}
