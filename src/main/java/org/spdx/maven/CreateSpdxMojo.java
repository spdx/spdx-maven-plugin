package org.spdx.maven;

/*
 * Copyright 2014 Source Auditor Inc.
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
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.License;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.shared.model.fileset.FileSet;
import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.model.Checksum;
import org.spdx.library.model.SpdxDocument;
import org.spdx.library.model.enumerations.ChecksumAlgorithm;
import org.spdx.library.model.enumerations.Purpose;
import org.spdx.library.model.license.AnyLicenseInfo;
import org.spdx.library.model.license.InvalidLicenseStringException;
import org.spdx.library.model.license.LicenseInfoFactory;
import org.spdx.library.model.license.SpdxNoAssertionLicense;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

/**
 * NOTE: Currently this is a prototype plugin for supporting SPDX in a Maven build.
 * <p>
 * Goal which creates a new SPDX file for the package being built.  Will replace any existing SPDX file.
 * <p>
 * All SPDX document and SPDX package properties are supported as parameters to the plugin.
 * <p>
 * File level data supports default parameters which are applied to all files. Future versions of this plugin will
 * support file specific parameters.
 * <p>
 * The treatment of licenses for Maven is somewhat involved.  Where possible, SPDX standard licenses ID's should be
 * used.  If no SPDX standard license is available, a nonStandardLicense must be declared as a parameter including a
 * unique license ID and the verbatim license text.
 * <p>
 * The following SPDX fields are populated from the POM project information: - package name: project name or artifactId
 * if the project name is not provided - package description: project description - package shortDescription: project
 * description - package downloadUrl: distributionManager url - package homePage: project url - package supplier:
 * project organization - package versionInfo: project version - files for analysis: build source files + project
 * resource files
 * <p>
 * Additional SPDX fields are supplied as configuration parameters to this plugin.
 */
@Mojo( name = "createSPDX",
       defaultPhase = LifecyclePhase.VERIFY,
       requiresDependencyResolution = ResolutionScope.TEST )
public class CreateSpdxMojo extends AbstractMojo
{
    static final String INCLUDE_ALL = "**/*";

    public static final String CREATOR_TOOL_MAVEN_PLUGIN = "Tool: spdx-maven-plugin";

    public static final String SPDX_RDF_ARTIFACT_TYPE = "spdx.rdf.xml";
    
    public static final String SPDX_JSON_ARTIFACT_TYPE = "spdx.json";
    
    public static final String JSON_OUTPUT_FORMAT = "JSON";
    
    public static final String RDF_OUTPUT_FORMAT = "RDF/XML";

    /**
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    @Parameter( defaultValue = "${project}", readonly = true )
    MavenProject mavenProject;

    @Component
    private MavenProjectHelper projectHelper;
    
    @Component
    private RepositorySystem repositorySystem;
    
    @Component
    private ProjectBuilder mavenProjectBuilder;

    @Parameter( defaultValue = "${session}", readonly = true )
    private MavenSession session;

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

    // Parameters for the plugin
    /**
     * SPDX File name
     */
    @Parameter( defaultValue = "${project.reporting.outputDirectory}/${project.groupId}_${project.artifactId}-${project.version}.spdx",
                property = "spdxFileName",
                required = true )
    private File spdxFile;

    /**
     * Document namespace - must be unique for the artifact and SPDX file
     */
    @Parameter( defaultValue = "http://spdx.org/spdxpackages/${project.groupId}_${project.artifactId}-${project.version}",
                property = "spdxDocumentNamespace",
                required = true )
    private String spdxDocumentNamespace;

    @Parameter( defaultValue = "${project.basedir}",
                property = "componentName" )
    private String componentName;

    /**
     * Licenses which are not SPDX listed licenses referenced within the Maven SPDX plugin configuration. All
     * non-standard licenses must be configured containing the required license ID and license text.
     */
    @Parameter
    private NonStandardLicense[] nonStandardLicenses;

    /**
     * Optional parameter if set to true will match a Maven license to an SPDX standard license if the Maven license URL
     * matches any of the cross-reference license URLs for a standard license.  Default value is true. Note: Several
     * SPDX standard licenses contain the same cross-reference license URL.  In this case, the SPDX standard license
     * used in indeterminate.
     */
    @Parameter( defaultValue = "true" )
    private boolean matchLicensesOnCrossReferenceUrls;

    /**
     * An optional field for creators of the SPDX file content to provide comments to the consumers of the SPDX
     * document.
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
     * optional default SPDX file comment field. The file comment field provides a place for the SPDX file creator to
     * record any general comments about the file.
     */
    @Parameter
    private String defaultFileComment;

    /**
     * optional list of default file contributors. This field provides a place for the SPDX file creator to record file
     * contributors. Contributors could include names of copyright holders and/or authors who may not be copyright
     * holders, yet contributed to the file content.
     */
    @Parameter
    private String[] defaultFileContributors;

