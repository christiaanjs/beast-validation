package beast.core;

import beast.evolution.tree.TreeSampler;
import beast.evolution.tree.Tree;
import beast.evolution.tree.TreeInterface;

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
