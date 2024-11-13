package org.spdx.maven.utils;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.shared.model.fileset.FileSet;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.spdx.core.DefaultModelStore;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.library.LicenseInfoFactory;
import org.spdx.library.ModelCopyManager;
import org.spdx.library.SpdxModelFactory;
import org.spdx.library.model.v2.SpdxConstantsCompatV2;
import org.spdx.library.model.v2.enumerations.ChecksumAlgorithm;
import org.spdx.library.model.v3_0_1.SpdxConstantsV3;
import org.spdx.library.model.v3_0_1.core.DictionaryEntry;
import org.spdx.library.model.v3_0_1.core.Element;
import org.spdx.library.model.v3_0_1.core.PositiveIntegerRange;
import org.spdx.library.model.v3_0_1.core.Relationship;
import org.spdx.library.model.v3_0_1.core.RelationshipType;
import org.spdx.library.model.v3_0_1.core.SpdxDocument;
import org.spdx.library.model.v3_0_1.simplelicensing.AnyLicenseInfo;
import org.spdx.library.model.v3_0_1.software.Snippet;
import org.spdx.library.model.v3_0_1.software.SpdxFile;
import org.spdx.library.model.v3_0_1.software.SpdxPackage;
import org.spdx.maven.Checksum;
import org.spdx.maven.SnippetInfo;
import org.spdx.storage.simple.InMemSpdxStore;


public class TestSpdxV3FileCollector
{


