package org.spdx.maven;

import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
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
        AbstractDependencyBuilder dependencyBuilder = createDependencyBuilder( builder );
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
                    ArtifactFilter artifactFilter = getArtifactFilter();
                    DependencyNode parentNode = dependencyGraphBuilder.buildDependencyGraph( request, artifactFilter );
                    dependencyBuilder.addMavenDependencies( mavenProjectBuilder, session, project, parentNode, builder.getProjectPackage() );
                }
            }
        }
    }
}
