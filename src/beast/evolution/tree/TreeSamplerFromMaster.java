package beast.evolution.tree;

import beast.evolution.tree.TreeSampler;
import beast.core.Input;
import beast.evolution.tree.Tree;
import beast.evolution.tree.TreeInterface;
import master.BeastTreeFromMaster;

public class TreeSamplerFromMaster extends BeastTreeFromMaster implements TreeSampler {

    @Override
    public void nextTree(int sampleNr) {
        initAndValidate();
    }
}
