/* Copyright 2002-2015 CS Systèmes d'Information
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
package org.orekit.estimation;



/** Class representing a single parameter that can be estimated.
 * <p>
 * {@link #compareTo(Parameter) Comparison} and {@link #equals(Object) equality}
 * are evaluated only with respect to name. This implies that sorted collections
 * of parameters are sorted lexicographically.
 * </p>
 * @author Luc Maisonobe
 * @since 7.1
 */
public class Parameter implements Comparable<Parameter> {

    /** Name of the parameter. */
    private final String name;

    /** Current value. */
    private double[] value;

    /** Estimated/fixed status. */
    private boolean estimated;

    /** Simple constructor.
     * @param name name of the parameter
     * @param value initial value
     * @param estimated if true, the parameter will be estimated
     */
    public Parameter(final String name, final double[] value, final boolean estimated) {
        this.name      = name;
        this.value     = value.clone();
        this.estimated = estimated;
    }

    /** Get name.
     * @return name
     */
    public String getName() {
        return name;
    }

    /** Get the dimension of the parameter.
     * @return dimension of the parameter
     */
    public int getDimension() {
        return value.length;
    }

    /** Get current parameter value.
     * @return current parameter value
     */
    public double[] getValue() {
        return value.clone();
    }

    /** Set parameter value.
     * @param value new value
     */
    public void setValue(final double[] value) {
        System.arraycopy(value, 0, this.value, 0, value.length);
    }

    /** Configure a parameter estimation status.
     * @param estimated if true the parameter will be estimated,
     * otherwise it will be fixed
     */
    public void setEstimated(final boolean estimated) {
        this.estimated = estimated;
    }

    /** Check if parameter is estimated.
     * @return true if parameter is estimated, false if it is
     * fixed
     */
    public boolean isEstimated() {
        return estimated;
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(final Parameter p) {
        return getName().compareTo(p.getName());
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(final Object o) {
        if ((o != null) && (o instanceof Parameter)) {
            return getName().equals(((Parameter) o).getName());
        } else {
            return false;
        }
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return getName().hashCode();
    }

}