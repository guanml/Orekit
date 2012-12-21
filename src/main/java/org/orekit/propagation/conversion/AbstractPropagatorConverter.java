/* Copyright 2002-2012 CS Systèmes d'Information
 * Licensed to CS Systèmes d'Information (CS) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * CS licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.orekit.propagation.conversion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.math3.analysis.DifferentiableMultivariateVectorFunction;
import org.apache.commons.math3.exception.MaxCountExceededException;
import org.apache.commons.math3.optimization.PointVectorValuePair;
import org.apache.commons.math3.optimization.SimpleVectorValueChecker;
import org.apache.commons.math3.optimization.general.LevenbergMarquardtOptimizer;
import org.apache.commons.math3.util.FastMath;
import org.orekit.errors.OrekitException;
import org.orekit.errors.OrekitExceptionWrapper;
import org.orekit.errors.OrekitMessages;
import org.orekit.frames.Frame;
import org.orekit.propagation.Propagator;
import org.orekit.propagation.SpacecraftState;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;

/** Common handling of {@link PropagatorConverter} methods for propagators conversions.
 * <p>
 * This abstract class factorizes the common code for propagators conversion.
 * Only one method must be implemented by derived classes: {@link #getObjectiveFunction()}.
 * </p>
 * <p>
 * The converter uses the LevenbergMarquardtOptimizer from the <a
 * href="http://commons.apache.org/math/">commons math</a> library.
 * Different implementations correspond to different methods for computing the jacobian.
 * </p>
 * @author Pascal Parraud
 * @since 6.0
 */
public abstract class AbstractPropagatorConverter implements PropagatorConverter {

    /** Spacecraft states sample. */
    private List<SpacecraftState> sample;

    /** Reference date for conversion (1st date of the states sample). */
    private AbsoluteDate date;

    /** Target position and velocities at sample points. */
    private double[] target;

    /** Weight for residuals. */
    private double[] weight;

    /** Auxiliary outputData: RMS of solution. */
    private double rms;

    /** Position use indicator. */
    private boolean onlyPosition;

    /** Adapted propagator. */
    private Propagator adapted;

    /** List of the desired free parameters names. */
    private Collection<String> parameters;

    /** List of the available free parameters names. */
    private final Collection<String> availableParameters;

    /** Propagator builder. */
    private final PropagatorBuilder builder;

    /** Frame. */
    private final Frame frame;

    /** Optimizer for fitting. */
    private final LevenbergMarquardtOptimizer optimizer;

    /** Maximum number of iterations for optimization. */
    private final int maxIterations;

    /** Build a new instance.
     * @param builder propagator builder
     * @param threshold absolute convergence threshold for optimization algorithm
     * @param maxIterations maximum number of iterations for fitting
     */
    protected AbstractPropagatorConverter(final PropagatorBuilder builder,
                                          final double threshold,
                                          final int maxIterations) {
        this.builder             = builder;
        this.frame               = builder.getFrame();
        this.availableParameters = builder.getParametersNames();
        this.optimizer           = new LevenbergMarquardtOptimizer(new SimpleVectorValueChecker(-1.0, threshold));
        this.maxIterations       = maxIterations;
        this.sample              = new ArrayList<SpacecraftState>();
    }

    /** Convert a propagator to another.
     * @param source initial propagator
     * @param timeSpan time span for fitting
     * @param nbPoints number of fitting points over time span
     * @param freeParameters names of the free parameters
     * @return adapted propagator
     * @exception OrekitException if propagator cannot be adapted
     * @exception IllegalArgumentException if one of the parameters cannot be free
     */
    public Propagator convert(final Propagator source,
                              final double timeSpan,
                              final int nbPoints,
                              final Collection<String> freeParameters) throws OrekitException {

        checkParameters(freeParameters);
        final List<SpacecraftState> states = createSample(source, timeSpan, nbPoints);
        return convert(states, false, freeParameters);
    }

    /** Convert a propagator to another.
     * @param source initial propagator
     * @param timeSpan time span for fitting
     * @param nbPoints number of fitting points over time span
     * @param freeParameters names of the free parameters
     * @return adapted propagator
     * @exception OrekitException if propagator cannot be adapted
     * @exception IllegalArgumentException if one of the parameters cannot be free
     */
    public Propagator convert(final Propagator source,
                              final double timeSpan,
                              final int nbPoints,
                              final String ... freeParameters) throws OrekitException {

        checkParameters(freeParameters);
        final List<SpacecraftState> states = createSample(source, timeSpan, nbPoints);
        return convert(states, false, freeParameters);
    }

