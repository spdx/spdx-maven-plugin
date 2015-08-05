package org.spdx.maven;

/*
 * Copyright 2014 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License" );
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.License;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.shared.model.fileset.FileSet;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.LicenseInfoFactory;
import org.spdx.rdfparser.license.SpdxNoAssertionLicense;
import org.spdx.rdfparser.model.DoapProject;
import org.spdx.spdxspreadsheet.InvalidLicenseStringException;

import edu.emory.mathcs.backport.java.util.Arrays;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

/**
 * NOTE: Currently this is a prototype plugin for supporting SPDX in a Maven build.
 * 
 * Goal which creates a new SPDX file for the package being built.  Will replace
 * any existing SPDX file.
 * 
 * All SPDX document and SPDX package properties are supported as parameters
 * to the plugin.
 * 
 * File level data supports default parameters which are applied to all files.
 * Future versions of this plugin will support file specific parameters.
 * 
 * The treatment of licenses for Maven is somewhat involved.  Where possible,
 * SPDX standard licenses ID's should be used.  If no SPDX standard license
 * is available, a nonStandardLicense must be declared as a parameter including
 * a unique license ID and the verbatim license text.
 * 
 * The following SPDX fields are populated from the POM project information:
 *  - package name: project name or artifactId if the project name is not provided
 *  - package description: project description
 *  - package shortDescription: project description
 *  - package downloadUrl: distributionManager url
 *  - package homePage: project url
 *  - package supplier: project organization
 *  - package versionInfo: project version
 *  - files for analysis: build source files + project resource files
 *  
 * Additional SPDX fields are supplied as configuration parameters to this plugin.
 */
