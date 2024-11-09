/*
 * Copyright 2014 Source Auditor Inc.
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
package org.spdx.maven.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.Map.Entry;

import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.library.LicenseInfoFactory;
import org.spdx.library.model.v2.Relationship;
import org.spdx.library.model.v2.SpdxDocument;
import org.spdx.library.model.v2.SpdxFile;
import org.spdx.library.model.v2.SpdxPackage;
import org.spdx.library.model.v2.SpdxPackageVerificationCode;
import org.spdx.library.model.v2.SpdxSnippet;
import org.spdx.library.model.v2.enumerations.ChecksumAlgorithm;
import org.spdx.library.model.v2.enumerations.FileType;
import org.spdx.library.model.v2.enumerations.RelationshipType;
import org.spdx.library.model.v2.license.AnyLicenseInfo;
import org.spdx.library.model.v2.license.InvalidLicenseStringException;
import org.spdx.maven.Checksum;
import org.spdx.maven.SnippetInfo;
import org.spdx.storage.IModelStore.IdType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Collects SPDX file information from directories.
 * <p>
 * The method <code>collectFilesInDirectory(FileSet[] filesets)</code> will scan and create SPDX File information for
 * all files in the filesets.
 *
 * @author Gary O'Neall
 */
public class SpdxV2FileCollector
{
    private static final Logger LOG = LoggerFactory.getLogger( SpdxV2FileCollector.class );

    // constants for mapping extensions to types.
    static final String SPDX_FILE_TYPE_CONSTANTS_PROP_PATH = "resources/SpdxFileTypeConstants.prop";
    
    static final Map<String, FileType> EXT_TO_FILE_TYPE = new HashMap<>();

    static
    {
        loadFileExtensionConstants();
    }

    static final Map<String, String> checksumAlgorithms = new HashMap<>();

    static
    {
        checksumAlgorithms.put( ChecksumAlgorithm.SHA1.toString(), "SHA-1" );
        checksumAlgorithms.put( ChecksumAlgorithm.SHA224.toString(), "SHA-224" );
        checksumAlgorithms.put( ChecksumAlgorithm.SHA256.toString(), "SHA-256" );
        checksumAlgorithms.put( ChecksumAlgorithm.SHA384.toString(), "SHA-384" );
        checksumAlgorithms.put( ChecksumAlgorithm.SHA3_384.toString(), "SHA-512" );
        checksumAlgorithms.put( ChecksumAlgorithm.MD2.toString(), "MD2" );
        checksumAlgorithms.put( ChecksumAlgorithm.MD4.toString(), "MD4" );
        checksumAlgorithms.put( ChecksumAlgorithm.MD5.toString(), "MD5" );
        checksumAlgorithms.put( ChecksumAlgorithm.MD6.toString(), "MD6" );
    }

    Set<AnyLicenseInfo> licensesFromFiles = new HashSet<>();
    /**
     * Map of fileName, SPDXFile for all files in the SPDX document
     */
    Map<String, SpdxFile> spdxFiles = new HashMap<>();
    List<SpdxSnippet> spdxSnippets = new ArrayList<>();

    FileSetManager fileSetManager = new FileSetManager();

    /**
     * SpdxFileCollector collects SPDX file information for files
     */
    public SpdxV2FileCollector()
    {
    }