    @BeforeClass
    public static void setUpBeforeClass() throws Exception
    {
        SpdxModelFactory.init();
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

    private static final Set<String> sha1Algorithm = new HashSet<>();
    static
    {
        sha1Algorithm.add( ChecksumAlgorithm.SHA1.toString() );
    }

    private SpdxDefaultFileInformation defaultFileInformation;
    private File directory;
    private String[] filePaths;
    private String[] SpdxFileNames;
    private List<FileSet> fileSets;
    private SpdxPackage spdxPackage;
    private List<DictionaryEntry> customIdMap = new ArrayList<>();
    SpdxDocument spdxDoc = null;
    Comparator<Element> elementComparer = new Comparator<Element>() {

        @Override
        public int compare( Element o1, Element o2 )
        {
            try
            {
                return o1.getName().orElse( "" ).compareTo(o2.getName().orElse( "" ));
            }
            catch ( InvalidSPDXAnalysisException e )
            {
                fail( "Error getting element names" );
                return 0;
            }
        }
        
    };

    @Before
    public void setUp() throws Exception
    {
        DefaultModelStore.initialize(new InMemSpdxStore(), "http://default/namespace", new ModelCopyManager());
        spdxDoc = new SpdxDocument( new InMemSpdxStore(), TEST_SPDX_DOCUMENT_URL + "/Document", new ModelCopyManager(), true,  TEST_SPDX_DOCUMENT_URL + "/" );
        this.defaultFileInformation = new SpdxDefaultFileInformation();
        this.defaultFileInformation.setComment( DEFAULT_COMMENT );
        AnyLicenseInfo concludedLicense = LicenseInfoFactory.parseSPDXLicenseString( DEFAULT_CONCLUDED_LICENSE, spdxDoc.getModelStore(), 
                                                                                     spdxDoc.getIdPrefix(), spdxDoc.getCopyManager(), customIdMap );
        this.defaultFileInformation.setConcludedLicense( DEFAULT_CONCLUDED_LICENSE );
        this.defaultFileInformation.setContributors( DEFAULT_CONTRIBUTORS );
        this.defaultFileInformation.setCopyright( DEFAULT_COPYRIGHT );
        AnyLicenseInfo declaredLicense = LicenseInfoFactory.parseSPDXLicenseString( DEFAULT_DECLARED_LICENSE,
                                                                                    spdxDoc.getModelStore(), spdxDoc.getIdPrefix(), 
                                                                                    spdxDoc.getCopyManager(), customIdMap );
        this.defaultFileInformation.setDeclaredLicense( DEFAULT_DECLARED_LICENSE );
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

        this.directory = Files.createTempDirectory( "SpdxV3FileCollector" ).toFile();
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
        this.fileSets = Arrays.asList( dirFileSet );
        this.spdxPackage = spdxDoc.createSpdxPackage( spdxDoc.getIdPrefix() +  SpdxConstantsCompatV2.SPDX_ELEMENT_REF_PRENUM+"test" )
                        .setName( "TestPackage" )
                        .setCopyrightText( "Package copyright" )
                        .setDownloadLocation( "NOASSERTION" )
                        .build();
        spdxDoc.createRelationship( spdxDoc.getIdPrefix() + SpdxConstantsCompatV2.SPDX_ELEMENT_REF_PRENUM+"pkgdeclared" )
                        .addTo( declaredLicense )
                        .setFrom( spdxPackage )
                        .setRelationshipType( RelationshipType.HAS_DECLARED_LICENSE )
                        .build();
        spdxDoc.createRelationship( spdxDoc.getIdPrefix() + SpdxConstantsCompatV2.SPDX_ELEMENT_REF_PRENUM+"pkgconcluded" )
                        .addTo( concludedLicense )
                        .setFrom( spdxPackage )
                        .setRelationshipType( RelationshipType.HAS_CONCLUDED_LICENSE )
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
    public void testSpdxV3FileCollector() throws InvalidSPDXAnalysisException
    {
        SpdxV3FileCollector collector = new SpdxV3FileCollector( customIdMap );
        Collection<SpdxFile> files = collector.getFiles();
        assertEquals( 0, files.size() );
    }

    @Test
    public void testCollectFilesInDirectory() throws InvalidSPDXAnalysisException, SpdxCollectionException
    {
        SpdxV3FileCollector collector = new SpdxV3FileCollector( customIdMap );
        SpdxFile[] spdxFiles = collector.getFiles().toArray( new SpdxFile[collector.getFiles().size()] );
        assertEquals( 0, spdxFiles.length );

        collector.collectFiles( this.fileSets, this.directory.getAbsolutePath(), this.defaultFileInformation,
                new HashMap<>(), spdxPackage, RelationshipType.GENERATES, spdxDoc, sha1Algorithm );
        spdxFiles = collector.getFiles().toArray( new SpdxFile[collector.getFiles().size()] );
        assertEquals( filePaths.length, spdxFiles.length );
        Arrays.sort( spdxFiles, elementComparer );
        @SuppressWarnings( "unchecked" )
        List<Relationship> allRelationships = (List<Relationship>)SpdxModelFactory
                        .getSpdxObjects( spdxDoc.getModelStore(), 
                                         spdxDoc.getCopyManager(), 
                                         SpdxConstantsV3.CORE_RELATIONSHIP, 
                                         null, spdxDoc.getIdPrefix() ).collect( Collectors.toList() );
        for ( int i = 0; i < spdxFiles.length; i++ )
        {
            boolean foundGenerates = false;
            for ( Relationship relationship : allRelationships )
            {
                if ( relationship.getFrom().equals( spdxFiles[i] ) && 
                                RelationshipType.GENERATES.equals( relationship.getRelationshipType() ) )
                {
                    Element[] tos = relationship.getTos().toArray( new Element[relationship.getTos().size()] );
                    assertEquals( 1, tos.length );
                    assertEquals( spdxPackage, tos[0] );
                    foundGenerates = true;
                    break;
                }
            }
            assertTrue( foundGenerates );
        }
    }

    @Test
    public void testCollectFileInDirectoryPattern() throws SpdxCollectionException, InvalidSPDXAnalysisException
    {
        FileSet skipBin = new FileSet();
        skipBin.setDirectory( this.fileSets.get(0).getDirectory() );
        skipBin.addExclude( "**/*.bin" );
        skipBin.setOutputDirectory( this.fileSets.get(0).getOutputDirectory() );
        SpdxV3FileCollector collector = new SpdxV3FileCollector( customIdMap );
        SpdxFile[] SpdxFiles = collector.getFiles().toArray( new SpdxFile[collector.getFiles().size()] );
        assertEquals( 0, SpdxFiles.length );

        collector.collectFiles( Arrays.asList( skipBin ), this.directory.getAbsolutePath(), this.defaultFileInformation,
                new HashMap<>(), spdxPackage, RelationshipType.GENERATES, spdxDoc, sha1Algorithm );
        SpdxFiles = collector.getFiles().toArray( new SpdxFile[collector.getFiles().size()] );
        assertEquals( filePaths.length - 2, SpdxFiles.length );
        Arrays.sort( SpdxFiles, elementComparer );
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
        SpdxV3FileCollector collector = new SpdxV3FileCollector( customIdMap );
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
        SpdxV3FileCollector collector = new SpdxV3FileCollector( customIdMap );
        SpdxFile[] SpdxFiles = collector.getFiles().toArray( new SpdxFile[collector.getFiles().size()] );
        assertEquals( 0, SpdxFiles.length );

        collector.collectFiles( this.fileSets, this.directory.getAbsolutePath(), this.defaultFileInformation,
                new HashMap<>(), spdxPackage, RelationshipType.GENERATES, spdxDoc, sha1Algorithm );
        SpdxFiles = collector.getFiles().toArray( new SpdxFile[collector.getFiles().size()] );
        assertEquals( filePaths.length, SpdxFiles.length );
        Arrays.sort( SpdxFiles, elementComparer );
        @SuppressWarnings( "unchecked" )
        List<Relationship> allRelationships = (List<Relationship>)SpdxModelFactory
                        .getSpdxObjects( spdxDoc.getModelStore(), 
                                         spdxDoc.getCopyManager(), 
                                         SpdxConstantsV3.CORE_RELATIONSHIP, 
                                         null, spdxDoc.getIdPrefix() ).collect( Collectors.toList() );
        for ( int i = 0; i < SpdxFiles.length; i++ )
        {
            assertEquals( SpdxFileNames[i], SpdxFiles[i].getName().get() );
            assertTrue( SpdxFiles[i].getComment().get().startsWith( DEFAULT_COMMENT ) );
            assertEquals( DEFAULT_CONTRIBUTORS.length, SpdxFiles[i].getOriginatedBys().size() );
            List<String> contributors = new ArrayList<>();
            SpdxFiles[i].getOriginatedBys().spliterator().forEachRemaining( agent -> {
                try
                {
                    contributors.add( agent.getName().get() );
                }
                catch ( InvalidSPDXAnalysisException e )
                {
                    fail( "SPDX Exception getting agent name ");
                }
            } );
            assertArrayEquals( DEFAULT_CONTRIBUTORS, TestUtils.toSortedArray( contributors ) );
            assertEquals( DEFAULT_COPYRIGHT, SpdxFiles[i].getCopyrightText().get() );
            AnyLicenseInfo declaredLicense = null;
            AnyLicenseInfo concludedLicense = null;
            for ( Relationship relationship : allRelationships )
            {
                if ( SpdxFiles[i].equals( relationship.getFrom() ))
                {
                    if ( RelationshipType.HAS_CONCLUDED_LICENSE.equals( relationship.getRelationshipType() ) )
                    {
                        assertEquals( 1, relationship.getTos().size() );
                        concludedLicense = (AnyLicenseInfo) relationship.getTos().iterator().next();
                    } else if ( RelationshipType.HAS_DECLARED_LICENSE.equals( relationship.getRelationshipType() ) )
                    {
                        assertEquals( 1, relationship.getTos().size() );
                        declaredLicense = (AnyLicenseInfo) relationship.getTos().iterator().next();
                    }
                }
            }
            if ( SpdxFileNames[i].endsWith( FILE_NAME_WITH_ID ) )
            {
                assertEquals(LicenseInfoFactory.parseSPDXLicenseString( FILE_WITH_IDS_DECLARED_LICENSE ), declaredLicense );
                assertEquals( LicenseInfoFactory.parseSPDXLicenseString( FILE_WITH_IDS__CONCLUDED_LICENSE ), concludedLicense );
            }
            else
            {
                assertEquals( DEFAULT_DECLARED_LICENSE, declaredLicense.toString() );
                assertTrue( SpdxFiles[i].getComment().get().endsWith( DEFAULT_LICENSE_COMMENT ) );
                assertEquals( DEFAULT_CONCLUDED_LICENSE, concludedLicense.toString() );
            }
            assertEquals( 1, SpdxFiles[i].getAttributionTexts().size() );
            assertEquals( DEFAULT_NOTICE, SpdxFiles[i].getAttributionTexts().iterator().next() );
        }
    }

    @Test
    public void testGetSnippets() throws SpdxCollectionException, InvalidSPDXAnalysisException
    {
        SpdxV3FileCollector collector = new SpdxV3FileCollector( customIdMap );
        List<Snippet> snippets = collector.getSnippets();
        assertEquals( 0, snippets.size() );

        collector.collectFiles( this.fileSets, this.directory.getAbsolutePath(), this.defaultFileInformation,
                new HashMap<>(), spdxPackage, RelationshipType.GENERATES, spdxDoc, sha1Algorithm );
        snippets = collector.getSnippets();
        assertEquals( filePaths.length, snippets.size() );
        @SuppressWarnings( "unchecked" )
        List<Relationship> allRelationships = (List<Relationship>)SpdxModelFactory
                        .getSpdxObjects( spdxDoc.getModelStore(), 
                                         spdxDoc.getCopyManager(), 
                                         SpdxConstantsV3.CORE_RELATIONSHIP, 
                                         null, spdxDoc.getIdPrefix() ).collect( Collectors.toList() );
        for ( Snippet snippet : snippets )
        {
            AnyLicenseInfo declaredLicense = null;
            AnyLicenseInfo concludedLicense = null;
            for ( Relationship relationship : allRelationships )
            {
                if ( snippet.equals( relationship.getFrom() ))
                {
                    if ( RelationshipType.HAS_CONCLUDED_LICENSE.equals( relationship.getRelationshipType() ) )
                    {
                        assertEquals( 1, relationship.getTos().size() );
                        concludedLicense = (AnyLicenseInfo) relationship.getTos().iterator().next();
                    } else if ( RelationshipType.HAS_DECLARED_LICENSE.equals( relationship.getRelationshipType() ) )
                    {
                        assertEquals( 1, relationship.getTos().size() );
                        declaredLicense = (AnyLicenseInfo) relationship.getTos().iterator().next();
                    }
                }
            }
            assertEquals( SNIPPET_NAMES, snippet.getName().get() );
            assertTrue( snippet.getComment().get().startsWith( DEFAULT_SNIPPET_COMMENT ) );
            assertEquals( DEFAULT_SNIPPET_CONCLUDED_LICENSE, concludedLicense.toString() );
            assertEquals( DEFAULT_SNIPPET_COPYRIGHT, snippet.getCopyrightText().get() );
            assertEquals( DEFAULT_SNIPPET_DECLARED_LICENSE, declaredLicense.toString() );
            assertTrue( snippet.getComment().get().endsWith( DEFAULT_SNIPPET_LICENSE_COMMENT ) );
            assertEquals( DEFAULT_SNIPPET_BYTE_RANGE, positiveIntegerRangeToString( snippet.getByteRange().get() ) );
            assertEquals( DEFAULT_SNIPPET_LINE_RANGE, positiveIntegerRangeToString( snippet.getLineRange().get() ) );
        }
    }


    public static String positiveIntegerRangeToString( PositiveIntegerRange range ) throws InvalidSPDXAnalysisException
    {
        return Integer.toString( range.getBeginIntegerRange() ) + ":" + Integer.toString( range.getEndIntegerRange() );
    }

    @Test
    public void testCollectFilesWithPattern() throws SpdxCollectionException, InvalidSPDXAnalysisException
    {
        SpdxV3FileCollector collector = new SpdxV3FileCollector( customIdMap );
        SpdxFile[] SpdxFiles = collector.getFiles().toArray( new SpdxFile[collector.getFiles().size()] );
        assertEquals( 0, SpdxFiles.length );
        HashMap<String, SpdxDefaultFileInformation> fileSpecificInfo = new HashMap<>();
        SpdxDefaultFileInformation file2Info = new SpdxDefaultFileInformation();
        String file2Comment = "File 2 comment";
        file2Info.setComment( file2Comment );
        String file2License = defaultFileInformation.getDeclaredLicense();
        file2Info.setConcludedLicense( file2License );
        String[] file2Contributors = new String[] {"Person: File 2 contributor"};
        file2Info.setContributors( file2Contributors );
        String file2Copyright = "File 2 copyright";
        file2Info.setCopyright( file2Copyright );
        String file2DeclaredLicense = defaultFileInformation.getConcludedLicense();
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
        String file3License = defaultFileInformation.getDeclaredLicense();
        file3Info.setConcludedLicense( file3License );
        String[] file3Contributors = new String[] {"Person: File 3 contributor"};
        file3Info.setContributors( file3Contributors );
        String file3Copyright = "File 3 copyright";
        file3Info.setCopyright( file3Copyright );
        String file3DeclaredLicense = defaultFileInformation.getDeclaredLicense();
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
        Arrays.sort( SpdxFiles, elementComparer );
        String subDirAPrefix = "./" + directory.getName() + "/" + SUB_DIRS[0];

        @SuppressWarnings( "unchecked" )
        List<Relationship> allRelationships = (List<Relationship>)SpdxModelFactory
                        .getSpdxObjects( spdxDoc.getModelStore(), 
                                         spdxDoc.getCopyManager(), 
                                         SpdxConstantsV3.CORE_RELATIONSHIP, 
                                         null, spdxDoc.getIdPrefix() ).collect( Collectors.toList() );
        for ( int i = 0; i < SpdxFiles.length; i++ )
        {
            AnyLicenseInfo declaredLicense = null;
            AnyLicenseInfo concludedLicense = null;
            for ( Relationship relationship : allRelationships )
            {
                if ( SpdxFiles[i].equals( relationship.getFrom() ))
                {
                    if ( RelationshipType.HAS_CONCLUDED_LICENSE.equals( relationship.getRelationshipType() ) )
                    {
                        assertEquals( 1, relationship.getTos().size() );
                        concludedLicense = (AnyLicenseInfo) relationship.getTos().iterator().next();
                    } else if ( RelationshipType.HAS_DECLARED_LICENSE.equals( relationship.getRelationshipType() ) )
                    {
                        assertEquals( 1, relationship.getTos().size() );
                        declaredLicense = (AnyLicenseInfo) relationship.getTos().iterator().next();
                    }
                }
            }
            List<String> contributors = new ArrayList<>();
            SpdxFiles[i].getOriginatedBys().spliterator().forEachRemaining( agent -> {
                try
                {
                    contributors.add( agent.getName().get() );
                }
                catch ( InvalidSPDXAnalysisException e )
                {
                    fail( "SPDX Exception getting agent name ");
                }
            } );
            assertEquals( SpdxFileNames[i], SpdxFiles[i].getName().get() );
            if ( SpdxFiles[i].getName().get().equals( SpdxFileNames[1] ) )
            {
                assertTrue( SpdxFiles[i].getComment().get().startsWith( file2Comment ) );
                assertEquals( file2License.toString(), concludedLicense.toString() );
                assertEquals( file2Contributors.length, contributors.size() );
                
                assertArrayEquals( file2Contributors, TestUtils.toSortedArray( contributors ) );
                assertEquals( file2Copyright, SpdxFiles[1].getCopyrightText().get() );
                assertEquals( file2DeclaredLicense.toString(), declaredLicense.toString() );
                assertTrue( SpdxFiles[i].getComment().get().endsWith( file2LicenseComment ) );
                assertEquals( 1, SpdxFiles[i].getAttributionTexts().size() );
                assertEquals( file2Notice, SpdxFiles[i].getAttributionTexts().iterator().next() );
            }
            else if ( SpdxFiles[i].getName().get().startsWith( subDirAPrefix ) )
            {
                assertTrue( SpdxFiles[i].getComment().get().startsWith( file3Comment ) );
                assertEquals( file3License.toString(), concludedLicense.toString() );
                assertEquals( file3Contributors.length, contributors.size() );
                assertArrayEquals( file3Contributors, TestUtils.toSortedArray( contributors ) );
                assertEquals( file3Copyright, SpdxFiles[i].getCopyrightText().get() );
                assertEquals( file3DeclaredLicense.toString(), declaredLicense.toString() );
                assertTrue( SpdxFiles[i].getComment().get().endsWith( file3LicenseComment ) );
                assertEquals( 1, SpdxFiles[i].getAttributionTexts().size() );
                assertEquals( file3Notice, SpdxFiles[i].getAttributionTexts().iterator().next() );
            }
            else if ( !SpdxFiles[i].getName().get().endsWith( FILE_NAME_WITH_ID ) )
            {
                assertTrue( SpdxFiles[i].getComment().get().startsWith( DEFAULT_COMMENT ) );
                assertEquals( DEFAULT_CONCLUDED_LICENSE, concludedLicense.toString() );
                assertEquals( DEFAULT_CONTRIBUTORS.length, contributors.size() );
                assertArrayEquals( DEFAULT_CONTRIBUTORS, TestUtils.toSortedArray( contributors ) );
                assertEquals( DEFAULT_COPYRIGHT, SpdxFiles[i].getCopyrightText().get() );
                assertEquals( DEFAULT_DECLARED_LICENSE, declaredLicense.toString() );
                assertTrue( SpdxFiles[i].getComment().get().endsWith( DEFAULT_LICENSE_COMMENT ) );
                assertEquals( 1, SpdxFiles[i].getAttributionTexts().size() );
                assertEquals( DEFAULT_NOTICE, SpdxFiles[i].getAttributionTexts().iterator().next() );
            }
        }
    }

    @Test
    public void testConvertChecksumToString()
    {
        byte[] cksumBytes = new byte[] {00, 01, 02, 03, 04, 05, 06, 07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x1E};
        String expected = "000102030405060708090a0b0c0d1e";
        String result = SpdxV3FileCollector.convertChecksumToString( cksumBytes );
        assertEquals( expected, result );
    }

    @Test
    public void testConvertFilePathToSpdxFileName()
    {
        String dosFilePath = "subdir\\subdir2\\file.c";
        String dosSpdxFile = "./subdir/subdir2/file.c";
        SpdxV3FileCollector collector = new SpdxV3FileCollector( customIdMap );
        String result = collector.convertFilePathToSpdxFileName( dosFilePath );
        assertEquals( dosSpdxFile, result );

        String unixFilePath = "unixFolder/subfolder/file.sh";
        String unixSpdxFile = "./unixFolder/subfolder/file.sh";
        result = collector.convertFilePathToSpdxFileName( unixFilePath );
        assertEquals( unixSpdxFile, result );
    }

    @Test
    public void testGenerateChecksums() throws SpdxCollectionException, InvalidSPDXAnalysisException
    {
        SpdxV3FileCollector collector = new SpdxV3FileCollector( customIdMap );
        collector.collectFiles( this.fileSets, this.directory.getAbsolutePath(), this.defaultFileInformation,
                new HashMap<>(), spdxPackage, RelationshipType.GENERATES, spdxDoc, sha1Algorithm );
        File spdxFile = new File( filePaths[0] );

        Set<String> checksumAlgorithmSet = new HashSet<>();
        checksumAlgorithmSet.add(ChecksumAlgorithm.SHA1.toString() );
        checksumAlgorithmSet.add( ChecksumAlgorithm.SHA256.toString() );

        Set<Checksum> expectedChecksums = new HashSet<>();
        expectedChecksums.add( new Checksum( ChecksumAlgorithm.SHA1.toString(), "1834453c87b9188024c7b18d179eb64f95f29fcf" ) );
        expectedChecksums.add( new Checksum( ChecksumAlgorithm.SHA256.toString(),  "1c94046c63f61f5dbe5c15cc6c4e34510132ab262aa266735b344d836ef8cb3c") );
        // Checksum does not override equals currently. Hence, need to compare manually
        Set<Checksum> actualChecksums = SpdxV3FileCollector.generateChecksum( spdxFile, checksumAlgorithmSet );
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
