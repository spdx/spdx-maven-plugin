/*
 * Copyright 2014 The Apache Software Foundation.
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
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Contributor;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.Resource;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.shared.model.fileset.FileSet;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.spdx.rdfparser.InvalidSPDXAnalysisException;
import org.spdx.rdfparser.SPDXDocumentFactory;
import org.spdx.rdfparser.SpdxPackageVerificationCode;
import org.spdx.rdfparser.SpdxRdfConstants;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.SpdxNoAssertionLicense;
import org.spdx.rdfparser.model.Checksum;
import org.spdx.rdfparser.model.Checksum.ChecksumAlgorithm;
import org.spdx.rdfparser.model.DoapProject;
import org.spdx.rdfparser.model.ExternalDocumentRef;
import org.spdx.rdfparser.model.ExternalSpdxElement;
import org.spdx.rdfparser.model.Relationship;
import org.spdx.rdfparser.model.Relationship.RelationshipType;
import org.spdx.rdfparser.model.SpdxDocument;
import org.spdx.rdfparser.model.SpdxElement;
import org.spdx.rdfparser.model.SpdxFile;
import org.spdx.rdfparser.model.SpdxItem;
import org.spdx.rdfparser.model.SpdxPackage;

/**
 * Contains information about package dependencies collected from the 
 * Maven dependencies.
 * @author Gary O'Neall
 *
 */
public class SpdxDependencyInformation
{

    private Log log;
    /**
     * List of all Relationships added for dependencies
     */
    private List<Relationship> relationships = new ArrayList<Relationship>();
    private Map<String, ExternalDocumentRef> externalDocuments = 
                    new HashMap<String, ExternalDocumentRef>();
    private LicenseManager licenseManager;
    
    /**
     * @param log Logger for Maven
     */
    public SpdxDependencyInformation( Log log, LicenseManager licenseManager ) {
        this.log = log;
        this.licenseManager = licenseManager;
    }
    /**
     * Add information about a Maven dependency to the list of SPDX Dependencies
     * @param dependency
     * @throws LicenseMapperException 
     */
    public void addMavenDependency( Artifact dependency ) throws LicenseMapperException
    {
        String scope = dependency.getScope();
        RelationshipType relType = scopeToRelationshipType( scope, dependency.isOptional() );
        if ( relType == RelationshipType.relationshipType_other ) {
            log.warn( "Could not determine the SPDX relationship type for dependency artifact ID "+dependency.getArtifactId()+" scope " + scope );
        }
        SpdxElement dependencyPackage = createSpdxPackage( dependency );
        Relationship relationship = new Relationship( dependencyPackage, relType, "Relationship based on Maven POM file dependency information" );
        this.relationships.add( relationship );
    }

    /**
     * Translate the scope to the SPDX relationship type
     * @param scope Maven Dependency Scope (see https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope)
     * @param optional True if this is an optional dependency
     * @return
     */
    private RelationshipType scopeToRelationshipType( String scope, boolean optional )
    {
        if ( scope == null ) {
            return RelationshipType.relationshipType_other;
        } else if ( optional ) {
            return RelationshipType.relationshipType_optionalComponentOf;
        } else if ( scope.equals( "compile" ) || scope.equals( "runtime" )) {
            return RelationshipType.relationshipType_dynamicLink;
        } else if (scope.equals( "test" )) {
            return RelationshipType.relationshipType_testcaseOf;
        } else {
            return RelationshipType.relationshipType_other;
        }
    }

