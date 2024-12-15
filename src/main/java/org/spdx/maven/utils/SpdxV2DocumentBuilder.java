/**
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Source Auditor Inc.
 */
package org.spdx.maven.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.apache.maven.model.License;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spdx.core.CoreModelObject;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.jacksonstore.MultiFormatStore;
import org.spdx.jacksonstore.MultiFormatStore.Format;
import org.spdx.library.LicenseInfoFactory;
import org.spdx.library.ListedLicenses;
import org.spdx.library.ModelCopyManager;
import org.spdx.library.model.v2.Annotation;
import org.spdx.library.model.v2.ExternalRef;
import org.spdx.library.model.v2.ReferenceType;
import org.spdx.library.model.v2.Relationship;
import org.spdx.library.model.v2.SpdxConstantsCompatV2;
import org.spdx.library.model.v2.SpdxCreatorInformation;
import org.spdx.library.model.v2.SpdxDocument;
import org.spdx.library.model.v2.SpdxModelFactoryCompatV2;
import org.spdx.library.model.v2.SpdxPackage;
import org.spdx.library.model.v2.SpdxPackageVerificationCode;
import org.spdx.library.model.v2.SpdxVerificationHelper;
import org.spdx.library.model.v2.enumerations.ReferenceCategory;
import org.spdx.library.model.v2.enumerations.RelationshipType;
import org.spdx.library.model.v2.license.AnyLicenseInfo;
import org.spdx.library.model.v2.license.SpdxListedLicense;
import org.spdx.library.referencetype.ListedReferenceTypes;
import org.spdx.library.model.v2.enumerations.AnnotationType;
import org.spdx.library.model.v2.enumerations.ChecksumAlgorithm;
import org.spdx.library.model.v2.enumerations.Purpose;
import org.spdx.maven.Checksum;
import org.spdx.maven.ExternalReference;
import org.spdx.maven.NonStandardLicense;
import org.spdx.maven.OutputFormat;
import org.spdx.maven.Packaging;
import org.spdx.spdxRdfStore.RdfStore;
import org.spdx.storage.IModelStore.IdType;
import org.spdx.storage.simple.InMemSpdxStore;

/**
 * Builder for SPDX Spec version 2 SPDX Documents
 * 
 * @author Gary O'Neall
 *
 */
