package beast.simulation;

import beast.TreeSimulator;
import beast.core.Input;
import beast.evolution.tree.Tree;
import beast.evolution.tree.TreeInterface;
import master.BeastTreeFromMaster;
import master.InheritanceTrajectory;

public class TreeSimulatorFromMaster extends TreeSimulator {

    public Input<BeastTreeFromMaster> simulationInput = new Input<>("simulation", "MASTER simulation", Input.Validate.REQUIRED);

    private BeastTreeFromMaster simulation;

    @Override
    public TreeInterface getNextTree() {
        Tree tree = new Tree();
        simulation.setInputValue(simulation.m_initial.getName(), tree);
        simulation.initAndValidate();
        return tree;
    }

    @Override
    public void initAndValidate() {
        simulation = simulationInput.get();
    }
}
