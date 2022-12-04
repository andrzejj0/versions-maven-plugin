package org.codehaus.mojo.versions.utils;

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
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.apache.commons.io.IOUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static java.util.Collections.singletonList;
import static org.apache.hc.core5.http.HttpHeaders.USER_AGENT;
import static org.apache.hc.core5.http.HttpHeaders.WWW_AUTHENTICATE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HttpTransport}
 */
@WireMockTest
public class HttpTransportTest
{
    @Rule
    public WireMockRule wireMockRule = new WireMockRule();

    private static final HttpTransport TRANSPORT = new HttpTransport();

    private MavenSession mavenSession;

    @Before
    public void setUp() throws Exception
    {
        mavenSession = mock( MavenSession.class );
        when( mavenSession.getSettings() ).thenReturn( new Settings()
        {{
           setServers( singletonList( new Server()
           {{
               setId( "basicAuthPlainHttp" );
               setUsername( "user" );
               setPassword( "password" );
           }} ) );
        }} );
        when( mavenSession.getRepositorySession() )
                .thenReturn( new DefaultRepositorySystemSession() );
    }

    private static String readString( InputStream stream )
    {
        try
        {
            return IOUtils.toString( stream );
        }
        catch ( IOException e )
        {
            throw new RuntimeException( e );
        }
    }

    @Test
    public void testUnauthenticatedPlainHttp()
            throws IOException, URISyntaxException
    {
        wireMockRule.stubFor( get( anyUrl() )
                .withHeader( USER_AGENT, matching( "Maven" ) )
                .willReturn( ok().withBody( "Hello, world!" ) ) );
        assertThat( TRANSPORT.download( new URI( wireMockRule.baseUrl() ),
                "basicAuthPlainHttp", mavenSession, HttpTransportTest::readString ),
                containsString( "Hello, world!" ) );
    }

    @Test
    public void testPlainHttpWithBasicAuth()
            throws IOException, URISyntaxException
    {
        wireMockRule.stubFor( get( anyUrl() )
                .withHeader( USER_AGENT, matching( "Maven" ) )
                .willReturn( unauthorized()
                        .withHeader( WWW_AUTHENTICATE, "Basic" ) ) );
        wireMockRule.stubFor( get( anyUrl() )
                .withHeader( USER_AGENT, matching( "Maven" ) )
                .withBasicAuth( "user", "password" )
                .willReturn( ok().withBody( "Hello, world!" ) ) );

        assertThat( TRANSPORT.download( new URI( wireMockRule.baseUrl() ),
                "basicAuthPlainHttp", mavenSession, HttpTransportTest::readString ),
                containsString( "Hello, world!" ) );
    }
}
