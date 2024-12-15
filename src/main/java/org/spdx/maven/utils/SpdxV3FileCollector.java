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
import java.util.*;
import java.util.Map.Entry;

import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.library.LicenseInfoFactory;
import org.spdx.library.conversion.Spdx2to3Converter;
import org.spdx.library.model.v2.enumerations.ChecksumAlgorithm;
import org.spdx.library.model.v2.enumerations.FileType;
import org.spdx.library.model.v3_0_1.core.Agent;
import org.spdx.library.model.v3_0_1.core.DictionaryEntry;
import org.spdx.library.model.v3_0_1.core.HashAlgorithm;
import org.spdx.library.model.v3_0_1.core.IntegrityMethod;
import org.spdx.library.model.v3_0_1.core.PositiveIntegerRange;
import org.spdx.library.model.v3_0_1.core.RelationshipCompleteness;
import org.spdx.library.model.v3_0_1.core.RelationshipType;
import org.spdx.library.model.v3_0_1.core.SpdxDocument;
import org.spdx.library.model.v3_0_1.simplelicensing.AnyLicenseInfo;
import org.spdx.library.model.v3_0_1.software.Snippet;
import org.spdx.library.model.v3_0_1.software.SoftwarePurpose;
import org.spdx.library.model.v3_0_1.software.SpdxFile;
import org.spdx.library.model.v3_0_1.software.SpdxPackage;
import org.spdx.maven.Checksum;
import org.spdx.maven.SnippetInfo;
import org.spdx.storage.IModelStore.IdType;


/**
 * Collects SPDX file information from directories in SPDX Spec version 3 format
 * <p>
 * The method <code>collectFilesInDirectory(FileSet[] filesets)</code> will scan and create SPDX File information for
 * all files in the filesets.
 *
 * @author Gary O'Neall
 */
public class SpdxV3FileCollector extends AbstractFileCollector
{
    static final Map<String, String> EXT_TO_MEDIA_TYPE = new HashMap<>();
    static final Map<String, SoftwarePurpose> EXT_TO_PURPOSE = new HashMap<>();

    static
    {
        for ( Entry<String, FileType> entry : SpdxV2FileCollector.EXT_TO_FILE_TYPE.entrySet() )
        {
            switch ( entry.getValue() )
            {
                case SOURCE:    EXT_TO_MEDIA_TYPE.put( entry.getKey(), "text/plain" );
                                EXT_TO_PURPOSE.put( entry.getKey(), SoftwarePurpose.SOURCE );
                                break;
                case BINARY:    EXT_TO_MEDIA_TYPE.put( entry.getKey(), "application/octet-stream" );
                                EXT_TO_PURPOSE.put( entry.getKey(), SoftwarePurpose.LIBRARY );
                                break;
                case ARCHIVE:   EXT_TO_PURPOSE.put( entry.getKey(), SoftwarePurpose.ARCHIVE ); break;
                case APPLICATION: EXT_TO_PURPOSE.put( entry.getKey(), SoftwarePurpose.APPLICATION ); break;
                case AUDIO:     EXT_TO_MEDIA_TYPE.put( entry.getKey(), "audio/*" ); break;
                case IMAGE:     EXT_TO_MEDIA_TYPE.put( entry.getKey(), "image/*" ); break;
                case TEXT:      EXT_TO_MEDIA_TYPE.put( entry.getKey(), "text/plain" ); break;
                case VIDEO:     EXT_TO_MEDIA_TYPE.put( entry.getKey(), "video/*" ); break;
                case DOCUMENTATION: EXT_TO_PURPOSE.put( entry.getKey(), SoftwarePurpose.DOCUMENTATION ); break;
                case SPDX:      EXT_TO_MEDIA_TYPE.put( entry.getKey(), "application/spdx" );
                                EXT_TO_PURPOSE.put( entry.getKey(), SoftwarePurpose.BOM ); break;
                case OTHER:     EXT_TO_PURPOSE.put( entry.getKey(), SoftwarePurpose.OTHER ); break;
            }
        }
    }

