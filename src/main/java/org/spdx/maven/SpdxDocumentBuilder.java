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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.shared.model.fileset.FileSet;
import org.spdx.rdfparser.InvalidSPDXAnalysisException;
import org.spdx.rdfparser.SPDXCreatorInformation;
import org.spdx.rdfparser.SpdxDocumentContainer;
import org.spdx.rdfparser.SpdxPackageVerificationCode;
import org.spdx.rdfparser.SpdxRdfConstants;
import org.spdx.rdfparser.SpdxVerificationHelper;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.LicenseInfoFactory;
import org.spdx.rdfparser.license.ListedLicenses;
import org.spdx.rdfparser.license.SpdxListedLicense;
import org.spdx.rdfparser.model.Annotation;
import org.spdx.rdfparser.model.Checksum;
import org.spdx.rdfparser.model.Checksum.ChecksumAlgorithm;
import org.spdx.rdfparser.model.ExternalDocumentRef;
import org.spdx.rdfparser.model.Relationship;
import org.spdx.rdfparser.model.Relationship.RelationshipType;
import org.spdx.rdfparser.model.SpdxDocument;
import org.spdx.rdfparser.model.SpdxFile;
import org.spdx.rdfparser.model.SpdxPackage;
import org.spdx.spdxspreadsheet.InvalidLicenseStringException;

/**
 * Builds SPDX documents for a given set of source files.
 * This is the primary class to use when creating SPDX documents based on project files.
 * @author Gary O'Neall
 *
 */
public class SpdxDocumentBuilder
{
    private static final String UNSPECIFIED = "UNSPECIFIED";

    public static final String NULL_SHA1 = "cf23df2207d99a74fbe169e3eba035e633b65d94";

    //TODO: Use a previous SPDX to document file specific information and update
    //TODO: Map the SPDX document to the Maven build artifacts
    static DateFormat format = new SimpleDateFormat( SpdxRdfConstants.SPDX_DATE_FORMAT );

    private Log log;
    private SpdxDocument spdxDoc;
    private SpdxPackage projectPackage;
    private SpdxDocumentContainer container;
    private LicenseManager licenseManager;
    private File spdxFile;

    /**
     * @param log Log for logging information and errors
     * @param spdxFile File to store the SPDX document results
     * @param spdxDocumentNamespace URI for SPDX document - must be unique
     * @param useStdLicenseSourceUrls if true, map any SPDX standard license source URL to license ID.  Note: significant performance degredation
     * @throws SpdxBuilderException
     * @throws LicenseMapperException 
     */
    public SpdxDocumentBuilder( Log log, File spdxFile, URL spdxDocumentNamespace,
                                boolean useStdLicenseSourceUrls) throws SpdxBuilderException, LicenseMapperException
    {
        this.log = log;
        this.spdxFile = spdxFile;
        
        if ( spdxDocumentNamespace == null ) 
        {
            this.getLog().error( "spdxDocumentNamespace must be specified as a configuration parameter" );
            throw( new SpdxBuilderException( "Missing spdxDocumentNamespace" ) );
        }
        
        // Handle the SPDX file
        if ( !spdxFile.exists() )
        {
            File parentDir = spdxFile.getParentFile();
            if ( parentDir != null && !parentDir.exists() ) 
            {
                if ( !parentDir.mkdirs() ) 
                {
                    this.getLog().error( "Unable to create directory containing the SPDX file: "+parentDir.getPath() );
                    throw( new SpdxBuilderException( "Unable to create directories for SPDX file" ) );
                }
            }

            try 
            {
                if ( !spdxFile.createNewFile() ) 
                {
                    this.getLog().error( "Unable to create the SPDX file: "+spdxFile.getPath() );
                    throw( new SpdxBuilderException( "Unable to create the SPDX file" ) );
                }
            } catch ( IOException e ) 
            {
                this.getLog().error( "IO error creating the SPDX file "+spdxFile.getPath() + ":"+e.getMessage(),e );
                throw( new SpdxBuilderException( "IO error creating the SPDX file" ) );
            }
        }
        if ( !spdxFile.canWrite() ) 
        {
            this.getLog().error( "Can not write to SPDX file "+spdxFile.getPath() );
            throw( new SpdxBuilderException( "Unable to write to SPDX file - check permissions: "+spdxFile.getPath() ) ) ;
        }
        
        // create the SPDX document
        try 
        {
            container = new SpdxDocumentContainer( spdxDocumentNamespace.toString() );
            spdxDoc = container.getSpdxDocument();
        } catch ( InvalidSPDXAnalysisException e ) 
        {
            this.getLog().error( "Error creating SPDX document", e );
            throw( new SpdxBuilderException( "Error creating SPDX document: "+e.getMessage() ) );
        }
        
        // process the licenses
        licenseManager = new LicenseManager( spdxDoc, getLog(), useStdLicenseSourceUrls ); 
    }
    
