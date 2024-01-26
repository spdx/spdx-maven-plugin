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
package org.spdx.maven.utils;

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
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;

import org.spdx.jacksonstore.MultiFormatStore;
import org.spdx.jacksonstore.MultiFormatStore.Format;
import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.ModelCopyManager;
import org.spdx.library.SpdxConstants;
import org.spdx.library.SpdxVerificationHelper;
import org.spdx.library.model.Annotation;
import org.spdx.library.model.Relationship;
import org.spdx.library.model.SpdxCreatorInformation;
import org.spdx.library.model.SpdxDocument;
import org.spdx.library.model.SpdxElement;
import org.spdx.library.model.SpdxModelFactory;
import org.spdx.library.model.SpdxPackage;
import org.spdx.library.model.SpdxPackageVerificationCode;
import org.spdx.library.model.enumerations.ChecksumAlgorithm;
import org.spdx.library.model.enumerations.RelationshipType;
import org.spdx.library.model.license.LicenseInfoFactory;
import org.spdx.library.model.license.ListedLicenses;
import org.spdx.library.model.license.SpdxListedLicense;
import org.spdx.maven.ExternalReference;
import org.spdx.maven.NonStandardLicense;
import org.spdx.maven.OutputFormat;
import org.spdx.spdxRdfStore.RdfStore;
import org.spdx.storage.IModelStore.IdType;
import org.spdx.storage.ISerializableModelStore;
import org.spdx.storage.simple.InMemSpdxStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds SPDX documents for a given set of source files. This is the primary class to use when creating SPDX documents
 * based on project files.
 *
 * @author Gary O'Neall
 */
public class SpdxDocumentBuilder
{
    private static final Logger LOG = LoggerFactory.getLogger( SpdxDocumentBuilder.class );

    private static final String UNSPECIFIED = "UNSPECIFIED";

    public static final String NULL_SHA1 = "cf23df2207d99a74fbe169e3eba035e633b65d94";

    //TODO: Use a previous SPDX to document file specific information and update
    //TODO: Map the SPDX document to the Maven build artifacts
    DateFormat format = new SimpleDateFormat( SpdxConstants.SPDX_DATE_FORMAT );

    private MavenProject project;
    private boolean generatePurls;
    private SpdxDocument spdxDoc;
    private SpdxPackage projectPackage;
    private LicenseManager licenseManager;
    private File spdxFile;

    private ISerializableModelStore modelStore;

    private ModelCopyManager copyManager;

    /**
     * @param spdxFile                File to store the SPDX document results
     * @param spdxDocumentNamespace   URI for SPDX document - must be unique
     * @param useStdLicenseSourceUrls if true, map any SPDX standard license source URL to license ID.  Note:
     *                                significant performance degradation
     * @param outputFormat            File format for the SPDX file
     * @throws SpdxBuilderException
     * @throws LicenseMapperException
     */
    public SpdxDocumentBuilder( MavenProject project, boolean generatePurls, File spdxFile, URI spdxDocumentNamespace,
                                boolean useStdLicenseSourceUrls, OutputFormat outputFormat ) throws SpdxBuilderException, LicenseMapperException
    {
        this.project = project;
        this.generatePurls = generatePurls;
        this.spdxFile = spdxFile;

        if ( spdxDocumentNamespace == null )
        {
            throw new SpdxBuilderException( "Missing spdxDocumentNamespace" );
        }

        // Handle the SPDX file
        if ( !spdxFile.exists() )
        {
            File parentDir = spdxFile.getParentFile();
            if ( parentDir != null && !parentDir.exists() )
            {
                if ( !parentDir.mkdirs() )
                {
                    throw new SpdxBuilderException( "Unable to create directories for SPDX file" );
                }
            }

            try
            {
                if ( !spdxFile.createNewFile() )
                {
                    throw new SpdxBuilderException( "Unable to create the SPDX file" );
                }
            }
            catch ( IOException e )
            {
                throw new SpdxBuilderException( "IO error creating the SPDX file", e );
            }
        }
        if ( !spdxFile.canWrite() )
        {
            throw new SpdxBuilderException( "Unable to write to SPDX file - check permissions: " + spdxFile.getPath() );
        }

        // create the SPDX document
        try
        {
            modelStore = outputFormat == OutputFormat.RDF_XML ? new RdfStore() :  new MultiFormatStore( new InMemSpdxStore(), Format.JSON_PRETTY );
            copyManager = new ModelCopyManager();
            spdxDoc = SpdxModelFactory.createSpdxDocument( modelStore, spdxDocumentNamespace.toString(), copyManager );
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            throw new SpdxBuilderException( "Error creating SPDX document", e );
        }

        // process the licenses
        licenseManager = new LicenseManager( spdxDoc, useStdLicenseSourceUrls );
    }

