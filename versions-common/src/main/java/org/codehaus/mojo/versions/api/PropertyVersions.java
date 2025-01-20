package org.codehaus.mojo.versions.api;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.artifact.versioning.Restriction;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.mojo.versions.ordering.BoundArtifactVersion;
import org.codehaus.mojo.versions.ordering.InvalidSegmentException;
import org.codehaus.mojo.versions.ordering.MavenVersionComparator;
import org.codehaus.mojo.versions.ordering.VersionComparator;
import org.codehaus.mojo.versions.utils.ArtifactVersionService;

import static java.util.Optional.empty;
import static org.codehaus.mojo.versions.api.Segment.SUBINCREMENTAL;

/**
 * Manages a property that is associated with one or more artifacts.
 *
 * @author Stephen Connolly
 * @since 1.0-alpha-3
 */
public class PropertyVersions extends AbstractVersionDetails {
    private final String name;

    private final String profileId;

    private final Set<ArtifactAssociation> associations;

    /**
     * The available versions.
     *
     * @since 1.0-beta-1
     */
    private final SortedSet<ArtifactVersion> versions;

    private final PropertyVersions.PropertyVersionComparator comparator;

    private final Log log;

    PropertyVersions(
            VersionsHelper helper, String profileId, String name, Log log, Set<ArtifactAssociation> associations)
            throws VersionRetrievalException {
        this.profileId = profileId;
        this.name = name;
        this.log = log;
        this.associations = new TreeSet<>(associations);
        this.comparator = new PropertyVersionComparator();
        this.versions = helper.resolveAssociatedVersions(associations, comparator);
    }

    /**
     * Gets the rule for version comparison of this artifact.
     *
     * @return the rule for version comparison of this artifact.
     * @since 1.0-beta-1
     */
    public VersionComparator getVersionComparator() {
        return comparator;
    }

    public ArtifactAssociation[] getAssociations() {
        return associations.toArray(new ArtifactAssociation[0]);
    }

    /**
     * Uses the supplied {@link Collection} of {@link Artifact} instances to see if an ArtifactVersion can be provided.
     *
     * @param artifacts The {@link Collection} of {@link Artifact} instances .
     * @return The versions that can be resolved from the supplied Artifact instances or an empty array if no version
     *         can be resolved (i.e. the property is not associated with any of the supplied artifacts or the property
     *         is also associated to an artifact that has not been provided).
     * @since 1.0-alpha-3
     */
    public ArtifactVersion[] getVersions(Collection<Artifact> artifacts) {
        List<ArtifactVersion> result = new ArrayList<>();
        // go through all the associations
        // see if they are met from the collection
        // add the version if they are
        // go through all the versions
        // see if the version is available for all associations
        for (ArtifactAssociation association : associations) {
            for (Artifact artifact : artifacts) {
                if (association.getArtifact().getGroupId().equals(artifact.getGroupId())
                        && association.getArtifact().getArtifactId().equals(artifact.getArtifactId())) {
                    try {
                        result.add(artifact.getSelectedVersion());
                    } catch (OverConstrainedVersionException e) {
                        // ignore this one as we cannot resolve a valid version
                    }
                }
            }
        }
        // we now have a list of all the versions that partially satisfy the association requirements
        Iterator<ArtifactVersion> k = result.iterator();
        versions:
        while (k.hasNext()) {
            ArtifactVersion candidate = k.next();
            associations:
            for (ArtifactAssociation association : associations) {
                for (Artifact artifact : artifacts) {
                    if (association.getArtifact().getGroupId().equals(artifact.getGroupId())
                            && association.getArtifact().getArtifactId().equals(artifact.getArtifactId())) {
                        try {
                            if (candidate
                                    .toString()
                                    .equals(artifact.getSelectedVersion().toString())) {
                                // this association can be met, try the next
                                continue associations;
                            }
                        } catch (OverConstrainedVersionException e) {
                            // ignore this one again
                        }
                    }
                }
                // candidate is not valid as at least one association cannot be met
                k.remove();
                continue versions;
            }
        }
        return asArtifactVersionArray(result);
    }

    /**
     * Uses the {@link DefaultVersionsHelper} to find all available versions that match all the associations with this
     * property.
     *
     * @param includeSnapshots Whether to include snapshot versions in our search.
     * @return The (possibly empty) array of versions.
     */
    public synchronized ArtifactVersion[] getVersions(boolean includeSnapshots) {
        Set<ArtifactVersion> result;
        if (includeSnapshots) {
            result = versions;
        } else {
            result = new TreeSet<>(getVersionComparator());
            for (ArtifactVersion candidate : versions) {
                if (ArtifactUtils.isSnapshot(candidate.toString())) {
                    continue;
                }
                result.add(candidate);
            }
        }
        return asArtifactVersionArray(result);
    }

    private ArtifactVersion[] asArtifactVersionArray(Collection<ArtifactVersion> result) {
        if (result == null || result.isEmpty()) {
            return new ArtifactVersion[0];
        } else {
            final ArtifactVersion[] answer = result.toArray(new ArtifactVersion[0]);
            Arrays.sort(answer);
            return answer;
        }
    }

    public String getName() {
        return name;
    }

    public String getProfileId() {
        return profileId;
    }

    public boolean isAssociated() {
        return !associations.isEmpty();
    }

    public String toString() {
        return "PropertyVersions{" + (profileId == null ? "" : "profileId='" + profileId + "', ") + "name='" + name
                + '\'' + ", associations=" + associations + '}';
    }

