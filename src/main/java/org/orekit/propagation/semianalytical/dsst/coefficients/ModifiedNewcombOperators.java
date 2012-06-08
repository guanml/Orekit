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
package org.orekit.propagation.semianalytical.dsst.coefficients;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import org.apache.commons.math3.analysis.polynomials.PolynomialsUtils;
import org.apache.commons.math3.util.FastMath;
import org.orekit.errors.OrekitException;

/** Implementation of the Modified Newcomb Operators.
 * From equation 2.7.3 - (12)(13) of the Danielson paper for the Satellite Semianalytical Theory,
 * those operators are defined as follow :
 *
 * <pre>
 * 4(&rho; + &sigma;)Y<sub>&rho;,&sigma;</sub><sup>n,s</sup> = 2(2s - n)Y<sub>&rho;-1,&sigma;</sub><sup>n,s+1</sup> + (s - n)Y<sub>&rho;-2,&sigma;</sub><sup>n,s+2</sup>
 * - 2(2s + n)Y<sub>&rho;,&sigma;-1</sub><sup>n,s-1</sup> - (s+n)Y<sub>&rho;,&sigma;-2</sub><sup>n,s-2</sup> + 2(2&rho; + 2&sigma; + 2 + 3n)Y<sub>&rho;-1,&sigma;-1</sub><sup>n,s</sup>
 * </pre>
 *
 * With &rho; >= &sigma;. Initialization is given by :
 *
 * <pre>
 * Y<sub>0,0</sub><sup>n,s</sup> = 1 & Y<sub>1,0</sub><sup>n,s</sup> = s - n / 2
 * </pre>
 *
 * Internally, the Modified Newcomb Operators are stored as an array of {@link PolynomialFunction} :
 *
 * <pre>
 * Y<sub>&rho;,&sigma;</sub><sup>n,s</sup> = P<sub>k<sub>0</sub></sub> + P<sub>k<sub>1</sub></sub>n + ... + P<sub>k<sub>j</sub></sub>n<sup>j</sup>
 * </pre>
 *
 * where the P<sub>k<sub>j</sub></sub> are given by
 *
 * <pre>
 *  P<sub>k<sub>j</sub></sub> = &sum;<sub>j=0;&rho;</sub> a<sub>j</sub>s<sup>j</sup>
 * </pre>
 *
 * @author Romain Di Costanzo
 */
public class ModifiedNewcombOperators {

    /** Private constructor as class is a utility.
     */
    private ModifiedNewcombOperators() {
    }

    /** Get the polynomial value for the couple (&rho;, &sigma;), at n,s.
     *
     * @param rho gives the polynomial first index
     * @param sigma gives the polynomial second index
     * @param n n-value
     * @param s s-value
     * @return evaluated polynomial
     * @throws OrekitException if &rho; < &sigma;
     */
    public static double getValue(final int rho, final int sigma, final int n, final int s)
        throws OrekitException {
        final boolean reverse = rho < sigma;
        final int maxOrder = reverse ?
                             NewcombPolynomialsGenerator.COMPUTED_REVERSE_ORDER :
                             NewcombPolynomialsGenerator.COMPUTED_DIRECT_ORDER;
        // If order hasn't been computed yet, update the Newcomb polynomials
        if (reverse && sigma > maxOrder) {
            if (sigma > NewcombPolynomialsGenerator.COMPUTED_DIRECT_ORDER) {
                NewcombPolynomialsGenerator.computeUpToDegree(sigma, maxOrder, false);
            }
            NewcombPolynomialsGenerator.computeUpToDegree(sigma, maxOrder, true);
        } else if (!reverse && rho > maxOrder) {
            NewcombPolynomialsGenerator.computeUpToDegree(rho, maxOrder, false);
        }

        // Initialization :
        double result = 0d;
        // Compute value from the list of polynomials :
        // First get the Newcomb polynomial for a given couple rho / sigma
        final List<PolynomialFunction> poly = NewcombPolynomialsGenerator.NEWCOMB_POLYNOMIALS.get(new Couple(rho, sigma));
        double power = 1;
        for (final PolynomialFunction function : poly) {
            result += function.value(s) * power;
            power = n * power;
        }

        return result;
    }

