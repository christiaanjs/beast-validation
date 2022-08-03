package beastvalidation.experimenter;



import beast.base.evolution.tree.Tree;
import beast.base.inference.Logger;
import beast.base.inference.parameter.RealParameter;
import beast.base.parser.NexusParser;
import beast.base.parser.XMLParserException;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.evolution.alignment.Alignment;
import beast.base.evolution.alignment.Sequence;
import beast.base.evolution.branchratemodel.StrictClockModel;
import beast.base.evolution.sitemodel.SiteModel;
import beast.base.evolution.substitutionmodel.Frequencies;
import beast.base.evolution.substitutionmodel.HKY;
import beastfx.app.seqgen.MergeDataWith;
import beastfx.app.seqgen.SequenceSimulator;
import beastfx.app.tools.Application;
import beastfx.app.tools.LogAnalyser;
import beastfx.app.util.LogFile;
import beastfx.app.util.TreeFile;
import beastfx.app.util.XMLFile;
import beastvalidation.core.*;
import beastvalidation.util.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

//import beagle.BeagleFlag;

@Description("Generate XML for performing coverage test (using CoverageCalculator)")
public class CoverageTestXMLGenerator extends beast.base.inference.Runnable {
	final public Input<File> workingDirInput = new Input<>("workingDir",
			"working directory where input files live and output directory is created");
	final public Input<String> outDirInput = new Input<>("outDir",
			"output directory where generated XML goes (as sub dir of working dir)", "mcmc");
	final public Input<LogFile> logFileInput = new Input<>("logFile",
			"trace log file containing model parameter values to use for generating sequence data");
	final public Input<TreeFile> treeFileInput = new Input<>("treeFile",
			"tree log file containing trees to generate sequence data on");
	final public Input<XMLFile> xmlFileInput = new Input<>("xmlFile",
			"XML template file containing analysis to be merged with generated sequence data");
	final public Input<Integer> skipLogLinesInput = new Input<>("skip", "numer of log file lines to skip", 1);
	final public Input<Integer> burnInPercentageInput = new Input<>("burnin",
			"percentage of trees to used as burn-in (and will be ignored)", 1);
	final public Input<Integer> siteCountInput = new Input<>("siteCount",
			"number of site to be generted in alignment", 1000);
	final public Input<Boolean> useGammaInput = new Input<>("useGamma", "use gamma rate heterogeneity", true);
	final public Input<Boolean> useClockInput = new Input<>("useClock", "use strict clock rate from file (defaults to 1)", true);

	int N = 100;
	List<Tree> trees;
	Double[][] f;
	Double[] kappa;
	Double[] shapes;
	Double[] clockRate;

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

