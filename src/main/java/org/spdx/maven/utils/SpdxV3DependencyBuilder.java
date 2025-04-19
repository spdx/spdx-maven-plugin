/**
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Source Auditor Inc.
 */
package org.spdx.maven.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Contributor;
import org.apache.maven.model.License;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.spdx.core.CoreModelObject;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.core.SpdxInvalidIdException;
import org.spdx.core.SpdxCoreConstants.SpdxMajorVersion;
import org.spdx.library.SpdxModelFactory;
import org.spdx.library.conversion.Spdx2to3Converter;
import org.spdx.library.model.v2.Checksum;
import org.spdx.library.model.v2.SpdxPackageVerificationCode;
import org.spdx.library.model.v2.enumerations.ChecksumAlgorithm;
import org.spdx.library.model.v2.enumerations.Purpose;
import org.spdx.library.model.v3_0_1.SpdxConstantsV3;
import org.spdx.library.model.v3_0_1.core.*;
import org.spdx.library.model.v3_0_1.expandedlicensing.NoAssertionLicense;
import org.spdx.library.model.v3_0_1.simplelicensing.AnyLicenseInfo;
import org.spdx.library.model.v3_0_1.simplelicensing.LicenseExpression;
import org.spdx.library.model.v3_0_1.software.Sbom;
import org.spdx.library.model.v3_0_1.software.SoftwarePurpose;
import org.spdx.library.model.v3_0_1.software.SpdxFile;
import org.spdx.library.model.v3_0_1.software.SpdxPackage;
import org.spdx.maven.LicenseOverwrite;
import org.spdx.maven.OutputFormat;
import org.spdx.storage.ISerializableModelStore;
import org.spdx.storage.IModelStore.IdType;
import org.spdx.storage.simple.InMemSpdxStore;
import org.spdx.v3jsonldstore.JsonLDStore;

/**
 * Adds dependency information into the spdxDoc
 * @author Gary O'Neall
 *
 */
