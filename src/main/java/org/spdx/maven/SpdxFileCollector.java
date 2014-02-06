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
package org.spdx.maven;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;


import org.apache.maven.plugin.logging.Log;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spdx.rdfparser.DOAPProject;
import org.spdx.rdfparser.SPDXFile;
import org.spdx.rdfparser.SPDXLicenseInfo;
import org.spdx.rdfparser.SpdxPackageVerificationCode;
import org.spdx.rdfparser.SpdxRdfConstants;


/**
 * Collects SPDX file information from directories.
 * 
 * The method <code>collectFilesInDirectory(File directory)</code> will scan and
 * create SPDX File information for all files in the directory.
 * 
 * File patterns can be passed to the constructor file files which should be ignored.
 * 
 * @author Gary O'Neall
 *
 */
public class SpdxFileCollector 
{
    static Logger logger = LoggerFactory.getLogger( SpdxFileCollector.class );
    // constants for mapping extensions to types.
    static HashSet<String> SOURCE_EXTENSIONS = new HashSet<String>();
    static HashSet<String> BINARY_EXTENSIONS = new HashSet<String>();
    static HashSet<String> ARCHIVE_EXTENSIONS = new HashSet<String>();
    static final String SPDX_FILE_TYPE_CONSTANTS_PROP_PATH = "org/spdx/maven/SpdxFileTypeConstants.prop";
    static final String SPDX_PROP_FILETYPE_SOURCE = "SpdxSourceExtensions";
    static final String SPDX_PROP_FILETYPE_BINARY = "SpdxBinaryExtensions";
    static final String SPDX_PROP_FILETYPE_ARCHIVE = "SpdxArchiveExtensions";
    static 
    {
        loadFileExtensionConstants();
    }
    
    static final String SHA1_ALGORITHM = "SHA-1";
    static final String PACKAGE_VERIFICATION_CHARSET = "UTF-8";
    private static MessageDigest digest;
    static 
    {
        try 
        {
            digest = MessageDigest.getInstance( SHA1_ALGORITHM );
        } catch ( NoSuchAlgorithmException e ) {
            logger.error( "No such algorithm error initializing the SPDX file collector - SHA1", e );
            digest = null;
        };
    }
    
    
    HashSet<SPDXLicenseInfo> licensesFromFiles = new HashSet<SPDXLicenseInfo>();
    /**
     * Map of fileName, SPDXFile for all files in the SPDX document
     */
    HashMap<String, SPDXFile> spdxFiles = new HashMap<String, SPDXFile>();
    
    FileSetManager fileSetManager = new FileSetManager();
    private Log log;

    /**
     * SpdxFileCollector collects SPDX file information for files
     */
    public SpdxFileCollector() 
    {
        
    }
 
    /**
     * Load file type constants from the properties file
     */
    private static void loadFileExtensionConstants()
    {
        InputStream is = null;
        Properties prop = new Properties();
        try {
            is = SpdxFileCollector.class.getClassLoader().getResourceAsStream(SPDX_FILE_TYPE_CONSTANTS_PROP_PATH);
            if ( is == null ) {
                logger.error( "Unable to load properties file "+SPDX_FILE_TYPE_CONSTANTS_PROP_PATH );
            }
            prop.load(is);
            String sourceExtensionStr = prop.getProperty( SPDX_PROP_FILETYPE_SOURCE );
            loadSetUpcase( SOURCE_EXTENSIONS, sourceExtensionStr );
            String binaryExtensionStr = prop.getProperty( SPDX_PROP_FILETYPE_BINARY );
            loadSetUpcase( BINARY_EXTENSIONS, binaryExtensionStr );
            String archiveExtensionStr = prop.getProperty( SPDX_PROP_FILETYPE_ARCHIVE );
            loadSetUpcase( ARCHIVE_EXTENSIONS, archiveExtensionStr );
        }
        catch ( IOException e )
        {
            logger.warn("WARNING: Error reading SpdxFileTypeConstants properties file.  All file types will be mapped to Other.");
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (Throwable e) {
                logger.warn("WARNING: Error closing SpdxFileTypeConstants properties file");
            }
        }
    }

