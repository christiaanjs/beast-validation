package beast.validation.statistics;

import beast.core.Input;
import beast.core.StateNode;
import beast.core.parameter.RealParameter;
import beast.evolution.tree.Tree;
import org.w3c.dom.Node;

import java.io.PrintStream;

public abstract class Statistics extends RealParameter {
    public Input<Tree> treeInput = new Input<>("tree", "The tree to compute statistics on", Input.Validate.REQUIRED);

    protected Tree tree;

    public void initAndValidate(){
        tree = treeInput.get();
    }

    public abstract void updateStatistics(int sampleNr);

}
