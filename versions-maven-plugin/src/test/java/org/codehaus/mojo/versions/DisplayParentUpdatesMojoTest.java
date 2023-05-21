package org.codehaus.mojo.versions;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;

import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.mojo.versions.utils.TestChangeRecorder;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.apache.commons.codec.CharEncoding.UTF_8;
import static org.codehaus.mojo.versions.utils.MockUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

/**
 * Basic tests for {@linkplain DisplayDependencyUpdatesMojo}.
 *
 * @author Andrzej Jarmoniuk
 */
public class DisplayParentUpdatesMojoTest {

    private TestChangeRecorder changeRecorder;
    private Path tempFile;
    private DisplayParentUpdatesMojo mojo;
    private static RepositorySystem repositorySystem;

    private static org.eclipse.aether.RepositorySystem aetherRepositorySystem;

    @BeforeClass
    public static void setUpStatic() {
        repositorySystem = mockRepositorySystem();
        aetherRepositorySystem = mockAetherRepositorySystem(new HashMap<String, String[]>() {
            {
                put("parent-artifact", new String[] {"7", "9"});
            }
        });
    }

    @Before
    public void setUp() throws IllegalAccessException, IOException {
        tempFile = Files.createTempFile("display-parent-updates", "txt");
        changeRecorder = new TestChangeRecorder();
        mojo =
                new DisplayParentUpdatesMojo(
                        repositorySystem, aetherRepositorySystem, null, changeRecorder.asTestMap()) {
                    {
                        setProject(createProject());
                        reactorProjects = Collections.emptyList();
                        session = mockMavenSession();
                        outputFile = tempFile.toFile();
                        outputEncoding = UTF_8;
                        setPluginContext(new HashMap<String, String>());
                    }
                };
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(tempFile);
    }

    private MavenProject createProject() {
        return new MavenProject() {
            {
                setModel(new Model() {
                    {
                        setGroupId("default-group");
                        setArtifactId("project-artifact");
                        setVersion("1.0.1-SNAPSHOT");
                    }
                });

                setParent(new MavenProject() {
                    {
                        setGroupId("default-group");
                        setArtifactId("parent-artifact");
                        setVersion("9");
                    }
                });
            }
        };
    }

    @Test
    public void testSingleDigitVersions() throws Exception {
        mojo.execute();
        String output = String.join("", Files.readAllLines(tempFile));
        assertThat(output, not(containsString("7")));
    }
}
