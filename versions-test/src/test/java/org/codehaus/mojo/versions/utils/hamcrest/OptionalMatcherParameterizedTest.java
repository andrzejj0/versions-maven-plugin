package org.codehaus.mojo.versions.utils.hamcrest;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.codehaus.mojo.versions.utils.hamcrest.OptionalMatcher.contains;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * Parameterized tests for {@link OptionalMatcher}.
 * <p>
 * Demonstrates how to validate multiple Optional cases in a single test class.
 * </p>
 */
class OptionalMatcherParameterizedTest {

    /**
     * Provides test cases: each argument is (Optional under test, expected matcher).
     */
    static Stream<Arguments> optionalsAndMatchers() {
        return Stream.of(
                Arguments.of(Optional.of("foo"), contains("foo")),
                Arguments.of(Optional.of(42), contains(greaterThan(40))),
                Arguments.of(Optional.empty(), contains(nullValue())),
                Arguments.of(Optional.of("bar"), not(contains("foo"))),
                Arguments.of(Optional.empty(), not(contains("baz"))));
    }

    @ParameterizedTest(name = "Optional {0} should match {1}")
    @MethodSource("optionalsAndMatchers")
    void testOptionalsWithMatchers(Optional<?> opt, org.hamcrest.Matcher<Optional<?>> matcher) {
        assertThat(opt, matcher);
    }
}