    /** Get the list of polynomials representing the Modified Newcomb Operator for the (&rho;,&sigma;) couple.
     * @param rho &rho;
     * @param sigma &sigma;
     * @return polynomial representing the Modified Newcomb Operator for the (&rho;,&sigma;) couple.
     *         If couple hasn't been computed, return null.
     */
    public static List<PolynomialFunction> getPolynomialList(final int rho, final int sigma) {
        final boolean reverse = rho < sigma;
        final int maxOrder = reverse ?
                             NewcombPolynomialsGenerator.COMPUTED_REVERSE_ORDER :
                             NewcombPolynomialsGenerator.COMPUTED_DIRECT_ORDER;

        // If order hasn't been computed yet, update the Newcomb polynomials
        if (reverse && sigma > NewcombPolynomialsGenerator.COMPUTED_REVERSE_ORDER) {
            if (sigma > NewcombPolynomialsGenerator.COMPUTED_DIRECT_ORDER) {
                NewcombPolynomialsGenerator.computeUpToDegree(sigma, NewcombPolynomialsGenerator.COMPUTED_DIRECT_ORDER, false);
            }
            NewcombPolynomialsGenerator.computeUpToDegree(sigma, maxOrder, true);
        } else if (!reverse && rho > NewcombPolynomialsGenerator.COMPUTED_DIRECT_ORDER) {
            NewcombPolynomialsGenerator.computeUpToDegree(rho, maxOrder, false);
        }
        return NewcombPolynomialsGenerator.NEWCOMB_POLYNOMIALS.get(new Couple(rho, sigma));
    }

    /** Generator for Newcomb polynomials. */
    public static class NewcombPolynomialsGenerator {

        /** Store polynomials to avoid recomputation for a given &rho;, &sigma;. */
        private static SortedMap<Couple, List<PolynomialFunction>> NEWCOMB_POLYNOMIALS = new TreeMap<Couple, List<PolynomialFunction>>();

        /** Maximum computed order with &rho;>&sigma;. */
        private static int                                         COMPUTED_DIRECT_ORDER;

        /** Maximum order computed with &rho;<&sigma;. */
        private static int                                         COMPUTED_REVERSE_ORDER;

        /** Recurrence generator. */
        private static RecurrencePolynomialGenerator               POLYNOMIAL_GENERATOR;

        static {
            // Initialize list with Y(rho = 0, sigma = 0) & Y(rho = 1, sigma = 0)
            final List<PolynomialFunction> l00 = new ArrayList<PolynomialFunction>();
            final List<PolynomialFunction> l10 = new ArrayList<PolynomialFunction>();
            final List<PolynomialFunction> l01 = new ArrayList<PolynomialFunction>();

            // For Y(rho = 0, sigma = 0) : Pk0(x) = 1; */
            l00.add(new PolynomialFunction(new double[] {
                1
            }));
            // Y(rho = 1, sigma = 0) = s - 1/2 n, so Pk0(x) = [0, 1] & Pk1(x) = -1/2
            l10.add(new PolynomialFunction(new double[] {
                0, 1
            }));
            l10.add(new PolynomialFunction(new double[] {
                -0.5
            }));
            // Y(rho = 0, sigma = 1) = -s - 1/2 n, so Pk0(x) = [0, -1] & Pk1(x) = -1/2
            l01.add(new PolynomialFunction(new double[] {
                0, -1
            }));
            l01.add(new PolynomialFunction(new double[] {
                -0.5
            }));

            // Initialize polynomials
            NEWCOMB_POLYNOMIALS.put(new Couple(0, 0), l00);
            NEWCOMB_POLYNOMIALS.put(new Couple(1, 0), l10);
            NEWCOMB_POLYNOMIALS.put(new Couple(0, 1), l01);
            COMPUTED_REVERSE_ORDER = 0;
            COMPUTED_DIRECT_ORDER = 0;
            POLYNOMIAL_GENERATOR = new RecurrencePolynomialGenerator();
        }

        /** Private constructor as class is a utility.
         */
        private NewcombPolynomialsGenerator() {
        }

