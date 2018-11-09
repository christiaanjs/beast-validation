package beast.util;

import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.linear.*;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.MathArrays;

public class MultivariateNormalDistribution  {
    private int dim;
    private final double[] means;
    private final RealMatrix covarianceMatrix;
    private final RealMatrix covarianceMatrixInverse;
    private final double covarianceMatrixDeterminant;
    private final RealMatrix samplingMatrix;

    public MultivariateNormalDistribution(double[] means, double[][] covariances) throws SingularMatrixException, DimensionMismatchException, NonPositiveDefiniteMatrixException {
        dim = means.length;
        if (covariances.length != dim) {
            throw new DimensionMismatchException(covariances.length, dim);
        } else {
            for(int i = 0; i < dim; ++i) {
                if (dim != covariances[i].length) {
                    throw new DimensionMismatchException(covariances[i].length, dim);
                }
            }

            this.means = MathArrays.copyOf(means);
            this.covarianceMatrix = new Array2DRowRealMatrix(covariances);
            EigenDecomposition covMatDec = new EigenDecomposition(this.covarianceMatrix);
            this.covarianceMatrixInverse = covMatDec.getSolver().getInverse();
            this.covarianceMatrixDeterminant = covMatDec.getDeterminant();
            double[] covMatEigenvalues = covMatDec.getRealEigenvalues();

            for(int i = 0; i < covMatEigenvalues.length; ++i) {
                if (covMatEigenvalues[i] < 0.0D) {
                    throw new NonPositiveDefiniteMatrixException(covMatEigenvalues[i], i, 0.0D);
                }
            }

            Array2DRowRealMatrix covMatEigenvectors = new Array2DRowRealMatrix(dim, dim);

            for(int v = 0; v < dim; ++v) {
                double[] evec = covMatDec.getEigenvector(v).toArray();
                covMatEigenvectors.setColumn(v, evec);
            }

            RealMatrix tmpMatrix = covMatEigenvectors.transpose();

            for(int row = 0; row < dim; ++row) {
                double factor = FastMath.sqrt(covMatEigenvalues[row]);

                for(int col = 0; col < dim; ++col) {
                    tmpMatrix.multiplyEntry(row, col, factor);
                }
            }

            this.samplingMatrix = covMatEigenvectors.multiply(tmpMatrix);
        }
    }

    public double density(double[] vals) throws DimensionMismatchException {
        return FastMath.exp(logDensity(vals));
    }

    public double logDensity(double[] vals){
        if (vals.length != dim) {
            throw new DimensionMismatchException(vals.length, dim);
        } else {
            return -0.5D * (dim * FastMath.log(6.283185307179586D) + FastMath.log(covarianceMatrixDeterminant)) + getLogExponentTerm(vals);
        }
    }

    private double getLogExponentTerm(double[] values) {
        double[] centered = new double[values.length];

        for(int i = 0; i < centered.length; ++i) {
            centered[i] = values[i] - means[i];
        }

        double[] preMultiplied = this.covarianceMatrixInverse.preMultiply(centered);
        double sum = 0.0D;

        for(int i = 0; i < preMultiplied.length; ++i) {
            sum += preMultiplied[i] * centered[i];
        }

        return -0.5D * sum;
    }
}