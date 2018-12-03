package beast.simulation;

import master.BeastTreeFromMaster;

public class TreeSamplerFromMaster extends BeastTreeFromMaster implements TreeSampler {

    @Override
    public void nextTree(int sampleNr) {
        initAndValidate();
    }
}
