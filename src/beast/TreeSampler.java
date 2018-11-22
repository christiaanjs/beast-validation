package beast;

import beast.core.BEASTInterface;
import beast.core.BEASTObject;
import beast.evolution.tree.TreeInterface;

public abstract class TreeSampler extends BEASTObject {
    public abstract TreeInterface getNextTree();
}