    /**
     * add non standard licenses to the SPDX document
     * @param spdxDoc
     * @throws SpdxBuilderException 
     */
    public void addNonStandardLicenses( NonStandardLicense[] nonStandardLicenses ) throws SpdxBuilderException 
    {
        if ( nonStandardLicenses != null ) 
        {
            for ( int i = 0; i < nonStandardLicenses.length; i++ ) 
            {
                try
                {
                    licenseManager.addExtractedLicense( nonStandardLicenses[i] );
                }
                catch ( LicenseManagerException e )
                {
                    this.getLog().error( "Error adding license "+e.getMessage(), e );
                    throw(new SpdxBuilderException("Error adding non standard license: "+e.getMessage(), e));
                }
            }
        }
    }

    public Log getLog()
    {
        return this.log;
    }
    
    public void setLog(Log log) {
        this.log = log;
    }

    public SpdxDocument getSpdxDoc()
    {
        return this.spdxDoc;
    }

    /**
     * Build the SPDX document from the files and save the information to the SPDX file
   * @param includedSourceDirectories Source directories to be included in the document
   * @param includedResourceDirectories Test directories to be included in the document
   * @param includedTestDirectories Resource directories to be included in the document
     * @param baseDir Base directory used to create the relative file paths for the SPDX file names
     * @param projectInformation Project level SPDX information
     * @param defaultFileInformation Default SPDX file information
     * @param pathSpecificInformation Map of path to file information used to override the default file information
     * @param dependencyInformation Dependencies to add to the SPDX file (typically based on project dependencies in the POM file)
     * @throws SpdxBuilderException
     */
    public void buildDocumentFromFiles( FileSet[] includedSourceDirectories,
                                        FileSet[] includedTestDirectories, FileSet[] includedResourceDirectories, String baseDir, SpdxProjectInformation projectInformation,
                                        SpdxDefaultFileInformation defaultFileInformation,
                                        Map<String, SpdxDefaultFileInformation> pathSpecificInformation, 
                                        SpdxDependencyInformation dependencyInformation ) throws SpdxBuilderException
    {
        FileOutputStream spdxOut = null;
        try 
        {
            this.log.debug(  "Starting buid document from files" );
            spdxOut = new FileOutputStream ( spdxFile );
            fillSpdxDocumentInformation( projectInformation );
            collectSpdxFileInformation( includedSourceDirectories, includedTestDirectories,
                                        includedResourceDirectories, baseDir,
                    defaultFileInformation, spdxFile.getPath().replace( "\\", "/" ),
                    pathSpecificInformation );
            addDependencyInformation( dependencyInformation );
            container.getModel().write( spdxOut );
            this.log.debug( "Completed build document from files" );
        } catch ( FileNotFoundException e ) 
        {
            this.getLog().error( "Error saving SPDX data to file", e );
            throw( new SpdxBuilderException( "Error saving SPDX data to file: "+e.getMessage() ) ) ;
        } catch ( InvalidSPDXAnalysisException e ) 
        {
            this.getLog().error( "Error collecting SPDX file data", e );
            throw( new SpdxBuilderException( "Error collecting SPDX file data: "+e.getMessage() ) );
        } finally 
        {
            if ( spdxOut != null ) 
            {
                try {
                    spdxOut.close();
                } catch ( IOException e ) 
                {
                    this.getLog().warn( "Error closing SPDX output file", e );
                }
            }
        }
    }
    
    
    /**
     * Add dependency information to the SPDX file
     * @param dependencyInformation dependency information collected from the project POM file
     * @throws SpdxBuilderException 
     */
    private void addDependencyInformation( SpdxDependencyInformation dependencyInformation ) throws SpdxBuilderException
    {
        List<Relationship> packageRelationships = dependencyInformation.getPackageRelationships();
        if ( packageRelationships != null ) {
            for ( Relationship relationship:packageRelationships ) {
                try
                {
                    this.projectPackage.addRelationship( relationship );
                }
                catch ( InvalidSPDXAnalysisException e )
                {
                    log.error( "Unable to set package dependencies: "+e.getMessage() );
                    throw new SpdxBuilderException("Unable to set package dependencies", e );
                }
            }
        }
        Collection<ExternalDocumentRef> externalDocRefs = dependencyInformation.getDocumentExternalReferences();

        if ( externalDocRefs != null && !externalDocRefs.isEmpty() ) {
            try
            {
                this.spdxDoc.setExternalDocumentRefs( externalDocRefs.toArray( new ExternalDocumentRef[externalDocRefs.size()] ) );
            }
            catch ( InvalidSPDXAnalysisException e )
            {
                log.error( "Unable to set external document references: "+e.getMessage() );
                throw new SpdxBuilderException("Unable to set external document references", e );
            }
        }
    }