    /** Find the propagator that minimize the mean square error for a sample of {@link SpacecraftState states}.
     * @param states spacecraft states sample to fit
     * @param positionOnly if true, consider only position data otherwise both position and velocity are used
     * @param freeParameters names of the free parameters
     * @return adapted propagator
     * @exception OrekitException if propagator cannot be adapted
     * @exception IllegalArgumentException if one of the parameters cannot be free
     */
    public Propagator convert(final List<SpacecraftState> states,
                              final boolean positionOnly,
                              final Collection<String> freeParameters) throws OrekitException {

        checkParameters(freeParameters);

        parameters = new ArrayList<String>();
        parameters.addAll(freeParameters);

        builder.setFreeParameters(parameters);

        return adapt(states, positionOnly);
    }

    /** Find the propagator that minimize the mean square error for a sample of {@link SpacecraftState states}.
     * @param states spacecraft states sample to fit
     * @param positionOnly if true, consider only position data otherwise both position and velocity are used
     * @param freeParameters names of the free parameters
     * @return adapted propagator
     * @exception OrekitException if propagator cannot be adapted
     * @exception IllegalArgumentException if one of the parameters cannot be free
     */
    public Propagator convert(final List<SpacecraftState> states,
                              final boolean positionOnly,
                              final String ... freeParameters) throws OrekitException {

        checkParameters(freeParameters);

        parameters = new ArrayList<String>();
        for (final String name : freeParameters) {
            parameters.add(name);
        }

        builder.setFreeParameters(parameters);

        return adapt(states, positionOnly);
    }

    /** Get the available free parameters.
     * @return available free parameters
     */
    public Collection<String> getAvailableParameters() {
        return availableParameters;
    }

