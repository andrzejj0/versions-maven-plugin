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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.Restriction;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.proxy.ProxyInfo;
import org.codehaus.mojo.versions.model.IgnoreVersion;
import org.codehaus.mojo.versions.ordering.VersionComparator;
import org.codehaus.mojo.versions.rule.RuleService;
import org.codehaus.mojo.versions.utils.ArtifactCreationService;
import org.codehaus.mojo.versions.utils.ArtifactVersionService;
import org.codehaus.mojo.versions.utils.DependencyComparator;
import org.codehaus.mojo.versions.utils.PluginComparator;
import org.codehaus.mojo.versions.utils.VersionsExpressionEvaluator;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResolutionException;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static org.apache.maven.RepositoryUtils.toArtifact;
;

/**
 * Helper class that provides common functionality required by both the mojos and the reports.
 *
 * @author Stephen Connolly
 * @since 1.0-alpha-3
 */
public class DefaultVersionsHelper implements VersionsHelper {
    private static final String CLASSPATH_PROTOCOL = "classpath";

    private static final int LOOKUP_PARALLEL_THREADS = 5;

    private final RuleService ruleService;

    private final ArtifactCreationService artifactCreationService;

    private final RepositorySystem repositorySystem;

    /**
     * The {@link Log} to send log messages to.
     *
     * @since 1.0-alpha-3
     */
    private final Log log;

    /**
     * The maven session.
     *
     * @since 1.0-beta-1
     */
    private final MavenSession mavenSession;

    private final MojoExecution mojoExecution;

    private final List<RemoteRepository> remotePluginRepositories;

    private final List<RemoteRepository> remoteProjectRepositories;

    private PomHelper pomHelper;

    /**
     * Private constructor used by the builder
     */
    private DefaultVersionsHelper(
            PomHelper pomHelper,
            ArtifactCreationService artifactCreationService,
            RepositorySystem repositorySystem,
            MavenSession mavenSession,
            MojoExecution mojoExecution,
            RuleService ruleService,
            Log log) {
        this.pomHelper = requireNonNull(pomHelper);
        this.artifactCreationService = requireNonNull(artifactCreationService);
        this.repositorySystem = requireNonNull(repositorySystem);
        this.mavenSession = requireNonNull(mavenSession);
        this.mojoExecution = requireNonNull(mojoExecution);
        this.ruleService = requireNonNull(ruleService);
        this.log = requireNonNull(log);

        this.remoteProjectRepositories = of(mavenSession)
                .map(MavenSession::getCurrentProject)
                .map(MavenProject::getRemoteProjectRepositories)
                .map(DefaultVersionsHelper::adjustRemoteRepositoriesRefreshPolicy)
                .orElseGet(Collections::emptyList);

        this.remotePluginRepositories = of(mavenSession)
                .map(MavenSession::getCurrentProject)
                .map(MavenProject::getRemotePluginRepositories)
                .map(DefaultVersionsHelper::adjustRemoteRepositoriesRefreshPolicy)
                .orElseGet(Collections::emptyList);
    }

    static List<RemoteRepository> adjustRemoteRepositoriesRefreshPolicy(List<RemoteRepository> remoteRepositories) {
        return remoteRepositories.stream()
                .map(DefaultVersionsHelper::adjustRemoteRepositoryRefreshPolicy)
                .collect(Collectors.toList());
    }

    static RemoteRepository adjustRemoteRepositoryRefreshPolicy(RemoteRepository remoteRepository) {
        RepositoryPolicy snapshotPolicy = remoteRepository.getPolicy(true);
        RepositoryPolicy releasePolicy = remoteRepository.getPolicy(false);

        RepositoryPolicy newSnapshotPolicy = null;
        RepositoryPolicy newReleasePolicy = null;

        if (snapshotPolicy.isEnabled()
                && RepositoryPolicy.UPDATE_POLICY_NEVER.equals(snapshotPolicy.getUpdatePolicy())) {
            newSnapshotPolicy = new RepositoryPolicy(
                    true, RepositoryPolicy.UPDATE_POLICY_DAILY, snapshotPolicy.getChecksumPolicy());
        }

        if (releasePolicy.isEnabled() && RepositoryPolicy.UPDATE_POLICY_NEVER.equals(releasePolicy.getUpdatePolicy())) {
            newReleasePolicy =
                    new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_DAILY, releasePolicy.getChecksumPolicy());
        }

