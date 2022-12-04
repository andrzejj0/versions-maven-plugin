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
import java.util.function.Function;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.maven.execution.MavenSession;
import org.codehaus.mojo.versions.api.Transport;
import org.eclipse.aether.repository.AuthenticationContext;
import org.eclipse.aether.repository.RemoteRepository;

import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;
import static org.eclipse.aether.ConfigurationProperties.USER_AGENT;

public class HttpTransport implements Transport
{
    @Override
    public boolean isApplicable( URI uri )
    {
        return "http".equals( uri.getScheme() ) || "https".equals( uri.getScheme() );
    }

    private RemoteRepository remoteRepository( URI uri, String serverId, MavenSession mavenSession )
    {
        RemoteRepository prototype = new RemoteRepository.Builder( serverId, uri.getScheme(), uri.toString() )
                .build();
        RemoteRepository.Builder builder = new RemoteRepository.Builder( prototype );
        ofNullable( mavenSession.getRepositorySession().getProxySelector().getProxy( prototype ) )
                .ifPresent( builder::setProxy );
        ofNullable( mavenSession.getRepositorySession().getAuthenticationSelector().getAuthentication( prototype ) )
                .ifPresent( builder::setAuthentication );
        ofNullable( mavenSession.getRepositorySession().getMirrorSelector().getMirror( prototype ) )
                .ifPresent( mirror -> builder.setMirroredRepositories( singletonList( mirror ) ) );
        return builder.build();
    }

    private static Credentials getCredentials( AuthenticationContext authCtx )
    {
        String userName = authCtx.get( AuthenticationContext.USERNAME );
        String password = authCtx.get( AuthenticationContext.PASSWORD );
        String ntlmDomain = authCtx.get( AuthenticationContext.NTLM_DOMAIN );
        String ntlmHost = authCtx.get( AuthenticationContext.NTLM_WORKSTATION );

        return ntlmDomain != null && ntlmHost != null
                ? new NTCredentials( userName, password.toCharArray(), ntlmHost, ntlmDomain )
                : new UsernamePasswordCredentials( userName, password.toCharArray() );
    }

    /**
     * Retrieves the resource indicated by the given uri.
     * @param uri uri pointing to the resource
     * @param serverId id of the server from which to download the information; <em>may not</em> be {@code null}
     * @param mavenSession current Maven session; <em>may not</em> be {@code null}
     * @return input stream with the resource if the {@code uri} is a http or https resource; otherwise returns null
     * @throws IOException thrown if the I/O operation doesn't succeed
     */
    @Override
    public <T> T download( URI uri, String serverId, MavenSession mavenSession, Function<InputStream, T> supplier )
            throws IOException
    {
        assert serverId != null;
        assert mavenSession != null;

        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();

        HttpClientBuilder builder = HttpClientBuilder.create()
            .setUserAgent( mavenSession.getRepositorySession().getConfigProperties()
                .getOrDefault( USER_AGENT, "Maven" ).toString() )
                .setDefaultCredentialsProvider( credentialsProvider );

        RemoteRepository repository = remoteRepository( uri, serverId, mavenSession );
        ofNullable( repository.getAuthentication() ).ifPresent( auth ->
        {
            Credentials result;
            try ( AuthenticationContext authCtx = AuthenticationContext
                    .forRepository( mavenSession.getRepositorySession(), repository ) )
            {
                result = getCredentials( authCtx );
            }
            credentialsProvider.setCredentials( new AuthScope( uri.getHost(), uri.getPort() ),
                    result );
        } );
        ofNullable( repository.getProxy() ).ifPresent( proxy ->
        {
            builder.setProxy( new HttpHost( proxy.getType(), proxy.getHost(), proxy.getPort() ) );
            ofNullable( proxy.getAuthentication() ).ifPresent( auth ->
            {
                Credentials result;
                try ( AuthenticationContext authCtx = AuthenticationContext
                        .forProxy( mavenSession.getRepositorySession(), new RemoteRepository.Builder( repository )
                                .setProxy( proxy ).build() ) )
                {
                    result = getCredentials( authCtx );
                }
                credentialsProvider.setCredentials( new AuthScope( proxy.getHost(), proxy.getPort() ),
                        result );
            } );
        } );

//        mavenSession.getSettings().getProxies()
//                .stream()
//                .filter( Proxy::isActive )
//                .filter( proxy -> ofNullable( proxy.getProtocol() )
//                        .map( p -> p.equals( uri.getScheme() ) )
//                        .orElse( true ) )
//                .filter( proxy -> ofNullable( proxy.getNonProxyHosts() )
//                        .map( s -> s.split( "\\|" ) )
//                        .map( a -> Arrays.stream( a )
//                                .noneMatch( s -> s.equals( uri.getHost() ) ) )
//                        .orElse( true ) )
//                .findAny()
//                .ifPresent( proxy ->
//                {
//                    builder.setProxy( new HttpHost( proxy.getProtocol(), proxy.getHost(), proxy.getPort() ) );
//                    if ( !isBlank( proxy.getUsername() ) && !isBlank( proxy.getPassword() ) )
//                    {
//                        builder.setDefaultCredentialsProvider( new BasicCredentialsProvider()
//                        {{
//                            setCredentials( new AuthScope( proxy.getHost(), proxy.getPort() ),
//                                    new UsernamePasswordCredentials( proxy.getUsername(),
//                                            proxy.getPassword().toCharArray() ) );
//                        }} );
//                    }
//                } );
        // TODO: add authentication, truststore, keystore, etc
//        mavenSession.getSettings().getServers()
//                .stream()
//                .filter( s -> serverId.equals( s.getId() ) )
//                .findFirst()
//                .ifPresent( server ->
//                {
//                    return;
//                } );
//        mavenSession.getSettings().getMirrors()
//                .stream()
//                .filter( m -> serverId.equals( m.getMirrorOf() ) )
//                .findFirst()
//                .ifPresent( mirror ->
//                {
//                    // TODO: handle this
//                    return;
//                } );

        try ( CloseableHttpClient httpClient = builder.build() )
        {
            return supplier.apply( httpClient.execute( new HttpGet( uri ) ).getEntity().getContent() );
        }
    }
}
