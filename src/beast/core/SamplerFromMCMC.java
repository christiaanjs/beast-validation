package beast.core;

import beast.simulation.Sampler;

public class SamplerFromMCMC extends MCMC implements Sampler {

    @Override
    public void initAndValidate(){
        super.initAndValidate();
        for(int i = 0; i < burnIn; i++) propagateState(i); // TODO: Is sample number a problem?
    }

    @Override
    public void nextState(int sampleNr) {
        for(int i = 0; i < storeEvery; i++)
            propagateState(sampleNr);
    }
}
