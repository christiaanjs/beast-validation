package beast.experimenter;


import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;

import org.apache.commons.math3.distribution.RealDistribution;
import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.apache.commons.math3.exception.MathArithmeticException;
import org.apache.commons.math3.exception.MathInternalError;
import org.apache.commons.math3.exception.NotPositiveException;
import org.apache.commons.math3.exception.NullArgumentException;
import org.apache.commons.math3.exception.NumberIsTooLargeException;
import org.apache.commons.math3.exception.TooManyIterationsException;
import org.apache.commons.math3.exception.util.LocalizedFormats;
import org.apache.commons.math3.fraction.BigFraction;
import org.apache.commons.math3.fraction.BigFractionField;
import org.apache.commons.math3.fraction.FractionConversionException;
import org.apache.commons.math3.linear.Array2DRowFieldMatrix;
import org.apache.commons.math3.linear.FieldMatrix;
import org.apache.commons.math3.util.ArithmeticUtils;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.MathArrays;

import beast.app.util.Application;
import beast.app.util.LogFile;
import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.util.LogAnalyser;
import beast.core.Runnable;
import beast.core.util.Log;

@Description("Calculate Kolmogorov-Smirnof statistic for comparing trace logs")
public class TraceKSStats extends Runnable {
	final public Input<LogFile> trace1Input = new Input<>("trace1", "first trace file to compare", Validate.REQUIRED);
	final public Input<LogFile> trace2Input = new Input<>("trace2", "second trace file to compare", Validate.REQUIRED);
	final public Input<Integer> burnInPercentageInput = new Input<>("burnin", "percentage of trace logs to used as burn-in (and will be ignored)", 10);

	@Override
	public void initAndValidate() {
	}

	@Override
	public void run() throws Exception {
		int burnInPercentage = burnInPercentageInput.get();
		LogAnalyser trace1 = new LogAnalyser(trace1Input.get().getAbsolutePath(), burnInPercentage, true, false);
		LogAnalyser trace2 = new LogAnalyser(trace2Input.get().getAbsolutePath(), burnInPercentage, true, false);
		
		// ensure traces are over the same entries
		if (trace1.getLabels().size() != trace2.getLabels().size()) {
			Log.warning("Looks like different log files -- expect things to crash");
		}
		String label = "Trace entry";				
		Log.info(label + (label.length() < CoverageCalculator.space.length() ? CoverageCalculator.space.substring(label.length()) : " ") + " p-value");
		for (int i = 0; i < trace1.getLabels().size(); i++) {
			if (!trace1.getLabels().get(i).equals(trace2.getLabels().get(i))) {
				Log.warning("Columns do not match: " + trace1.getLabels().get(i) + " != " + trace2.getLabels().get(i));
			}
			Double [] x = trace1.getTrace(i);
			Double [] y = trace2.getTrace(i);
			double [] x0 = toDouble(x);
			double [] y0 = toDouble(y);
			
			
			double p = kolmogorovSmirnovTest(x0, y0, true);
			label = trace1.getLabels().get(i);
			Log.info(label + (label.length() < CoverageCalculator.space.length() ? CoverageCalculator.space.substring(label.length()) : " ") + " " + p);
		}

	}

	private double[] toDouble(Double[] x) {
		double [] x0 = new double[x.length];
		for (int i = 0; i < x.length; i++) {
			x0[i] = x[i];
		}
		return x0;
	}

	
	public static void main(String[] args) throws Exception {
		new Application(new TraceKSStats(), "Trace K-S statistics", args);
	}

	
	/****************************************************************************************
	 * 
	 * the following is code from apache commons math v3.6.1 that is not available in v3.3.1
	 *  
	 *****************************************************************************************/
    public double kolmogorovSmirnovTest(double[] x, double[] y, boolean strict) {
        final long lengthProduct = (long) x.length * y.length;
        double[] xa = null;
        double[] ya = null;
        if (lengthProduct < LARGE_SAMPLE_PRODUCT && hasTies(x,y)) {
            xa = MathArrays.copyOf(x);
            ya = MathArrays.copyOf(y);
            fixTies(xa, ya);
        } else {
            xa = x;
            ya = y;
        }
        if (lengthProduct < LARGE_SAMPLE_PRODUCT) {
            return exactP(kolmogorovSmirnovStatistic(xa, ya), x.length, y.length, strict);
        }
        return approximateP(kolmogorovSmirnovStatistic(x, y), x.length, y.length);
    }
    
    public double exactP(double d, int n, int m, boolean strict) {
        return 1 - n(m, n, m, n, calculateIntegralD(d, m, n, strict), strict) /
                binomialCoefficientDouble(n + m, m);
     }

