/*
 * Copyright 2014 Source Auditor Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package org.spdx.maven;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.shared.model.fileset.FileSet;
import org.spdx.jacksonstore.MultiFormatStore;
import org.spdx.jacksonstore.MultiFormatStore.Format;
import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.ModelCopyManager;
import org.spdx.library.SpdxConstants;
import org.spdx.library.SpdxVerificationHelper;
import org.spdx.library.model.Annotation;
import org.spdx.library.model.ExternalRef;
import org.spdx.library.model.Relationship;
import org.spdx.library.model.SpdxCreatorInformation;
import org.spdx.library.model.SpdxDocument;
import org.spdx.library.model.SpdxModelFactory;
import org.spdx.library.model.SpdxPackage;
import org.spdx.library.model.SpdxPackageVerificationCode;
import org.spdx.library.model.enumerations.ChecksumAlgorithm;
import org.spdx.library.model.enumerations.RelationshipType;
import org.spdx.library.model.license.LicenseInfoFactory;
import org.spdx.library.model.license.ListedLicenses;
import org.spdx.library.model.license.SpdxListedLicense;
import org.spdx.spdxRdfStore.RdfStore;
import org.spdx.storage.IModelStore.IdType;
import org.spdx.storage.ISerializableModelStore;
import org.spdx.storage.simple.InMemSpdxStore;

/**
 * Builds SPDX documents for a given set of source files. This is the primary class to use when creating SPDX documents
 * based on project files.
 *
 * @author Gary O'Neall
 */
public class SpdxDocumentBuilder
{
    private static final String UNSPECIFIED = "UNSPECIFIED";

    public static final String NULL_SHA1 = "cf23df2207d99a74fbe169e3eba035e633b65d94";

    //TODO: Use a previous SPDX to document file specific information and update
    //TODO: Map the SPDX document to the Maven build artifacts
    DateFormat format = new SimpleDateFormat( SpdxConstants.SPDX_DATE_FORMAT );

    private Log log;
    private SpdxDocument spdxDoc;
    private SpdxPackage projectPackage;
    private LicenseManager licenseManager;
    private File spdxFile;

    private ISerializableModelStore modelStore;

    private ModelCopyManager copyManager;

    /**
     * @param log                     Log for logging information and errors
     * @param spdxFile                File to store the SPDX document results
     * @param spdxDocumentNamespace   URI for SPDX document - must be unique
     * @param useStdLicenseSourceUrls if true, map any SPDX standard license source URL to license ID.  Note:
     *                                significant performance degradation
     * @param outputFormat            File format for the SPDX file
     * @throws SpdxBuilderException
     * @throws LicenseMapperException
     */
    public SpdxDocumentBuilder( Log log, File spdxFile, URI spdxDocumentNamespace, 
                                boolean useStdLicenseSourceUrls, String outputFormat ) throws SpdxBuilderException, LicenseMapperException
    {
        this.log = log;
        this.spdxFile = spdxFile;

        if ( spdxDocumentNamespace == null )
        {
            this.getLog().error( "spdxDocumentNamespace must be specified as a configuration parameter" );
            throw ( new SpdxBuilderException( "Missing spdxDocumentNamespace" ) );
        }

        // Handle the SPDX file
        if ( !spdxFile.exists() )
        {
            File parentDir = spdxFile.getParentFile();
            if ( parentDir != null && !parentDir.exists() )
            {
                if ( !parentDir.mkdirs() )
                {
                    this.getLog().error(
                            "Unable to create directory containing the SPDX file: " + parentDir.getPath() );
                    throw ( new SpdxBuilderException( "Unable to create directories for SPDX file" ) );
                }
            }

            try
            {
                if ( !spdxFile.createNewFile() )
                {
                    this.getLog().error( "Unable to create the SPDX file: " + spdxFile.getPath() );
                    throw ( new SpdxBuilderException( "Unable to create the SPDX file" ) );
                }
            }
            catch ( IOException e )
            {
                this.getLog().error( "IO error creating the SPDX file " + spdxFile.getPath() + ":" + e.getMessage(),
                        e );
                throw ( new SpdxBuilderException( "IO error creating the SPDX file" ) );
            }
        }
        if ( !spdxFile.canWrite() )
        {
            this.getLog().error( "Can not write to SPDX file " + spdxFile.getPath() );
            throw ( new SpdxBuilderException(
                    "Unable to write to SPDX file - check permissions: " + spdxFile.getPath() ) );
        }

        // create the SPDX document
        try
        {
            if (outputFormat.equals( CreateSpdxMojo.RDF_OUTPUT_FORMAT )) 
            {
                modelStore = new RdfStore();
            }
            else 
            {
                // use the default JSON
                modelStore = new MultiFormatStore( new InMemSpdxStore(), Format.JSON_PRETTY );
            }
            copyManager = new ModelCopyManager();
            spdxDoc = SpdxModelFactory.createSpdxDocument( modelStore, spdxDocumentNamespace.toString(), copyManager );
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            this.getLog().error( "Error creating SPDX document", e );
            throw ( new SpdxBuilderException( "Error creating SPDX document: " + e.getMessage() ) );
        }

        // process the licenses
        licenseManager = new LicenseManager( spdxDoc, getLog(), useStdLicenseSourceUrls );
    }

