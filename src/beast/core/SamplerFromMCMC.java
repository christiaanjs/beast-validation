package beast.core;

import beast.simulation.Sampler;

public class SamplerFromMCMC extends MCMC implements Sampler {

    @Override
    public void initAndValidate(){
        super.initAndValidate();
        System.out.println("Performing burn in");
        for(int i = 0; i < burnIn; i++) propagateState(i); // TODO: Is sample number a problem?
    }

    @Override
    public void nextState(int sampleNr) {
        System.out.println("Propagating state");
        for(int i = 0; i < storeEvery; i++)
            propagateState(sampleNr);
    }
}
