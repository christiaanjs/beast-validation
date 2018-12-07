package beast.validation.statistics;

import beast.core.Input;
import beast.evolution.tree.Node;
import com.sun.org.apache.xpath.internal.operations.Bool;

import java.io.PrintStream;
import java.util.Arrays;

public class UltrametricTreeStatistics extends TreeStatistics {

    public Input<Boolean> includeLeafCountInput = new Input<>("includeLeafCount", "Whether to include leaf count in input", true, Input.Validate.OPTIONAL);

    private double getTreeLength(){
        return Arrays.stream(tree.getNodesAsArray())
                .filter(Node::isRoot)
                .mapToDouble(n -> n.getParent().getHeight() - n.getHeight())
                .sum();
    }

    @Override
    public void updateStatistics(int sampleNr) {
        values[0] = getTreeLength();
        values[1] = tree.getRoot().getHeight();
        if(includeLeafCountInput.get())
            values[2] = tree.getLeafNodeCount();
    }

    @Override
    public int getDimension() {
        return includeLeafCountInput.get() ? 3 : 2;
    }

    @Override
    public void init(PrintStream out) {
        out.print("treeLength\t rootHeight");
        if(includeLeafCountInput.get())
            out.print("\trootHeight");
    }
}