		for (int i = 0; i < N; i++) {

			// set up model to draw samples from
			String[] taxa = trees.get(0).getTaxaNames();
			List<Sequence> seqs = new ArrayList<>();
			for (int j = 0; j < taxa.length; j++) {
				Sequence A = new Sequence();
				A.initByName("taxon", taxa[j], "value", "?");
				seqs.add(A);
			}

			Alignment data = new Alignment();
			data.initByName("sequence", seqs);

			Tree tree = trees.get(i);

			RealParameter freqs = new RealParameter(f[0][i] + " " + f[1][i] + " " + f[2][i] + " " + f[3][i]);
			Frequencies f = new Frequencies();
			f.initByName("frequencies", freqs);

			HKY hky = new beast.base.evolution.substitutionmodel.HKY();
			hky.initByName("frequencies", f, "kappa", kappa[i] + "");

			StrictClockModel clockmodel = new StrictClockModel();
			clockmodel.initByName("clock.rate", clockRate[i] + "");

			// change gammaCategoryCount=1 for generating without gamma rate
			// categories
			int gcc = (useGammaInput.get() ? 4 : 1);
			RealParameter p = new RealParameter("0.0");
			SiteModel sitemodel = new SiteModel();
			sitemodel.initByName("gammaCategoryCount", gcc, "substModel", hky, "shape", "" + shapes[i],
					"proportionInvariant", p);
			MergeDataWith mergewith = new MergeDataWith();
			mergewith.initByName("template", analysisXML, "output", dir + "/analysis-out" + i + ".xml");
			SequenceSimulator sim = new SequenceSimulator();
			sim.initByName("data", data, "tree", tree, "sequencelength", siteCountInput.get(), "outputFileName",
					"gammaShapeSequence.xml", "siteModel", sitemodel, "branchRateModel", clockmodel,
					"merge", mergewith);
			// produce gammaShapeSequence.xml and merge with analysis.xml to get
			// analysis-out.xml
			sim.run();
			System.err.print('.');
		}
		System.err.println();
	}

	public void run() throws Exception {
		String wdir = workingDirInput.get().getAbsolutePath() + "/";
		String traceFile = wdir + logFileInput.get().getPath();
		if (!new File(traceFile).exists()) {
			traceFile = logFileInput.get().getPath();
		}
		LogAnalyser trace = new LogAnalyser(traceFile, burnInPercentageInput.get(), true, false);

		N = trace.getTrace(0).length;

		List<String> labels = trace.getLabels();
		f = new Double[N][];
		int fIndex = getIndex(labels, "freq");
		if (fIndex > 0) {
			f[0] = trace.getTrace(fIndex);
			f[1] = trace.getTrace(fIndex + 1);
			f[2] = trace.getTrace(fIndex + 2);
			f[3] = trace.getTrace(fIndex + 3);
		} else {
			f[0] = new Double[N];
			f[1] = new Double[N];
			f[2] = new Double[N];
			f[3] = new Double[N];
			for (int i = 0; i < N; i++) {
				f[0][i] = 0.25;
				f[1][i] = 0.25;
				f[2][i] = 0.25;
				f[3][i] = 0.25;
			}
		}

		if (getIndex(labels, "kappa") > 0) {
			kappa = trace.getTrace(getIndex(labels, "kappa"));
		} else {
			// assume JC model
			kappa = new Double[N];
			for (int i  =0; i < N; i++) {
				kappa[i] = 1.0;
			}			
		}
		shapes = null;
		if (useGammaInput.get()) {
			shapes = trace.getTrace(getIndex(labels, "gammaShape"));
		} else {
			shapes = new Double[N];
			for (int i  =0; i < N; i++) {
				shapes[i] = 1.0;
			}
		}
		
		clockRate = null;
		if (useClockInput.get()) {
			clockRate = trace.getTrace(getIndex(labels, "clockRate"));
		} else {
			clockRate = new Double[N];
			for (int i  =0; i < N; i++) {
				clockRate[i] = 1.0;
			}
		}

		Logger.FILE_MODE = beast.base.inference.Logger.LogFileMode.overwrite;

		// set up flags for BEAGLE -- YMMV
//		long beagleFlags = BeagleFlag.VECTOR_SSE.getMask() | BeagleFlag.PROCESSOR_CPU.getMask();
//		System.setProperty("beagle.preferred.flags", Long.toString(beagleFlags));

		NexusParser parser = new beast.base.parser.NexusParser();
		String treeFile = wdir + treeFileInput.get().getName();
		if (!new File(treeFile).exists()) {
			treeFile = treeFileInput.get().getName();
		}		
		parser.parseFile(new File(treeFile));
		trees = parser.trees;
		int burnin = 0;
		while (trees.size() > N) {
			trees.remove(0);
			burnin++;
		}

		if (trees.size() + burnin != N * (100 + burnInPercentageInput.get()) / 100) {
			throw new RuntimeException("treeFile length != logFile length");
		}

		process();

		System.err.println("Done");

	}

	private int getIndex(List<String> labels, String prefix) {
		for (int i = 0; i < labels.size(); i++) {
			if (labels.get(i).startsWith(prefix)) {
				return i + 1;
			}
		}
		return 0;
	}

	public static void main(String[] args) throws Exception {
		new Application(new CoverageTestXMLGenerator(), "CoverageTestXMLGenerator", args);
	}

}
