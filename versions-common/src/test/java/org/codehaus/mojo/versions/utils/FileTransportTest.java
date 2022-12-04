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
import java.nio.file.Paths;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

/**
 * Unit tests for {@link FileTransport}
 */
public class FileTransportTest
{
    private static final FileTransport TRANSPORT = new FileTransport();

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
    public void testFileTransport()
            throws IOException
    {
        final URI rulesUri = Paths.get( "src/test/resources/"
                        + getClass().getPackage().getName().replace( '.', '/' ) )
                .resolve( "hello.txt" ).toUri();

        assertThat( TRANSPORT.download( rulesUri, null, null, FileTransportTest::readString ),
                containsString( "Hello, world!" ) );
    }

}