        /** Compute the Modified Newcomb Operators.
         * The computation sequence is the following one (for more clarity, only the subscript are written here.
         *
         * <pre>
         * Y<sub>0, 0</sub>
         * Y<sub>1, 0</sub>
         * Y<sub>2, 0</sub> Y<sub>1, 1</sub>
         * Y<sub>3, 0</sub> Y<sub>2, 1</sub>
         * Y<sub>4, 0</sub> Y<sub>3, 1</sub> Y<sub>2, 2</sub>
         * Y<sub>5, 0</sub> Y<sub>4, 1</sub> Y<sub>3, 2</sub>
         * .....
         *
         * </pre>
         *
         * @param rho degree to reach
         * @param maximumOrder maximum order
         * @param reverseOrder if true the indices are in reverse order
         */
        private static void computeUpToDegree(final int rho, final int maximumOrder, final boolean reverseOrder) {
            boolean sequenceRespected = true;
            // Iteration on rho (need 2 * rho + 1 to get the Y[rho, rho][n, s] computation done
            for (int i = 2 * FastMath.min(maximumOrder, rho) + 1; i < 2 * rho + 1; i++) {
                int k = i;
                int j = 0;
                sequenceRespected = true;
                // Iteration on sigma
                while (sequenceRespected) {

                    // Maximum order
                    final int max = i + j + 1;
                    // Initialize result :
                    List<PolynomialFunction> result = new ArrayList<PolynomialFunction>();
                    initializeListOfPolynomials(max, result);

                    // Create the current couple
                    final Couple couple = reverseOrder ? new Couple(j, k) : new Couple(k, j);

                    // Get the coefficient from the recurrence relation
                    final Map<Integer, List<PolynomialFunction>> map =
                            POLYNOMIAL_GENERATOR.generateRecurrenceCoefficients(couple);

                    // cannot get a Newcomb polynomials with rho < sigma
                    if (couple.rho - 1 >= couple.sigma) {
                        // 2(2s - n) * Y[p - 1, sigma][n, s + 1]
                        final List<PolynomialFunction> poly0 = map.get(0);
                        final List<PolynomialFunction> list0 = NEWCOMB_POLYNOMIALS.get(new Couple(couple.rho - 1, couple.sigma));
                        final List<PolynomialFunction> shiftedList0 = shiftList(list0, 1);
                        result = multiplyPolynomialList(poly0, shiftedList0);
                    }

                    // cannot get a newcomb polynomials with rho < sigma
                    if (couple.rho - 2 >= couple.sigma) {
                        // (s - n) * Y[p - 2, sigma][n, s + 2]
                        final List<PolynomialFunction> poly1 = map.get(1);
                        final List<PolynomialFunction> list1 = NEWCOMB_POLYNOMIALS.get(new Couple(couple.rho - 2, couple.sigma));
                        final List<PolynomialFunction> shiftedList1 = shiftList(list1, 2);
                        result = sumPolynomialList(result, multiplyPolynomialList(poly1, shiftedList1));
                    }

                    // Y[rho, sigma][n, s] is = 0 if rho or sigma < 0
                    if (couple.sigma - 1 >= 0) {
                        // - 2(2s + n) * Y[p, sigma - 1][n, s - 1]
                        final List<PolynomialFunction> poly2 = map.get(2);
                        final List<PolynomialFunction> list2 = NEWCOMB_POLYNOMIALS.get(new Couple(couple.rho, couple.sigma - 1));
                        final List<PolynomialFunction> shiftedList2 = shiftList(list2, -1);
                        result = sumPolynomialList(result, multiplyPolynomialList(poly2, shiftedList2));
                    }

                    // Y[rho, sigma][n, s] is = 0 if rho or sigma < 0
                    if (couple.sigma - 2 >= 0) {
                        // -(s + n) * Y[p, sigma - 2][n, s - 2]
                        final List<PolynomialFunction> poly3 = map.get(3);
                        final List<PolynomialFunction> list3 = NEWCOMB_POLYNOMIALS.get(new Couple(couple.rho, couple.sigma - 2));
                        final List<PolynomialFunction> shiftedList3 = shiftList(list3, -2);
                        result = sumPolynomialList(result, multiplyPolynomialList(poly3, shiftedList3));
                    }

                    // Y[rho, sigma][n, s] is = 0 if rho or sigma < 0
                    if (couple.rho - 1 >= 0 && couple.sigma - 1 >= 0) {
                        // 2(2p + 2sigma + 2 + 3n) * Y[p - 1, sigma - 1][n, s]
                        final List<PolynomialFunction> poly4 = map.get(4);
                        final List<PolynomialFunction> list4 = NEWCOMB_POLYNOMIALS.get(new Couple(couple.rho - 1, couple.sigma - 1));
                        // No shift.
                        result = sumPolynomialList(result, multiplyPolynomialList(poly4, list4));
                    }

                    // Sequence order principle : add +1 to sigma, and decrease rho by 1 at each
                    // iteration, with sigma (j) always inferior or equal to rho (k)
                    NEWCOMB_POLYNOMIALS.put(couple, result);
                    j++;
                    k--;
                    // If the sequence order is not respected, start with a new couple (new rho)
                    sequenceRespected = j <= k;
                }
            }
            // Set the maximum order computed
            COMPUTED_DIRECT_ORDER = (rho > COMPUTED_DIRECT_ORDER) ? rho : COMPUTED_DIRECT_ORDER;
            if (reverseOrder) {
                COMPUTED_REVERSE_ORDER = rho;
            }
        }

