package beast.evolution.tree;

import beast.evolution.tree.TreeSampler;
import beast.core.Input;
import beast.evolution.tree.Tree;
import beast.evolution.tree.TreeInterface;
import master.BeastTreeFromMaster;

public class TreeSamplerFromMaster extends TreeSampler {

    public Input<BeastTreeFromMaster> simulationInput = new Input<>("simulation", "MASTER simulation", Input.Validate.REQUIRED);

    private BeastTreeFromMaster simulation;

    @Override
    public TreeInterface getNextTree() {
        Tree tree = new Tree();
        tree.initAndValidate();
        simulation.setInputValue(simulation.m_initial.getName(), tree);
        simulation.initAndValidate();
        return tree;
    }

    @Override
    public void initAndValidate() {
        simulation = simulationInput.get();
    }
}
