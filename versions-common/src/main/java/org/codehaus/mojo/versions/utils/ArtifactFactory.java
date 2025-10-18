package org.codehaus.mojo.versions.utils;

import javax.inject.Inject;
import javax.inject.Named;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Dependency;

/**
 * Factory for creating {@link Artifact} instances.
 */
@Named
public class ArtifactFactory {
    private final ArtifactHandlerManager artifactHandlerManager;
    private final Map<ArtifactIdentifier, Artifact> artifactCache = new ConcurrentHashMap<>();

    private static class ArtifactIdentifier {
        String groupId;
        String artifactId;
        String version;
        String type;
        String classifier;
        String scope;
        boolean optional;

        ArtifactIdentifier(
                String groupId,
                String artifactId,
                String version,
                String type,
                String classifier,
                String scope,
                boolean optional) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.type = type;
            this.classifier = classifier;
            this.scope = scope;
            this.optional = optional;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ArtifactIdentifier)) {
                return false;
            }
            ArtifactIdentifier that = (ArtifactIdentifier) o;
            return optional == that.optional
                    && Objects.equals(groupId, that.groupId)
                    && Objects.equals(artifactId, that.artifactId)
                    && Objects.equals(version, that.version)
                    && Objects.equals(type, that.type)
                    && Objects.equals(classifier, that.classifier)
                    && Objects.equals(scope, that.scope);
        }

        @Override
        public int hashCode() {
            int result = groupId.hashCode();
            result = 31 * result + (artifactId != null ? artifactId.hashCode() : 47);
            result = 31 * result + (version != null ? version.hashCode() : 89);
            result = 31 * result + (type != null ? type.hashCode() : 127);
            result = 31 * result + (classifier != null ? classifier.hashCode() : 167);
            result = 31 * result + (scope != null ? scope.hashCode() : 223);
            result = 31 * result + (optional ? 233 : 0);
            return result;
        }
    }

    /**
     * Constructs a new instance
     * @param artifactHandlerManager {@link ArtifactHandlerManager} instance
     */
    @Inject
    public ArtifactFactory(ArtifactHandlerManager artifactHandlerManager) {
        this.artifactHandlerManager = artifactHandlerManager;
    }

    private Artifact createArtifact(ArtifactIdentifier id, Supplier<Artifact> supplier) {
        return artifactCache.computeIfAbsent(id, __ -> supplier.get());
    }

    /**
     * Creates a new {@link Artifact} instance with the given parameters.
     * The version parameter may be a version range.
     * @param groupId the groupId
     * @param artifactId the artifactId
     * @param version the version or version range
     * @param type the type
     * @param classifier the classifier
     * @param scope the scope
     * @param optional whether the dependency is optional
     * @return the created {@link Artifact} instance
     * @throws RuntimeException if the version parameter is not a valid version or version range
     * (in this case the cause will be an {@link InvalidVersionSpecificationException})
     */
    public Artifact createArtifact(
            String groupId,
            String artifactId,
            String version,
            String type,
            String classifier,
            String scope,
            boolean optional) {
        return createArtifact(
                new ArtifactIdentifier(groupId, artifactId, version, type, classifier, scope, optional), () -> {
                    try {
                        return new DefaultArtifact(
                                groupId,
                                artifactId,
                                VersionRange.createFromVersionSpec(StringUtils.isNotBlank(version) ? version : "[0,]"),
                                scope,
                                type,
                                classifier,
                                artifactHandlerManager.getArtifactHandler(type),
                                optional);
                    } catch (InvalidVersionSpecificationException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    /**
     * Creates a new "maven-plugin"-type artifact with the given coordinates. The scope will be {@code runtime}.
     * @param groupId the groupId
     * @param artifactId the artifactId
     * @param version the version
     * @return the created {@link Artifact} instance
     */
    public Artifact createMavenPluginArtifact(String groupId, String artifactId, String version) {
        return createArtifact(groupId, artifactId, version, "maven-plugin", null, "runtime", false);
    }

    /**
     * Creates an {@link Artifact} object based on a {@link Dependency} instance
     * @param dependency the dependency
     * @return the created {@link Artifact} instance
     */
    public Artifact createArtifact(Dependency dependency) {
        return createArtifact(
                new ArtifactIdentifier(
                        dependency.getGroupId(),
                        dependency.getArtifactId(),
                        dependency.getVersion(),
                        dependency.getType(),
                        dependency.getClassifier(),
                        dependency.getScope(),
                        false),
                () -> {
                    try {
                        Artifact artifact = new DefaultArtifact(
                                dependency.getGroupId(),
                                dependency.getArtifactId(),
                                VersionRange.createFromVersionSpec(
                                        StringUtils.isNotBlank(dependency.getVersion())
                                                ? dependency.getVersion()
                                                : "[0,]"),
                                dependency.getScope(),
                                dependency.getType(),
                                dependency.getClassifier(),
                                artifactHandlerManager.getArtifactHandler(dependency.getType()),
                                dependency.isOptional());
                        if (Artifact.SCOPE_SYSTEM.equals(dependency.getScope()) && dependency.getSystemPath() != null) {
                            artifact.setFile(new File(dependency.getSystemPath()));
                        }
                        return artifact;
                    } catch (InvalidVersionSpecificationException e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}