    /**
     * Load file type constants from the properties file
     */
    private static void loadFileExtensionConstants()
    {
        Properties prop = new Properties();
        try ( InputStream is = SpdxV2FileCollector.class.getClassLoader().getResourceAsStream(
                SPDX_FILE_TYPE_CONSTANTS_PROP_PATH ) )
        {
            if ( is == null )
            {
                LOG.error( "Unable to load properties file " + SPDX_FILE_TYPE_CONSTANTS_PROP_PATH );
                return;
            }
            prop.load( is );
            Iterator<Entry<Object, Object>> iter = prop.entrySet().iterator();
            while ( iter.hasNext() ) 
            {
                Entry<Object, Object> entry = iter.next();
                String fileTypeStr = (String)entry.getKey();
                FileType fileType = FileType.valueOf( fileTypeStr );
                String[] extensions = ((String)entry.getValue()).split( "," );
                for ( String extension:extensions )
                {
                    try
                    {
                        String trimmedExtension = extension.toUpperCase().trim();
                        if ( EXT_TO_FILE_TYPE.containsKey( trimmedExtension ) )
                        {
                            LOG.warn( "Duplicate file extension: "+trimmedExtension );
                        }
                        EXT_TO_FILE_TYPE.put( trimmedExtension, fileType );
                    }
                    catch ( Exception ex ) {
                        LOG.error( "Error adding file extensions to filetype map", ex );
                    }
                }
            }
        }
        catch ( IOException e )
        {
            LOG.warn(
                    "WARNING: Error reading SpdxFileTypeConstants properties file.  All file types will be mapped to Other." );
        }
    }

    /**
     * Collect file information in the directory (including subdirectories).
     *
     * @param fileSets                FileSets containing the description of the directory to be scanned
     * @param baseDir                 project base directory used to construct the relative paths for the SPDX files
     * @param defaultFileInformation  Information on default SPDX field data for the files
     * @param pathSpecificInformation Map of path to file information used to override the default file information
     * @param relationshipType        Type of relationship to the project package
     * @param projectPackage          Package to which the files belong
     * @param spdxDoc                 SPDX document which contains the extracted license infos that may be needed for license parsing
     *
     * @throws SpdxCollectionException
     */
    public void collectFiles( List<FileSet> fileSets, String baseDir, 
                              SpdxDefaultFileInformation defaultFileInformation, 
                              Map<String, SpdxDefaultFileInformation> pathSpecificInformation, 
                              SpdxPackage projectPackage, RelationshipType relationshipType, 
                              SpdxDocument spdxDoc, Set<String> algorithms ) throws SpdxCollectionException
    {
        for ( FileSet fileSet : fileSets )
        {
            String[] includedFiles = fileSetManager.getIncludedFiles( fileSet );
            for ( String includedFile : includedFiles )
            {
                String filePath = fileSet.getDirectory() + File.separator + includedFile;
                File file = new File( filePath );
                String relativeFilePath = file.getAbsolutePath().substring( baseDir.length() + 1 ).replace( '\\', '/' );
                SpdxDefaultFileInformation fileInfo = findDefaultFileInformation( relativeFilePath,
                        pathSpecificInformation );
                if ( fileInfo == null )
                {
                    fileInfo = defaultFileInformation;
                }

                String outputFileName;
                if ( fileSet.getOutputDirectory() != null )
                {
                    outputFileName = fileSet.getOutputDirectory() + File.separator + includedFile;
                }
                else
                {
                    outputFileName = file.getAbsolutePath().substring( baseDir.length() + 1 );
                }
                collectFile( file, outputFileName, fileInfo, relationshipType, projectPackage, spdxDoc, algorithms );
            }
        }
    }

    /**
     * Find the most appropriate file information based on the lowest level match (closed to file)
     *
     * @param filePath
     * @param pathSpecificInformation
     * @return
     */
    private SpdxDefaultFileInformation findDefaultFileInformation( String filePath, Map<String, SpdxDefaultFileInformation> pathSpecificInformation )
    {
        LOG.debug( "Checking for file path " + filePath );
        SpdxDefaultFileInformation retval = pathSpecificInformation.get( filePath );
        if ( retval != null )
        {
            LOG.debug( "Found filepath" );
            return retval;
        }
        // see if any of the parent directories contain default information which should be used
        String parentPath = filePath;
        int parentPathIndex = 0;
        do
        {
            parentPathIndex = parentPath.lastIndexOf( "/" );
            if ( parentPathIndex > 0 )
            {
                parentPath = parentPath.substring( 0, parentPathIndex );
                retval = pathSpecificInformation.get( parentPath );
            }
        } while ( retval == null && parentPathIndex > 0 );
        if ( retval != null )
        {
            LOG.debug( "Found directory containing file path for path specific information.  File path: " + parentPath );
        }
        return retval;
    }

