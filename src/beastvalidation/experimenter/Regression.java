/*
 * Regression.java
 *
 * Copyright (c) 2002-2015 Alexei Drummond, Andrew Rambaut and Marc Suchard
 *
 * This file is part of BEAST.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership and licensing.
 *
 * BEAST is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 *  BEAST is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BEAST; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package beastvalidation.experimenter;


/**
 * simple regression of two variables
 *
 * @author Andrew Rambaut
 * @version $Id: Regression.java,v 1.5 2005/05/24 20:26:01 rambaut Exp $
 */
public class Regression {
    private double[] xData = null;
    private double[] yData = null;

    private boolean forceOrigin = false;
    private boolean regressionKnown = false;

    private double gradient;
    private double intercept;
    private double covariance;
    private double sumResidualsSquared;
    private double residualMeanSquared;
    private double correlationCoefficient;

    /**
     * Constructor
     */
    public Regression() {
    }

    /**
     * Constructor
     */
    public Regression(double[] xData, double[] yData) {
        setData(xData, yData);
    }

    /**
     * Constructor
     */
    public Regression(double[] xData, double[] yData, boolean forceOrigin) {
        setData(xData, yData);
        setForceOrigin(forceOrigin);
    }

    /**
     * Set data
     */
    public void setData(double[] xData, double[] yData) {

        this.xData = xData;
        this.yData = yData;

        regressionKnown = false;
    }

    public void setForceOrigin(boolean forceOrigin) {
        this.forceOrigin = forceOrigin;

        regressionKnown = false;
    }

    public double getGradient() {
        if (!regressionKnown)
            calculateRegression();
        return gradient;
    }

    public double getIntercept() {
        if (!regressionKnown)
            calculateRegression();
        return intercept;
    }

    public double getYIntercept() {
        return getIntercept();
    }

    public double getXIntercept() {
        return -getIntercept() / getGradient();
    }

    public double getCovariance() {
        if (!regressionKnown)
            calculateRegression();
        return covariance;
    }

    public double getResidualMeanSquared() {
        if (!regressionKnown)
            calculateRegression();
        return residualMeanSquared;
    }

    public double getSumResidualsSquared() {
        if (!regressionKnown)
            calculateRegression();
        return sumResidualsSquared;
    }

    public double getCorrelationCoefficient() {
        if (!regressionKnown) {
            calculateRegression();
        }
        return correlationCoefficient;
    }

    public double getRSquared() {
        if (!regressionKnown) {
            calculateRegression();
        }
        return correlationCoefficient * correlationCoefficient;
    }

    public double getResidual(final double x, final double y) {
        return y - ((getGradient() * x) + getIntercept());
    }

    public double getX(final double y) {
        return (y - getIntercept()) / getGradient();
    }

    public double getY(final double x) {
        return x * getGradient() + getIntercept();
    }


    public double [] getYResidualData() {
        double [] rd = new double[xData.length];

        for (int i = 0; i < xData.length; i++) {
            rd[i] = getResidual(xData[i], yData[i]);
        }

        return rd;
    }

    private void calculateRegression() {
        int i, n = xData.length;
        double meanX = 0.0, meanY = 0.0;

        if (!forceOrigin) {
            meanX = getMean(xData);
            meanY = getMean(yData);
        }

        //Calculate sum of products & sum of x squares

        double sumProducts = 0.0;
        double sumSquareX = 0.0;
        double sumSquareY = 0.0;
        double x1, y1;
        for (i = 0; i < n; i++) {
            x1 = xData[i] - meanX;
            y1 = yData[i] - meanY;
            sumProducts += x1 * y1;
            sumSquareX += x1 * x1;
            sumSquareY += y1 * y1;
        }

        //Calculate gradient and intercept of regression line. Calculate covariance.

        gradient = sumProducts / sumSquareX;             // Gradient
        intercept = meanY - (gradient * meanX);            // Intercept
        covariance = sumProducts / (n - 1);                // Covariance

        correlationCoefficient = sumProducts / Math.sqrt(sumSquareX * sumSquareY);

        //Calculate residual mean square

        sumResidualsSquared = 0;
        double residual;
        for (i = 0; i < n; i++) {
            residual = yData[i] - ((gradient * xData[i]) + intercept);
            sumResidualsSquared += residual * residual;
        }

        residualMeanSquared = sumResidualsSquared / (n - 2);// Residual Mean Square

        regressionKnown = true;
    }

	private double getMean(double[] data) {
		double sum = 0;
		for (double d : data) {
			sum += d;
		}
		return sum / data.length;
	}
	
	   /**
     * Returns a string representation of the simple linear regression model.
     *
     * @return a string representation of the simple linear regression model,
     *         including the best-fit line and the coefficient of determination
     *         <em>R</em><sup>2</sup>
     */
	@Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append(String.format("%.2f x " + (getIntercept() >= 0 ? "+":"")+ "  %.2f", getGradient(), getIntercept()));
        s.append("  (R^2 = " + String.format("%.2f", getRSquared()));
        // s.append("  cov = "  + String.format("%.2f", getCovariance()) + ")");
        return s.toString();
    }



}