    /**
     * Load a set from a comma delimited string of values trimming and upcasing all values
     * @param set
     * @param str
     */
    private static void loadSetUpcase( Set<String> set, String str )
    {
        String[] values = str.split( "," );
        for ( int i = 0; i < values.length; i++ )
        {
            set.add( values[i].toUpperCase().trim() );
        }
    }

    /**
     * Collect file information in the directory (including subdirectories).  
     * @param fileSets FileSets containing the description of the directory to be scanned
     * @param pathPrefix Path string which should be removed when creating the SPDX file name
     * @param defaultFileInformation Information on default SPDX field data for the files
     * @param pathSpecificInformation Map of path to file information used to override the default file information
     * @throws SpdxCollectionException 
     */
    public void collectFiles( FileSet[] fileSets,
                              SpdxDefaultFileInformation defaultFileInformation,
                              Map<String, SpdxDefaultFileInformation> pathSpecificInformation ) throws SpdxCollectionException 
    {
       for ( int i = 0; i < fileSets.length; i++ ) 
       {
           String[] includedFiles = fileSetManager.getIncludedFiles( fileSets[i] );
           for ( int j = 0; j < includedFiles.length; j++ )
           {
               String filePath = fileSets[i].getDirectory() + File.separator + includedFiles[j];
               SpdxDefaultFileInformation fileInfo = findDefaultFileInformation( filePath, pathSpecificInformation );
               if ( fileInfo == null ) 
               {
                   fileInfo = defaultFileInformation;
               }
               File file = new File( filePath );
               String outputFileName;
               if ( fileSets[i].getOutputDirectory() != null ) 
               {
                   outputFileName = fileSets[i].getOutputDirectory() + File.separator + includedFiles[j];
               } else 
               {
                   outputFileName = includedFiles[j];
               }
               collectFile( file, outputFileName, fileInfo );
           }
       }
    }
    
    /**
     * Find the most appropriate file information based on the lowset level match (closedt to file)
     * @param filePath
     * @param pathSpecificInformation
     * @return
     */
    private SpdxDefaultFileInformation findDefaultFileInformation( String filePath,
                                                                   Map<String, SpdxDefaultFileInformation> pathSpecificInformation )
    {
        debug( "Mapping file info for "+filePath );
        SpdxDefaultFileInformation retval = pathSpecificInformation.get( filePath );
        if ( retval != null ) 
        {
            debug( "Found file path.  File comment: "+retval.getComment() );
            return retval;
        }
        // see if any of the parent directories contain default information which should be used
        String parentPath = filePath;
        int parentPathIndex = 0;
        do
        {
            parentPathIndex = parentPath.lastIndexOf( File.separator );
            if ( parentPathIndex > 0 ) 
            {
                debug( "Mapping file info for "+parentPath );
                parentPath = parentPath.substring( 0, parentPathIndex );
                retval = pathSpecificInformation.get( parentPath );
            }
        } while ( retval == null && parentPathIndex > 0 );
        if ( retval != null )
        {
            debug( "Found file path.  File comment: "+retval.getComment() );
        }
        return retval;
    }

    private void debug( String msg )
    {
        if ( this.getLog() != null ) 
        {
            this.getLog().debug( msg );
        } 
        else 
        {
            logger.debug( msg );
        }
    }

    /**
     * Collect SPDX information for a specific file
     * @param file
     * @param outputFileName Path to the output file name relative to the root of the output archive file
     * @param defaultFileInformation Information on default SPDX field data for the files
     * @param fileSpecificInformation Map of file path to file information used to override the default file information
     * @throws SpdxCollectionException
     */
    private void collectFile( File file, String outputFileName, SpdxDefaultFileInformation fileInfo ) throws SpdxCollectionException
    {
        if ( spdxFiles.containsKey( file.getPath() )) 
        {
            return; // already added from a previous scan
        }
        SPDXFile spdxFile = convertToSpdxFile( file, outputFileName, fileInfo );
        spdxFiles.put( file.getPath(), spdxFile );
        SPDXLicenseInfo[] seenLicenses = spdxFile.getSeenLicenses();
        for ( int j = 0; j < seenLicenses.length; j++ ) 
        {
            licensesFromFiles.add( seenLicenses[j] );
        }
    }

