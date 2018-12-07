package beast.validation.statistics;

import beast.evolution.tree.Tree;

public abstract class TreeStatistics extends Statistics {

    protected Tree tree;

    public void initAndValidate(){
        super.initAndValidate();

        if(stateNode instanceof Tree){
            tree = (Tree) stateNode;
        } else {
            throw new IllegalArgumentException("State must be a tree");
        }

    }
}
