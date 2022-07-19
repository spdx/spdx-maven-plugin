package org.spdx.maven;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.shared.model.fileset.FileSet;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.ModelCopyManager;
import org.spdx.library.SpdxConstants;
import org.spdx.library.model.Checksum;
import org.spdx.library.model.Relationship;
import org.spdx.library.model.SpdxDocument;
import org.spdx.library.model.SpdxFile;
import org.spdx.library.model.SpdxPackage;
import org.spdx.library.model.SpdxPackageVerificationCode;
import org.spdx.library.model.SpdxSnippet;
import org.spdx.library.model.enumerations.ChecksumAlgorithm;
import org.spdx.library.model.enumerations.FileType;
import org.spdx.library.model.enumerations.RelationshipType;
import org.spdx.library.model.license.AnyLicenseInfo;
import org.spdx.library.model.license.LicenseInfoFactory;
import org.spdx.library.model.pointer.ByteOffsetPointer;
import org.spdx.library.model.pointer.LineCharPointer;
import org.spdx.library.model.pointer.SinglePointer;
import org.spdx.library.model.pointer.StartEndPointer;
import org.spdx.storage.simple.InMemSpdxStore;

import edu.emory.mathcs.backport.java.util.Collections;


public class TestSpdxFileCollector
{


    @BeforeClass
    public static void setUpBeforeClass() throws Exception
    {
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception
    {
    }
    private static final String TEST_SPDX_DOCUMENT_URL = "http://www.spdx.org/documents/test";
    static final String[] FILE_NAMES = new String[] {"file1.bin", "file2.c", "file3.php", "file4.zip"};
    static final String FILE_NAME_WITH_ID = "FileWithSpdxIds.java";
    static final String FILE_WITH_ID_CONTENT = "/**\n  *SPDX-License-Identifier: MIT\n  *\n  *SPDX-License-Identifier: Apache-2.0\n**/";
    static final String FILE_WITH_IDS_DECLARED_LICENSE = "(MIT AND Apache-2.0)";
    static final String FILE_WITH_IDS__CONCLUDED_LICENSE = FILE_WITH_IDS_DECLARED_LICENSE;
    static final String SNIPPET_NAMES = "Snippet Name";
    static final String[] SUB_DIRS = new String[] {"dirA", "folderB"};
    static final String[][] SUBDIR_FILES = new String[][] {new String[] {"subfile1.c", "subfile2.bin"}, new String[] {"sub2files.php"}};
    private static final String DEFAULT_COMMENT = "Default comment";
    private static final String DEFAULT_CONCLUDED_LICENSE = "Apache-2.0";
    private static final String[] DEFAULT_CONTRIBUTORS = new String[] {"Contrib1", "Contrib2"};
    private static final String DEFAULT_COPYRIGHT = "Default copyright";
    private static final String DEFAULT_DECLARED_LICENSE = "APSL-1.1";
    private static final String DEFAULT_LICENSE_COMMENT = "Default license comment";
    private static final String DEFAULT_NOTICE = "Default notice";
    private static final String DEFAULT_SNIPPET_BYTE_RANGE = "12:5234";
    private static final String DEFAULT_SNIPPET_LINE_RANGE = "88:99";
    private static final String DEFAULT_SNIPPET_COMMENT = "Snippet comment";
    private static final String DEFAULT_SNIPPET_CONCLUDED_LICENSE = "CC-BY-3.0";
    private static final String DEFAULT_SNIPPET_DECLARED_LICENSE = "LGPL-2.0";
    private static final String DEFAULT_SNIPPET_LICENSE_COMMENT = "Snippet License Comment";
    private static final String DEFAULT_SNIPPET_COPYRIGHT = "Snippet Copyright";

    private static final Set<ChecksumAlgorithm> sha1Algorithm = new HashSet<>();
    static
    {
        sha1Algorithm.add( ChecksumAlgorithm.SHA1 );
    }

    private SpdxDefaultFileInformation defaultFileInformation;
    private File directory;
    private String[] filePaths;
    private String[] SpdxFileNames;
    private FileSet[] fileSets;
    private SpdxPackage spdxPackage;
    SpdxDocument spdxDoc = null;

    @Before
    public void setUp() throws Exception
    {
        spdxDoc = new SpdxDocument( new InMemSpdxStore(), TEST_SPDX_DOCUMENT_URL, new ModelCopyManager(), true );
        this.defaultFileInformation = new SpdxDefaultFileInformation();
        this.defaultFileInformation.setComment( DEFAULT_COMMENT );
        AnyLicenseInfo concludedLicense = LicenseInfoFactory.parseSPDXLicenseString( DEFAULT_CONCLUDED_LICENSE,
                                                                                     spdxDoc.getModelStore(), spdxDoc.getDocumentUri(), spdxDoc.getCopyManager() );
        this.defaultFileInformation.setConcludedLicense( concludedLicense );
        this.defaultFileInformation.setContributors( DEFAULT_CONTRIBUTORS );
        this.defaultFileInformation.setCopyright( DEFAULT_COPYRIGHT );
        AnyLicenseInfo declaredLicense = LicenseInfoFactory.parseSPDXLicenseString( DEFAULT_DECLARED_LICENSE,
                                                                                    spdxDoc.getModelStore(), spdxDoc.getDocumentUri(), spdxDoc.getCopyManager() );
        this.defaultFileInformation.setDeclaredLicense( declaredLicense );
        this.defaultFileInformation.setLicenseComment( DEFAULT_LICENSE_COMMENT );
        this.defaultFileInformation.setNotice( DEFAULT_NOTICE );
        SnippetInfo si = new SnippetInfo();
        si.setByteRange( DEFAULT_SNIPPET_BYTE_RANGE );
        si.setComment( DEFAULT_SNIPPET_COMMENT );
        si.setConcludedLicense( DEFAULT_SNIPPET_CONCLUDED_LICENSE );
        si.setLicenseInfoInSnippet( DEFAULT_SNIPPET_DECLARED_LICENSE );
        si.setLicenseComment( DEFAULT_SNIPPET_LICENSE_COMMENT );
        si.setCopyrightText( DEFAULT_SNIPPET_COPYRIGHT );
        si.setLineRange( DEFAULT_SNIPPET_LINE_RANGE );
        si.setName( SNIPPET_NAMES );
        ArrayList<SnippetInfo> snippets = new ArrayList<>();
        snippets.add( si );
        this.defaultFileInformation.setSnippets( snippets );

        this.directory = Files.createTempDirectory( "spdxfilecollector" ).toFile();
        int numFiles = FILE_NAMES.length;
        for ( String[] subdirFile : SUBDIR_FILES )
        {
            numFiles = numFiles + subdirFile.length;
        }
        numFiles++;    // for the SPDX file with license IDs
        this.filePaths = new String[numFiles];
        this.SpdxFileNames = new String[numFiles];
        int fpi = 0;   // file path index
        for ( String fileName : FILE_NAMES )
        {
            File newFile = new File( this.directory.getPath() + File.separator + fileName );
            newFile.createNewFile();
            createUniqueContent( newFile );
            this.filePaths[fpi] = newFile.getPath();
            this.SpdxFileNames[fpi++] = "./" + this.directory.getName() + "/" + fileName;
        }
        for ( int i = 0; i < SUB_DIRS.length; i++ )
        {
            File newDir = new File( this.directory.getPath() + File.separator + SUB_DIRS[i] );
            newDir.mkdir();
            for ( int j = 0; j < SUBDIR_FILES[i].length; j++ )
            {
                File newFile = new File( newDir.getPath() + File.separator + SUBDIR_FILES[i][j] );
                newFile.createNewFile();
                createUniqueContent( newFile );
                this.filePaths[fpi] = newFile.getPath();
                this.SpdxFileNames[fpi++] = "./" + this.directory.getName() + "/" + SUB_DIRS[i] + "/" + SUBDIR_FILES[i][j];
            }
        }
        File fileWithIds = new File( this.directory.getPath() + File.separator + FILE_NAME_WITH_ID );
        fileWithIds.createNewFile();
        try ( PrintWriter writer = new PrintWriter( fileWithIds ) )
        {
            writer.print( FILE_WITH_ID_CONTENT );
        }
        this.filePaths[fpi] = fileWithIds.getPath();
        this.SpdxFileNames[fpi++] = "./" + this.directory.getName() + "/" + FILE_NAME_WITH_ID;
        Arrays.sort( this.filePaths );
        Arrays.sort( SpdxFileNames );
        FileSet dirFileSet = new FileSet();
        dirFileSet.setDirectory( directory.getPath() );
        dirFileSet.setOutputDirectory( this.directory.getName() );
        this.fileSets = new FileSet[] {dirFileSet};
        this.spdxPackage = spdxDoc.createPackage( SpdxConstants.SPDX_ELEMENT_REF_PRENUM+"test", 
                                                  "TestPackage", 
                                                  concludedLicense, 
                                                  "Package copyright",
                                                  declaredLicense )
                        .setPackageVerificationCode( spdxDoc.createPackageVerificationCode( "000102030405060708090a0b0c0d1e", new ArrayList<>() ) )
                        .setDownloadLocation( "NOASSERTION" )
                        .build();
    }

    private void createUniqueContent( File file ) throws FileNotFoundException
    {
        try ( PrintWriter writer = new PrintWriter( file ) )
        {
            writer.println( file.getPath() );
            writer.println( System.nanoTime() );
        }
    }

    @After
    public void tearDown() throws Exception
    {
        spdxDoc = null;
        deleteDirectory( this.directory );
    }

    private void deleteDirectory( File dir )
    {
        if ( dir.isFile() )
        {
            if ( !dir.delete() )
            {
                System.console().writer().println( "Unable to delete " + dir.getPath() );
            }
        }
        else if ( dir.isDirectory() )
        {
            File[] children = dir.listFiles();
            if ( children != null )
            {
                for ( File child : children )
                {
                    if ( child.isFile() )
                    {
                        if ( !child.delete() )
                        {
                            System.console().writer().println( "Unable to delete " + child.getPath() );
                        }
                    }
                    else if ( child.isDirectory() )
                    {
                        deleteDirectory( child );
                    }
                }
            }
        }
    }

    @Test
    public void testSpdxFileCollector() throws InvalidSPDXAnalysisException
    {
        SpdxFileCollector collector = new SpdxFileCollector( null );
        Collection<SpdxFile> files = collector.getFiles();
        assertEquals( 0, files.size() );
    }

    @Test
    public void testCollectFilesInDirectory() throws InvalidSPDXAnalysisException, SpdxCollectionException
    {
        SpdxFileCollector collector = new SpdxFileCollector( null );
        SpdxFile[] SpdxFiles = collector.getFiles().toArray( new SpdxFile[collector.getFiles().size()] );
        assertEquals( 0, SpdxFiles.length );

        collector.collectFiles( this.fileSets, this.directory.getAbsolutePath(), this.defaultFileInformation,
                new HashMap<>(), spdxPackage, RelationshipType.GENERATES, spdxDoc, sha1Algorithm );
        SpdxFiles = collector.getFiles().toArray( new SpdxFile[collector.getFiles().size()] );
        assertEquals( filePaths.length, SpdxFiles.length );
        Arrays.sort( SpdxFiles );
        for ( int i = 0; i < SpdxFiles.length; i++ )
        {
            Relationship[] relationships = SpdxFiles[i].getRelationships().toArray( new Relationship[SpdxFiles[i].getRelationships().size()] );
            assertEquals( SpdxFileNames[i], SpdxFiles[i].getName() );
            assertEquals( 1, relationships.length );
            assertEquals( RelationshipType.GENERATES, relationships[0].getRelationshipType() );
            assertEquals( spdxPackage.getName(), relationships[0].getRelatedSpdxElement().get().getName().get() );
        }
    }

    @Test
    public void testCollectFileInDirectoryPattern() throws SpdxCollectionException, InvalidSPDXAnalysisException
    {
        FileSet skipBin = new FileSet();
        skipBin.setDirectory( this.fileSets[0].getDirectory() );
        skipBin.addExclude( "**/*.bin" );
        skipBin.setOutputDirectory( this.fileSets[0].getOutputDirectory() );
        SpdxFileCollector collector = new SpdxFileCollector( null );
        SpdxFile[] SpdxFiles = collector.getFiles().toArray( new SpdxFile[collector.getFiles().size()] );
        assertEquals( 0, SpdxFiles.length );

        collector.collectFiles( new FileSet[] {skipBin}, this.directory.getAbsolutePath(), this.defaultFileInformation,
                new HashMap<>(), spdxPackage, RelationshipType.GENERATES, spdxDoc, sha1Algorithm );
        SpdxFiles = collector.getFiles().toArray( new SpdxFile[collector.getFiles().size()] );
        assertEquals( filePaths.length - 2, SpdxFiles.length );
        Arrays.sort( SpdxFiles );
        int SpdxFilesIndex = 0;
        for ( String spdxFileName : SpdxFileNames )
        {
            if ( spdxFileName.endsWith( ".bin" ) )
            {
                continue;
            }
            assertEquals( spdxFileName, SpdxFiles[SpdxFilesIndex++].getName().get() );
        }
    }

    @Test
    public void testGetExtension() throws InvalidSPDXAnalysisException
    {
        SpdxFileCollector collector = new SpdxFileCollector( null );
        File noExtension = new File( "noextension" );
        String result = collector.getExtension( noExtension );
        assertTrue( result.isEmpty() );
        String ext = "abcd";
        File abcd = new File( "fileName" + "." + ext );
        result = collector.getExtension( abcd );
        assertEquals( ext, result );
        File startsWithDot = new File( ".configfile" );
        result = collector.getExtension( startsWithDot );
        assertTrue( result.isEmpty() );
        File multipleDots = new File( "file.with.more.dots." + ext );
        result = collector.getExtension( multipleDots );
        assertEquals( ext, result );
    }

    @Test
    public void testGetFiles() throws SpdxCollectionException, InvalidSPDXAnalysisException
    {
        SpdxFileCollector collector = new SpdxFileCollector( null );
        SpdxFile[] SpdxFiles = collector.getFiles().toArray( new SpdxFile[collector.getFiles().size()] );
        assertEquals( 0, SpdxFiles.length );

        collector.collectFiles( this.fileSets, this.directory.getAbsolutePath(), this.defaultFileInformation,
                new HashMap<>(), spdxPackage, RelationshipType.GENERATES, spdxDoc, sha1Algorithm );
        SpdxFiles = collector.getFiles().toArray( new SpdxFile[collector.getFiles().size()] );
        assertEquals( filePaths.length, SpdxFiles.length );
        Arrays.sort( SpdxFiles );
        for ( int i = 0; i < SpdxFiles.length; i++ )
        {
            assertEquals( SpdxFileNames[i], SpdxFiles[i].getName() );
            assertEquals( DEFAULT_COMMENT, SpdxFiles[i].getComment() );
            assertEquals( DEFAULT_CONTRIBUTORS.length, SpdxFiles[i].getFileContributors().size() );
            TestLicenseManager.assertArraysEqual( DEFAULT_CONTRIBUTORS, SpdxFiles[i].getFileContributors().toArray( new String[SpdxFiles[i].getFileContributors().size()] ) );
            assertEquals( DEFAULT_COPYRIGHT, SpdxFiles[i].getCopyrightText() );
            if ( SpdxFileNames[i].endsWith( FILE_NAME_WITH_ID ) )
            {
                assertEquals( FILE_WITH_IDS_DECLARED_LICENSE, SpdxFiles[i].getLicenseInfoFromFiles().toArray( new AnyLicenseInfo[SpdxFiles[i].getLicenseInfoFromFiles().size()] )[0].toString() );
                assertTrue( SpdxFiles[i].getLicenseComments().get().contains( FILE_WITH_IDS_DECLARED_LICENSE ) );
                assertEquals( FILE_WITH_IDS__CONCLUDED_LICENSE, SpdxFiles[i].getLicenseConcluded().toString() );
            }
            else
            {
                assertEquals( DEFAULT_DECLARED_LICENSE, SpdxFiles[i].getLicenseInfoFromFiles().toArray( new AnyLicenseInfo[SpdxFiles[i].getLicenseInfoFromFiles().size()] )[0].toString() );
                assertEquals( DEFAULT_LICENSE_COMMENT, SpdxFiles[i].getLicenseComments() );
                assertEquals( DEFAULT_CONCLUDED_LICENSE, SpdxFiles[i].getLicenseConcluded().toString() );
            }
            assertEquals( DEFAULT_NOTICE, SpdxFiles[i].getNoticeText() );
        }
    }

    @Test
    public void testGetSnippets() throws SpdxCollectionException, InvalidSPDXAnalysisException
    {
        SpdxFileCollector collector = new SpdxFileCollector( null );
        List<SpdxSnippet> snippets = collector.getSnippets();
        assertEquals( 0, snippets.size() );

        collector.collectFiles( this.fileSets, this.directory.getAbsolutePath(), this.defaultFileInformation,
                new HashMap<>(), spdxPackage, RelationshipType.GENERATES, spdxDoc, sha1Algorithm );
        snippets = collector.getSnippets();
        assertEquals( filePaths.length, snippets.size() );
        Collections.sort( snippets );
        for ( SpdxSnippet snippet : snippets )
        {
            assertEquals( SNIPPET_NAMES, snippet.getName() );
            assertEquals( DEFAULT_SNIPPET_COMMENT, snippet.getComment() );
            assertEquals( DEFAULT_SNIPPET_CONCLUDED_LICENSE, snippet.getLicenseConcluded().toString() );
            assertEquals( DEFAULT_SNIPPET_COPYRIGHT, snippet.getCopyrightText() );
            assertEquals( DEFAULT_SNIPPET_DECLARED_LICENSE, snippet.getLicenseInfoFromFiles().toArray( new AnyLicenseInfo[snippet.getLicenseInfoFromFiles().size()] )[0].toString() );
            assertEquals( DEFAULT_SNIPPET_LICENSE_COMMENT, snippet.getLicenseComments() );
            assertEquals( DEFAULT_SNIPPET_BYTE_RANGE, startEndPointerToString( snippet.getByteRange() ) );
            assertEquals( DEFAULT_SNIPPET_LINE_RANGE, startEndPointerToString( snippet.getLineRange().get() ) );
        }
    }


    public static String startEndPointerToString( StartEndPointer range ) throws InvalidSPDXAnalysisException
    {
        return singlePointerToString( range.getStartPointer() ) + ":" + singlePointerToString( range.getEndPointer() );
    }

    private static String singlePointerToString( SinglePointer pointer ) throws InvalidSPDXAnalysisException
    {
        if ( pointer instanceof ByteOffsetPointer )
        {
            return String.valueOf( ( (ByteOffsetPointer) pointer ).getOffset() );
        }
        else if ( pointer instanceof LineCharPointer )
        {
            return String.valueOf( ( (LineCharPointer) pointer ).getLineNumber() );
        }
        else
        {
            return "Unknown type - can't convert pointer";
        }
    }

    @Test
    public void testCollectFilesWithPattern() throws SpdxCollectionException, InvalidSPDXAnalysisException
    {
        SpdxFileCollector collector = new SpdxFileCollector( null );
        SpdxFile[] SpdxFiles = collector.getFiles().toArray( new SpdxFile[collector.getFiles().size()] );
        assertEquals( 0, SpdxFiles.length );
        HashMap<String, SpdxDefaultFileInformation> fileSpecificInfo = new HashMap<>();
        SpdxDefaultFileInformation file2Info = new SpdxDefaultFileInformation();
        String file2Comment = "File 2 comment";
        file2Info.setComment( file2Comment );
        AnyLicenseInfo file2License = defaultFileInformation.getDeclaredLicense();
        file2Info.setConcludedLicense( file2License );
        String[] file2Contributors = new String[] {"Person: File 2 contributor"};
        file2Info.setContributors( file2Contributors );
        String file2Copyright = "File 2 copyright";
        file2Info.setCopyright( file2Copyright );
        AnyLicenseInfo file2DeclaredLicense = defaultFileInformation.getConcludedLicense();
        file2Info.setDeclaredLicense( file2DeclaredLicense );
        String file2LicenseComment = "File 2 license comment";
        file2Info.setLicenseComment( file2LicenseComment );
        String file2Notice = "file 2 notice";
        file2Info.setNotice( file2Notice );
        fileSpecificInfo.put(
                filePaths[1].substring( this.directory.getAbsolutePath().length() + 1 ).replace( '\\', '/' ),
                file2Info );

        SpdxDefaultFileInformation file3Info = new SpdxDefaultFileInformation();
        String file3Comment = "File 3 comment";
        file3Info.setComment( file3Comment );
        AnyLicenseInfo file3License = defaultFileInformation.getDeclaredLicense();
        file3Info.setConcludedLicense( file3License );
        String[] file3Contributors = new String[] {"Person: File 3 contributor"};
        file3Info.setContributors( file3Contributors );
        String file3Copyright = "File 3 copyright";
        file3Info.setCopyright( file3Copyright );
        AnyLicenseInfo file3DeclaredLicense = defaultFileInformation.getDeclaredLicense();
        file3Info.setDeclaredLicense( file3DeclaredLicense );
        String file3LicenseComment = "File 3 license comment";
        file3Info.setLicenseComment( file3LicenseComment );
        String file3Notice = "file 3 notice";
        file3Info.setNotice( file3Notice );
        fileSpecificInfo.put( SUB_DIRS[0], file3Info );

        //TODO: Test directory patterns
        collector.collectFiles( this.fileSets, this.directory.getAbsolutePath(), this.defaultFileInformation,
                fileSpecificInfo, spdxPackage, RelationshipType.GENERATES, spdxDoc, sha1Algorithm );
        SpdxFiles = collector.getFiles().toArray( new SpdxFile[collector.getFiles().size()] );
        assertEquals( filePaths.length, SpdxFiles.length );
        Arrays.sort( SpdxFiles );
        String subDirAPrefix = "./" + directory.getName() + "/" + SUB_DIRS[0];

        for ( int i = 0; i < SpdxFiles.length; i++ )
        {
            assertEquals( SpdxFileNames[i], SpdxFiles[i].getName() );
            if ( SpdxFiles[i].getName().equals( SpdxFileNames[1] ) )
            {
                assertEquals( file2Comment, SpdxFiles[1].getComment() );
                assertEquals( file2License.toString(), SpdxFiles[1].getLicenseConcluded().toString() );
                assertEquals( file2Contributors.length, SpdxFiles[1].getFileContributors().size() );
                TestLicenseManager.assertArraysEqual( file2Contributors, SpdxFiles[1].getFileContributors().toArray( new String[SpdxFiles[1].getFileContributors().size()] ) );
                assertEquals( file2Copyright, SpdxFiles[1].getCopyrightText() );
                assertEquals( file2DeclaredLicense.toString(), SpdxFiles[1].getLicenseInfoFromFiles().toArray( new AnyLicenseInfo[SpdxFiles[1].getLicenseInfoFromFiles().size()] )[0].toString() );
                assertEquals( file2LicenseComment, SpdxFiles[1].getLicenseComments() );
                assertEquals( file2Notice, SpdxFiles[1].getNoticeText() );
            }
            else if ( SpdxFiles[i].getName().get().startsWith( subDirAPrefix ) )
            {
                assertEquals( file3Comment, SpdxFiles[i].getComment() );
                assertEquals( file3License.toString(), SpdxFiles[i].getLicenseConcluded().toString() );
                assertEquals( file3Contributors.length, SpdxFiles[i].getFileContributors().size() );
                TestLicenseManager.assertArraysEqual( file3Contributors, SpdxFiles[i].getFileContributors().toArray( new String[SpdxFiles[i].getFileContributors().size()] ) );
                assertEquals( file3Copyright, SpdxFiles[i].getCopyrightText() );
                assertEquals( file3DeclaredLicense.toString(), SpdxFiles[i].getLicenseInfoFromFiles().toArray( new AnyLicenseInfo[SpdxFiles[i].getLicenseInfoFromFiles().size()] )[0].toString() );
                assertEquals( file3LicenseComment, SpdxFiles[i].getLicenseComments() );
                assertEquals( file3Notice, SpdxFiles[i].getNoticeText() );
            }
            else if ( !SpdxFiles[i].getName().get().endsWith( FILE_NAME_WITH_ID ) )
            {
                assertEquals( DEFAULT_COMMENT, SpdxFiles[i].getComment() );
                assertEquals( DEFAULT_CONCLUDED_LICENSE, SpdxFiles[i].getLicenseConcluded().toString() );
                assertEquals( DEFAULT_CONTRIBUTORS.length, SpdxFiles[i].getFileContributors().size() );
                TestLicenseManager.assertArraysEqual( DEFAULT_CONTRIBUTORS, SpdxFiles[i].getFileContributors().toArray( new String[SpdxFiles[i].getFileContributors().size()] ) );
                assertEquals( DEFAULT_COPYRIGHT, SpdxFiles[i].getCopyrightText() );
                assertEquals( DEFAULT_DECLARED_LICENSE, SpdxFiles[i].getLicenseInfoFromFiles().toArray( new AnyLicenseInfo[SpdxFiles[i].getLicenseInfoFromFiles().size()] )[0].toString() );
                assertEquals( DEFAULT_LICENSE_COMMENT, SpdxFiles[i].getLicenseComments() );
                assertEquals( DEFAULT_NOTICE, SpdxFiles[i].getNoticeText() );
            }
        }
    }

    @Test
    public void testGetLicenseInfoFromFiles() throws SpdxCollectionException, IOException, InvalidSPDXAnalysisException
    {
        SpdxFileCollector collector = new SpdxFileCollector( null );
        AnyLicenseInfo[] result = collector.getLicenseInfoFromFiles().toArray( new AnyLicenseInfo[collector.getLicenseInfoFromFiles().size()] );
        assertEquals( 0, result.length );

        collector.collectFiles( this.fileSets, this.directory.getAbsolutePath(), this.defaultFileInformation,
                new HashMap<>(), spdxPackage, RelationshipType.GENERATES, spdxDoc
                , sha1Algorithm );
        result = collector.getLicenseInfoFromFiles().toArray( new AnyLicenseInfo[collector.getLicenseInfoFromFiles().size()] );
        assertEquals( 2, result.length );
        if ( DEFAULT_DECLARED_LICENSE.equals( result[0].toString() ) )
        {
            assertEquals( FILE_WITH_IDS_DECLARED_LICENSE, result[1].toString() );
        }
        else
        {
            assertEquals( DEFAULT_DECLARED_LICENSE, result[1].toString() );
            assertEquals( FILE_WITH_IDS_DECLARED_LICENSE, result[0].toString() );
        }

        File tempDir2 = Files.createTempDirectory( "TempTest" ).toFile();
        try
        {
            File oneMoreFile = new File( tempDir2.getPath() + File.separator + "oneMore.c" );
            oneMoreFile.createNewFile();
            createUniqueContent( oneMoreFile );
            SpdxDefaultFileInformation info2 = new SpdxDefaultFileInformation();
            info2.setConcludedLicense( this.defaultFileInformation.getConcludedLicense() );
            info2.setCopyright( this.defaultFileInformation.getCopyright() );
            String newLicenseName = "LicenseRef-newLicense";
            AnyLicenseInfo newDeclaredLicense = LicenseInfoFactory.parseSPDXLicenseString( newLicenseName );
            info2.setDeclaredLicense( newDeclaredLicense );
            FileSet fileSet2 = new FileSet();
            fileSet2.setDirectory( tempDir2.getPath() );

            collector.collectFiles( new FileSet[] {fileSet2}, this.directory.getAbsolutePath(), info2, new HashMap<>(),
                    spdxPackage, RelationshipType.GENERATES, spdxDoc, sha1Algorithm );
            result = collector.getLicenseInfoFromFiles().toArray( new AnyLicenseInfo[collector.getLicenseInfoFromFiles().size()] );
            assertEquals( 3, result.length );
            boolean foundDefault = false;
            boolean foundFileWithIds = false;
            boolean foundNewLicense = false;
            for ( AnyLicenseInfo lic : result )
            {
                if ( lic.toString().equals( DEFAULT_DECLARED_LICENSE ) )
                {
                    foundDefault = true;
                }
                if ( lic.toString().equals( FILE_WITH_IDS_DECLARED_LICENSE ) )
                {
                    foundFileWithIds = true;
                }
                if ( lic.toString().equals( newLicenseName ) )
                {
                    foundNewLicense = true;
                }
            }
            assertTrue( foundDefault );
            assertTrue( foundFileWithIds );
            assertTrue( foundNewLicense );
        }
        finally
        {
            deleteDirectory( tempDir2 );
        }
    }

    @Test
    public void testGetVerificationCode() throws SpdxCollectionException, NoSuchAlgorithmException, InvalidSPDXAnalysisException
    {
        SpdxFileCollector collector = new SpdxFileCollector( null );
        SpdxFile[] SpdxFiles = collector.getFiles().toArray( new SpdxFile[collector.getFiles().size()] );
        assertEquals( 0, SpdxFiles.length );

        collector.collectFiles( this.fileSets, this.directory.getAbsolutePath(), this.defaultFileInformation,
                new HashMap<>(), spdxPackage, RelationshipType.GENERATES, spdxDoc, sha1Algorithm );
        File SpdxFile = new File( filePaths[0] );
        SpdxPackageVerificationCode result = collector.getVerificationCode( SpdxFile.getPath(), spdxDoc );
        assertTrue( !result.getValue().isEmpty() );
        assertEquals( 1, result.getExcludedFileNames().size() );
        assertEquals( SpdxFileNames[0], result.getExcludedFileNames().toArray( new String[result.getExcludedFileNames().size()] )[0] );
    }

    @Test
    public void testConvertChecksumToString()
    {
        byte[] cksumBytes = new byte[] {00, 01, 02, 03, 04, 05, 06, 07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x1E};
        String expected = "000102030405060708090a0b0c0d1e";
        String result = SpdxFileCollector.convertChecksumToString( cksumBytes );
        assertEquals( expected, result );
    }

    @Test
    public void testConvertFilePathToSpdxFileName()
    {
        String dosFilePath = "subdir\\subdir2\\file.c";
        String dosSpdxFile = "./subdir/subdir2/file.c";
        SpdxFileCollector collector = new SpdxFileCollector( null );
        String result = collector.convertFilePathToSpdxFileName( dosFilePath );
        assertEquals( dosSpdxFile, result );

        String unixFilePath = "unixFolder/subfolder/file.sh";
        String unixSpdxFile = "./unixFolder/subfolder/file.sh";
        result = collector.convertFilePathToSpdxFileName( unixFilePath );
        assertEquals( unixSpdxFile, result );
    }

    @Test
    public void testIsSourceFile()
    {
        SpdxFileCollector collector = new SpdxFileCollector( null );
        assertTrue( collector.isSourceFile( toFileTypeCollection( new FileType[] {FileType.SOURCE} ) ) );
        assertTrue( collector.isSourceFile( toFileTypeCollection( new FileType[] {FileType.TEXT, FileType.SOURCE} ) ) );
        assertFalse( collector.isSourceFile( toFileTypeCollection( new FileType[] {FileType.BINARY, FileType.IMAGE} ) ) );
    }

    /**
     * @param fileTypes
     * @return
     */
    private Collection<FileType> toFileTypeCollection( FileType[] fileTypes )
    {
        ArrayList<FileType> retval = new ArrayList<>();
        for (FileType ft:fileTypes) {
            retval.add( ft );
        }
        return retval;
    }

    @Test
    public void testGenerateChecksums() throws SpdxCollectionException, InvalidSPDXAnalysisException
    {
        SpdxFileCollector collector = new SpdxFileCollector( null );
        collector.collectFiles( this.fileSets, this.directory.getAbsolutePath(), this.defaultFileInformation,
                new HashMap<>(), spdxPackage, RelationshipType.GENERATES, spdxDoc, sha1Algorithm );
        File spdxFile = new File( filePaths[0] );

        Set<ChecksumAlgorithm> checksumAlgorithmSet = new HashSet<>();
        checksumAlgorithmSet.add(ChecksumAlgorithm.SHA1 );
        checksumAlgorithmSet.add( ChecksumAlgorithm.SHA256 );

        Set<Checksum> expectedChecksums = new HashSet<>();
        expectedChecksums.add( spdxDoc.createChecksum( ChecksumAlgorithm.SHA1, "1834453c87b9188024c7b18d179eb64f95f29fcf" ) );
        expectedChecksums.add( spdxDoc.createChecksum( ChecksumAlgorithm.SHA256,  "1c94046c63f61f5dbe5c15cc6c4e34510132ab262aa266735b344d836ef8cb3c") );
        // Checksum does not override equals currently. Hence, need to compare manually
        Set<Checksum> actualChecksums = SpdxFileCollector.generateChecksum( spdxFile, checksumAlgorithmSet, spdxDoc );
        for ( Checksum expectedChecksum : expectedChecksums )
        {
            boolean found = false;
            for ( Checksum actualChecksum : actualChecksums )
            {
                if ( expectedChecksum.getAlgorithm().equals( actualChecksum.getAlgorithm() ) )
                {
                    if ( expectedChecksum.getValue().equals( actualChecksum.getValue() ) )
                    {
                        found = true;
                    }
                    else
                    {
                        fail("Expected checksum : " + expectedChecksum + "does not match actual checksum : " + actualChecksum);
                    }
                }
            }
            if ( !found )
            {
                fail("Expected checksum : " + expectedChecksum + "not found in actual checksums : " + actualChecksums);
            }
        }
    }
}
