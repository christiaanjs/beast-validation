package beast.core;

import beast.evolution.tree.TreeSampler;
import beast.evolution.tree.Tree;
import beast.evolution.tree.TreeInterface;

public class TreeSamplerFromMCMC extends MCMC implements TreeSampler {

    @Override
    public void nextTree(int sampleNr) {
        propagateState(sampleNr);
    }
}
