package org.codehaus.mojo.versions.ordering;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.codehaus.mojo.versions.api.Segment;
import org.codehaus.mojo.versions.utils.DefaultArtifactVersionCache;
import org.codehaus.plexus.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * <p>Represents an <b>immutable</b> upper bound artifact version
 * where all segments <em>major or equal</em> to the given segment
 * held in place. It can be thought of as an artifact having +∞ as its upper bound
 * on all segments minor or equal to the held segment.</p>
 * <p>For example:</p>
 * <p>A {@link BoundArtifactVersion} of {@code [1.2.3-2, INCREMENTAL]} can be seen as {@code 1.2.+∞}
 * and will be greater than all versions matching the {@code 1.2.*} pattern.</p>
 * <p>A {@link BoundArtifactVersion} of {@code [1.2.3-2, SUBINCREMENTAL]} will be greater
 *  * than all versions matching the {@code 1.2.3.*} pattern.</p>
 * <p>When compared to another artifact versions, this results with the other object
 * with the segment versions up to the held segment being equal,
 * always comparing lower than this object.</p>
 * <p>This is particularly helpful for -SNAPSHOT and other versions with qualifiers, which
 * are lower than version 0 in the Maven versioning system.</p>
 */
public class BoundArtifactVersion implements ArtifactVersion {
    /**
     * Least major segment that may not change
     * All segments that are more major than this one are also held in place.
     * If equal to {@code null}, no restrictions are in place, meaning that all version
     * segments can change freely.
     */
    private final Segment unchangedSegment;

    private final ArtifactVersion comparable;

    /**
     * Constructs the instance given the version in a text format.
     * @param artifactVersion version in a text format
     * @param unchangedSegment most major segment that may not change, may be {@code null} meaning no restrictions
     */
    public BoundArtifactVersion(String artifactVersion, Segment unchangedSegment) {
        this.unchangedSegment = unchangedSegment;
        comparable = createComparable(artifactVersion, unchangedSegment);
    }

    static ArtifactVersion createComparable(String artifactVersion, Segment unchangedSegment) {
        final ArtifactVersion comparable;
        StringBuilder versionBuilder = new StringBuilder();
        String[] segments = tokens(artifactVersion);
        for (int segNr = 0;
                segNr <= segments.length || segNr <= Segment.SUBINCREMENTAL.value();
                segNr++, versionBuilder.append(".")) {
            if (segNr <= Optional.ofNullable(unchangedSegment).map(Segment::value).orElse(-1)) {
                versionBuilder.append(segNr < segments.length ? integerItemOrZero(segments[segNr]) : "0");
            } else {
                versionBuilder.append(Integer.MAX_VALUE);
            }
        }
        versionBuilder.append(Integer.MAX_VALUE);
        comparable = DefaultArtifactVersionCache.of(versionBuilder.toString());
        return comparable;
    }

    /**
     * Constructs the instance given a {@link ArtifactVersion instance}
     * @param artifactVersion artifact version containing the segment version values
     * @param unchangedSegment least major segment that may not change
     */
    public BoundArtifactVersion(ArtifactVersion artifactVersion, Segment unchangedSegment) {
        this(artifactVersion.toString(), unchangedSegment);
    }

    /**
     * Splits the given version string into tokens, splitting them on the {@code .} or {@code -} characters
     * as well as on letter/digit boundaries.
     * @param version version string
     * @return tokens of the parsed version string
     */
    private static String[] tokens(String version) {
        if (version == null) {
            return new String[0];
        }
        List<String> result = new ArrayList<>();
        for (int begin = 0, end = 0; end <= version.length(); end++) {
            if (end == version.length()
                    || version.charAt(end) == '.'
                    || version.charAt(end) == '-'
                    || isTokenBoundary(version.charAt(begin), version.charAt(end))) {
                if (end > begin) {
                    result.add(version.substring(begin, end));
                }
                begin = end + 1;
            }
        }
        return result.toArray(new String[0]);
    }

    /**
     * @param c1 character
     * @param c2 another character
     * @return will only return {@code true} if one of the characters is a digit and the other a letter
     */
    private static boolean isTokenBoundary(char c1, char c2) {
        return Character.isDigit(c1) ^ Character.isDigit(c2);
    }

    private static String integerItemOrZero(String item) {
        return StringUtils.isNumeric(item) ? item : "0";
    }

    /**
     * Returns the most major segment that can change.
     * All segments that are more major than this one are held in place.
     * @return segment that can change
     */
    public Segment getUnchangedSegment() {
        return unchangedSegment;
    }

    @Override
    public int compareTo(ArtifactVersion other) {
        if (other == null) {
            return -1;
        }

        return comparable.compareTo(other);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof BoundArtifactVersion)) {
            return false;
        }

        BoundArtifactVersion that = (BoundArtifactVersion) o;

        return new EqualsBuilder()
                .appendSuper(super.equals(o))
                .append(getUnchangedSegment(), that.getUnchangedSegment())
                .append(comparable, that.comparable)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .appendSuper(super.hashCode())
                .append(getUnchangedSegment())
                .append(comparable)
                .toHashCode();
    }

    @Override
    public int getMajorVersion() {
        return comparable.getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
        return comparable.getMinorVersion();
    }

    @Override
    public int getIncrementalVersion() {
        return comparable.getIncrementalVersion();
    }

    @Override
    public int getBuildNumber() {
        return comparable.getBuildNumber();
    }

    @Override
    public String getQualifier() {
        return comparable.getQualifier();
    }

    /**
     * @deprecated do not use: this method would mutate the state and therefore is illegal to use
     * @throws UnsupportedOperationException thrown if the method is called
     */
    @Override
    @Deprecated
    public void parseVersion(String version) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     *
     * Quasi-contract: {@link #toString()} must produce the textual representation of the version, without any
     * additional items, this is required by the implementation of {@link NumericVersionComparator}.
     */
    @Override
    public String toString() {
        return comparable.toString();
    }
}
