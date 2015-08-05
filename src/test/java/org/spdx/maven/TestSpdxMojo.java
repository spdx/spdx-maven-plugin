package org.spdx.maven;


import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.spdx.rdfparser.SPDXDocumentFactory;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.ExtractedLicenseInfo;
import org.spdx.rdfparser.license.LicenseInfoFactory;
import org.spdx.rdfparser.model.Annotation.AnnotationType;
import org.spdx.rdfparser.model.SpdxDocument;
import org.spdx.rdfparser.model.SpdxFile;
import org.spdx.rdfparser.model.SpdxPackage;

public class TestSpdxMojo
    extends AbstractMojoTestCase
{

    private static final String UNIT_TEST_RESOURCE_DIR = "src/test/resources/unit/spdx-maven-plugin-test";
    private static final String SPDX_FILE_NAME = UNIT_TEST_RESOURCE_DIR + "/test.spdx";

    @AfterClass
    public static void tearDownAfterClass()
        throws Exception
    {
    }

    @Before
    protected void setUp()
        throws Exception
    {
        super.setUp();
    }

    @After
    protected void tearDown()
        throws Exception
    {
        super.tearDown();
    }

    @Test
    public void testExecute() throws Exception
    {
        File testPom = new File( getBasedir(),
                                 UNIT_TEST_RESOURCE_DIR + "/pom.xml" );
//        CreateSpdxMojo mojo = (CreateSpdxMojo) configureMojo( myMojo, "spdx-maven-plugin", testPom );
        // if the below does not work due to a lookup error, run mvn test goal
        CreateSpdxMojo mojo = (CreateSpdxMojo) lookupMojo( "createSPDX", testPom );
        assertNotNull( mojo );
        mojo.execute();
        // Test SPDX filename parameter
        File spdxFile = new File ( getBasedir(), SPDX_FILE_NAME );
        assertTrue ( spdxFile.exists() );
        // Test output artifact file is created
        File artifactFile = new File( getBasedir(), "src/test/resources/unit/spdx-maven-plugin-test/spdx-maven-plugin-test.spdx");
        assertTrue ( artifactFile.exists() );
        SpdxDocument result = SPDXDocumentFactory.createSpdxDocument( artifactFile.getAbsolutePath() );
        List<String> warnings = result.verify();
        assertEquals( 0, warnings.size() );
        // Test configuration parameters found in the test resources pom.xml file
        // Document namespace
        assertEquals( "http://spdx.org/documents/spdx-toolsv2.0-rc1", result.getDocumentNamespace() );
        // Non standard licenses
        ExtractedLicenseInfo[] licenseInfos = result.getExtractedLicenseInfos();
        assertEquals(2, licenseInfos.length);
        ExtractedLicenseInfo testLicense1 = null;
        ExtractedLicenseInfo testLicense2 = null;
        for (ExtractedLicenseInfo li:licenseInfos) {
            if (li.getLicenseId().equals( "LicenseRef-testLicense" )) {
                testLicense1 = li;
            } else if (li.getLicenseId().equals( "LicenseRef-testLicense2" )) {
                testLicense2 = li;
            }
        }
        assertTrue(testLicense1 != null);
        assertEquals("Test License", testLicense1.getName());
        assertEquals("Test license text", testLicense1.getExtractedText());
        assertEquals(1, testLicense1.getSeeAlso().length);
        assertEquals("http://www.test.url/testLicense.html", testLicense1.getSeeAlso()[0]);
        assertEquals("Test license comment", testLicense1.getComment());
        
        assertTrue(testLicense2 != null);
        assertEquals("Second Test License", testLicense2.getName());
        assertEquals("Second est license text", testLicense2.getExtractedText());
        assertEquals(2, testLicense2.getSeeAlso().length);
        boolean foundSeeAlso1 = false;
        boolean foundSeeAlso2 = false;
        for (String seeAlso:testLicense2.getSeeAlso()) {
            if (seeAlso.equals( "http://www.test.url/testLicense2.html" )) {
                foundSeeAlso1 = true;
            } else if (seeAlso.equals( "http://www.test.url/testLicense2-alt.html" )) {
                foundSeeAlso2 = true;
            }
        }
        assertTrue(foundSeeAlso1);
        assertTrue(foundSeeAlso2);
        assertEquals("Second Test license comment", testLicense2.getComment());
        // documentComment
        assertEquals( "Document Comment", result.getComment() );
        // documentAnnotations
        assertEquals(2, result.getAnnotations().length);
        org.spdx.rdfparser.model.Annotation annotation1 = null;
        org.spdx.rdfparser.model.Annotation annotation2 = null;
        for (org.spdx.rdfparser.model.Annotation annotation:result.getAnnotations()) {
            if (annotation.getComment().equals( "Annotation1" )) {
                annotation1 = annotation;
            } else if (annotation.getComment().equals( "Annotation2" )) {
                annotation2 = annotation;
            }
        }
        assertTrue(annotation1 != null);
        assertEquals( "2010-01-29T18:30:22Z", annotation1.getAnnotationDate() );
        assertEquals( "Person:Test Person", annotation1.getAnnotator() );
        assertEquals( AnnotationType.annotationType_review, annotation1.getAnnotationType() );
        
        assertTrue(annotation2 != null);
        assertEquals( "2012-11-29T18:30:22Z", annotation2.getAnnotationDate() );
        assertEquals( "Organization:Test Organization", annotation2.getAnnotator() );
        assertEquals( AnnotationType.annotationType_other, annotation2.getAnnotationType() );
        //creatorComment
        assertEquals( "Creator comment", result.getCreationInfo().getComment() );
        // creators
        assertEquals( 3, result.getCreationInfo().getCreators().length );
        boolean foundCreator1 = false;
        boolean foundCreator2 = false;
        for (String creator:result.getCreationInfo().getCreators() ) {
            if (creator.equals( "Person: Creator1" )) {
                foundCreator1 = true;
            } else if (creator.equals( "Person: Creator2" )) {
                foundCreator2 = true;
            }
        }
        assertTrue( foundCreator1 );
        assertTrue( foundCreator2 );
        // package parameters
        assertEquals( 1, result.getDocumentDescribes().length );
        assertTrue( result.getDocumentDescribes()[0] instanceof SpdxPackage );
        SpdxPackage pkg = (SpdxPackage)result.getDocumentDescribes()[0];
        // packageAnnotations
        assertEquals( 1, pkg.getAnnotations().length );
        assertEquals( "PackageAnnotation", pkg.getAnnotations()[0].getComment() );
        assertEquals( "2015-01-29T18:30:22Z", pkg.getAnnotations()[0].getAnnotationDate() );
        assertEquals( "Person:Test Package Person", pkg.getAnnotations()[0].getAnnotator() );
        assertEquals( AnnotationType.annotationType_review, pkg.getAnnotations()[0].getAnnotationType() );
        //licenseDeclared
        AnyLicenseInfo licenseDeclared = LicenseInfoFactory.getListedLicenseById( "BSD-2-Clause" );
        assertEquals( licenseDeclared, pkg.getLicenseDeclared() );
        //licenseConcluded
        AnyLicenseInfo licenseConcluded = LicenseInfoFactory.getListedLicenseById( "BSD-3-Clause" );
        assertEquals( licenseConcluded, pkg.getLicenseConcluded() );
        //licenseComments
        assertEquals( "License comments", pkg.getLicenseComments() );
        //originator
        assertEquals( "Organization: Originating org.", pkg.getOriginator() );
        // sourceInfo
        assertEquals( "Source info", pkg.getSourceInfo() );
        //copyrightText
        assertEquals( "Copyright Text for Package", pkg.getCopyrightText() );
        // Test all files are included
        List<String> filePaths = new ArrayList<String>();
        File sourceDir = new File( getBasedir(),
                        UNIT_TEST_RESOURCE_DIR + "/src/main" );
        addFilePaths( getBasedir() + UNIT_TEST_RESOURCE_DIR, sourceDir, filePaths );
        File testDir = new File( getBasedir(),
                                   UNIT_TEST_RESOURCE_DIR + "/src/test" );
        addFilePaths( getBasedir() + UNIT_TEST_RESOURCE_DIR, testDir, filePaths );
        File resourceDir = new File( getBasedir(),
                                 UNIT_TEST_RESOURCE_DIR + "/src/resources" );
//TODO: Add resource to project stub and uncomment the line below
//        addFilePaths( getBasedir() + UNIT_TEST_RESOURCE_DIR, resourceDir, filePaths );
        SpdxFile[] pkgFiles = pkg.getFiles();
        assertEquals( filePaths.size(), pkgFiles.length );
        for (SpdxFile sFile:pkgFiles) {
            filePaths.remove( sFile.getName() );
        }
        assertEquals( 0, filePaths.size() );
        // file parameters
        // defaultFileComment
        // defaultFileContributors
        // defaultFileCopyright
        // defaultFileLicenseComment
        // defaultFileNotice
        // defaultFileConcludedLicense
        // defaultLicenseInformationInFile
        // defaultFileArtifactOfs
        // 
        // Test derived values 
        // package name
        
        // Path specific information
        //TODO Test dependencies
        
    }

    /**
     * Add relative file paths to the filePaths list
     * @param prefix Absolute path of the directory to which the filpaths are relative
     * @param dir Directory of files to add
     * @param filePaths return list of file paths to which paths are added
     */
    private void addFilePaths( String prefix, File dir, List<String> filePaths )
    {
        if ( !dir.exists() ) {
            return;
        }
        if ( !dir.isDirectory() ) {
            filePaths.add( "./" + dir.getAbsolutePath().substring( prefix.length()+2 ).replace( '\\', '/' ) );
            return;
        }
        File[] files = dir.listFiles();
        for (File file:files) {
            if (file.isDirectory()) {
                addFilePaths( prefix, file, filePaths );
            } else {
                filePaths.add( "./" + file.getAbsolutePath().substring( prefix.length()+2 ).replace( '\\', '/' ) );
            }
        }
    }

    @Test
    public void testmatchLicensesOnCrossReferenceUrls() {
        //TODO Implement testcase - parameter matLicensesOnCrossReferenceUrls=false
    }
}
