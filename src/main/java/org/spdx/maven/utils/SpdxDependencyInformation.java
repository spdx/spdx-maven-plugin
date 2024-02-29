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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Contributor;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.Resource;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.model.fileset.FileSet;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.spdx.jacksonstore.MultiFormatStore;
import org.spdx.jacksonstore.MultiFormatStore.Format;
import org.spdx.jacksonstore.MultiFormatStore.Verbose;
import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.SpdxConstants;
import org.spdx.library.SpdxInvalidIdException;
import org.spdx.library.model.Checksum;
import org.spdx.library.model.ExternalDocumentRef;
import org.spdx.library.model.ExternalSpdxElement;
import org.spdx.library.model.Relationship;
import org.spdx.library.model.SpdxDocument;
import org.spdx.library.model.SpdxElement;
import org.spdx.library.model.SpdxPackage;
import org.spdx.library.model.enumerations.AnnotationType;
import org.spdx.library.model.enumerations.ChecksumAlgorithm;
import org.spdx.library.model.enumerations.Purpose;
import org.spdx.library.model.enumerations.RelationshipType;
import org.spdx.library.model.license.AnyLicenseInfo;
import org.spdx.library.model.license.SpdxNoAssertionLicense;

import org.spdx.maven.CreateSpdxMojo;
import org.spdx.spdxRdfStore.RdfStore;
import org.spdx.storage.IModelStore.IdType;
import org.spdx.storage.ISerializableModelStore;
import org.spdx.storage.simple.InMemSpdxStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contains information about package dependencies collected from the Maven dependencies.
 *
 * @author Gary O'Neall
 */
public class SpdxDependencyInformation
{
    private static final Logger LOG = LoggerFactory.getLogger( SpdxDependencyInformation.class );

    /**
     * List of all Relationships added for dependances To a related element
     */
    private Map<SpdxElement, List<Relationship>> relationships = new HashMap<>();
    
    /**
     * Map of namespaces to ExternalDocumentRefs
     */
    private Map<String, ExternalDocumentRef> externalDocuments = new HashMap<>();
    private List<org.spdx.library.model.Annotation> documentAnnotations = new ArrayList<>();
    private LicenseManager licenseManager;
    private SpdxDocument spdxDoc;
    private boolean createExternalRefs = false;
    private boolean generatePurls = false;
    private boolean useArtifactID = false;
    private boolean includeTransitiveDependencies = false;
    DateFormat format = new SimpleDateFormat( SpdxConstants.SPDX_DATE_FORMAT );

    /**
     */
    public SpdxDependencyInformation( LicenseManager licenseManager, 
                                      SpdxDocument spdxDoc, boolean createExternalRefs, boolean generatePurls, boolean useArtifactID,
                                      boolean includeTransitiveDependencies )
    {
        this.licenseManager = licenseManager;
        this.spdxDoc = spdxDoc;
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
     * @param mavenProject Maven project
     */
    public void addMavenDependencies( ProjectBuilder mavenProjectBuilder, MavenSession session, MavenProject mavenProject,
        DependencyNode node, SpdxElement pkg ) throws LicenseMapperException, InvalidSPDXAnalysisException
    {
        List<DependencyNode> children = node.getChildren();

        logDependencies( children );

        for ( DependencyNode childNode : children )
        {
            addMavenDependency( pkg, childNode, mavenProjectBuilder, session, mavenProject );
        }
    }

    private void addMavenDependency( SpdxElement parentPackage, DependencyNode dependencyNode, ProjectBuilder mavenProjectBuilder,
                                    MavenSession session, MavenProject mavenProject )
        throws LicenseMapperException, InvalidSPDXAnalysisException
    {
        Artifact dependency = dependencyNode.getArtifact();
        String scope = dependency.getScope();
        RelationshipType relType = scopeToRelationshipType( scope, dependency.isOptional() );
        if ( relType == RelationshipType.OTHER )
        {
            LOG.warn(
                    "Could not determine the SPDX relationship type for dependency artifact ID " + dependency.getArtifactId() + " scope " + scope );
        }

        SpdxElement dependencyPackage = createSpdxPackage( dependency, mavenProjectBuilder, session, mavenProject, useArtifactID );

        if ( relType.toString().endsWith( "_OF" ) )
        {
            if ( dependencyPackage instanceof SpdxPackage )
            {
                this.relationships.computeIfAbsent( parentPackage, key -> new ArrayList<>() )
                    .add( spdxDoc.createRelationship( dependencyPackage, relType,
                        "Relationship created based on Maven POM information" ) );
                LOG.debug( "Added relationship of type " + relType + " for " + dependencyPackage.getName() );
            }
            else
            {
                this.relationships.computeIfAbsent( dependencyPackage, key -> new ArrayList<>() )
                    .add( spdxDoc.createRelationship( parentPackage, RelationshipType.OTHER,
                        "This relationship is the inverse of " + relType + " to an external document reference." ) );
                LOG.debug( "Could not create proper to relationships for external element " + dependencyPackage.getId() );
            }
        } 
        else
        {
            this.relationships.computeIfAbsent( parentPackage, key -> new ArrayList<>() )
                .add( spdxDoc.createRelationship( dependencyPackage, relType,
                    "Relationship based on Maven POM file dependency information" ) );
        }

        if ( includeTransitiveDependencies ) {
            addMavenDependencies( mavenProjectBuilder, session, mavenProject, dependencyNode, dependencyPackage );
        }
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
            LOG.debug(
                "ArtifactId: " + dependency.getArtifactId() + ", file path: " + filePath + ", Scope: " + scope );
        }
    }