    /**
     * Add non-standard licenses to the SPDX document.
     *
     * @param spdxDoc
     * @throws SpdxBuilderException
     */
    public void addNonStandardLicenses( NonStandardLicense[] nonStandardLicenses ) throws SpdxBuilderException
    {
        if ( nonStandardLicenses != null )
        {
            for ( NonStandardLicense nonStandardLicense : nonStandardLicenses )
            {
                try
                {
                    // the following will add the non-standard license to the document container
                    licenseManager.addExtractedLicense( nonStandardLicense );
                }
                catch ( LicenseManagerException e )
                {
                    this.getLog().error( "Error adding license " + e.getMessage(), e );
                    throw ( new SpdxBuilderException( "Error adding non standard license: " + e.getMessage(), e ) );
                }
            }
        }
    }

    public Log getLog()
    {
        return this.log;
    }

    public void setLog( Log log )
    {
        this.log = log;
    }

    public SpdxDocument getSpdxDoc()
    {
        return this.spdxDoc;
    }

    /**
     * Build the SPDX document from the files and save the information to the SPDX file
     *
     * @param includedSourceDirectories   Source directories to be included in the document
     * @param includedResourceDirectories Test directories to be included in the document
     * @param includedTestDirectories     Resource directories to be included in the document
     * @param baseDir                     Base directory used to create the relative file paths for the SPDX file names
     * @param projectInformation          Project level SPDX information
     * @param defaultFileInformation      Default SPDX file information
     * @param pathSpecificInformation     Map of path to file information used to override the default file information
     * @param dependencyInformation       Dependencies to add to the SPDX file (typically based on project dependencies
     *                                    in the POM file)
     * @param algorithms                  algorithms to use to generate checksums
     * @throws SpdxBuilderException
     */
    public void buildDocumentFromFiles( FileSet[] includedSourceDirectories, 
                                        FileSet[] includedTestDirectories, 
                                        FileSet[] includedResourceDirectories, 
                                        String baseDir, SpdxProjectInformation projectInformation, 
                                        SpdxDefaultFileInformation defaultFileInformation, 
                                        Map<String, SpdxDefaultFileInformation> pathSpecificInformation, 
                                        SpdxDependencyInformation dependencyInformation, 
                                        Set<ChecksumAlgorithm> algorithms,
                                        String spdxDocumentNamespace ) throws SpdxBuilderException
    {
        FileOutputStream spdxOut = null;
        try
        {
            this.log.debug( "Starting buid document from files" );
            spdxOut = new FileOutputStream( spdxFile );
            fillSpdxDocumentInformation( projectInformation );
            collectSpdxFileInformation( includedSourceDirectories, includedTestDirectories, includedResourceDirectories,
                    baseDir, defaultFileInformation, spdxFile.getPath().replace( "\\", "/" ), pathSpecificInformation, algorithms );
            addDependencyInformation( dependencyInformation );
            modelStore.serialize( spdxDocumentNamespace, spdxOut );
            this.log.debug( "Completed build document from files" );
        }
        catch ( FileNotFoundException e )
        {
            this.getLog().error( "Error saving SPDX data to file", e );
            throw ( new SpdxBuilderException( "Error saving SPDX data to file: " + e.getMessage() ) );
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            this.getLog().error( "Error collecting SPDX file data", e );
            throw ( new SpdxBuilderException( "Error collecting SPDX file data: " + e.getMessage() ) );
        }
        catch ( IOException e )
        {
            this.getLog().error( "I/O Error saving SPDX data to file", e );
            throw ( new SpdxBuilderException( "I/O Error saving SPDX data to file: " + e.getMessage() ) );
        }
        finally
        {
            if ( spdxOut != null )
            {
                try
                {
                    spdxOut.close();
                }
                catch ( IOException e )
                {
                    this.getLog().warn( "Error closing SPDX output file", e );
                }
            }
        }
    }