    /**
     * Create an SPDX Document from a POM file stored in the Maven repository
     * @param artifact Maven dependency artifact
     * @return
     * @throws LicenseMapperException 
     */
    private SpdxElement createSpdxPackage( Artifact artifact ) throws LicenseMapperException
    {
        File spdxFile = artifactFileToSpdxFile( artifact.getFile() );
        log.debug( "Dependency "+artifact.getArtifactId()+"Looking for SPDX file "+spdxFile.getAbsolutePath() );
        if ( spdxFile != null && spdxFile.exists() ) {
            try
            {
                log.debug( "Dependency "+artifact.getArtifactId()+"Dependency information collected from SPDX file "+spdxFile.getAbsolutePath() );
                SpdxDocument spdxDoc = SPDXDocumentFactory.createSpdxDocument( spdxFile.getPath() );
                return createExternalSpdxPackageReference( spdxDoc, spdxFile, SpdxRdfConstants.EXTERNAL_DOC_REF_PRENUM + artifact.getArtifactId() );
            }
            catch ( IOException e )
            {
                log.error( "IO error reading SPDX document for dependency artifact ID "+artifact.getArtifactId() +  
                           ":"+e.getMessage() + ".  Using POM file information for creating SPDX package data." );
            }
            catch ( InvalidSPDXAnalysisException e )
            {
                log.error( "Invalid SPDX analysis exception reading SPDX document for dependency artifact ID "+artifact.getArtifactId() +  
                           ":"+e.getMessage() + ".  Using POM file information for creating SPDX package data." );
            }
            catch ( SpdxCollectionException e )
            {
                log.error( "Unable to create file checksum for external SPDX document for dependency artifact ID "+artifact.getArtifactId() +  
                           ":"+e.getMessage() + ".  Using POM file information for creating SPDX package data." );
            }
        }
        File pomFile = artifactFileToPomFile( artifact.getFile() );
        log.debug( "Dependency "+artifact.getArtifactId()+"Looking for POM file "+pomFile.getAbsolutePath() );
        if ( pomFile != null && pomFile.exists() ) {
            try
            {
                log.debug( "Dependency "+artifact.getArtifactId()+"Collecting information from POM file "+pomFile.getAbsolutePath() );
                return createSpdxPackage( pomFile );
            }
            catch ( IOException e )
            {
                log.error( "IO Error reading POM file for dependency artifact ID "+artifact.getArtifactId() +  
                           ":"+e.getMessage() );
            }
            catch ( XmlPullParserException e )
            {
                log.error( "Parser Error reading POM file for dependency artifact ID "+artifact.getArtifactId() +  
                           ":"+e.getMessage() );
            }
            catch ( SpdxCollectionException e )
            {
                log.error( "SPDX File Collection Error reading POM file for dependency artifact ID "+artifact.getArtifactId() +  
                           ":"+e.getMessage() );
            }
            catch ( NoSuchAlgorithmException e )
            {
                log.error( "Verification Code Error reading POM file for dependency artifact ID "+artifact.getArtifactId() +  
                           ":"+e.getMessage() );
            }
        this.log.warn( "No POM file found for dependency artifact ID "+artifact.getArtifactId()+
                       ".  A minimal SPDX package will be created." );
        }
        // Create a minimal SPDX package from dependency
        // Name will be the artifact ID
        log.debug( "Dependency "+artifact.getArtifactId()+"Using only artifact information to create dependent package" );
        SpdxPackage pkg = new SpdxPackage(artifact.getArtifactId(), 
                                          new SpdxNoAssertionLicense(), 
                                          new AnyLicenseInfo[] {new SpdxNoAssertionLicense()},
                                          "UNSPECIFIED",
                                          new SpdxNoAssertionLicense(), 
                                          "NOASSERTION", new SpdxFile[0], new SpdxPackageVerificationCode(SpdxDocumentBuilder.NULL_SHA1, new String[0]));
        pkg.setComment( "This package was created for a Maven dependency.  No SPDX or license information could be found in the Maven POM file." );
        pkg.setVersionInfo( artifact.getBaseVersion() );
        return pkg;
    }