        /**
         * Multiply two list of polynomial defined as the internal representation of the Modified
         * Newcomb Operator. Let's call R<sub>s</sub>(n) the result returned by the method :
         *
         * <pre>
         * R<sub>s</sub>(n) = (P<sub>s<sub>0</sub></sub> + P<sub>s<sub>1</sub></sub>n + ... + P<sub>s<sub>j</sub></sub>n<sup>j</sup>) *(Q<sub>s<sub>0</sub></sub> + Q<sub>s<sub>1</sub></sub>n + ... + Q<sub>s<sub>k</sub></sub>n<sup>k</sup>
         * </pre>
         *
         * * where the P<sub>s<sub>j</sub></sub> and Q<sub>s<sub>k</sub></sub> are polynomials in s,
         * s being the index of the Y<sub>&rho;,&sigma;</sub><sup>n,s</sup> function
         *
         * @param poly1
         *            first list of polynomial function
         * @param poly2
         *            second list of polynomial function
         * @return R<sub>s</sub>(n) as a list of {@link PolynomialFunction}
         */
        public static List<PolynomialFunction> multiplyPolynomialList(final List<PolynomialFunction> poly1,
                                                                      final List<PolynomialFunction> poly2) {
            // Initialize the result list of polynomial function
            final List<PolynomialFunction> result = new ArrayList<PolynomialFunction>();
            initializeListOfPolynomials(poly1.size() + poly2.size() - 1, result);

            int i = 0;
            // Iterate over first polynomial list
            for (PolynomialFunction f1 : poly1) {
                // Iterate over second polynomial list
                for (int j = i; j < poly2.size() + i; j++) {
                    final PolynomialFunction p2 = poly2.get(j - i);
                    // Get previous polynomial for current 'n' order
                    final PolynomialFunction previousP2 = result.get(j);
                    // Replace the current order by summing the product of both of the polynomials
                    result.set(j, previousP2.add(f1.multiply(p2)));
                }
                // shift polynomial order in 'n'
                i++;
            }
            return result;
        }

        /** Sum two list of {@link PolynomialFunction}.
         * @param poly1 first list
         * @param poly2 second list
         * @return the summed list
         */
        public static List<PolynomialFunction> sumPolynomialList(final List<PolynomialFunction> poly1,
                                                                 final List<PolynomialFunction> poly2) {
            // identify the lowest degree polynomial
            final int lowLength = FastMath.min(poly1.size(), poly2.size());
            final int highLength = FastMath.max(poly1.size(), poly2.size());
            // Initialize the result list of polynomial function
            final List<PolynomialFunction> result = new ArrayList<PolynomialFunction>();
            initializeListOfPolynomials(highLength, result);

            for (int i = 0; i < lowLength; i++) {
                // Add polynomials by increasing order of 'n'
                result.set(i, poly1.get(i).add(poly2.get(i)));
            }
            // Complete the list if list are of different size:
            for (int i = lowLength; i < highLength; i++) {
                if (poly1.size() < poly2.size()) {
                    result.set(i, poly2.get(i));
                } else {
                    result.set(i, poly1.get(i));
                }
            }
            return result;
        }