    /**
     * Add dependency information to the SPDX file
     *
     * @param dependencyInformation dependency information collected from the project POM file
     * @throws SpdxBuilderException
     */
    private void addDependencyInformation( SpdxDependencyInformation dependencyInformation ) throws SpdxBuilderException
    {
        List<Relationship> packageRelationships = dependencyInformation.getToRelationships();
        if ( packageRelationships != null )
        {
            for ( Relationship relationship : packageRelationships )
            {
                try
                {
                    this.projectPackage.addRelationship( relationship );
                }
                catch ( InvalidSPDXAnalysisException e )
                {
                    log.error( "Unable to set package dependencies: " + e.getMessage() );
                    throw new SpdxBuilderException( "Unable to set package dependencies", e );
                }
            }
        }
        List<SpdxDependencyInformation.FromRelationship> fromRelationships = dependencyInformation.getFromRelationships();
        if ( fromRelationships != null )
        {
            for ( SpdxDependencyInformation.FromRelationship fromRelationship : fromRelationships )
            {
                try
                {
                    Relationship rel =fromRelationship.createAndAddRelationship( projectPackage );
                    log.debug( "Created relationship of type "+rel.getRelationshipType().toString() +  
                               " from "+fromRelationship.getFromPackage().getName() );
                }
                catch ( InvalidSPDXAnalysisException e )
                {
                    log.error( "Unable to dependency to package: " + e.getMessage() );
                    throw new SpdxBuilderException( "Unable to set dependency to package", e );
                }
            }
        }
    }

    /**
     * Fill in the document level information for SPDX
     *
     * @param projectInformation project information to be used
     * @throws SpdxBuilderException
     */
    private void fillSpdxDocumentInformation( SpdxProjectInformation projectInformation ) throws SpdxBuilderException
    {
        try
        {
            // document comment
            if ( projectInformation.getDocumentComment() != null && !projectInformation.getDocumentComment().isEmpty() )
            {
                spdxDoc.setComment( projectInformation.getDocumentComment() );
            }
            // creator
            fillCreatorInfo( projectInformation );
            // data license
            SpdxListedLicense dataLicense = LicenseInfoFactory.getListedLicenseById( SpdxConstants.SPDX_DATA_LICENSE_ID );
            spdxDoc.setDataLicense( dataLicense );
            // annotations
            if ( projectInformation.getDocumentAnnotations() != null && projectInformation.getDocumentAnnotations().length > 0 )
            {
                spdxDoc.setAnnotations( toSpdxAnnotations( projectInformation.getDocumentAnnotations() ) );
            }
            //TODO: Implement document annotations
            //TODO: Add document level relationships
            spdxDoc.setName( projectInformation.getName() );  // Same as package name
            // Package level information
            projectPackage = createSpdxPackage( projectInformation );
            Relationship documentContainsRelationship = spdxDoc.createRelationship( projectPackage, RelationshipType.DESCRIBES, "" );
            spdxDoc.addRelationship( documentContainsRelationship );
        }
        catch ( InvalidSPDXAnalysisException | MojoExecutionException e )
        {
            this.getLog().error( "SPDX error filling SPDX information", e );
            throw ( new SpdxBuilderException( "Error adding package information to SPDX document: " + e.getMessage(),
                    e ) );
        }
    }

    private Collection<Annotation> toSpdxAnnotations( org.spdx.maven.Annotation[] annotations ) throws MojoExecutionException
    {
        List<Annotation> retval = new ArrayList<>();
        for ( int i = 0; i < annotations.length; i++ )
        {
            retval.add( annotations[i].toSpdxAnnotation( spdxDoc ) );
        }
        return retval;
    }