    /**
     * Create and return an external document reference for an existing package in an SPDX document
     * @param spdxDoc SPDX Document containing the package to be referenced.
     * @param spdxFile SPDX file containing the SPDX document
     * @param externalRefId A unique external reference ID for the external SPDX document
     * @return
     * @throws SpdxCollectionException 
     * @throws InvalidSPDXAnalysisException 
     */
    private SpdxElement createExternalSpdxPackageReference( SpdxDocument spdxDoc, File spdxFile, String externalRefId ) throws SpdxCollectionException, InvalidSPDXAnalysisException
    {
        ExternalDocumentRef externalRef = this.externalDocuments.get( fixExternalRefId( externalRefId ) );
        if ( externalRef == null ) {
            String sha1 = SpdxFileCollector.generateSha1( spdxFile );
            Checksum cksum = new Checksum( ChecksumAlgorithm.checksumAlgorithm_sha1, sha1 );
            externalRef = new ExternalDocumentRef( spdxDoc, cksum, externalRefId );
            this.externalDocuments.put( externalRefId, externalRef );
        }
        SpdxItem[] describedItems = spdxDoc.getDocumentDescribes();
        if ( describedItems == null || describedItems.length == 0 ) {
            throw( new InvalidSPDXAnalysisException( "SPDX document does not contain any described items.") );
        }
        SpdxItem itemDescribed = describedItems[0];
        if ( describedItems.length > 1 ) {
            // pick out the first described package
            for ( SpdxItem item:describedItems ) {
                if ( item instanceof SpdxPackage ) {
                    itemDescribed = item;
                    break;
                    //TODO: This could be more sophisticated looking for a matching package name
                }
            }
        }
        return new ExternalSpdxElement( externalRef + ":" + itemDescribed.getId() );
    }
    /**
     * Make an external document reference ID valid by replacing any invalid characters with dashes
     * @param externalRefId
     * @return
     */
    private String fixExternalRefId( String externalRefId )
    {
        StringBuilder sb = new StringBuilder();
        for ( int i = 0; i < externalRefId.length(); i++ ) {
            if ( validExternalRefIdChar( externalRefId.charAt( i ) ) ) {
                sb.append( externalRefId.charAt( i ) );
            } else {
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
        return ( (ch >= 'a' && ch <='z') || (ch >= 'A' && ch <= 'Z') ||
                        ch == '.' || ch == '-' );
    }
    /**
     * Create an SPDX package from the information in an SPDX Pom file
     * @param pomFile
     * @return
     * @throws XmlPullParserException 
     * @throws IOException 
     * @throws SpdxCollectionException 
     * @throws NoSuchAlgorithmException 
     * @throws LicenseMapperException 
     */
    private SpdxPackage createSpdxPackage( File pomFile ) throws IOException, XmlPullParserException, SpdxCollectionException, NoSuchAlgorithmException, LicenseMapperException
    {
        MavenXpp3Reader pomReader = new MavenXpp3Reader();
        Model model;

        model = pomReader.read( ReaderFactory.newXmlReader( pomFile ) );
        SpdxDefaultFileInformation fileInfo = new SpdxDefaultFileInformation();
        
        // initialize the SPDX information from the POM file
        String packageName = model.getName();
        if ( packageName == null || packageName.isEmpty() ) {
            packageName = model.getArtifactId();
        }
        List<Contributor> contributors = model.getContributors();
        ArrayList<String> fileContributorList = new ArrayList<String>();
        if ( contributors != null ) {
            for ( Contributor contributor:contributors ) {
                fileContributorList.add( contributor.getName() );
            }
        }
        String copyright = "UNSPECIFIED";
        String notice = "UNSPECIFIED";
        String downloadLocation = "NOASSERTION";
        AnyLicenseInfo declaredLicense = mavenLicensesToSpdxLicense( model.getLicenses() );
        fileInfo.setArtifactOf( new DoapProject[0] );
        fileInfo.setComment( "" );
        fileInfo.setConcludedLicense( new SpdxNoAssertionLicense() );
        fileInfo.setContributors( fileContributorList.toArray(new String[fileContributorList.size()]) );
        fileInfo.setCopyright( copyright );
        fileInfo.setDeclaredLicense( declaredLicense );
        fileInfo.setLicenseComment( "" );
        fileInfo.setNotice( notice );
        
        SpdxPackage retval = new SpdxPackage(packageName, new SpdxNoAssertionLicense(), 
                                             new AnyLicenseInfo[] {new SpdxNoAssertionLicense()},
                                             copyright, declaredLicense, downloadLocation,
                                             new SpdxFile[0], 
                                             new SpdxPackageVerificationCode(SpdxDocumentBuilder.NULL_SHA1, new String[0]));
        if ( model.getVersion() != null ) {
            retval.setVersionInfo( model.getVersion() );
        }
        if (model.getDescription() != null) {
            retval.setDescription( model.getDescription() );
            retval.setSummary( model.getDescription() );
        }
        if (model.getOrganization() != null) {
            retval.setOriginator( SpdxRdfConstants.CREATOR_PREFIX_ORGANIZATION +  model.getOrganization().getName() );
        }
        if (model.getUrl() != null) {
            retval.setHomepage( model.getUrl() );
        }
        return retval;
    }
    
    /**
     * Convert a list of Maven licenses to an SPDX License
     * @param mavenLicenses List of maven licenses to map
     * @return
     * @throws LicenseMapperException 
     * @throws LicenseManagerException 
     */
    private AnyLicenseInfo mavenLicensesToSpdxLicense( List<License> mavenLicenses ) throws LicenseMapperException
    {
        try {
            // The call below will map non standard licenses as well as standard licenses
            // but will throw an exception if no mapping is found - we'll try this first
            // and if there is an error, try just the standard license mapper which will
            // return an UNSPECIFIED license type if there is no mapping
            return this.licenseManager.mavenLicenseListToSpdxLicense( mavenLicenses );
        } catch (LicenseManagerException ex) {
            return MavenToSpdxLicenseMapper.getInstance( log ).mavenLicenseListToSpdxLicense( mavenLicenses );
        }
        
    }
    /**
     * Get filsets of files included in the project from the Maven model
     * @param model Maven model
     * @return Source file set and resource filesets
     */
    private FileSet[] getIncludedDirectoriesFromModel( Model model ) 
    {
        //TODO: This can be refactored to common code from the CreateSpdxMojo
        ArrayList<FileSet> result = new ArrayList<FileSet>();
        String sourcePath = model.getBuild().getSourceDirectory();
        if ( sourcePath != null && !sourcePath.isEmpty() ) {
            FileSet srcFileSet = new FileSet();
            File sourceDir = new File( sourcePath );
            srcFileSet.setDirectory( sourceDir.getAbsolutePath() );
            srcFileSet.addInclude( CreateSpdxMojo.INCLUDE_ALL );
            result.add( srcFileSet );  
        }
  
        List<Resource> resourceList = model.getBuild().getResources();
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
            }
        }
        return result.toArray( new FileSet[result.size()] );
    }
    
