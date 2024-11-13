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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.maven.model.License;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spdx.core.CoreModelObject;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.library.LicenseInfoFactory;
import org.spdx.library.conversion.Spdx2to3Converter;
import org.spdx.library.model.v2.ReferenceType;
import org.spdx.library.model.v2.enumerations.ChecksumAlgorithm;
import org.spdx.library.model.v2.enumerations.ReferenceCategory;
import org.spdx.library.model.v3_0_1.SpdxConstantsV3;
import org.spdx.library.model.v3_0_1.SpdxModelClassFactoryV3;
import org.spdx.library.model.v3_0_1.core.AnnotationType;
import org.spdx.library.model.v3_0_1.core.CreationInfo;
import org.spdx.library.model.v3_0_1.core.DictionaryEntry;
import org.spdx.library.model.v3_0_1.core.Element;
import org.spdx.library.model.v3_0_1.core.HashAlgorithm;
import org.spdx.library.model.v3_0_1.core.Relationship;
import org.spdx.library.model.v3_0_1.core.RelationshipCompleteness;
import org.spdx.library.model.v3_0_1.core.RelationshipType;
import org.spdx.library.model.v3_0_1.core.SpdxDocument;
import org.spdx.library.model.v3_0_1.simplelicensing.AnyLicenseInfo;
import org.spdx.library.model.v3_0_1.software.Sbom;
import org.spdx.library.model.v3_0_1.software.SoftwareArtifact;
import org.spdx.library.model.v3_0_1.software.SoftwarePurpose;
import org.spdx.library.model.v3_0_1.software.SpdxFile;
import org.spdx.library.model.v3_0_1.software.SpdxPackage;
import org.spdx.library.referencetype.ListedReferenceTypes;
import org.spdx.maven.Checksum;
import org.spdx.maven.ExternalReference;
import org.spdx.maven.NonStandardLicense;
import org.spdx.maven.OutputFormat;
import org.spdx.maven.Packaging;
import org.spdx.storage.IModelStore.IdType;
import org.spdx.storage.simple.InMemSpdxStore;
import org.spdx.v3jsonldstore.JsonLDStore;

/**
 * Builder for SPDX Spec version 3 SBOMs
 * 
 * @author Gary O'Neall
 *
 */
