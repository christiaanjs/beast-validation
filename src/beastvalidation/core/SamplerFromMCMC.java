package beastvalidation.core;

import beast.base.inference.Logger;
import beast.base.inference.MCMC;
import beastvalidation.simulation.Sampler;

import java.io.IOException;

public class SamplerFromMCMC extends MCMC implements Sampler {

    @Override
    public void initAndValidate(){
        super.initAndValidate();
        posterior = posteriorInput.get();
        loggers = loggersInput.get();
        for (final Logger log : loggers) {
            try {
                log.init();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        for(int i = 0; i < burnIn; i++) propagateState(i); // TODO: Is sample number a problem?
    }

    @Override
    public void nextState(int sampleNr) {
        for(int i = 0; i < storeEvery; i++)
            propagateState(sampleNr);
    }
}
