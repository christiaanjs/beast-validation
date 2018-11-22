package beast.core;

import beast.evolution.tree.TreeSampler;
import beast.evolution.tree.Tree;
import beast.evolution.tree.TreeInterface;

public class TreeSamplerFromMCMC extends TreeSampler {

    public Input<MCMC> mcmcInput = new Input<>("mcmc", "MCMC sampler to draw from", Input.Validate.REQUIRED);
    public Input<TreeInterface> treeInput;

    @Override
    public Tree getNextTreeImpl() {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void initAndValidate() {
        throw new RuntimeException("Not implemented");
    }

}