    /**
     * Converts an artifact file to an SPDX file
     * @param file input file
     * @return SPDX file using the SPDX naming conventions
     */
    private File artifactFileToSpdxFile( File file )
    {
        return getFileWithDifferentType( file, "spdx" );
    }
    /**
     * Convert a file to a different type (e.g. file.txt -> file.rdf with a type rdf parameter)
     * @param file Input file
     * @param type Type to change to
     * @return New file type with only the type changed
     */
    private File getFileWithDifferentType( File file, String type )
    {
        String filePath = file.getAbsolutePath();
        int indexOfDot = filePath.lastIndexOf( '.' );
        if (indexOfDot > 0) {
            filePath = filePath.substring( 0, indexOfDot+1 );
        }
        filePath = filePath + type;
        File retval = new File(filePath);
        return retval;
    }
    /**
     * Converts an artifact file to a POM file
     * @param file input file
     * @return POM file using the POM naming conventions
     */
    private File artifactFileToPomFile( File file )
    {
        return getFileWithDifferentType( file, "pom" );
    }

    /**
     * @return all relationship associated with SPDX dependencies based on the Maven dependencies for the package
     * added using the addMavenDependency method
     */
    public List<Relationship> getPackageRelationships()
    {
        return this.relationships;
    }

    /**
     * @return All external document references used by any dependency relationships
     */
    public Collection<ExternalDocumentRef> getDocumentExternalReferences()
    {
        return this.externalDocuments.values();
    }

}
