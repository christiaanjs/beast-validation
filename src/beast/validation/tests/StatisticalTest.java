package beast.validation.tests;

import beast.core.BEASTObject;
import beast.core.Loggable;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class StatisticalTest extends BEASTObject implements Loggable {

    protected double pValue;
    public abstract void performTest(List<double[][]> values);

    public double getPValue(){
        return pValue;
    }

    private int summarySize;
    private String[] keys;
    private String[] values;

    public abstract StatisticalTestType getType();

    public abstract Map<String, String> getSummary();

    @Override
    public void initAndValidate() {

    }

    @Override
    public void init(PrintStream out){
        out.print("key\tvalue\t");
        Map<String, String> summary = getSummary();
        summarySize = summary.size();

        keys = new String[summarySize];

        keys = summary.keySet().toArray(keys);

        values = new String[summarySize];
        for(int i = 0; i < summarySize; i++){
            out.print(values[i]);
            out.print("\t");
            values[i] = summary.get(keys[i]);
        }

        out.print("pValue");
    }


    @Override
    public void log(long sample, PrintStream out){
        for(int i = 0; i < summarySize; i++){
            out.print(values[i]);
            out.print("\t");
        }
        out.print(pValue);
    }

    @Override
    public void close(PrintStream out) {

    }
}