    /** Check if a parameter can be free.
     * @param name parameter name to check
     * @return true if the parameter can be free
     */
    public boolean isAvailable(final String name) {
        for (final String supportedName : availableParameters) {
            if (supportedName.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Get the adapted propagator.
     * @return adapted propagator
     */
    public Propagator getAdaptedPropagator() {
        return adapted;
    }

    /** Get the Root Mean Square Deviation of the fitting.
     * @return RMSD
     */
    public double getRMS() {
        return rms;
    }

    /** Get the number of objective function evaluations.
     *  @return the number of objective function evaluations.
     */
    public int getEvaluations() {
        return optimizer.getEvaluations();
    }

    /** Get the function computing position/velocity at sample points.
     * @return function computing position/velocity at sample points
     */
    protected abstract DifferentiableMultivariateVectorFunction getObjectiveFunction();

    /** Check if fitting uses only sample positions.
     * @return true if fitting uses only sample positions
     */
    protected boolean isOnlyPosition() {
        return onlyPosition;
    }

    /** Get the size of the target.
     * @return target size
     */
    protected int getTargetSize() {
        return target.length;
    }

    /** Get the date of the initial state.
     * @return the date
     */
    protected AbsoluteDate getDate() {
        return date;
    }

    /** Get the frame of the initial state.
     * @return the orbit frame
     */
    protected Frame getFrame() {
        return frame;
    }

    /** Get the states sample.
     * @return the states sample
     */
    protected List<SpacecraftState> getSample() {
        return sample;
    }

    /** Get the free parameters.
     * @return the free parameters
     */
    protected Collection<String>  getFreeParameters() {
        return parameters;
    }

    /** Create a sample of {@link SpacecraftState}.
     * @param source initial propagator
     * @param timeSpan time span for the sample
     * @param nbPoints number of points for the sample over the time span
     * @return a sample of {@link SpacecraftState}
     * @exception OrekitException if one of the sample point cannot be computed
     */
    private List<SpacecraftState> createSample(final Propagator source,
                                               final double timeSpan,
                                               final int nbPoints) throws OrekitException {

        final SpacecraftState initialState = source.getInitialState();

        final List<SpacecraftState> states = new ArrayList<SpacecraftState>();

        final double stepSize = timeSpan / (nbPoints - 1);
        final AbsoluteDate iniDate = source.getInitialState().getDate();
        for (double dt = 0; dt < timeSpan; dt += stepSize) {
            states.add(source.propagate(iniDate.shiftedBy(dt)));
        }

        source.resetInitialState(initialState);

        return states;
    }

    /** Check if parameters can be free.
     * @param freeParameters names of the free parameters
     * @exception IllegalArgumentException if one of the parameters cannot be free
     */
    private void checkParameters(final Collection<String> freeParameters)
        throws IllegalArgumentException {

        for (String parameter : freeParameters) {
            if ( !availableParameters.contains(parameter) ) {
                throw OrekitException.createIllegalArgumentException(OrekitMessages.UNKNOWN_PARAMETER, parameter);
            }
        }

    }

    /** Check if parameters can be free.
     * @param freeParameters names of the free parameters
     * @exception IllegalArgumentException if one of the parameters cannot be free
     */
    private void checkParameters(final String ... freeParameters)
        throws IllegalArgumentException {

        for (String parameter : freeParameters) {
            if ( !availableParameters.contains(parameter) ) {
                throw OrekitException.createIllegalArgumentException(OrekitMessages.UNKNOWN_PARAMETER, parameter);
            }
        }

    }

    /** Adapt a propagator to minimize the mean square error for a set of {@link SpacecraftState states}.
     * @param states set of spacecraft states to fit
     * @param positionOnly if true, consider only position data otherwise both position and velocity are used
     * @return adapted propagator
     * @exception OrekitException if propagator cannot be adapted
     */
    private Propagator adapt(final List<SpacecraftState> states,
                             final boolean positionOnly) throws OrekitException {

        this.date = states.get(0).getDate();
        this.onlyPosition = positionOnly;

        // very rough first guess using osculating parameters of first sample point
        final double[] initial = new double[6 + parameters.size()];
        final PVCoordinates pv = states.get(0).getPVCoordinates(frame);
        initial[0] = pv.getPosition().getX();
        initial[1] = pv.getPosition().getY();
        initial[2] = pv.getPosition().getZ();
        initial[3] = pv.getVelocity().getX();
        initial[4] = pv.getVelocity().getY();
        initial[5] = pv.getVelocity().getZ();
        int i = 6;
        for (String name : parameters) {
            initial[i++] = builder.getParameter(name);
        }

        // warm-up iterations, using only a few points
        setSample(states.subList(0, onlyPosition ? 2 : 1));
        final double[] intermediate = fit(initial);

        // final search using all points
        setSample(states);
        final double[] result = fit(intermediate);

        rms = getRMS(result);
        adapted = buildAdaptedPropagator(result);

        return adapted;
    }

    /** Find the propagator that minimize the mean square error for a sample of {@link SpacecraftState states}.
     * @param initial initial estimation parameters (position, velocity, free parameters)
     * @return fitted parameters
     * @exception OrekitException if propagator cannot be adapted
     * @exception MaxCountExceededException if maximal number of iterations is exceeded
     */
    private double[] fit(final double[] initial)
        throws OrekitException, MaxCountExceededException {

        final PointVectorValuePair optimum = optimizer.optimize(maxIterations,
                                                                getObjectiveFunction(),
                                                                target, weight, initial);
        return optimum.getPoint();
    }

    /** Get the Root Mean Square Deviation for a given parameters set.
     * @param parameterSet position/velocity parameters set
     * @return RMSD
     * @exception OrekitException if position/velocity cannot be computed at some date
     */
    private double getRMS(final double[] parameterSet) throws OrekitException {

        final double[] residuals = getResiduals(parameterSet);
        double sum2 = 0;
        for (final double residual : residuals) {
            sum2 += residual * residual;
        }
        return FastMath.sqrt(sum2 / residuals.length);

    }

    /** Get the residuals for a given position/velocity parameters set.
     * @param parameterSet position/velocity parameters set
     * @return residuals
     * @exception OrekitException if position/velocity cannot be computed at some date
     */
    private double[] getResiduals(final double[] parameterSet) throws OrekitException {
        try {
            final double[] residuals = getObjectiveFunction().value(parameterSet);
            for (int i = 0; i < residuals.length; ++i) {
                residuals[i] = target[i] - residuals[i];
            }
            return residuals;
        } catch (OrekitExceptionWrapper oew) {
            throw oew.getException();
        }
    }

    /** Build the adpated propagator for a given position/velocity(/free) parameters set.
     * @param parameterSet position/velocity(/free) parameters set
     * @return adapted propagator
     * @exception OrekitException if propagator cannot be build
     */
    private Propagator buildAdaptedPropagator(final double[] parameterSet)
        throws OrekitException {
        return builder.buildPropagator(date, parameterSet);
    }

    /** Set the states sample.
     * @param states spacecraft states sample
     * @exception OrekitException if position/velocity cannot be extracted from sample
     */
    private void setSample(final List<SpacecraftState> states) throws OrekitException {

        this.sample = states;

        if (onlyPosition) {
            target = new double[states.size() * 3];
            weight = new double[states.size() * 3];
        } else {
            target = new double[states.size() * 6];
            weight = new double[states.size() * 6];
        }

        int k = 0;
        for (int i = 0; i < states.size(); i++) {

            final PVCoordinates pv = states.get(i).getPVCoordinates(frame);

            // position
            target[k]   = pv.getPosition().getX();
            weight[k++] = 1;
            target[k]   = pv.getPosition().getY();
            weight[k++] = 1;
            target[k]   = pv.getPosition().getZ();
            weight[k++] = 1;

            // velocity
            if (!onlyPosition) {
                // velocity weight relative to position
                final double r2 = pv.getPosition().getNormSq();
                final double v  = pv.getVelocity().getNorm();
                final double vWeight = v * r2 / states.get(i).getMu();

                target[k]   = pv.getVelocity().getX();
                weight[k++] = vWeight;
                target[k]   = pv.getVelocity().getY();
                weight[k++] = vWeight;
                target[k]   = pv.getVelocity().getZ();
                weight[k++] = vWeight;
            }

        }

    }

}