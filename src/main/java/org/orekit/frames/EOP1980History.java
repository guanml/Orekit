/* Copyright 2002-2013 CS Systèmes d'Information
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
package org.orekit.frames;

import java.util.Collection;

/** This class holds Earth Orientation Parameters (IAU1980) data throughout a large time range.
 * @author Pascal Parraud
 */
public class EOP1980History extends AbstractEOPHistory {

    /** Serializable UID. */
    private static final long serialVersionUID = 3003752420705950441L;

    /**
     * Simple constructor.
     *
     * @param data the {@link EOP1980Entry} data to include in this history.
     * @see AbstractEOPHistory#AbstractEOPHistory(Collection)
     */
    public EOP1980History(final Collection<? extends EOP1980Entry> data) {
        super(data);
    }

}