    /**
     * Default file copyright text. If no copyright text is specified, NOASSERTION will be used The copyrightText field
     * Identifies the copyright holder of the file, as well as any dates present. The text must much the copyright
     * notice found in the file. The options to populate this field are limited to: (a) any text relating to a copyright
     * notice, even if not complete; (b) NONE, if the file contains no license information whatsoever; or (c)
     * NOASSERTION, if the SPDX creator has not examined the contents of the actual file or if the SPDX creator has
     * intentionally provided no information(no meaning should be implied from the absence of an assertion).
     */
    @Parameter( defaultValue = "NOASSERTION" )
    private String defaultFileCopyright;

    /**
     * Optional default file license comment. The licenseComments property allows the preparer of the SPDX document to
     * describe why the licensing in spdx:licenseConcluded was chosen.
     */
    @Parameter
    private String defaultFileLicenseComment;

    /**
     * Optional default file notice text. This field provides a place for the SPDX file creator to record potential
     * legal notices found in the file. This may or may not include copyright statements.
     */
    @Parameter
    private String defaultFileNotice;

    /**
     * This field contains the license the SPDX file creator has concluded as governing the file or alternative values
     * if the governing license cannot be determined. If no concluded license is specified "NOASSERTION" will be used.
     */
    @Parameter( defaultValue = "NOASSERTION" )
    private String defaultFileConcludedLicense;

    /**
     * Default license information in file. If no licenseInformationInFile is specified, NOASSERTION will be used This
     * field contains the license information actually found in the file, if any. Any license information not actually
     * in the file, e.g., “COPYING.txt” file in a toplevel directory, should not be reflected in this field. This
     * information is most commonly found in the header of the file, although it may be in other areas of the actual
     * file. The options to populate this field are limited to: (a) the SPDX License List short form identifier, if the
     * license is on the SPDX License List; (b) a reference to the license, denoted by
     * LicenseRef-#LicenseRef-[idString], if the license is not on the SPDX License List; (c) NONE, if the actual file
     * contains no license information whatsoever; or (d) NOASSERTION, if the SPDX file creator has not examined the
     * contents of the actual file or the SPDX file creator has intentionally provided no information (no meaning should
     * be implied by doing so). For a license set, when there is a choice between licenses (“disjunctive license”), they
     * should be separated with “or” and enclosed in brackets. Similarly when multiple licenses need to be applied
     * (“conjunctive license”), they should be separated with “and” and enclosed in parentheses.
     */
    @Parameter( required = false )
    private String defaultLicenseInformationInFile;

    // the following parameters are for the SPDX project

    /**
     * License declared by the originator for the package.  If no license is specified, the license information in the
     * project POM file will be mapped to a standard SPDX license if available.  If a non-standard license is used, a
     * NOASSERTION value will be used.  The format of the string follows the standard license string format for SPDX
     * files (see the defaultFileConcludedLicense parameter for a full description).
     */
    @Parameter
    private String licenseDeclared;

    /**
     * This field contains the license the SPDX file creator has concluded as governing the package or alternative
     * values, if the governing license cannot be determined. If this field is not specified, the declared license value
     * will be used for the concluded license. The format of the string follows the standard license string format for
     * SPDX files (see the defaultFileConcludedLicense parameter for a full description).
     */
    @Parameter
    private String licenseConcluded;

    /**
     * An optional field for creators of the SPDX file to provide general comments about the creation of the SPDX file
     * or any other relevant comment not included in the other fields.
     */
    @Parameter
    private String creatorComment;

    /**
     * Identify who (or what, in the case of a tool) created the SPDX file. If the SPDX file was created by an
     * individual, indicate the person's name. If the SPDX file was created on behalf of a company or organization,
     * indicate the entity name. If multiple participants or tools were involved, use multiple instances of this field.
     * Person name or organization name may be designated as “anonymous” if appropriate. Format: single line of text
     * with the following keywords: ”Person: person name” and optional “(email)” "Organization: organization” and
     * optional “(email)” "Tool: toolidentifier-version”
     * <p>
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
     * Note that the supplier field of SPDX is filled in by the Organization in the POM.  However, the originator may be
     * different than the supplier (e.g. a Maven POM was build by organization X containing code originating from
     * organization Y).
     * <p>
     * The default for this is the Maven organization
     */
    @Parameter
    private String originator;

    /**
     * This field provides a place for the SPDX file creator to record any relevant background information or additional
     * comments about the origin of the package. For example, this field might include comments indicating whether the
     * package been was pulled from a source code management system or has been repackaged.
     */
    @Parameter
    private String sourceInfo;

    /**
     * Identify the copyright holders of the package, as well as any dates present. This will be a free form text field
     * extracted from the package information files.  The options to populate this field are limited to: (a) any text
     * related to a copyright notice, even if not complete; (b) NONE if the package contains no license information
     * whatsoever; or (c) NOASSERTION, if the SPDX file creator has not examined the contents of the       package or if
     * the SPDX file creator has intentionally provided no iInformation(no meaning should be implied by doing so).
     */
    @Parameter( defaultValue = "NOASSERTION" )
    private String copyrightText;

