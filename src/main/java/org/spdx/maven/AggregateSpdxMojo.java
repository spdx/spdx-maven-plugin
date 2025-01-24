package org.spdx.maven;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;

import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.maven.utils.AbstractDependencyBuilder;
import org.spdx.maven.utils.AbstractDocumentBuilder;
import org.spdx.maven.utils.LicenseMapperException;
import org.spdx.maven.utils.SpdxV2DependencyBuilder;
import org.spdx.maven.utils.SpdxV2DocumentBuilder;
import org.spdx.maven.utils.SpdxV3DependencyBuilder;
import org.spdx.maven.utils.SpdxV3DocumentBuilder;


import java.util.List;
import java.util.Arrays;

@Mojo( name = "aggregateSPDX",
        defaultPhase = LifecyclePhase.VERIFY,
        requiresOnline = true,
        threadSafe = true )
public class AggregateSpdxMojo extends CreateSpdxMojo {

    @Override
    protected void buildSpdxDependencyInformation( AbstractDocumentBuilder builder, OutputFormat outputFormatEnum )
            throws DependencyGraphBuilderException, LicenseMapperException, InvalidSPDXAnalysisException {
        AbstractDependencyBuilder dependencyBuilder;
        if ( builder instanceof SpdxV3DocumentBuilder)
        {
            dependencyBuilder = new SpdxV3DependencyBuilder( ( SpdxV3DocumentBuilder ) builder, createExternalRefs,
                    generatePurls, useArtifactID, includeTransitiveDependencies );
        }
        else
        {
            dependencyBuilder = new SpdxV2DependencyBuilder( ( SpdxV2DocumentBuilder ) builder, createExternalRefs,
                    generatePurls, useArtifactID, includeTransitiveDependencies );
        }
        if ( session != null )
        {
            List<MavenProject> projects = session.getAllProjects(); //includes the current project
            if ( !projects.isEmpty() )
            {
                getLog().info( "List of projects that will be aggregated into one file: "
                        + Arrays.toString( projects.toArray() ) );
                for ( MavenProject project : projects )
                {
                    ProjectBuildingRequest request = new DefaultProjectBuildingRequest( session.getProjectBuildingRequest() );
                    request.setProject( project );
                    DependencyNode parentNode = dependencyGraphBuilder.buildDependencyGraph( request, null );
                    dependencyBuilder.addMavenDependencies( mavenProjectBuilder, session, project, parentNode, builder.getProjectPackage() );
                }
            }
        }
    }
}
