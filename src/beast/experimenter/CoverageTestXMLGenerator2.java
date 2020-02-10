package beast.experimenter;

import beast.evolution.tree.Tree;
import beast.util.*;
import beast.app.beauti.BeautiDoc;
import beast.app.util.Application;
import beast.app.util.*;
import beast.core.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import beagle.BeagleFlag;

@Description("Generate XML for performing coverage test (using CoverageCalculator) with SimulatedAlignments")
public class CoverageTestXMLGenerator2 extends beast.core.Runnable {
	final public Input<File> workingDirInput = new Input<>("workingDir",
			"working directory where input files live and output directory is created");
	final public Input<String> outDirInput = new Input<>("outDir",
			"output directory where generated XML goes (as sub dir of working dir)", "mcmc");
	final public Input<LogFile> logFileInput = new Input<>("logFile",
			"trace log file containing model parameter values to use for generating sequence data");
	final public Input<TreeFile> treeFileInput = new Input<>("treeFile",
			"tree log file containing trees to generate sequence data on");
	final public Input<File> geneTreeFileInput = new Input<>("geneTreeFile",
			"configueation file with gene tree identifiers and log file names, one per line separated by a tab,"
			+ "for example \"gene1\t/xyz/abc/gene1.tree\ngene2\t/xyz/abc/gene2.tree\n");
	final public Input<XMLFile> xmlFileInput = new Input<>("xmlFile",
			"XML template file containing analysis to be merged with generated sequence data");
	final public Input<Integer> skipLogLinesInput = new Input<>("skip", "numer of log file lines to skip", 1);

	int N = 100;
	List<Tree> trees;
	String [] geneTreeNames;
	List<Tree> [] geneTrees;
	LogAnalyser trace;
	List<String> traceLabels;
	
	@Override
	public void initAndValidate() {
	}
	
	void process() throws IllegalArgumentException, IllegalAccessException, IOException, XMLParserException {
		String wdir = workingDirInput.get().getAbsolutePath() + "/";
		String dir = wdir + outDirInput.get();
		String analysisXML = wdir + xmlFileInput.get().getAbsolutePath();
		if (!new File(analysisXML).exists()) {
			analysisXML = xmlFileInput.get().getAbsolutePath();
		}

		if (!new File(analysisXML).exists()) {
			throw new IllegalArgumentException("Could not find template XML at " + wdir + analysisXML + " or " + analysisXML);
		}
		
		
		if (!(new File(dir).exists())) {
			new File(dir).mkdirs();
		}
		System.err.print("Processing " + dir);

		int m = traceLabels.size();
		int skip = skipLogLinesInput.get();
		
		
		for (int i = 0; i < N; i++) {
			String xml = BeautiDoc.load(analysisXML);
			
			// replace species tree
			Tree tree = trees.get(i);
			xml = xml.replaceAll("\\$\\(tree\\)", tree.getRoot().toNewick());
			
			// replace gene trees
			for (int j = 0; j < geneTrees.length; j++) {
				xml = xml.replaceAll("\\$\\(" + geneTreeNames[j] + "\\)", geneTrees[j].get(i).getRoot().toNewick());
			}
			
			// replace parameters
			for (int j = 0; j < m; j++) {				
				xml = xml.replaceAll("\\$\\(" + traceLabels.get(j) + "\\)", toString(trace.getTrace(j+1)[i + skip]));
			}
			
			// produce XML
	        FileWriter outfile = new FileWriter(dir + "/analysis-out" + i + ".xml");
	        outfile.write(xml);
	        outfile.close();
			
			System.err.print('.');
		}
		System.err.println();
	}

	private String toString(Double x) {
		String str = x.toString();
		if (str.endsWith(".0")) {
			str = str.substring(0, str.length() - 2);
		}
		return str;
	}

	@Override
	public void run() throws Exception {
		String wdir = workingDirInput.get().getAbsolutePath() + "/";
		String traceFile = wdir + logFileInput.get().getPath();
		if (!new File(traceFile).exists()) {
			traceFile = logFileInput.get().getPath();
		}
		trace = new LogAnalyser(traceFile, 0, true, false);

		N = trace.getTrace(0).length - skipLogLinesInput.get();

		traceLabels = trace.getLabels();

		Logger.FILE_MODE = beast.core.Logger.LogFileMode.overwrite;

		// set up flags for BEAGLE -- YMMV
		long beagleFlags = BeagleFlag.VECTOR_SSE.getMask() | BeagleFlag.PROCESSOR_CPU.getMask();
		System.setProperty("beagle.preferred.flags", Long.toString(beagleFlags));

		
		String treeFile = wdir + treeFileInput.get().getName();
		if (!new File(treeFile).exists()) {
			treeFile = treeFileInput.get().getName();
		}		
		trees = getTrees(treeFile);
		

		if (geneTreeFileInput.get() != null) {
			String geneTreeFile = wdir + geneTreeFileInput.get().getPath();
			if (!new File(geneTreeFile).exists()) {
				geneTreeFile = geneTreeFileInput.get().getPath();
			}	
			String [] geneTreeFiles = BeautiDoc.load(geneTreeFile).split("\n");
			
			geneTrees = new List[geneTreeFiles.length];
			geneTreeNames = new String[geneTreeFiles.length];
			
			for (int i = 0; i < geneTrees.length; i++) {
				String [] str = geneTreeFiles[i].trim().split("\\t");
				geneTreeNames[i] = str[0];
				geneTrees[i] = getTrees(str[1]);
			}		
		} else {
			geneTrees = new List[0];
		}
		
		process();

		System.err.println("Done");

	}

	private List<Tree> getTrees(String treeFile) throws IOException {
		NexusParser parser = new beast.util.NexusParser();
		parser.parseFile(new File(treeFile));
		List<Tree> trees = parser.trees;
		int burnin = 0;
		while (trees.size() > N) {
			trees.remove(0);
			burnin++;
		}

		if (trees.size() + burnin != N + skipLogLinesInput.get()) {
			throw new RuntimeException("treeFile length != logFile length for file " + treeFile);
		}
		return trees;
	}

	public static void main(String[] args) throws Exception {
		new Application(new CoverageTestXMLGenerator2(), "CoverageTestXMLGenerator2", args);
	}

}