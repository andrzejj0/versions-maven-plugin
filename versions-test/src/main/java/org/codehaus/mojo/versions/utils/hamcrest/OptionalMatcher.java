package org.codehaus.mojo.versions.utils.hamcrest;

import java.util.Optional;

import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;

import static org.hamcrest.core.IsEqual.equalTo;

/**
 * A Hamcrest {@link Matcher} implementation for working with {@link Optional} values.
 * <p>
 * This matcher allows assertions on the contents of an {@code Optional}, similar to
 * AssertJ's {@code contains}. It unwraps the {@code Optional} and delegates matching
 * to the provided inner matcher.
 * </p>
 *
 * <p>Examples:</p>
 * <pre>{@code
 * // Match Optional containing a specific value
 * assertThat(Optional.of("foo"), OptionalMatcher.contains("foo"));
 *
 * // Match Optional containing a value that satisfies another matcher
 * assertThat(Optional.of(42), OptionalMatcher.contains(greaterThan(40)));
 *
 * // Match Optional.empty()
 * assertThat(Optional.empty(), OptionalMatcher.contains(nullValue()));
 * }</pre>
 *
 * @param <T> the type of the {@code Optional} being matched
 * @param <Q> the type of the value contained within the {@code Optional}
 */
public class OptionalMatcher<T extends Optional<Q>, Q> extends FeatureMatcher<T, Q> {

    /**
     * Creates a new {@code OptionalMatcher} that applies the given matcher
     * to the value contained in the {@code Optional}.
     *
     * @param targetMatcher the matcher to apply to the contained value
     */
    public OptionalMatcher(Matcher<Q> targetMatcher) {
        super(targetMatcher, "contained object matches", "contains()");
    }

    /**
     * Extracts the value from the {@code Optional} under test.
     * <p>
     * If the {@code Optional} is empty, this method returns {@code null},
     * allowing the use of {@link org.hamcrest.Matchers#nullValue()} for matching.
     * </p>
     *
     * @param actual the {@code Optional} under test
     * @return the contained value, or {@code null} if empty
     */
    @Override
    protected Q featureValueOf(T actual) {
        return actual.orElse(null);
    }

    /**
     * Creates a matcher that verifies an {@code Optional} contains a value
     * satisfying the given matcher.
     *
     * @param containedObjectMatcher the matcher to apply to the contained value
     * @param <T> the type of the {@code Optional}
     * @param <Q> the type of the contained value
     * @return a matcher that checks the {@code Optional}'s contents
     */
    public static <T extends Optional<Q>, Q> Matcher<T> contains(Matcher<Q> containedObjectMatcher) {
        return new OptionalMatcher<>(containedObjectMatcher);
    }

    /**
     * Creates a matcher that verifies an {@code Optional} contains the given value.
     * <p>
     * This is a convenience overload that wraps the value in an {@link org.hamcrest.core.IsEqual#equalTo} matcher.
     * </p>
     *
     * @param containedObject the expected contained value
     * @param <T> the type of the {@code Optional}
     * @param <Q> the type of the contained value
     * @return a matcher that checks the {@code Optional}'s contents
     */
    public static <T extends Optional<Q>, Q> Matcher<T> contains(Q containedObject) {
        return new OptionalMatcher<>(equalTo(containedObject));
    }
}