    /**
     * Collect SPDX information for a specific file
     *
     * @param file
     * @param outputFileName   Path to the output file name relative to the root of the output archive file
     * @param relationshipType Type of relationship to the project package
     * @param projectPackage   Package to which the files belong
     * @param spdxDoc          SPDX Document which will contain the files
     * @param algorithms       algorithms to use to generate checksums
     * @throws SpdxCollectionException
     */
    private void collectFile( File file, String outputFileName, SpdxDefaultFileInformation fileInfo, 
                              RelationshipType relationshipType, SpdxPackage projectPackage, 
                              SpdxDocument spdxDoc, Set<String> algorithms ) throws SpdxCollectionException
    {
        if ( spdxFiles.containsKey( file.getPath() ) )
        {
            return; // already added from a previous scan
        }
        SpdxFile spdxFile = convertToSpdxFile( file, outputFileName, fileInfo, algorithms, spdxDoc );
        try
        {
            Relationship relationship = spdxDoc.createRelationship( projectPackage, relationshipType, "" );
            spdxFile.addRelationship( relationship );
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            throw new SpdxCollectionException( "Error creating SPDX file relationship", e );
        }
        if ( fileInfo.getSnippets() != null )
        {
            for ( SnippetInfo snippet : fileInfo.getSnippets() )
            {
                SpdxSnippet spdxSnippet;
                try
                {
                    spdxSnippet = convertToSpdxSnippet( snippet, spdxFile, spdxDoc );
                }
                catch ( InvalidLicenseStringException e )
                {
                    throw new SpdxCollectionException(
                            "Error processing SPDX snippet information.  Invalid license string specified in snippet.",
                            e );
                }
                catch ( SpdxBuilderException e )
                {
                    throw new SpdxCollectionException( "Error creating SPDX snippet information.", e );
                }
                catch ( InvalidSPDXAnalysisException e )
                {
                    throw new SpdxCollectionException(
                            "Error processing SPDX snippet information.",
                            e );
                }
                spdxSnippets.add( spdxSnippet );
            }
        }
        spdxFiles.put( file.getPath(), spdxFile );
        Collection<AnyLicenseInfo> licenseInfoFromFiles;
        try
        {
            licenseInfoFromFiles = spdxFile.getLicenseInfoFromFiles();
            licensesFromFiles.addAll( licenseInfoFromFiles );
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            throw new SpdxCollectionException( "Error getting license information from files.", e );
        }
    }

    /**
     * Create an SpdxSnippet from the snippet information provided
     * @param snippet
     * @param spdxFile
     * @param spdxDoc
     * @return
     * @throws SpdxBuilderException
     * @throws InvalidSPDXAnalysisException 
     */
    private SpdxSnippet convertToSpdxSnippet( SnippetInfo snippet, SpdxFile spdxFile, SpdxDocument spdxDoc ) throws SpdxBuilderException, InvalidSPDXAnalysisException
    {
        //TODO: Add annotations to snippet
        return spdxDoc.createSpdxSnippet( spdxDoc.getModelStore().getNextId( IdType.SpdxId ),
                                                        snippet.getName(), snippet.getLicenseConcluded( spdxDoc ),
                                                        snippet.getLicenseInfoInSnippet( spdxDoc ),
                                                        snippet.getCopyrightText(), spdxFile, 
                                                        snippet.getByteRangeStart(), snippet.getByteRangeEnd() )
                        .setComment( snippet.getComment() )
                        .setLicenseComments( snippet.getLicensComment() )
                        .setLineRange( snippet.getLineRangeStart(), snippet.getLineRangeEnd() )
                        .build();
    }