public class SpdxV3DocumentBuilder
    extends AbstractDocumentBuilder
{
    private static final Logger LOG = LoggerFactory.getLogger( SpdxV3DocumentBuilder.class );
    
    protected CreationInfo creationInfo;
    protected Sbom sbom;
    protected SpdxDocument spdxDoc;
    protected SpdxPackage projectPackage;
    protected SpdxV3LicenseManager licenseManager;
    /**
     * Holds a mapping of IDs to URIs for any custom licenses defined outside the spdxDoc
     */
    protected List<DictionaryEntry> customIdToUri = new ArrayList<>();

    /**
     * @param mavenProject             Maven project
     * @param generatePurls            If true, generated Package URLs for all package references
     * @param spdxFile                 File to store the SPDX document results
     * @param namespaceUri             Namspace prefix for generated SPDX URIs document - must be unique
     * @param useStdLicenseSourceUrls  if true, map any SPDX standard license source URL to license ID.  Note:
     *                                 significant performance degradation 
     * @param outputFormatEnum
     */
    public SpdxV3DocumentBuilder( MavenProject mavenProject, boolean generatePurls, File spdxFile, URI namespaceUri,
                                  boolean useStdLicenseSourceUrls, 
                                  OutputFormat outputFormatEnum ) throws SpdxBuilderException, LicenseMapperException
    {
        super( mavenProject, generatePurls, spdxFile, outputFormatEnum );
        if ( namespaceUri == null )
        {
            throw new SpdxBuilderException( "Missing namespaceUri" );
        }
        
        if ( !OutputFormat.JSON_LD.equals( outputFormatEnum )) {
            throw new SpdxBuilderException( String.format( "Unsupported output format for SPDX spec version 3: %s",
                                                           outputFormatEnum.toString() ));
        }
        // create the SPDX document
        try
        {
            modelStore = new JsonLDStore( new InMemSpdxStore() );
            String supplier = ( mavenProject.getOrganization() != null && 
                            mavenProject.getOrganization().getName() != null 
                            && !mavenProject.getOrganization().getName().isEmpty() ) ? mavenProject.getOrganization().getName() : project.getName() ;

            creationInfo = SpdxModelClassFactoryV3.createCreationInfo( modelStore, namespaceUri + "Agent/supplier", supplier,
                                                                       copyManager);
            creationInfo.getCreatedUsings().add( creationInfo.createTool( namespaceUri + "Agent/SpdxMavenPlugin" )
                                                 .setName( "SPDX Maven Plugin" )
                                                 .setCreationInfo( creationInfo )
                                                 .build() );
            sbom = creationInfo.createSbom( namespaceUri + "sbom" ).build();
            spdxDoc = sbom.createSpdxDocument( namespaceUri + "/Document" )
                            .addRootElement( sbom )
                            .build();
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            throw new SpdxBuilderException( "Error creating SPDX SBOM", e );
        }

        // process the licenses
        licenseManager = new SpdxV3LicenseManager( spdxDoc, useStdLicenseSourceUrls );
        // TODO: if we want to support external custom licenses, we will need to add dictionary entries
        // to the customIdToUri
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
            // TODO: Add a data license option to the project configuration options
            // spdxDoc.setDataLicense( dataLicense );
            // annotations
            if ( projectInformation.getDocumentAnnotations() != null && projectInformation.getDocumentAnnotations().length > 0 )
            {
                addSpdxAnnotations( projectInformation.getDocumentAnnotations(), spdxDoc );
            }
            spdxDoc.setName( projectInformation.getName() );  // Same as package name
            // Package level information
            projectPackage = createSpdxPackage( projectInformation );
            sbom.getRootElements().add( projectPackage );
        }
        catch ( InvalidSPDXAnalysisException | MojoExecutionException e )
        {
            throw new SpdxBuilderException( "Error adding package information to SPDX document", e );
        }
    }
    
    /**
     * Adds annotation to the element
     * @param annotations             Annotations to add
     * @param element                 Elements to add the annotations to
     * @throws MojoExecutionException On any SPDX parsing errors (typically due to invalid annotation data)
     */
    private void addSpdxAnnotations( org.spdx.maven.Annotation[] annotations, Element element ) throws MojoExecutionException
    {
        for ( org.spdx.maven.Annotation annotation: annotations )
        {
            
            AnnotationType annotationType = AnnotationType.OTHER;
            try
            {
                annotationType = Spdx2to3Converter.ANNOTATION_TYPE_MAP.get( 
                                                                            org.spdx.library.model.v2.enumerations.AnnotationType
                                                                            .valueOf( annotation.getAnnotationType() ) );
            }
            catch ( Exception ex )
            {
                throw new MojoExecutionException( "Invalid annotation type "+annotation.getAnnotationType() );
            }
            try
            {
                CreationInfo creationInfo = new CreationInfo.CreationInfoBuilder( spdxDoc.getModelStore(), 
                                                                                  spdxDoc.getModelStore().getNextId(IdType.Anonymous), 
                                                                                  spdxDoc.getCopyManager() )
                                .setCreated( annotation.getAnnotationDate() )
                                .setSpecVersion( SpdxConstantsV3.MODEL_SPEC_VERSION )
                                .build();
                creationInfo.getCreatedBys().add( Spdx2to3Converter.stringToAgent( annotation.getAnnotator(), creationInfo ) );
                element.createAnnotation( element.getIdPrefix() + element.getModelStore().getNextId( IdType.SpdxId ) )
                       .setAnnotationType( annotationType )
                       .setStatement( annotation.getAnnotationComment() )
                       .setSubject( element )
                       .setCreationInfo( creationInfo )
                       .build();
            }
            catch ( InvalidSPDXAnalysisException e )
            {
                throw new MojoExecutionException( "Error creating annotation.", e );
            }
        }
    }
    
    /**
     * Fill in the creator information to the SPDX document
     *
     * @param projectInformation project level information including the creators
     * @throws InvalidSPDXAnalysisException
     */
    private void fillCreatorInfo( SpdxProjectInformation projectInformation ) throws InvalidSPDXAnalysisException
    {
        CreationInfo creationInfo = spdxDoc.getCreationInfo();
        String[] parameterCreators = projectInformation.getCreators();
        for ( String parameterCreator : parameterCreators )
        {
            try
            {
                creationInfo.getCreatedBys().add( Spdx2to3Converter.stringToAgent( parameterCreator,  creationInfo ) );
            }
            catch (InvalidSPDXAnalysisException e)
            {
                LOG.warn(
                         "Invalid creator string, " + parameterCreator + " will be skipped." );
            }
            
        }
        String comment = projectInformation.getCreatorComment();
        if ( Objects.nonNull( comment ) && !comment.isBlank() )
        {
            creationInfo.setComment( comment );
        }
    }
    
    private SpdxPackage createSpdxPackage( SpdxProjectInformation projectInformation ) throws SpdxBuilderException
    {
        String copyrightText = projectInformation.getCopyrightText();
        if ( copyrightText == null )
        {
            copyrightText = UNSPECIFIED;
        }
        String downloadUrl = projectInformation.getDownloadUrl();
        
        if ( downloadUrl == null || downloadUrl.isBlank() )
        {
            downloadUrl = UNSPECIFIED;
        }
        SpdxPackage pkg;
        try
        {
            
            final AnyLicenseInfo concludedLicense = LicenseInfoFactory
                            .parseSPDXLicenseString( projectInformation.getConcludedLicense(), spdxDoc.getModelStore(), 
                                                     spdxDoc.getIdPrefix(), spdxDoc.getCopyManager(), customIdToUri );
            final AnyLicenseInfo declaredLicense = LicenseInfoFactory
                            .parseSPDXLicenseString( projectInformation.getDeclaredLicense(), spdxDoc.getModelStore(), 
                                                     spdxDoc.getIdPrefix(), spdxDoc.getCopyManager(), customIdToUri );
            final Packaging packaging = Packaging.valueOfPackaging( project.getPackaging() );
            final SoftwarePurpose primaryPurpose = packaging != null ? packaging.getSoftwarePurpose() : SoftwarePurpose.LIBRARY;
            pkg = spdxDoc.createSpdxPackage( spdxDoc.getIdPrefix() + spdxDoc.getModelStore().getNextId( IdType.SpdxId ) )
                            .setName( projectInformation.getName() )
                            .setDownloadLocation( downloadUrl )
                            .setPrimaryPurpose( primaryPurpose )
                            .setCopyrightText( copyrightText )
                            .addAllExternalIdentifier( SpdxExternalIdBuilder.getDefaultExternalIdentifiers( spdxDoc, generatePurls, project ) )
                            .build();

            pkg.createRelationship( pkg.getIdPrefix() + pkg.getModelStore().getNextId( IdType.SpdxId ) )
                            .setRelationshipType( RelationshipType.HAS_DECLARED_LICENSE )
                            .setCompleteness( RelationshipCompleteness.COMPLETE )
                            .setFrom( pkg )
                            .addTo( declaredLicense )
                            .build();
            
            pkg.createRelationship( pkg.getIdPrefix() + pkg.getModelStore().getNextId( IdType.SpdxId ) )
                            .setRelationshipType( RelationshipType.HAS_CONCLUDED_LICENSE )
                            .setCompleteness( RelationshipCompleteness.COMPLETE )
                            .setFrom( pkg )
                            .addTo( concludedLicense )
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
                addSpdxAnnotations(  projectInformation.getPackageAnnotations(), pkg );
            }
            catch ( MojoExecutionException e )
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
                SpdxFile packageFile = pkg.createSpdxFile( pkg.getIdPrefix() + pkg.getModelStore().getNextId( IdType.SpdxId ) )
                                .setName( projectInformation.getPackageArchiveFileName() )
                                .build();
                
                if ( projectInformation.getChecksums() != null )
                {
                    try
                    {
                        for ( Checksum checksum : projectInformation.getChecksums() )
                        {
                            final HashAlgorithm algorithm = Spdx2to3Converter.HASH_ALGORITH_MAP.get( ChecksumAlgorithm.valueOf( checksum.getAlgorithm() ) );
                            if ( Objects.isNull( algorithm ))
                            {
                                LOG.error( String.format( "Invalid checksum algorithm %s", checksum.getAlgorithm() ) );
                            }
                            else
                            {
                                packageFile.getVerifiedUsings().add( packageFile.createHash( packageFile.getModelStore().getNextId( IdType.Anonymous ) )
                                                                     .setAlgorithm( algorithm )
                                                                     .setHashValue( checksum.getValue() )
                                                                     .build() );
                            }
                        }
                    }
                    catch ( InvalidSPDXAnalysisException e )
                    {
                        throw new SpdxBuilderException(
                                "Error adding package information to SPDX document - Invalid checksum provided", e );
                    }
                }
                
                pkg.createRelationship( pkg.getIdPrefix() + pkg.getModelStore().getNextId( IdType.SpdxId ) )
                                .setFrom( pkg )
                                .addTo( packageFile )
                                .setCompleteness( RelationshipCompleteness.COMPLETE )
                                .setRelationshipType( RelationshipType.HAS_DISTRIBUTION_ARTIFACT )
                                .build();
            }
            // home page
            if ( projectInformation.getHomePage() != null )
            {
                try
                {
                    pkg.setHomePage( projectInformation.getHomePage() );
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
                pkg.setComment( "License: " + projectInformation.getLicenseComment() );
            }
            // originator
            if ( projectInformation.getOriginator() != null )
            {
                pkg.getOriginatedBys().add( Spdx2to3Converter.stringToAgent( projectInformation.getOriginator(), pkg.getCreationInfo() ) );
            }
            // short description
            if ( projectInformation.getShortDescription() != null )
            {
                pkg.setSummary( projectInformation.getShortDescription() );
            }
            // supplier
            if ( projectInformation.getSupplier() != null )
            {
                pkg.setSuppliedBy( Spdx2to3Converter.stringToAgent( projectInformation.getSupplier(), pkg.getCreationInfo() ) );
            }
            // version info
            if ( projectInformation.getVersionInfo() != null )
            {
                pkg.setPackageVersion( projectInformation.getVersionInfo() );
            }
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            throw new SpdxBuilderException( "Error adding package properties", e );
        }
        // external references
        ExternalReference[] externalRefs = projectInformation.getExternalRefs();
        try
        {
            addExternalRefs( externalRefs, pkg );
        }
        catch ( MojoExecutionException e )
        {
            throw new SpdxBuilderException( "Error adding external references to package", e );
        }
       
        return pkg;
    }

    /**
     * @param externalRefs  ExternalRefs to add
     * @param artifact      Spdx Artifact to add the ExternalRefs to
     * @throws MojoExecutionException On invalid externalRef data
     */
    private void addExternalRefs( ExternalReference[] externalRefs, SoftwareArtifact artifact ) throws MojoExecutionException
    {
        if ( Objects.isNull( externalRefs ))
        {
            return;
        }
        for ( ExternalReference externalRef : externalRefs )
        {
            ReferenceCategory cat = null;
            
            try {
                cat = ReferenceCategory.valueOf( externalRef.getCategory().replaceAll( "-", "_" ) );
            }
            catch ( Exception ex )
            {
                throw new MojoExecutionException("External reference category " + externalRef.getCategory() + " is not recognized as a valid, standard category." );
            }
            ReferenceType refType = null;
            try
            {
                refType = ListedReferenceTypes.getListedReferenceTypes().getListedReferenceTypeByName( externalRef.getType() );
            }
            catch ( InvalidSPDXAnalysisException e )
            {
                throw new MojoExecutionException( "Error getting listed reference type for " + externalRef.getType(), e );
            }
            if ( refType == null )
            {
                throw new MojoExecutionException( "Listed reference type not found for " + externalRef.getType() );
            }
            try
            {
                Spdx2to3Converter.addExternalRefToArtifact( cat, refType, externalRef.getLocator(), artifact, artifact.getModelStore() );
            }
            catch ( InvalidSPDXAnalysisException e )
            {
                throw new MojoExecutionException( "Error adding external ref to artifact", e );
            }
        }
    }

    @Override
    public void collectSpdxFileInformation( List<FileSet> sources, String baseDir,
                                            SpdxDefaultFileInformation defaultFileInformation,
                                            HashMap<String, SpdxDefaultFileInformation> pathSpecificInformation,
                                            Set<String> checksumAlgorithms ) throws SpdxBuilderException
    {
        SpdxV3FileCollector fileCollector = new SpdxV3FileCollector( customIdToUri );
        try
        {
            fileCollector.collectFiles( sources, baseDir, defaultFileInformation,
                    pathSpecificInformation, projectPackage, RelationshipType.GENERATES, spdxDoc, checksumAlgorithms );
            Relationship pkgRelationship = projectPackage.createRelationship( projectPackage.getIdPrefix() + projectPackage.getModelStore().getNextId( IdType.SpdxId ) )
                                                .setFrom( projectPackage )
                                                .setRelationshipType( RelationshipType.CONTAINS )
                                                .build();
            for ( SpdxFile file : fileCollector.getFiles() )
            {
                pkgRelationship.getTos().add( file );
            }
        }
        catch ( SpdxCollectionException|InvalidSPDXAnalysisException e )
        {
            throw new SpdxBuilderException( "Error collecting SPDX file information", e );
        }
        // TODO: Set a GITOID identifier
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

    /**
     * @return the licenseManager
     */
    public SpdxV3LicenseManager getLicenseManager()
    {
        return licenseManager;
    }

    @Override
    public List<String> verify()
    {
        return spdxDoc.verify();
    }

}
