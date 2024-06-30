package beastvalidation.experimenter;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.BitSet;

import javax.imageio.ImageIO;

import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.core.Log;
import beast.base.evolution.tree.Tree;
import beast.base.core.Input.Validate;
import beast.base.inference.Runnable;
import beastfx.app.beauti.ThemeProvider;
import beastfx.app.tools.Application;
import beastfx.app.treeannotator.CladeSystem;
import beastfx.app.treeannotator.CladeSystem.Clade;
import beastfx.app.treeannotator.TreeAnnotator;
import beastfx.app.treeannotator.TreeAnnotator.TreeSet;
import beastfx.app.util.Alert;
import beastfx.app.util.OutFile;
import beastfx.app.util.TreeFile;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;

@Description("Calculate coverage of clade probabilities")
public class CladeCoverageCalculator extends Runnable {
	public Input<TreeFile> truthInput = new Input<>("truth", "tree file with true clade information",
			Validate.REQUIRED);
	public Input<File> logFilePrefixInput = new Input<>("prefix",
			"log file name without the number and '.log' missing. It is assumed there are as many log files as there are entries in the truth file",
			Validate.REQUIRED);
	final public Input<OutFile> outputInput = new Input<>("out", "output file, or stdout if not specified",
			new OutFile("[[none]]"));
	final public Input<Integer> burnInPercentageInput = new Input<>("burnin",
			"percentage of trees to used as burn-in (and will be ignored)", 10);
	final public Input<OutFile> pngFileInput = new Input<>("png", "name of file to write bar-chart plot",
			new OutFile("[[none]]"));
	final public Input<Integer> binCountInput = new Input<>("bins", "number of bins=bars to use for the chart", 10);

	final public Input<Integer> skipInput = new Input<>("skip", "number of trees in truth to skip", 1);

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
//		showCoveragePlot(new int[]{164, 13, 8, 16, 16, 15, 27, 23, 28, 22, 31, 33, 31, 42, 57, 44, 61, 97, 142, 4030}, 
//				new int[]{1232, 224, 141, 107, 91, 89, 83, 89, 72, 64, 69, 84, 70, 76, 86, 74, 97, 119, 162, 4046}, 
//				new File("/tmp/out.png"));
//		Platform.exit();
//		System.exit(0);