@SuppressWarnings("LoggingSimilarMessage")
public class SpdxV3DependencyBuilder
    extends AbstractDependencyBuilder
{   
    private final SpdxDocument spdxDoc;
    private final SpdxV3LicenseManager licenseManager;
    private final Map<LicenseOverwrite, AnyLicenseInfo> licenseOverwrites = new HashMap<>();
    
    /**
     * @param builder The document builder
     * @param createExternalRefs if true, create external references for dependencies
     * @param generatePurls if true, generate a Package URL and include as an external identifier for the dependencies
     * @param useArtifactID if true, use the artifact ID for the name of the dependency package, otherwise use the Maven configured project name
     * @param includeTransitiveDependencies If true, include transitive dependencies, otherwise include only direct dependencies
     */
    public SpdxV3DependencyBuilder( SpdxV3DocumentBuilder builder, boolean createExternalRefs,
                                        boolean generatePurls, boolean useArtifactID,
                                        boolean includeTransitiveDependencies )
    {
        super( createExternalRefs, generatePurls, useArtifactID, includeTransitiveDependencies );
        this.spdxDoc = builder.getSpdxDoc();
        this.licenseManager = builder.getLicenseManager();
    }

    @Override
    protected void addMavenDependency( CoreModelObject parentPackage, DependencyNode dependencyNode, 
                                       ProjectBuilder mavenProjectBuilder,
                                       MavenSession session, MavenProject mavenProject )
         throws LicenseMapperException, InvalidSPDXAnalysisException
     {
         if ( !(parentPackage instanceof SpdxPackage) )
         {
             LOG.error( "Invalid type for parent package.  Expected 'SpdxPackage', found {}",
                     parentPackage.getClass().getName() );
             return;
         }
         Artifact dependency = dependencyNode.getArtifact();
         String scope = dependency.getScope();
         RelationshipType relType = scopeToRelationshipType( scope, dependency.isOptional() );
         if ( relType == RelationshipType.OTHER )
         {
             LOG.warn( "Could not determine the SPDX relationship type for dependency artifact ID {} scope {}", dependency.getArtifactId(), scope );
         }

         Element dependencyPackage = createSpdxPackage( dependency, mavenProjectBuilder, session, 
                                                            mavenProject, useArtifactID );
         
         spdxDoc.createLifecycleScopedRelationship(spdxDoc.getIdPrefix() +
                 IdGenerator.getIdGenerator().generateId( dependencyPackage.getId() + parentPackage.getId() + relType ) )
                   .setRelationshipType( relType )
                   .setCompleteness( RelationshipCompleteness.COMPLETE )
                   .setFrom( (SpdxPackage)parentPackage )
                   .addTo( dependencyPackage )
                   .setScope( scopeToLifecycleScope( scope ) )
                   .setComment( "Relationship created based on Maven POM information" )
                   .build();
         LOG.debug( "Added relationship of type {} for {}", relType, dependencyPackage.getName() );
         
         if ( includeTransitiveDependencies ) {
             addMavenDependencies( mavenProjectBuilder, session, mavenProject, dependencyNode, dependencyPackage );
         }
     }
    
    /**
     * Translate the scope to the SPDX relationship type
     *
     * @param scope    Maven Dependency Scope (see <a href="https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope">Maven dependency scope documentation</a>)
     * @param optional True if this is an optional dependency
     * @return SPDX Relationship type based on the scope
     */
    private RelationshipType scopeToRelationshipType( String scope, boolean optional )
    {
        if ( scope == null )
        {
            return RelationshipType.OTHER;
        }
        else if ( optional )
        {
            return RelationshipType.HAS_OPTIONAL_COMPONENT;
        }
        else if ( scope.equals( "compile" ) || scope.equals( "runtime" ) )
        {
            return RelationshipType.HAS_DYNAMIC_LINK;
        }
        else if ( scope.equals( "test" ) )
        {
            return RelationshipType.DEPENDS_ON;
        }
        else
        {
            return RelationshipType.OTHER;
        }
    }
    
    private LifecycleScopeType scopeToLifecycleScope( String scope ) {
        if ( scope == null )
        {
            return LifecycleScopeType.OTHER;
        }
        else if ( scope.equals( "compile" ) || scope.equals( "runtime" ) )
        {
            return LifecycleScopeType.RUNTIME;
        }
        else if ( scope.equals( "test" ) )
        {
            return LifecycleScopeType.TEST;
        }
        else
        {
            return LifecycleScopeType.OTHER;
        }
    }
    
    /**
     * Create an SPDX package from the information in a Maven Project
     *
     * @param project Maven project
     * @param useArtifactID If true, use ${project.groupId}:${artifactId} as the SPDX package name, otherwise, ${project.name} will be used
     * @return SPDX Package generated from the metadata in the Maven Project
     * @throws SpdxCollectionException On errors with SPDX collections
     * @throws NoSuchAlgorithmException if no checksum algorithm was found
     * @throws LicenseMapperException on errors mapping or creating SPDX custom licenses
     * @throws InvalidSPDXAnalysisException on any other general SPDX errors
     */
    private SpdxPackage createSpdxPackage( MavenProject project, boolean useArtifactID ) throws SpdxCollectionException, NoSuchAlgorithmException, LicenseMapperException, InvalidSPDXAnalysisException
    {
        SpdxDefaultFileInformation fileInfo = new SpdxDefaultFileInformation();

        // initialize the SPDX information from the project
        String packageName = project.getName();
        if ( packageName == null || packageName.isEmpty() || useArtifactID )
        {
            packageName = project.getGroupId() + ":" + project.getArtifactId();
        }
        List<Contributor> contributors = project.getContributors();
        ArrayList<String> fileContributorList = new ArrayList<>();
        if ( contributors != null )
        {
            for ( Contributor contributor : contributors )
            {
                fileContributorList.add( contributor.getName() );
            }
        }
        String copyright = "UNSPECIFIED";
        String notice = "UNSPECIFIED";
        String downloadLocation = "NOASSERTION";

        Optional<AnyLicenseInfo> declaredLicenseOverwrite = applyLicenseOverwrites( project, "declared" );
        AnyLicenseInfo originalDeclaredLicense = mavenLicensesToSpdxLicense( project.getLicenses() );
        AnyLicenseInfo declaredLicense = declaredLicenseOverwrite.orElse( originalDeclaredLicense );

        fileInfo.setComment( "" );
        Optional<AnyLicenseInfo> concludedLicenseOverwrite = applyLicenseOverwrites( project, "concluded" );
        AnyLicenseInfo originalConcludedLicense = new NoAssertionLicense();
        AnyLicenseInfo concludedLicense = concludedLicenseOverwrite.orElse( originalConcludedLicense );

        fileInfo.setConcludedLicense( concludedLicense.toString() );
        fileInfo.setContributors( fileContributorList.toArray( new String[0] ) );
        fileInfo.setCopyright( copyright );
        fileInfo.setDeclaredLicense( declaredLicense.toString() );
        
        List<String> comment = new ArrayList<>();

        if ( declaredLicenseOverwrite.isPresent() )
        {
            comment.add( "Declared license has been overwritten, original value: " + originalDeclaredLicense );
        }

        if ( concludedLicenseOverwrite.isPresent() )
        {
            comment.add( "Concluded license has been overwritten, original value: " + originalConcludedLicense );
        } 

        fileInfo.setLicenseComment( String.join( "\n", comment ) );
        fileInfo.setNotice( notice );

        SpdxPackage retval = spdxDoc.createSpdxPackage(  spdxDoc.getIdPrefix() +
                IdGenerator.getIdGenerator().generateId( project.getGroupId() + ":" +  project.getArtifactId() + ":" + project.getVersion() ) )
                        .setName( packageName )
                        .setCopyrightText( copyright )
                        .setDownloadLocation( downloadLocation )
                        .addAllExternalIdentifier( SpdxExternalIdBuilder.getDefaultExternalIdentifiers( spdxDoc, generatePurls, project ) )
                        .build();
        if ( generatePurls )
        {
            retval.setPackageUrl( SpdxExternalRefBuilder.generatePurl( project ) );
        }
        Relationship.RelationshipBuilder declaredLicenseRelationship = spdxDoc.createRelationship( spdxDoc.getIdPrefix() +
                                    IdGenerator.getIdGenerator().generateId( retval.getId() + declaredLicense.getId() + RelationshipType.HAS_DECLARED_LICENSE ) )
                    .setFrom( retval )
                    .addTo( declaredLicense )
                    .setRelationshipType( RelationshipType.HAS_DECLARED_LICENSE );

        if ( declaredLicenseOverwrite.isPresent() )
        {
            declaredLicenseRelationship.setComment( "Declared license has been overwritten, original value: " + originalDeclaredLicense );
        }
        declaredLicenseRelationship.build();

        Relationship.RelationshipBuilder concludedLicenseRelationship = spdxDoc.createRelationship( spdxDoc.getIdPrefix() +
                                    IdGenerator.getIdGenerator().generateId( retval.getId() + "NOASSERTION" + RelationshipType.HAS_CONCLUDED_LICENSE ) )
                    .setFrom( retval )
                    .addTo( concludedLicense )
                    .setRelationshipType( RelationshipType.HAS_CONCLUDED_LICENSE );

        if ( concludedLicenseOverwrite.isPresent() )
        {
                    concludedLicenseRelationship.setComment( "Concluded license has been overwritten, original value: " + originalConcludedLicense );
        }
        concludedLicenseRelationship.build();

        if ( project.getVersion() != null )
        {
            retval.setPackageVersion( project.getVersion() );
        }
        if ( project.getDescription() != null )
        {
            retval.setDescription( project.getDescription() );
            retval.setSummary( project.getDescription() );
        }
        if ( project.getOrganization() != null )
        {
            retval.getOriginatedBys().add( spdxDoc.createOrganization( spdxDoc.getIdPrefix() +
                    IdGenerator.getIdGenerator().generateId( "ORGANIZATION: " + project.getOrganization().getName() ) )
                                              .setName( project.getOrganization().getName() )
                                              .build() );
        }
        if ( project.getUrl() != null )
        {
            try {
                retval.setHomePage( project.getUrl() );
            } catch ( InvalidSPDXAnalysisException e ) {
                LOG.warn( "Invalid homepage for dependency {}: {}", project.getArtifactId(), project.getUrl() );
            }
        }
        return retval;
    }
    
    /**
     * Create an SPDX Document using the mavenProjectBuilder to resolve properties
     * including inherited properties
     * @param artifact Maven dependency artifact
     * @param mavenProjectBuilder project builder for the repo containing the POM file
     * @param session Maven session for building the project
     * @param mavenProject Maven project
     * @param useArtifactID If true, use ${project.groupId}:${artifactId} as the SPDX package name, otherwise, ${project.name} will be used
     * @return SPDX Package build from the MavenProject metadata
     * @throws InvalidSPDXAnalysisException on errors generating SPDX
     * @throws LicenseMapperException on errors mapping licenses or creating custom licenses
     */
    private Element createSpdxPackage( Artifact artifact, 
                                           ProjectBuilder mavenProjectBuilder, MavenSession session,
                                           MavenProject mavenProject, boolean useArtifactID ) throws LicenseMapperException, InvalidSPDXAnalysisException
    {
        LOG.debug( "Creating SPDX package for artifact {}", artifact.getArtifactId() );
        if ( artifact.getFile() == null )
        {
            LOG.debug( "Artifact file is null" );
        }
        else
        {
            LOG.debug( "Artifact file name = {}", artifact.getFile().getName() );
        }
        File spdxFile = null;
        if ( artifact.getFile() != null )
        {
            spdxFile = artifactFileToSpdxFile( artifact.getFile(), SpdxMajorVersion.VERSION_3 );
        }
        Element retval = null;
        if ( spdxFile != null && spdxFile.exists() )
        {
            LOG.debug( "Dependency {}Looking for SPDX file {}", artifact.getArtifactId(), spdxFile.getAbsolutePath() );
            try
            {
                LOG.debug( "Dependency {}Dependency information collected from SPDX spec version 3 file {}", artifact.getArtifactId(), spdxFile.getAbsolutePath() );
                
                SpdxDocument externalSpdxDoc = spdxDocumentFromFile( spdxFile.getPath() );
                if ( createExternalRefs )
                {
                    retval = createExternalSpdxPackage( externalSpdxDoc, spdxFile, artifact.getGroupId(), 
                                                      artifact.getArtifactId(), artifact.getVersion() );
                } 
                else
                {
                    retval = copyPackageInfoFromExternalDoc( externalSpdxDoc, artifact.getArtifactId() );
                }
            }
            catch ( IOException e )
            {
                LOG.warn( "IO error reading SPDX document for dependency artifact ID {}:{}.  Using POM file information for creating SPDX package data.", artifact.getArtifactId(), e.getMessage() );
            }
            catch ( SpdxInvalidIdException e ) 
            {
                LOG.warn( "Invalid SPDX ID exception reading SPDX document for dependency artifact ID {}:{}.  Using POM file information for creating SPDX package data.", artifact.getArtifactId(), e.getMessage() );
            }
            catch ( InvalidSPDXAnalysisException e )
            {
                LOG.warn( "Invalid SPDX analysis exception reading SPDX document for dependency artifact ID {}:{}.  Using POM file information for creating SPDX package data.", artifact.getArtifactId(), e.getMessage() );
            }
            catch ( SpdxCollectionException e )
            {
                LOG.warn( "Unable to create file checksum for external SPDX document for dependency artifact ID {}:{}.  Using POM file information for creating SPDX package data.", artifact.getArtifactId(), e.getMessage() );
            }
            catch ( Exception e )
            {
                LOG.warn( "Unknown error processing SPDX document for dependency artifact ID {}:{}.  Using POM file information for creating SPDX package data.", artifact.getArtifactId(), e.getMessage() );
            }
        }
        if ( retval != null )
        {
            return retval;
        }
        // Check for an SPDX spec version 2 file
        spdxFile = artifactFileToSpdxFile( artifact.getFile(), SpdxMajorVersion.VERSION_2 );
        if ( spdxFile != null && spdxFile.exists() )
        {
            LOG.debug( "Dependency {}Looking for SPDX spec version 2 file {}", artifact.getArtifactId(), spdxFile.getAbsolutePath() );
            try
            {
                LOG.debug( "Dependency {}Dependency information collected from SPDX spec version 2 file {}", artifact.getArtifactId(), spdxFile.getAbsolutePath() );
                
                retval = copyPackageInfoFromV2File( spdxFile.getPath(), artifact.getArtifactId() );
            }
            catch ( IOException e )
            {
                LOG.warn( "IO error reading SPDX document for dependency artifact ID {}:{}.  Using POM file information for creating SPDX package data.", artifact.getArtifactId(), e.getMessage() );
            }
            catch ( SpdxInvalidIdException e ) 
            {
                LOG.warn( "Invalid SPDX ID exception reading SPDX document for dependency artifact ID {}:{}.  Using POM file information for creating SPDX package data.", artifact.getArtifactId(), e.getMessage() );
            }
            catch ( InvalidSPDXAnalysisException e )
            {
                LOG.warn( "Invalid SPDX analysis exception reading SPDX document for dependency artifact ID {}:{}.  Using POM file information for creating SPDX package data.", artifact.getArtifactId(), e.getMessage() );
            }
            catch ( Exception e )
            {
                LOG.warn( "Unknown error processing SPDX document for dependency artifact ID {}:{}.  Using POM file information for creating SPDX package data.", artifact.getArtifactId(), e.getMessage() );
            }
        }
        if ( retval != null )
        {
            return retval;
        }
        try
        {
            ProjectBuildingRequest request = new DefaultProjectBuildingRequest( session.getProjectBuildingRequest() );
            request.setProcessPlugins( false );
            request.setRemoteRepositories( mavenProject.getRemoteArtifactRepositories() );
            for ( ArtifactRepository ar : request.getRemoteRepositories() ) {
                LOG.debug( "request Remote repository ID: {}", ar.getId() );
            }
            for ( ArtifactRepository ar : mavenProject.getRemoteArtifactRepositories() ) {
                LOG.debug( "Project Remote repository ID: {}", ar.getId() );
            }
            ProjectBuildingResult build = mavenProjectBuilder.build( artifact, request );
            MavenProject depProject = build.getProject();
            LOG.debug( "Dependency {}Collecting information from project metadata for {}", artifact.getArtifactId(), depProject.getArtifactId() );
            retval = createSpdxPackage( depProject, useArtifactID );
        }
        catch ( SpdxCollectionException e )
        {
            LOG.error( "SPDX File Collection Error creating SPDX package for dependency artifact ID {}:{}", artifact.getArtifactId(), e.getMessage() );
        }
        catch ( NoSuchAlgorithmException e )
        {
            LOG.error( "Verification Code Error creating SPDX package for dependency artifact ID {}:{}", artifact.getArtifactId(), e.getMessage() );
        }
        catch ( ProjectBuildingException e )
        {
            LOG.error( "Maven Project Build Error creating SPDX package for dependency artifact ID {}:{}", artifact.getArtifactId(), e.getMessage() );
        }
        if ( retval != null )
        {
            return retval;
        }
        LOG.warn( "Error creating SPDX package for dependency artifact ID {}.  A minimal SPDX package will be created.", artifact.getArtifactId() );
        // Create a minimal SPDX package from dependency
        // Name will be the artifact ID
        LOG.debug( "Dependency {}Using only artifact information to create dependent package", artifact.getArtifactId() );
        SpdxPackage pkg = spdxDoc.createSpdxPackage( spdxDoc.getIdPrefix() +
                        IdGenerator.getIdGenerator().generateId( artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion() ) )
                        .setName( artifact.getArtifactId() )
                        .setComment( "This package was created for a Maven dependency.  No SPDX or license information could be found in the Maven POM file." )
                        .setPackageVersion( artifact.getBaseVersion() )
                        .addAllExternalIdentifier( SpdxExternalIdBuilder
                                                   .getDefaultExternalIdentifiers( spdxDoc, generatePurls, 
                                                                                     mavenProject ) )
                        .build();
        spdxDoc.createRelationship( spdxDoc.getIdPrefix() + 
                                    IdGenerator.getIdGenerator().generateId( pkg.getId() + "NOASSERTION" + RelationshipType.HAS_DECLARED_LICENSE ) )
                    .setFrom( pkg )
                    .addTo( new NoAssertionLicense() )
                    .setRelationshipType( RelationshipType.HAS_DECLARED_LICENSE )
                    .build();
        spdxDoc.createRelationship( spdxDoc.getIdPrefix() +
                        IdGenerator.getIdGenerator().generateId( pkg.getId() + "NOASSERTION" + RelationshipType.HAS_CONCLUDED_LICENSE ) )
                    .setFrom( pkg )
                    .addTo( new NoAssertionLicense() )
                    .setRelationshipType( RelationshipType.HAS_CONCLUDED_LICENSE )
                    .build();
        return pkg;
    }
    
    /**
     * Creates a copy from an SPDX version 2 file
     * @param path        Path to the SPDX spec version 2 file
     * @param artifactId  Maven artifact ID for the file
     * @return SPDX V3 compliant element for the SPDX package represented by the artifactId in the SPDX file
     * @throws InvalidSPDXAnalysisException on SPDX parsing errors
     * @throws IOException on errors reading from the SPDX V2 document file
     * @throws FileNotFoundException on the SPDX V2 document file not being found
     */
    private Element copyPackageInfoFromV2File( String path, String artifactId ) throws FileNotFoundException, IOException, InvalidSPDXAnalysisException
    {
        org.spdx.library.model.v2.SpdxDocument v2Doc = SpdxV2DependencyBuilder.spdxDocumentFromFile( path );
        org.spdx.library.model.v2.SpdxPackage source = SpdxV2DependencyBuilder.findMatchingDescribedPackage( v2Doc, artifactId );
        
        Optional<String> downloadLocation = source.getDownloadLocation();
        Optional<String> name = source.getName();
        
        SpdxPackage dest = spdxDoc.createSpdxPackage( spdxDoc.getIdPrefix() +
                        IdGenerator.getIdGenerator().generateId( path + artifactId ) )
                        .setName(name.orElse( "NONE" ))
                        .setCopyrightText( source.getCopyrightText() != null ? source.getCopyrightText() : "NOASSERTION" )
                        .setDownloadLocation(downloadLocation.orElse( "NOASSERTION" ))
                        .build();
        
        Optional<SpdxPackageVerificationCode> pvc = source.getPackageVerificationCode();
        if ( pvc.isPresent() )
        {
            dest.getVerifiedUsings().add( dest.createPackageVerificationCode( dest.getModelStore().getNextId( IdType.Anonymous ) )
                                          .setAlgorithm( HashAlgorithm.SHA1 )
                                          .setHashValue( pvc.get().getValue() )
                                          .addAllPackageVerificationCodeExcludedFile( pvc.get().getExcludedFileNames() )
                                          .build());
        }
        
        for ( org.spdx.library.model.v2.ExternalRef fromRef : source.getExternalRefs() )
        {
            Spdx2to3Converter.addExternalRefToArtifact( fromRef, dest, dest.getModelStore() );
        }
        for ( org.spdx.library.model.v2.Annotation fromAnnotation : source.getAnnotations() )
        {
            CreationInfo creationInfo = new CreationInfo.CreationInfoBuilder( dest.getModelStore(), 
                                                                              dest.getModelStore().getNextId(IdType.Anonymous), 
                                                                              null )
                            .setCreated( fromAnnotation.getAnnotationDate() )
                            .setSpecVersion( SpdxConstantsV3.MODEL_SPEC_VERSION )
                            .build();
            creationInfo.getCreatedBys().add( Spdx2to3Converter.stringToAgent( fromAnnotation.getAnnotator(), creationInfo ) );
            AnnotationType annotationType = Spdx2to3Converter.ANNOTATION_TYPE_MAP.get( fromAnnotation.getAnnotationType() );
            dest.createAnnotation( dest.getIdPrefix() +
                            dest.getId() + annotationType + fromAnnotation.getComment() )
                .setAnnotationType( annotationType )
                .setStatement( fromAnnotation.getComment() )
                .setSubject( dest )
                .setCreationInfo( creationInfo )
                .build();
        }
        org.spdx.library.model.v2.license.AnyLicenseInfo v2Declared = source.getLicenseDeclared();
        String declaredExpression = v2Declared == null ? "NOASSERTION" : v2Declared.toString();
        LicenseExpression declaredLicense = dest.createLicenseExpression( dest.getIdPrefix() +
                        IdGenerator.getIdGenerator().generateId( path + artifactId + "DECLARED" + declaredExpression ) )
                        .setLicenseExpression( declaredExpression )
                        .build();
        Optional<String> licenseListVersion = v2Doc.getCreationInfo() == null ? Optional.empty() :
                v2Doc.getCreationInfo().getLicenseListVersion();
        if ( licenseListVersion.isPresent() )
        {
            declaredLicense.setLicenseListVersion( licenseListVersion.get() );
        }
        dest.createRelationship( dest.getIdPrefix() +
                        IdGenerator.getIdGenerator().generateId( dest.getId() + declaredLicense.getId() + RelationshipType.HAS_DECLARED_LICENSE ) )
                        .setRelationshipType( RelationshipType.HAS_DECLARED_LICENSE )
                        .setFrom( dest )
                        .addTo( declaredLicense )
                        .build();

        String concludedExpression = source.getLicenseConcluded().toString();
        LicenseExpression concludedLicense = dest.createLicenseExpression( dest.getIdPrefix() +
                        IdGenerator.getIdGenerator().generateId( path + artifactId + "DECLARED" + concludedExpression ) )
                        .setLicenseExpression( concludedExpression )
                        .build();
        if ( licenseListVersion.isPresent() )
        {
            concludedLicense.setLicenseListVersion( licenseListVersion.get() );
        }
        dest.createRelationship( dest.getIdPrefix() +
                        IdGenerator.getIdGenerator().generateId( dest.getId() + concludedLicense.getId() + RelationshipType.HAS_CONCLUDED_LICENSE ) )
                        .setRelationshipType( RelationshipType.HAS_CONCLUDED_LICENSE )
                        .setFrom( dest )
                        .addTo( concludedLicense )
                        .build();
        Optional<String> builtDate = source.getBuiltDate();
        
        if ( builtDate.isPresent() )
        {
            dest.setBuiltTime( builtDate.get() );
        }
        Optional<String> comment = source.getComment();
        Optional<String> licenseComments = source.getLicenseComments();
        if ( comment.isPresent() )
        {
            if ( licenseComments.isPresent() )
            {
                dest.setComment( comment.get() + "; License Comments: " + licenseComments.get() );
            }
            else
            {
                dest.setComment( comment.get() );
            }
            
        }
        else if ( licenseComments.isPresent() )
        {
            dest.setComment( "License Comments: " + licenseComments.get() );
        }
        Optional<String> desc = source.getDescription();
        if ( desc.isPresent() )
        {
            dest.setDescription( desc.get() );
        }
        Optional<String> homePage = source.getHomepage();
        if ( homePage.isPresent() )
        {
            dest.setHomePage( homePage.get() );
        }
        Optional<String> originator = source.getOriginator();
        if ( originator.isPresent() )
        {
            // we know the creationInfo is not null since it is copied from the SPDX package when initially created
            //noinspection DataFlowIssue
            dest.getOriginatedBys().add( Spdx2to3Converter.stringToAgent( originator.get(),
                    dest.getCreationInfo() ) );
        }
        Optional<String> pkgFileName = source.getPackageFileName();
        if ( pkgFileName.isPresent() )
        {
            SpdxFile packageFile = dest.createSpdxFile( dest.getIdPrefix() +
                            IdGenerator.getIdGenerator().generateId( path + artifactId + pkgFileName.get() ) )
                            .setName( pkgFileName.get() )
                            .build();
            for ( Checksum fromChecksum : source.getChecksums() )
            {
                packageFile.getVerifiedUsings().add( dest.createHash( dest.getModelStore().getNextId( IdType.Anonymous ) )
                                                     .setAlgorithm( Spdx2to3Converter.HASH_ALGORITH_MAP.get( fromChecksum.getAlgorithm() ) )
                                                     .setHashValue( fromChecksum.getValue() )
                                                     .build() );
            }
            dest.createRelationship( dest.getIdPrefix() +
                            IdGenerator.getIdGenerator().generateId( dest.getId() + packageFile.getId() + RelationshipType.HAS_DISTRIBUTION_ARTIFACT ) )
                        .setFrom( dest )
                        .addTo( packageFile )
                        .setRelationshipType( RelationshipType.HAS_DISTRIBUTION_ARTIFACT )
                        .setCompleteness( RelationshipCompleteness.COMPLETE )
                        .build();
        }
        Optional<Purpose> primaryPurpose = source.getPrimaryPurpose();
        if ( primaryPurpose.isPresent() )
        {
            dest.setPrimaryPurpose( Spdx2to3Converter.PURPOSE_MAP.get( primaryPurpose.get() ) );
        }
        Optional<String> releaseDate = source.getReleaseDate();
        if ( releaseDate.isPresent() )
        {
            dest.setReleaseTime( releaseDate.get() );
        }
        Optional<String> sourceInfo = source.getSourceInfo();
        if ( sourceInfo.isPresent() )
        {
            dest.setSourceInfo( sourceInfo.get() );
        }
        Optional<String> summary = source.getSummary();
        if ( summary.isPresent() )
        {
            dest.setSummary( summary.get() );
        }
        Optional<String> supplier = source.getSupplier();
        if ( supplier.isPresent() ) {
            // we know the creationInfo is not null since it is copied from the SPDX package when initially created
            //noinspection DataFlowIssue
            dest.setSuppliedBy( Spdx2to3Converter.stringToAgent( supplier.get(), dest.getCreationInfo() ) );
        }
        Optional<String> validUntil = source.getValidUntilDate();
        if ( validUntil.isPresent() )
        {
            dest.setValidUntilTime( validUntil.get() );
        }
        Optional<String> versionInfo = source.getVersionInfo();
        if ( versionInfo.isPresent() )
        {
            dest.setPackageVersion( versionInfo.get() );
        }
        return dest;
    }

    /**
     * Create and return an external element for the root document or root of an SBOM
     *
     * @param externalSpdxDoc       SPDX Document containing the package to be referenced.
     * @param spdxFile              SPDX file containing the SPDX document
     * @param groupId               Group ID for the external artifact
     * @param artifactId            Artifact ID for the external artifact
     * @param version               version for the external artifact
     * @return                      package described in the externalSpdxDoc, otherwise null if no package found
     * @throws InvalidSPDXAnalysisException on errors creating the external element
     * @throws SpdxCollectionException      on errors creating the SHA1 has for the file
     */
    private @Nullable ExternalElement createExternalSpdxPackage( SpdxDocument externalSpdxDoc, 
                                                                 File spdxFile, 
                                                                 String groupId,
                                                                 String artifactId,
                                                                 @Nullable String version ) throws InvalidSPDXAnalysisException, SpdxCollectionException
    {
        SpdxPackage describedPackage = null;
        for ( Element root : externalSpdxDoc.getRootElements() )
        {
            if ( root instanceof SpdxPackage ) 
            {
                describedPackage = (SpdxPackage)root;
                break;
            }
            else if ( root instanceof Sbom )
            {
                for ( Element sbomRoot : ((Sbom)root).getRootElements() )
                {
                    if ( sbomRoot instanceof SpdxPackage )
                    {
                        describedPackage = (SpdxPackage)sbomRoot;
                        break;
                    }
                }
                if ( describedPackage != null )
                {
                    break;
                }
            }
        }
        if ( describedPackage == null )
        {
            // not found
            return null;
        }
        
        ExternalElement retval = new ExternalElement(spdxDoc.getModelStore(), describedPackage.getObjectUri(),
                                                     spdxDoc.getCopyManager(), true, describedPackage.getIdPrefix());
        for ( ExternalMap ext : spdxDoc.getSpdxImports() )
        {
            if ( describedPackage.getObjectUri().equals( ext.getExternalSpdxId() ) )
            {
                return retval; // No need to create the external map
            }
        }
        org.spdx.maven.Checksum checksum = AbstractFileCollector.generateSha1( spdxFile );
        final HashAlgorithm algorithm = Spdx2to3Converter.HASH_ALGORITH_MAP.get( ChecksumAlgorithm.valueOf( checksum.getAlgorithm() ) );
        Hash hash = spdxDoc.createHash( spdxDoc.getModelStore().getNextId( IdType.Anonymous ) )
                        .setAlgorithm( algorithm )
                        .setHashValue( checksum.getValue() )
                        .build();
                        
        StringBuilder sb = new StringBuilder( groupId ).append( artifactId );
        if ( Objects.nonNull( version )) {
            sb.append( version );
        }
        String fullArtifactId = sb.toString();
        SpdxFile fileArtifact = spdxDoc.createSpdxFile( spdxDoc.getIdPrefix() +
                        IdGenerator.getIdGenerator().generateId( fullArtifactId ) )
                        .setName( spdxFile.getName() )
                        .setDescription( String.format( "SPDX File for %s", fullArtifactId ) )
                        .addVerifiedUsing( hash )
                        .build();
        spdxDoc.getSpdxImports().add( spdxDoc.createExternalMap( spdxDoc.getModelStore().getNextId( IdType.Anonymous ) )
                                      .addVerifiedUsing( hash )
                                      .setExternalSpdxId( describedPackage.getObjectUri() )
                                      .setDefiningArtifact( fileArtifact )
                                      .build() );
        return retval;
    }
    
    
    /**
     * Creates an SPDX document from a file
     * @param path Path to the SPDX file
     * @return an SPDX Spec version 2 document
     * @throws IOException on IO Error
     * @throws FileNotFoundException if the file does not exist
     * @throws InvalidSPDXAnalysisException on invalid SPDX file
     */
    private SpdxDocument spdxDocumentFromFile( String path ) throws FileNotFoundException, IOException, InvalidSPDXAnalysisException
    {
        ISerializableModelStore modelStore;
        OutputFormat of = OutputFormat.getOutputFormat( null, new File( path ) );
        if (!SpdxMajorVersion.VERSION_3.equals( of.getSpecVersion() )) {
            throw new InvalidSPDXAnalysisException( String.format( "Unsupported file type for SPDX Version 2 SPDX documents: %s", of.getSpecVersion().toString() ));
        }
        modelStore = new JsonLDStore( new InMemSpdxStore() );
        
        try ( InputStream inputStream = new FileInputStream( path ) ) 
        {
            CoreModelObject root = modelStore.deSerialize( inputStream, false );
            if ( root != null )
            {
                root.setCopyManager( spdxDoc.getCopyManager() );
                return (SpdxDocument)root;
            }
            else
            {
                throw new InvalidSPDXAnalysisException( String.format( "Could not find an SPDX document for SPDX file name %s", 
                                                                       path ) );
            }
        } 
        finally
        {
            try {
                modelStore.close();
            } catch (Exception e) {
                LOG.error( "Error closing SPDX model store", e );
            }
        }
    }
    
    /**
     * Copies the closest matching described package in the externalSpdxDoc to the returned element
     * @param externalSpdxDoc                SPDX document containing the described package
     * @param artifactId                     Artifact ID to search for
     * @return                               SPDX Package with values copied from the externalSpdxDoc
     * @throws InvalidSPDXAnalysisException  on errors copying from the external document
     */
    private SpdxPackage copyPackageInfoFromExternalDoc( SpdxDocument externalSpdxDoc,  String artifactId ) throws InvalidSPDXAnalysisException
    {
        SpdxPackage source = findMatchingDescribedPackage( externalSpdxDoc, artifactId );
        Optional<String> downloadLocation = source.getDownloadLocation();
        Optional<String> name = source.getName();
        SpdxPackage dest = spdxDoc.createSpdxPackage( spdxDoc.getIdPrefix() +
                        IdGenerator.getIdGenerator().generateId( externalSpdxDoc.getId() + artifactId ) )
                        .setName( name.orElse( "NONE" ) )
                        .setCopyrightText( source.getCopyrightText().orElse( "NOASSERTION" ) )
                        .addAllVerifiedUsing( source.getVerifiedUsings() )
                        .setDownloadLocation( downloadLocation.orElse( "NOASSERTION" ) )
                        .addAllExternalIdentifier( source.getExternalIdentifiers() )
                        .addAllExternalRef( source.getExternalRefs() )
                        .addAllOriginatedBy( source.getOriginatedBys() )
                        .build();
        @SuppressWarnings( "unchecked" )
        List<Relationship> sourceRelationships = 
                        (List<Relationship>) SpdxModelFactory.getSpdxObjects( externalSpdxDoc.getModelStore(), externalSpdxDoc.getCopyManager(), 
                         SpdxConstantsV3.CORE_RELATIONSHIP, null, null )
                                .filter( spdxObj -> {
                                    try
                                    {
                                        return source.equals( ((Relationship)spdxObj).getFrom() );
                                    }
                                    catch ( InvalidSPDXAnalysisException e )
                                    {
                                        LOG.error( "Error copying relationships from SPDX file for artifact {}", artifactId, e );
                                        return false;
                                    }
                                } )
                                .collect( Collectors.toList() );
        for ( Relationship rel : sourceRelationships )
        {
            dest.createRelationship( dest.getIdPrefix() +
                            IdGenerator.getIdGenerator().generateId( dest.getId() + rel.getId() ) )
                                        .setFrom( dest )
                                        .setCompleteness( rel.getCompleteness().orElse( RelationshipCompleteness.NO_ASSERTION ) )
                                        .setRelationshipType( rel.getRelationshipType() )
                                        .addAllTo( rel.getTos() ) // NOTE: The dest my have the same CopyManager as the relationships for this to copy correctly
                                        .build();
        }
        // We don't want to copy any of the properties which have other elements since it
        // may duplicate artifacts already included in the document - so we can't use copyFrom

        Optional<String> builtTime = source.getBuiltTime();
        if ( builtTime.isPresent() )
        {
            dest.setBuiltTime( builtTime.get() );
        }
        Optional<String> comment = source.getComment();
        if ( comment.isPresent() )
        {
            dest.setComment( comment.get() );
        }
        Optional<String> desc = source.getDescription();
        if ( desc.isPresent() )
        {
            dest.setDescription( desc.get() );
        }
        Optional<String> homePage = source.getHomePage();
        if ( homePage.isPresent() )
        {
            dest.setHomePage( homePage.get() );
        }
        Optional<SoftwarePurpose> primaryPurpose = source.getPrimaryPurpose();
        if ( primaryPurpose.isPresent() )
        {
            dest.setPrimaryPurpose( primaryPurpose.get() );
        }
        Optional<String> releaseTime = source.getReleaseTime();
        if ( releaseTime.isPresent() )
        {
            dest.setReleaseTime( releaseTime.get() );
        }
        Optional<String> sourceInfo = source.getSourceInfo();
        if ( sourceInfo.isPresent() )
        {
            dest.setSourceInfo( sourceInfo.get() );
        }
        Optional<String> summary = source.getSummary();
        if ( summary.isPresent() )
        {
            dest.setSummary( summary.get() );
        }
        Optional<Agent> supplier = source.getSuppliedBy();
        if ( supplier.isPresent() ) {
            dest.setSuppliedBy( supplier.get() );
        }
        Optional<String> validUntil = source.getValidUntilTime();
        if ( validUntil.isPresent() )
        {
            dest.setValidUntilTime( validUntil.get() );
        }
        Optional<String> versionInfo = source.getPackageVersion();
        if ( versionInfo.isPresent() )
        {
            dest.setPackageVersion( versionInfo.get() );
        }
        return dest;
    }
    
    /**
     * Searched the SPDX document for the closest matching package to the artifactId
     * @param externalSpdxDoc Doc containing the package
     * @param artifactId Maven artifact ID
     * @return the closest matching package described by the doc 
     * @throws InvalidSPDXAnalysisException on SPDX errors
     */
    private SpdxPackage findMatchingDescribedPackage( SpdxDocument externalSpdxDoc, String artifactId ) throws InvalidSPDXAnalysisException
    {
        Sbom firstFoundSbom = null;
        SpdxPackage firstFoundPackage = null;
        for ( Element root : externalSpdxDoc.getRootElements() )
        {
            if ( root instanceof SpdxPackage )
            {
                if ( root.getName().isPresent() && root.getName().get().equals( artifactId ) )
                {
                    return (SpdxPackage)root;
                } else if ( firstFoundPackage == null )
                {
                    firstFoundPackage =(SpdxPackage)root;
                }
            }
            else if ( root instanceof Sbom )
            {
                for ( Element sRoot : ((Sbom)root).getRootElements() )
                {
                    if ( sRoot instanceof SpdxPackage )
                    {
                        if ( sRoot.getName().isPresent() && sRoot.getName().get().equals( artifactId ) )
                        {
                            return (SpdxPackage)sRoot;
                        }
                    }
                }
                if ( firstFoundSbom == null )
                {
                    firstFoundSbom = (Sbom)root;
                }
            }
        }
        
        // If we got here, we didn't find the package in the SPDX document root or the SBOMs at the root of the SPDX document
        if ( firstFoundPackage != null )
        {
            LOG.warn( "Could not find matching artifact ID in SPDX file for {}.  Using the first package found in SPDX file.", artifactId );
            return firstFoundPackage;
        }
        if ( firstFoundSbom != null )
        {
            for ( Element sRoot : firstFoundSbom.getRootElements() )
            {
                if ( sRoot instanceof SpdxPackage )
                {
                    LOG.warn( "Could not find matching artifact ID in SPDX file for {}.  Using the first package found in Sbom.", artifactId );
                    return (SpdxPackage)sRoot;
                }
            }
        }
        throw new InvalidSPDXAnalysisException( "SPDX document does not contain any described items." );
    }

    /**
     * Convert a list of Maven licenses to an SPDX License
     *
     * @param mavenLicenses List of maven licenses to map
     * @return SPDX license represented by the maven license
     * @throws LicenseMapperException thrown if no SPDX listed or extracted license exists with the same URL
     * @throws InvalidSPDXAnalysisException on SPDX parsing errors
     */
    private AnyLicenseInfo mavenLicensesToSpdxLicense( List<License> mavenLicenses ) throws LicenseMapperException, InvalidSPDXAnalysisException
    {
        try
        {
            // The call below will map non-standard licenses as well as standard licenses
            // but will throw an exception if no mapping is found - we'll try this first
            // and if there is an error, try just the standard license mapper which will
            // return an UNSPECIFIED license type if there is no mapping
            return this.licenseManager.mavenLicenseListToSpdxLicense( mavenLicenses );
        }
        catch ( LicenseManagerException ex )
        {
            return MavenToSpdxLicenseMapper.getInstance().mavenLicenseListToSpdxV3License( mavenLicenses, spdxDoc );
        }

    }

    /**
     * @param licenseOverwrite the configured license overwrite
     * @param parsedLicense the the parsed licenseString of the licenseOverwrite
     */
    public void addLicenseOverwrite( LicenseOverwrite licenseOverwrite, AnyLicenseInfo parsedLicense )
    {
        this.licenseOverwrites.put( licenseOverwrite, parsedLicense );
    }

    Optional<AnyLicenseInfo> applyLicenseOverwrites( MavenProject mavenProject, String target )
    {
        return licenseOverwrites.entrySet().stream()
                .filter( (overwrite) -> overwrite.getKey().appliesTo( mavenProject, target ) )
                .reduce( (a, b) -> {
            throw new IllegalStateException( "multiple matching license overwrites: " + a.getKey() + " vs. " + b.getKey() );
        } ).map( Map.Entry::getValue );
    }
}