    /**
     * Configure whether only locally cached license list should be used. (a) If set to true, only locally cached
     * version of license list is used. (b) otherwise, the license list is queried over the internet.
     */
    @Parameter
    private boolean onlyUseLocalLicenses;

    /**
     * File checksums provides a unique identifier to match analysis information on each specific file in a package. The
     * SHA1 algorithm is always calculated. Configure which algorithms should be to calculate the file checksum. Other
     * algorithms that can be provided optionally include SHA224, SHA256, SHA384, SHA512, MD2, MD4, MD5, MD6.
     */
    @Parameter
    private String[] checksumAlgorithms;

    // Path specific data
    /**
     * File or directories which have SPDX information different from the project defaults.  The fileOrDirectory field
     * of the PathSpecificSpdxInfo is required. All files within the directory (or just the specific file) will use the
     * SPDX data specified in the PathSpecificSpdxInfo parameters.  All of the SPDX data parameters are optional.  If
     * any SPDX field is not specified, the project level default data will be used.
     * <p>
     * If a file or directory is nested within another pathsWithSpecificSpdxInfo, the lowest level values will be used.
     * Note: in this case the non-specified SPDX fields for the lowest level PathSpecificSpdxInfo will use the default
     * project level fields NOT the higher level PathSpecificSpdxInfo.
     */
    @Parameter( required = false )
    private List<PathSpecificSpdxInfo> pathsWithSpecificSpdxInfo;

    @Parameter( required = false )
    private List<ExternalReference> externalReferences;
    
    /**
     * Output file format for the SPDX file.  One of:
     * - JSON - JSON SPDX format
     * - RDF/XML - RDF/XML format
     */
    @Parameter( required = false )
    private String outputFormat;
    
    /**
     * If true, external document references will be created for any dependencies which
     * contain SPDX documents.  If false, the dependent package information will be copied
     * from the SPDX document into the generated SPDX document.
     */
    @Parameter( defaultValue = "true" )
    private boolean createExternalRefs;
    
    /**
     * If true, all transitive dependencies will be included in the SPDX document.  If false,
     * only direct dependencies will be included.
     */
    @Parameter( defaultValue = "true" )
    private boolean includeTransitiveDependencies;
    
    private String artifactType;

