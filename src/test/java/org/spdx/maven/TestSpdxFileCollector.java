package org.spdx.maven;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.spdx.maven.SpdxDefaultFileInformation;
import org.spdx.maven.SpdxFileCollector;
import org.spdx.rdfparser.DOAPProject;
import org.spdx.rdfparser.InvalidSPDXAnalysisException;
import org.spdx.rdfparser.SPDXFile;
import org.spdx.rdfparser.SPDXLicenseInfo;
import org.spdx.rdfparser.SPDXLicenseInfoFactory;
import org.spdx.rdfparser.SpdxPackageVerificationCode;
import org.spdx.spdxspreadsheet.InvalidLicenseStringException;

import com.google.common.io.Files;

import edu.emory.mathcs.backport.java.util.Arrays;


public class TestSpdxFileCollector {
	
	

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}
	
	static final String[] FILE_NAMES = new String[] {"file1.bin", "file2.c", "file3.php", "file4.zip"};
	static final String[] ARTIFACT_OF_NAMES = new String[] {"projectA", "projectB"};
	static final String[] ARTIFACT_OF_HOME_PAGES = new String[] {"http://www.projecta", "http://www.projectb"};
    private static final String DEFAULT_COMMENT = "Default comment";
    private static final String DEFAULT_CONCLUDED_LICENSE = "Apache-2.0";
    private static final String[] DEFAULT_CONTRIBUTORS = new String[] {"Contrib1", "Contrib2"};
    private static final String DEFAULT_COPYRIGHT = "Default copyright";
    private static final String DEFAULT_DECLARED_LICENSE = "APSL-1.1";
    private static final String DEFAULT_LICENSE_COMMENT = "Default license comment";
    private static final String DEFAULT_NOTICE = "Default notice";
	
    private SpdxDefaultFileInformation defaultFileInformation;
    private File directory;
    private String[] filePaths;
    private String[] spdxFileNames;

	@Before
	public void setUp() throws Exception {
	    this.defaultFileInformation = new SpdxDefaultFileInformation();
	    DOAPProject[] projects = new DOAPProject[ARTIFACT_OF_NAMES.length];
	    for ( int i = 0; i < projects.length; i++ ) {
	        projects[i] = new DOAPProject( ARTIFACT_OF_NAMES[i], ARTIFACT_OF_HOME_PAGES[i] );
	    }
	    this.defaultFileInformation.setArtifactOf( projects );
	    this.defaultFileInformation.setComment( DEFAULT_COMMENT );
	    SPDXLicenseInfo concludedLicense = SPDXLicenseInfoFactory.parseSPDXLicenseString( DEFAULT_CONCLUDED_LICENSE );
	    this.defaultFileInformation.setConcludedLicense( concludedLicense );
	    this.defaultFileInformation.setContributors( DEFAULT_CONTRIBUTORS );
	    this.defaultFileInformation.setCopyright( DEFAULT_COPYRIGHT );
	    SPDXLicenseInfo declaredLicense = SPDXLicenseInfoFactory.parseSPDXLicenseString( DEFAULT_DECLARED_LICENSE );
	    this.defaultFileInformation.setDeclaredLicense( declaredLicense );
	    this.defaultFileInformation.setLicenseComment( DEFAULT_LICENSE_COMMENT );
	    this.defaultFileInformation.setNotice( DEFAULT_NOTICE );	
	    
	    this.directory = Files.createTempDir();
	    this.filePaths = new String[FILE_NAMES.length];
	    this.spdxFileNames = new String[FILE_NAMES.length];
	    for ( int i = 0; i < FILE_NAMES.length; i++ ) {
	        File newFile = new File( this.directory.getPath() + File.separator + FILE_NAMES[i] );
	        newFile.createNewFile();
	        createUniqueContent( newFile );
	        this.filePaths[i] = newFile.getPath();
	        this.spdxFileNames[i] = "./" + this.directory.getName() + "/" + FILE_NAMES[i];
	    }
	    Arrays.sort( this.filePaths );
	}

	private void createUniqueContent( File file ) throws FileNotFoundException
    {
        PrintWriter writer = new PrintWriter( file );
        try {
            writer.println( file.getPath() );
            writer.println( System.nanoTime() );
        } finally {
            writer.close();
        }
    }

    @After
	public void tearDown() throws Exception {
        deleteDirectory( this.directory );
	}

	private void deleteDirectory( File dir )
    {
        if ( dir.isFile() ) {
            if (!dir.delete()) {
                System.console().writer().println("Unable to delete "+dir.getPath() );
            }
        } else if ( dir.isDirectory() ) {
            File[] children = dir.listFiles();
            for ( int i = 0; i < children.length; i++ ) {
                if ( children[i].isFile() ) {
                    if (!children[i].delete()) {
                        System.console().writer().println("Unable to delete "+children[i].getPath() );
                    }
                } else if ( children[i].isDirectory() ) {
                    deleteDirectory( children[i] );
                }
            }
            if ( !dir.delete() ) {
                System.console().writer().println("Unable to delete "+dir.getPath() );
            }
        }
    }

    @Test
	public void testSpdxFileCollector() throws InvalidSPDXAnalysisException {
        SpdxFileCollector collector = new SpdxFileCollector( new Pattern[] {} );
        SPDXFile[] files = collector.getFiles();
        assertEquals( 0, files.length );
	}

	@Test
	public void testCollectFilesInDirectory() throws InvalidSPDXAnalysisException, SpdxCollectionException {
        SpdxFileCollector collector = new SpdxFileCollector( new Pattern[] {} );
        SPDXFile[] spdxFiles = collector.getFiles();
        assertEquals( 0, spdxFiles.length );
        
        collector.collectFilesInDirectory( this.directory, this.defaultFileInformation );
        spdxFiles = collector.getFiles();
        assertEquals( filePaths.length, spdxFiles.length );
        Arrays.sort(  spdxFiles );
        for ( int i = 0; i < spdxFiles.length; i++ ) {
            assertEquals( spdxFileNames[i], spdxFiles[i].getName() );
        }
	}
	
	@Test
	public void testCollectFileInDirectoryPattern() throws SpdxCollectionException {
	    Pattern skipBin = Pattern.compile( ".*\\.bin" );
	    SpdxFileCollector collector = new SpdxFileCollector( new Pattern[] {skipBin} );
        SPDXFile[] spdxFiles = collector.getFiles();
        assertEquals( 0, spdxFiles.length );
        
        collector.collectFilesInDirectory( this.directory, this.defaultFileInformation );
        spdxFiles = collector.getFiles();
        assertEquals( filePaths.length - 1, spdxFiles.length );
        Arrays.sort(  spdxFiles );
        for ( int i = 0; i < spdxFiles.length; i++ ) {
            assertEquals( spdxFileNames[i + 1], spdxFiles[i].getName() );
        }
	}

	@Test
	public void testGetExtension() throws InvalidSPDXAnalysisException {
		SpdxFileCollector collector = new SpdxFileCollector( new Pattern[] {} );
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
		File multipleDots = new File ( "file.with.more.dots." + ext );
		result = collector.getExtension( multipleDots );
		assertEquals( ext, result );
	}

	@Test
	public void testGetFiles() throws SpdxCollectionException {
        SpdxFileCollector collector = new SpdxFileCollector( new Pattern[] {} );
        SPDXFile[] spdxFiles = collector.getFiles();
        assertEquals( 0, spdxFiles.length );
        
        collector.collectFilesInDirectory( this.directory, this.defaultFileInformation );
        spdxFiles = collector.getFiles();
        assertEquals( filePaths.length, spdxFiles.length );
        Arrays.sort(  spdxFiles );
        for ( int i = 0; i < spdxFiles.length; i++ ) {
            assertEquals( spdxFileNames[i], spdxFiles[i].getName() );
            DOAPProject[] artifactOfs = spdxFiles[i].getArtifactOf();
            assertEquals( ARTIFACT_OF_NAMES.length, artifactOfs.length );
            if ( artifactOfs[0].getName().equals( ARTIFACT_OF_NAMES[0] )) {
                assertEquals( ARTIFACT_OF_HOME_PAGES[0], artifactOfs[0].getHomePage() );
                assertEquals( ARTIFACT_OF_NAMES[1], artifactOfs[1].getName());
                assertEquals( ARTIFACT_OF_HOME_PAGES[1], artifactOfs[1].getHomePage() );
            } else if ( artifactOfs[0].getName().equals( ARTIFACT_OF_NAMES[1] )) {
                assertEquals( ARTIFACT_OF_HOME_PAGES[1], artifactOfs[0].getHomePage() );
                assertEquals( ARTIFACT_OF_NAMES[0], artifactOfs[1].getName());
                assertEquals( ARTIFACT_OF_HOME_PAGES[0], artifactOfs[1].getHomePage() );
            } else {
                fail( "ArtifactOf not found" );
            }
            assertEquals( DEFAULT_COMMENT, spdxFiles[i].getComment() );
            assertEquals( DEFAULT_CONCLUDED_LICENSE, spdxFiles[i].getConcludedLicenses().toString());
            assertEquals( DEFAULT_CONTRIBUTORS.length, spdxFiles[i].getContributors().length );
            TestLicenseManager.assertArraysEqual( DEFAULT_CONTRIBUTORS, spdxFiles[i].getContributors() );
            assertEquals( DEFAULT_COPYRIGHT, spdxFiles[i].getCopyright() );
            assertEquals( DEFAULT_DECLARED_LICENSE, spdxFiles[i].getSeenLicenses()[0].toString() );
            assertEquals( DEFAULT_LICENSE_COMMENT, spdxFiles[i].getLicenseComments() );
            assertEquals( DEFAULT_NOTICE, spdxFiles[i].getNoticeText() );
        }
	}

	@Test
	public void testGetLicenseInfoFromFiles() throws SpdxCollectionException, IOException, InvalidLicenseStringException {
        SpdxFileCollector collector = new SpdxFileCollector( new Pattern[] {} );
        SPDXLicenseInfo[] result = collector.getLicenseInfoFromFiles();
        assertEquals( 0, result.length );
        
        collector.collectFilesInDirectory( directory, this.defaultFileInformation );
        result = collector.getLicenseInfoFromFiles();
        assertEquals( 1, result.length );
        assertEquals( DEFAULT_DECLARED_LICENSE, result[0].toString() );
        
        File tempDir2 = Files.createTempDir();
        try {
            File oneMoreFile = new File( tempDir2.getPath() + File.separator + "oneMore.c") ;
            oneMoreFile.createNewFile();
            createUniqueContent( oneMoreFile );
            SpdxDefaultFileInformation info2 = new SpdxDefaultFileInformation();
            info2.setConcludedLicense( this.defaultFileInformation.getConcludedLicense() );
            info2.setCopyright( this.defaultFileInformation.getCopyright() );
            String newLicenseName = "LicenseRef-newLicense";
            SPDXLicenseInfo newDeclaredLicense = SPDXLicenseInfoFactory.parseSPDXLicenseString( newLicenseName );
            info2.setDeclaredLicense( newDeclaredLicense );
            collector.collectFilesInDirectory( tempDir2, info2 );
            result = collector.getLicenseInfoFromFiles();
            assertEquals( 2, result.length );
            if ( result[0].toString().equals( DEFAULT_DECLARED_LICENSE )) {
                assertEquals( newLicenseName, result[1].toString() );
            } else {
                assertEquals( newLicenseName, result[0].toString() );
                assertEquals( DEFAULT_DECLARED_LICENSE, result[1].toString() );
            }
        } finally {
            deleteDirectory( tempDir2 );
        }
	}

	@Test
	public void testGetVerificationCode() throws SpdxCollectionException, NoSuchAlgorithmException {
        SpdxFileCollector collector = new SpdxFileCollector( new Pattern[] {} );
        SPDXFile[] spdxFiles = collector.getFiles();
        assertEquals( 0, spdxFiles.length );
        
        collector.collectFilesInDirectory( this.directory, this.defaultFileInformation );
        File spdxFile = new File( this.directory.getPath() + File.separator + FILE_NAMES[0] );
        SpdxPackageVerificationCode result = collector.getVerificationCode( spdxFile.getPath() );
        assertTrue( !result.getValue().isEmpty() );
        assertEquals( 1, result.getExcludedFileNames().length );
        assertEquals( spdxFileNames[0], result.getExcludedFileNames()[0] );
	}

	@Test
	public void testConvertChecksumToString() {
        byte[] cksumBytes = new byte[] { 00, 01, 02, 03, 04, 05, 06, 07, 0x08, 0x09, 0x0A, 
            0x0B, 0x0C, 0x0D, 0x1E
        };
        String expected = "000102030405060708090a0b0c0d1e";
        String result = SpdxFileCollector.convertChecksumToString( cksumBytes );
        assertEquals( expected, result );
	}
	
	@Test
	public void testConvertFilePathToSpdxFileName() {
	    String dosPathPrefix = "C:\\dira\\dirb\\dirc\\";
	    String dosFilePath = dosPathPrefix  + "subdir\\subdir2\\file.c";
	    String dosSpdxFile = "./subdir/subdir2/file.c";
	    SpdxFileCollector collector = new SpdxFileCollector( new Pattern[] {} );
	    String result = collector.convertFilePathToSpdxFileName( dosFilePath, dosPathPrefix );
	    assertEquals( dosSpdxFile, result );
	    
	    String unixPathPrefix = "/root/something/to/prefix/";
	    String unixFilePath = unixPathPrefix + "unixFolder/subfolder/file.sh";
	    String unixSpdxFile = "./unixFolder/subfolder/file.sh";
	    result = collector.convertFilePathToSpdxFileName( unixFilePath, unixPathPrefix );
	    assertEquals( unixSpdxFile, result );
	}

}