    /**
     * Fill in the document level information for SPDX
     * @param projectInformation project information to be used
     * @throws SpdxBuilderException
     */
    private void fillSpdxDocumentInformation( SpdxProjectInformation projectInformation ) throws SpdxBuilderException 
    {
      try {
          // document comment
          if ( projectInformation.getDocumentComment() != null && !projectInformation.getDocumentComment().isEmpty() )
          {
              spdxDoc.setComment( projectInformation.getDocumentComment() );
          }
          // creator
          fillCreatorInfo( projectInformation );
          // data license
          SpdxListedLicense dataLicense = (SpdxListedLicense)(LicenseInfoFactory.parseSPDXLicenseString( SpdxDocumentContainer.SPDX_DATA_LICENSE_ID ) );
          spdxDoc.setDataLicense( dataLicense );         
          // annotations
          if ( projectInformation.getDocumentAnnotations() != null && projectInformation.getDocumentAnnotations().length > 0 ) {
              spdxDoc.setAnnotations( toSpdxAnnotations( projectInformation.getDocumentAnnotations() ) );
          }
          //TODO: Implement document annotations        
          //TODO: Add document level relationships
          spdxDoc.setName( projectInformation.getName() );  // Same as package name
          // Package level information
          projectPackage = createSpdxPackage( projectInformation );
          Relationship documentContainsRelationship = new Relationship( projectPackage, RelationshipType.relationshipType_describes, "" );
          spdxDoc.getDocumentContainer().addElement( projectPackage );
          spdxDoc.addRelationship( documentContainsRelationship );
      } catch ( InvalidSPDXAnalysisException e ) 
      {
          this.getLog().error( "SPDX error filling SPDX information", e );
          throw( new SpdxBuilderException( "Error adding package information to SPDX document: "+e.getMessage(), e ) );
      } catch ( InvalidLicenseStringException e ) 
      {
          this.getLog().error( "SPDX error creating license", e );
          throw( new SpdxBuilderException( "Error adding package information to SPDX document: "+e.getMessage(), e ) );
      }
  }