    @SuppressWarnings( "deprecation" )
    public void execute() throws MojoExecutionException
    {
        if ( includeTransitiveDependencies )
        {
            this.dependencies = mavenProject.getArtifacts();
        }
        else
        {
            this.dependencies = mavenProject.getDependencyArtifacts();
        }
        if ( this.getLog() == null )
        {
            throw ( new MojoExecutionException( "Null log for Mojo" ) );
        }
        if ( this.spdxFile == null )
        {
            throw ( new MojoExecutionException("No SPDX file referenced.  " + 
                            "Specify a configuration parameter spdxFile to resolve." ) );
        }
        if ( this.outputFormat == null ) 
        {
            if ( spdxFile.getName().toLowerCase().endsWith( ".rdf.xml" ) ) 
            {
                outputFormat = RDF_OUTPUT_FORMAT;
            }
            else
            {
                outputFormat = JSON_OUTPUT_FORMAT;
            }
        }
        else
        {
            outputFormat = this.outputFormat.toUpperCase();
        }
        if ( !RDF_OUTPUT_FORMAT.equals( outputFormat ) && !JSON_OUTPUT_FORMAT.equals( outputFormat ))
        {
            this.getLog().warn( "Invalid SPDX output format: "+this.outputFormat+".  Defaulting to JSON format." );
            this.outputFormat = JSON_OUTPUT_FORMAT;
        }
        this.artifactType = RDF_OUTPUT_FORMAT.equals( this.outputFormat ) ? SPDX_RDF_ARTIFACT_TYPE : SPDX_JSON_ARTIFACT_TYPE;
        if (spdxFile.getName().endsWith( ".spdx" )) {
            // add a default extension
            String spdxFileType = SPDX_RDF_ARTIFACT_TYPE.equals( this.artifactType ) ? ".rdf.xml" : ".json";
            getLog().info( "spdx file type = "+spdxFileType );            
            spdxFile = new File( spdxFile.getAbsolutePath() + spdxFileType );
        }
        File outputDir = this.spdxFile.getParentFile();
        if ( outputDir == null )
        {
            throw ( new MojoExecutionException(
                    "Invalid path for SPDX output file.  " + "Specify a configuration parameter spdxFile with a valid directory path to resolve." ) );
        }
        if ( !outputDir.exists() )
        {
            outputDir.mkdirs();
        }

        if ( onlyUseLocalLicenses )
        {
            System.setProperty( "SPDXParser.OnlyUseLocalLicenses", "true" );
        }
        if ( defaultLicenseInformationInFile == null ) {
            defaultLicenseInformationInFile = defaultFileConcludedLicense;
        }
        
       this.getLog().info( "Creating SPDX File " + spdxFile.getPath() );

        SpdxDocumentBuilder builder;
        try
        {
            if ( spdxDocumentNamespace.startsWith( "http://spdx.org/spdxpackages/" )) {
                // Fix up any URI encoding issues with the default
                spdxDocumentNamespace = spdxDocumentNamespace.replace( " ", "%20" );
            }
            URI namespaceUri = new URI( spdxDocumentNamespace );
            builder = new SpdxDocumentBuilder( this.getLog(), spdxFile, namespaceUri,
                    this.matchLicensesOnCrossReferenceUrls, outputFormat );
        }
        catch ( SpdxBuilderException e )
        {
            this.getLog().error( "Error creating SPDX Document Builder: " + e.getMessage(), e );
            throw ( new MojoExecutionException( "Error creating SPDX Document Builder: " + e.getMessage(), e ) );
        }
        catch ( LicenseMapperException e )
        {
            this.getLog().error( "License mapping error creating SPDX Document Builder: " + e.getMessage(), e );
            throw ( new MojoExecutionException(
                    "License mapping error creating SPDX Document Builder: " + e.getMessage(), e ) );

        }
        catch ( URISyntaxException e )
        {
            this.getLog().error( "Invalid SPDX document namespace - not a valid URI: " + spdxDocumentNamespace, e );
            throw ( new MojoExecutionException(
                    "Invalid SPDX document namespace - not a valid URI: " + spdxDocumentNamespace, e ) );
        }
        if ( nonStandardLicenses != null )
        {
            try
            {
                // the following will add the license to the document container
                builder.addNonStandardLicenses( nonStandardLicenses );
            }
            catch ( SpdxBuilderException e )
            {
                this.getLog().error( "Error adding non standard licenses: " + e.getMessage(), e );
                throw ( new MojoExecutionException( "Error adding non standard licenses: " + e.getMessage(), e ) );
            }
        }
        SpdxDocument spdxDoc = builder.getSpdxDoc();
        FileSet[] includedSourceDirectories = getSourceDirectories();
        FileSet[] includedResourceDirectories = getResourceDirectories();
        FileSet[] includedTestDirectories = getTestDirectories();

        SpdxProjectInformation projectInformation;
        try
        {
            projectInformation = getSpdxProjectInfoFromParameters( builder.getLicenseManager(),
                    spdxDoc );
        }
        catch ( InvalidSPDXAnalysisException e2 )
        {
            this.getLog().error( "Error getting project information from parameters: " + e2.getMessage(), e2 );
            throw ( new MojoExecutionException( "Error getting project information from parameters: " + e2.getMessage(),
                    e2 ) );
        }
        SpdxDefaultFileInformation defaultFileInformation = getDefaultFileInfoFromParameters( spdxDoc );
        HashMap<String, SpdxDefaultFileInformation> pathSpecificInformation = getPathSpecificInfoFromParameters(
                defaultFileInformation, spdxDoc );
        SpdxDependencyInformation dependencyInformation = null;
        try
        {
            dependencyInformation = getSpdxDependencyInformation( this.dependencies, builder.getLicenseManager(), spdxDoc );
        }
        catch ( LicenseMapperException e1 )
        {
            this.getLog().error( "Error mapping licenses for dependencies: " + e1.getMessage(), e1 );
            throw ( new MojoExecutionException( "Error mapping licenses for dependencies: " + e1.getMessage(), e1 ) );
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            this.getLog().error( "SPDX analysis error processing dependencies: " + e.getMessage(), e );
            throw ( new MojoExecutionException( "SPDX analysis error processing dependencies: " + e.getMessage(), e ) );
        }
        // The following is for debugging purposes
        logIncludedDirectories( includedSourceDirectories );
        logIncludedDirectories( includedTestDirectories );
        logIncludedDirectories( includedResourceDirectories );
        logNonStandardLicenses( this.nonStandardLicenses );
        projectInformation.logInfo( this.getLog(), spdxDoc );
        defaultFileInformation.logInfo( this.getLog() );
        logFileSpecificInfo( pathSpecificInformation );
        logDependencies( this.dependencies );
        try
        {
            builder.buildDocumentFromFiles( includedSourceDirectories, includedTestDirectories,
                    includedResourceDirectories, mavenProject.getBasedir().getAbsolutePath(), projectInformation,
                    defaultFileInformation, pathSpecificInformation, dependencyInformation, getChecksumAlgorithms(),
                    spdxDoc.getDocumentUri() );
        }
        catch ( SpdxBuilderException e )
        {
            this.getLog().error( "Error building SPDX document from project files: " + e.getMessage(), e );
            throw ( new MojoExecutionException( "Error building SPDX document from project files: " + e.getMessage(),
                    e ) );
        }
        getLog().debug( "Project Helper: " + projectHelper );
        if ( projectHelper != null )
        {
            projectHelper.attachArtifact( mavenProject, this.artifactType, spdxFile );
        }
        else
        {
            this.getLog().warn( "Unable to attach SPDX artifact file - no ProjectHelper exists" );
        }
        List<String> spdxErrors = builder.getSpdxDoc().verify();
        if ( spdxErrors != null && spdxErrors.size() > 0 )
        {
            // report error
            StringBuilder sb = new StringBuilder( "The following errors were found in the SPDX file:\n " );
            sb.append( spdxErrors.get( 0 ) );
            for ( String spdxError : spdxErrors )
            {
                sb.append( "\n " );
                sb.append( spdxError );
            }
            this.getLog().warn( sb.toString() );
        }
    }

