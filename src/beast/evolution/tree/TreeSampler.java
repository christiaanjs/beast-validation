package beast.evolution.tree;

import beast.core.BEASTInterface;
import beast.core.BEASTObject;
import beast.core.StateNode;
import beast.evolution.tree.Tree;
import beast.evolution.tree.TreeInterface;
import org.w3c.dom.Node;

import java.io.PrintStream;

public abstract class TreeSampler extends StateNode {

    private Tree tree;

    protected abstract Tree getNextTreeImpl();

    public TreeInterface getNextTree(){
        tree = getNextTreeImpl();
        return tree;
    }

    @Override
    public void initAndValidate() {
        tree = new Tree();
    }

    @Override
    public void setEverythingDirty(boolean isDirty) {
        tree.setEverythingDirty(isDirty);
    }

    @Override
    public StateNode copy() {
        return tree.copy();
    }

    @Override
    public void assignTo(StateNode other) {
        tree.assignTo(other);
    }

    @Override
    public void assignFrom(StateNode other) {
        tree.assignFrom(other);
    }

    @Override
    public void assignFromFragile(StateNode other) {
        tree.assignFromFragile(other);
    }

    @Override
    public void fromXML(Node node) {
        tree.fromXML(node);
    }

    @Override
    public int scale(double scale) {
        return tree.scale(scale);
    }

    @Override
    protected void store() {
        tree.store();
    }

    @Override
    public void restore() {
        tree.restore();
    }

    @Override
    public int getDimension() {
        return tree.getDimension();
    }

    @Override
    public double getArrayValue() {
        return tree.getArrayValue();
    }

    @Override
    public double getArrayValue(int dim) {
        return tree.getArrayValue(dim);
    }

    @Override
    public void init(PrintStream out) {
        tree.init(out);
    }

    @Override
    public void close(PrintStream out) {
        tree.close(out);
    }
}