        if (newSnapshotPolicy != null || newReleasePolicy != null) {
            RemoteRepository.Builder builder = new RemoteRepository.Builder(remoteRepository);
            if (newSnapshotPolicy != null) {
                builder.setSnapshotPolicy(newSnapshotPolicy);
            }
            if (newReleasePolicy != null) {
                builder.setReleasePolicy(newReleasePolicy);
            }
            return builder.build();
        } else {
            return remoteRepository;
        }
    }

    static boolean isClasspathUri(String uri) {
        return (uri != null && uri.startsWith(CLASSPATH_PROTOCOL + ":"));
    }

    @Override
    public ArtifactVersions lookupArtifactVersions(
            Artifact artifact, VersionRange versionRange, boolean usePluginRepositories)
            throws VersionRetrievalException {
        return lookupArtifactVersions(artifact, versionRange, usePluginRepositories, !usePluginRepositories);
    }

    @Override
    public ArtifactVersions lookupArtifactVersions(
            Artifact artifact, VersionRange versionRange, boolean usePluginRepositories, boolean useProjectRepositories)
            throws VersionRetrievalException {
        try {
            Collection<IgnoreVersion> ignoredVersions = ruleService.getIgnoredVersions(artifact);
            if (!ignoredVersions.isEmpty() && log.isDebugEnabled()) {
                log.debug("Found ignored versions: " + ignoredVersions + " for artifact" + artifact);
            }

            return new ArtifactVersions(
                    artifact,
                    repositorySystem
                            .resolveVersionRange(
                                    mavenSession.getRepositorySession(),
                                    new VersionRangeRequest(
                                            toArtifact(artifact)
                                                    .setVersion(ofNullable(versionRange)
                                                            .map(VersionRange::getRestrictions)
                                                            .flatMap(list -> list.stream()
                                                                    .findFirst()
                                                                    .map(Restriction::toString))
                                                            .orElse("(,)")),
                                            Stream.concat(
                                                            usePluginRepositories
                                                                    ? remotePluginRepositories.stream()
                                                                    : Stream.empty(),
                                                            useProjectRepositories
                                                                    ? remoteProjectRepositories.stream()
                                                                    : Stream.empty())
                                                    .distinct()
                                                    .collect(Collectors.toList()),
                                            "lookupArtifactVersions"))
                            .getVersions()
                            .stream()
                            .filter(v -> ignoredVersions.stream().noneMatch(i -> {
                                if (IgnoreVersionHelper.isVersionIgnored(v, i)) {
                                    if (log.isDebugEnabled()) {
                                        log.debug("Version " + v + " for artifact "
                                                + ArtifactUtils.versionlessKey(artifact)
                                                + " found on ignore list: "
                                                + i);
                                    }
                                    return true;
                                }

                                return false;
                            }))
                            .map(v -> ArtifactVersionService.getArtifactVersion(v.toString()))
                            .collect(Collectors.toList()),
                    ruleService.getVersionComparator(artifact));
        } catch (VersionRangeResolutionException e) {
            throw new VersionRetrievalException(e.getMessage(), e);
        }
    }

    public SortedSet<ArtifactVersion> resolveAssociatedVersions(
            Set<ArtifactAssociation> associations, VersionComparator versionComparator)
            throws VersionRetrievalException {
        SortedSet<ArtifactVersion> versions = null;
        for (ArtifactAssociation association : associations) {
            final ArtifactVersions associatedVersions =
                    lookupArtifactVersions(association.getArtifact(), association.isUsePluginRepositories());
            if (versions != null) {
                final ArtifactVersion[] artifactVersions = associatedVersions.getVersions(true);
                // since ArtifactVersion does not override equals, we have to do this the hard way
                // result.retainAll( Arrays.asList( artifactVersions ) );
                Iterator<ArtifactVersion> j = versions.iterator();
                while (j.hasNext()) {
                    boolean contains = false;
                    ArtifactVersion version = j.next();
                    for (ArtifactVersion artifactVersion : artifactVersions) {
                        if (version.compareTo(artifactVersion) == 0) {
                            contains = true;
                            break;
                        }
                    }
                    if (!contains) {
                        j.remove();
                    }
                }
            } else {
                versions = new TreeSet<>(versionComparator);
                versions.addAll(Arrays.asList(associatedVersions.getVersions(true)));
            }
        }
        if (versions == null) {
            versions = new TreeSet<>(versionComparator);
        }
        return Collections.unmodifiableSortedSet(versions);
    }

    @Override
    public ArtifactVersions lookupArtifactVersions(Artifact artifact, boolean usePluginRepositories)
            throws VersionRetrievalException {
        return lookupArtifactVersions(artifact, null, usePluginRepositories);
    }

    @Override
    public void resolveArtifact(Artifact artifact, boolean usePluginRepositories) throws ArtifactResolutionException {
        try {
            ArtifactResult artifactResult = repositorySystem.resolveArtifact(
                    mavenSession.getRepositorySession(),
                    new ArtifactRequest(
                            toArtifact(artifact),
                            usePluginRepositories
                                    ? mavenSession.getCurrentProject().getRemotePluginRepositories()
                                    : mavenSession.getCurrentProject().getRemoteProjectRepositories(),
                            getClass().getName()));
            artifact.setFile(artifactResult.getArtifact().getFile());
            artifact.setVersion(artifactResult.getArtifact().getVersion());
            artifact.setResolved(artifactResult.isResolved());
        } catch (org.eclipse.aether.resolution.ArtifactResolutionException e) {
            throw new ArtifactResolutionException(e.getMessage(), artifact);
        }
    }

    @Override
    public Set<Artifact> extractArtifacts(Collection<MavenProject> mavenProjects) {
        Set<Artifact> result = new HashSet<>();
        for (MavenProject project : mavenProjects) {
            result.add(project.getArtifact());
        }

        return result;
    }

    public Map<Dependency, ArtifactVersions> lookupDependenciesUpdates(
            Stream<Dependency> dependencies,
            boolean usePluginRepositories,
            boolean useProjectRepositories,
            boolean allowSnapshots)
            throws VersionRetrievalException {
        ExecutorService executor = Executors.newFixedThreadPool(LOOKUP_PARALLEL_THREADS);
        try {
            Map<Dependency, ArtifactVersions> dependencyUpdates = new TreeMap<>(DependencyComparator.INSTANCE);
            List<Future<? extends Pair<Dependency, ArtifactVersions>>> futures = dependencies
                    .map(dependency -> executor.submit(() -> new ImmutablePair<>(
                            dependency,
                            lookupDependencyUpdates(
                                    dependency, usePluginRepositories, useProjectRepositories, allowSnapshots))))
                    .collect(Collectors.toList());
            for (Future<? extends Pair<Dependency, ArtifactVersions>> details : futures) {
                Pair<Dependency, ArtifactVersions> pair = details.get();
                dependencyUpdates.put(pair.getKey(), pair.getValue());
            }

            return dependencyUpdates;
        } catch (ExecutionException | InterruptedException ie) {
            throw new VersionRetrievalException(
                    "Unable to acquire metadata for dependencies " + dependencies + ": " + ie.getMessage(), ie);
        } finally {
            executor.shutdown();
        }
    }

    @Override
    public Map<Dependency, ArtifactVersions> lookupDependenciesUpdates(
            Stream<Dependency> dependencies, boolean usePluginRepositories, boolean allowSnapshots)
            throws VersionRetrievalException {
        return lookupDependenciesUpdates(dependencies, usePluginRepositories, !usePluginRepositories, allowSnapshots);
    }

    @Override
    public ArtifactVersions lookupDependencyUpdates(
            Dependency dependency,
            boolean usePluginRepositories,
            boolean useProjectRepositories,
            boolean allowSnapshots)
            throws VersionRetrievalException {
        ArtifactVersions allVersions = lookupArtifactVersions(
                artifactCreationService.createArtifact(dependency),
                null,
                usePluginRepositories,
                useProjectRepositories);
        return new ArtifactVersions(
                allVersions.getArtifact(),
                Arrays.stream(allVersions.getAllUpdates(allowSnapshots)).collect(Collectors.toList()),
                allVersions.getVersionComparator());
    }

    @Override
    public Map<Plugin, PluginUpdatesDetails> lookupPluginsUpdates(Stream<Plugin> plugins, boolean allowSnapshots)
            throws VersionRetrievalException {
        ExecutorService executor = Executors.newFixedThreadPool(LOOKUP_PARALLEL_THREADS);
        try {
            Map<Plugin, PluginUpdatesDetails> pluginUpdates = new TreeMap<>(PluginComparator.INSTANCE);
            List<Future<? extends Pair<Plugin, PluginUpdatesDetails>>> futures = plugins.map(
                            p -> executor.submit(() -> new ImmutablePair<>(p, lookupPluginUpdates(p, allowSnapshots))))
                    .collect(Collectors.toList());
            for (Future<? extends Pair<Plugin, PluginUpdatesDetails>> details : futures) {
                Pair<Plugin, PluginUpdatesDetails> pair = details.get();
                pluginUpdates.put(pair.getKey(), pair.getValue());
            }

            return pluginUpdates;
        } catch (ExecutionException | InterruptedException ie) {
            throw new VersionRetrievalException(
                    "Unable to acquire metadata for plugins " + plugins + ": " + ie.getMessage(), ie);
        } finally {
            executor.shutdown();
        }
    }

    @Override
    public PluginUpdatesDetails lookupPluginUpdates(Plugin plugin, boolean allowSnapshots)
            throws VersionRetrievalException {
        String version = plugin.getVersion() != null ? plugin.getVersion() : "LATEST";

        Set<Dependency> pluginDependencies = new TreeSet<>(DependencyComparator.INSTANCE);
        if (plugin.getDependencies() != null) {
            pluginDependencies.addAll(plugin.getDependencies());
        }
        Map<Dependency, ArtifactVersions> pluginDependencyDetails =
                lookupDependenciesUpdates(pluginDependencies.stream(), false, allowSnapshots);

        ArtifactVersions allVersions = lookupArtifactVersions(
                artifactCreationService.createMavenPluginArtifact(plugin.getGroupId(), plugin.getArtifactId(), version),
                true);
        ArtifactVersions updatedVersions = new ArtifactVersions(
                allVersions.getArtifact(),
                Arrays.stream(allVersions.getAllUpdates(allowSnapshots)).collect(Collectors.toList()),
                allVersions.getVersionComparator());
        return new PluginUpdatesDetails(updatedVersions, pluginDependencyDetails, allowSnapshots);
    }

    @Override
    public ExpressionEvaluator getExpressionEvaluator(MavenProject project) {
        return new VersionsExpressionEvaluator(mavenSession, mojoExecution);
    }

    @Override
    public Map<Property, PropertyVersions> getVersionPropertiesMap(VersionPropertiesMapRequest request)
            throws MojoExecutionException {
        Map<String, Property> properties = new HashMap<>();
        if (request.getPropertyDefinitions() != null) {
            Arrays.stream(request.getPropertyDefinitions()).forEach(p -> properties.put(p.getName(), p));
        }
        Map<String, PropertyVersionsBuilder> builders = new HashMap<>();
        if (request.isAutoLinkItems()) {
            final PropertyVersionsBuilder[] propertyVersionsBuilders;
            try {
                propertyVersionsBuilders = pomHelper.getPropertyVersionsBuilders(
                        this, log, request.getMavenProject(), request.isIncludeParent());
            } catch (ExpressionEvaluationException | IOException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }

            for (PropertyVersionsBuilder propertyVersionsBuilder : propertyVersionsBuilders) {
                final String propertyName = propertyVersionsBuilder.getName();
                builders.put(propertyName, propertyVersionsBuilder);
                if (!properties.containsKey(propertyName)) {
                    final Property property = new Property(propertyName);
                    log.debug("Property ${" + propertyName + "}: Adding inferred version range of "
                            + propertyVersionsBuilder.getVersionRange());
                    property.setVersion(propertyVersionsBuilder.getVersionRange());
                    properties.put(propertyName, property);
                }
            }
        }

        List<String> includePropertiesList = request.getIncludeProperties() != null
                ? Arrays.asList(request.getIncludeProperties().split("\\s*,\\s*"))
                : Collections.emptyList();
        List<String> excludePropertiesList = request.getExcludeProperties() != null
                ? Arrays.asList(request.getExcludeProperties().split("\\s*,\\s*"))
                : Collections.emptyList();

        log.debug("Searching for properties associated with builders");
        Iterator<Property> i = properties.values().iterator();
        while (i.hasNext()) {
            Property property = i.next();

            log.debug("includePropertiesList:" + includePropertiesList + " property: " + property.getName());
            log.debug("excludePropertiesList:" + excludePropertiesList + " property: " + property.getName());
            if (!includePropertiesList.isEmpty() && !includePropertiesList.contains(property.getName())) {
                log.debug("Skipping property ${" + property.getName() + "}");
                i.remove();
            } else if (!excludePropertiesList.isEmpty() && excludePropertiesList.contains(property.getName())) {
                log.debug("Ignoring property ${" + property.getName() + "}");
                i.remove();
            }
        }
        i = properties.values().iterator();
        Map<Property, PropertyVersions> propertyVersions = new LinkedHashMap<>(properties.size());
        while (i.hasNext()) {
            Property property = i.next();
            log.debug("Property ${" + property.getName() + "}");
            PropertyVersionsBuilder builder = builders.get(property.getName());
            if (builder == null || !builder.isAssociated()) {
                log.debug("Property ${" + property.getName() + "}: Looks like this property is not "
                        + "associated with any dependency...");
                builder = new PropertyVersionsBuilder(this, ruleService, null, property.getName(), log);
            }
            if (!property.isAutoLinkDependencies()) {
                log.debug("Property ${" + property.getName() + "}: Removing any autoLinkDependencies");
                builder.clearAssociations();
            }
            Dependency[] dependencies = property.getDependencies();
            if (dependencies != null) {
                for (Dependency dependency : dependencies) {
                    log.debug("Property ${" + property.getName() + "}: Adding association to " + dependency);
                    builder.withAssociation(artifactCreationService.createArtifact(dependency), false);
                }
            }
            try {
                if (property.isAutoLinkDependencies()
                        && StringUtils.isEmpty(property.getVersion())
                        && !StringUtils.isEmpty(builder.getVersionRange())) {
                    log.debug("Property ${" + property.getName() + "}: Adding inferred version range of "
                            + builder.getVersionRange());
                    property.setVersion(builder.getVersionRange());
                }
                final String currentVersion =
                        request.getMavenProject().getProperties().getProperty(property.getName());
                property.setValue(currentVersion);
                final PropertyVersions versions;
                try {
                    if (currentVersion != null) {
                        builder.withCurrentVersion(ArtifactVersionService.getArtifactVersion(currentVersion))
                                .withCurrentVersionRange(VersionRange.createFromVersionSpec(currentVersion));
                    }
                } catch (InvalidVersionSpecificationException e) {
                    throw new RuntimeException(e);
                }
                versions = builder.build();
                propertyVersions.put(property, versions);
            } catch (VersionRetrievalException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }
        return propertyVersions;
    }

    /**
     * Builder class for {@linkplain DefaultVersionsHelper}
     */
    public static class Builder {
        private Log log;
        private MavenSession mavenSession;
        private MojoExecution mojoExecution;
        private RepositorySystem repositorySystem;
        private RuleService ruleService;
        private ArtifactCreationService artifactCreationService;
        private PomHelper pomHelper;

        public Builder() {}

        private Optional<ProxyInfo> getProxyInfo(RemoteRepository repository) {
            return ofNullable(repository.getProxy()).map(proxy -> new ProxyInfo() {
                {
                    setHost(proxy.getHost());
                    setPort(proxy.getPort());
                    setType(proxy.getType());
                    ofNullable(proxy.getAuthentication()).ifPresent(auth -> {
                        try (AuthenticationContext authCtx =
                                AuthenticationContext.forProxy(mavenSession.getRepositorySession(), repository)) {
                            ofNullable(authCtx.get(AuthenticationContext.USERNAME))
                                    .ifPresent(this::setUserName);
                            ofNullable(authCtx.get(AuthenticationContext.PASSWORD))
                                    .ifPresent(this::setPassword);
                            ofNullable(authCtx.get(AuthenticationContext.NTLM_DOMAIN))
                                    .ifPresent(this::setNtlmDomain);
                            ofNullable(authCtx.get(AuthenticationContext.NTLM_WORKSTATION))
                                    .ifPresent(this::setNtlmHost);
                        }
                    });
                }
            });
        }

        private Optional<AuthenticationInfo> getAuthenticationInfo(RemoteRepository repository) {
            return ofNullable(repository.getAuthentication()).map(authentication -> new AuthenticationInfo() {
                {
                    try (AuthenticationContext authCtx =
                            AuthenticationContext.forRepository(mavenSession.getRepositorySession(), repository)) {
                        ofNullable(authCtx.get(AuthenticationContext.USERNAME)).ifPresent(this::setUserName);
                        ofNullable(authCtx.get(AuthenticationContext.PASSWORD)).ifPresent(this::setPassword);
                        ofNullable(authCtx.get(AuthenticationContext.PRIVATE_KEY_PASSPHRASE))
                                .ifPresent(this::setPassphrase);
                        ofNullable(authCtx.get(AuthenticationContext.PRIVATE_KEY_PATH))
                                .ifPresent(this::setPrivateKey);
                    }
                }
            });
        }

        private org.apache.maven.wagon.repository.Repository wagonRepository(RemoteRepository repository) {
            return new org.apache.maven.wagon.repository.Repository(repository.getId(), repository.getUrl());
        }

        public static Optional<String> protocol(final String url) {
            int pos = url.indexOf(":");
            return pos == -1 ? empty() : of(url.substring(0, pos).trim());
        }

        public Builder withLog(Log log) {
            this.log = log;
            return this;
        }

        public Builder withMavenSession(MavenSession mavenSession) {
            this.mavenSession = mavenSession;
            return this;
        }

        public Builder withMojoExecution(MojoExecution mojoExecution) {
            this.mojoExecution = mojoExecution;
            return this;
        }

        public Builder withRepositorySystem(RepositorySystem repositorySystem) {
            this.repositorySystem = repositorySystem;
            return this;
        }

        public Builder withRuleService(RuleService ruleService) {
            this.ruleService = ruleService;
            return this;
        }

        public Builder withArtifactCreationService(ArtifactCreationService artifactCreationService) {
            this.artifactCreationService = artifactCreationService;
            return this;
        }

        public Builder withPomHelper(PomHelper pomHelper) {
            this.pomHelper = pomHelper;
            return this;
        }

        /**
         * Builds the constructed {@linkplain DefaultVersionsHelper} object
         * @return constructed {@linkplain DefaultVersionsHelper}
         * @throws MojoExecutionException should the constructor with the RuleSet retrieval doesn't succeed
         */
        public DefaultVersionsHelper build() throws MojoExecutionException {
            return new DefaultVersionsHelper(
                    pomHelper,
                    artifactCreationService,
                    repositorySystem,
                    mavenSession,
                    mojoExecution,
                    ruleService,
                    log);
        }
    }
}