public class SpdxV2DocumentBuilder
    extends AbstractDocumentBuilder
{
    private static final Logger LOG = LoggerFactory.getLogger( SpdxV2DocumentBuilder.class );

    protected SpdxDocument spdxDoc;
    protected SpdxV2LicenseManager licenseManager;
    protected SpdxPackage projectPackage;
    
    /**
     * @param mavenProject             Maven project
     * @param generatePurls            If true, generated Package URLs for all package references
     * @param spdxFile                 File to store the SPDX document results
     * @param spdxDocumentNamespace    SPDX Document namespace - must be unique
     * @param outputFormatEnum         output format to use for storing the SPDX file
     */
    public SpdxV2DocumentBuilder( MavenProject mavenProject, boolean generatePurls, File spdxFile, URI spdxDocumentNamespace,
                                  OutputFormat outputFormatEnum ) throws SpdxBuilderException, LicenseMapperException
    {
        super( mavenProject, generatePurls, spdxFile, outputFormatEnum );
        if ( spdxDocumentNamespace == null )
        {
            throw new SpdxBuilderException( "Missing namespaceUri" );
        }
        
        // create the SPDX document
        try
        {
            modelStore = outputFormatEnum == OutputFormat.RDF_XML ? new RdfStore( spdxDocumentNamespace.toString() ) :  new MultiFormatStore( new InMemSpdxStore(), Format.JSON_PRETTY );
            copyManager = new ModelCopyManager();
            spdxDoc = SpdxModelFactoryCompatV2.createSpdxDocumentV2( modelStore, spdxDocumentNamespace.toString(), copyManager );
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            throw new SpdxBuilderException( "Error creating SPDX document", e );
        }

        // process the licenses
        licenseManager = new SpdxV2LicenseManager( spdxDoc);
    }

    /**
     * @return the SPDX Document
     */
    public SpdxDocument getSpdxDoc()
    {
        return this.spdxDoc;
    }

    @Override
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
            SpdxListedLicense dataLicense = LicenseInfoFactory.getListedLicenseByIdCompatV2( SpdxConstantsCompatV2.SPDX_DATA_LICENSE_ID );
            spdxDoc.setDataLicense( dataLicense );
            // annotations
            if ( projectInformation.getDocumentAnnotations() != null && projectInformation.getDocumentAnnotations().length > 0 )
            {
                spdxDoc.setAnnotations( toSpdxAnnotations( projectInformation.getDocumentAnnotations() ) );
            }
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
            
            @SuppressWarnings("UnusedAssignment") AnnotationType annotationType = AnnotationType.OTHER;
            try
            {
                annotationType = AnnotationType.valueOf( annotation.getAnnotationType() );
            }
            catch ( Exception ex )
            {
                throw new MojoExecutionException( "Invalid annotation type "+annotation.getAnnotationType() );
            }
            try
            {
                retval.add(  spdxDoc.createAnnotation( annotation.getAnnotator(), 
                                                       annotationType,
                                                       annotation.getAnnotationDate(),
                                                       annotation.getAnnotationComment() ) );
            }
            catch ( InvalidSPDXAnalysisException e )
            {
                throw new MojoExecutionException( "Error creating annotation.", e );
            }
        }
        return retval;
    }
    
    /**
     * Fill in the creator information to the SPDX document
     *
     * @param projectInformation project level information including the creators
     * @throws InvalidSPDXAnalysisException on SPDX parsing errors
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
                LOG.warn("Invalid creator string ( {} ), {} will be skipped.", verify, parameterCreator);
            }
        }
        SpdxCreatorInformation spdxCreator = spdxDoc.createCreationInfo( creators, format.format( new Date() ) );
        spdxCreator.setComment( projectInformation.getCreatorComment() );
        spdxCreator.setLicenseListVersion( ListedLicenses.getListedLicenses().getLicenseListVersion() );
        spdxDoc.setCreationInfo( spdxCreator );
    }
    
    private SpdxPackage createSpdxPackage( SpdxProjectInformation projectInformation ) throws SpdxBuilderException
    {
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
            LOG.warn("Invalid download location in POM file: {}", projectInformation.getDownloadUrl());
        }
        if ( downloadUrl == null )
        {
            downloadUrl = UNSPECIFIED;
        }
        SpdxPackageVerificationCode nullPackageVerificationCode;
        try
        {
            nullPackageVerificationCode = spdxDoc.createPackageVerificationCode( NULL_SHA1, new ArrayList<>() );
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            throw new SpdxBuilderException( "Error creating null package verification code", e );
        }
        SpdxPackage pkg;
        try
        {
            final AnyLicenseInfo concludedLicense = LicenseInfoFactory
                            .parseSPDXLicenseStringCompatV2( projectInformation.getConcludedLicense(), spdxDoc.getModelStore(), 
                                                             spdxDoc.getDocumentUri(), spdxDoc.getCopyManager() );
            final AnyLicenseInfo declaredLicense = LicenseInfoFactory
                            .parseSPDXLicenseStringCompatV2( projectInformation.getDeclaredLicense(), spdxDoc.getModelStore(), 
                                                             spdxDoc.getDocumentUri(), spdxDoc.getCopyManager() );
            final Packaging packaging = Packaging.valueOfPackaging( project.getPackaging() );
            final Purpose primaryPurpose = packaging != null ? packaging.getV2Purpose() : Purpose.LIBRARY;
            pkg = spdxDoc.createPackage( spdxDoc.getModelStore().getNextId( IdType.SpdxId ), 
                                                     projectInformation.getName(), concludedLicense,
                                                     copyrightText, declaredLicense )
                            .setDownloadLocation( downloadUrl )
                            .setPackageVerificationCode( nullPackageVerificationCode )
                            .setPrimaryPurpose( primaryPurpose )
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
                    LOG.warn("Invalid URL in project POM file: {}", projectInformation.getHomePage());
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
                for ( Checksum checksum : projectInformation.getChecksums() )
                {
                    try
                    {
                        final ChecksumAlgorithm algorithm = ChecksumAlgorithm.valueOf( checksum.getAlgorithm() );
                        pkg.getChecksums().add( spdxDoc.createChecksum( algorithm, checksum.getValue() ));
                    }
                    catch ( IllegalArgumentException | NullPointerException e1 )
                    {
                        LOG.error("Invalid checksum algorithm {}", checksum.getAlgorithm());
                    }
                }
            }
            catch ( InvalidSPDXAnalysisException e )
            {
                throw new SpdxBuilderException(
                        "Error adding package information to SPDX document - Invalid checksum provided", e );
            }
        }
        // external references
        ExternalReference[] externalRefs = projectInformation.getExternalRefs();
        if (externalRefs != null)
        {
            for ( ExternalReference externalRef : externalRefs )
            {
                try
                {
                    pkg.getExternalRefs().add( convertExternalRef( externalRef ) );
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

    @Override
    public void collectSpdxFileInformation( List<FileSet> sources, String baseDir,
                                            SpdxDefaultFileInformation defaultFileInformation,
                                            HashMap<String, SpdxDefaultFileInformation> pathSpecificInformation,
                                            Set<String> checksumAlgorithms ) throws SpdxBuilderException
    {
        SpdxV2FileCollector fileCollector = new SpdxV2FileCollector();
        try
        {
            fileCollector.collectFiles( sources, baseDir, defaultFileInformation,
                    pathSpecificInformation, projectPackage, RelationshipType.GENERATES, spdxDoc, checksumAlgorithms );
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

    @Override
    public void saveSpdxDocumentToFile() throws SpdxBuilderException
    {
        try ( FileOutputStream spdxOut = new FileOutputStream( spdxFile ) )
        {
            modelStore.serialize( spdxOut );
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

    @Override
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
    
    public ExternalRef convertExternalRef( ExternalReference externalReference ) throws MojoExecutionException
    {
        ReferenceCategory cat;
        
        try {
            cat = ReferenceCategory.valueOf( externalReference.getCategory().replaceAll( "-", "_" ) );
        }
        catch ( Exception ex )
        {
            throw new MojoExecutionException("External reference category " + externalReference.getCategory() + " is not recognized as a valid, standard category." );
        }
        ReferenceType refType;
        try
        {
            refType = ListedReferenceTypes.getListedReferenceTypes().getListedReferenceTypeByName( externalReference.getType() );
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            throw new MojoExecutionException( "Error getting listed reference type for " + externalReference.getType(), e );
        }
        if ( refType == null )
        {
            throw new MojoExecutionException( "Listed reference type not found for " + externalReference.getType() );
        }
        try
        {
            return spdxDoc.createExternalRef( cat, refType, externalReference.getLocator(), externalReference.getComment() );
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            throw new MojoExecutionException( "Error creating External Reference", e );
        }
    }

    @Override
    public CoreModelObject getProjectPackage()
    {
        return projectPackage;
    }

    @Override
    public String mavenLicenseListToSpdxLicenseExpression( List<License> mavenLicenses ) throws LicenseManagerException
    {
        return licenseManager.mavenLicenseListToSpdxLicense( mavenLicenses ).toString();
    }

    @Override
    public List<String> verify()
    {
        return spdxDoc.verify();
    }
    
    /**
     * @return the license manager
     */
    public SpdxV2LicenseManager getLicenseManager()
    {
        return this.licenseManager;
    }

}