    /**
     * @param file
     * @param outputFileName Path to the output file name relative to the root of the output archive file
     * @param defaultFileInformation Information on default SPDX field data for the files
     * @return
     * @throws SpdxCollectionException
     */
    private SPDXFile convertToSpdxFile( File file, String outputFileName,
                                        SpdxDefaultFileInformation defaultFileInformation) throws SpdxCollectionException 
    {
        String relativePath = convertFilePathToSpdxFileName( outputFileName );
        String fileType = extensionToFileType( getExtension( file ) );
        String sha1 = generateSha1( file );
        SPDXLicenseInfo license;
        license = defaultFileInformation.getDeclaredLicense();
        String copyright = defaultFileInformation.getCopyright();
        String notice = defaultFileInformation.getNotice();
        String comment = defaultFileInformation.getComment();
        String[] contributors = defaultFileInformation.getContributors();
        DOAPProject[] artifactOf = defaultFileInformation.getArtifactOf();
        SPDXLicenseInfo concludedLicense = defaultFileInformation.getConcludedLicense();
        String licenseComment = defaultFileInformation.getLicenseComment();
        return new SPDXFile( relativePath, fileType, 
                sha1, concludedLicense, new SPDXLicenseInfo[] {license}, 
                licenseComment, copyright, artifactOf, comment, null, 
                contributors, notice );
    }

    /**
     * Create the SPDX file name from a system specific path name
     * @param filePath system specific file path relative to the top of the archive root
     * to the top of the archive directory where the file is stored.
     * @return
     */
    public String convertFilePathToSpdxFileName( String filePath )
    {
        String result = filePath.replace( '\\', '/' );
        if ( !result.startsWith( "./" )) 
        {
            result = "./" + result;
        }
        return result;
    }

    public String getExtension( File file ) {
        String fileName = file.getName();
        int lastDot = fileName.lastIndexOf( '.' );
        if ( lastDot < 1 ) 
        {
            return "";
        } else {
            return fileName.substring( lastDot+1 );
        }
    }

    private static String extensionToFileType( String fileExtension ) {
        if ( fileExtension == null ) {
            return SpdxRdfConstants.FILE_TYPE_OTHER;
        }
        String upperExtension = fileExtension.toUpperCase();
        if ( SOURCE_EXTENSIONS.contains( upperExtension ) ) 
        {
            return SpdxRdfConstants.FILE_TYPE_SOURCE;
        } else if ( BINARY_EXTENSIONS.contains( upperExtension ) ) 
        {
            return SpdxRdfConstants.FILE_TYPE_BINARY;
        } else if ( ARCHIVE_EXTENSIONS.contains( upperExtension ) ) 
        {
            return SpdxRdfConstants.FILE_TYPE_ARCHIVE;
        } else 
        {
            return SpdxRdfConstants.FILE_TYPE_OTHER;
        }
    }

    /**
     * @return SPDX Files which have been acquired through the collectFilesInDirectory method
     */
    public SPDXFile[] getFiles() 
    {
        return spdxFiles.values().toArray( new SPDXFile[spdxFiles.size()] );
    }

    /**
     * @return all license information used in the SPDX files
     */
    public SPDXLicenseInfo[] getLicenseInfoFromFiles() 
    {
        return licensesFromFiles.toArray( new SPDXLicenseInfo[licensesFromFiles.size()] );
    }

    /**
     * Create a verification code from all SPDX files collected
     * @param spdxFilePath Complete file path for the SPDX file - this will be excluded from the verification code
     * @return
     * @throws NoSuchAlgorithmException
     */
    public SpdxPackageVerificationCode getVerificationCode( String spdxFilePath ) throws NoSuchAlgorithmException 
    {
        ArrayList<String> excludedFileNamesFromVerificationCode = new ArrayList<String>();

        if ( spdxFilePath != null && spdxFiles.containsKey( spdxFilePath ) ) 
        {
            excludedFileNamesFromVerificationCode.add( spdxFiles.get( spdxFilePath ).getName() );
        }            
        SpdxPackageVerificationCode verificationCode;
        verificationCode = calculatePackageVerificationCode( spdxFiles.values(), excludedFileNamesFromVerificationCode );
        return verificationCode;
    }

