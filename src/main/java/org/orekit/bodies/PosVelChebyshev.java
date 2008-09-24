/* Copyright 2002-2008 CS Communication & Systèmes
 * Licensed to CS Communication & Systèmes (CS) under one or more
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
package org.orekit.bodies;

import org.apache.commons.math.geometry.Vector3D;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeStamped;
import org.orekit.utils.PVCoordinates;


/** Position-Velocity model based on Chebyshev polynomials.
 * <p>This class represent the most basic element of the piecewise ephemerides
 * for solar system bodies like JPL DE 405 ephemerides.</p>
 * @author Luc Maisonobe
 * @version $Revision$ $Date$
 */
class PosVelChebyshev implements TimeStamped {

    /** Start of the validity range of the instance. */
    private final AbsoluteDate start;

    /** Duration of validity range of the instance. */
    private final double duration;

    /** Chebyshev polynomials coefficients for the X component. */
    private final double[] xCoeffs;

    /** Chebyshev polynomials coefficients for the Y component. */
    private final double[] yCoeffs;

    /** Chebyshev polynomials coefficients for the Z component. */
    private final double[] zCoeffs;

    /** Simple constructor.
     * @param start start of the validity range of the instance
     * @param duration duration of the validity range of the instance
     * @param xCoeffs Chebyshev polynomials coefficients for the X component
     * (a reference to the array will be stored in the instance)
     * @param yCoeffs Chebyshev polynomials coefficients for the Y component
     * (a reference to the array will be stored in the instance)
     * @param zCoeffs Chebyshev polynomials coefficients for the Z component
     * (a reference to the array will be stored in the instance)
     */
    public PosVelChebyshev(final AbsoluteDate start, final double duration,
                           final double[] xCoeffs, final double[] yCoeffs,
                           final double[] zCoeffs) {
        this.start    = start;
        this.duration = duration;
        this.xCoeffs  = xCoeffs;
        this.yCoeffs  = yCoeffs;
        this.zCoeffs  = zCoeffs;
    }

    /** {@inheritDoc} */
    public AbsoluteDate getDate() {
        return start;
    }

    /** Check if a date is in validity range.
     * @param date date to check
     * @return true if date is in validity range
     */
    public boolean inRange(final AbsoluteDate date) {
        final double dt = date.durationFrom(start);
        return (dt >= 0) && (dt <= duration);
    }

    /** Get the position-velocity at a specified date.
     * @param date date at which position-velocity is requested
     * @return position-velocity at specified date
     */
    public PVCoordinates getPositionVelocity(final AbsoluteDate date) {

        // normalize date
        final double t = (2 * date.durationFrom(start) - duration) / duration;
        final double twoT = 2 * t;

        // initialize Chebyshev polynomials recursion
        double pKm1 = 1;
        double pK   = t;
        double xP   = xCoeffs[0];
        double yP   = yCoeffs[0];
        double zP   = zCoeffs[0];

        // initialize Chebishev polynomials derivatives recursion
        double qKm1 = 0;
        double qK   = 1;
        double xV   = 0;
        double yV   = 0;
        double zV   = 0;

        // combine polynomials by applying coefficients
        for (int k = 1; k < xCoeffs.length; ++k) {

            // consider last computed polynomials on position
            xP += xCoeffs[k] * pK;
            yP += yCoeffs[k] * pK;
            zP += zCoeffs[k] * pK;

            // consider last computed polynomials on velocity
            xV += xCoeffs[k] * qK;
            yV += yCoeffs[k] * qK;
            zV += zCoeffs[k] * qK;

            // compute next Chebyshev polynomial value
            final double pKm2 = pKm1;
            pKm1 = pK;
            pK   = twoT * pKm1 - pKm2;

            // compute nex Chebyshev polynomial derivative
            final double qKm2 = qKm1;
            qKm1 = qK;
            qK   = twoT * qKm1 + 2 * pKm1 - qKm2;

        }

        final double vScale = 2 / duration;
        return new PVCoordinates(new Vector3D(xP, yP, zP),
                                 new Vector3D(xV * vScale, yV * vScale, zV * vScale));

    }

}