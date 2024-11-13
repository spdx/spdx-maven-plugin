/**
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Source Auditor Inc.
 */
package org.spdx.maven.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.library.model.v2.enumerations.ChecksumAlgorithm;
import org.spdx.library.model.v2.enumerations.FileType;
import org.spdx.maven.Checksum;

/**
 * Collects SPDX file information from directories.
 * 
 * Concrete subclasses implement specific SPDX spec specific formats
 *
 * @author Gary O'Neall
 */
public abstract class AbstractFileCollector
{
    protected static final Logger LOG = LoggerFactory.getLogger( AbstractFileCollector.class );
    
    // constants for mapping extensions to types.
    static final String SPDX_FILE_TYPE_CONSTANTS_PROP_PATH = "resources/SpdxFileTypeConstants.prop";
    
    public static final Map<String, FileType> EXT_TO_FILE_TYPE = new HashMap<>();

    static
    {
        loadFileExtensionConstants();
    }
    
    public static final Map<String, String> CHECKSUM_ALGORITHMS = new HashMap<>();

    static
    {
        CHECKSUM_ALGORITHMS.put( ChecksumAlgorithm.SHA1.toString(), "SHA-1" );
        CHECKSUM_ALGORITHMS.put( ChecksumAlgorithm.SHA224.toString(), "SHA-224" );
        CHECKSUM_ALGORITHMS.put( ChecksumAlgorithm.SHA256.toString(), "SHA-256" );
        CHECKSUM_ALGORITHMS.put( ChecksumAlgorithm.SHA384.toString(), "SHA-384" );
        CHECKSUM_ALGORITHMS.put( ChecksumAlgorithm.SHA3_384.toString(), "SHA-512" );
        CHECKSUM_ALGORITHMS.put( ChecksumAlgorithm.MD2.toString(), "MD2" );
        CHECKSUM_ALGORITHMS.put( ChecksumAlgorithm.MD4.toString(), "MD4" );
        CHECKSUM_ALGORITHMS.put( ChecksumAlgorithm.MD5.toString(), "MD5" );
        CHECKSUM_ALGORITHMS.put( ChecksumAlgorithm.MD6.toString(), "MD6" );
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
    
    protected static FileType extensionToFileType( String fileExtension )
    {
        return EXT_TO_FILE_TYPE.getOrDefault( fileExtension.trim().toUpperCase(), FileType.OTHER );

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
            String checksumAlgorithm = CHECKSUM_ALGORITHMS.get( algorithm );

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