    /**
     * @param file
     * @param outputFileName         Path to the output file name relative to the root of the output archive file
     * @param defaultFileInformation Information on default SPDX field data for the files
     * @param algorithms             algorithms to use to generate checksums
     * @param spdxDoc                SPDX document which will contain the SPDX file
     * @return
     * @throws SpdxCollectionException
     */
    private SpdxFile convertToSpdxFile( File file, String outputFileName, 
                                        SpdxDefaultFileInformation defaultFileInformation, 
                                        Set<String> algorithms,
                                        SpdxDocument spdxDoc ) throws SpdxCollectionException
    {
        String relativePath = convertFilePathToSpdxFileName( outputFileName );
        ArrayList<FileType> fileTypes = new ArrayList<>();
        fileTypes.add( extensionToFileType( getExtension( file ) ) );
        Set<Checksum> checksums;
        try
        {
            checksums = generateChecksum( file, algorithms );
        }
        catch ( SpdxCollectionException | InvalidSPDXAnalysisException e1 )
        {
            throw new SpdxCollectionException( "Unable to generate checksum for file "+file.getName() );
        }
        AnyLicenseInfo concludedLicense = null;
        AnyLicenseInfo license = null;
        String licenseComment = defaultFileInformation.getLicenseComment();
        if ( isSourceFile( fileTypes ) && file.length() < SpdxSourceFileParser.MAXIMUM_SOURCE_FILE_LENGTH )
        {
            List<String> fileSpdxLicenses = null;
            try
            {
                fileSpdxLicenses = SpdxSourceFileParser.parseFileForSpdxLicenses( file );
            }
            catch ( SpdxSourceParserException ex )
            {
                LOG.error( "Error parsing for SPDX license ID's", ex );
            }
            if ( fileSpdxLicenses != null && fileSpdxLicenses.size() > 0 )
            {
                // The file has declared licenses of the form SPDX-License-Identifier: licenseId
                try
                {
                    if ( fileSpdxLicenses.size() == 1 )
                    {
                        license = LicenseInfoFactory.parseSPDXLicenseStringCompatV2( fileSpdxLicenses.get( 0 ) );
                    }
                    else
                    {
                        Set<AnyLicenseInfo> licenseSet = new HashSet<>();
                        for ( String licenseExpression : fileSpdxLicenses )
                        {
                            licenseSet.add( LicenseInfoFactory.parseSPDXLicenseStringCompatV2( licenseExpression ) );
                        }
                        license = spdxDoc.createConjunctiveLicenseSet( licenseSet );
                    }
                }
                catch ( InvalidSPDXAnalysisException e )
                {
                    throw new SpdxCollectionException( "Error creating SPDX file - unable to create a license set", e );
                }
                if ( licenseComment == null )
                {
                    licenseComment = "";
                }
                else if ( licenseComment.length() > 0 )
                {
                    licenseComment = licenseComment.concat( ";  " );
                }
                licenseComment = licenseComment.concat( "This file contains SPDX-License-Identifiers for " );
                licenseComment = licenseComment.concat( license.toString() );
            }
        }
        if ( license == null )
        {
            try
            {
                license = LicenseInfoFactory.parseSPDXLicenseStringCompatV2( defaultFileInformation.getDeclaredLicense() );
                concludedLicense = LicenseInfoFactory.parseSPDXLicenseStringCompatV2( defaultFileInformation.getConcludedLicense() );
            }
            catch ( InvalidSPDXAnalysisException e )
            {
                throw new SpdxCollectionException( "Error creating SPDX file - unable create default file license", e );
            }
        }
        else
        {
            concludedLicense = license;
        }

        String copyright = defaultFileInformation.getCopyright();
        String notice = defaultFileInformation.getNotice();
        String comment = defaultFileInformation.getComment();
        String[] defaultContributors = defaultFileInformation.getContributors();
        List<String> contributors;
        if ( defaultContributors != null ) {
            contributors = Arrays.asList( defaultFileInformation.getContributors() );
        } else {
            contributors = new ArrayList<>();
        }

        SpdxFile retval = null;
        //TODO: Add annotation
        try
        {
            List<AnyLicenseInfo> seenLicenses = new ArrayList<>();
            seenLicenses.add( license );
            Checksum sha1 = null;
            for ( Checksum checksum:checksums )
            {
                if (ChecksumAlgorithm.SHA1.toString().equals( checksum.getAlgorithm() )) {
                    sha1 = checksum;
                    break;
                }
            }
            retval = spdxDoc.createSpdxFile( spdxDoc.getModelStore().getNextId( IdType.SpdxId ),
                                             relativePath, concludedLicense, seenLicenses, 
                                             copyright, 
                                             spdxDoc.createChecksum( ChecksumAlgorithm.SHA1, sha1.getValue() ) )
                            .setComment( comment )
                            .setLicenseComments( licenseComment )
                            .setFileTypes( fileTypes )
                            .setFileContributors( contributors )
                            .build();
                            

            retval.setNoticeText( notice );
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            throw new SpdxCollectionException( "Error creating SPDX file", e );
        }

        return retval;
    }