    public static double binomialCoefficientDouble(final int n, final int k)
            throws NotPositiveException, NumberIsTooLargeException, MathArithmeticException {
            checkBinomial(n, k);
            if ((n == k) || (k == 0)) {
                return 1d;
            }
            if ((k == 1) || (k == n - 1)) {
                return n;
            }
            if (k > n/2) {
                return binomialCoefficientDouble(n, n - k);
            }
            if (n < 67) {
                return binomialCoefficient(n,k);
            }

            double result = 1d;
            for (int i = 1; i <= k; i++) {
                 result *= (double)(n - k + i) / (double)i;
            }

            return FastMath.floor(result + 0.5);
        }
    
    public static long binomialCoefficient(final int n, final int k)
            throws NotPositiveException, NumberIsTooLargeException, MathArithmeticException {
            checkBinomial(n, k);
            if ((n == k) || (k == 0)) {
                return 1;
            }
            if ((k == 1) || (k == n - 1)) {
                return n;
            }
            // Use symmetry for large k
            if (k > n / 2) {
                return binomialCoefficient(n, n - k);
            }

            // We use the formula
            // (n choose k) = n! / (n-k)! / k!
            // (n choose k) == ((n-k+1)*...*n) / (1*...*k)
            // which could be written
            // (n choose k) == (n-1 choose k-1) * n / k
            long result = 1;
            if (n <= 61) {
                // For n <= 61, the naive implementation cannot overflow.
                int i = n - k + 1;
                for (int j = 1; j <= k; j++) {
                    result = result * i / j;
                    i++;
                }
            } else if (n <= 66) {
                // For n > 61 but n <= 66, the result cannot overflow,
                // but we must take care not to overflow intermediate values.
                int i = n - k + 1;
                for (int j = 1; j <= k; j++) {
                    // We know that (result * i) is divisible by j,
                    // but (result * i) may overflow, so we split j:
                    // Filter out the gcd, d, so j/d and i/d are integer.
                    // result is divisible by (j/d) because (j/d)
                    // is relative prime to (i/d) and is a divisor of
                    // result * (i/d).
                    final long d = ArithmeticUtils.gcd(i, j);
                    result = (result / (j / d)) * (i / d);
                    i++;
                }
            } else {
                // For n > 66, a result overflow might occur, so we check
                // the multiplication, taking care to not overflow
                // unnecessary.
                int i = n - k + 1;
                for (int j = 1; j <= k; j++) {
                    final long d = ArithmeticUtils.gcd(i, j);
                    result = ArithmeticUtils.mulAndCheck(result / (j / d), i / d);
                    i++;
                }
            }
            return result;
        }
    public static void checkBinomial(final int n,
            final int k)
            		throws NumberIsTooLargeException,
            		NotPositiveException {
    	if (n < k) {
    		throw new NumberIsTooLargeException(LocalizedFormats.BINOMIAL_INVALID_PARAMETERS_ORDER,
                       k, n, true);
    	}
    	if (n < 0) {
    		throw new NotPositiveException(LocalizedFormats.BINOMIAL_NEGATIVE_PARAMETER, n);
    	}
    }
    
    
    private static double n(int i, int j, int m, int n, long cnm, boolean strict) {
        /*
         * Unwind the recursive definition given in [4].
         * Compute n(1,1), n(1,2)...n(2,1), n(2,2)... up to n(i,j), one row at a time.
         * When n(i,*) are being computed, lag[] holds the values of n(i - 1, *).
         */
        final double[] lag = new double[n];
        double last = 0;
        for (int k = 0; k < n; k++) {
            lag[k] = c(0, k + 1, m, n, cnm, strict);
        }
        for (int k = 1; k <= i; k++) {
            last = c(k, 0, m, n, cnm, strict);
            for (int l = 1; l <= j; l++) {
                lag[l - 1] = c(k, l, m, n, cnm, strict) * (last + lag[l - 1]);
                last = lag[l - 1];
            }
        }
        return last;
    }
    
    private static int c(int i, int j, int m, int n, long cmn, boolean strict) {
        if (strict) {
            return FastMath.abs(i*(long)n - j*(long)m) <= cmn ? 1 : 0;
        }
        return FastMath.abs(i*(long)n - j*(long)m) < cmn ? 1 : 0;
    }

    private static long calculateIntegralD(double d, int n, int m, boolean strict) {
        final double tol = 1e-12;  // d-values within tol of one another are considered equal
        long nm = n * (long)m;
        long upperBound = (long)FastMath.ceil((d - tol) * nm);
        long lowerBound = (long)FastMath.floor((d + tol) * nm);
        if (strict && lowerBound == upperBound) {
            return upperBound + 1l;
        }
        else {
            return upperBound;
        }
    }

