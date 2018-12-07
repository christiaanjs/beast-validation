package beast.validation.tests;

import beast.core.Input;

import java.util.List;
import java.util.Map;

public class BootstrapMultivariateDistributionTest extends StatisticalTest {

    public Input<Integer> nBootsInput = new Input<>("nBoots", "Number of boostrap resamples", 1000, Input.Validate.OPTIONAL);
    public Input<String> criterionInput = new Input<>("criterion", "Test criterion to use: ks (Kolmogorov-Smirnov) or cvm (Cramer-von Mises)", "ks", Input.Validate.REQUIRED);

    private boolean[][] pairwiseLess;

    private boolean allLessEq(double[] x, double[] y){
        for(int i = 0; i < x.length; i++){
            if(x[i] > y[i]) return false;
        }
        return true;
    }

    @Override
    public void performTest(List<double[][]> values) {
        double[][] sample1 = values.get(0);
        double[][] sample2 = values.get(1);

        int n1 = sample1.length;
        int n2  = sample2.length;
        int N = n1 + n2;

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

        throw new RuntimeException("Not implemented");

    }

    @Override
    public StatisticalTestType getType() {
        return StatisticalTestType.TwoSample;
    }

    @Override
    public Map<String, String> getSummary() {
        throw new RuntimeException("Not implemented");
    }
}