    private SpdxPackage createSpdxPackage( SpdxProjectInformation projectInformation ) throws SpdxBuilderException
    {
        //TODO: Add annotations
        //TODO: Add relationships
        //TODO: Add comment
        String copyrightText = projectInformation.getCopyrightText();
        if ( copyrightText == null )
        {
            copyrightText = UNSPECIFIED;
        }
        String downloadUrl = null;
        
        if ( SpdxVerificationHelper.isValidUri( projectInformation.getDownloadUrl() ))
        {
            downloadUrl = projectInformation.getDownloadUrl();
        }
        else
        {
            log.warn( "Invalid download location in POM file: " + projectInformation.getDownloadUrl() );
        }
        if ( downloadUrl == null )
        {
            downloadUrl = UNSPECIFIED;
        }
        SpdxPackageVerificationCode nullPackageVerificationCode;
        try
        {
            nullPackageVerificationCode = spdxDoc.createPackageVerificationCode( NULL_SHA1, new ArrayList<String>() );
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            this.getLog().error( "Error creating null package verification code", e );
            throw ( new SpdxBuilderException(
                    "Error creating null package verification code: " + e.getMessage(), e ) );
        }
        SpdxPackage pkg;
        try
        {
            pkg = spdxDoc.createPackage( spdxDoc.getModelStore().getNextId( IdType.SpdxId, spdxDoc.getDocumentUri() ), 
                                                     projectInformation.getName(), projectInformation.getConcludedLicense(),
                                                     copyrightText, projectInformation.getDeclaredLicense() )
                            .setDownloadLocation( downloadUrl )
                            .setPackageVerificationCode( nullPackageVerificationCode )
                            .setPrimaryPurpose( projectInformation.getPrimaryPurpose() )
                            .build();
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            this.getLog().error( "Error creating initial package", e );
            throw ( new SpdxBuilderException(
                    "Error creating initial package: " + e.getMessage(), e ) );
        }
        // Annotations
        if ( projectInformation.getPackageAnnotations() != null && projectInformation.getPackageAnnotations().length > 0 )
        {
            try
            {
                pkg.setAnnotations( toSpdxAnnotations( projectInformation.getPackageAnnotations() ) );
            }
            catch ( InvalidSPDXAnalysisException | MojoExecutionException e )
            {
                this.getLog().error( "Invalid package annotation", e );
                throw ( new SpdxBuilderException(
                        "Error adding package annotations to SPDX document: " + e.getMessage(), e ) );
            }
        }
        try
        {
         // description
            if ( projectInformation.getDescription() != null )
            {
                pkg.setDescription( projectInformation.getDescription() );
            }
            // download url
            if ( projectInformation.getDownloadUrl() != null )
            {
                pkg.setDownloadLocation( projectInformation.getDownloadUrl() );
            }
            // archive file name
            if ( projectInformation.getPackageArchiveFileName() != null )
            {
                pkg.setPackageFileName( projectInformation.getPackageArchiveFileName() );
            }
            // home page
            if ( projectInformation.getHomePage() != null )
            {
                try
                {
                    pkg.setHomepage( projectInformation.getHomePage() );
                }
                catch( InvalidSPDXAnalysisException ex ) 
                {
                    log.warn( "Invalid URL in project POM file: "+projectInformation.getHomePage() );
                }
                
            }
            // source information
            if ( projectInformation.getSourceInfo() != null )
            {
                pkg.setSourceInfo( projectInformation.getSourceInfo() );
            }
            // license comment
            if ( projectInformation.getLicenseComment() != null )
            {
                pkg.setLicenseComments( projectInformation.getLicenseComment() );
            }
            // originator
            if ( projectInformation.getOriginator() != null )
            {
                pkg.setOriginator( projectInformation.getOriginator() );
            }
            // short description
            if ( projectInformation.getShortDescription() != null )
            {
                pkg.setSummary( projectInformation.getShortDescription() );
            }
            // supplier
            if ( projectInformation.getSupplier() != null )
            {
                pkg.setSupplier( projectInformation.getSupplier() );
            }
            // version info
            if ( projectInformation.getVersionInfo() != null )
            {
                pkg.setVersionInfo( projectInformation.getVersionInfo() );
            }
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            this.getLog().error( "Error adding package properties", e );
            throw ( new SpdxBuilderException(
                    "Error adding package properties: " + e.getMessage(), e ) );
        }
        
        // sha1 checksum
        if ( projectInformation.getChecksums() != null )
        {
            try
            {
                pkg.getChecksums().addAll( projectInformation.getChecksums() );
            }
            catch ( InvalidSPDXAnalysisException e )
            {
                this.getLog().error( "Invalid checksum value for package", e );
                throw ( new SpdxBuilderException(
                        "Error adding package information to SPDX document - Invalid checksum provided: " + e.getMessage(),
                        e ) );
            }
        }
        // external references
        List<ExternalReference> externalRefs = projectInformation.getExternalRefs();
        if ( externalRefs != null && externalRefs.size() > 0 )
        {
            
            ExternalRef[] externalRefAr = new ExternalRef[externalRefs.size()];
            for ( int i = 0; i < externalRefAr.length; i++ )
            {
                try
                {
                    pkg.getExternalRefs().add( externalRefs.get( i ).getExternalRef( spdxDoc ) );
                }
                catch ( MojoExecutionException | InvalidSPDXAnalysisException e )
                {
                    this.getLog().error( "Invalid external refs", e );
                    throw ( new SpdxBuilderException(
                            "Error adding package information to SPDX document - Invalid external refs provided: " + e.getMessage(),
                            e ) );
                }
            }
        }
        return pkg;
    }