    /**
     * Map of fileName, SPDXFile for all files in the SPDX document
     */
    Map<String, SpdxFile> spdxFiles = new HashMap<>();
    List<Snippet> spdxSnippets = new ArrayList<>();

    FileSetManager fileSetManager = new FileSetManager();

    private final List<DictionaryEntry> customIdToUri;

    /**
     * SpdxFileCollector collects SPDX file information for files
     * @param customIdToUri Holds a mapping of IDs to URIs for any custom licenses defined outside the spdxDoc
     */
    public SpdxV3FileCollector( List<DictionaryEntry> customIdToUri)
    {
        this.customIdToUri = customIdToUri;
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
     * @throws SpdxCollectionException on incompatible types in an SPDX collection
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
     * @param filePath                file path for possible file path specific information
     * @param pathSpecificInformation information to be applied to the file path
     * @return                        default SPDX parameters for a given file path or null if package level defaults are to be used
     */
    private SpdxDefaultFileInformation findDefaultFileInformation( String filePath, Map<String, SpdxDefaultFileInformation> pathSpecificInformation )
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
            LOG.debug( "Found directory containing file path for path specific information.  File path: {}", parentPath );
        }
        return retval;
    }

    /**
     * Collect SPDX information for a specific file
     *
     * @param file             File to collect SPDX information for
     * @param outputFileName   Path to the output file name relative to the root of the output archive file
     * @param relationshipType Type of relationship to the project package
     * @param projectPackage   Package to which the files belong
     * @param spdxDoc          SPDX Document which will contain the files
     * @param algorithms       algorithms to use to generate checksums
     * @throws SpdxCollectionException on incompatible types in an SPDX collection
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
            spdxDoc.createRelationship( spdxDoc.getIdPrefix() + spdxDoc.getModelStore().getNextId( IdType.SpdxId ) )
                                .setFrom( spdxFile )
                                .addTo( projectPackage )
                                .setRelationshipType( relationshipType )
                                .build();
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            throw new SpdxCollectionException( "Error creating SPDX file relationship", e );
        }
        if ( fileInfo.getSnippets() != null )
        {
            for ( SnippetInfo snippet : fileInfo.getSnippets() )
            {
                Snippet spdxSnippet;
                try
                {
                    spdxSnippet = convertToSpdxSnippet( snippet, spdxFile );
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
    }

    /**
     * Create an SpdxSnippet from the snippet information provided
     * @param snippet    Information on the Snippet
     * @param spdxFile   File containing the snippet
     * @return           SPDX Snippet
     * @throws SpdxBuilderException on Maven metadata related errors
     * @throws InvalidSPDXAnalysisException on SPDX analysis errors
     */
    private Snippet convertToSpdxSnippet( SnippetInfo snippet, SpdxFile spdxFile ) throws SpdxBuilderException, InvalidSPDXAnalysisException
    {
        //TODO: Add annotations to snippet

        PositiveIntegerRange byteRange = spdxFile.createPositiveIntegerRange( spdxFile.getModelStore().getNextId( IdType.Anonymous ) )
                        .setBeginIntegerRange( snippet.getByteRangeStart() )
                        .setEndIntegerRange( snippet.getByteRangeEnd() )
                        .build();
        PositiveIntegerRange lineRange = spdxFile.createPositiveIntegerRange( spdxFile.getModelStore().getNextId( IdType.Anonymous ) )
                        .setBeginIntegerRange( snippet.getLineRangeStart() )
                        .setEndIntegerRange( snippet.getLineRangeEnd() )
                        .build();
        
        Snippet retval =  spdxFile.createSnippet( spdxFile.getIdPrefix() + spdxFile.getModelStore().getNextId( IdType.SpdxId ) )
                        .setName( snippet.getName() )
                        .setCopyrightText( snippet.getCopyrightText() )
                        .setSnippetFromFile( spdxFile )
                        .setByteRange( byteRange )
                        .setLineRange( lineRange )
                        .build();

        String comment = snippet.getComment();
        if ( Objects.isNull( comment ) )
        {
            comment = "";
        }
        String licenseComment = snippet.getLicensComment();
        if ( Objects.nonNull( licenseComment ) && !licenseComment.isBlank() )
        {
            comment = comment + "; License: " + licenseComment;
        }
        retval.setComment( comment );
        final AnyLicenseInfo concludedLicense = LicenseInfoFactory
                        .parseSPDXLicenseString( snippet.getConcludedLicense(), spdxFile.getModelStore(), 
                                                 spdxFile.getIdPrefix(), spdxFile.getCopyManager(), customIdToUri );
        retval.createRelationship( retval.getIdPrefix() + retval.getModelStore().getNextId( IdType.SpdxId ) )
                        .setCompleteness( RelationshipCompleteness.COMPLETE )
                        .setFrom( retval )
                        .addTo( concludedLicense )
                        .setRelationshipType( RelationshipType.HAS_CONCLUDED_LICENSE )
                        .build();
        
        final AnyLicenseInfo declaredLicense = LicenseInfoFactory
                        .parseSPDXLicenseString( snippet.getLicenseInfoInSnippet(), spdxFile.getModelStore(), 
                                                 spdxFile.getIdPrefix(), spdxFile.getCopyManager(), customIdToUri );
        retval.createRelationship( retval.getIdPrefix() + retval.getModelStore().getNextId( IdType.SpdxId ) )
                        .setCompleteness( RelationshipCompleteness.COMPLETE )
                        .setFrom( retval )
                        .addTo( declaredLicense )
                        .setRelationshipType( RelationshipType.HAS_DECLARED_LICENSE )
                        .build();
        return retval;
    }

    /**
     * @param file                   File to convert to an SPDX file from
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
        String extension = getExtension( file ).trim().toUpperCase();
        SoftwarePurpose purpose = EXT_TO_PURPOSE.getOrDefault( extension, SoftwarePurpose.OTHER );
        Collection<IntegrityMethod> hashes = new ArrayList<>();
        try
        {
            Set<Checksum> checksums = generateChecksum( file, algorithms );
            for ( Checksum checksum : checksums )
            {
                final HashAlgorithm algorithm = Spdx2to3Converter.HASH_ALGORITH_MAP.get( ChecksumAlgorithm.valueOf( checksum.getAlgorithm() ) );
                if ( Objects.isNull( algorithm ) )
                {
                    throw new SpdxCollectionException( "Invalid checksum algorithm for file "+file.getName() );
                }
                hashes.add( spdxDoc.createHash( spdxDoc.getModelStore().getNextId( IdType.Anonymous ) )
                            .setAlgorithm( algorithm )
                            .setHashValue( checksum.getValue() )
                            .build() );
            }
            
        }
        catch ( SpdxCollectionException | InvalidSPDXAnalysisException e1 )
        {
            throw new SpdxCollectionException( "Unable to generate checksum for file "+file.getName() );
        }
        AnyLicenseInfo concludedLicense;
        AnyLicenseInfo license = null;
        String licenseComment = defaultFileInformation.getLicenseComment();
        if ( SoftwarePurpose.SOURCE.equals( purpose ) && file.length() < SpdxSourceFileParser.MAXIMUM_SOURCE_FILE_LENGTH )
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
            if ( fileSpdxLicenses != null && !fileSpdxLicenses.isEmpty() )
            {
                // The file has declared licenses of the form SPDX-License-Identifier: licenseId
                try
                {
                    if ( fileSpdxLicenses.size() == 1 )
                    {
                        license = LicenseInfoFactory.parseSPDXLicenseString( fileSpdxLicenses.get( 0 ),
                                spdxDoc.getModelStore(), spdxDoc.getIdPrefix(), spdxDoc.getCopyManager(), customIdToUri );
                    }
                    else
                    {
                        Set<AnyLicenseInfo> licenseSet = new HashSet<>();
                        for ( String licenseExpression : fileSpdxLicenses )
                        {
                            licenseSet.add( LicenseInfoFactory.parseSPDXLicenseString( licenseExpression,
                                    spdxDoc.getModelStore(), spdxDoc.getIdPrefix(), spdxDoc.getCopyManager(), customIdToUri ) );
                        }
                        license = spdxDoc.createConjunctiveLicenseSet( spdxDoc.getIdPrefix() + spdxDoc.getModelStore().getNextId( IdType.SpdxId ) )
                                        .addAllMember( licenseSet )
                                        .build();
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
                license = LicenseInfoFactory.parseSPDXLicenseString( defaultFileInformation.getDeclaredLicense(),
                        spdxDoc.getModelStore(), spdxDoc.getIdPrefix(), spdxDoc.getCopyManager(), customIdToUri );
                concludedLicense = LicenseInfoFactory.parseSPDXLicenseString( defaultFileInformation.getConcludedLicense(),
                        spdxDoc.getModelStore(), spdxDoc.getIdPrefix(), spdxDoc.getCopyManager(), customIdToUri );
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
        if ( Objects.isNull( comment ) )
        {
            comment = "";
        }
        if ( Objects.nonNull( licenseComment ) && !licenseComment.isBlank() )
        {
            comment = comment + " ;License: " + licenseComment;
        }
        String[] defaultContributors = defaultFileInformation.getContributors();
        List<Agent> contributors = new ArrayList<>();
        if ( defaultContributors != null ) {
            for ( String contributor : defaultFileInformation.getContributors() )
            {
                if ( Objects.nonNull( contributor ) && !contributor.isBlank() )
                {
                    try
                    {
                        contributors.add( spdxDoc.createPerson( spdxDoc.getModelStore().getNextId( IdType.Anonymous ) )
                                          .setName( contributor )
                                          .setDescription( "Contributor" )
                                          .build() );
                    }
                    catch ( InvalidSPDXAnalysisException e )
                    {
                        LOG.warn( "Error creating contributor {} for file {}.  Skipping.", contributor, file );
                    }
                }
            }
        } else {
            contributors = new ArrayList<>();
        }

        SpdxFile retval;
        //TODO: Add annotation
        try
        {
            retval = spdxDoc.createSpdxFile( spdxDoc.getIdPrefix() + spdxDoc.getModelStore().getNextId( IdType.SpdxId ) )
                            .setName( relativePath )
                            .setCopyrightText( copyright )
                            .setComment( comment )
                            .setPrimaryPurpose( purpose )
                            .addAllVerifiedUsing( hashes )
                            .addAttributionText( notice )
                            .addAllOriginatedBy( contributors )
                            .build();
            String mediaType = EXT_TO_MEDIA_TYPE.get( extension );
            if ( Objects.nonNull( mediaType ) )
            {
                retval.setContentType( mediaType );
            }
            retval.createRelationship( retval.getIdPrefix() + retval.getModelStore().getNextId( IdType.SpdxId ) )
                                        .setCompleteness( RelationshipCompleteness.COMPLETE )
                                        .setFrom( retval )
                                        .addTo( concludedLicense )
                                        .setRelationshipType( RelationshipType.HAS_CONCLUDED_LICENSE )
                                        .build();
            retval.createRelationship( retval.getIdPrefix() + retval.getModelStore().getNextId( IdType.SpdxId ) )
                                        .setCompleteness( RelationshipCompleteness.COMPLETE )
                                        .setFrom( retval )
                                        .addTo( license )
                                        .setRelationshipType( RelationshipType.HAS_DECLARED_LICENSE )
                                        .build();
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
    public List<Snippet> getSnippets()
    {
        return this.spdxSnippets;
    }
}
