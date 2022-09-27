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

import java.util.Map;

import org.apache.http.annotation.Contract;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.model.Dependency;
import org.codehaus.mojo.versions.api.ArtifactVersions;
import org.codehaus.mojo.versions.reporting.model.DependencyUpdatesReportModel;
import org.codehaus.plexus.i18n.I18N;

import static java.util.Optional.of;
import static org.apache.http.annotation.ThreadingBehavior.UNSAFE;
import static org.codehaus.mojo.versions.api.Segment.INCREMENTAL;
import static org.codehaus.mojo.versions.api.Segment.MAJOR;
import static org.codehaus.mojo.versions.api.Segment.MINOR;
import static org.codehaus.mojo.versions.api.Segment.SUBINCREMENTAL;

/**
 * @since 1.0-beta-1
 */
@Contract( threading = UNSAFE )
@Named( "dependency-updates-report" )
public class DependencyUpdatesRenderer extends AbstractVersionsReportRenderer<DependencyUpdatesReportModel>
{
    @Inject
    public DependencyUpdatesRenderer( I18N i18n )
    {
        super( i18n );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void renderSummary()
    {
        renderSummaryTotalsTable();
        renderDependencyManagementSummary();
        renderDependencySummary();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void renderDetails()
    {
        model.getAllUpdates().forEach( this::renderDependencyDetail );
    }

    protected void renderDependencySummary()
    {
        renderSummaryTable( "report.overview.dependency", model.getArtifactUpdates(),
                "report.overview.noDependency" );
    }

    protected void renderDependencyManagementSummary()
    {
        renderSummaryTable( "report.overview.dependencyManagement",
                model.getArtifactManagementUpdates(), "report.overview.noDependencyManagement" );
    }

    protected void renderSummaryTable( String titleKey, Map<Dependency, ArtifactVersions> contents, String emptyKey )
    {
        sink.section2();
        sink.sectionTitle2();
        sink.text( getText( titleKey ) );
        sink.sectionTitle2_();

        if ( contents.isEmpty() )
        {
            sink.paragraph();
            sink.text( getText( emptyKey ) );
            sink.paragraph_();
        }
        else
        {
            renderDependencySummaryTable( contents );
        }
        sink.section2_();
    }

    protected void renderSummaryTotalsTable()
    {
        int numInc = 0;
        int numMin = 0;
        int numMaj = 0;
        int numAny = 0;
        int numCur = 0;
        for ( ArtifactVersions details : model.getAllUpdates().values() )
        {
            if ( details.getOldestUpdate( of( SUBINCREMENTAL ) ) != null )
            {
                numAny++;
            }
            else if ( details.getOldestUpdate( of( INCREMENTAL ) ) != null )
            {
                numInc++;
            }
            else if ( details.getOldestUpdate( of( MINOR ) ) != null )
            {
                numMin++;
            }
            else if ( details.getOldestUpdate( of( MAJOR ) ) != null )
            {
                numMaj++;
            }
            else
            {
                numCur++;
            }
        }
        sink.table();
        sink.tableRow();
        sink.tableCell();
        renderSuccessIcon();
        sink.tableCell_();
        sink.tableCell();
        sink.text( getText( "report.overview.numUpToDate" ) );
        sink.tableCell_();
        sink.tableCell();
        sink.text( Integer.toString( numCur ) );
        sink.tableCell_();
        sink.tableRow_();
        sink.tableRow();
        sink.tableCell();
        renderWarningIcon();
        sink.tableCell_();
        sink.tableCell();
        sink.text( getText( "report.overview.numNewerVersionAvailable" ) );
        sink.tableCell_();
        sink.tableCell();
        sink.text( Integer.toString( numAny ) );
        sink.tableCell_();
        sink.tableRow_();
        sink.tableRow();
        sink.tableCell();
        renderWarningIcon();
        sink.tableCell_();
        sink.tableCell();
        sink.text( getText( "report.overview.numNewerIncrementalAvailable" ) );
        sink.tableCell_();
        sink.tableCell();
        sink.text( Integer.toString( numInc ) );
        sink.tableCell_();
        sink.tableRow_();
        sink.tableRow();
        sink.tableCell();
        renderWarningIcon();
        sink.tableCell_();
        sink.tableCell();
        sink.text( getText( "report.overview.numNewerMinorAvailable" ) );
        sink.tableCell_();
        sink.tableCell();
        sink.text( Integer.toString( numMin ) );
        sink.tableCell_();
        sink.tableRow_();
        sink.tableRow();
        sink.tableCell();
        renderWarningIcon();
        sink.tableCell_();
        sink.tableCell();
        sink.text( getText( "report.overview.numNewerMajorAvailable" ) );
        sink.tableCell_();
        sink.tableCell();
        sink.text( Integer.toString( numMaj ) );
        sink.tableCell_();
        sink.tableRow_();
        sink.table_();
    }

    protected void renderDependencyDetail( Dependency artifact, ArtifactVersions details )
    {
        sink.section2();
        sink.sectionTitle2();
        sink.text( ArtifactUtils.versionlessKey( artifact.getGroupId(), artifact.getArtifactId() ) );
        sink.sectionTitle2_();
        renderDependencyDetailTable( artifact, details );
        sink.section2_();
    }
}