@Mojo( name = "createSPDX", defaultPhase = LifecyclePhase.VERIFY )
@Execute ( goal = "createSPDX", phase = LifecyclePhase.VERIFY )
public class CreateSpdxMojo
    extends AbstractMojo
{    
    static final String INCLUDE_ALL = "**/*";

    private static final String CREATOR_TOOL_MAVEN_PLUGIN = "Tool: spdx-maven-plugin";

    private static final String SPDX_ARTIFACT_TYPE = "spdx";
    
    /**
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    @Parameter ( defaultValue ="${project}" )
    MavenProject mavenProject;
    
    @Component
    private MavenProjectHelper projectHelper;
    
    /**
     * @requiresDependencyResolution test
     */
    /**
     * @requiresDependencyResolution compile
     */
    /**
     * @requiresDependencyResolution runtime
     */
    private Set<Artifact> dependencies;
    
//    /**
//     * @parameter default-value="${session}"
//     * @readonly
//     */
//    private MavenSession session;
//    
    // Parameters for the plugin
    /**
     * SPDX File name
     */
    @Parameter( defaultValue = "${project.reporting.outputDirectory}/${project.name}-${project.version}.spdx", property = "spdxFileName", required = true )
    private File spdxFile;
    
    /**
     * Document namespace - must be unique for the artifact and SPDX file
     */
    @Parameter( defaultValue = "http://spdx.org/spdxpackages/${project.name}-${project.version}", property = "spdxDocumentUrl", required = true )
    private URL spdxDocumentNamespace;
    
    /**
     * Licenses which are not SPDX listed licenses referenced within the Maven SPDX plugin configuration.
     * All non standard licenses must be configured containing the required license ID
     * and license text.
     */
    @Parameter
    private NonStandardLicense[] nonStandardLicenses;
    
    /**
     * Optional parameter if set to true will match a Maven license to an SPDX
     * standard license if the Maven license URL matches any of the cross-reference
     * license URLs for a standard license.  Default value is true.
     * Note: Several SPDX standard licenses contain the same cross-reference license
     * URL.  In this case, the SPDX standard license used in indeterminate.
     */
    @Parameter( defaultValue = "true" )
    private boolean matchLicensesOnCrossReferenceUrls;
    
    /**
     * An optional field for creators of the SPDX file content to provide comments to 
     * the consumers of the SPDX document.
     */
    @Parameter
    private String documentComment;
    
    /**
     * Optional annotations for the SPDX document
     */
    @Parameter
    private Annotation[] documentAnnotations;
    
    /**
     * Optional annotations for the package
     */
    @Parameter
    private Annotation[] packageAnnotations;

    /**
     * optional default SPDX file comment field.  
     * The file comment field provides a place for the SPDX file creator to 
     * record any general comments about the file.
     */
    @Parameter
    private String defaultFileComment;

    /**
     * optional list of default file contributors.
     * This field provides a place for the SPDX file creator to record file contributors. 
     * Contributors could include names of copyright holders and/or authors who may not be 
     * copyright holders, yet contributed to the file content.
     */
    @Parameter
    private String[] defaultFileContributors;

    /**
     * Default file copyright text.
     * If no copyright text is specified, NOASSERTION will be used
     * The copyrightText field Identifies the copyright holder of the file, 
     * as well as any dates present. The text must much the copyright notice found in the file. 
     * The options to populate this field are limited to:
     * (a) any text relating to a copyright notice, even if not complete;
     * (b) NONE, if the file contains no license information whatsoever; or
     * (c) NOASSERTION, if the SPDX creator has not examined the contents of the actual file or if the SPDX creator has intentionally provided no information(no meaning should be implied from the absence of an assertion).
     */
    @Parameter( defaultValue ="NOASSERTION" )
    private String defaultFileCopyright;

    /**
     * Optional default file license comment.
     * The licenseComments property allows the preparer of the SPDX 
     * document to describe why the licensing in spdx:licenseConcluded was chosen.
     */
    @Parameter
    private String defaultFileLicenseComment;

    /**
     * Optional default file notice text.
     * This field provides a place for the SPDX file creator to record potential legal notices found in the file. 
     * This may or may not include copyright statements.
     */
    @Parameter
    private String defaultFileNotice;

    /**
     * This field contains the license the SPDX file creator has concluded as governing the file or alternative values 
     * if the governing license cannot be determined.
     * If no concluded license is specified "NOASSERTION" will be used.
     */
    @Parameter( defaultValue ="NOASSERTION" )
    private String defaultFileConcludedLicense;

     /**
     * Default license information in file.
     * If no licenseInformationInFile is specified, NOASSERTION will be used
     * This field contains the license information actually found in the file, 
     * if any. Any license information not actually in the file, e.g., “COPYING.txt” file in a toplevel directory, should not be reflected in this field. This information is most commonly found in the header of the file, although it may be in other areas of the actual file. The options to populate this field are limited to:
     * (a) the SPDX License List short form identifier, if the license is on the SPDX License List;
     * (b) a reference to the license, denoted by LicenseRef-#LicenseRef-[idString], if the license is not on the SPDX License List;
     * (c) NONE, if the actual file contains no license information whatsoever; or
     * (d) NOASSERTION, if the SPDX file creator has not examined the contents of the actual file or the SPDX file creator has intentionally provided no information (no meaning should be implied by doing so).
     * For a license set, when there is a choice between licenses (“disjunctive license”), 
     * they should be separated with “or” and enclosed in brackets. 
     * Similarly when multiple licenses need to be applied (“conjunctive license”), 
     * they should be separated with “and” and enclosed in parentheses.
     */
    @Parameter( defaultValue ="NOASSERTION" )
    private String defaultLicenseInformationInFile;
    
    /**
     * Optional default file artifactOf.
     * ArtifactOf indicates the origin for a given file if it originates from a separate project.
     */
    @Parameter
    private ArtifactOf[] defaultFileArtifactOfs;

    // the following parameters are for the SPDX project
    
    /**
     * License declared by the originator for the package.  If no license
     * is specified, the license information in the project POM file will be mapped
     * to a standard SPDX license if available.  If a non-standard license is used,
     * a NOASSERTION value will be used.  The format of the string follows the
     * standard license string format for SPDX files (see the defaultFileConcludedLicense parameter
     * for a full description).
     */
    @Parameter
    private String licenseDeclared;

    /**
     * This field contains the license the SPDX file creator has concluded as governing the package or alternative values, 
     * if the governing license cannot be determined.
     * If this field is not specified, the declared license value will be used for the
     * concluded license.  
     * The format of the string follows the
     * standard license string format for SPDX files (see the defaultFileConcludedLicense parameter
     * for a full description).
     */
    @Parameter
    private String licenseConcluded;

    /**
     * An optional field for creators of the SPDX file to provide general comments 
     * about the creation of the SPDX file 
     * or any other relevant comment not included in the other fields.
     */
    @Parameter
    private String creatorComment;

    /**
     * Identify who (or what, in the case of a tool) created the SPDX file. 
     * If the SPDX file was created by an individual, indicate the person's name. 
     * If the SPDX file was created on behalf of a company or organization, indicate the entity name. 
     * If multiple participants or tools were involved, use multiple instances of this field. 
     * Person name or organization name may be designated as “anonymous” if appropriate.
     * Format: single line of text with the following keywords:
     * ”Person: person name” and optional “(email)”
     * "Organization: organization” and optional “(email)”
     * "Tool: toolidentifier-version”
     * 
     * NOTE: the Tool: spdx-maven-plugin will automatically be added by the plugin
     */
    @Parameter
    private String[] creators;

    /**
     * This field provides a place for the SPDX file creator to record any general comments about the license.
     */
    @Parameter
    private String licenseComments;
    
    /**
     * The name and, optionally, contact information of the person or organization that originally created the package.
     * Note that the supplier field of SPDX is filled in by the Organization in the POM.  However, the originator may
     * be different than the supplier (e.g. a Maven POM was build by organization X containing code originating from organization Y).
     * 
     * The default for this is the Maven organization
     */
    @Parameter
    private String originator;
 
    /**
     * This field provides a place for the SPDX file creator to record any relevant 
     * background information or additional comments about the origin of the package.
     * For example, this field might include comments indicating whether the package 
     * been was pulled from a source code management system or has been repackaged.
     */
    @Parameter
    private String sourceInfo;

    /**
     * Identify the copyright holders of the package, as well as any dates present. This will be a free form text field extracted from the package information files.  The options to populate this field are limited to:
     *   (a) any text related to a copyright notice, even if not complete; 
     *   (b) NONE if the package contains no license information whatsoever; or 
     *   (c) NOASSERTION, if the SPDX file creator has not examined the contents of 
     *         the       package or if the SPDX file creator has intentionally provided no 
     *        iInformation(no 
      *  meaning should be implied by doing so).
     */
    @Parameter( defaultValue ="NOASSERTION" )
    private String copyrightText;
    
    // Path specific data
    /**
     * File or directories which have SPDX information different from the project
     * defaults.  The fileOrDirectory field of the PathSpecificSpdxInfo is required.
     * All files within the directory (or just the specific file) will use the 
     * SPDX data specified in the PathSpecificSpdxInfo parameters.  All of the SPDX
     * data parameters are optional.  If any SPDX field is not specified, the project level default
     * data will be used.
     * 
     * If a file or directory is nested within another pathsWithSpcificSpdxInfo, the
     * lowest level values will be used.  Note: in this case the non-specified SPDX
     * fields for the lowest level PathSpecificSpdxInfo will use the default project
     * level fields NOT the higher level PathSpecificSpdxInfo.
     */
    @Parameter( required = false )
    private List<PathSpecificSpdxInfo> pathsWithSpecificSpdxInfo;

    @SuppressWarnings( "unchecked" )
    public void execute()
        throws MojoExecutionException
    {
        this.dependencies = mavenProject.getDependencyArtifacts();
        if ( this.getLog() == null ) 
        {
            throw( new MojoExecutionException( "Null log for Mojo" ) );
        }
        if ( this.spdxFile == null ) 
        {
            throw( new MojoExecutionException( "No SPDX file referenced.  " +
            		"Specify a configuration paramaeter spdxFile to resolve." ) );
        }
        File outputDir = this.spdxFile.getParentFile();
        if (outputDir == null) {
            throw( new MojoExecutionException( "Invalid path for SPDX output file.  " +
                            "Specify a configuration parameter spdxFile with a valid directory path to resolve." ) );
        }
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        this.getLog().info( "Creating SPDX File "+spdxFile.getPath() );
        
        SpdxDocumentBuilder builder;
        try
        {
            builder = new SpdxDocumentBuilder( this.getLog(), spdxFile, spdxDocumentNamespace,
                                               this.matchLicensesOnCrossReferenceUrls );
        }
        catch ( SpdxBuilderException e )
        {
            this.getLog().error( "Error creating SPDX Document Builder: "+e.getMessage(), e );
            throw( new MojoExecutionException( "Error creating SPDX Document Builder: "+e.getMessage(), e ) );
        }
        catch ( LicenseMapperException e )
        {
            this.getLog().error( "License mapping error creating SPDX Document Builder: "+e.getMessage(), e );
            throw( new MojoExecutionException( "License mapping error creating SPDX Document Builder: "+e.getMessage(), e ) );

        }
        if ( nonStandardLicenses != null ) 
        {
            try
            {
                builder.addNonStandardLicenses( nonStandardLicenses );
            }
            catch ( SpdxBuilderException e )
            {
                this.getLog().error( "Error adding non standard licenses: "+e.getMessage(), e );
                throw( new MojoExecutionException( "Error adding non standard licenses: "+e.getMessage(), e ) );
            }
        }
        FileSet[] includedSourceDirectories = getSourceDirectories();
        FileSet[] includedResourceDirectories = getResourceDirectories();
        FileSet[] includedTestDirectories = getTestDirectories();

        SpdxProjectInformation projectInformation = getSpdxProjectInfoFromParameters( builder.getLicenseManager() );
        SpdxDefaultFileInformation defaultFileInformation = getDefaultFileInfoFromParameters();
        HashMap<String, SpdxDefaultFileInformation> pathSpecificInformation = getPathSpecificInfoFromParameters( defaultFileInformation );
        SpdxDependencyInformation dependencyInformation = null;
        try
        {
            dependencyInformation = getSpdxDependencyInformation( this.dependencies, builder.getLicenseManager() );
        }
        catch ( LicenseMapperException e1 )
        {
            this.getLog().error( "Error mapping licenses for dependencies: "+e1.getMessage(), e1 );
            throw( new MojoExecutionException( "Error mapping licenses for dependencies: "+e1.getMessage(), e1 ) );
        }
        // The following is for debugging purposes
        logIncludedDirectories( includedSourceDirectories );
        logIncludedDirectories( includedTestDirectories );
        logIncludedDirectories( includedResourceDirectories );
        logNonStandardLicenses( this.nonStandardLicenses );
        projectInformation.logInfo( this.getLog() );
        defaultFileInformation.logInfo( this.getLog() );
        logFileSpecificInfo( pathSpecificInformation );
        logDependencies( this.dependencies );
        try
        {
            builder.buildDocumentFromFiles( includedSourceDirectories, includedTestDirectories,
                                            includedResourceDirectories,
                                            mavenProject.getBasedir().getAbsolutePath(),
                                            projectInformation, defaultFileInformation,
                                            pathSpecificInformation, dependencyInformation );
        }
        catch ( SpdxBuilderException e )
        {
            this.getLog().error( "Error building SPDX document from project files: "+e.getMessage(), e );
            throw( new MojoExecutionException( "Error building SPDX document from project files: "+e.getMessage(), e ) );
        }
        getLog().debug( "Project Helper: "+projectHelper );
        if (projectHelper != null) {
            projectHelper.attachArtifact( mavenProject, SPDX_ARTIFACT_TYPE, spdxFile );
        } else {
            this.getLog().warn( "Unable to attach SPDX artifact file - no ProjectHelper exists" );
        }
        
        List<String> spdxErrors = builder.getSpdxDoc().verify();
        if ( spdxErrors != null && spdxErrors.size() > 0 ) 
        {
            // report error
            StringBuilder sb = new StringBuilder("The following errors were found in the SPDX file:\n ");
            sb.append( spdxErrors.get( 0 ) );
            for ( int i = 0; i < spdxErrors.size(); i++ ) 
            {
                sb.append( "\n " );
                sb.append( spdxErrors.get( i ) );
            }
            this.getLog().warn( sb.toString() );
        }
    }

    /**
     * Collect dependency information from Maven dependencies
     * @param dependencies Maven dependencies
     * @param session2 
     * @return information collected from Maven dependencies
     * @throws LicenseMapperException 
     */
    private SpdxDependencyInformation getSpdxDependencyInformation( Set<Artifact> dependencies, LicenseManager licenseManager ) throws LicenseMapperException
    {
        SpdxDependencyInformation retval = new SpdxDependencyInformation( getLog(), licenseManager );
        if (dependencies != null) {
            for (Artifact dependency:dependencies) {
                retval.addMavenDependency( dependency );
            }
        }
        return retval;
    }
    
    private void logDependencies( Set<Artifact> dependencies ) {
        this.getLog().debug( "Dependencies:" );
        if ( dependencies == null ) {
            this.getLog().debug( "\tNull dependencies" );
            return;
        }
        if ( dependencies.isEmpty() ) {
            this.getLog().debug( "\tZero dependencies" );
            return;
        }
        for ( Artifact dependency:dependencies ) {
            this.getLog().debug( "ArtifactId: "+dependency.getArtifactId() + 
                                 ", file path: "+dependency.getFile().getAbsolutePath() +
                                 ", Scope: "+dependency.getScope() );
        }
    }

    private void logFileSpecificInfo( HashMap<String, SpdxDefaultFileInformation> fileSpecificInformation )
    {
        Iterator<Entry<String, SpdxDefaultFileInformation>> iter = fileSpecificInformation.entrySet().iterator();
        while ( iter.hasNext() ) 
        {
            Entry<String, SpdxDefaultFileInformation> entry = iter.next();
            this.getLog().debug( "File Specific Information for "+entry.getKey() );
            entry.getValue().logInfo( this.getLog() );
        }
    }

    private HashMap<String, SpdxDefaultFileInformation> getPathSpecificInfoFromParameters(
                                                  SpdxDefaultFileInformation projectDefault ) throws MojoExecutionException
    {
        HashMap<String, SpdxDefaultFileInformation> retval = new HashMap<String, SpdxDefaultFileInformation>();
        if ( this.pathsWithSpecificSpdxInfo != null ) 
        {
            Iterator<PathSpecificSpdxInfo> iter = this.pathsWithSpecificSpdxInfo.iterator();
            while ( iter.hasNext() ) 
            {
                PathSpecificSpdxInfo spdxInfo = iter.next();
                SpdxDefaultFileInformation value = null;
                try
                {
                    value = spdxInfo.getDefaultFileInformation( projectDefault );
                }
                catch ( InvalidLicenseStringException e )
                {
                    this.getLog().error( "Invalid license string used in the path specific SPDX information for file "+spdxInfo.getPath(), e );
                    throw( new MojoExecutionException( "Invalid license string used in the path specific SPDX information for file "+spdxInfo.getPath(), e ) );
                } 
                if ( retval.containsKey( spdxInfo.getPath() )) 
                {
                    this.getLog().warn( "Multiple file path specific SPDX data for "+spdxInfo.getPath() );
                }
                retval.put( spdxInfo.getPath(), value );            
            }
        }
        return retval;
    }

    /**
     * Primarily for debugging purposes - logs nonStandardLicenses as info
     * @param nonStandardLicenses
     */
    private void logNonStandardLicenses(
            NonStandardLicense[] nonStandardLicenses ) 
    {
        if ( nonStandardLicenses == null ) 
        {
            return;
        }
        for ( int i = 0; i < nonStandardLicenses.length; i++ ) 
        {
            this.getLog().debug( "Non standard license ID: "+nonStandardLicenses[i].getLicenseId() );
            this.getLog().debug( "Non standard license Text: "+nonStandardLicenses[i].getExtractedText() );
            this.getLog().debug( "Non standard license Comment: "+nonStandardLicenses[i].getComment() );
            this.getLog().debug( "Non standard license Name: "+nonStandardLicenses[i].getName() );
            String[] crossReferences = nonStandardLicenses[i].getCrossReference();
            if ( crossReferences != null ) 
            {
                for ( int j = 0; j < crossReferences.length; j++ ) {
                    this.getLog().debug( "Non standard license cross reference: "+crossReferences[j] );
                }
            }
        }
    }

    /**
     * Primarily for debugging purposes - logs includedDirectories as info
     * @param includedDirectories
     */
    private void logIncludedDirectories( FileSet[] includedDirectories ) 
    {
        if ( includedDirectories == null ) {
            return;
        }
        this.getLog().debug( "Logging "+String.valueOf( includedDirectories.length ) + " filesets." );
        for ( int i = 0; i < includedDirectories.length; i++ ) 
        {
            StringBuilder sb = new StringBuilder( "Included Directory: "+includedDirectories[i].getDirectory() );
            @SuppressWarnings( "unchecked" )
            List<String> includes = includedDirectories[i].getIncludes();
            if ( includes != null && includes.size() > 0) 
            {                
                sb.append( "; Included=" );
                sb.append( includes.get( 0 ) );
                for ( int j = 1; j < includes.size(); j++ ) 
                {
                    sb.append( "," );
                    sb.append( includes.get(j) );
                }
            }
            @SuppressWarnings( "unchecked" )
            List<String> excludes = includedDirectories[i].getExcludes();
            if ( excludes != null && excludes.size() > 0 ) 
            {
                sb.append( "; Excluded=" );
                sb.append( excludes.get( 0 ) );
                for ( int j = 1; j < excludes.size(); j++ ) 
                {
                    sb.append( "," );
                    sb.append( excludes.get( j ) );
                }
            }
            this.getLog().debug( sb.toString() );
        }
    }

    /**
     * @return default file information from the plugin parameters
     * @throws MojoExecutionException 
     */
    private SpdxDefaultFileInformation getDefaultFileInfoFromParameters() throws MojoExecutionException 
    {
        SpdxDefaultFileInformation retval = new SpdxDefaultFileInformation();
        retval.setArtifactOf( getDefaultFileProjects() );
        retval.setComment( defaultFileComment );
        AnyLicenseInfo concludedLicense = null;
        try 
        {
            concludedLicense = LicenseInfoFactory.parseSPDXLicenseString( defaultFileConcludedLicense.trim() );
        } catch ( InvalidLicenseStringException e ) 
        {
            this.getLog().error( "Invalid default file concluded license: "+e.getMessage() );
            throw( new MojoExecutionException( "Invalid default file concluded license: "+e.getMessage() ) );
        }
        retval.setConcludedLicense( concludedLicense );
        retval.setContributors( defaultFileContributors );
        retval.setCopyright( defaultFileCopyright );
        AnyLicenseInfo declaredLicense = null;
        try 
        {
            declaredLicense = LicenseInfoFactory.parseSPDXLicenseString( defaultLicenseInformationInFile.trim() );
        } catch ( InvalidLicenseStringException e ) 
        {
            this.getLog().error( "Invalid default file declared license: "+e.getMessage() );
            throw( new MojoExecutionException( "Invalid default file declared license: "+e.getMessage() ) );
        }
        retval.setDeclaredLicense( declaredLicense );
        retval.setLicenseComment( defaultFileLicenseComment );
        retval.setNotice( defaultFileNotice );
        return retval;
    }

    private DoapProject[] getDefaultFileProjects() 
    {
        if ( this.defaultFileArtifactOfs == null ) 
        {
            return new DoapProject[0];
        }
        DoapProject[] retval = new DoapProject[this.defaultFileArtifactOfs.length];
        for ( int i = 0; i < retval.length; i++ ) 
        {
            retval[i] = new DoapProject( defaultFileArtifactOfs[i].getName(), 
                    defaultFileArtifactOfs[i].getHomePage().toString() );
        }
        return retval;
    }

    /**
     * Get the SPDX project level information from the parameters
     * The following project level information is taken from the POM project description:
     *   declaredLicense - mapped by the license parameter in the project.  Can be overriden by specifying a plugin configuration declaredLicense string
     *   concludedLicense - same as the declared license unless overridden by the plugin configuration parameter concludedLicense
     *   name - name of the project.  If not provided, the artifactId is used
     *   downloadUrl - distributionManagement().downloadUrl - If not provided, a default value of 'NOASSERTION' is used
     *   packageFileName is the artifact().getFile fileName if it can be found.  The checksum is also calculated from this value.  If no file
     *     could be determined, a 'NOASSERTION' value is used [currently not implemented] 
     *   description, summary - The project description is used for the SPDX package description and SPDX package summary
     *   supplier - the project organization is used for the supplier.  "ORGANIZATION: " is prepended
     * @return
     * @throws MojoExecutionException 
     */
    private SpdxProjectInformation getSpdxProjectInfoFromParameters( LicenseManager licenseManager ) throws MojoExecutionException 
    {
        SpdxProjectInformation retval = new SpdxProjectInformation();
        if ( this.documentComment != null ) 
        {
            retval.setDocumentComment( this.documentComment );
        }
        AnyLicenseInfo declaredLicense = null;
        if ( this.licenseDeclared == null ) 
        {
            @SuppressWarnings( "unchecked" )
            List<License> mavenLicenses = mavenProject.getLicenses();
            try
            {
                declaredLicense = licenseManager.mavenLicenseListToSpdxLicense( mavenLicenses );
            }
            catch ( LicenseManagerException e )
            {
               this.getLog().warn( "Unable to map maven licenses to a declared license.  Using NOASSERTION" );
               declaredLicense = new SpdxNoAssertionLicense();
            }
        } else 
        {
            try 
            {
                declaredLicense = LicenseInfoFactory.parseSPDXLicenseString( this.licenseDeclared.trim() );
            } catch ( InvalidLicenseStringException e ) 
            {
                this.getLog().error( "Invalid declared license: "+e.getMessage() );
                throw( new MojoExecutionException( "Invalid declared license: "+e.getMessage() ) );
            }
        }
        AnyLicenseInfo concludedLicense = null;
        if ( this.licenseConcluded == null ) 
        {
            concludedLicense = declaredLicense;
        } else 
        {
            try 
            {
                concludedLicense = LicenseInfoFactory.parseSPDXLicenseString( this.licenseConcluded.trim() );
            } catch ( InvalidLicenseStringException e ) 
            {
                this.getLog().error( "Invalid concluded license: "+e.getMessage() );
                throw( new MojoExecutionException( "Invalid concluded license: "+e.getMessage() ) );
            }
        }
        retval.setConcludedLicense( concludedLicense );
        retval.setCreatorComment( this.creatorComment );
        if ( this.creators == null ) 
        {
            this.creators = new String[0];
        }
        String[] allCreators = (String[]) Arrays.copyOf( creators, creators.length + 1 );
        allCreators[allCreators.length - 1] = CREATOR_TOOL_MAVEN_PLUGIN;
        retval.setCreators( allCreators );
        retval.setCopyrightText( this.copyrightText );
        retval.setDeclaredLicense( declaredLicense );
        String projectName = mavenProject.getName();
        if ( projectName == null || projectName.isEmpty() ) 
        {
            projectName = getDefaultProjectName();
        }
        retval.setName( projectName );
        retval.setDescription( mavenProject.getDescription() );
        String downloadUrl = "NOASSERTION";
        DistributionManagement distributionManager = mavenProject.getDistributionManagement();
        if ( distributionManager != null ) 
        {
            if ( distributionManager.getDownloadUrl() != null && !distributionManager.getDownloadUrl().isEmpty() ) 
            {
                downloadUrl = distributionManager.getDownloadUrl();
            }
        }
        retval.setDownloadUrl( downloadUrl );
        retval.setHomePage( mavenProject.getUrl() );
        retval.setLicenseComment( this.licenseComments );
        if ( this.originator == null ) {
            // use the POM organization as the default
            if (this.mavenProject.getOrganization() != null && this.mavenProject.getOrganization().getName() != null &&
                            !this.mavenProject.getOrganization().getName().isEmpty()) {
                this.originator = "Organization:"+this.mavenProject.getOrganization().getName();
            }
        }
        retval.setOriginator( this.originator );
        String packageFileName = null;
        File packageFile = null;
        Artifact mainArtifact = mavenProject.getArtifact();
        
        if (mainArtifact != null && mainArtifact.getFile() != null) {
            packageFileName = mainArtifact.getArtifactId() + "." + mainArtifact.getType();
            packageFile = mainArtifact.getFile();
        } else {
            packageFileName = "NOASSERTION";
        }
        retval.setPackageArchiveFileName( packageFileName );
        String sha1 = null;
        if ( packageFile != null && packageFile.exists() ) 
        {
            try
            {
                sha1 = SpdxFileCollector.generateSha1( packageFile );
            }
            catch ( SpdxCollectionException e )
            {
                this.getLog().warn( "Unable to collect sha1 value for "+packageFile.getName()+":"+e.getMessage() );
            }
        }
        retval.setSha1( sha1 );
        retval.setShortDescription( mavenProject.getDescription() );
        if ( mavenProject.getOrganization() != null ) 
        {
            String supplier = mavenProject.getOrganization().getName();
            if ( supplier != null && !supplier.isEmpty() ) 
            {
                supplier = "Organization: "+supplier;
                retval.setSupplier( supplier );
            }        
        }
        retval.setSourceInfo( this.sourceInfo );
        retval.setVersionInfo( mavenProject.getVersion() );
        retval.setDocumentAnnotations( this.documentAnnotations );
        retval.setPackageAnnotations( this.packageAnnotations );
        return retval;
    }


    /**
     * Get the default project name if no project name is specified in the POM
     * @return
     */
    private String getDefaultProjectName() {
        return this.mavenProject.getArtifactId();
    }

    /**
     * Combine all inputs for source files which are to be included in the SPDX analysis.
     * FileSets are all normalized to include the full (absolute) path and use filtering.
     * @return included files from the project source roots, resources, and includedDirectories parameter
     */
    private FileSet[] getSourceDirectories() 
    {
        ArrayList<FileSet> result = new ArrayList<FileSet>();
        @SuppressWarnings( "unchecked" )
        List<String> sourceRoots = this.mavenProject.getCompileSourceRoots();
        if ( sourceRoots != null ) 
        {
            Iterator<String> sourceRootIter = sourceRoots.iterator();
            while ( sourceRootIter.hasNext() ) {
                FileSet srcFileSet = new FileSet();
                File sourceDir = new File( sourceRootIter.next() );
                srcFileSet.setDirectory( sourceDir.getAbsolutePath() );
                srcFileSet.addInclude( INCLUDE_ALL );
                result.add( srcFileSet );
                this.getLog().debug( "Adding sourceRoot directory "+srcFileSet.getDirectory() );
            }
        }
        return result.toArray( new FileSet[result.size()] );
    }
    
    /**
     * Combine all inputs for resource files which are to be included in the SPDX analysis.
     * FileSets are all normalized to include the full (absolute) path and use filtering.
     * @return included files from the project source roots, resources, and includedDirectories parameter
     */
    private FileSet[] getResourceDirectories() 
    {
        ArrayList<FileSet> result = new ArrayList<FileSet>();
        @SuppressWarnings( "unchecked" )
        List<String> sourceRoots = this.mavenProject.getCompileSourceRoots();
        if ( sourceRoots != null ) 
        {
            Iterator<String> sourceRootIter = sourceRoots.iterator();
            while ( sourceRootIter.hasNext() ) {
                FileSet srcFileSet = new FileSet();
                File sourceDir = new File( sourceRootIter.next() );
                srcFileSet.setDirectory( sourceDir.getAbsolutePath() );
                srcFileSet.addInclude( INCLUDE_ALL );
                result.add( srcFileSet );
                this.getLog().debug( "Adding sourceRoot directory "+srcFileSet.getDirectory() );
            }
        }
        @SuppressWarnings( "unchecked" )
        List<Resource> resourceList = this.mavenProject.getResources();
        if ( resourceList != null ) 
        {
            Iterator<Resource> resourceIter = resourceList.iterator();
            while ( resourceIter.hasNext() ) 
            {
                Resource resource = resourceIter.next();
                FileSet resourceFileSet = new FileSet();
                File resourceDir = new File( resource.getDirectory() );
                resourceFileSet.setDirectory( resourceDir.getAbsolutePath() );
                resourceFileSet.setExcludes( resource.getExcludes() );
                resourceFileSet.setIncludes( resource.getIncludes() );
                result.add( resourceFileSet );
                this.getLog().debug( "Adding resource directory "+resource.getDirectory() );
            }
        }
        this.getLog().debug( "Number of filesets: "+String.valueOf( result.size() ) );
        return result.toArray( new FileSet[result.size()] );
    }
    
    /**
     * Combine all inputs for test files which are to be included in the SPDX analysis.
     * FileSets are all normalized to include the full (absolute) path and use filtering.
     * @return included files from the project source roots, resources, and includedDirectories parameter
     */
    private FileSet[] getTestDirectories() 
    {
        ArrayList<FileSet> result = new ArrayList<FileSet>();
        @SuppressWarnings( "unchecked" )
        List<String> sourceRoots = this.mavenProject.getTestCompileSourceRoots();
        if ( sourceRoots != null ) 
        {
            Iterator<String> sourceRootIter = sourceRoots.iterator();
            while ( sourceRootIter.hasNext() ) {
                FileSet srcFileSet = new FileSet();
                File sourceDir = new File( sourceRootIter.next() );
                srcFileSet.setDirectory( sourceDir.getAbsolutePath() );
                srcFileSet.addInclude( INCLUDE_ALL );
                result.add( srcFileSet );
                this.getLog().debug( "Adding TestSourceRoot directory "+srcFileSet.getDirectory() );
            }
        }
        return result.toArray( new FileSet[result.size()] );
    }
}