    /**
     * Translate the scope to the SPDX relationship type
     *
     * @param scope    Maven Dependency Scope (see https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope)
     * @param optional True if this is an optional dependency
     * @return
     */
    private RelationshipType scopeToRelationshipType( String scope, boolean optional )
    {
        if ( scope == null )
        {
            return RelationshipType.OTHER;
        }
        else if ( optional )
        {
            return RelationshipType.OPTIONAL_COMPONENT_OF;
        }
        else if ( scope.equals( "compile" ) || scope.equals( "runtime" ) )
        {
            return RelationshipType.DYNAMIC_LINK;
        }
        else if ( scope.equals( "test" ) )
        {
            return RelationshipType.TEST_DEPENDENCY_OF;
        }
        else
        {
            return RelationshipType.OTHER;
        }
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
     * @throws LicenseMapperException
     * @throws InvalidSPDXAnalysisException 
     */
    private SpdxElement createSpdxPackage( Artifact artifact, 
                                           ProjectBuilder mavenProjectBuilder, MavenSession session,
                                           MavenProject mavenProject, boolean useArtifactID ) throws LicenseMapperException, InvalidSPDXAnalysisException
    {
        LOG.debug( "Creating SPDX package for artifact " + artifact.getArtifactId() );
        if ( artifact.getFile() == null )
        {
            LOG.debug( "Artifact file is null" );
        }
        else
        {
            LOG.debug( "Artifact file name = " + artifact.getFile().getName() );
        }
        File spdxFile = null;
        if ( artifact.getFile() != null )
        {
            spdxFile = artifactFileToSpdxFile( artifact.getFile() );
        }
        if ( spdxFile != null && spdxFile.exists() )
        {
            LOG.debug(
                    "Dependency " + artifact.getArtifactId() + "Looking for SPDX file " + spdxFile.getAbsolutePath() );
            try
            {
                LOG.debug(
                        "Dependency " + artifact.getArtifactId() + "Dependency information collected from SPDX file " + spdxFile.getAbsolutePath() );
                
                SpdxDocument externalSpdxDoc = spdxDocumentFromFile( spdxFile.getPath() );
                if ( createExternalRefs )
                {
                    return createExternalSpdxPackageReference( externalSpdxDoc, spdxFile, artifact.getGroupId(), 
                                                               artifact.getArtifactId(), artifact.getVersion() );
                } 
                else
                {
                    return copyPackageInfoFromExternalDoc( externalSpdxDoc, artifact.getGroupId(), 
                                                           artifact.getArtifactId(), artifact.getVersion() );
                }
            }
            catch ( IOException e )
            {
                LOG.warn(
                        "IO error reading SPDX document for dependency artifact ID " + artifact.getArtifactId() + ":" + e.getMessage() + ".  Using POM file information for creating SPDX package data." );
            }
            catch ( SpdxInvalidIdException e ) 
            {
                LOG.warn(
                          "Invalid SPDX ID exception reading SPDX document for dependency artifact ID " + artifact.getArtifactId() + ":" + e.getMessage() + ".  Using POM file information for creating SPDX package data." );
            }
            catch ( InvalidSPDXAnalysisException e )
            {
                LOG.warn(
                        "Invalid SPDX analysis exception reading SPDX document for dependency artifact ID " + artifact.getArtifactId() + ":" + e.getMessage() + ".  Using POM file information for creating SPDX package data." );
            }
            catch ( SpdxCollectionException e )
            {
                LOG.warn(
                        "Unable to create file checksum for external SPDX document for dependency artifact ID " + artifact.getArtifactId() + ":" + e.getMessage() + ".  Using POM file information for creating SPDX package data." );
            }
            catch ( Exception e )
            {
                LOG.warn(
                        "Unknown error processing SPDX document for dependency artifact ID " + artifact.getArtifactId() + ":" + e.getMessage() + ".  Using POM file information for creating SPDX package data." );
            }
        }
        try
        {
            ProjectBuildingRequest request = new DefaultProjectBuildingRequest( session.getProjectBuildingRequest() );
            request.setRemoteRepositories( mavenProject.getRemoteArtifactRepositories() );
            for ( ArtifactRepository ar : request.getRemoteRepositories() ) {
                LOG.debug( "request Remote repository ID: " + ar.getId() );
            }
            for ( ArtifactRepository ar : mavenProject.getRemoteArtifactRepositories() ) {
                LOG.debug( "Project Remote repository ID: " + ar.getId() );
            }
            ProjectBuildingResult build = mavenProjectBuilder.build( artifact, request );
            MavenProject depProject = build.getProject();
            LOG.debug(
                      "Dependency " + artifact.getArtifactId() + "Collecting information from project metadata for " + depProject.getArtifactId() );
            return createSpdxPackage( depProject, useArtifactID );
        }
        catch ( SpdxCollectionException e )
        {
            LOG.error(
                    "SPDX File Collection Error creating SPDX package for dependency artifact ID " + artifact.getArtifactId() + ":" + e.getMessage() );
        }
        catch ( NoSuchAlgorithmException e )
        {
            LOG.error(
                    "Verification Code Error creating SPDX package for dependency artifact ID " + artifact.getArtifactId() + ":" + e.getMessage() );
        }
        catch ( ProjectBuildingException e )
        {
            LOG.error(
                      "Maven Project Build Error creating SPDX package for dependency artifact ID " + artifact.getArtifactId() + ":" + e.getMessage() );
        }
        LOG.warn(
                "Error creating SPDX package for dependency artifact ID " + artifact.getArtifactId() + ".  A minimal SPDX package will be created." );
        // Create a minimal SPDX package from dependency
        // Name will be the artifact ID
        LOG.debug(
                "Dependency " + artifact.getArtifactId() + "Using only artifact information to create dependent package" );
        SpdxPackage pkg = spdxDoc.createPackage( spdxDoc.getModelStore().getNextId( IdType.SpdxId, spdxDoc.getDocumentUri() ), 
                                                 artifact.getArtifactId(), new SpdxNoAssertionLicense(), "NOASSERTION", 
                                                 new SpdxNoAssertionLicense() )
                        .setComment( "This package was created for a Maven dependency.  No SPDX or license information could be found in the Maven POM file." )
                        .setVersionInfo( artifact.getBaseVersion() )
                        .setFilesAnalyzed( false )
                        .setDownloadLocation( "NOASSERTION" )
                        .setExternalRefs( SpdxExternalRefBuilder.getDefaultExternalRefs( spdxDoc, generatePurls, mavenProject ) )
                        .build();
        return pkg;
    }

    /**
     * Copies the closest matching described package in the externalSpdxDoc to the returned element
     * @param externalSpdxDoc
     * @param groupId Group ID of the artifact
     * @param artifactId Artifact ID to search for
     * @param version Version of the artifact
     * @return SPDX Package with values copied from the externalSpdxDoc
     * @throws InvalidSPDXAnalysisException 
     */
    private SpdxPackage copyPackageInfoFromExternalDoc( SpdxDocument externalSpdxDoc, String groupId,
                                                        String artifactId, String version ) throws InvalidSPDXAnalysisException
    {
        SpdxPackage source = findMatchingDescribedPackage( externalSpdxDoc, artifactId );
        Optional<String> downloadLocation = source.getDownloadLocation();
        Optional<String> name = source.getName();
        SpdxPackage dest = spdxDoc.createPackage( spdxDoc.getModelStore().getNextId( IdType.SpdxId, spdxDoc.getDocumentUri()),
                                                  name.isPresent() ? name.get() : "NONE", source.getLicenseConcluded(), source.getCopyrightText(), 
                                                  source.getLicenseDeclared() )
                      .setFilesAnalyzed( false )
                      .setAnnotations( source.getAnnotations() )
                      .setChecksums( source.getChecksums() )
                      .setDownloadLocation( downloadLocation.isPresent() ? downloadLocation.get() : "NOASSERTION" )
                      .setExternalRefs( source.getExternalRefs() )
                      .build();
        // We don't want to copy any of the properties which have other elements since it
        // may duplicate artifacts already included in the document - so we can't use copyFrom

        Optional<String> builtDate = source.getBuiltDate();
        if ( builtDate.isPresent() )
        {
            dest.setBuiltDate( builtDate.get() );
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
        Optional<String> homePage = source.getHomepage();
        if ( homePage.isPresent() )
        {
            dest.setHomepage( homePage.get() );
        }
        Optional<String> licenseComments = source.getLicenseComments();
        if ( licenseComments.isPresent() )
        {
            dest.setLicenseComments( licenseComments.get() );
        }
        Optional<String> originator = source.getOriginator();
        if ( originator.isPresent() )
        {
            dest.setOriginator( originator.get() );
        }
        Optional<String> pkgFileName = source.getPackageFileName();
        if ( pkgFileName.isPresent() )
        {
            dest.setPackageFileName( pkgFileName.get() );
        }
        Optional<Purpose> primaryPurpose = source.getPrimaryPurpose();
        if ( primaryPurpose.isPresent() )
        {
            dest.setPrimaryPurpose( primaryPurpose.get() );
        }
        Optional<String> releaseDate = source.getReleaseDate();
        if ( releaseDate.isPresent() )
        {
            dest.setReleaseDate( releaseDate.get() );
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
            dest.setSupplier( supplier.get() );
        }
        Optional<String> validUntil = source.getValidUntilDate();
        if ( validUntil.isPresent() )
        {
            dest.setValidUntilDate( validUntil.get() );
        }
        Optional<String> versionInfo = source.getVersionInfo();
        if ( versionInfo.isPresent() )
        {
            dest.setVersionInfo( versionInfo.get() );
        }
        return dest;
    }

    /**
     * Searched the described packages for the SPDX document for the closest matching package to the artifactId
     * @param externalSpdxDoc Doc containing the package
     * @param artifactId Maven artifact ID
     * @return the closest matching package described by the doc 
     * @throws InvalidSPDXAnalysisException 
     */
    private SpdxPackage findMatchingDescribedPackage( SpdxDocument externalSpdxDoc, String artifactId ) throws InvalidSPDXAnalysisException
    {
        SpdxElement itemDescribed = null;
        // Find an item described with matching artifact ID
        for ( SpdxElement item : externalSpdxDoc.getDocumentDescribes() )
        {
            Optional<String> name = item.getName();
            if ( item instanceof SpdxPackage && name.isPresent() && item.getName().get().equals( artifactId )  )
            {
                itemDescribed = item;
                break;
            }
        }
        if ( itemDescribed == null ) {
            // Find the first package
            LOG.warn( "Could not find matching artifact ID in SPDX file for "+artifactId+".  Using the first package found in SPDX file." );
            for ( SpdxElement item : externalSpdxDoc.getDocumentDescribes() )
            {
                if ( item instanceof SpdxPackage  )
                {
                    itemDescribed = item;
                    break;
                }
            }
        }
        if ( itemDescribed == null ) {
            throw new InvalidSPDXAnalysisException( "SPDX document does not contain any described items." );
        }
        return (SpdxPackage)itemDescribed;
    }

    /**
     * Creates an SPDX document from a file
     * @param path Path to the SPDX file
     * @return
     * @throws IOException 
     * @throws FileNotFoundException 
     * @throws InvalidSPDXAnalysisException 
     */
    private SpdxDocument spdxDocumentFromFile( String path ) throws FileNotFoundException, IOException, InvalidSPDXAnalysisException
    {
        ISerializableModelStore modelStore;
        if ( path.toLowerCase().endsWith( "json" ) ) 
        {
            modelStore = new MultiFormatStore(new InMemSpdxStore(), Format.JSON_PRETTY, Verbose.COMPACT);
        }
        else
        {
            modelStore = new RdfStore();
        }
        try ( InputStream inputStream = new FileInputStream( path ) ) 
        {
            String documentUri =  modelStore.deSerialize( inputStream, false );
            return new SpdxDocument(modelStore, documentUri, spdxDoc.getCopyManager(), false);
        } 
        finally
        {
            if ( modelStore != null ) {
                try
                {
                    modelStore.close();
                }
                catch ( Exception e )
                {
                    LOG.error( "Error closing SPDX model store", e );
                }
            }
        }
    }

    /**
     * Create and return an external document reference for an existing package in an SPDX document
     *
     * @param externalSpdxDoc       SPDX Document containing the package to be referenced.
     * @param spdxFile      SPDX file containing the SPDX document
     * @param groupId Group ID for the external artifact
     * @param artifactId Artifact ID for the external artifact
     * @param version version for the external artifact
     * @return created SPDX element
     * @throws SpdxCollectionException
     * @throws InvalidSPDXAnalysisException
     */
    private SpdxElement createExternalSpdxPackageReference( SpdxDocument externalSpdxDoc, 
                                                            File spdxFile, 
                                                            String groupId,
                                                            String artifactId,
                                                            @Nullable String version ) throws SpdxCollectionException, InvalidSPDXAnalysisException
    {
        String externalDocNamespace = externalSpdxDoc.getDocumentUri();
        ExternalDocumentRef externalRef = this.externalDocuments.get( externalDocNamespace );
        StringBuilder sb = new StringBuilder( groupId ).append( artifactId );
        if ( Objects.nonNull( version )) {
            sb.append( version );
        }
        String fullArtifactId = sb.toString();
        if ( externalRef == null )
        {
            String externalRefDocId = SpdxConstants.EXTERNAL_DOC_REF_PRENUM + fixExternalRefId( fullArtifactId );
            LOG.debug( "Creating external document ref " + externalDocNamespace );
            if (spdxFile.getName().endsWith(".json")) {
                String sha1 = SpdxFileCollector.generateSha1( spdxFile, spdxDoc );
                Checksum cksum = externalSpdxDoc.createChecksum( ChecksumAlgorithm.SHA1, sha1 );
                externalRef = spdxDoc.createExternalDocumentRef( externalRefDocId, externalSpdxDoc.getDocumentUri(), cksum );
            } else {
                String sha1 = SpdxFileCollector.generateSha1( spdxFile, spdxDoc );
                Checksum cksum = externalSpdxDoc.createChecksum( ChecksumAlgorithm.SHA1, sha1 );
                externalRef = spdxDoc.createExternalDocumentRef( externalRefDocId, externalSpdxDoc.getDocumentUri(), cksum );
            }    
            spdxDoc.getExternalDocumentRefs().add( externalRef );
            org.spdx.library.model.Annotation docRefAddedAnnotation = spdxDoc.createAnnotation( "Tool: spdx-maven-plugin", 
                                                                         AnnotationType.OTHER, 
                                                                         format.format( new Date() ), 
                                                                         "External document ref '"+externalRefDocId+"' created for artifact "+fullArtifactId );
            spdxDoc.getAnnotations().add( docRefAddedAnnotation );
            this.documentAnnotations.add( docRefAddedAnnotation );
            this.externalDocuments.put( externalDocNamespace, externalRef );
            LOG.debug( "Created external document ref " + externalRefDocId );
        }
        SpdxPackage pkg = findMatchingDescribedPackage( externalSpdxDoc, artifactId );
        return new ExternalSpdxElement( spdxDoc.getModelStore(), spdxDoc.getDocumentUri(),  
                                        externalRef.getId() + ":" + pkg.getId(), spdxDoc.getCopyManager(), true );
    }

    /**
     * Make an external document reference ID valid by replacing any invalid characters with dashes
     *
     * @param externalRefId
     * @return
     */
    private String fixExternalRefId( String externalRefId )
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

    /**
     * Create an SPDX package from the information in a Maven Project
     *
     * @param project Maven project
     * @param useArtifactID If true, use ${project.groupId}:${artifactId} as the SPDX package name, otherwise, ${project.name} will be used
     * @return SPDX Package generated from the metadata in the Maven Project
     * @throws XmlPullParserException
     * @throws IOException
     * @throws SpdxCollectionException
     * @throws NoSuchAlgorithmException
     * @throws LicenseMapperException
     * @throws InvalidSPDXAnalysisException 
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
        AnyLicenseInfo declaredLicense = mavenLicensesToSpdxLicense( project.getLicenses() );
        fileInfo.setComment( "" );
        fileInfo.setConcludedLicense( new SpdxNoAssertionLicense() );
        fileInfo.setContributors( fileContributorList.toArray( new String[0] ) );
        fileInfo.setCopyright( copyright );
        fileInfo.setDeclaredLicense( declaredLicense );
        fileInfo.setLicenseComment( "" );
        fileInfo.setNotice( notice );

        SpdxPackage retval = spdxDoc.createPackage( spdxDoc.getModelStore().getNextId( IdType.SpdxId, spdxDoc.getDocumentUri() ),
                                                    packageName, new SpdxNoAssertionLicense(), copyright, declaredLicense )
                        .setDownloadLocation( downloadLocation )
                        .setFilesAnalyzed( false )
                        .setExternalRefs( SpdxExternalRefBuilder.getDefaultExternalRefs( spdxDoc, generatePurls, project ) )
                        .build();
        if ( project.getVersion() != null )
        {
            retval.setVersionInfo( project.getVersion() );
        }
        if ( project.getDescription() != null )
        {
            retval.setDescription( project.getDescription() );
            retval.setSummary( project.getDescription() );
        }
        if ( project.getOrganization() != null )
        {
            retval.setOriginator( SpdxConstants.CREATOR_PREFIX_ORGANIZATION + project.getOrganization().getName() );
        }
        if ( project.getUrl() != null )
        {
            try {
                retval.setHomepage( project.getUrl() );
            } catch ( InvalidSPDXAnalysisException e ) {
                LOG.warn( "Invalid homepage for dependency " + project.getArtifactId() + ": " + project.getUrl() );
            }
        }
        return retval;
    }

    /**
     * Convert a list of Maven licenses to an SPDX License
     *
     * @param mavenLicenses List of maven licenses to map
     * @return
     * @throws LicenseMapperException
     * @throws InvalidSPDXAnalysisException 
     * @throws LicenseManagerException
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
            return MavenToSpdxLicenseMapper.getInstance().mavenLicenseListToSpdxLicense( mavenLicenses, spdxDoc );
        }

    }

    /**
     * Get filsets of files included in the project from the Maven model
     *
     * @param model Maven model
     * @return Source file set and resource filesets
     */
    @SuppressWarnings( "unused" )
    private FileSet[] getIncludedDirectoriesFromModel( Model model )
    {
        //TODO: This can be refactored to common code from the CreateSpdxMojo
        ArrayList<FileSet> result = new ArrayList<>();
        String sourcePath = model.getBuild().getSourceDirectory();
        if ( sourcePath != null && !sourcePath.isEmpty() )
        {
            FileSet srcFileSet = new FileSet();
            File sourceDir = new File( sourcePath );
            srcFileSet.setDirectory( sourceDir.getAbsolutePath() );
            srcFileSet.addInclude( CreateSpdxMojo.INCLUDE_ALL );
            result.add( srcFileSet );
        }

        List<Resource> resourceList = model.getBuild().getResources();
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
            }
        }
        return result.toArray( new FileSet[0] );
    }

