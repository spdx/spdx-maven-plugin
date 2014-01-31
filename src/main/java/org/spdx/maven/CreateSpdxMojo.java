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

import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.License;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.spdx.rdfparser.DOAPProject;
import org.spdx.rdfparser.InvalidSPDXAnalysisException;
import org.spdx.rdfparser.SPDXCreatorInformation;
import org.spdx.rdfparser.SPDXDocument;
import org.spdx.rdfparser.SPDXLicenseInfo;
import org.spdx.rdfparser.SPDXLicenseInfoFactory;
import org.spdx.rdfparser.SPDXStandardLicense;
import org.spdx.rdfparser.SpdxNoAssertionLicense;
import org.spdx.rdfparser.SpdxRdfConstants;
import org.spdx.rdfparser.SpdxVerificationHelper;
import org.spdx.spdxspreadsheet.InvalidLicenseStringException;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

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
 *  
 * Additional SPDX fields are supplied as configuration parameters to this plugin.
 */
@Mojo( name = "createSPDX", defaultPhase = LifecyclePhase.PREPARE_PACKAGE )
@Execute ( goal = "createSPDX", phase = LifecyclePhase.PREPARE_PACKAGE )
public class CreateSpdxMojo
    extends AbstractMojo
{
    //TODO: Refactor to a separate Jar file for most of the functionality
    //TODO: Use a previous SPDX to document file specific information and update
    //TODO: Add file specific parameters
    //TODO: Create actual SPDX distribution package

    static DateFormat format = new SimpleDateFormat( SpdxRdfConstants.SPDX_DATE_FORMAT );

    private static final String CREATOR_TOOL_MAVEN_PLUGIN = "tool: spdx-maven-plugin";
    
    /**
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    @Parameter ( defaultValue ="${project}" )
    MavenProject mavenProject;
    
    // Parameters for the plugin
    /**
     * Location of the SPDX file.
     */
    @Parameter( defaultValue = "${project.build.directory}/${project.name}-${project.version}-SPDX.rdf", property = "spdxFileName", required = true )
    private File spdxFile;
    
    /**
     * Document URL - must be unique for the artifact and SPDX file
     */
    @Parameter( defaultValue = "http://spdx.org/spdxpackages/${project.name}-${project.version}", property = "spdxDocumentUrl", required = true )
    private URL spdxDocumentUrl;
    
    /**
     * Non standard licenses referenced within the Maven SPDX plugin configuration.
     * All non standard licenses must be configured containing the required license ID
     * and license text.
     */
    @Parameter
    private NonStandardLicense[] nonStandardLicenses;

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
     */
    @Parameter
    private String originator;
    //TODO: Determine if the POM organization should be the originator or the supplier or both

    /**
     * Regular expressions for any file or directory names which should be excluded from the SPDX verification code.
     * These typically are meta data files which are not included in the distribution of the source files.  
     * See http://spdx.org/rdf/terms#PackageVerificationCode
     */
    @Parameter
    private String[] excludedFilePatterns;

    /**
     * Directory of files which are included in the package.  This is used to create
     * the SPDXFiles and the verification code.  If there are files in the directory
     * which should not be included, the <code>excludedFilePatterns</code> parameter
     * can be used to specify any patterns for file or directory names which should be skipped
     */
    @Parameter( required = true )
    private File[] includedDirectories;

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

    public void execute()
        throws MojoExecutionException
    {
        if ( this.getLog() == null ) {
            throw( new MojoExecutionException( "Null log for Mojo" ) );
        }
        if ( this.spdxFile == null ) {
            throw( new MojoExecutionException( "No SPDX file referenced.  " +
            		"Specify a configuration paramaeter spdxFile to resolve." ) );
        }
        this.getLog().info( "Creating SPDX File "+spdxFile.getPath() );

        if ( !spdxFile.exists() )
        {
            File parentDir = spdxFile.getParentFile();
            if ( parentDir != null && !parentDir.exists() ) {
                if ( !parentDir.mkdirs() ) {
                    this.getLog().error( "Unable to create directory containing the SPDX file: "+parentDir.getPath() );
                    throw( new MojoExecutionException( "Unable to create directories for SPDX file" ) );
                }
            }

            try {
                if ( !spdxFile.createNewFile() ) {
                       this.getLog().error( "Unable to create the SPDX file: "+spdxFile.getPath() );
                    throw( new MojoExecutionException( "Unable to create the SPDX file" ) );
                }
            } catch ( IOException e ) {
                   this.getLog().error( "IO error creating the SPDX file "+spdxFile.getPath() + ":"+e.getMessage(),e );
                throw( new MojoExecutionException( "IO error creating the SPDX file" ) );
            }
        }
        if ( !spdxFile.canWrite() ) {
            this.getLog().error( "Can not write to SPDX file "+spdxFile.getPath() );
            throw( new MojoExecutionException( "Unable to write to SPDX file - check permissions: "+spdxFile.getPath() ) ) ;
        }
        Model model = ModelFactory.createDefaultModel();
        SPDXDocument spdxDoc;
        try {
            spdxDoc = new SPDXDocument( model );
        } catch ( InvalidSPDXAnalysisException e ) {
            this.getLog().error( "Error creating SPDX document", e );
            throw( new MojoExecutionException( "Error creating SPDX document: "+e.getMessage() ) );
        }
        if ( spdxDocumentUrl == null ) {
            this.getLog().error( "spdxDocumentUrl must be specified as a configuration parameter" );
            throw( new MojoExecutionException( "Missing spdxDocumentUrl" ) );
        }
        try {
            spdxDoc.createSpdxAnalysis( spdxDocumentUrl.toString() );
        } catch ( InvalidSPDXAnalysisException e ) {
            this.getLog().error( "Error creating SPDX analysis", e );
            throw( new MojoExecutionException( "Error creating SPDX analysis: "+e.getMessage() ) );
        }
        try {
            spdxDoc.createSpdxPackage();
        } catch ( InvalidSPDXAnalysisException e ) {
            this.getLog().error( "Error creating SPDX package", e );
            throw( new MojoExecutionException( "Error creating SPDX package: "+e.getMessage() ) );
        }
        Pattern[] excludedFilePatterns = getPatternFromParameters();
        File[] includedDirectories = getIncludedDirectoriesFromParameters();
        LicenseManager licenseManager = new LicenseManager( spdxDoc, getLog(), false ); //TODO: Add a parameter for matching cross reference URL's
        processNonStandardLicenses( licenseManager );
        SpdxProjectInformation projectInformation = getSpdxProjectInfoFromParameters( licenseManager );
        SpdxDefaultFileInformation defaultFileInformation = getDefaultFileInfoFromParameters();
        
        // The following is for debugging purposes
        logExcludedFilePatterns( excludedFilePatterns );
        logIncludedDirectories( includedDirectories );
        logNonStandardLicenses( this.nonStandardLicenses );
        projectInformation.logInfo( this.getLog() );
        defaultFileInformation.logInfo( this.getLog() );
        createSpdxFromProject( spdxFile, spdxDoc, spdxDocumentUrl, excludedFilePatterns, includedDirectories,
                                projectInformation, defaultFileInformation, licenseManager );
        ArrayList<String> spdxErrors = spdxDoc.verify();
        if ( spdxErrors != null && spdxErrors.size() > 0 ) {
            // report error
            StringBuilder sb = new StringBuilder("The following errors were found in the SPDX file:\n");
            sb.append( spdxErrors.get( 0 ) );
            for ( int i = 0; i < spdxErrors.size(); i++ ) {
                sb.append( '\n' );
                sb.append( spdxErrors.get( i ) );
            }
            this.getLog().warn( sb.toString() );
        }
    }

    /**
     * Primarily for debugging purposes - logs nonStandardLicenses as info
     * @param nonStandardLicenses
     */
    private void logNonStandardLicenses(
            NonStandardLicense[] nonStandardLicenses ) {
        if ( nonStandardLicenses == null ) {
            return;
        }
        for ( int i = 0; i < nonStandardLicenses.length; i++ ) {
            this.getLog().info( "Non standard license ID: "+nonStandardLicenses[i].getLicenseId() );
            this.getLog().info( "Non standard license Text: "+nonStandardLicenses[i].getExtractedText() );
            this.getLog().info( "Non standard license Comment: "+nonStandardLicenses[i].getComment() );
            this.getLog().info( "Non standard license Name: "+nonStandardLicenses[i].getName() );
            String[] crossReferences = nonStandardLicenses[i].getCrossReference();
            if ( crossReferences != null ) {
                for ( int j = 0; j < crossReferences.length; j++ ) {
                    this.getLog().info( "Non standard license cross reference: "+crossReferences[j] );
                }
            }
        }
    }

    /**
     * Primarily for debugging purposes - logs includedDirectories as info
     * @param includedDirectories
     */
    private void logIncludedDirectories( File[] includedDirectories ) {
        if ( includedDirectories == null ) {
            return;
        }
        for ( int i = 0; i < includedDirectories.length; i++ ) {
            this.getLog().info( "Included Directory: "+includedDirectories[i].getPath() );
        }
    }

    /**
     * Primarily for debugging purposes - logs excludedFilePatterns as info
     * @param excludedFilePatterns
     */
    private void logExcludedFilePatterns( Pattern[] excludedFilePatterns ) {
        if ( excludedFilePatterns == null ) {
            return;
        }
        for ( int i = 0; i < excludedFilePatterns.length; i++ ) {
            this.getLog().info( "Excluded File Pattern: "+excludedFilePatterns[i].pattern() );
        }
    }

    /**
     * Run through the non standard licenses and add them to the SPDX document
     * @param spdxDoc
     * @throws MojoExecutionException 
     */
    private void processNonStandardLicenses( LicenseManager licenseManager ) throws MojoExecutionException {
        if ( this.nonStandardLicenses != null ) {
            for ( int i = 0; i < this.nonStandardLicenses.length; i++ ) {
                try
                {
                    licenseManager.addNonStandardLicense( nonStandardLicenses[i] );
                }
                catch ( LicenseManagerException e )
                {
                    this.getLog().error( "Error adding license "+e.getMessage(), e );
                    throw(new MojoExecutionException("Error adding non standard license: "+e.getMessage(), e));
                }
            }
        }
    }

    /**
     * @return default file information from the plugin parameters
     * @throws MojoExecutionException 
     */
    private SpdxDefaultFileInformation getDefaultFileInfoFromParameters() throws MojoExecutionException {
        SpdxDefaultFileInformation retval = new SpdxDefaultFileInformation();
        retval.setArtifactOf( getDefaultFileProjects() );
        retval.setComment( defaultFileComment );
        SPDXLicenseInfo concludedLicense = null;
        try {
            concludedLicense = SPDXLicenseInfoFactory.parseSPDXLicenseString( defaultFileConcludedLicense.trim() );
        } catch ( InvalidLicenseStringException e ) {
            this.getLog().error( "Invalid default file concluded license: "+e.getMessage() );
            throw( new MojoExecutionException( "Invalid default file concluded license: "+e.getMessage() ) );
        }
        retval.setConcludedLicense( concludedLicense );
        retval.setContributors( defaultFileContributors );
        retval.setCopyright( defaultFileCopyright );
        SPDXLicenseInfo declaredLicense = null;
        try {
            declaredLicense = SPDXLicenseInfoFactory.parseSPDXLicenseString( defaultLicenseInformationInFile.trim() );
        } catch ( InvalidLicenseStringException e ) {
            this.getLog().error( "Invalid default file declared license: "+e.getMessage() );
            throw( new MojoExecutionException( "Invalid default file declared license: "+e.getMessage() ) );
        }
        retval.setDeclaredLicense( declaredLicense );
        retval.setLicenseComment( defaultFileLicenseComment );
        retval.setNotice( defaultFileNotice );
        return retval;
    }

    private DOAPProject[] getDefaultFileProjects() {
        if ( this.defaultFileArtifactOfs == null ) {
            return new DOAPProject[0];
        }
        DOAPProject[] retval = new DOAPProject[this.defaultFileArtifactOfs.length];
        for ( int i = 0; i < retval.length; i++ ) {
            retval[i] = new DOAPProject( defaultFileArtifactOfs[i].getName(), 
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
    @SuppressWarnings( "unused" )
    private SpdxProjectInformation getSpdxProjectInfoFromParameters( LicenseManager licenseManager ) throws MojoExecutionException {
        SpdxProjectInformation retval = new SpdxProjectInformation();
        SPDXLicenseInfo declaredLicense = null;
        if ( this.licenseDeclared == null ) {
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
        } else {
            try {
                declaredLicense = SPDXLicenseInfoFactory.parseSPDXLicenseString( this.licenseDeclared.trim() );
            } catch ( InvalidLicenseStringException e ) {
                this.getLog().error( "Invalid declared license: "+e.getMessage() );
                throw( new MojoExecutionException( "Invalid declared license: "+e.getMessage() ) );
            }
        }
        SPDXLicenseInfo concludedLicense = null;
        if ( this.licenseConcluded == null ) {
            concludedLicense = declaredLicense;
        } else {
            try {
                concludedLicense = SPDXLicenseInfoFactory.parseSPDXLicenseString( this.licenseConcluded.trim() );
            } catch ( InvalidLicenseStringException e ) {
                this.getLog().error( "Invalid concluded license: "+e.getMessage() );
                throw( new MojoExecutionException( "Invalid concluded license: "+e.getMessage() ) );
            }
        }
        retval.setConcludedLicense( concludedLicense );
        retval.setCreatorComment( this.creatorComment );
        retval.setCreators( this.creators );
        retval.setCopyrightText( this.copyrightText );
        retval.setDeclaredLicense( declaredLicense );
        String projectName = mavenProject.getName();
        if ( projectName == null || projectName.isEmpty() ) {
            projectName = getDefaultProjectName();
        }
        retval.setName( projectName );
        retval.setDescription( mavenProject.getDescription() );
        String downloadUrl = "NOASSERTION";
        DistributionManagement distributionManager = mavenProject.getDistributionManagement();
        if ( distributionManager != null ) {
            if ( distributionManager.getDownloadUrl() != null && !distributionManager.getDownloadUrl().isEmpty() ) {
                downloadUrl = distributionManager.getDownloadUrl();
            }
        }
        retval.setDownloadUrl( downloadUrl );
        retval.setHomePage( mavenProject.getUrl() );
        retval.setLicenseComment( this.licenseComments );
        retval.setOriginator( this.originator );
//        String packageFileName;
//        File packageFile = mavenProject.getArtifact().getFile();
//        if ( packageFile != null ) {
//            packageFileName = packageFile.getName();
//        } else {
//            packageFileName = "NOASSERTION";
//        }
//        retval.setPackageArchiveFileName( packageFileName );
        retval.setPackageArchiveFileName( "NOASSERTION" );
        File packageFile = null;
        //TODO Determine the package file based on the packaging and properly fill in
        String sha1 = null;
        if ( packageFile != null ) {
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
        if ( mavenProject.getOrganization() != null ) {
            String supplier = mavenProject.getOrganization().getName();
            if ( supplier != null && !supplier.isEmpty() ) {
                supplier = "Organization: "+supplier;
                retval.setSupplier( supplier );
            }        
        }
        retval.setSourceInfo( this.sourceInfo );
        retval.setVersionInfo( mavenProject.getVersion() );
        return retval;
    }


    /**
     * Get the default project name if no project name is specified in the POM
     * @return
     */
    private String getDefaultProjectName() {
        return this.mavenProject.getArtifactId();
    }

    private File[] getIncludedDirectoriesFromParameters() {
        return this.includedDirectories;
        //TODO: See if this can be extracted from other parameters
    }

    private Pattern[] getPatternFromParameters() throws MojoExecutionException {
        if ( this.excludedFilePatterns == null ) {
            return new Pattern[0];
        }
        Pattern[] retval = new Pattern[this.excludedFilePatterns.length];
        for ( int i = 0; i < retval.length; i++ ) {
            try {
                retval[i] = Pattern.compile( excludedFilePatterns[i] );
            } catch ( Exception e ) {
                this.getLog().error( "Error in excluded file pattern "+excludedFilePatterns[i], e );
                throw( new MojoExecutionException( "Error in excluded file pattern "+excludedFilePatterns[i]+": "+e.getMessage() ) );
            }
        }
        return retval;
    }

    public void createSpdxFromProject( File spdxFile, SPDXDocument spdxDoc, URL spdxDocumentUrl, Pattern[] excludedFilePatterns,
            File[] includedDirectories, SpdxProjectInformation projectInformation,
            SpdxDefaultFileInformation defaultFileInformation, LicenseManager licenseManager ) throws MojoExecutionException {

        FileOutputStream spdxOut = null;
        try {
            spdxOut = new FileOutputStream ( spdxFile );
            fillSpdxDocumentInformation( spdxDoc, projectInformation );
            collectSpdxFileInformation( spdxDoc, excludedFilePatterns, includedDirectories,
                    defaultFileInformation, spdxFile.getPath().replace( "\\", "/" ) );
            spdxDoc.getModel().write( spdxOut );
        } catch ( FileNotFoundException e ) {
            this.getLog().error( "Error saving SPDX data to file", e );
            throw( new MojoExecutionException( "Error saving SPDX data to file: "+e.getMessage() ) ) ;
        } catch ( InvalidSPDXAnalysisException e ) {
            this.getLog().error( "Error collecting SPDX file data", e );
            throw( new MojoExecutionException( "Error collecting SPDX file data: "+e.getMessage() ) );
        } finally {
            if ( spdxOut != null ) {
                try {
                    spdxOut.close();
                } catch ( IOException e ) {
                    this.getLog().warn( "Error closing SPDX output file", e );
                }
            }
        }
    }

    private void fillSpdxDocumentInformation( SPDXDocument spdxDoc,
            SpdxProjectInformation projectInformation ) throws MojoExecutionException {
        try {
            // creator
            fillCreatorInfo( spdxDoc, projectInformation );
            // data license
            SPDXStandardLicense dataLicense = (SPDXStandardLicense)(SPDXLicenseInfoFactory.parseSPDXLicenseString( SPDXDocument.SPDX_DATA_LICENSE_ID ) );
            spdxDoc.setDataLicense( dataLicense );
            // reviewers - not implemented
            // packageName
            if ( projectInformation.getName() != null ) {
                spdxDoc.getSpdxPackage().setDeclaredName( projectInformation.getName() );
            }
            // concluded license
            spdxDoc.getSpdxPackage().setConcludedLicenses( projectInformation.getConcludedLicense() );
            // declared license
            spdxDoc.getSpdxPackage().setDeclaredLicense( projectInformation.getDeclaredLicense() );
            // description
            if ( projectInformation.getDescription() != null ) {
                spdxDoc.getSpdxPackage().setDescription( projectInformation.getDescription() );
            }
            // download url
            if ( projectInformation.getDownloadUrl() != null ) {
                spdxDoc.getSpdxPackage().setDownloadUrl( projectInformation.getDownloadUrl() );
            }
            // archive file name
            if ( projectInformation.getPackageArchiveFileName() != null ) {
                spdxDoc.getSpdxPackage().setFileName( projectInformation.getPackageArchiveFileName() );
            }
            // home page
            if ( projectInformation.getHomePage() != null ) {
                spdxDoc.getSpdxPackage().setHomePage( projectInformation.getHomePage() );
            }
            // source information
            if ( projectInformation.getSourceInfo() != null ) {
                spdxDoc.getSpdxPackage().setSourceInfo( projectInformation.getSourceInfo() );
            }
            // license comment
            if ( projectInformation.getLicenseComment() != null ) {
                spdxDoc.getSpdxPackage().setLicenseComment( projectInformation.getLicenseComment() );
            }
            // originator
            if ( projectInformation.getOriginator() != null ) {
                spdxDoc.getSpdxPackage().setOriginator( projectInformation.getOriginator() );
            }
            // sha1 checksum
            if ( projectInformation.getSha1() != null ) {
                spdxDoc.getSpdxPackage().setSha1( projectInformation.getSha1() );
            }
            // copyright text
            if ( projectInformation.getCopyrightText() != null ) {
                spdxDoc.getSpdxPackage().setDeclaredCopyright( projectInformation.getCopyrightText() );
            }
            // short description
            if ( projectInformation.getShortDescription() != null ) {
                spdxDoc.getSpdxPackage().setShortDescription( projectInformation.getShortDescription() );
            }
            // supplier
            if ( projectInformation.getSupplier() != null ) {
                spdxDoc.getSpdxPackage().setSupplier( projectInformation.getSupplier() );
            }
            // version info        
            if ( projectInformation.getVersionInfo() != null ) {
                spdxDoc.getSpdxPackage().setVersionInfo( projectInformation.getVersionInfo() );
            }
        } catch ( InvalidSPDXAnalysisException e ) {
            this.getLog().error( "SPDX error filling SPDX information", e );
            throw( new MojoExecutionException( "Error adding package information to SPDX document: "+e.getMessage(), e ) );
        } catch ( InvalidLicenseStringException e ) {
            this.getLog().error( "SPDX error creating license", e );
            throw( new MojoExecutionException( "Error adding package information to SPDX document: "+e.getMessage(), e ) );
        }
    }

    private void fillCreatorInfo( SPDXDocument spdxDoc,
            SpdxProjectInformation projectInformation ) throws InvalidSPDXAnalysisException {
        ArrayList<String> creators = new ArrayList<String>();
        creators.add( CREATOR_TOOL_MAVEN_PLUGIN );
        String[] parameterCreators = projectInformation.getCreators();
        for ( int i = 0; i < parameterCreators.length; i++ ) {
            String verify = SpdxVerificationHelper.verifyCreator( parameterCreators[i] );
            if ( verify == null ) {
                creators.add( parameterCreators[i] );
            } else {
                this.getLog().warn( "Invalid creator string ( "+verify+" ), "+
                            parameterCreators[i]+" will be skipped." );
            }
        }
        SPDXCreatorInformation spdxCreator = new SPDXCreatorInformation(
                creators.toArray( new String[creators.size()] ), format.format( new Date() ),
                projectInformation.getCreatorComment(), 
                SPDXLicenseInfoFactory.DEFAULT_LICENSE_LIST_VERSION );
        spdxDoc.setCreationInfo( spdxCreator );        
    }

    private void collectSpdxFileInformation( SPDXDocument spdxDoc,
            Pattern[] excludedFilePatterns, File[] includedDirectories,
            SpdxDefaultFileInformation defaultFileInformation,
            String spdxFileName ) throws InvalidSPDXAnalysisException, MojoExecutionException {
        
        SpdxFileCollector fileCollector = new SpdxFileCollector( excludedFilePatterns );

        for ( int i = 0; i < includedDirectories.length; i++ ) {
            try {
                fileCollector.collectFilesInDirectory( includedDirectories[i], defaultFileInformation );
            } catch ( SpdxCollectionException e ) {
                this.getLog().error( "SPDX error collecting file information", e );
                throw( new MojoExecutionException( "Error collecting SPDX file information: "+e.getMessage() ) );
            }
        }
        spdxDoc.getSpdxPackage().setFiles( fileCollector.getFiles() );
        spdxDoc.getSpdxPackage().setLicenseInfoFromFiles( fileCollector.getLicenseInfoFromFiles() );
        try {
            spdxDoc.getSpdxPackage().setVerificationCode( fileCollector.getVerificationCode( spdxFileName ) );
        } catch ( NoSuchAlgorithmException e )  {
            this.getLog().error( "Error calculating verification code", e );
            throw( new MojoExecutionException( "Unable to calculate verification code" ) );
        } catch ( InvalidSPDXAnalysisException e ) {
            this.getLog().error( "SPDX Error updating verification code", e );
            throw( new MojoExecutionException( "Unable to update verification code" ) );
        }
    }
}