    private double exactK(double d, int n)
            throws MathArithmeticException {

            final int k = (int) Math.ceil(n * d);

            final FieldMatrix<BigFraction> H = this.createExactH(d, n);
            final FieldMatrix<BigFraction> Hpower = H.power(n);

            BigFraction pFrac = Hpower.getEntry(k - 1, k - 1);

            for (int i = 1; i <= n; ++i) {
                pFrac = pFrac.multiply(i).divide(n);
            }

            /*
             * BigFraction.doubleValue converts numerator to double and the denominator to double and
             * divides afterwards. That gives NaN quite easy. This does not (scale is the number of
             * digits):
             */
            return pFrac.bigDecimalValue(20, BigDecimal.ROUND_HALF_UP).doubleValue();
        }
    private FieldMatrix<BigFraction> createExactH(double d, int n)
            throws NumberIsTooLargeException, FractionConversionException {

            final int k = (int) Math.ceil(n * d);
            final int m = 2 * k - 1;
            final double hDouble = k - n * d;
            if (hDouble >= 1) {
                throw new NumberIsTooLargeException(hDouble, 1.0, false);
            }
            BigFraction h = null;
            try {
                h = new BigFraction(hDouble, 1.0e-20, 10000);
            } catch (final FractionConversionException e1) {
                try {
                    h = new BigFraction(hDouble, 1.0e-10, 10000);
                } catch (final FractionConversionException e2) {
                    h = new BigFraction(hDouble, 1.0e-5, 10000);
                }
            }
            final BigFraction[][] Hdata = new BigFraction[m][m];

            /*
             * Start by filling everything with either 0 or 1.
             */
            for (int i = 0; i < m; ++i) {
                for (int j = 0; j < m; ++j) {
                    if (i - j + 1 < 0) {
                        Hdata[i][j] = BigFraction.ZERO;
                    } else {
                        Hdata[i][j] = BigFraction.ONE;
                    }
                }
            }

            /*
             * Setting up power-array to avoid calculating the same value twice: hPowers[0] = h^1 ...
             * hPowers[m-1] = h^m
             */
            final BigFraction[] hPowers = new BigFraction[m];
            hPowers[0] = h;
            for (int i = 1; i < m; ++i) {
                hPowers[i] = h.multiply(hPowers[i - 1]);
            }

            /*
             * First column and last row has special values (each other reversed).
             */
            for (int i = 0; i < m; ++i) {
                Hdata[i][0] = Hdata[i][0].subtract(hPowers[i]);
                Hdata[m - 1][i] = Hdata[m - 1][i].subtract(hPowers[m - i - 1]);
            }

            /*
             * [1] states: "For 1/2 < h < 1 the bottom left element of the matrix should be (1 - 2*h^m +
             * (2h - 1)^m )/m!" Since 0 <= h < 1, then if h > 1/2 is sufficient to check:
             */
            if (h.compareTo(BigFraction.ONE_HALF) == 1) {
                Hdata[m - 1][0] = Hdata[m - 1][0].add(h.multiply(2).subtract(1).pow(m));
            }

            /*
             * Aside from the first column and last row, the (i, j)-th element is 1/(i - j + 1)! if i -
             * j + 1 >= 0, else 0. 1's and 0's are already put, so only division with (i - j + 1)! is
             * needed in the elements that have 1's. There is no need to calculate (i - j + 1)! and then
             * divide - small steps avoid overflows. Note that i - j + 1 > 0 <=> i + 1 > j instead of
             * j'ing all the way to m. Also note that it is started at g = 2 because dividing by 1 isn't
             * really necessary.
             */
            for (int i = 0; i < m; ++i) {
                for (int j = 0; j < i + 1; ++j) {
                    if (i - j + 1 > 0) {
                        for (int g = 2; g <= i - j + 1; ++g) {
                            Hdata[i][j] = Hdata[i][j].divide(g);
                        }
                    }
                }
            }
            return new Array2DRowFieldMatrix<BigFraction>(BigFractionField.getInstance(), Hdata);
        }
    
    private static boolean hasTies(double[] x, double[] y) {
        final HashSet<Double> values = new HashSet<Double>();
            for (int i = 0; i < x.length; i++) {
                if (!values.add(x[i])) {
                    return true;
                }
            }
            for (int i = 0; i < y.length; i++) {
                if (!values.add(y[i])) {
                    return true;
                }
            }
        return false;
    }

