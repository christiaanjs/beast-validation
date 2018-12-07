package beast.simulation;

import beast.core.StateNode;
import master.BeastTreeFromMaster;

public class TreeSamplerFromMaster extends BeastTreeFromMaster implements StateNodeSampler {

    @Override
    public void nextState(int sampleNr) {
        initAndValidate();
    }

    @Override
    public StateNode getStateNode() {
        return this;
    }
}
