package beast.simulation;

import master.BeastTreeFromMaster;

public class TreeSamplerFromMaster extends BeastTreeFromMaster implements Sampler {

    @Override
    public void nextState(int sampleNr) {
        initAndValidate();
    }
}