        /**
         * Shift a list of {@link PolynomialFunction}, from the
         * {@link PolynomialsUtils#shift(double[], double)} method.
         *
         * @param polynomialList
         *            list of {@link PolynomialFunction}
         * @param shift
         *            shift value
         * @return new list of shifted {@link PolynomialFunction}
         */
        private static List<PolynomialFunction> shiftList(final List<PolynomialFunction> polynomialList,
                                                          final int shift) {
            final List<PolynomialFunction> shiftedList = new ArrayList<PolynomialFunction>();
            for (PolynomialFunction function : polynomialList) {
                shiftedList.add(new PolynomialFunction(PolynomialsUtils.shift(function.getCoefficients(), shift)));
            }
            return shiftedList;
        }

    }

    /** Initialize an empty list of polynomial.
     * @param i order
     * @param result list into which polynomials should be added
     */
    private static void initializeListOfPolynomials(final int i,
                                                    final List<PolynomialFunction> result) {
        for (int k = 0; k < i; k++) {
            result.add(new PolynomialFunction(new double[] {
                0
            }));
        }

    }

    /** Interface for recurrence coefficients generation. */
    private static class RecurrencePolynomialGenerator {

        /** Generate recurrence coefficients.
         * @param couple index couple
         * @return recurrence coefficients
         */
        Map<Integer, List<PolynomialFunction>> generateRecurrenceCoefficients(final Couple couple) {
            final int den = 4 * (couple.rho + couple.sigma);
            // Initialization :
            final Map<Integer, List<PolynomialFunction>> list = new TreeMap<Integer, List<PolynomialFunction>>();
            final List<PolynomialFunction> poly0 = new ArrayList<PolynomialFunction>();
            final List<PolynomialFunction> poly1 = new ArrayList<PolynomialFunction>();
            final List<PolynomialFunction> poly2 = new ArrayList<PolynomialFunction>();
            final List<PolynomialFunction> poly3 = new ArrayList<PolynomialFunction>();
            final List<PolynomialFunction> poly4 = new ArrayList<PolynomialFunction>();
            // 2(2s - n)
            poly0.add(new PolynomialFunction(new double[] {
                0, 4. / den
            }));
            poly0.add(new PolynomialFunction(new double[] {
                -2. / den
            }));
            // (s - n)
            poly1.add(new PolynomialFunction(new double[] {
                0, 1. / den
            }));
            poly1.add(new PolynomialFunction(new double[] {
                -1. / den
            }));
            // - 2(2s + n)
            poly2.add(new PolynomialFunction(new double[] {
                0, -4. / den
            }));
            poly2.add(new PolynomialFunction(new double[] {
                -2. / den
            }));
            // - (s + n)
            poly3.add(new PolynomialFunction(new double[] {
                0, -1. / den
            }));
            poly3.add(new PolynomialFunction(new double[] {
                -1. / den
            }));
            // 2(2 * rho + 2 * sigma + 2 + 3*n)
            poly4.add(new PolynomialFunction(new double[] {
                4. * (couple.rho + couple.sigma + 1) / den
            }));
            poly4.add(new PolynomialFunction(new double[] {
                6. / den
            }));

            // Fill the map :
            list.put(0, poly0);
            list.put(1, poly1);
            list.put(2, poly2);
            list.put(3, poly3);
            list.put(4, poly4);
            return list;

        }
    }

    /** Private class to define a couple of value. */
    private static class Couple implements Comparable<Couple> {

        /** first couple value. */
        private final int rho;

        /** second couple value. */
        private final int sigma;

        /** Constructor.
         * @param rho first couple value
         * @param sigma second couple value
         */
        private Couple(final int rho, final int sigma) {
            this.rho = rho;
            this.sigma = sigma;
        }

        /** {@inheritDoc} */
        public int compareTo(final Couple c) {
            int result = 1;
            if (rho == c.rho) {
                if (sigma < c.sigma) {
                    result = -1;
                } else if (sigma == c.sigma) {
                    result = 0;
                }
            } else if (rho < c.rho) {
                result = -1;
            }
            return result;
        }

        /** {@inheritDoc} */
        public boolean equals(final Object couple) {

            if (couple == this) {
                // first fast check
                return true;
            }

            if ((couple != null) && (couple instanceof Couple)) {
                return (rho == ((Couple) couple).rho) && (sigma == ((Couple) couple).sigma);
            }

            return false;

        }

        /** {@inheritDoc} */
        public int hashCode() {
            return 0x7ab17c0c ^ (rho << 8) ^ sigma;
        }

    }
}