  private Annotation[] toSpdxAnnotations( org.spdx.maven.Annotation[] annotations )
    {
        Annotation[] retval = new Annotation[annotations.length];
        for ( int i = 0; i < annotations.length; i++ ) {
            retval[i] = annotations[i].toSpdxAnnotation();
        }
        return retval;
    }

private SpdxPackage createSpdxPackage( SpdxProjectInformation projectInformation ) throws SpdxBuilderException
    {
      //TODO: Add annotations
      //TODO: Add relationships
      //TODO: Add comment
      String copyrightText = projectInformation.getCopyrightText();
      if ( copyrightText == null ) {
          copyrightText = UNSPECIFIED;
      }
      String downloadUrl = projectInformation.getDownloadUrl();
      if ( downloadUrl == null ) {
          downloadUrl = UNSPECIFIED;
      }
      SpdxPackageVerificationCode nullPackageVerificationCode = new SpdxPackageVerificationCode(NULL_SHA1, new String[0]);
      SpdxPackage pkg = new SpdxPackage(projectInformation.getName(),
                                                   projectInformation.getConcludedLicense(),
                                                   new AnyLicenseInfo[0],
                                                   copyrightText, 
                                                   projectInformation.getDeclaredLicense(),
                                                   downloadUrl, new SpdxFile[0],
                                                   nullPackageVerificationCode);
      // Annotations
      if ( projectInformation.getPackageAnnotations() != null && projectInformation.getPackageAnnotations().length > 0 ) {
          try
        {
            pkg.setAnnotations( toSpdxAnnotations( projectInformation.getPackageAnnotations() ) );
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            this.getLog().error( "Invalid package annotation", e );
            throw( new SpdxBuilderException( "Error adding package annotations to SPDX document: "+e.getMessage(), e ) );
        }
      }
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
          pkg.setHomepage( projectInformation.getHomePage() );
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
      // sha1 checksum
      if ( projectInformation.getSha1() != null ) 
      {
          //TODO: Add options for additional checksums
          Checksum checksum = new Checksum( ChecksumAlgorithm.checksumAlgorithm_sha1, projectInformation.getSha1() );
          try
        {
            pkg.setChecksums( new Checksum[] { checksum } );
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            this.getLog().error( "Invalid checksum value for package", e );
            throw( new SpdxBuilderException( "Error adding package information to SPDX document - Invalid checksum provided: "+e.getMessage(), e ) );
        }
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
      return pkg;
    }

/**
   * Fill in the creator information to the SPDX document
 * @param projectInformation project level information including the creators
 * @throws InvalidSPDXAnalysisException
 */
private void fillCreatorInfo( SpdxProjectInformation projectInformation ) throws InvalidSPDXAnalysisException 
{
      ArrayList<String> creators = new ArrayList<String>();
      String[] parameterCreators = projectInformation.getCreators();
      for ( int i = 0; i < parameterCreators.length; i++ ) 
      {
          String verify = SpdxVerificationHelper.verifyCreator( parameterCreators[i] );
          if ( verify == null ) 
          {
              creators.add( parameterCreators[i] );
          } else 
          {
              this.getLog().warn( "Invalid creator string ( "+verify+" ), "+
                          parameterCreators[i]+" will be skipped." );
          }
      }
      SPDXCreatorInformation spdxCreator = new SPDXCreatorInformation(
              creators.toArray( new String[creators.size()] ), format.format( new Date() ),
              projectInformation.getCreatorComment(), 
              ListedLicenses.DEFAULT_LICENSE_LIST_VERSION );
      spdxDoc.setCreationInfo( spdxCreator );        
  }

  /**
   * Collect information at the file level, fill in the SPDX document
   * @param includedSourceDirectories Source directories to be included in the document
   * @param includedResourceDirectories Test directories to be included in the document
   * @param includedTestDirectories Resource directories to be included in the document
   * @param baseDir project base directory used to construct the relative paths for the SPDX files
   * @param projectInformation Project level SPDX information
   * @param spdxFileName SPDX file name - will be used for the skipped file names in the verification code
   * @param pathSpecificInformation Map of path to file information used to override the default file information
   * @throws InvalidSPDXAnalysisException
   * @throws SpdxBuilderException
 */
private void collectSpdxFileInformation( FileSet[] includedSourceDirectories,
          FileSet[] includedTestDirectories, FileSet[] includedResourceDirectories, String baseDir, SpdxDefaultFileInformation defaultFileInformation,
          String spdxFileName, 
          Map<String, SpdxDefaultFileInformation> pathSpecificInformation ) throws InvalidSPDXAnalysisException, SpdxBuilderException 
  {      
      SpdxFileCollector fileCollector = new SpdxFileCollector();
      fileCollector.setLog( getLog() );
      try {
          fileCollector.collectFiles( includedSourceDirectories, baseDir,
                                                 defaultFileInformation, pathSpecificInformation,
                                                 projectPackage, RelationshipType.relationshipType_generates );
          fileCollector.collectFiles( includedTestDirectories, baseDir,
                                      defaultFileInformation, pathSpecificInformation,
                                      projectPackage, RelationshipType.relationshipType_testcaseOf );
          fileCollector.collectFiles( includedResourceDirectories, baseDir,
                                      defaultFileInformation, pathSpecificInformation,
                                      projectPackage, RelationshipType.relationshipType_containedBy );
      } catch ( SpdxCollectionException e ) {
          this.getLog().error( "SPDX error collecting file information", e );
          throw( new SpdxBuilderException( "Error collecting SPDX file information: "+e.getMessage() ) );
      }
      projectPackage.setFiles( fileCollector.getFiles() );
      projectPackage.setLicenseInfosFromFiles( fileCollector.getLicenseInfoFromFiles() );
      try 
      {
          projectPackage.setPackageVerificationCode( fileCollector.getVerificationCode( spdxFileName ) );
      } catch ( NoSuchAlgorithmException e )  
      {
          this.getLog().error( "Error calculating verification code", e );
          throw( new SpdxBuilderException( "Unable to calculate verification code" ) );
      } catch ( InvalidSPDXAnalysisException e ) 
      {
          this.getLog().error( "SPDX Error updating verification code", e );
          throw( new SpdxBuilderException( "Unable to update verification code" ) );
      }
  }

public LicenseManager getLicenseManager()
{
    return this.licenseManager;
}
    
}
