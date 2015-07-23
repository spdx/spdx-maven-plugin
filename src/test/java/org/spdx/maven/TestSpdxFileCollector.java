package org.spdx.maven;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;

import org.apache.maven.shared.model.fileset.FileSet;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.spdx.maven.SpdxDefaultFileInformation;
import org.spdx.maven.SpdxFileCollector;
import org.spdx.rdfparser.InvalidSPDXAnalysisException;
import org.spdx.rdfparser.SpdxPackageVerificationCode;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.LicenseInfoFactory;
import org.spdx.rdfparser.model.DoapProject;
import org.spdx.rdfparser.model.SpdxFile;
import org.spdx.spdxspreadsheet.InvalidLicenseStringException;

import com.google.common.io.Files;


public class TestSpdxFileCollector {
	
	

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}
	
	static final String[] FILE_NAMES = new String[] {"file1.bin", "file2.c", "file3.php", "file4.zip"};
	static final String[] SUB_DIRS = new String[] {"dirA", "folderB"};
	static final String[][] SUBDIR_FILES = new String[][] { new String[] {"subfile1.c", "subfile2.bin"},
	    new String[] {"sub2files.php"} };
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
    private String[] SpdxFileNames;
    private FileSet[] fileSets;

	@Before
	public void setUp() throws Exception {
	    this.defaultFileInformation = new SpdxDefaultFileInformation();
	    DoapProject[] projects = new DoapProject[ARTIFACT_OF_NAMES.length];
	    for ( int i = 0; i < projects.length; i++ ) {
	        projects[i] = new DoapProject( ARTIFACT_OF_NAMES[i], ARTIFACT_OF_HOME_PAGES[i] );
	    }
	    this.defaultFileInformation.setArtifactOf( projects );
	    this.defaultFileInformation.setComment( DEFAULT_COMMENT );
	    AnyLicenseInfo concludedLicense = LicenseInfoFactory.parseSPDXLicenseString( DEFAULT_CONCLUDED_LICENSE );
	    this.defaultFileInformation.setConcludedLicense( concludedLicense );
	    this.defaultFileInformation.setContributors( DEFAULT_CONTRIBUTORS );
	    this.defaultFileInformation.setCopyright( DEFAULT_COPYRIGHT );
	    AnyLicenseInfo declaredLicense = LicenseInfoFactory.parseSPDXLicenseString( DEFAULT_DECLARED_LICENSE );
	    this.defaultFileInformation.setDeclaredLicense( declaredLicense );
	    this.defaultFileInformation.setLicenseComment( DEFAULT_LICENSE_COMMENT );
	    this.defaultFileInformation.setNotice( DEFAULT_NOTICE );	
	    
	    this.directory = Files.createTempDir();
	    int numFiles = FILE_NAMES.length;
	    for ( int i = 0; i < SUBDIR_FILES.length; i++ )
	    {
	        numFiles = numFiles + SUBDIR_FILES[i].length;
	    }
	    this.filePaths = new String[numFiles];
	    this.SpdxFileNames = new String[numFiles];
	    int fpi = 0;   // file path index
	    for ( int i = 0; i < FILE_NAMES.length; i++ ) {
	        File newFile = new File( this.directory.getPath() + File.separator + FILE_NAMES[i] );
	        newFile.createNewFile();
	        createUniqueContent( newFile );
	        this.filePaths[fpi] = newFile.getPath();
	        this.SpdxFileNames[fpi++] = "./" + this.directory.getName() + "/" + FILE_NAMES[i];
	    }
	    for ( int i = 0; i < SUB_DIRS.length; i++ ) {
	        File newDir = new File( this.directory.getPath() + File.separator + SUB_DIRS[i] );
	        newDir.mkdir();
	        for ( int j = 0; j < SUBDIR_FILES[i].length; j++ ) {
	            File newFile = new File ( newDir.getPath() + File.separator + SUBDIR_FILES[i][j] );
	            newFile.createNewFile();
	            createUniqueContent( newFile );
	            this.filePaths[fpi] = newFile.getPath();
	            this.SpdxFileNames[fpi++] = "./" + this.directory.getName() + "/" + SUB_DIRS[i] +
	                            "/" + SUBDIR_FILES[i][j];
	        }
	    }
	    Arrays.sort( this.filePaths );
	    Arrays.sort( SpdxFileNames );
	    FileSet dirFileSet = new FileSet();
	    dirFileSet.setDirectory( directory.getPath() );
	    dirFileSet.setOutputDirectory( this.directory.getName() );
	    this.fileSets = new FileSet[] { dirFileSet };
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
        }
    }

    @Test
	public void testSpdxFileCollector() throws InvalidSPDXAnalysisException {
        SpdxFileCollector collector = new SpdxFileCollector();
        SpdxFile[] files = collector.getFiles();
        assertEquals( 0, files.length );
	}

	@Test
	public void testCollectFilesInDirectory() throws InvalidSPDXAnalysisException, SpdxCollectionException {
        SpdxFileCollector collector = new SpdxFileCollector();
        SpdxFile[] SpdxFiles = collector.getFiles();
        assertEquals( 0, SpdxFiles.length );
        
        collector.collectFiles( this.fileSets, this.defaultFileInformation,
                                           new HashMap<String, SpdxDefaultFileInformation>() );
        SpdxFiles = collector.getFiles();
        assertEquals( filePaths.length, SpdxFiles.length );
        Arrays.sort(  SpdxFiles );
        for ( int i = 0; i < SpdxFiles.length; i++ ) {
            assertEquals( SpdxFileNames[i], SpdxFiles[i].getName() );
        }
	}
	
	@Test
	public void testCollectFileInDirectoryPattern() throws SpdxCollectionException {
	    FileSet skipBin = new FileSet();
	    skipBin.setDirectory( this.fileSets[0].getDirectory() );
	    skipBin.addExclude( "**/*.bin" );
	    skipBin.setOutputDirectory( this.fileSets[0].getOutputDirectory() );
	    SpdxFileCollector collector = new SpdxFileCollector();
        SpdxFile[] SpdxFiles = collector.getFiles();
        assertEquals( 0, SpdxFiles.length );
        
        collector.collectFiles( new FileSet[] {skipBin},  this.defaultFileInformation,
                                           new HashMap<String, SpdxDefaultFileInformation>() );
        SpdxFiles = collector.getFiles();
        assertEquals( filePaths.length - 2, SpdxFiles.length );
        Arrays.sort(  SpdxFiles );
        int SpdxFilesIndex = 0;
        for ( int i = 0; i < SpdxFileNames.length; i++ ) {
            if ( SpdxFileNames[i].endsWith( ".bin" )) {
                continue;
            }
            assertEquals( SpdxFileNames[i], SpdxFiles[SpdxFilesIndex++].getName() );
        }
	}

	@Test
	public void testGetExtension() throws InvalidSPDXAnalysisException {
		SpdxFileCollector collector = new SpdxFileCollector();
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
        SpdxFileCollector collector = new SpdxFileCollector();
        SpdxFile[] SpdxFiles = collector.getFiles();
        assertEquals( 0, SpdxFiles.length );
        
        collector.collectFiles( this.fileSets, this.defaultFileInformation,
                                           new HashMap<String, SpdxDefaultFileInformation>() );
        SpdxFiles = collector.getFiles();
        assertEquals( filePaths.length, SpdxFiles.length );
        Arrays.sort(  SpdxFiles );
        for ( int i = 0; i < SpdxFiles.length; i++ ) {
            assertEquals( SpdxFileNames[i], SpdxFiles[i].getName() );
            DoapProject[] artifactOfs = SpdxFiles[i].getArtifactOf();
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
            assertEquals( DEFAULT_COMMENT, SpdxFiles[i].getComment() );
            assertEquals( DEFAULT_CONCLUDED_LICENSE, SpdxFiles[i].getLicenseConcluded().toString());
            assertEquals( DEFAULT_CONTRIBUTORS.length, SpdxFiles[i].getFileContributors().length );
            TestLicenseManager.assertArraysEqual( DEFAULT_CONTRIBUTORS, SpdxFiles[i].getFileContributors() );
            assertEquals( DEFAULT_COPYRIGHT, SpdxFiles[i].getCopyrightText() );
            assertEquals( DEFAULT_DECLARED_LICENSE, SpdxFiles[i].getLicenseInfoFromFiles()[0].toString() );
            assertEquals( DEFAULT_LICENSE_COMMENT, SpdxFiles[i].getLicenseComments() );
            assertEquals( DEFAULT_NOTICE, SpdxFiles[i].getNoticeText() );
        }
	}
	
	   @Test
	    public void testCollectFilesWithPattern() throws SpdxCollectionException {
	        SpdxFileCollector collector = new SpdxFileCollector();
	        SpdxFile[] SpdxFiles = collector.getFiles();
	        assertEquals( 0, SpdxFiles.length );
	        HashMap<String, SpdxDefaultFileInformation> fileSpecificInfo = 
	                        new HashMap<String, SpdxDefaultFileInformation>();
	        SpdxDefaultFileInformation file2Info = new SpdxDefaultFileInformation();
	        DoapProject file2DoapProject = new DoapProject( "file2project", "http://file2homepage.net");
	        DoapProject[] file2ArtifactOf = new DoapProject[] { file2DoapProject };
	        file2Info.setArtifactOf( file2ArtifactOf );
	        String file2Comment = "File 2 comment";
            file2Info.setComment( file2Comment  );
            AnyLicenseInfo file2License = defaultFileInformation.getDeclaredLicense();
            file2Info.setConcludedLicense( file2License );
            String[] file2Contributors = new String[] {"Person: File 2 contributor"};
            file2Info.setContributors( file2Contributors  );
            String file2Copyright = "File 2 copyright";
            file2Info.setCopyright( file2Copyright  );
            AnyLicenseInfo file2DeclaredLicense = defaultFileInformation.getConcludedLicense();
            file2Info.setDeclaredLicense( file2DeclaredLicense  );
            String file2LicenseComment = "File 2 license comment";
            file2Info.setLicenseComment( file2LicenseComment  );
            String file2Notice = "file 2 notice";
            file2Info.setNotice( file2Notice  );
            fileSpecificInfo.put( filePaths[1], file2Info );
            
            SpdxDefaultFileInformation file3Info = new SpdxDefaultFileInformation();
            DoapProject file3DoapProject = new DoapProject( "prj3file", "http://file3homepage.net");
            DoapProject[] file3ArtifactOf = new DoapProject[] { file3DoapProject };
            file3Info.setArtifactOf( file3ArtifactOf );
            String file3Comment = "File 3 comment";
            file3Info.setComment( file3Comment  );
            AnyLicenseInfo file3License = defaultFileInformation.getDeclaredLicense();
            file3Info.setConcludedLicense( file3License );
            String[] file3Contributors = new String[] {"Person: File 3 contributor"};
            file3Info.setContributors( file3Contributors  );
            String file3Copyright = "File 3 copyright";
            file3Info.setCopyright( file3Copyright  );
            AnyLicenseInfo file3DeclaredLicense = defaultFileInformation.getDeclaredLicense();
            file3Info.setDeclaredLicense( file3DeclaredLicense  );
            String file3LicenseComment = "File 3 license comment";
            file3Info.setLicenseComment( file3LicenseComment  );
            String file3Notice = "file 3 notice";
            file3Info.setNotice( file3Notice  );
            String subdir1Path = this.directory.getPath() + File.separator + SUB_DIRS[0];
            fileSpecificInfo.put( subdir1Path, file3Info );
            
            //TODO: Test directory patterns
	        collector.collectFiles( this.fileSets, this.defaultFileInformation,
	                                           fileSpecificInfo );
	        SpdxFiles = collector.getFiles();
	        assertEquals( filePaths.length, SpdxFiles.length );
	        Arrays.sort(  SpdxFiles );
	        DoapProject[] artifactOfs;
            String subDirAPrefix = "./" + directory.getName() + "/" + SUB_DIRS[0];
            
	        for ( int i = 0; i < SpdxFiles.length; i++ ) {
                assertEquals( SpdxFileNames[i], SpdxFiles[i].getName() );
                if ( SpdxFiles[i].getName().equals( SpdxFileNames[1] ))
                {
                    artifactOfs = SpdxFiles[1].getArtifactOf();
                    assertEquals( file2ArtifactOf.length, artifactOfs.length );
                    assertEquals( file2ArtifactOf[0].getName(), artifactOfs[0].getName() );
                    assertEquals( file2ArtifactOf[0].getHomePage(), artifactOfs[0].getHomePage() );
                    assertEquals( file2Comment, SpdxFiles[1].getComment() );
                    assertEquals( file2License.toString(), SpdxFiles[1].getLicenseConcluded().toString());
                    assertEquals( file2Contributors.length, SpdxFiles[1].getFileContributors().length );
                    TestLicenseManager.assertArraysEqual( file2Contributors, SpdxFiles[1].getFileContributors() );
                    assertEquals( file2Copyright, SpdxFiles[1].getCopyrightText() );
                    assertEquals( file2DeclaredLicense.toString(), SpdxFiles[1].getLicenseInfoFromFiles()[0].toString() );
                    assertEquals( file2LicenseComment, SpdxFiles[1].getLicenseComments() );
                    assertEquals( file2Notice, SpdxFiles[1].getNoticeText() );  
                }
                else if ( SpdxFiles[i].getName().startsWith( subDirAPrefix )) 
	            {
                    artifactOfs = SpdxFiles[i].getArtifactOf();
                    assertEquals( file3ArtifactOf.length, artifactOfs.length );
                    assertEquals( file3ArtifactOf[0].getName(), artifactOfs[0].getName() );
                    assertEquals( file3ArtifactOf[0].getHomePage(), artifactOfs[0].getHomePage() );
                    assertEquals( file3Comment, SpdxFiles[i].getComment() );
                    assertEquals( file3License.toString(), SpdxFiles[i].getLicenseConcluded().toString());
                    assertEquals( file3Contributors.length, SpdxFiles[i].getFileContributors().length );
                    TestLicenseManager.assertArraysEqual( file3Contributors, SpdxFiles[i].getFileContributors() );
                    assertEquals( file3Copyright, SpdxFiles[i].getCopyrightText() );
                    assertEquals( file3DeclaredLicense.toString(), SpdxFiles[i].getLicenseInfoFromFiles()[0].toString() );
                    assertEquals( file3LicenseComment, SpdxFiles[i].getLicenseComments() );
                    assertEquals( file3Notice, SpdxFiles[i].getNoticeText() );
	            } else 
	            {
    	            artifactOfs = SpdxFiles[i].getArtifactOf();
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
    	            assertEquals( DEFAULT_COMMENT, SpdxFiles[i].getComment() );
    	            assertEquals( DEFAULT_CONCLUDED_LICENSE, SpdxFiles[i].getLicenseConcluded().toString());
    	            assertEquals( DEFAULT_CONTRIBUTORS.length, SpdxFiles[i].getFileContributors().length );
    	            TestLicenseManager.assertArraysEqual( DEFAULT_CONTRIBUTORS, SpdxFiles[i].getFileContributors() );
    	            assertEquals( DEFAULT_COPYRIGHT, SpdxFiles[i].getCopyrightText() );
    	            assertEquals( DEFAULT_DECLARED_LICENSE, SpdxFiles[i].getLicenseInfoFromFiles()[0].toString() );
    	            assertEquals( DEFAULT_LICENSE_COMMENT, SpdxFiles[i].getLicenseComments() );
    	            assertEquals( DEFAULT_NOTICE, SpdxFiles[i].getNoticeText() );
	            }
	        }
	    }

	@Test
	public void testGetLicenseInfoFromFiles() throws SpdxCollectionException, IOException, InvalidLicenseStringException {
        SpdxFileCollector collector = new SpdxFileCollector();
        AnyLicenseInfo[] result = collector.getLicenseInfoFromFiles();
        assertEquals( 0, result.length );
        
        collector.collectFiles( this.fileSets, this.defaultFileInformation,
                                           new HashMap<String, SpdxDefaultFileInformation>() );
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
            AnyLicenseInfo newDeclaredLicense = LicenseInfoFactory.parseSPDXLicenseString( newLicenseName );
            info2.setDeclaredLicense( newDeclaredLicense );
            FileSet fileSet2 = new FileSet();
            fileSet2.setDirectory( tempDir2.getPath() );
            
            collector.collectFiles( new FileSet[] { fileSet2 }, info2,
                                               new HashMap<String, SpdxDefaultFileInformation>() );
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
        SpdxFileCollector collector = new SpdxFileCollector();
        SpdxFile[] SpdxFiles = collector.getFiles();
        assertEquals( 0, SpdxFiles.length );
        
        collector.collectFiles( this.fileSets, this.defaultFileInformation,
                                           new HashMap<String, SpdxDefaultFileInformation>() );
        File SpdxFile = new File( filePaths[0] );
        SpdxPackageVerificationCode result = collector.getVerificationCode( SpdxFile.getPath() );
        assertTrue( !result.getValue().isEmpty() );
        assertEquals( 1, result.getExcludedFileNames().length );
        assertEquals( SpdxFileNames[0], result.getExcludedFileNames()[0] );
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
	    String dosFilePath = "subdir\\subdir2\\file.c";
	    String dosSpdxFile = "./subdir/subdir2/file.c";
	    SpdxFileCollector collector = new SpdxFileCollector();
	    String result = collector.convertFilePathToSpdxFileName( dosFilePath );
	    assertEquals( dosSpdxFile, result );
	    
	    String unixFilePath = "unixFolder/subfolder/file.sh";
	    String unixSpdxFile = "./unixFolder/subfolder/file.sh";
	    result = collector.convertFilePathToSpdxFileName( unixFilePath );
	    assertEquals( unixSpdxFile, result );
	}
}
