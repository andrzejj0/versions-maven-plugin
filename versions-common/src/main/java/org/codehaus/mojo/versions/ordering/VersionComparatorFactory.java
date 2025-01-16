package org.codehaus.mojo.versions.ordering;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/**
 * Factory for {@link VersionComparator} instances
 */
public final class VersionComparatorFactory {
    private static final VersionComparator MAVEN_VERSION_COMPARATOR = new MavenVersionComparator();

    /**
     * Returns the version comparator to use.
     *
     * @param comparisonMethod the comparison method.
     * @return the version comparator to use.
     * @since 1.0-alpha-1
     */
    public static VersionComparator getVersionComparator(String comparisonMethod) {
        switch (comparisonMethod) {
            case "numeric":
                return new NumericVersionComparator();
            case "mercury":
                return new MercuryVersionComparator();
            case "maven":
            default:
                // be lenient so that we're backwards compatible
                return MAVEN_VERSION_COMPARATOR;
        }
    }
}