    private static void fixTies(double[] x, double[] y) {
        final double[] values = unique(concatenate(x,y));
        if (values.length == x.length + y.length) {
            return;  // There are no ties
        }

        // Find the smallest difference between values, or 1 if all values are the same
        double minDelta = 1;
        double prev = values[0];
        double delta = 1;
        for (int i = 1; i < values.length; i++) {
           delta = prev - values[i];
           if (delta < minDelta) {
               minDelta = delta;
           }
           prev = values[i];
        }
        minDelta /= 2;

        // Add jitter using a fixed seed (so same arguments always give same results),
        // low-initialization-overhead generator
        final RealDistribution dist =
                new UniformRealDistribution(-minDelta, minDelta);

        // It is theoretically possible that jitter does not break ties, so repeat
        // until all ties are gone.  Bound the loop and throw MIE if bound is exceeded.
        int ct = 0;
        boolean ties = true;
        do {
            jitter(x, dist);
            jitter(y, dist);
            ties = hasTies(x, y);
            ct++;
        } while (ties && ct < 1000);
        if (ties) {
            throw new MathInternalError(); // Should never happen
        }
     }
    
    public static double[] unique(double[] data) {
        TreeSet<Double> values = new TreeSet<Double>();
        for (int i = 0; i < data.length; i++) {
            values.add(data[i]);
        }
        final int count = values.size();
        final double[] out = new double[count];
        Iterator<Double> iterator = values.iterator();
        int i = 0;
        while (iterator.hasNext()) {
            out[count - ++i] = iterator.next();
        }
        return out;
    }
    public static double[] concatenate(double[] ...x) {
        int combinedLength = 0;
        for (double[] a : x) {
            combinedLength += a.length;
        }
        int offset = 0;
        int curLength = 0;
        final double[] combined = new double[combinedLength];
        for (int i = 0; i < x.length; i++) {
            curLength = x[i].length;
            System.arraycopy(x[i], 0, combined, offset, curLength);
            offset += curLength;
        }
        return combined;
    }

    private static void jitter(double[] data, RealDistribution dist) {
        for (int i = 0; i < data.length; i++) {
            data[i] += dist.sample();
        }
    }

    protected static final double KS_SUM_CAUCHY_CRITERION = 1E-20;
    protected static final int LARGE_SAMPLE_PRODUCT = 10000;
    protected static final int MAXIMUM_PARTIAL_SUM_COUNT = 100000;

    public double approximateP(double d, int n, int m) {
        final double dm = m;
        final double dn = n;
        return 1 - ksSum(d * Math.sqrt((dm * dn) / (dm + dn)),
                         KS_SUM_CAUCHY_CRITERION, MAXIMUM_PARTIAL_SUM_COUNT);
    }

    public double ksSum(double t, double tolerance, int maxIterations) {
        if (t == 0.0) {
            return 0.0;
        }

        // TODO: for small t (say less than 1), the alternative expansion in part 3 of [1]
        // from class javadoc should be used.

        final double x = -2 * t * t;
        int sign = -1;
        long i = 1;
        double partialSum = 0.5d;
        double delta = 1;
        while (delta > tolerance && i < maxIterations) {
            delta = Math.exp(x * i * i);
            partialSum += sign * delta;
            sign *= -1;
            i++;
        }
        if (i == maxIterations) {
            throw new TooManyIterationsException(maxIterations);
        }
        return partialSum * 2;
    }
    public double kolmogorovSmirnovStatistic(double[] x, double[] y) {
        return integralKolmogorovSmirnovStatistic(x, y)/((double)(x.length * (long)y.length));
    }

    private long integralKolmogorovSmirnovStatistic(double[] x, double[] y) {
        checkArray(x);
        checkArray(y);
        // Copy and sort the sample arrays
        final double[] sx = MathArrays.copyOf(x);
        final double[] sy = MathArrays.copyOf(y);
        Arrays.sort(sx);
        Arrays.sort(sy);
        final int n = sx.length;
        final int m = sy.length;

        int rankX = 0;
        int rankY = 0;
        long curD = 0l;

        // Find the max difference between cdf_x and cdf_y
        long supD = 0l;
        do {
            double z = Double.compare(sx[rankX], sy[rankY]) <= 0 ? sx[rankX] : sy[rankY];
            while(rankX < n && Double.compare(sx[rankX], z) == 0) {
                rankX += 1;
                curD += m;
            }
            while(rankY < m && Double.compare(sy[rankY], z) == 0) {
                rankY += 1;
                curD -= n;
            }
            if (curD > supD) {
                supD = curD;
            }
            else if (-curD > supD) {
                supD = -curD;
            }
        } while(rankX < n && rankY < m);
        return supD;
    }
    
    private void checkArray(double[] array) {
        if (array == null) {
            throw new NullArgumentException(LocalizedFormats.NULL_NOT_ALLOWED);
        }
        if (array.length < 2) {
            throw new IllegalArgumentException("Not enough data: expected at least " + LocalizedFormats.INSUFFICIENT_OBSERVED_POINTS_IN_SAMPLE + " but got "+ array.length);
        }
    }


}