    /**
     * @param fileTypes
     * @return true if the fileTypes contain a source file type
     */
    protected boolean isSourceFile( Collection<FileType> fileTypes )
    {
        for ( FileType ft : fileTypes )
        {
            if ( ft == FileType.SOURCE )
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Create the SPDX file name from a system specific path name
     *
     * @param filePath system specific file path relative to the top of the archive root to the top of the archive
     *                 directory where the file is stored.
     * @return
     */
    public String convertFilePathToSpdxFileName( String filePath )
    {
        String result = filePath.replace( '\\', '/' );
        if ( !result.startsWith( "./" ) )
        {
            result = "./" + result;
        }
        return result;
    }

    public String getExtension( File file )
    {
        String fileName = file.getName();
        int lastDot = fileName.lastIndexOf( '.' );
        if ( lastDot < 1 )
        {
            return "";
        }
        else
        {
            return fileName.substring( lastDot + 1 );
        }
    }

    protected static FileType extensionToFileType( String fileExtension )
    {
        FileType retval = EXT_TO_FILE_TYPE.get( fileExtension.trim().toUpperCase() );
        if ( retval == null )
        {
            retval = FileType.OTHER;
        }
        return retval;
    }

    /**
     * @return SPDX Files which have been acquired through the collectFilesInDirectory method
     */
    public Collection<SpdxFile> getFiles()
    {
        return spdxFiles.values();
    }

    /**
     * @return SPDX Snippets collected through the collectFilesInDirectory method
     */
    public List<SpdxSnippet> getSnippets()
    {
        return this.spdxSnippets;
    }

    /**
     * @return all license information used in the SPDX files
     */
    public Collection<AnyLicenseInfo> getLicenseInfoFromFiles()
    {
        return licensesFromFiles;
    }

    /**
     * Create a verification code from all SPDX files collected
     *
     * @param spdxFilePath               Complete file path for the SPDX file - this will be excluded from the verification code
     * @param spdxDoc                    SPDX document which will contain the package verification code.
     * @return                           package verification code
     * @throws NoSuchAlgorithmException  on error generating checksum
     * @throws InvalidSPDXAnalysisException on SPDX parsing errors
     */
    public SpdxPackageVerificationCode getVerificationCode( String spdxFilePath, SpdxDocument spdxDoc ) throws NoSuchAlgorithmException, InvalidSPDXAnalysisException
    {
        List<String> excludedFileNamesFromVerificationCode = new ArrayList<>();

        if ( spdxFilePath != null && spdxFiles.containsKey( spdxFilePath ) )
        {
            Optional<String> excludedFileName = spdxFiles.get( spdxFilePath ).getName();
            if ( excludedFileName.isPresent() )
            {
                excludedFileNamesFromVerificationCode.add( excludedFileName.get() );
            }
        }
        SpdxPackageVerificationCode verificationCode;
        verificationCode = calculatePackageVerificationCode( spdxFiles.values(),
                excludedFileNamesFromVerificationCode, spdxDoc );
        return verificationCode;
    }

    /**
     * Calculate the package verification code for a collection of SPDX files
     *
     * @param spdxFiles                             Files used to calculate the verification code
     * @param excludedFileNamesFromVerificationCode List of file names to exclude
     * @param spdxDoc SPDX document which will contain the Package Verification Code
     * @return
     * @throws NoSuchAlgorithmException
     * @throws InvalidSPDXAnalysisException 
     */
    private SpdxPackageVerificationCode calculatePackageVerificationCode( Collection<SpdxFile> spdxFiles, 
                                                                          List<String> excludedFileNamesFromVerificationCode,
                                                                          SpdxDocument spdxDoc ) throws NoSuchAlgorithmException, InvalidSPDXAnalysisException
    {
        List<String> fileChecksums = new ArrayList<>();
        for ( SpdxFile file : spdxFiles )
        {
            Optional<String> filename = file.getName();
            if ( filename.isPresent() && includeInVerificationCode( file.getName().get(), excludedFileNamesFromVerificationCode ) )
            {
                fileChecksums.add( file.getSha1() );
            }
        }
        Collections.sort( fileChecksums );
        MessageDigest verificationCodeDigest = MessageDigest.getInstance( "SHA-1" );
        for ( String fileChecksum : fileChecksums )
        {
            byte[] hashInput = fileChecksum.getBytes( StandardCharsets.UTF_8 );
            verificationCodeDigest.update( hashInput );
        }
        String value = convertChecksumToString( verificationCodeDigest.digest() );
        return spdxDoc.createPackageVerificationCode( value, excludedFileNamesFromVerificationCode );
    }

    private boolean includeInVerificationCode( String name, List<String> excludedFileNamesFromVerificationCode )
    {
        for ( String s : excludedFileNamesFromVerificationCode )
        {
            if ( s.equals( name ) )
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Converts an array of bytes to a string compliant with the SPDX sha1 representation
     *
     * @param digestBytes
     * @return
     */
    public static String convertChecksumToString( byte[] digestBytes )
    {
        StringBuilder sb = new StringBuilder();
        for ( byte digestByte : digestBytes )
        {
            String hex = Integer.toHexString( 0xff & digestByte );
            if ( hex.length() < 2 )
            {
                sb.append( '0' );
            }
            sb.append( hex );
        }
        return sb.toString();
    }

    /**
     * Generate the Sha1 for a given file.  Must have read access to the file. This method is equivalent to calling
     * {@code SpdxFileCollector.generateChecksum(file, "SHA-1")}.
     *
     * @param file file to generate checksum for
     * @param builder Builder for the SPDX document that will contain the checksum
     * @return SHA1 checksum of the input file
     * @throws SpdxCollectionException if the algorithm is unavailable or the file cannot be read
     * @throws InvalidSPDXAnalysisException 
     */
    public static Checksum generateSha1( File file ) throws SpdxCollectionException, InvalidSPDXAnalysisException
    {
        Set<String> sha1 = new HashSet<>();
        sha1.add( "SHA-1" );
        return generateChecksum( file, sha1 ).iterator().next();
    }

    /**
     * Generate checksums for a given file using each algorithm supplied. Must have read access to the file.
     *
     * @param file       file whose checksum is to be generated
     * @param algorithms algorithms to generate the checksums
     * @return {@code Set} of checksums for file using each algorithm specified
     * @throws SpdxCollectionException if the input algorithm is invalid or unavailable or if the file cannot be read
     * @throws InvalidSPDXAnalysisException 
     */
    public static Set<Checksum> generateChecksum( File file, Set<String> algorithms ) throws SpdxCollectionException, InvalidSPDXAnalysisException
    {
        Set<Checksum> checksums = new HashSet<>();

        byte[] buffer;
        try
        {
            buffer = Files.readAllBytes( file.toPath() );
        }
        catch ( IOException e )
        {
            throw new SpdxCollectionException( "IO error while calculating checksums.", e );
        }

        for ( String algorithm : algorithms )
        {
            String checksumAlgorithm = checksumAlgorithms.get( algorithm );

            MessageDigest digest;
            try
            {
                digest = MessageDigest.getInstance( checksumAlgorithm );
            }
            catch ( NoSuchAlgorithmException e )
            {
                throw new SpdxCollectionException( e );
            }

            digest.update( buffer );
            String checksum = convertChecksumToString( digest.digest() );
            checksums.add( new Checksum( algorithm, checksum ) );
        }

        return checksums;
    }
}
