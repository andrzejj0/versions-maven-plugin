package org.codehaus.mojo.versions.rules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.codehaus.mojo.versions.model.IgnoreVersion;
import org.codehaus.mojo.versions.model.Rule;
import org.codehaus.mojo.versions.model.RuleSet;
import org.junit.jupiter.api.Test;

import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

public class RuleServiceTest {
    @Test
    void testDefaultsShouldBePresentInAnEmptyRuleSet() throws MojoExecutionException {
        RuleService service = new RulesServiceBuilder()
                .withLog(new SystemStreamLog())
                .withIgnoredVersions(singletonList(".*-M."))
                .build();
        RuleSet ruleSet = service.getRuleSet();
        assertThat(ruleSet.getComparisonMethod(), is("maven"));
    }

    @Test
    void testIgnoredVersionsShouldExtendTheRuleSet() throws MojoExecutionException, IllegalAccessException {
        RuleService service = new RulesServiceBuilder()
                .withLog(new SystemStreamLog())
                .withRuleSet(new RuleSet() {
                    {
                        setIgnoreVersions(new ArrayList<>(singletonList(new IgnoreVersion() {
                            {
                                setVersion("1.0.0");
                            }
                        })));
                        setRules(singletonList(new Rule() {
                            {
                                setGroupId("org.slf4j");
                                setArtifactId("slf4j-api");
                                setIgnoreVersions(singletonList(new IgnoreVersion() {
                                    {
                                        setType("regex");
                                        setVersion("^[^1]\\.*");
                                    }
                                }));
                            }
                        }));
                    }
                })
                .withIgnoredVersions(Arrays.asList(".*-M.", ".*-SNAPSHOT"))
                .build();
        RuleSet ruleSet = service.getRuleSet();
        assertThat(ruleSet.getIgnoreVersions(), hasSize(3));
        assertThat(
                ruleSet.getIgnoreVersions().stream()
                        .map(IgnoreVersion::getVersion)
                        .collect(Collectors.toList()),
                containsInAnyOrder(".*-M.", ".*-SNAPSHOT", "1.0.0"));
    }

    @Test
    void testIgnoredVersionsShouldBeTheOnlyPresentInAnEmptyRuleSet() throws MojoExecutionException {
        RuleService service = new RulesServiceBuilder()
                .withLog(mock(Log.class))
                .withIgnoredVersions(Arrays.asList(".*-M.", ".*-SNAPSHOT"))
                .build();
        RuleSet ruleSet = service.getRuleSet();
        assertThat(ruleSet.getIgnoreVersions(), hasSize(2));
        assertThat(
                ruleSet.getIgnoreVersions().stream()
                        .map(IgnoreVersion::getVersion)
                        .collect(Collectors.toList()),
                containsInAnyOrder(".*-M.", ".*-SNAPSHOT"));
    }
}
