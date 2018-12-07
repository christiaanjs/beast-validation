package beast.simulation;

import beast.core.StateNode;

public interface Sampler {
    public void nextState(int sampleNr);
}