    public ArtifactVersion getNewestVersion(
            String currentVersion,
            Property property,
            boolean allowSnapshots,
            List<MavenProject> reactorProjects,
            VersionsHelper helper)
            throws InvalidVersionSpecificationException, InvalidSegmentException {
        return getNewestVersion(currentVersion, property, allowSnapshots, reactorProjects, false, empty());
    }

    /**
     * Retrieves the newest artifact version for the given property-denoted artifact or {@code null} if no newer
     * version could be found.
     *
     * @param versionString     current version of the artifact
     * @param property          property name indicating the artifact
     * @param allowSnapshots    whether snapshots should be considered
     * @param reactorProjects   collection of reactor projects
     * @param allowDowngrade    whether downgrades should be allowed
     * @param upperBoundSegment the upper bound segment; empty() means no upper bound
     * @return newest artifact version fulfilling the criteria or null if no newer version could be found
     * @throws InvalidSegmentException              thrown if the {@code unchangedSegment} is not valid (e.g. greater
     *                                              than the number
     *                                              of segments in the version string)
     * @throws InvalidVersionSpecificationException thrown if the version string in the property is not valid
     */
    public ArtifactVersion getNewestVersion(
            String versionString,
            Property property,
            boolean allowSnapshots,
            Collection<MavenProject> reactorProjects,
            boolean allowDowngrade,
            Optional<Segment> upperBoundSegment)
            throws InvalidSegmentException, InvalidVersionSpecificationException {
        final boolean includeSnapshots = !property.isBanSnapshots() && allowSnapshots;
        log.debug("getNewestVersion(): includeSnapshots='" + includeSnapshots + "'");
        log.debug("Property ${" + property.getName() + "}: Set of valid available versions is "
                + Arrays.asList(getVersions(includeSnapshots)));
        VersionRange range =
                property.getVersion() != null ? VersionRange.createFromVersionSpec(property.getVersion()) : null;
        log.debug("Property ${" + property.getName() + "}: Restricting results to " + range);

        ArtifactVersion currentVersion = ArtifactVersionService.getArtifactVersion(versionString);
        ArtifactVersion lowerBound = allowDowngrade
                ? getLowerBound(currentVersion, upperBoundSegment)
                        .map(ArtifactVersionService::getArtifactVersion)
                        .orElse(null)
                : currentVersion;
        if (log.isDebugEnabled()) {
            log.debug("lowerBoundArtifactVersion: " + lowerBound);
        }

        ArtifactVersion upperBound = !upperBoundSegment.isPresent()
                ? null
                : upperBoundSegment
                        .map(s -> (ArtifactVersion) new BoundArtifactVersion(
                                currentVersion, s.isMajorTo(SUBINCREMENTAL) ? Segment.minorTo(s) : s))
                        .orElse(null);
        if (log.isDebugEnabled()) {
            log.debug("Property ${" + property.getName() + "}: upperBound is: " + upperBound);
        }

        Restriction restriction = new Restriction(lowerBound, allowDowngrade, upperBound, allowDowngrade);
        ArtifactVersion result = getNewestVersion(range, restriction, includeSnapshots);

        log.debug("Property ${" + property.getName() + "}: Current winner is: " + result);

        if (property.isSearchReactor()) {
            log.debug("Property ${" + property.getName() + "}: Searching reactor for a valid version...");
            Set<Artifact> reactorArtifacts =
                    reactorProjects.stream().map(MavenProject::getArtifact).collect(Collectors.toSet());
            ArtifactVersion[] reactorVersions = getVersions(reactorArtifacts);
            log.debug("Property ${" + property.getName()
                    + "}: Set of valid available versions from the reactor is "
                    + Arrays.asList(reactorVersions));
            ArtifactVersion fromReactor = null;
            if (reactorVersions.length > 0) {
                for (int j = reactorVersions.length - 1; j >= 0; j--) {
                    if (range == null || ArtifactVersions.isVersionInRange(reactorVersions[j], range)) {
                        fromReactor = reactorVersions[j];
                        log.debug("Property ${" + property.getName() + "}: Reactor has version " + fromReactor);
                        break;
                    }
                }
            }
            if (fromReactor != null && (result != null || !currentVersion.equals(fromReactor.toString()))) {
                if (property.isPreferReactor()) {
                    log.debug(
                            "Property ${" + property.getName() + "}: Reactor has a version and we prefer the reactor");
                    result = fromReactor;
                } else {
                    if (result == null) {
                        log.debug("Property ${" + property.getName() + "}: Reactor has the only version");
                        result = fromReactor;
                    } else if (getVersionComparator().compare(result, fromReactor) < 0) {
                        log.debug("Property ${" + property.getName() + "}: Reactor has a newer version");
                        result = fromReactor;
                    } else {
                        log.debug("Property ${" + property.getName() + "}: Reactor has the same or older version");
                    }
                }
            }
        }
        return result;
    }

    private final class PropertyVersionComparator implements VersionComparator {
        public int compare(ArtifactVersion v1, ArtifactVersion v2) {
            return innerCompare(v1, v2);
        }

        private int innerCompare(ArtifactVersion v1, ArtifactVersion v2) {
            if (!isAssociated()) {
                throw new IllegalStateException("Cannot compare versions for a property with no associations");
            }
            return v1.compareTo(v2);
        }

        public int getSegmentCount(ArtifactVersion v) {
            if (!isAssociated()) {
                throw new IllegalStateException("Cannot compare versions for a property with no associations");
            }
            return MavenVersionComparator.INSTANCE.getSegmentCount(v);
        }
    }
}
