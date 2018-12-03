package beast.core;

import beast.simulation.TreeSampler;

public class TreeSamplerFromMCMC extends MCMC implements TreeSampler {

    @Override
    public void initAndValidate(){
        super.initAndValidate();
        for(int i = 0; i < burnIn; i++) propagateState(i); // TODO: Is sample number a problem?
    }

    @Override
    public void nextTree(int sampleNr) {
        for(int i = 0; i < storeEvery; i++)
            propagateState(sampleNr);
    }
}