    /**
     * Add non-standard licenses to the SPDX document.
     *
     * @param nonStandardLicenses
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
                    throw new SpdxBuilderException( "Error adding non standard license", e );
                }
            }
        }
    }

    public SpdxDocument getSpdxDoc()
    {
        return this.spdxDoc;
    }

    public void saveSpdxDocumentToFile() throws SpdxBuilderException
    {
        try ( FileOutputStream spdxOut = new FileOutputStream( spdxFile ) )
        {
            modelStore.serialize( spdxDoc.getDocumentUri(), spdxOut );
        }
        catch ( FileNotFoundException e )
        {
            throw new SpdxBuilderException( "Error saving SPDX data to file", e );
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            throw new SpdxBuilderException( "Error collecting SPDX file data", e );
        }
        catch ( IOException e )
        {
            throw new SpdxBuilderException( "I/O Error saving SPDX data to file", e );
        }
    }

    /**
     * Add dependency information to the SPDX file
     *
     * @param dependencyInformation dependency information collected from the project POM file
     * @throws SpdxBuilderException
     */
    public void addDependencyInformation( SpdxDependencyInformation dependencyInformation ) throws SpdxBuilderException
    {
        Map<SpdxElement, List<Relationship>> packageRelationships = dependencyInformation.getRelationships();
        if ( packageRelationships != null )
        {
            for ( Map.Entry<SpdxElement, List<Relationship>> entry : packageRelationships.entrySet() )
            {
                SpdxElement parentElement = entry.getKey();
                List<Relationship> relationships = entry.getValue();

                for ( Relationship relationship : relationships )
                {
                    try
                    {
                        parentElement.addRelationship( relationship );
                    }
                    catch ( InvalidSPDXAnalysisException e )
                    {
                        throw new SpdxBuilderException("Unable to set package dependencies", e);
                    }
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
    public void fillSpdxDocumentInformation( SpdxProjectInformation projectInformation ) throws SpdxBuilderException
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
            throw new SpdxBuilderException( "Error adding package information to SPDX document", e );
        }
    }

    private Collection<Annotation> toSpdxAnnotations( org.spdx.maven.Annotation[] annotations ) throws MojoExecutionException
    {
        List<Annotation> retval = new ArrayList<>();
        for ( org.spdx.maven.Annotation annotation: annotations )
        {
            retval.add( annotation.toSpdxAnnotation( spdxDoc ) );
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
            LOG.warn( "Invalid download location in POM file: " + projectInformation.getDownloadUrl() );
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
            throw new SpdxBuilderException( "Error creating null package verification code", e );
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
                            .setExternalRefs( SpdxExternalRefBuilder.getDefaultExternalRefs( spdxDoc, generatePurls, project ) )
                            .build();
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            throw new SpdxBuilderException( "Error creating initial package", e );
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
                throw new SpdxBuilderException( "Error adding package annotations to SPDX document", e );
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
                    LOG.warn( "Invalid URL in project POM file: "+projectInformation.getHomePage() );
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
            throw new SpdxBuilderException( "Error adding package properties", e );
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
                throw new SpdxBuilderException(
                        "Error adding package information to SPDX document - Invalid checksum provided", e );
            }
        }
        // external references
        ExternalReference[] externalRefs = projectInformation.getExternalRefs();
        if ( externalRefs != null && externalRefs.length > 0 )
        {
            for ( ExternalReference externalRef : externalRefs )
            {
                try
                {
                    pkg.getExternalRefs().add( externalRef.getExternalRef( spdxDoc ) );
                }
                catch ( MojoExecutionException | InvalidSPDXAnalysisException e )
                {
                    throw new SpdxBuilderException(
                            "Error adding package information to SPDX document - Invalid external refs provided", e );
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
                LOG.warn(
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
     * @param sources                     Source directories to be included in the document
     * @param baseDir                     project base directory used to construct the relative paths for the SPDX
     *                                    files
     * @param pathSpecificInformation     Map of path to file information used to override the default file information
     * @param algorithms                  algorithms to use to generate checksums
     * @throws SpdxBuilderException
     */
    public void collectSpdxFileInformation( List<FileSet> sources, String baseDir,
                                            SpdxDefaultFileInformation defaultFileInformation,
                                            Map<String, SpdxDefaultFileInformation> pathSpecificInformation, 
                                            Set<ChecksumAlgorithm> algorithms ) throws SpdxBuilderException
    {
        SpdxFileCollector fileCollector = new SpdxFileCollector();
        try
        {
            fileCollector.collectFiles( sources, baseDir, defaultFileInformation,
                    pathSpecificInformation, projectPackage, RelationshipType.GENERATES, spdxDoc, algorithms );
            projectPackage.getFiles().addAll( fileCollector.getFiles() );
            projectPackage.getLicenseInfoFromFiles().addAll( fileCollector.getLicenseInfoFromFiles() );
        }
        catch ( SpdxCollectionException|InvalidSPDXAnalysisException e )
        {
            throw new SpdxBuilderException( "Error collecting SPDX file information", e );
        }
        try
        {
            String spdxFileName = spdxFile.getPath().replace( "\\", "/" );
            projectPackage.setPackageVerificationCode( fileCollector.getVerificationCode( spdxFileName, spdxDoc ) );
        }
        catch ( NoSuchAlgorithmException e )
        {
            throw new SpdxBuilderException( "Unable to calculate verification code", e );
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            throw new SpdxBuilderException( "Unable to update verification code", e );
        }
    }

    public LicenseManager getLicenseManager()
    {
        return this.licenseManager;
    }

    public SpdxPackage getProjectPackage()
    {
        return projectPackage;
    }

}
