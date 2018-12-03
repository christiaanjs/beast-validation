package beast.validation.statistics;

import beast.core.Input;
import beast.core.Loggable;
import beast.core.StateNode;
import beast.core.parameter.RealParameter;
import beast.evolution.tree.Tree;
import org.w3c.dom.Node;

import java.io.PrintStream;

public abstract class Statistics extends StateNode implements Loggable {
    public Input<Tree> treeInput = new Input<>("tree", "The tree to compute statistics on", Input.Validate.REQUIRED);

    protected Tree tree;
    protected double[] values;
    private double[] storedValues;

    public void initAndValidate(){
        tree = treeInput.get();
        values = new double[getDimension()];
        storedValues = new double[getDimension()];
    }

    public abstract void updateStatistics(int sampleNr);

    @Override
    public void setEverythingDirty(boolean isDirty) {

    }

    @Override
    public StateNode copy() {
        return null;
    }

    @Override
    public void assignTo(StateNode other) {
        Statistics otherS = (Statistics) other;
        otherS.tree = tree;
        otherS.values = values;

    }

    @Override
    public void assignFrom(StateNode other) {
        Statistics otherS = (Statistics) other;
        tree = otherS.tree;
        values = otherS.values;

    }

    @Override
    public void assignFromFragile(StateNode other) {
        Statistics otherS = (Statistics) other;
        values = otherS.values;
    }

    @Override
    public void fromXML(Node node) {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public int scale(double scale) {
        throw new RuntimeException("Operators should not be used on summary statistics");
    }

    @Override
    protected void store() {
        System.arraycopy(values, 0, storedValues, 0, getDimension());
    }

    @Override
    public void restore() {
        System.arraycopy(storedValues, 0, values, 0, getDimension());
    }

    public double[] getArrayValues(){
        return values;
    }

    @Override
    public double getArrayValue() {
        return values[0];
    }

    @Override
    public double getArrayValue(int dim) {
        return values[dim];
    }

    @Override
    public void log(final long sample, final PrintStream out) {
        for(int i = 0; i < getDimension(); i++){
            out.print(values[i]);
            out.print("\t");
        }
    }

    @Override
    public void close(PrintStream out) {

    }
}
