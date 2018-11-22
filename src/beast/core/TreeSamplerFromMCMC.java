package beast.core;

import beast.TreeSampler;
import beast.core.Input;
import beast.core.MCMC;
import beast.evolution.tree.TreeInterface;

public class TreeSamplerFromMCMC extends TreeSampler {

    public Input<MCMC> mcmcInput = new Input<>("mcmc", "MCMC sampler to draw from", Input.Validate.REQUIRED);
    public Input<TreeInterface> treeInput;

    @Override
    public TreeInterface getNextTree() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void initAndValidate() {
        throw new RuntimeException("Not implemented");
    }
}
