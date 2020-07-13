package beast.experimenter;

import java.io.File;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import beast.app.util.Application;
import beast.app.util.OutFile;
import beast.app.util.TreeFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.core.Runnable;
import beast.core.util.Log;
import beast.evolution.tree.Node;
import beast.evolution.tree.Tree;
import beast.util.HeapSort;
import beast.util.NexusParser;

@Description("Compare meta data from a tree with meta data of an MCC tree")
public class MCCTreeComparator extends Runnable {
	final public Input<TreeFile> src1Input = new Input<>("tree", "source tree file with meta data", Validate.REQUIRED);
	final public Input<TreeFile> src2Input = new Input<>("mcc", "MCC source tree file", Validate.REQUIRED);
	final public Input<Integer> fromInput = new Input<>("from", "start value to loop over", 0);
	final public Input<Integer> toInput = new Input<>("to", "end value (inclusive) to loop over. If less than 0, no loop is performed. If more than 0, the part $(n) in the file path will be replaced by an integer, starting at "
			+ "'from' and ending in 'to'", 99);

	final public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified",
			new OutFile("[[none]]"));

	PrintStream out;
	
	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		long start = System.currentTimeMillis();
		out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			String str = outputInput.get().getPath();
			Log.warning("Writing to file " + str);
			out = new PrintStream(str);
		}
		map = new LinkedHashMap<>();
		map.put("posterior", new MatchCounter("posterior"));

		if (toInput.get() < 0 || fromInput.get() < 0) {
			process(src1Input.get(), src2Input.get());
		} else {
			String tree1path = src1Input.get().getPath();
			String tree2path = src2Input.get().getPath();
			for (int i = fromInput.get(); i <= toInput.get(); i++) {
				File f1 = new File(tree1path.replaceAll("\\$\\(n\\)", i+""));
				File f2 = new File(tree2path.replaceAll("\\$\\(n\\)", i+""));
				process(f1, f2);
			}
		}

		report();
		
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			out.close();
		}
		long end = System.currentTimeMillis();
		System.err.println("All done in " + (end - start) / 1000 + " seconds");
	}

	private void report() {
		String [] keys = map.keySet().toArray(new String[]{});
		Arrays.sort(keys);
		out.println("metadata \tTotal matches\tMissers\tLeaf branch matches\tMissers\tInternal branch matches\tmissers"); 
		for (String metadata : keys) {
			MatchCounter m = map.get(metadata);
			out.println(metadata + "\t" + m.matchCount + "\t" + m.missMatchCount + 
					"\t" + m.leafMatchCount + "\t" + m.leafMissMatchCount + 
					"\t" + (m.matchCount-m.leafMatchCount) + "\t" + (m.missMatchCount - m.leafMissMatchCount) 
					);
		}
		

		out.println();
		out.println("metadata \t% matches\t% leaf branch matches\t% internal branch matches"); 
		DecimalFormat f = new DecimalFormat("#.##");
		for (String metadata : keys) {
			MatchCounter m = map.get(metadata);
			double percentMatches =  100.0*(m.matchCount + 0.0) /(m.matchCount + m.missMatchCount);
			double percentLBMatches = 100.0*(m.leafMatchCount + 0.0) /(m.leafMatchCount + m.leafMissMatchCount);
			double percentIBMatches = 100.0*(m.matchCount-m.leafMatchCount + 0.0) /(m.matchCount-m.leafMatchCount + m.missMatchCount-m.leafMissMatchCount);
			
			out.println(metadata + "\t" +  f.format(percentMatches) + "\t" + 
					(Double.isNaN(percentLBMatches) ? "N/A" : f.format(percentLBMatches)) + "\t" + 
					f.format(percentIBMatches)); 
		}

	}

	private void process(File tree1, File tree2) throws Exception {
		
		NexusParser p = new NexusParser();
		p.parseFile(tree1);
		if (p.trees.size() != 1) {
			throw new IllegalArgumentException("Expected only 1 tree in file " + tree1.getPath());
		}
		Tree tree = p.trees.get(0);
		p.parseFile(tree2);
		if (p.trees.size() != 1) {
			throw new IllegalArgumentException("Expected only 1 tree in file " + tree2.getPath());
		}
		Tree mcc = p.trees.get(0);
		
		// make sure topologies match
		tree.getRoot().sort();
		mcc.getRoot().sort();
		String newick1 = tree.getRoot().toNewick(true);
		String newick2 = mcc.getRoot().toNewick(true);
		if (!newick1.equals(newick2)) {
			throw new IllegalArgumentException("topologies do not match");
		}
		
		traverse(tree.getRoot(), mcc.getRoot());
	}

	Map<String, MatchCounter> map;
	
	class MatchCounter {
		String trait;
		int matchCount;
		int missMatchCount;
		int leafMatchCount;
		int leafMissMatchCount;
		
		MatchCounter(String trait) {
			this.trait = trait;
			matchCount = 0;
			missMatchCount = 0;
			leafMatchCount = 0;
			leafMissMatchCount = 0;
		}
	}
	
	
	private void traverse(Node node, Node nodeMCC) {
		node.setMetaData("height", node.getHeight());
		
		// deal with posterior
		Object posterior = nodeMCC.getMetaData("posterior");
		if (posterior != null) {
			MatchCounter matchCounter = map.get("posterior");
			Double value = (Double) posterior;
			if (value >= 0.05) {
				matchCounter.matchCount++;
				if (node.isLeaf()) {
					matchCounter.leafMatchCount++;
				}
			} else {
				matchCounter.missMatchCount++;
				if (node.isLeaf()) {
					matchCounter.leafMissMatchCount++;
				}
			}
		}
		
		// deal with other metadata
		for (String metadata : node.getMetaDataNames()) {
			if (!map.containsKey(metadata)) {
				map.put(metadata, new MatchCounter(metadata));
			}
			MatchCounter matchCounter = map.get(metadata);
			if (!metadata.equals("height") || !node.isLeaf()) {
			Object o = node.getMetaData(metadata);
			if (o instanceof Double) {
				Object range = nodeMCC.getMetaData(metadata + "_95%_HPD");
				if (range != null) {
					Double [] values = (Double []) range;
					Double value = (Double) o;
					if (value >= values[0] && value < values[1]) {
						matchCounter.matchCount++;
						if (node.isLeaf()) {
							matchCounter.leafMatchCount++;
						}
					} else {
						matchCounter.missMatchCount++;
						if (node.isLeaf()) {
							matchCounter.leafMissMatchCount++;
						}
					}
				}
			} else  if (o instanceof String) {
				Object set = nodeMCC.getMetaData(metadata + ".set");
				if (set != null) {
					String [] values = (String []) set;
					Double [] probs = (Double []) nodeMCC.getMetaData(metadata + ".set.prob");
					if (isInCredibleSet(values, probs, (String)o)) {
						matchCounter.matchCount++;
						if (node.isLeaf()) {
							matchCounter.leafMatchCount++;
						}
					} else {
						matchCounter.missMatchCount++;
						if (node.isLeaf()) {
							matchCounter.leafMissMatchCount++;
						}
					}
				}
			}
			}
		}
		
		if (node.isLeaf()) {
			// sanity check
			if (!node.getID().equals(nodeMCC.getID())) {
				throw new IllegalArgumentException("leaf nodes do not match");
			}
		} else {
			traverse(node.getLeft(), nodeMCC.getLeft());
			traverse(node.getRight(), nodeMCC.getRight());
		}
		
		
	}

	private boolean isInCredibleSet(String[] values, Double[] probs, String o) {
		int i = 0;
		boolean found = false;
		while (!found && i < values.length) {
			if (values[i].equals(o)) {
				found = true;
				break;
			}
			i++;
		}
		if (!found) {
			return false;
		}
		
		// calculate credible set
		int [] index = new int[values.length];
		double [] probs2 = new double[probs.length];
		for (int j = 0; j < probs2.length; j++) {
			probs2[j] = probs[j];
		}
		HeapSort.sort(probs2, index);
		
		i= index.length - 1;
		double cumProb = 0;
		while (i >= 0 && cumProb < 0.95) {
			if (o.equals(values[index[i]])) {
				return true;
			}
			cumProb += probs2[index[i]];
			i--;
		}
				
		return false;
	}

	public static void main(String[] args) throws Exception {
		new Application(new MCCTreeComparator(), "Clade Set Comparator", args);

	}

}
