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

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.codehaus.mojo.versions.utils.DefaultArtifactVersionCache;
import org.junit.Test;

import static org.codehaus.mojo.versions.api.Segment.INCREMENTAL;
import static org.codehaus.mojo.versions.api.Segment.MAJOR;
import static org.codehaus.mojo.versions.api.Segment.MINOR;
import static org.codehaus.mojo.versions.api.Segment.SUBINCREMENTAL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Unit tests for {@link BoundArtifactVersion}
 */
public class BoundArtifactVersionTest {
    @Test
    public void testCreateComparableForNoRestrictions() {
        ArtifactVersion comparable = BoundArtifactVersion.createComparable("1.2.3", null);
        assertThat(comparable.toString(), matchesPattern("^\\Q2147483647.2147483647.2147483647\\E.*"));
    }

    @Test
    public void testCreateComparableForMajor() {
        ArtifactVersion comparable = BoundArtifactVersion.createComparable("1.2.3", MAJOR);
        assertThat(comparable.toString(), matchesPattern("^\\Q1.2147483647.2147483647\\E.*"));
    }

    @Test
    public void testCreateComparableForMinor() {
        ArtifactVersion comparable = BoundArtifactVersion.createComparable("1.2.3", MINOR);
        assertThat(comparable.toString(), matchesPattern("^\\Q1.2.2147483647\\E.*"));
    }

    @Test
    public void testCreateComparableForIncremental() {
        ArtifactVersion comparable = BoundArtifactVersion.createComparable("1.2.3", INCREMENTAL);
        assertThat(comparable.toString(), matchesPattern("^\\Q1.2.3\\E.*"));
    }

    @Test
    public void testCreateComparableForSubIncremental() {
        ArtifactVersion comparable = BoundArtifactVersion.createComparable("1.2.3-4", SUBINCREMENTAL);
        assertThat(comparable.toString(), matchesPattern("^\\Q1.2.3.4\\E.*"));
    }


    @Test
    public void testNullLockedGreaterThanNextMajor() {
        BoundArtifactVersion bound = new BoundArtifactVersion("1.2.3", null);
        ArtifactVersion artifactVersion = DefaultArtifactVersionCache.of("2.0.0");
        assertThat(bound.compareTo(artifactVersion), greaterThan(0));
    }

    @Test
    public void testIncrementalLockedGreaterThanNextSubIncremental() {
        BoundArtifactVersion bound = new BoundArtifactVersion("1.2.3-2", INCREMENTAL);
        ArtifactVersion artifactVersion = DefaultArtifactVersionCache.of("1.2.3-3");
        assertThat(bound.compareTo(artifactVersion), greaterThan(0));
    }

    @Test
    public void testVersionShorterThanSegment() {
        BoundArtifactVersion bound = new BoundArtifactVersion("1.1", MINOR);
        ArtifactVersion artifactVersion = DefaultArtifactVersionCache.of("1.1.3");
        assertThat(bound.compareTo(artifactVersion), greaterThan(0));
    }

    @Test
    public void testVersionBoundArtifactVersionShorterThanConcreteVersionAndSegment() {
        BoundArtifactVersion bound = new BoundArtifactVersion("1.1", INCREMENTAL);
        ArtifactVersion artifactVersion = DefaultArtifactVersionCache.of("1.1.3");
        assertThat(bound.compareTo(artifactVersion), lessThan(0));
    }

    @Test
    public void testVersionHeldIncrementalBoundGreaterThanSubIncremental() {
        BoundArtifactVersion bound = new BoundArtifactVersion("1.1", INCREMENTAL);
        ArtifactVersion artifactVersion = DefaultArtifactVersionCache.of("1.1.0-2");
        assertThat(bound.compareTo(artifactVersion), greaterThan(0));
    }

    @Test
    public void testVersionHeldMinorBoundGreaterThanIncremental() {
        BoundArtifactVersion bound = new BoundArtifactVersion("1.1", MINOR);
        ArtifactVersion artifactVersion = DefaultArtifactVersionCache.of("1.1.3");
        assertThat(bound.compareTo(artifactVersion), greaterThan(0));
    }

    @Test
    public void testVersionHeldMajorBoundGreaterThanMinor() {
        BoundArtifactVersion bound = new BoundArtifactVersion("1.1", MAJOR);
        ArtifactVersion artifactVersion = DefaultArtifactVersionCache.of("1.3");
        assertThat(bound.compareTo(artifactVersion), greaterThan(0));
    }

    @Test
    public void testSnapshotWithSubIncremental() {
        BoundArtifactVersion bound = new BoundArtifactVersion("1.0.0-SNAPSHOT", MAJOR);
        ArtifactVersion artifactVersion = DefaultArtifactVersionCache.of("1.0.0");
        assertThat(bound.compareTo(artifactVersion), greaterThan(0));
    }
}
