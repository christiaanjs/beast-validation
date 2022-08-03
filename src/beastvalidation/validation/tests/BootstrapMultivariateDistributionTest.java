package beastvalidation.validation.tests;

import beast.base.core.Input;
import beast.base.util.Randomizer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BootstrapMultivariateDistributionTest extends StatisticalTest {

    public Input<Integer> nBootsInput = new Input<>("nBoots", "Number of boostrap resamples", 1000, Input.Validate.OPTIONAL);
    public Input<Integer> printEveryInput = new Input<>("printEvery", "Print bootstrapping progress every", 100, Input.Validate.OPTIONAL);
    public Input<String> criterionInput = new Input<>("criterion", "Test criterion to use: ks (Kolmogorov-Smirnov) or cvm (Cramer-von Mises)", "ks", new String[]{ "ks", "cvm" });

    private String criterion;
    private int nBoots;
    private int printEvery;

    private boolean[][] pairwiseLess;
    private double statisticValue;
    private double[] bootStatistics;
    private int n1;
    private int n2;
    private int N;
    private int[] sample1Indices;
    private int[] sample2Indices;

    private boolean allLessEq(double[] x, double[] y){
        for(int i = 0; i < x.length; i++){
            if(x[i] > y[i]) return false;
        }
        return true;
    }

    private double calcStatistic(){
        double[] F1_all = new double[N];
        double[] F2_all = new double[N];

        for(int i = 0; i < N; i++){
            int[] sampleIndices = i < n1 ? sample1Indices : sample2Indices;
            int indexI = i < n1 ? i : i - n1;
            for(int j = 0; j < n1; j++){
                if(pairwiseLess[sample1Indices[j]][sampleIndices[indexI]]){
                    F1_all[i] += 1.0 / n1;
                }
            }
            for(int j = 0; j < n2; j++){
                if(pairwiseLess[sample2Indices[j]][sampleIndices[indexI]]){
                    F2_all[i] += 1.0 / n2;
                }
            }
        }

        double statisticValue = 0.0;
        if(criterion.equals("ks")){
            for(int i = 0; i < N; i++){
                statisticValue = Math.max(statisticValue, Math.abs(F1_all[i] - F2_all[i]));
            }
        } else if(criterion.equals("cvm")){
            for(int i = 0; i < N; i++){
                statisticValue += Math.pow(F1_all[i] - F2_all[i], 2.0);
            }
        }
        return statisticValue;
    }

    private void assignTrueSampleIndices() {
        for(int i = 0; i < N; i++){
            int[] sampleIndices = i < n1 ? sample1Indices : sample2Indices;
            int indexI = i < n1 ? i : i - n1;
            sampleIndices[indexI] = i;
        }
    }

    private void randomiseSampleIndices(){
        for(int i = 0; i < n1; i++){
            sample1Indices[i] = Randomizer.nextInt(N);
        }
        for(int i = 0; i < n2; i++){
            sample2Indices[i] = Randomizer.nextInt(N);
        }
    }

    @Override
    public void performTest(List<double[][]> values) {
        double[][] sample1 = values.get(0);
        double[][] sample2 = values.get(1);

        n1 = sample1.length;
        n2  = sample2.length;
        N = n1 + n2;

        pairwiseLess = new boolean[N][N];

        for(int i = 0; i < N; i++){
            for(int j = 0; j < N; j++){
                double[][] iSample = i < n1 ? sample1 : sample2;
                int iIndex = i < n1 ? i : i - n1;
                double[][] jSample = j < n1 ? sample1 : sample2;
                int jIndex = j < n1 ? j : j - n1;
                pairwiseLess[i][j] = allLessEq(iSample[iIndex], jSample[jIndex]);
            }
        }

        sample1Indices = new int[n1];
        sample2Indices = new int[n2];

        assignTrueSampleIndices();

        statisticValue = calcStatistic();

        bootStatistics = new double[nBoots];
        pValue = 0.0;
        for(int i = 0; i < nBoots; i++) {
            if(i % printEvery == 0) System.out.println("Bootstrap sample " + (i + 1));
            randomiseSampleIndices();
            bootStatistics[i] = calcStatistic();
            if(bootStatistics[i] > statisticValue){
                pValue += 1.0 / nBoots;
            }
        }

    }

    public double getStatistic(){
        return statisticValue;
    }

    @Override
    public StatisticalTestType getType() {
        return StatisticalTestType.TwoSample;
    }

    @Override
    public Map<String, String> getSummary() {
        Map<String, String> summary = new HashMap<>();
        summary.put("statistic", Double.toString(statisticValue));
        summary.put("bootStatistics", Arrays.toString(bootStatistics));
        return summary;
    }

    public void initAndValidate(){
        super.initAndValidate();
        criterion = criterionInput.get().toLowerCase();
        nBoots = nBootsInput.get();
        if(nBoots <= 1) throw new IllegalArgumentException("nBoots must be greater than 1");
        printEvery = printEveryInput.get();
        if(printEvery < 1) throw new IllegalArgumentException("printEvery must be 1 or greater");
    }
}
