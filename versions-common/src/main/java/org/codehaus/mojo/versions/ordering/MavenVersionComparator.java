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

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * A comparator which uses Maven's version rules, i.e. 1.3.34 &gt; 1.3.9 but 1.3.4.3.2.34 &lt; 1.3.4.3.2.9.
 *
 * @author Stephen Connolly
 * @since 1.0-alpha-3
 */
public class MavenVersionComparator extends AbstractVersionComparator {

    /**
     * {@inheritDoc}
     */
    public int compare(ArtifactVersion o1, ArtifactVersion o2) {
        if (o1 instanceof BoundArtifactVersion) {
            return o1.compareTo(o2);
        }
        return new ComparableVersion(o1.toString()).compareTo(new ComparableVersion(o2.toString()));
    }

    /**
     * {@inheritDoc}
     */
    protected int innerGetSegmentCount(ArtifactVersion v) {
        // if the version does not match the maven rules, then we have only one segment
        // i.e. the qualifier
        if (v.getBuildNumber() != 0) {
            // the version was successfully parsed, and we have a build number
            // have to have four segments
            return 4;
        }
        if ((v.getMajorVersion() != 0 || v.getMinorVersion() != 0 || v.getIncrementalVersion() != 0)
                && v.getQualifier() != null) {
            // the version was successfully parsed, and we have a qualifier
            // have to have four segments
            return 4;
        }
        final String version = v.toString();
        if (version.indexOf('-') != -1) {
            // the version has parts and was not parsed successfully
            // have to have one segment
            return version.equals(v.getQualifier()) ? 1 : 4;
        }
        if (version.indexOf('.') != -1) {
            // the version has parts and was not parsed successfully
            // have to have one segment
            return version.equals(v.getQualifier()) ? 1 : 3;
        }
        if (StringUtils.isEmpty(version)) {
            return 3;
        }
        try {
            Integer.parseInt(version);
            return 3;
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