    /**
     * Collect dependency information from Maven dependencies
     *
     * @param dependencies Maven dependencies
     * @param licenseManager
     * @param spdxDoc SPDX document to contain the dependencies
     * @return information collected from Maven dependencies
     * @throws LicenseMapperException
     * @throws InvalidSPDXAnalysisException 
     */
    private SpdxDependencyInformation getSpdxDependencyInformation( Set<Artifact> dependencies, 
                                                                    LicenseManager licenseManager,
                                                                    SpdxDocument spdxDoc ) throws LicenseMapperException, InvalidSPDXAnalysisException
    {
        SpdxDependencyInformation retval = new SpdxDependencyInformation( getLog(), licenseManager, spdxDoc, createExternalRefs );
        if ( dependencies != null )
        {
            for ( Artifact dependency : dependencies )
            {
                retval.addMavenDependency( dependency, mavenProjectBuilder, session, mavenProject );
            }
        }
        return retval;
    }

    private void logDependencies( Set<Artifact> dependencies )
    {
        this.getLog().debug( "Dependencies:" );
        if ( dependencies == null )
        {
            this.getLog().debug( "\tNull dependencies" );
            return;
        }
        if ( dependencies.isEmpty() )
        {
            this.getLog().debug( "\tZero dependencies" );
            return;
        }
        for ( Artifact dependency : dependencies )
        {
            String filePath;
            if ( dependency.getFile() != null )
            {
                filePath = dependency.getFile().getAbsolutePath();
            }
            else
            {
                filePath = "[NONE]";
            }
            String scope;
            if ( dependency.getScope() != null )
            {
                scope = dependency.getScope();
            }
            else
            {
                scope = "[NONE]";
            }
            this.getLog().debug(
                    "ArtifactId: " + dependency.getArtifactId() + ", file path: " + filePath + ", Scope: " + scope );
        }
    }

    private void logFileSpecificInfo( HashMap<String, SpdxDefaultFileInformation> fileSpecificInformation )
    {
        for ( Entry<String, SpdxDefaultFileInformation> entry : fileSpecificInformation.entrySet() )
        {
            this.getLog().debug( "File Specific Information for " + entry.getKey() );
            entry.getValue().logInfo( this.getLog() );
        }
    }

    /**
     * Get the patch specific information
     *
     * @param projectDefault
     * @param spdxDoc      SPDX document containing any extracted license infos
     * @return
     * @throws MojoExecutionException
     */
    private HashMap<String, SpdxDefaultFileInformation> getPathSpecificInfoFromParameters( SpdxDefaultFileInformation projectDefault, 
                                                                                           SpdxDocument spdxDoc ) throws MojoExecutionException
    {
        HashMap<String, SpdxDefaultFileInformation> retval = new HashMap<>();
        if ( this.pathsWithSpecificSpdxInfo != null )
        {
            for ( PathSpecificSpdxInfo spdxInfo : this.pathsWithSpecificSpdxInfo )
            {
                SpdxDefaultFileInformation value = null;
                try
                {
                    value = spdxInfo.getDefaultFileInformation( projectDefault, spdxDoc );
                }
                catch ( InvalidSPDXAnalysisException e )
                {
                    this.getLog().error(
                            "Invalid license string used in the path specific SPDX information for file " + spdxInfo.getPath(),
                            e );
                    throw ( new MojoExecutionException(
                            "Invalid license string used in the path specific SPDX information for file " + spdxInfo.getPath(),
                            e ) );
                }
                if ( retval.containsKey( spdxInfo.getPath() ) )
                {
                    this.getLog().warn( "Multiple file path specific SPDX data for " + spdxInfo.getPath() );
                }
                retval.put( spdxInfo.getPath(), value );
            }
        }
        return retval;
    }

    /**
     * Primarily for debugging purposes - logs nonStandardLicenses as info
     *
     * @param nonStandardLicenses
     */
    private void logNonStandardLicenses( NonStandardLicense[] nonStandardLicenses )
    {
        if ( nonStandardLicenses == null )
        {
            return;
        }
        for ( NonStandardLicense nonStandardLicense : nonStandardLicenses )
        {
            this.getLog().debug( "Non standard license ID: " + nonStandardLicense.getLicenseId() );
            this.getLog().debug( "Non standard license Text: " + nonStandardLicense.getExtractedText() );
            this.getLog().debug( "Non standard license Comment: " + nonStandardLicense.getComment() );
            this.getLog().debug( "Non standard license Name: " + nonStandardLicense.getName() );
            String[] crossReferences = nonStandardLicense.getCrossReference();
            if ( crossReferences != null )
            {
                for ( String crossReference : crossReferences )
                {
                    this.getLog().debug( "Non standard license cross reference: " + crossReference );
                }
            }
        }
    }

