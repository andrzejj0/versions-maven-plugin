package org.codehaus.mojo.versions.api;

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

import java.util.Locale;

import org.apache.maven.doxia.sink.Sink;

/**
 * Common interface for reporting components
 * @param <T> model object being rendered
 */
public interface ReportRenderer<T>
{
    /**
     * Renders the report
     * @param sink sink to use
     * @param locale locale to use
     * @param bundleName name of the report properties bundle
     * @param model model to render
     */
    void render( Sink sink, Locale locale, String bundleName, T model );
}