		PrintStream out = System.out;
		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			Log.warning("Writing to file " + outputInput.get().getPath());
			out = new PrintStream(outputInput.get());
		}

		int binCount = binCountInput.get();
		int[] truebins = new int[binCount];
		int[] totals = new int[truebins.length];

		TreeSet trueTrees = new TreeAnnotator().new MemoryFriendlyTreeSet(truthInput.get().getPath(), 0);
		int n = trueTrees.totalTrees - skipInput.get();
		trueTrees.reset();
		for (int i = 0; i < skipInput.get(); i++) {
			trueTrees.next();
		}

		int i = 0;
		int missedClades = 0;
		int totalClades = 0;
		while (trueTrees.hasNext()) {
			Tree trueTree = trueTrees.next();

			String filename = logFilePrefixInput.get().getPath() + i + ".trees";
			TreeSet estimatedTrees = new TreeAnnotator().new MemoryFriendlyTreeSet(filename,
					burnInPercentageInput.get());
			estimatedTrees.reset();
			CladeSystem clades = new CladeSystem();
			double treeCount = 0;
			Tree tree = null;
			while (estimatedTrees.hasNext()) {
				tree = estimatedTrees.next();
				if (treeCount == 0) {
					normalise(trueTree, tree);
				}
				clades.add(tree, false);
				treeCount++;
			}

			for (Clade clade : clades.getCladeMap().values()) {
				double p = clade.getCount() / treeCount;
				int b = (int) (totals.length * p);
				if (b >= totals.length) {
					b = totals.length - 1;
				}
				totals[b]++;
			}

			CladeSystem trueClades = new CladeSystem();
			trueClades.add(trueTree, false);

			for (BitSet bits : trueClades.getCladeMap().keySet()) {
				Clade clade = clades.getCladeMap().get(bits);
				totalClades++;
				if (clade == null) {
					StringBuilder b = new StringBuilder();
					int taxonCount = trueTree.getLeafNodeCount();
					for (int j = 0; j < taxonCount; j++) {
						if (bits.get(j * 2)) {
							b.append(tree.getNode(j).getID() + ",");
						}
					}
					b.deleteCharAt(b.length() - 1);
					Log.warning(i + ":" + bits.cardinality() + " taxa clade not found: " + b.toString());
					// put this in the zero probability bin
					truebins[0]++;
					missedClades++;
				} else {
					double p = clade.getCount() / treeCount;
					int b = (int) (truebins.length * p);
					if (b >= truebins.length) {
						b = truebins.length - 1;
					}
					truebins[b]++;
				}
			}

			i++;
		}

		System.out.println();
		System.out.println("totals: " + Arrays.toString(totals));
		System.out.println("true:   " + Arrays.toString(truebins));
		System.out.print("percentage: ");
		for (int x = 0; x < truebins.length; x++) {
			System.out.print((double) truebins[x] / totals[x]);
			if (x < truebins.length - 1) {
				System.out.print(", ");
			}
		}
		System.out.println("\n" + missedClades + " clades missed out of " + totalClades +" = " + (missedClades * 100.0 / totalClades) +"%");

		if (outputInput.get() != null && !outputInput.get().getName().equals("[[none]]")) {
			out.close();
		}

		if (pngFileInput.get() != null && !pngFileInput.get().getName().equals("[[none]]")) {
			showCoveragePlot(truebins, totals, pngFileInput.get());
		}

		Log.warning("\nDone");
		Platform.exit();
	}

	private void normalise(Tree trueTree, Tree tree) {
		int n = tree.getLeafNodeCount();
		for (int i = 0; i < n; i++) {
			beast.base.evolution.tree.Node node = tree.getNode(i);
			String name = node.getID();
			for (int j = 0; j < n; j++) {
				beast.base.evolution.tree.Node node2 = trueTree.getNode(i);
				if (node2.getID().equals(name)) {
					node2.setNr(i);
					break;
				}
			}
		}
	}

	static public void showCoveragePlot(int[] truebins, int[] totals, File pngfile) {
		// this initialised the javafx toolkit
		new JFXPanel();
		Platform.runLater(() -> {

			HBox root = new HBox();

			Scene scene = new Scene(root, 60 * totals.length, 60 * totals.length);
			CategoryAxis xAxis = new CategoryAxis();
			xAxis.setLabel("Inferred");

			NumberAxis yAxis = new NumberAxis();
			yAxis.setLabel("Actual");
			yAxis.setAutoRanging(false);
			yAxis.setLowerBound(0);
			yAxis.setUpperBound(100);

			BarChart barChart = new BarChart(xAxis, yAxis);
			barChart.setTitle("Clades true vs inferred ");
			barChart.setPrefSize(60 * totals.length - 77, 60 * totals.length - 52);

			XYChart.Series data = new XYChart.Series<String, Number>();

			for (int i = 0; i < truebins.length; i++) {
				data.getData().add(new XYChart.Data<>(
						(i * 100) / truebins.length + "-" + ((i + 1) * 100) / truebins.length + "\n" + totals[i],
						totals[i] > 0 ? 100.0 * truebins[i] / totals[i] : 0));
			}

			barChart.getData().add(data);
			barChart.setLegendVisible(false);
			barChart.setVerticalGridLinesVisible(false);

			root.getChildren().add(barChart);

			Dialog<Node> alert = new javafx.scene.control.Dialog<>();
			DialogPane pane = new DialogPane();
			pane.setContent(root);
			alert.setDialogPane(pane);
			alert.setHeaderText("coverage");
			alert.getDialogPane().getButtonTypes().addAll(Alert.CLOSED_OPTION);
			pane.setPrefHeight(60 * totals.length);
			pane.setPrefWidth(60 * totals.length);
			alert.setResizable(true);
			ThemeProvider.loadStyleSheet(alert.getDialogPane().getScene());

			SnapshotParameters param = new SnapshotParameters();
			param.setDepthBuffer(true);
			WritableImage snapshot = root.snapshot(param, null);
			BufferedImage tempImg = SwingFXUtils.fromFXImage(snapshot, null);
			try {
				Graphics g = tempImg.getGraphics();
				g.setColor(Color.black);
				g.drawLine(77,  60 * totals.length - (600-429), totals.length * 60 - (600-490), 52);
				ImageIO.write(tempImg, "png", new FileOutputStream(pngfile));
			} catch (IOException e) {
				e.printStackTrace();
			}

//		    try {
//			    System.out.println("To Printer!");
//	            PrinterJob job = PrinterJob.createPrinterJob();
//	            Stage primaryStage = new Stage();
//	            primaryStage.setScene(scene);
//	            if(job != null){
//		            job.showPrintDialog(primaryStage); 
//		            job.printPage(root);
//		            job.endJob();
//	            }
//		    } catch (Throwable e) {
//				e.printStackTrace();
//			}			
			// alert.showAndWait();
		});

	}

	public static void main(String[] args) throws Exception {
		new Application(new CladeCoverageCalculator(), "CladeCoverageCalculator", args);
	}

}