    /**
     * Fill in the creator information to the SPDX document
     *
     * @param projectInformation project level information including the creators
     * @throws InvalidSPDXAnalysisException
     */
    private void fillCreatorInfo( SpdxProjectInformation projectInformation ) throws InvalidSPDXAnalysisException
    {
        ArrayList<String> creators = new ArrayList<>();
        String[] parameterCreators = projectInformation.getCreators();
        for ( String parameterCreator : parameterCreators )
        {
            String verify = SpdxVerificationHelper.verifyCreator( parameterCreator );
            if ( verify == null )
            {
                creators.add( parameterCreator );
            }
            else
            {
                this.getLog().warn(
                        "Invalid creator string ( " + verify + " ), " + parameterCreator + " will be skipped." );
            }
        }
        SpdxCreatorInformation spdxCreator = spdxDoc.createCreationInfo( creators, format.format( new Date() ) );
        spdxCreator.setComment( projectInformation.getCreatorComment() );
        spdxCreator.setLicenseListVersion( ListedLicenses.getListedLicenses().getLicenseListVersion() );
        spdxDoc.setCreationInfo( spdxCreator );
    }

    /**
     * Collect information at the file level, fill in the SPDX document
     *
     * @param includedSourceDirectories   Source directories to be included in the document
     * @param includedResourceDirectories Test directories to be included in the document
     * @param includedTestDirectories     Resource directories to be included in the document
     * @param baseDir                     project base directory used to construct the relative paths for the SPDX
     *                                    files
     * @param projectInformation          Project level SPDX information
     * @param spdxFileName                SPDX file name - will be used for the skipped file names in the verification
     *                                    code
     * @param pathSpecificInformation     Map of path to file information used to override the default file information
     * @param algorithms                  algorithms to use to generate checksums
     * @throws InvalidSPDXAnalysisException
     * @throws SpdxBuilderException
     */
    private void collectSpdxFileInformation( FileSet[] includedSourceDirectories, FileSet[] includedTestDirectories, FileSet[] includedResourceDirectories, 
                                             String baseDir, SpdxDefaultFileInformation defaultFileInformation, String spdxFileName, 
                                             Map<String, SpdxDefaultFileInformation> pathSpecificInformation, 
                                             Set<ChecksumAlgorithm> algorithms ) throws InvalidSPDXAnalysisException, SpdxBuilderException
    {
        SpdxFileCollector fileCollector = new SpdxFileCollector( getLog() );
        fileCollector.setLog( getLog() );
        try
        {
            fileCollector.collectFiles( includedSourceDirectories, baseDir, defaultFileInformation,
                    pathSpecificInformation, projectPackage, RelationshipType.GENERATES, spdxDoc, algorithms );
            fileCollector.collectFiles( includedTestDirectories, baseDir, defaultFileInformation,
                    pathSpecificInformation, projectPackage, RelationshipType.TEST_CASE_OF, spdxDoc, algorithms );
            fileCollector.collectFiles( includedResourceDirectories, baseDir, defaultFileInformation,
                    pathSpecificInformation, projectPackage, RelationshipType.CONTAINED_BY, spdxDoc, algorithms );
        }
        catch ( SpdxCollectionException e )
        {
            this.getLog().error( "SPDX error collecting file information", e );
            throw ( new SpdxBuilderException( "Error collecting SPDX file information: " + e.getMessage() ) );
        }
        projectPackage.getFiles().addAll( fileCollector.getFiles() );
        projectPackage.getLicenseInfoFromFiles().addAll( fileCollector.getLicenseInfoFromFiles() );
        try
        {
            projectPackage.setPackageVerificationCode( fileCollector.getVerificationCode( spdxFileName, spdxDoc ) );
        }
        catch ( NoSuchAlgorithmException e )
        {
            this.getLog().error( "Error calculating verification code", e );
            throw ( new SpdxBuilderException( "Unable to calculate verification code" ) );
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            this.getLog().error( "SPDX Error updating verification code", e );
            throw ( new SpdxBuilderException( "Unable to update verification code" ) );
        }
    }

    public LicenseManager getLicenseManager()
    {
        return this.licenseManager;
    }

}