    /**
     * Converts an artifact file to an SPDX file
     *
     * @param file input file
     * @return SPDX file using the SPDX naming conventions
     */
    private File artifactFileToSpdxFile( File file )
    {
        File retval = getFileWithDifferentType( file, "spdx.rdf.xml" );
        if ( retval == null || !retval.exists() )
        {
            retval = getFileWithDifferentType( file, "spdx.json" );
        }
        if ( retval == null || !retval.exists() )
        {
            retval = getFileWithDifferentType( file, "spdx" );
        }
        return retval;
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
        File retval = new File( filePath );
        return retval;
    }

    /**
     * @return All external document references used by any dependency toRelationships
     */
    public Collection<ExternalDocumentRef> getDocumentExternalReferences()
    {
        return this.externalDocuments.values();
    }

    /**
     * @return the relationships
     */
    public Map<SpdxElement, List<Relationship>> getRelationships()
    {
        return relationships;
    }

    /**
     * @return the externalDocuments
     */
    public Map<String, ExternalDocumentRef> getExternalDocuments()
    {
        return externalDocuments;
    }

    /**
     * @return the documentAnnotations
     */
    public List<org.spdx.library.model.Annotation> getDocumentAnnotations()
    {
        return documentAnnotations;
    }

    /**
     * @return the spdxDoc
     */
    public SpdxDocument getSpdxDoc()
    {
        return spdxDoc;
    }

    /**
     * @return the createExternalRefs
     */
    public boolean isCreateExternalRefs()
    {
        return createExternalRefs;
    }
}