    /**
     * Primarily for debugging purposes - logs includedDirectories as info
     *
     * @param includedDirectories
     */
    private void logIncludedDirectories( FileSet[] includedDirectories )
    {
        if ( includedDirectories == null )
        {
            return;
        }
        this.getLog().debug( "Logging " + includedDirectories.length + " filesets." );
        for ( FileSet includedDirectory : includedDirectories )
        {
            StringBuilder sb = new StringBuilder( "Included Directory: " + includedDirectory.getDirectory() );
            List<String> includes = includedDirectory.getIncludes();
            if ( includes != null && includes.size() > 0 )
            {
                sb.append( "; Included=" );
                sb.append( includes.get( 0 ) );
                for ( int j = 1; j < includes.size(); j++ )
                {
                    sb.append( "," );
                    sb.append( includes.get( j ) );
                }
            }
            List<String> excludes = includedDirectory.getExcludes();
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
     * @param container SPDX Document containing any extracted license infos
     * @return default file information from the plugin parameters
     * @throws MojoExecutionException
     */
    private SpdxDefaultFileInformation getDefaultFileInfoFromParameters( SpdxDocument spdxDoc ) throws MojoExecutionException
    {
        SpdxDefaultFileInformation retval;
        try
        {
            retval = new SpdxDefaultFileInformation();
        }
        catch ( InvalidSPDXAnalysisException e1 )
        {
            this.getLog().error( "Error getting default file information: " + e1.getMessage(), e1 );
            throw ( new MojoExecutionException( "Error getting default file information: " + e1.getMessage(), e1 ) );
        }
        retval.setComment( defaultFileComment );
        AnyLicenseInfo concludedLicense = null;
        try
        {
            concludedLicense = LicenseInfoFactory.parseSPDXLicenseString( defaultFileConcludedLicense.trim(),
                    spdxDoc.getModelStore(), spdxDoc.getDocumentUri(), spdxDoc.getCopyManager() );
        }
        catch ( InvalidLicenseStringException e )
        {
            this.getLog().error( "Invalid default file concluded license: " + e.getMessage() );
            throw ( new MojoExecutionException( "Invalid default file concluded license: " + e.getMessage() ) );
        }
        retval.setConcludedLicense( concludedLicense );
        retval.setContributors( defaultFileContributors );
        retval.setCopyright( defaultFileCopyright );
        AnyLicenseInfo declaredLicense = null;
        try
        {
            declaredLicense = LicenseInfoFactory.parseSPDXLicenseString( defaultLicenseInformationInFile.trim(),
                                                                         spdxDoc.getModelStore(), spdxDoc.getDocumentUri(), spdxDoc.getCopyManager() );
        }
        catch ( InvalidLicenseStringException e )
        {
            this.getLog().error( "Invalid default file declared license: " + e.getMessage() );
            throw ( new MojoExecutionException( "Invalid default file declared license: " + e.getMessage() ) );
        }
        retval.setDeclaredLicense( declaredLicense );
        retval.setLicenseComment( defaultFileLicenseComment );
        retval.setNotice( defaultFileNotice );
        return retval;
    }

    /**
     * Get the SPDX project level information from the parameters The following project level information is taken from
     * the POM project description: declaredLicense - mapped by the license parameter in the project.  Can be overridden
     * by specifying a plugin configuration declaredLicense string concludedLicense - same as the declared license
     * unless overridden by the plugin configuration parameter concludedLicense name - name of the project.  If not
     * provided, the artifactId is used downloadUrl - distributionManagement().downloadUrl - If not provided, a default
     * value of 'NOASSERTION' is used packageFileName is the artifact().getFile fileName if it can be found.  The
     * checksum is also calculated from this value.  If no file could be determined, a 'NOASSERTION' value is used
     * [currently not implemented] description, summary - The project description is used for the SPDX package
     * description and SPDX package summary supplier - the project organization is used for the supplier. "ORGANIZATION:
     * " is prepended
     *
     * @param licenseManager maps the Maven licenses to SPDX licenses
     * @param spdxDoc      SPDX document containing any extracted license infos
     * @return
     * @throws MojoExecutionException
     * @throws InvalidSPDXAnalysisException 
     */
    private SpdxProjectInformation getSpdxProjectInfoFromParameters( LicenseManager licenseManager, 
                                                                     SpdxDocument spdxDoc ) throws MojoExecutionException, InvalidSPDXAnalysisException
    {
        SpdxProjectInformation retval;
        try
        {
            retval = new SpdxProjectInformation();
        }
        catch ( InvalidSPDXAnalysisException e1 )
        {
            this.getLog().error( "Unable to get SPDX project information: " + e1.getMessage() );
            throw ( new MojoExecutionException( "Unable to get SPDX project information: " + e1.getMessage() ) );
        }
        if ( this.documentComment != null )
        {
            retval.setDocumentComment( this.documentComment );
        }
        AnyLicenseInfo declaredLicense = null;
        if ( this.licenseDeclared == null )
        {
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
        }
        else
        {
            try
            {
                declaredLicense = LicenseInfoFactory.parseSPDXLicenseString( this.licenseDeclared.trim(), 
                                                                             spdxDoc.getModelStore(), 
                                                                             spdxDoc.getDocumentUri(),
                                                                             spdxDoc.getCopyManager());
            }
            catch ( InvalidLicenseStringException e )
            {
                this.getLog().error( "Invalid declared license: " + e.getMessage() );
                throw ( new MojoExecutionException( "Invalid declared license: " + e.getMessage() ) );
            }
        }
        AnyLicenseInfo concludedLicense = null;
        if ( this.licenseConcluded == null )
        {
            concludedLicense = declaredLicense;
        }
        else
        {
            try
            {
                concludedLicense = LicenseInfoFactory.parseSPDXLicenseString( this.licenseConcluded.trim(),
                                                                              spdxDoc.getModelStore(),
                                                                              spdxDoc.getDocumentUri(),
                                                                              spdxDoc.getCopyManager() );
            }
            catch ( InvalidLicenseStringException e )
            {
                this.getLog().error( "Invalid concluded license: " + e.getMessage() );
                throw ( new MojoExecutionException( "Invalid concluded license: " + e.getMessage() ) );
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
        if ( this.originator == null )
        {
            // use the POM organization as the default
            if ( this.mavenProject.getOrganization() != null && this.mavenProject.getOrganization().getName() != null && !this.mavenProject.getOrganization().getName().isEmpty() )
            {
                this.originator = "Organization:" + this.mavenProject.getOrganization().getName();
            }
        }
        retval.setOriginator( this.originator );
        String packageFileName = null;
        File packageFile = null;
        Artifact mainArtifact = mavenProject.getArtifact();

        if ( mainArtifact != null && mainArtifact.getFile() != null )
        {
            packageFileName = mainArtifact.getArtifactId() + "-" + mainArtifact.getVersion() + "." + mainArtifact.getType();
            packageFile = new File(mainArtifact.getFile().getParent() + File.separator + packageFileName);
        }

        Set<Checksum> checksums = null;
        if ( packageFile != null && packageFile.exists() )
        {
            try
            {
                this.getLog().debug( "Generating checksum for file "+packageFile.getAbsolutePath() );
                Set<ChecksumAlgorithm> algorithms = getChecksumAlgorithms();
                checksums = SpdxFileCollector.generateChecksum( packageFile, algorithms, spdxDoc );
            }
            catch ( SpdxCollectionException | InvalidSPDXAnalysisException e )
            {
                this.getLog().warn( "Unable to compute checksum for " + packageFile.getName() + ":" + e.getMessage() );
                this.getLog().debug( "Exception information for checksum error", e );
            }
        }
        else
        {
            this.getLog().warn( packageFile == null ? "Null package file" : "Package file " + packageFile.getAbsolutePath() + "does not exit" );
            packageFileName = "NOASSERTION";
        }
        retval.setPackageArchiveFileName( packageFileName );
        retval.setChecksums( checksums );
        retval.setShortDescription( mavenProject.getDescription() );
        if ( mavenProject.getOrganization() != null )
        {
            String supplier = mavenProject.getOrganization().getName();
            if ( supplier != null && !supplier.isEmpty() )
            {
                supplier = "Organization: " + supplier;
                retval.setSupplier( supplier );
            }
        }
        retval.setSourceInfo( this.sourceInfo );
        retval.setVersionInfo( mavenProject.getVersion() );
        retval.setDocumentAnnotations( this.documentAnnotations );
        retval.setPackageAnnotations( this.packageAnnotations );
        retval.setExternalRefs( this.externalReferences );
        String packaging = mavenProject.getPackaging();
        if ( "pom".equals( packaging ) )
        {
            retval.setPrimaryPurpose( Purpose.INSTALL );
        }
        else if ( "ejb".equals( packaging ) )
        {
            retval.setPrimaryPurpose( Purpose.LIBRARY );
        }
        else if ( "jar".equals( packaging ) )
        {
            retval.setPrimaryPurpose( Purpose.LIBRARY );
        }
        else if ( "maven-plugin".equals( packaging ) )
        {
            retval.setPrimaryPurpose( Purpose.LIBRARY );
        }
        else if ( "war".equals( packaging ) )
        {
            retval.setPrimaryPurpose( Purpose.APPLICATION );
        }
        else if ( "ear".equals( packaging ) )
        {
            retval.setPrimaryPurpose( Purpose.APPLICATION );
        }
        else if ( "rar".equals( packaging ) )
        {
            retval.setPrimaryPurpose( Purpose.OTHER );
        }
        else
        {
            retval.setPrimaryPurpose( Purpose.LIBRARY );
        }
        return retval;
    }


    /**
     * Get the default project name if no project name is specified in the POM
     *
     * @return
     */
    private String getDefaultProjectName()
    {
        return this.mavenProject.getArtifactId();
    }

    /**
     * Combine all inputs for source files which are to be included in the SPDX analysis. FileSets are all normalized to
     * include the full (absolute) path and use filtering.
     *
     * @return included files from the project source roots, resources, and includedDirectories parameter
     */
    private FileSet[] getSourceDirectories()
    {
        ArrayList<FileSet> result = new ArrayList<>();
        List<String> sourceRoots = this.mavenProject.getCompileSourceRoots();
        if ( sourceRoots != null )
        {
            for ( String sourceRoot : sourceRoots )
            {
                FileSet srcFileSet = new FileSet();
                File sourceDir = new File( sourceRoot );
                srcFileSet.setDirectory( sourceDir.getAbsolutePath() );
                srcFileSet.addInclude( INCLUDE_ALL );
                result.add( srcFileSet );
                this.getLog().debug( "Adding sourceRoot directory " + srcFileSet.getDirectory() );
            }
        }
        return result.toArray( new FileSet[0] );
    }

    /**
     * Combine all inputs for resource files which are to be included in the SPDX analysis. FileSets are all normalized
     * to include the full (absolute) path and use filtering.
     *
     * @return included files from the project source roots, resources, and includedDirectories parameter
     */
    private FileSet[] getResourceDirectories()
    {
        ArrayList<FileSet> result = new ArrayList<>();
        List<String> sourceRoots = this.mavenProject.getCompileSourceRoots();
        if ( sourceRoots != null )
        {
            for ( String sourceRoot : sourceRoots )
            {
                FileSet srcFileSet = new FileSet();
                File sourceDir = new File( sourceRoot );
                srcFileSet.setDirectory( sourceDir.getAbsolutePath() );
                srcFileSet.addInclude( INCLUDE_ALL );
                result.add( srcFileSet );
                this.getLog().debug( "Adding sourceRoot directory " + srcFileSet.getDirectory() );
            }
        }
        List<Resource> resourceList = this.mavenProject.getResources();
        if ( resourceList != null )
        {
            for ( Resource resource : resourceList )
            {
                FileSet resourceFileSet = new FileSet();
                File resourceDir = new File( resource.getDirectory() );
                resourceFileSet.setDirectory( resourceDir.getAbsolutePath() );
                resourceFileSet.setExcludes( resource.getExcludes() );
                resourceFileSet.setIncludes( resource.getIncludes() );
                result.add( resourceFileSet );
                this.getLog().debug( "Adding resource directory " + resource.getDirectory() );
            }
        }
        this.getLog().debug( "Number of filesets: " + result.size() );
        return result.toArray( new FileSet[0] );
    }

    /**
     * Combine all inputs for test files which are to be included in the SPDX analysis. FileSets are all normalized to
     * include the full (absolute) path and use filtering.
     *
     * @return included files from the project source roots, resources, and includedDirectories parameter
     */
    private FileSet[] getTestDirectories()
    {
        ArrayList<FileSet> result = new ArrayList<>();
        List<String> sourceRoots = this.mavenProject.getTestCompileSourceRoots();
        if ( sourceRoots != null )
        {
            for ( String sourceRoot : sourceRoots )
            {
                FileSet srcFileSet = new FileSet();
                File sourceDir = new File( sourceRoot );
                srcFileSet.setDirectory( sourceDir.getAbsolutePath() );
                srcFileSet.addInclude( INCLUDE_ALL );
                result.add( srcFileSet );
                this.getLog().debug( "Adding TestSourceRoot directory " + srcFileSet.getDirectory() );
            }
        }
        return result.toArray( new FileSet[0] );
    }

    /**
     * Map user input algorithms to Checksum.ChecksumAlgorithm values. {@code ChecksumAlgorithm.checksumAlgorithm_sha1}
     * is always added to the set because it is mandatory to include the SHA1 checksum. A warning is logged for invalid
     * user input.
     * @return set of algorithms to calculate checksum with
     */
    private Set<ChecksumAlgorithm> getChecksumAlgorithms()
    {
        Set<ChecksumAlgorithm> algorithms = new HashSet<>();
        algorithms.add( ChecksumAlgorithm.SHA1 );
        if ( checksumAlgorithms != null )
        {
            for ( String checksumAlgorithm : checksumAlgorithms )
            {
                switch ( checksumAlgorithm.toUpperCase() )
                {
                    case "SHA1":
                        algorithms.add( ChecksumAlgorithm.SHA1 );
                        break;
                    case "SHA224":
                        algorithms.add( ChecksumAlgorithm.SHA224 );
                        break;
                    case "SHA256":
                        algorithms.add( ChecksumAlgorithm.SHA256 );
                        break;
                    case "SHA384":
                        algorithms.add( ChecksumAlgorithm.SHA384 );
                        break;
                    case "SHA512":
                        algorithms.add( ChecksumAlgorithm.SHA512 );
                        break;
                    case "MD2":
                        algorithms.add( ChecksumAlgorithm.MD2 );
                        break;
                    case "MD4":
                        algorithms.add( ChecksumAlgorithm.MD4 );
                        break;
                    case "MD5":
                        algorithms.add( ChecksumAlgorithm.MD5 );
                        break;
                    case "MD6":
                        algorithms.add( ChecksumAlgorithm.MD6 );
                        break;
                    default:
                        this.getLog().warn( "Ignoring unsupported checksum algorithm: " + checksumAlgorithm );
                }
            }
        }
        return algorithms;
    }
}
