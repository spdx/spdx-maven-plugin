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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

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

import javax.annotation.Nullable;


/**
 * Collects SPDX file information from directories in SPDX Spec version 2 format
 * <p>
 * The method <code>collectFilesInDirectory(FileSet[] filesets)</code> will scan and create SPDX File information for
 * all files in the filesets.
 *
 * @author Gary O'Neall
 */
public class SpdxV2FileCollector extends AbstractFileCollector
{
    private static final String DEFAULT_SHA1_VALUE = "0000000000000000000000000000000000000000";
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
     * Collect file information in the directory (including subdirectories).
     *
     * @param fileSets                FileSets containing the description of the directory to be scanned
     * @param baseDir                 project base directory used to construct the relative paths for the SPDX files
     * @param defaultFileInformation  Information on default SPDX field data for the files
     * @param pathSpecificInformation Map of path to file information used to override the default file information
     * @param relationshipType        Type of relationship to the project package
     * @param projectPackage          Package to which the files belong
     * @param spdxDoc                 SPDX document which contains the extracted license infos that may be needed for license parsing
     * @param algorithms              algorithms to use to generate checksums
     *
     * @throws SpdxCollectionException on incompatible types in an SPDX collection
     */
    @SuppressWarnings("DuplicateExpressions")
    public void collectFiles(List<FileSet> fileSets, String baseDir,
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
                String relativeFilePath = toRelativeFilePath( file, baseDir );
                SpdxDefaultFileInformation fileInfo = findDefaultFileInformation( relativeFilePath,
                        pathSpecificInformation );
                if ( fileInfo == null )
                {
                    fileInfo = defaultFileInformation;
                }
                String outputFileName = Objects.nonNull( fileSet.getOutputDirectory() ) ?
                        fileSet.getOutputDirectory() + File.separator + includedFile : relativeFilePath;
                collectFile( file, outputFileName, fileInfo, relationshipType, projectPackage, spdxDoc, algorithms );
            }
        }
    }

    /**
     * Find the most appropriate file information based on the lowest level match (closed to file)
     *
     * @param filePath                file path for possible file path specific information
     * @param pathSpecificInformation information to be applied to the file path
     * @return                        default SPDX parameters for a given file path or null if package level defaults are to be used
     */
    private @Nullable SpdxDefaultFileInformation findDefaultFileInformation(String filePath, Map<String, SpdxDefaultFileInformation> pathSpecificInformation )
    {
        LOG.debug( "Checking for file path {}", filePath );
        SpdxDefaultFileInformation retval = pathSpecificInformation.get( filePath );
        if ( retval != null )
        {
            LOG.debug( "Found filepath" );
            return retval;
        }
        // see if any of the parent directories contain default information which should be used
        String parentPath = filePath;
        int parentPathIndex;
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
            LOG.debug( "Found directory containing file path for path specific information.  File path: {}",
                    parentPath );
        }
        return retval;
    }

    /**
     * Collect SPDX information for a specific file
     *
     * @param file             File to collect SPDX information about
     * @param outputFileName   Path to the output file name relative to the root of the output archive file
     * @param relationshipType Type of relationship to the project package
     * @param projectPackage   Package to which the files belong
     * @param spdxDoc          SPDX Document which will contain the files
     * @param algorithms       algorithms to use to generate checksums
     * @throws SpdxCollectionException on incompatible type errors in an SPDX collection
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
     * @param snippet   snippet to collect SPDX information about
     * @param spdxFile  SPDX file containing the snippet
     * @param spdxDoc   SPDX document containing the SPDX file
     * @return          SPDX Snippet based on the information collected
     * @throws SpdxBuilderException         on errors building the snippet
     * @throws InvalidSPDXAnalysisException on SPDX parsing errors
     */
    private SpdxSnippet convertToSpdxSnippet( SnippetInfo snippet, SpdxFile spdxFile, SpdxDocument spdxDoc ) throws SpdxBuilderException, InvalidSPDXAnalysisException
    {
        //TODO: Add annotations to snippet
        AnyLicenseInfo concludedLicense = LicenseInfoFactory.parseSPDXLicenseStringCompatV2(  snippet.getLicenseConcluded(), spdxDoc.getModelStore(), 
                                                                                             spdxDoc.getDocumentUri(), spdxDoc.getCopyManager() );
        List<AnyLicenseInfo> licenseInfoInSnippet = new ArrayList<>();
        licenseInfoInSnippet.add( LicenseInfoFactory.parseSPDXLicenseStringCompatV2( snippet.getLicenseInfoInSnippet(), 
                                                                                     spdxDoc.getModelStore(), 
                                                                                     spdxDoc.getDocumentUri(), 
                                                                                     spdxDoc.getCopyManager() ) );
        return spdxDoc.createSpdxSnippet( IdGenerator.getIdGenerator().generateId( spdxFile.getId() + snippet.getByteRangeStart() ),
                                                        snippet.getName(), concludedLicense,
                                                        licenseInfoInSnippet,
                                                        snippet.getCopyrightText(), spdxFile, 
                                                        snippet.getByteRangeStart(), snippet.getByteRangeEnd() )
                        .setComment( snippet.getComment() )
                        .setLicenseComments( snippet.getLicenseComment() )
                        .setLineRange( snippet.getLineRangeStart(), snippet.getLineRangeEnd() )
                        .build();
    }

    /**
     * @param file                   File to be to convert to SPDX file metadata
     * @param outputFileName         Path to the output file name relative to the root of the output archive file
     * @param defaultFileInformation Information on default SPDX field data for the files
     * @param algorithms             algorithms to use to generate checksums
     * @param spdxDoc                SPDX document which will contain the SPDX file
     * @return                       SPDX file based on file and default file information
     * @throws SpdxCollectionException on incompatible class types in an SPDX collection
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
        AnyLicenseInfo concludedLicense;
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
            if ( fileSpdxLicenses != null && !fileSpdxLicenses.isEmpty())
            {
                // The file has declared licenses of the form SPDX-License-Identifier: licenseId
                try
                {
                    if ( fileSpdxLicenses.size() == 1 )
                    {
                        license = LicenseInfoFactory.parseSPDXLicenseStringCompatV2( fileSpdxLicenses.get( 0 ),
                                spdxDoc.getModelStore(),
                                spdxDoc.getDocumentUri(),
                                spdxDoc.getCopyManager() );
                    }
                    else
                    {
                        Set<AnyLicenseInfo> licenseSet = new HashSet<>();
                        for ( String licenseExpression : fileSpdxLicenses )
                        {
                            licenseSet.add( LicenseInfoFactory.parseSPDXLicenseStringCompatV2( licenseExpression,
                                    spdxDoc.getModelStore(),
                                    spdxDoc.getDocumentUri(),
                                    spdxDoc.getCopyManager() ) );
                        }
                        license = spdxDoc.createConjunctiveLicenseSet( licenseSet );
                    }
                }
                catch ( InvalidSPDXAnalysisException e )
                {
                    LOG.error( "Invalid license expressions found in source file {}", file.getName(), e );
                }
                if ( licenseComment == null )
                {
                    licenseComment = "";
                }
                else if ( !licenseComment.isEmpty() )
                {
                    licenseComment = licenseComment.concat( ";  " );
                }
                licenseComment = licenseComment.concat( "This file contains SPDX-License-Identifiers for " );
                if ( license != null )
                {
                    licenseComment = licenseComment.concat( license.toString() );
                }
            }
        }
        if ( license == null )
        {
            try
            {
                license = LicenseInfoFactory.parseSPDXLicenseStringCompatV2( defaultFileInformation.getDeclaredLicense(),
                        spdxDoc.getModelStore(),
                        spdxDoc.getDocumentUri(),
                        spdxDoc.getCopyManager() );
                concludedLicense = LicenseInfoFactory.parseSPDXLicenseStringCompatV2( defaultFileInformation.getConcludedLicense(),
                        spdxDoc.getModelStore(),
                        spdxDoc.getDocumentUri(),
                        spdxDoc.getCopyManager() );
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

        SpdxFile retval;
        //TODO: Add annotation
        try
        {
            List<AnyLicenseInfo> seenLicenses = new ArrayList<>();
            seenLicenses.add( license );
            String sha1Value = null;
            for ( Checksum checksum:checksums )
            {
                if (ChecksumAlgorithm.SHA1.toString().equals( checksum.getAlgorithm() )) {
                    sha1Value = checksum.getValue();
                    break;
                }
            }
            if ( sha1Value == null )
            {
                LOG.error( "No SHA1 checksum was found for file {}", file.getName() );
                sha1Value = DEFAULT_SHA1_VALUE;
            }
            retval = spdxDoc.createSpdxFile( IdGenerator.getIdGenerator().generateId( relativePath ),
                                             relativePath, concludedLicense, seenLicenses, 
                                             copyright, 
                                             spdxDoc.createChecksum( ChecksumAlgorithm.SHA1, sha1Value ) )
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
            excludedFileName.ifPresent(excludedFileNamesFromVerificationCode::add);
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
     * @return Generated SPDX Package Verification Code
     * @throws NoSuchAlgorithmException in the unlikely event the encryption algorithm could not be found
     * @throws InvalidSPDXAnalysisException on SPDX parsing errors
     */
    private SpdxPackageVerificationCode calculatePackageVerificationCode( Collection<SpdxFile> spdxFiles, 
                                                                          List<String> excludedFileNamesFromVerificationCode,
                                                                          SpdxDocument spdxDoc ) throws NoSuchAlgorithmException, InvalidSPDXAnalysisException
    {
        List<String> fileChecksums = new ArrayList<>();
        for ( SpdxFile file : spdxFiles )
        {
            Optional<String> filename = file.getName();
            if ( filename.isPresent() && includeInVerificationCode( filename.get(), excludedFileNamesFromVerificationCode ) )
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
}