    /**
     * Calculate the package verification code for a collection of SPDX files
     * @param spdxFiles Files used to calculate the verification code
     * @param excludedFileNamesFromVerificationCode List of file names to exclude
     * @return
     * @throws NoSuchAlgorithmException
     */
    private SpdxPackageVerificationCode calculatePackageVerificationCode(
            Collection<SPDXFile> spdxFiles,
            ArrayList<String> excludedFileNamesFromVerificationCode ) throws NoSuchAlgorithmException 
    {
        ArrayList<String> fileChecksums = new ArrayList<String>();
        Iterator<SPDXFile> iter = spdxFiles.iterator();
        while ( iter.hasNext() ) 
        {
            SPDXFile file = iter.next();
            if ( includeInVerificationCode( file.getName(), excludedFileNamesFromVerificationCode ) ) 
            {
                fileChecksums.add( file.getSha1() );
            }
        }
        Collections.sort( fileChecksums );
        MessageDigest verificationCodeDigest = MessageDigest.getInstance( "SHA-1" );
        for ( int i = 0;i < fileChecksums.size(); i++ ) 
        {
            byte[] hashInput = fileChecksums.get( i ).getBytes( Charset.forName( "UTF-8" ) );
            verificationCodeDigest.update( hashInput );
        }
        String value = convertChecksumToString( verificationCodeDigest.digest() );
        return new SpdxPackageVerificationCode( value, excludedFileNamesFromVerificationCode.toArray(
                new String[excludedFileNamesFromVerificationCode.size()] ) );
    }

    private boolean includeInVerificationCode( String name, ArrayList<String> excludedFileNamesFromVerificationCode ) 
    {
        for ( int i = 0; i < excludedFileNamesFromVerificationCode.size(); i++ ) 
        {
            if ( excludedFileNamesFromVerificationCode.get( i ).equals( name ) ) 
            {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Converts an array of bytes to a string compliant with the SPDX sha1 representation
     * @param digestBytes
     * @return
     */
    public static String convertChecksumToString( byte[] digestBytes ) 
    {
        StringBuilder sb = new StringBuilder();   
        for ( int i = 0; i < digestBytes.length; i++ ) 
        {
            String hex = Integer.toHexString( 0xff & digestBytes[i] );
            if ( hex.length() < 2 ) 
            {
                sb.append( '0' );
            }
            sb.append( hex );
        }
        return sb.toString();
    }
    
    /**
     * Generate the Sha1 for a given file.  Must have read access to the file.
     * @param file
     * @return
     * @throws SpdxCollectionException
     */
    public static String generateSha1( File file ) throws SpdxCollectionException 
    {
        if ( digest == null ) 
        {
            try {
                digest = MessageDigest.getInstance( SHA1_ALGORITHM );
            } catch ( NoSuchAlgorithmException e ) 
            {
                throw( new SpdxCollectionException( "Unable to create the message digest for generating the File SHA1" ) );
            }
        }
        digest.reset();
        InputStream in;
        try 
        {
            in = new FileInputStream( file );
        } catch ( IOException e1 ) 
        {
            throw( new SpdxCollectionException( "IO getting file content while calculating the SHA1" ) );
        }
        try {
            byte[] buffer = new byte[2048];
            int numBytes = in.read( buffer );
            while ( numBytes >= 0 ) 
            {
                digest.update( buffer, 0, numBytes );
                numBytes = in.read( buffer );
            }
            return convertChecksumToString( digest.digest() );
        } catch ( IOException e ) 
        {
            throw( new SpdxCollectionException( "IO error reading file input stream while calculating the SHA1" ) );
        } finally {
            try 
            {
                if ( in != null ) 
                {
                      in.close(); 
                }
            } catch ( IOException e ) 
            {
                throw( new SpdxCollectionException( "IO error closing file input stream while calculating the SHA1" ) );
            }
        }
    }

    public void setLog( Log log )
    {
        this.log = log;
    }
    
    private Log getLog()
    {
        return this.log;
    }
}
