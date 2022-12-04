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
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.apache.commons.io.IOUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.AuthenticationDigest;
import org.eclipse.aether.util.repository.DefaultAuthenticationSelector;
import org.junit.After;
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
    private static final Random RND = new Random();
    @Rule
    public WireMockRule wireMock;

    @Rule
    public WireMockRule secureWireMock;

    private static final HttpTransport TRANSPORT = new HttpTransport();

    private MavenSession mavenSession;

    @Before
    public void setUp() throws Exception
    {
        wireMock = new WireMockRule( RND.nextInt(0x4000 ) + 0x1000 );
        wireMock.start();

        secureWireMock = new WireMockRule( RND.nextInt(0x4000 ) + 0x1000 );
        secureWireMock.start();

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

        DefaultRepositorySystemSession repositorySystemSession = new DefaultRepositorySystemSession();
        when( mavenSession.getRepositorySession() ) .thenReturn( repositorySystemSession );
    }

    @After
    public void tearDown()
    {
        wireMock.stop();
        secureWireMock.stop();
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

    private void setAuthContext( Consumer<AuthenticationContext> filler )
    {
        DefaultAuthenticationSelector authSelector = new DefaultAuthenticationSelector();
        authSelector.add( "basicAuthPlainHttp", new Authentication()
        {
            @Override
            public void fill( AuthenticationContext context, String key, Map<String, String> data )
            {
                filler.accept( context );
            }

            @Override
            public void digest( AuthenticationDigest digest )
            {
            }
        } );
        ( (DefaultRepositorySystemSession) mavenSession.getRepositorySession() )
                .setAuthenticationSelector( authSelector );
    }

    @Test
    public void testUnauthenticatedPlainHttp()
            throws IOException, URISyntaxException
    {
        wireMock.stubFor( get( anyUrl() )
                .withHeader( USER_AGENT, matching( "Maven" ) )
                .willReturn( ok().withBody( "Hello, world!" ) ) );
        assertThat( TRANSPORT.download( new URI( wireMock.baseUrl() ),
                "basicAuthPlainHttp", mavenSession, HttpTransportTest::readString ),
                containsString( "Hello, world!" ) );
    }

    @Test
    public void testPlainHttpWithBasicAuth()
            throws IOException, URISyntaxException
    {
        wireMock.stubFor( get( anyUrl() )
                .withHeader( USER_AGENT, matching( "Maven" ) )
                .willReturn( unauthorized()
                        .withHeader( WWW_AUTHENTICATE, "Basic" ) ) );
        wireMock.stubFor( get( anyUrl() )
                .withHeader( USER_AGENT, matching( "Maven" ) )
                .withBasicAuth( "user", "password" )
                .willReturn( ok().withBody( "Hello, world!" ) ) );

        setAuthContext( context ->
        {
            context.put( AuthenticationContext.USERNAME, "user" );
            context.put( AuthenticationContext.PASSWORD, "password" );
        } );

        assertThat( TRANSPORT.download( new URI( wireMock.baseUrl() ),
                "basicAuthPlainHttp", mavenSession, HttpTransportTest::readString ),
                containsString( "Hello, world!" ) );
    }

    @Test
    public void testHttpsWithNoAuth()
            throws IOException, URISyntaxException
    {
        secureWireMock.stubFor( get( anyUrl() )
                .withHeader( USER_AGENT, matching( "Maven" ) )
                .willReturn( ok().withBody( "Hello, world!" ) ) );

        setAuthContext( context ->
        {
            context.put( AuthenticationContext.USERNAME, "user" );
            context.put( AuthenticationContext.PASSWORD, "password" );
        } );

        assertThat( TRANSPORT.download( new URI( secureWireMock.baseUrl().replace( "http", "https" ) ),
                        "basicAuthPlainHttp", mavenSession, HttpTransportTest::readString ),
                containsString( "Hello, world!" ) );
    }
}
