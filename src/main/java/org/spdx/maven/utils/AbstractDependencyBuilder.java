/**
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Source Auditor Inc.
 */
package org.spdx.maven.utils;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spdx.core.CoreModelObject;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.core.SpdxCoreConstants.SpdxMajorVersion;
import org.spdx.library.model.v2.SpdxConstantsCompatV2;
import org.spdx.maven.OutputFormat;

/**
 * Contains information about package dependencies collected from the Maven dependencies.
 * <p>
 * Subclasses implement dependency information specific to SPDX spec major versions
 * 
 * @author Gary O'Neall
 *
 */
public abstract class AbstractDependencyBuilder
{
    
    protected static final Logger LOG = LoggerFactory.getLogger( AbstractDependencyBuilder.class );
    protected boolean createExternalRefs;
    protected boolean generatePurls;
    protected boolean useArtifactID;
    protected boolean includeTransitiveDependencies;
    DateFormat format = new SimpleDateFormat( SpdxConstantsCompatV2.SPDX_DATE_FORMAT );

    /**
     * @param createExternalRefs if true, create external references for dependencies
     * @param generatePurls if true, generate a Package URL and include as an external identifier for the dependencies
     * @param useArtifactID if true, use the artifact ID for the name of the dependency package, otherwise use the Maven configured project name
     * @param includeTransitiveDependencies If true, include transitive dependencies, otherwise include only direct dependencies
     */
    public AbstractDependencyBuilder( boolean createExternalRefs, boolean generatePurls, 
                                          boolean useArtifactID, boolean includeTransitiveDependencies )
    {
        this.createExternalRefs = createExternalRefs;
        this.generatePurls = generatePurls;
        this.useArtifactID = useArtifactID;
        this.includeTransitiveDependencies = includeTransitiveDependencies;
    }

    /**
     * Adds information about Maven dependencies to the list of SPDX Dependencies
     * 
     * @param mavenProjectBuilder project builder for the repo containing the POM file
     * @param session Maven session for building the project
     * @param mavenProject Mave project
     * @param node Dependency node which contains all the dependencies
     * @param pkg SPDX Package to attach the dependencies to
     * @throws InvalidSPDXAnalysisException on errors generating SPDX
     * @throws LicenseMapperException on errors mapping licenses or creating custom licenses
     */
    public void addMavenDependencies( ProjectBuilder mavenProjectBuilder, MavenSession session,
                                                  MavenProject mavenProject, DependencyNode node,
                                                  CoreModelObject pkg ) throws LicenseMapperException, InvalidSPDXAnalysisException
    {
        List<DependencyNode> children = node.getChildren();

        logDependencies( children );

        for ( DependencyNode childNode : children )
        {
            addMavenDependency( pkg, childNode, mavenProjectBuilder, session, mavenProject );
        }
    }
    
    abstract void addMavenDependency( CoreModelObject parentPackage, DependencyNode dependencyNode, 
                                       ProjectBuilder mavenProjectBuilder,
                                       MavenSession session, MavenProject mavenProject )
         throws LicenseMapperException, InvalidSPDXAnalysisException;
    

    /**
     * Converts an artifact file to an SPDX file
     *
     * @param file input file
     * @param versionFilter Optional (nullable) version - if present, only return file formats that support the filter version
     * @return SPDX file using the SPDX naming conventions if it exists, otherwise return null
     */
    protected @Nullable File artifactFileToSpdxFile( @Nullable File file, @Nullable SpdxMajorVersion versionFilter )
    {
        if ( Objects.isNull( file ) )
        {
            return null;
        }
        for ( OutputFormat of: OutputFormat.values() )
        {
            if ( versionFilter == null || versionFilter.equals( of.getSpecVersion() ))
            {
                File retval = getFileWithDifferentType( file, of.getFileType() );
                if ( retval.exists() )  {
                    return retval;
                }
            }
        }
        return null;
    }

    /**
     * Convert a file to a different type (e.g. file.txt -> file.rdf with a type rdf parameter)
     *
     * @param file Input file
     * @param type Type to change to
     * @return New file type with only the type changed
     */
    private File getFileWithDifferentType( File file, String type )
    {
        String filePath = file.getAbsolutePath();
        int indexOfDot = filePath.lastIndexOf( '.' );
        if ( indexOfDot > 0 )
        {
            filePath = filePath.substring( 0, indexOfDot + 1 );
        }
        filePath = filePath + type;
        return new File( filePath );
    }
    
    private void logDependencies( List<DependencyNode> dependencies )
    {
        if ( !LOG.isDebugEnabled() )
        {
            return;
        }
        LOG.debug( "Dependencies:" );
        if ( dependencies == null )
        {
            LOG.debug( "\tNull dependencies" );
            return;
        }
        if ( dependencies.isEmpty() )
        {
            LOG.debug( "\tZero dependencies" );
            return;
        }
        for ( DependencyNode node : dependencies )
        {
            Artifact dependency = node.getArtifact();
            String filePath = dependency.getFile() != null ? dependency.getFile().getAbsolutePath() : "[NONE]";
            String scope = dependency.getScope() != null ? dependency.getScope() : "[NONE]";
            LOG.debug( "ArtifactId: {}, file path: {}, Scope: {}", dependency.getArtifactId(), filePath, scope );
        }
    }
    
    /**
     * Make an external document reference ID valid by replacing any invalid characters with dashes
     *
     * @param externalRefId ID for external reference
     * @return valid external ref ID
     */
    protected String fixExternalRefId( String externalRefId )
    {
        StringBuilder sb = new StringBuilder();
        for ( int i = 0; i < externalRefId.length(); i++ )
        {
            if ( validExternalRefIdChar( externalRefId.charAt( i ) ) )
            {
                sb.append( externalRefId.charAt( i ) );
            }
            else
            {
                sb.append( "-" );
            }
        }
        return sb.toString();
    }
    
    /**
     * @param ch character to test
     * @return true if the character is valid for use in an External Reference ID
     */
    private boolean validExternalRefIdChar( char ch )
    {
        return ( ( ch >= 'a' && ch <= 'z' ) || ( ch >= 'A' && ch <= 'Z' ) || ( ch >= '0' && ch <= '9' ) || ch == '.' || ch == '-' );
    }

}
