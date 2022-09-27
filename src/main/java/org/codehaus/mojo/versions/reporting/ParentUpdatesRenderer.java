package org.codehaus.mojo.versions.reporting;

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

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.http.annotation.Contract;
import org.codehaus.plexus.i18n.I18N;

import static org.apache.http.annotation.ThreadingBehavior.UNSAFE;

/**
 * @since 1.0-beta-1
 */
@Contract( threading = UNSAFE )
@Named( "parent-updates-report" )
public class ParentUpdatesRenderer extends DependencyUpdatesRenderer
{
    @Inject
    public ParentUpdatesRenderer( I18N i18n )
    {
        super( i18n );
    }

    @Override
    protected void renderSummaryTotalsTable()
    {
    }

    @Override
    protected void renderDependencyManagementSummary()
    {
    }
}
