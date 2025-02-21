package org.spdx.maven;


import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.spdx.core.DefaultModelStore;
import org.spdx.jacksonstore.MultiFormatStore;
import org.spdx.jacksonstore.MultiFormatStore.Format;
import org.spdx.library.LicenseInfoFactory;
import org.spdx.library.ModelCopyManager;
import org.spdx.library.SpdxModelFactory;
import org.spdx.library.model.v2.ExternalRef;
import org.spdx.library.model.v2.SpdxConstantsCompatV2;
import org.spdx.library.model.v2.SpdxDocument;
import org.spdx.library.model.v2.SpdxElement;
import org.spdx.library.model.v2.SpdxFile;
import org.spdx.library.model.v2.SpdxPackage;
import org.spdx.library.model.v2.SpdxSnippet;
import org.spdx.library.model.v2.enumerations.AnnotationType;
import org.spdx.library.model.v2.enumerations.ReferenceCategory;
import org.spdx.library.model.v2.license.AnyLicenseInfo;
import org.spdx.library.model.v2.license.ExtractedLicenseInfo;
import org.spdx.library.referencetype.ListedReferenceTypes;
import org.spdx.maven.utils.TestSpdxV2FileCollector;
import org.spdx.spdxRdfStore.RdfStore;
import org.spdx.storage.ISerializableModelStore;
import org.spdx.storage.simple.InMemSpdxStore;

public class TestSpdxV2Mojo extends AbstractMojoTestCase
{

    private static final String UNIT_TEST_RESOURCE_DIR = "target/test-classes/unit/spdx-maven-plugin-test";
    private static final String SPDX_FILE_NAME = UNIT_TEST_RESOURCE_DIR + "/test.spdx.rdf.xml";

    private static final String UNIT_TEST_APP_RESOURCE_DIR = "target/test-classes/unit/app-bomination";
    @SuppressWarnings( "unused" )
    private static final String APP_SPDX_FILE_NAME = UNIT_TEST_APP_RESOURCE_DIR + "/test.spdx.rdf.xml";
    private static final String SPDX_JSON_FILE_NAME = UNIT_TEST_RESOURCE_DIR + "/test.spdx.json";
    @AfterClass
    public static void tearDownAfterClass() throws Exception
    {
    }

    @Before
    protected void setUp() throws Exception
    {
        super.setUp();
        SpdxModelFactory.init();
        DefaultModelStore.initialize(new InMemSpdxStore(), "http://default/namespace", new ModelCopyManager());
    }

    @After
    protected void tearDown() throws Exception
    {
        super.tearDown();
    }

    @Test
    public void testExecute() throws Exception
    {
        File testPom = new File( getBasedir(), UNIT_TEST_RESOURCE_DIR + "/pom.xml" );
        Xpp3Dom pluginPomDom = Xpp3DomBuilder.build(ReaderFactory.newXmlReader(testPom));
        System.out.println("Created time test: "+ pluginPomDom.getChild( "properties" ).getChild( "project.build.outputTimestamp" ));
        //        CreateSpdxMojo mojo = (CreateSpdxMojo) configureMojo( myMojo, "spdx-maven-plugin", testPom );
        // if the below does not work due to a lookup error, run mvn test goal
        CreateSpdxMojo mojo = (CreateSpdxMojo) lookupMojo( "createSPDX", testPom );
        assertNotNull( mojo );
        mojo.execute();
        // Test SPDX filename parameter
        File spdxFile = new File( getBasedir(), SPDX_FILE_NAME );
        assertTrue( spdxFile.exists() );
        // Test output artifact file is created
        File artifactFile = getTestFile(
                "target/test-classes/unit/spdx-maven-plugin-test/spdx maven plugin test.spdx.rdf.xml" );
        assertTrue( artifactFile.exists() );
        ISerializableModelStore modelStore = new RdfStore();
        ModelCopyManager copyManager = new ModelCopyManager();
        SpdxDocument result;
        try ( InputStream is = new FileInputStream( artifactFile.getAbsolutePath() ) )
        {
            result = (SpdxDocument)modelStore.deSerialize( is, false );
        }
        List<String> warnings = result.verify();
        assertEquals( 0, warnings.size() );
        // Test configuration parameters found in the test resources pom.xml file
        // Document namespace
        assertEquals( "http://spdx.org/spdxpackages/spdx%20toolsv2.0%20rc1", result.getDocumentUri() );
        // Non-standard licenses
        ExtractedLicenseInfo[] licenseInfos = result.getExtractedLicenseInfos().toArray( new ExtractedLicenseInfo[result.getExtractedLicenseInfos().size()] );
        assertEquals( 2, licenseInfos.length );
        ExtractedLicenseInfo testLicense1 = null;
        ExtractedLicenseInfo testLicense2 = null;
        for ( ExtractedLicenseInfo li : licenseInfos )
        {
            if ( li.getLicenseId().equals( "LicenseRef-testLicense" ) )
            {
                testLicense1 = li;
            }
            else if ( li.getLicenseId().equals( "LicenseRef-testLicense2" ) )
            {
                testLicense2 = li;
            }
        }
        assertTrue( testLicense1 != null );
        assertEquals( "Test License", testLicense1.getName() );
        assertEquals( "Test license text", testLicense1.getExtractedText() );
        assertEquals( 1, testLicense1.getSeeAlso().size() );
        assertEquals( "http://www.test.url/testLicense.html", testLicense1.getSeeAlso().toArray( new String[testLicense1.getSeeAlso().size()]  )[0] );
        assertEquals( "Test license comment", testLicense1.getComment() );

        assertTrue( testLicense2 != null );
        assertEquals( "Second Test License", testLicense2.getName() );
        assertEquals( "Second est license text", testLicense2.getExtractedText() );
        assertEquals( 2, testLicense2.getSeeAlso().size() );
        boolean foundSeeAlso1 = false;
        boolean foundSeeAlso2 = false;
        for ( String seeAlso : testLicense2.getSeeAlso() )
        {
            if ( seeAlso.equals( "http://www.test.url/testLicense2.html" ) )
            {
                foundSeeAlso1 = true;
            }
            else if ( seeAlso.equals( "http://www.test.url/testLicense2-alt.html" ) )
            {
                foundSeeAlso2 = true;
            }
        }
        assertTrue( foundSeeAlso1 );
        assertTrue( foundSeeAlso2 );
        assertEquals( "Second Test license comment", testLicense2.getComment() );
        // documentComment
        assertEquals( "Document Comment", result.getComment().get() );
        // documentAnnotations
        assertEquals( 2, result.getAnnotations().size() );
        org.spdx.library.model.v2.Annotation annotation1 = null;
        org.spdx.library.model.v2.Annotation annotation2 = null;
        for ( org.spdx.library.model.v2.Annotation annotation : result.getAnnotations() )
        {
            if ( annotation.getComment().equals( "Annotation1" ) )
            {
                annotation1 = annotation;
            }
            else if ( annotation.getComment().equals( "Annotation2" ) )
            {
                annotation2 = annotation;
            }
        }
        assertTrue( annotation1 != null );
        assertEquals( "2010-01-29T18:30:22Z", annotation1.getAnnotationDate() );
        assertEquals( "Person:Test Person", annotation1.getAnnotator() );
        assertEquals( AnnotationType.REVIEW, annotation1.getAnnotationType() );

        assertTrue( annotation2 != null );
        assertEquals( "2012-11-29T18:30:22Z", annotation2.getAnnotationDate() );
        assertEquals( "Organization:Test Organization", annotation2.getAnnotator() );
        assertEquals( AnnotationType.OTHER, annotation2.getAnnotationType() );
        //creatorComment
        assertEquals( "Creator comment", result.getCreationInfo().getComment().get() );
        // creators
        assertEquals( 3, result.getCreationInfo().getCreators().size() );
        boolean foundCreator1 = false;
        boolean foundCreator2 = false;
        for ( String creator : result.getCreationInfo().getCreators() )
        {
            if ( creator.equals( "Person: Creator1" ) )
            {
                foundCreator1 = true;
            }
            else if ( creator.equals( "Person: Creator2" ) )
            {
                foundCreator2 = true;
            }
        }
        assertTrue( foundCreator1 );
        assertTrue( foundCreator2 );
        // created
        assertEquals( "2025-02-21T20:18:31Z", result.getCreationInfo().getCreated() );
        // package parameters
        assertEquals( 1, result.getDocumentDescribes().size() );
        SpdxElement described = result.getDocumentDescribes().toArray( new SpdxElement[result.getDocumentDescribes().size()] )[0];
        assertTrue( described instanceof SpdxPackage );
        SpdxPackage pkg = (SpdxPackage) described;
        // packageAnnotations
        assertEquals( 1, pkg.getAnnotations().size() );
        org.spdx.library.model.v2.Annotation annotation = pkg.getAnnotations().toArray( new org.spdx.library.model.v2.Annotation [pkg.getAnnotations().size()] )[0];
        assertEquals( "PackageAnnotation", annotation.getComment() );
        assertEquals( "2015-01-29T18:30:22Z", annotation.getAnnotationDate() );
        assertEquals( "Person:Test Package Person", annotation.getAnnotator() );
        assertEquals( AnnotationType.REVIEW, annotation.getAnnotationType() );
        //licenseDeclared
        AnyLicenseInfo licenseDeclared = LicenseInfoFactory.getListedLicenseByIdCompatV2( "BSD-2-Clause" );
        assertEquals( licenseDeclared, pkg.getLicenseDeclared() );
        //licenseConcluded
        AnyLicenseInfo licenseConcluded = LicenseInfoFactory.getListedLicenseByIdCompatV2( "BSD-3-Clause" );
        assertEquals( licenseConcluded, pkg.getLicenseConcluded() );
        //licenseComments
        assertEquals( "License comments", pkg.getLicenseComments().get() );
        //originator
        assertEquals( "Organization: Originating org.", pkg.getOriginator().get() );
        // sourceInfo
        assertEquals( "Source info", pkg.getSourceInfo().get() );
        //copyrightText
        assertEquals( "Copyright Text for Package", pkg.getCopyrightText() );
        //external Refs
        ExternalRef[] externalRefs = pkg.getExternalRefs().toArray( new ExternalRef[pkg.getExternalRefs().size()] );
        assertEquals( 2, externalRefs.length );
        if ( externalRefs[0].getReferenceCategory().equals( ReferenceCategory.SECURITY ) )
        {
            assertEquals( "extref comment1", externalRefs[0].getComment().get() );
            assertEquals( "example-locator-CPE22Type", externalRefs[0].getReferenceLocator() );
            assertEquals( "cpe22Type", ListedReferenceTypes.getListedReferenceTypes().getListedReferenceName(
                    new URI( externalRefs[0].getReferenceType().getIndividualURI() ) ) );
            assertEquals( "extref comment2", externalRefs[1].getComment().get() );
            assertEquals( "org.apache.tomcat:tomcat:9.0.0.M4", externalRefs[1].getReferenceLocator() );
            assertEquals( "maven-central", ListedReferenceTypes.getListedReferenceTypes().getListedReferenceName(
                    new URI( externalRefs[1].getReferenceType().getIndividualURI() ) ) );
        }
        else if ( externalRefs[0].getReferenceCategory().equals(
                ReferenceCategory.PACKAGE_MANAGER ) )
        {
            assertEquals( "extref comment1", externalRefs[1].getComment().get() );
            assertEquals( "example-locator-CPE22Type", externalRefs[1].getReferenceLocator() );
            assertEquals( "cpe22Type", ListedReferenceTypes.getListedReferenceTypes().getListedReferenceName(
                    new URI(externalRefs[1].getReferenceType().getIndividualURI() ) ) );
            assertEquals( "extref comment2", externalRefs[0].getComment().get() );
            assertEquals( "org.apache.tomcat:tomcat:9.0.0.M4", externalRefs[0].getReferenceLocator() );
            assertEquals( "maven-central", ListedReferenceTypes.getListedReferenceTypes().getListedReferenceName(
                    new URI( externalRefs[0].getReferenceType().getIndividualURI() ) ) );
        }
        else
        {
            fail( "Unexpected reference category" );
        }
        // Test all files are included
        List<String> filePaths = new ArrayList<>();
        File sourceDir = new File( getBasedir(), UNIT_TEST_RESOURCE_DIR + "/src/main" );
        addFilePaths( getBasedir() + UNIT_TEST_RESOURCE_DIR, sourceDir, filePaths );
        File testDir = new File( getBasedir(), UNIT_TEST_RESOURCE_DIR + "/src/test" );
        addFilePaths( getBasedir() + UNIT_TEST_RESOURCE_DIR, testDir, filePaths );
        @SuppressWarnings( "unused" )
        File resourceDir = new File( getBasedir(), UNIT_TEST_RESOURCE_DIR + "/src/resources" );
        //TODO: Add resource to project stub and uncomment the line below
        //        addFilePaths( getBasedir() + UNIT_TEST_RESOURCE_DIR, resourceDir, filePaths );
        Collection<SpdxFile> pkgFiles = pkg.getFiles();
        assertEquals( filePaths.size(), pkgFiles.size() );
        String fileWithSnippet = null;
        for ( SpdxFile sFile : pkgFiles )
        {
            if ( sFile.getName().get().equals( "./src/main/java/CommonCode.java" ) )
            {
                fileWithSnippet = sFile.getId();
                assertEquals( "Comment for CommonCode", sFile.getComment().get() );
                assertEquals( "Common Code Copyright", sFile.getCopyrightText() );
                assertEquals( "License Comment for Common Code", sFile.getLicenseComments().get() );
                assertEquals( "Notice for Commmon Code", sFile.getNoticeText().get() );
                assertEquals( "EPL-1.0", sFile.getLicenseConcluded().toString() );
                assertEquals( "Contributor to CommonCode", sFile.getFileContributors().toArray( new String[sFile.getFileContributors().size()] )[0] );
                assertEquals( "ISC", sFile.getLicenseInfoFromFiles().toArray( new AnyLicenseInfo[sFile.getLicenseInfoFromFiles().size()]  )[0].toString() );
            }
            else
            {
                assertEquals( "Default file comment", sFile.getComment().get() );
                assertEquals( "Copyright (c) 2012, 2013, 2014 Source Auditor Inc.", sFile.getCopyrightText() );
                assertEquals( "Default file license comment", sFile.getLicenseComments().get() );
                assertEquals( "Default file notice", sFile.getNoticeText().get() );
                assertEquals( "Apache-2.0", sFile.getLicenseConcluded().toString() );
                assertEquals( 2, sFile.getFileContributors().size() );
                assertEquals( "Apache-1.1", sFile.getLicenseInfoFromFiles().toArray( new AnyLicenseInfo[sFile.getLicenseInfoFromFiles().size()] )[0].toString() );
            }
            filePaths.remove( sFile.getName().get() );
        }
        assertEquals( 0, filePaths.size() );
        List<SpdxSnippet> snippets = new ArrayList<>();
        SpdxModelFactory.getSpdxObjects( modelStore, copyManager, SpdxConstantsCompatV2.CLASS_SPDX_SNIPPET, 
                                         null, result.getIdPrefix() ).forEach( (snippet) -> {
            snippets.add( (SpdxSnippet)snippet );
        });
        assertEquals( 2, snippets.size() );
        Collections.sort( snippets );
        assertEquals( "Snippet Comment", snippets.get( 0 ).getComment().get() );
        assertEquals( "Snippet Copyright Text", snippets.get( 0 ).getCopyrightText() );
        assertEquals( "Snippet License Comment", snippets.get( 0 ).getLicenseComments().get() );
        assertEquals( "SnippetName", snippets.get( 0 ).getName().get() );
        assertEquals( "1231:3442",
                TestSpdxV2FileCollector.startEndPointerToString( snippets.get( 0 ).getByteRange() ) );
        assertEquals( "BSD-2-Clause", snippets.get( 0 ).getLicenseConcluded().toString() );
        assertEquals( "BSD-2-Clause-FreeBSD", snippets.get( 0 ).getLicenseInfoFromFiles().toArray( new AnyLicenseInfo[snippets.get( 0 ).getLicenseInfoFromFiles().size()] )[0].toString() );
        assertEquals( "44:55", TestSpdxV2FileCollector.startEndPointerToString( snippets.get( 0 ).getLineRange().get() ) );
        assertEquals( fileWithSnippet, snippets.get( 0 ).getSnippetFromFile().getId() );

        assertEquals( "Snippet Comment2", snippets.get( 1 ).getComment().get() );
        assertEquals( "Snippet2 Copyright Text", snippets.get( 1 ).getCopyrightText() );
        assertEquals( "Snippet2 License Comment", snippets.get( 1 ).getLicenseComments().get() );
        assertEquals( "SnippetName2", snippets.get( 1 ).getName().get() );
        assertEquals( "31231:33442",
                TestSpdxV2FileCollector.startEndPointerToString( snippets.get( 1 ).getByteRange() ) );
        assertEquals( "MITNFA", snippets.get( 1 ).getLicenseConcluded().toString() );
        assertEquals( "LicenseRef-testLicense", snippets.get( 1 ).getLicenseInfoFromFiles().toArray( new AnyLicenseInfo[snippets.get( 1 ).getLicenseInfoFromFiles().size()] )[0].toString() );
        assertEquals( "444:554",
                TestSpdxV2FileCollector.startEndPointerToString( snippets.get( 1 ).getLineRange().get() ) );
        assertEquals( fileWithSnippet, snippets.get( 1 ).getSnippetFromFile().getId() );
        //TODO Test dependencies
    }
    
    @Test
    public void testExecuteUseArtfactId() throws Exception
    {
        File testPom = new File( getBasedir(), UNIT_TEST_RESOURCE_DIR + "/json-pom-use-artifact.xml" );
        //        CreateSpdxMojo mojo = (CreateSpdxMojo) configureMojo( myMojo, "spdx-maven-plugin", testPom );
        // if the below does not work due to a lookup error, run mvn test goal
        CreateSpdxMojo mojo = (CreateSpdxMojo) lookupMojo( "createSPDX", testPom );
        assertNotNull( mojo );
        mojo.execute();
        // Test SPDX filename parameter
        File spdxFile = new File( getBasedir(), SPDX_JSON_FILE_NAME );
        assertTrue( spdxFile.exists() );
        // Test output artifact file is created
        File artifactFile = getTestFile(
                "target/test-classes/unit/spdx-maven-plugin-test/spdx maven plugin test.spdx.json" );
        assertTrue( artifactFile.exists() );
        ISerializableModelStore modelStore = new MultiFormatStore( new InMemSpdxStore(), Format.JSON );
        ModelCopyManager copyManager = new ModelCopyManager();
        SpdxDocument result;
        try ( InputStream is = new FileInputStream( artifactFile.getAbsolutePath() ) )
        {
            result = (SpdxDocument)modelStore.deSerialize( is, false );
        }
        List<String> warnings = result.verify();
        assertEquals( 0, warnings.size() );
        // Test configuration parameters found in the test resources pom.xml file
        // Document namespace
        assertEquals( "http://spdx.org/documents/spdx%20toolsv2.0%20rc1", result.getDocumentUri() );
        // Non-standard licenses
        ExtractedLicenseInfo[] licenseInfos = result.getExtractedLicenseInfos().toArray( new ExtractedLicenseInfo[result.getExtractedLicenseInfos().size()] );
        assertEquals( 2, licenseInfos.length );
        ExtractedLicenseInfo testLicense1 = null;
        ExtractedLicenseInfo testLicense2 = null;
        for ( ExtractedLicenseInfo li : licenseInfos )
        {
            if ( li.getLicenseId().equals( "LicenseRef-testLicense" ) )
            {
                testLicense1 = li;
            }
            else if ( li.getLicenseId().equals( "LicenseRef-testLicense2" ) )
            {
                testLicense2 = li;
            }
        }
        assertTrue( testLicense1 != null );
        assertEquals( "Test License", testLicense1.getName() );
        assertEquals( "Test license text", testLicense1.getExtractedText() );
        assertEquals( 1, testLicense1.getSeeAlso().size() );
        assertEquals( "http://www.test.url/testLicense.html", testLicense1.getSeeAlso().toArray( new String[testLicense1.getSeeAlso().size()]  )[0] );
        assertEquals( "Test license comment", testLicense1.getComment() );

        assertTrue( testLicense2 != null );
        assertEquals( "Second Test License", testLicense2.getName() );
        assertEquals( "Second est license text", testLicense2.getExtractedText() );
        assertEquals( 2, testLicense2.getSeeAlso().size() );
        boolean foundSeeAlso1 = false;
        boolean foundSeeAlso2 = false;
        for ( String seeAlso : testLicense2.getSeeAlso() )
        {
            if ( seeAlso.equals( "http://www.test.url/testLicense2.html" ) )
            {
                foundSeeAlso1 = true;
            }
            else if ( seeAlso.equals( "http://www.test.url/testLicense2-alt.html" ) )
            {
                foundSeeAlso2 = true;
            }
        }
        assertTrue( foundSeeAlso1 );
        assertTrue( foundSeeAlso2 );
        assertEquals( "Second Test license comment", testLicense2.getComment() );
        // documentComment
        assertEquals( "Document Comment", result.getComment().get() );
        // documentAnnotations
        assertEquals( 2, result.getAnnotations().size() );
        org.spdx.library.model.v2.Annotation annotation1 = null;
        org.spdx.library.model.v2.Annotation annotation2 = null;
        for ( org.spdx.library.model.v2.Annotation annotation : result.getAnnotations() )
        {
            if ( annotation.getComment().equals( "Annotation1" ) )
            {
                annotation1 = annotation;
            }
            else if ( annotation.getComment().equals( "Annotation2" ) )
            {
                annotation2 = annotation;
            }
        }
        assertTrue( annotation1 != null );
        assertEquals( "2010-01-29T18:30:22Z", annotation1.getAnnotationDate() );
        assertEquals( "Person:Test Person", annotation1.getAnnotator() );
        assertEquals( AnnotationType.REVIEW, annotation1.getAnnotationType() );

        assertTrue( annotation2 != null );
        assertEquals( "2012-11-29T18:30:22Z", annotation2.getAnnotationDate() );
        assertEquals( "Organization:Test Organization", annotation2.getAnnotator() );
        assertEquals( AnnotationType.OTHER, annotation2.getAnnotationType() );
        //creatorComment
        assertEquals( "Creator comment", result.getCreationInfo().getComment().get() );
        // creators
        assertEquals( 3, result.getCreationInfo().getCreators().size() );
        boolean foundCreator1 = false;
        boolean foundCreator2 = false;
        for ( String creator : result.getCreationInfo().getCreators() )
        {
            if ( creator.equals( "Person: Creator1" ) )
            {
                foundCreator1 = true;
            }
            else if ( creator.equals( "Person: Creator2" ) )
            {
                foundCreator2 = true;
            }
        }
        assertTrue( foundCreator1 );
        assertTrue( foundCreator2 );
        // package parameters
        assertEquals( 1, result.getDocumentDescribes().size() );
        SpdxElement described = result.getDocumentDescribes().toArray( new SpdxElement[result.getDocumentDescribes().size()] )[0];
        assertTrue( described instanceof SpdxPackage );
        SpdxPackage pkg = (SpdxPackage) described;
        // name
        assertEquals( "org.spdx:spdx maven plugin test", pkg.getName().get() );
        // packageAnnotations
        assertEquals( 1, pkg.getAnnotations().size() );
        org.spdx.library.model.v2.Annotation annotation = pkg.getAnnotations().toArray( new org.spdx.library.model.v2.Annotation [pkg.getAnnotations().size()] )[0];
        assertEquals( "PackageAnnotation", annotation.getComment() );
        assertEquals( "2015-01-29T18:30:22Z", annotation.getAnnotationDate() );
        assertEquals( "Person:Test Package Person", annotation.getAnnotator() );
        assertEquals( AnnotationType.REVIEW, annotation.getAnnotationType() );
        //licenseDeclared
        AnyLicenseInfo licenseDeclared = LicenseInfoFactory.getListedLicenseByIdCompatV2( "BSD-2-Clause" );
        assertEquals( licenseDeclared, pkg.getLicenseDeclared() );
        //licenseConcluded
        AnyLicenseInfo licenseConcluded = LicenseInfoFactory.getListedLicenseByIdCompatV2( "BSD-3-Clause" );
        assertEquals( licenseConcluded, pkg.getLicenseConcluded() );
        //licenseComments
        assertEquals( "License comments", pkg.getLicenseComments().get() );
        //originator
        assertEquals( "Organization: Originating org.", pkg.getOriginator().get() );
        // sourceInfo
        assertEquals( "Source info", pkg.getSourceInfo().get() );
        //copyrightText
        assertEquals( "Copyright Text for Package", pkg.getCopyrightText() );
        //external Refs
        ExternalRef[] externalRefs = pkg.getExternalRefs().toArray( new ExternalRef[pkg.getExternalRefs().size()] );
        assertEquals( 2, externalRefs.length );
        if ( externalRefs[0].getReferenceCategory().equals( ReferenceCategory.SECURITY ) )
        {
            assertEquals( "extref comment1", externalRefs[0].getComment().get() );
            assertEquals( "example-locator-CPE22Type", externalRefs[0].getReferenceLocator() );
            assertEquals( "cpe22Type", ListedReferenceTypes.getListedReferenceTypes().getListedReferenceName(
                    new URI( externalRefs[0].getReferenceType().getIndividualURI() ) ) );
            assertEquals( "extref comment2", externalRefs[1].getComment().get() );
            assertEquals( "org.apache.tomcat:tomcat:9.0.0.M4", externalRefs[1].getReferenceLocator() );
            assertEquals( "maven-central", ListedReferenceTypes.getListedReferenceTypes().getListedReferenceName(
                    new URI( externalRefs[1].getReferenceType().getIndividualURI() ) ) );
        }
        else if ( externalRefs[0].getReferenceCategory().equals(
                ReferenceCategory.PACKAGE_MANAGER ) )
        {
            assertEquals( "extref comment1", externalRefs[1].getComment().get() );
            assertEquals( "example-locator-CPE22Type", externalRefs[1].getReferenceLocator() );
            assertEquals( "cpe22Type", ListedReferenceTypes.getListedReferenceTypes().getListedReferenceName(
                    new URI(externalRefs[1].getReferenceType().getIndividualURI() ) ) );
            assertEquals( "extref comment2", externalRefs[0].getComment().get() );
            assertEquals( "org.apache.tomcat:tomcat:9.0.0.M4", externalRefs[0].getReferenceLocator() );
            assertEquals( "maven-central", ListedReferenceTypes.getListedReferenceTypes().getListedReferenceName(
                    new URI( externalRefs[0].getReferenceType().getIndividualURI() ) ) );
        }
        else
        {
            fail( "Unexpected reference category" );
        }
        // Test all files are included
        List<String> filePaths = new ArrayList<>();
        File sourceDir = new File( getBasedir(), UNIT_TEST_RESOURCE_DIR + "/src/main" );
        addFilePaths( getBasedir() + UNIT_TEST_RESOURCE_DIR, sourceDir, filePaths );
        File testDir = new File( getBasedir(), UNIT_TEST_RESOURCE_DIR + "/src/test" );
        addFilePaths( getBasedir() + UNIT_TEST_RESOURCE_DIR, testDir, filePaths );
        @SuppressWarnings( "unused" )
        File resourceDir = new File( getBasedir(), UNIT_TEST_RESOURCE_DIR + "/src/resources" );
        //TODO: Add resource to project stub and uncomment the line below
        //        addFilePaths( getBasedir() + UNIT_TEST_RESOURCE_DIR, resourceDir, filePaths );
        Collection<SpdxFile> pkgFiles = pkg.getFiles();
        assertEquals( filePaths.size(), pkgFiles.size() );
        String fileWithSnippet = null;
        for ( SpdxFile sFile : pkgFiles )
        {
            if ( sFile.getName().get().equals( "./src/main/java/CommonCode.java" ) )
            {
                fileWithSnippet = sFile.getId();
                assertEquals( "Comment for CommonCode", sFile.getComment().get() );
                assertEquals( "Common Code Copyright", sFile.getCopyrightText() );
                assertEquals( "License Comment for Common Code", sFile.getLicenseComments().get() );
                assertEquals( "Notice for Commmon Code", sFile.getNoticeText().get() );
                assertEquals( "EPL-1.0", sFile.getLicenseConcluded().toString() );
                assertEquals( "Contributor to CommonCode", sFile.getFileContributors().toArray( new String[sFile.getFileContributors().size()] )[0] );
                assertEquals( "ISC", sFile.getLicenseInfoFromFiles().toArray( new AnyLicenseInfo[sFile.getLicenseInfoFromFiles().size()]  )[0].toString() );
            }
            else
            {
                assertEquals( "Default file comment", sFile.getComment().get() );
                assertEquals( "Copyright (c) 2012, 2013, 2014 Source Auditor Inc.", sFile.getCopyrightText() );
                assertEquals( "Default file license comment", sFile.getLicenseComments().get() );
                assertEquals( "Default file notice", sFile.getNoticeText().get() );
                assertEquals( "Apache-2.0", sFile.getLicenseConcluded().toString() );
                assertEquals( 2, sFile.getFileContributors().size() );
                assertEquals( "Apache-1.1", sFile.getLicenseInfoFromFiles().toArray( new AnyLicenseInfo[sFile.getLicenseInfoFromFiles().size()] )[0].toString() );
            }
            filePaths.remove( sFile.getName().get() );
        }
        assertEquals( 0, filePaths.size() );
        List<SpdxSnippet> snippets = new ArrayList<>();
        SpdxModelFactory.getSpdxObjects( modelStore, copyManager, SpdxConstantsCompatV2.CLASS_SPDX_SNIPPET, 
                                         null, result.getIdPrefix() ).forEach( (snippet) -> {
            snippets.add( (SpdxSnippet)snippet );
        });
        assertEquals( 2, snippets.size() );
        Collections.sort( snippets );
        assertEquals( "Snippet Comment", snippets.get( 0 ).getComment().get() );
        assertEquals( "Snippet Copyright Text", snippets.get( 0 ).getCopyrightText() );
        assertEquals( "Snippet License Comment", snippets.get( 0 ).getLicenseComments().get() );
        assertEquals( "SnippetName", snippets.get( 0 ).getName().get() );
        assertEquals( "1231:3442",
                TestSpdxV2FileCollector.startEndPointerToString( snippets.get( 0 ).getByteRange() ) );
        assertEquals( "BSD-2-Clause", snippets.get( 0 ).getLicenseConcluded().toString() );
        assertEquals( "BSD-2-Clause-FreeBSD", snippets.get( 0 ).getLicenseInfoFromFiles().toArray( new AnyLicenseInfo[snippets.get( 0 ).getLicenseInfoFromFiles().size()] )[0].toString() );
        assertEquals( "44:55", TestSpdxV2FileCollector.startEndPointerToString( snippets.get( 0 ).getLineRange().get() ) );
        assertEquals( fileWithSnippet, snippets.get( 0 ).getSnippetFromFile().getId() );

        assertEquals( "Snippet Comment2", snippets.get( 1 ).getComment().get() );
        assertEquals( "Snippet2 Copyright Text", snippets.get( 1 ).getCopyrightText() );
        assertEquals( "Snippet2 License Comment", snippets.get( 1 ).getLicenseComments().get() );
        assertEquals( "SnippetName2", snippets.get( 1 ).getName().get() );
        assertEquals( "31231:33442",
                TestSpdxV2FileCollector.startEndPointerToString( snippets.get( 1 ).getByteRange() ) );
        assertEquals( "MITNFA", snippets.get( 1 ).getLicenseConcluded().toString() );
        assertEquals( "LicenseRef-testLicense", snippets.get( 1 ).getLicenseInfoFromFiles().toArray( new AnyLicenseInfo[snippets.get( 1 ).getLicenseInfoFromFiles().size()] )[0].toString() );
        assertEquals( "444:554",
                TestSpdxV2FileCollector.startEndPointerToString( snippets.get( 1 ).getLineRange().get() ) );
        assertEquals( fileWithSnippet, snippets.get( 1 ).getSnippetFromFile().getId() );
        //TODO Test dependencies
    }

    @Test
    public void testExecuteJson() throws Exception
    {
        File testPom = new File( getBasedir(), UNIT_TEST_RESOURCE_DIR + "/json-pom.xml" );
        //        CreateSpdxMojo mojo = (CreateSpdxMojo) configureMojo( myMojo, "spdx-maven-plugin", testPom );
        // if the below does not work due to a lookup error, run mvn test goal
        CreateSpdxMojo mojo = (CreateSpdxMojo) lookupMojo( "createSPDX", testPom );
        assertNotNull( mojo );
        mojo.execute();
        // Test SPDX filename parameter
        File spdxFile = new File( getBasedir(), SPDX_JSON_FILE_NAME );
        assertTrue( spdxFile.exists() );
        // Test output artifact file is created
        File artifactFile = getTestFile(
                "target/test-classes/unit/spdx-maven-plugin-test/spdx maven plugin test.spdx.json" );
        assertTrue( artifactFile.exists() );
        ISerializableModelStore modelStore = new MultiFormatStore( new InMemSpdxStore(), Format.JSON );
        ModelCopyManager copyManager = new ModelCopyManager();
        SpdxDocument result;
        try ( InputStream is = new FileInputStream( artifactFile.getAbsolutePath() ) )
        {
            result = (SpdxDocument)modelStore.deSerialize( is, false );
        }
        List<String> warnings = result.verify();
        assertEquals( 0, warnings.size() );
        // Test configuration parameters found in the test resources pom.xml file
        // Document namespace
        assertEquals( "http://spdx.org/documents/spdx%20toolsv2.0%20rc1", result.getDocumentUri() );
        // Non-standard licenses
        ExtractedLicenseInfo[] licenseInfos = result.getExtractedLicenseInfos().toArray( new ExtractedLicenseInfo[result.getExtractedLicenseInfos().size()] );
        assertEquals( 2, licenseInfos.length );
        ExtractedLicenseInfo testLicense1 = null;
        ExtractedLicenseInfo testLicense2 = null;
        for ( ExtractedLicenseInfo li : licenseInfos )
        {
            if ( li.getLicenseId().equals( "LicenseRef-testLicense" ) )
            {
                testLicense1 = li;
            }
            else if ( li.getLicenseId().equals( "LicenseRef-testLicense2" ) )
            {
                testLicense2 = li;
            }
        }
        assertTrue( testLicense1 != null );
        assertEquals( "Test License", testLicense1.getName() );
        assertEquals( "Test license text", testLicense1.getExtractedText() );
        assertEquals( 1, testLicense1.getSeeAlso().size() );
        assertEquals( "http://www.test.url/testLicense.html", testLicense1.getSeeAlso().toArray( new String[testLicense1.getSeeAlso().size()]  )[0] );
        assertEquals( "Test license comment", testLicense1.getComment() );

        assertTrue( testLicense2 != null );
        assertEquals( "Second Test License", testLicense2.getName() );
        assertEquals( "Second est license text", testLicense2.getExtractedText() );
        assertEquals( 2, testLicense2.getSeeAlso().size() );
        boolean foundSeeAlso1 = false;
        boolean foundSeeAlso2 = false;
        for ( String seeAlso : testLicense2.getSeeAlso() )
        {
            if ( seeAlso.equals( "http://www.test.url/testLicense2.html" ) )
            {
                foundSeeAlso1 = true;
            }
            else if ( seeAlso.equals( "http://www.test.url/testLicense2-alt.html" ) )
            {
                foundSeeAlso2 = true;
            }
        }
        assertTrue( foundSeeAlso1 );
        assertTrue( foundSeeAlso2 );
        assertEquals( "Second Test license comment", testLicense2.getComment() );
        // documentComment
        assertEquals( "Document Comment", result.getComment().get() );
        // documentAnnotations
        assertEquals( 2, result.getAnnotations().size() );
        org.spdx.library.model.v2.Annotation annotation1 = null;
        org.spdx.library.model.v2.Annotation annotation2 = null;
        for ( org.spdx.library.model.v2.Annotation annotation : result.getAnnotations() )
        {
            if ( annotation.getComment().equals( "Annotation1" ) )
            {
                annotation1 = annotation;
            }
            else if ( annotation.getComment().equals( "Annotation2" ) )
            {
                annotation2 = annotation;
            }
        }
        assertTrue( annotation1 != null );
        assertEquals( "2010-01-29T18:30:22Z", annotation1.getAnnotationDate() );
        assertEquals( "Person:Test Person", annotation1.getAnnotator() );
        assertEquals( AnnotationType.REVIEW, annotation1.getAnnotationType() );

        assertTrue( annotation2 != null );
        assertEquals( "2012-11-29T18:30:22Z", annotation2.getAnnotationDate() );
        assertEquals( "Organization:Test Organization", annotation2.getAnnotator() );
        assertEquals( AnnotationType.OTHER, annotation2.getAnnotationType() );
        //creatorComment
        assertEquals( "Creator comment", result.getCreationInfo().getComment().get() );
        // creators
        assertEquals( 3, result.getCreationInfo().getCreators().size() );
        boolean foundCreator1 = false;
        boolean foundCreator2 = false;
        for ( String creator : result.getCreationInfo().getCreators() )
        {
            if ( creator.equals( "Person: Creator1" ) )
            {
                foundCreator1 = true;
            }
            else if ( creator.equals( "Person: Creator2" ) )
            {
                foundCreator2 = true;
            }
        }
        assertTrue( foundCreator1 );
        assertTrue( foundCreator2 );
        // package parameters
        assertEquals( 1, result.getDocumentDescribes().size() );
        SpdxElement described = result.getDocumentDescribes().toArray( new SpdxElement[result.getDocumentDescribes().size()] )[0];
        assertTrue( described instanceof SpdxPackage );
        SpdxPackage pkg = (SpdxPackage) described;
        // name
        assertEquals( "Test SPDX Plugin", pkg.getName().get() );
        // packageAnnotations
        assertEquals( 1, pkg.getAnnotations().size() );
        org.spdx.library.model.v2.Annotation annotation = pkg.getAnnotations().toArray( new org.spdx.library.model.v2.Annotation [pkg.getAnnotations().size()] )[0];
        assertEquals( "PackageAnnotation", annotation.getComment() );
        assertEquals( "2015-01-29T18:30:22Z", annotation.getAnnotationDate() );
        assertEquals( "Person:Test Package Person", annotation.getAnnotator() );
        assertEquals( AnnotationType.REVIEW, annotation.getAnnotationType() );
        //licenseDeclared
        AnyLicenseInfo licenseDeclared = LicenseInfoFactory.getListedLicenseByIdCompatV2( "BSD-2-Clause" );
        assertEquals( licenseDeclared, pkg.getLicenseDeclared() );
        //licenseConcluded
        AnyLicenseInfo licenseConcluded = LicenseInfoFactory.getListedLicenseByIdCompatV2( "BSD-3-Clause" );
        assertEquals( licenseConcluded, pkg.getLicenseConcluded() );
        //licenseComments
        assertEquals( "License comments", pkg.getLicenseComments().get() );
        //originator
        assertEquals( "Organization: Originating org.", pkg.getOriginator().get() );
        // sourceInfo
        assertEquals( "Source info", pkg.getSourceInfo().get() );
        //copyrightText
        assertEquals( "Copyright Text for Package", pkg.getCopyrightText() );
        //external Refs
        ExternalRef[] externalRefs = pkg.getExternalRefs().toArray( new ExternalRef[pkg.getExternalRefs().size()] );
        assertEquals( 2, externalRefs.length );
        if ( externalRefs[0].getReferenceCategory().equals( ReferenceCategory.SECURITY ) )
        {
            assertEquals( "extref comment1", externalRefs[0].getComment().get() );
            assertEquals( "example-locator-CPE22Type", externalRefs[0].getReferenceLocator() );
            assertEquals( "cpe22Type", ListedReferenceTypes.getListedReferenceTypes().getListedReferenceName(
                    new URI( externalRefs[0].getReferenceType().getIndividualURI() ) ) );
            assertEquals( "extref comment2", externalRefs[1].getComment().get() );
            assertEquals( "org.apache.tomcat:tomcat:9.0.0.M4", externalRefs[1].getReferenceLocator() );
            assertEquals( "maven-central", ListedReferenceTypes.getListedReferenceTypes().getListedReferenceName(
                    new URI( externalRefs[1].getReferenceType().getIndividualURI() ) ) );
        }
        else if ( externalRefs[0].getReferenceCategory().equals(
                ReferenceCategory.PACKAGE_MANAGER ) )
        {
            assertEquals( "extref comment1", externalRefs[1].getComment().get() );
            assertEquals( "example-locator-CPE22Type", externalRefs[1].getReferenceLocator() );
            assertEquals( "cpe22Type", ListedReferenceTypes.getListedReferenceTypes().getListedReferenceName(
                    new URI(externalRefs[1].getReferenceType().getIndividualURI() ) ) );
            assertEquals( "extref comment2", externalRefs[0].getComment().get() );
            assertEquals( "org.apache.tomcat:tomcat:9.0.0.M4", externalRefs[0].getReferenceLocator() );
            assertEquals( "maven-central", ListedReferenceTypes.getListedReferenceTypes().getListedReferenceName(
                    new URI( externalRefs[0].getReferenceType().getIndividualURI() ) ) );
        }
        else
        {
            fail( "Unexpected reference category" );
        }
        // Test all files are included
        List<String> filePaths = new ArrayList<>();
        File sourceDir = new File( getBasedir(), UNIT_TEST_RESOURCE_DIR + "/src/main" );
        addFilePaths( getBasedir() + UNIT_TEST_RESOURCE_DIR, sourceDir, filePaths );
        File testDir = new File( getBasedir(), UNIT_TEST_RESOURCE_DIR + "/src/test" );
        addFilePaths( getBasedir() + UNIT_TEST_RESOURCE_DIR, testDir, filePaths );
        @SuppressWarnings( "unused" )
        File resourceDir = new File( getBasedir(), UNIT_TEST_RESOURCE_DIR + "/src/resources" );
        //TODO: Add resource to project stub and uncomment the line below
        //        addFilePaths( getBasedir() + UNIT_TEST_RESOURCE_DIR, resourceDir, filePaths );
        Collection<SpdxFile> pkgFiles = pkg.getFiles();
        assertEquals( filePaths.size(), pkgFiles.size() );
        String fileWithSnippet = null;
        for ( SpdxFile sFile : pkgFiles )
        {
            if ( sFile.getName().get().equals( "./src/main/java/CommonCode.java" ) )
            {
                fileWithSnippet = sFile.getId();
                assertEquals( "Comment for CommonCode", sFile.getComment().get() );
                assertEquals( "Common Code Copyright", sFile.getCopyrightText() );
                assertEquals( "License Comment for Common Code", sFile.getLicenseComments().get() );
                assertEquals( "Notice for Commmon Code", sFile.getNoticeText().get() );
                assertEquals( "EPL-1.0", sFile.getLicenseConcluded().toString() );
                assertEquals( "Contributor to CommonCode", sFile.getFileContributors().toArray( new String[sFile.getFileContributors().size()] )[0] );
                assertEquals( "ISC", sFile.getLicenseInfoFromFiles().toArray( new AnyLicenseInfo[sFile.getLicenseInfoFromFiles().size()]  )[0].toString() );
            }
            else
            {
                assertEquals( "Default file comment", sFile.getComment().get() );
                assertEquals( "Copyright (c) 2012, 2013, 2014 Source Auditor Inc.", sFile.getCopyrightText() );
                assertEquals( "Default file license comment", sFile.getLicenseComments().get() );
                assertEquals( "Default file notice", sFile.getNoticeText().get() );
                assertEquals( "Apache-2.0", sFile.getLicenseConcluded().toString() );
                assertEquals( 2, sFile.getFileContributors().size() );
                assertEquals( "Apache-1.1", sFile.getLicenseInfoFromFiles().toArray( new AnyLicenseInfo[sFile.getLicenseInfoFromFiles().size()] )[0].toString() );
            }
            filePaths.remove( sFile.getName().get() );
        }
        assertEquals( 0, filePaths.size() );
        List<SpdxSnippet> snippets = new ArrayList<>();
        SpdxModelFactory.getSpdxObjects( modelStore, copyManager, SpdxConstantsCompatV2.CLASS_SPDX_SNIPPET, 
                                         null, result.getIdPrefix() ).forEach( (snippet) -> {
            snippets.add( (SpdxSnippet)snippet );
        });
        assertEquals( 2, snippets.size() );
        Collections.sort( snippets );
        assertEquals( "Snippet Comment", snippets.get( 0 ).getComment().get() );
        assertEquals( "Snippet Copyright Text", snippets.get( 0 ).getCopyrightText() );
        assertEquals( "Snippet License Comment", snippets.get( 0 ).getLicenseComments().get() );
        assertEquals( "SnippetName", snippets.get( 0 ).getName().get() );
        assertEquals( "1231:3442",
                TestSpdxV2FileCollector.startEndPointerToString( snippets.get( 0 ).getByteRange() ) );
        assertEquals( "BSD-2-Clause", snippets.get( 0 ).getLicenseConcluded().toString() );
        assertEquals( "BSD-2-Clause-FreeBSD", snippets.get( 0 ).getLicenseInfoFromFiles().toArray( new AnyLicenseInfo[snippets.get( 0 ).getLicenseInfoFromFiles().size()] )[0].toString() );
        assertEquals( "44:55", TestSpdxV2FileCollector.startEndPointerToString( snippets.get( 0 ).getLineRange().get() ) );
        assertEquals( fileWithSnippet, snippets.get( 0 ).getSnippetFromFile().getId() );

        assertEquals( "Snippet Comment2", snippets.get( 1 ).getComment().get() );
        assertEquals( "Snippet2 Copyright Text", snippets.get( 1 ).getCopyrightText() );
        assertEquals( "Snippet2 License Comment", snippets.get( 1 ).getLicenseComments().get() );
        assertEquals( "SnippetName2", snippets.get( 1 ).getName().get() );
        assertEquals( "31231:33442",
                TestSpdxV2FileCollector.startEndPointerToString( snippets.get( 1 ).getByteRange() ) );
        assertEquals( "MITNFA", snippets.get( 1 ).getLicenseConcluded().toString() );
        assertEquals( "LicenseRef-testLicense", snippets.get( 1 ).getLicenseInfoFromFiles().toArray( new AnyLicenseInfo[snippets.get( 1 ).getLicenseInfoFromFiles().size()] )[0].toString() );
        assertEquals( "444:554",
                TestSpdxV2FileCollector.startEndPointerToString( snippets.get( 1 ).getLineRange().get() ) );
        assertEquals( fileWithSnippet, snippets.get( 1 ).getSnippetFromFile().getId() );
        //TODO Test dependencies
    }
    
    /**
     * Add relative file paths to the filePaths list
     *
     * @param prefix    Absolute path of the directory to which the filepaths are relative
     * @param dir       Directory of files to add
     * @param filePaths return list of file paths to which paths are added
     */
    private void addFilePaths( String prefix, File dir, List<String> filePaths )
    {
        if ( !dir.exists() )
        {
            return;
        }
        if ( !dir.isDirectory() )
        {
            filePaths.add( "./" + dir.getAbsolutePath().substring( prefix.length() + 2 ).replace( '\\', '/' ) );
            return;
        }
        File[] files = dir.listFiles();
        for ( File file : files )
        {
            if ( file.isDirectory() )
            {
                addFilePaths( prefix, file, filePaths );
            }
            else
            {
                filePaths.add( "./" + file.getAbsolutePath().substring( prefix.length() + 2 ).replace( '\\', '/' ) );
            }
        }
    }

    @Test
    public void testmatchLicensesOnCrossReferenceUrls()
    {
        //TODO Implement testcase - parameter matLicensesOnCrossReferenceUrls=false
    }
    
    @Test
    public void testExecuteUriNotUrl()  throws Exception
    {
        File testPom = new File( getBasedir(), UNIT_TEST_RESOURCE_DIR + "/uri-pom.xml" );
        //        CreateSpdxMojo mojo = (CreateSpdxMojo) configureMojo( myMojo, "spdx-maven-plugin", testPom );
        // if the below does not work due to a lookup error, run mvn test goal
        CreateSpdxMojo mojo = (CreateSpdxMojo) lookupMojo( "createSPDX", testPom );
        assertNotNull( mojo );
        mojo.execute();
        File spdxFile = new File( getBasedir(), SPDX_FILE_NAME );
        assertTrue( spdxFile.exists() );
        // Test output artifact file is created
        File artifactFile = getTestFile(
                "target/test-classes/unit/spdx-maven-plugin-test/spdx maven plugin test.spdx.rdf.xml" );
        assertTrue( artifactFile.exists() );
        try ( ISerializableModelStore modelStore = new RdfStore() ) {
            SpdxDocument result;
            try ( InputStream is = new FileInputStream( artifactFile.getAbsolutePath() ) )
            {
                result = (SpdxDocument)modelStore.deSerialize( is, false );
            }
            List<String> warnings = result.verify();
            assertEquals( 0, warnings.size() );
            // Test configuration parameters found in the test resources pom.xml file
            // Document namespace
            assertEquals( "spdx://sbom.foobar.dev/2.3/test-package-1.1.0", result.getDocumentUri() );
        }
    }
    
    @Test
    public void testExecuteNoContributors() throws Exception
    // Test regression for issue #53
    {
        File testPom = new File( getBasedir(), UNIT_TEST_RESOURCE_DIR + "/pom-with-no-contributors.xml" );
        //        CreateSpdxMojo mojo = (CreateSpdxMojo) configureMojo( myMojo, "spdx-maven-plugin", testPom );
        // if the below does not work due to a lookup error, run mvn test goal
        CreateSpdxMojo mojo = (CreateSpdxMojo) lookupMojo( "createSPDX", testPom );
        assertNotNull( mojo );
        mojo.execute();
        // Test SPDX filename parameter
        File spdxFile = new File( getBasedir(), SPDX_FILE_NAME );
        assertTrue( spdxFile.exists() );
        // Test output artifact file is created
        File artifactFile = getTestFile(
                "target/test-classes/unit/spdx-maven-plugin-test/spdx maven plugin test.spdx.rdf.xml" );
        assertTrue( artifactFile.exists() );
        ISerializableModelStore modelStore = new RdfStore();
        ModelCopyManager copyManager = new ModelCopyManager();
        SpdxDocument result;
        try ( InputStream is = new FileInputStream( artifactFile.getAbsolutePath() ) )
        {
            result = (SpdxDocument)modelStore.deSerialize( is, false );
        }
        List<String> warnings = result.verify();
        assertEquals( 0, warnings.size() );
        // Test configuration parameters found in the test resources pom.xml file
        // Document namespace
        assertEquals( "http://spdx.org/documents/spdx%20toolsv2.0%20rc1", result.getDocumentUri() );
        // Non-standard licenses
        ExtractedLicenseInfo[] licenseInfos = result.getExtractedLicenseInfos().toArray( new ExtractedLicenseInfo[result.getExtractedLicenseInfos().size()] );
        assertEquals( 2, licenseInfos.length );
        ExtractedLicenseInfo testLicense1 = null;
        ExtractedLicenseInfo testLicense2 = null;
        for ( ExtractedLicenseInfo li : licenseInfos )
        {
            if ( li.getLicenseId().equals( "LicenseRef-testLicense" ) )
            {
                testLicense1 = li;
            }
            else if ( li.getLicenseId().equals( "LicenseRef-testLicense2" ) )
            {
                testLicense2 = li;
            }
        }
        assertTrue( testLicense1 != null );
        assertEquals( "Test License", testLicense1.getName() );
        assertEquals( "Test license text", testLicense1.getExtractedText() );
        assertEquals( 1, testLicense1.getSeeAlso().size() );
        assertEquals( "http://www.test.url/testLicense.html", testLicense1.getSeeAlso().toArray( new String[testLicense1.getSeeAlso().size()]  )[0] );
        assertEquals( "Test license comment", testLicense1.getComment() );

        assertTrue( testLicense2 != null );
        assertEquals( "Second Test License", testLicense2.getName() );
        assertEquals( "Second est license text", testLicense2.getExtractedText() );
        assertEquals( 2, testLicense2.getSeeAlso().size() );
        boolean foundSeeAlso1 = false;
        boolean foundSeeAlso2 = false;
        for ( String seeAlso : testLicense2.getSeeAlso() )
        {
            if ( seeAlso.equals( "http://www.test.url/testLicense2.html" ) )
            {
                foundSeeAlso1 = true;
            }
            else if ( seeAlso.equals( "http://www.test.url/testLicense2-alt.html" ) )
            {
                foundSeeAlso2 = true;
            }
        }
        assertTrue( foundSeeAlso1 );
        assertTrue( foundSeeAlso2 );
        assertEquals( "Second Test license comment", testLicense2.getComment() );
        // documentComment
        assertEquals( "Document Comment", result.getComment().get() );
        // documentAnnotations
        assertEquals( 2, result.getAnnotations().size() );
        org.spdx.library.model.v2.Annotation annotation1 = null;
        org.spdx.library.model.v2.Annotation annotation2 = null;
        for ( org.spdx.library.model.v2.Annotation annotation : result.getAnnotations() )
        {
            if ( annotation.getComment().equals( "Annotation1" ) )
            {
                annotation1 = annotation;
            }
            else if ( annotation.getComment().equals( "Annotation2" ) )
            {
                annotation2 = annotation;
            }
        }
        assertTrue( annotation1 != null );
        assertEquals( "2010-01-29T18:30:22Z", annotation1.getAnnotationDate() );
        assertEquals( "Person:Test Person", annotation1.getAnnotator() );
        assertEquals( AnnotationType.REVIEW, annotation1.getAnnotationType() );

        assertTrue( annotation2 != null );
        assertEquals( "2012-11-29T18:30:22Z", annotation2.getAnnotationDate() );
        assertEquals( "Organization:Test Organization", annotation2.getAnnotator() );
        assertEquals( AnnotationType.OTHER, annotation2.getAnnotationType() );
        //creatorComment
        assertEquals( "Creator comment", result.getCreationInfo().getComment().get() );
        // creators
        assertEquals( 3, result.getCreationInfo().getCreators().size() );
        boolean foundCreator1 = false;
        boolean foundCreator2 = false;
        for ( String creator : result.getCreationInfo().getCreators() )
        {
            if ( creator.equals( "Person: Creator1" ) )
            {
                foundCreator1 = true;
            }
            else if ( creator.equals( "Person: Creator2" ) )
            {
                foundCreator2 = true;
            }
        }
        assertTrue( foundCreator1 );
        assertTrue( foundCreator2 );
        // package parameters
        assertEquals( 1, result.getDocumentDescribes().size() );
        SpdxElement described = result.getDocumentDescribes().toArray( new SpdxElement[result.getDocumentDescribes().size()] )[0];
        assertTrue( described instanceof SpdxPackage );
        SpdxPackage pkg = (SpdxPackage) described;
        // packageAnnotations
        assertEquals( 1, pkg.getAnnotations().size() );
        org.spdx.library.model.v2.Annotation annotation = pkg.getAnnotations().toArray( new org.spdx.library.model.v2.Annotation [pkg.getAnnotations().size()] )[0];
        assertEquals( "PackageAnnotation", annotation.getComment() );
        assertEquals( "2015-01-29T18:30:22Z", annotation.getAnnotationDate() );
        assertEquals( "Person:Test Package Person", annotation.getAnnotator() );
        assertEquals( AnnotationType.REVIEW, annotation.getAnnotationType() );
        //licenseDeclared
        AnyLicenseInfo licenseDeclared = LicenseInfoFactory.getListedLicenseByIdCompatV2( "BSD-2-Clause" );
        assertEquals( licenseDeclared, pkg.getLicenseDeclared() );
        //licenseConcluded
        AnyLicenseInfo licenseConcluded = LicenseInfoFactory.getListedLicenseByIdCompatV2( "BSD-3-Clause" );
        assertEquals( licenseConcluded, pkg.getLicenseConcluded() );
        //licenseComments
        assertEquals( "License comments", pkg.getLicenseComments().get() );
        //originator
        assertEquals( "Organization: Originating org.", pkg.getOriginator().get() );
        // sourceInfo
        assertEquals( "Source info", pkg.getSourceInfo().get() );
        //copyrightText
        assertEquals( "Copyright Text for Package", pkg.getCopyrightText() );
        //external Refs
        ExternalRef[] externalRefs = pkg.getExternalRefs().toArray( new ExternalRef[pkg.getExternalRefs().size()] );
        assertEquals( 2, externalRefs.length );
        if ( externalRefs[0].getReferenceCategory().equals( ReferenceCategory.SECURITY ) )
        {
            assertEquals( "extref comment1", externalRefs[0].getComment().get() );
            assertEquals( "example-locator-CPE22Type", externalRefs[0].getReferenceLocator() );
            assertEquals( "cpe22Type", ListedReferenceTypes.getListedReferenceTypes().getListedReferenceName(
                    new URI( externalRefs[0].getReferenceType().getIndividualURI() ) ) );
            assertEquals( "extref comment2", externalRefs[1].getComment().get() );
            assertEquals( "org.apache.tomcat:tomcat:9.0.0.M4", externalRefs[1].getReferenceLocator() );
            assertEquals( "maven-central", ListedReferenceTypes.getListedReferenceTypes().getListedReferenceName(
                    new URI( externalRefs[1].getReferenceType().getIndividualURI() ) ) );
        }
        else if ( externalRefs[0].getReferenceCategory().equals(
                ReferenceCategory.PACKAGE_MANAGER ) )
        {
            assertEquals( "extref comment1", externalRefs[1].getComment().get() );
            assertEquals( "example-locator-CPE22Type", externalRefs[1].getReferenceLocator() );
            assertEquals( "cpe22Type", ListedReferenceTypes.getListedReferenceTypes().getListedReferenceName(
                    new URI(externalRefs[1].getReferenceType().getIndividualURI() ) ) );
            assertEquals( "extref comment2", externalRefs[0].getComment().get() );
            assertEquals( "org.apache.tomcat:tomcat:9.0.0.M4", externalRefs[0].getReferenceLocator() );
            assertEquals( "maven-central", ListedReferenceTypes.getListedReferenceTypes().getListedReferenceName(
                    new URI( externalRefs[0].getReferenceType().getIndividualURI() ) ) );
        }
        else
        {
            fail( "Unexpected reference category" );
        }
        // Test all files are included
        List<String> filePaths = new ArrayList<>();
        File sourceDir = new File( getBasedir(), UNIT_TEST_RESOURCE_DIR + "/src/main" );
        addFilePaths( getBasedir() + UNIT_TEST_RESOURCE_DIR, sourceDir, filePaths );
        File testDir = new File( getBasedir(), UNIT_TEST_RESOURCE_DIR + "/src/test" );
        addFilePaths( getBasedir() + UNIT_TEST_RESOURCE_DIR, testDir, filePaths );
        @SuppressWarnings( "unused" )
        File resourceDir = new File( getBasedir(), UNIT_TEST_RESOURCE_DIR + "/src/resources" );
        //TODO: Add resource to project stub and uncomment the line below
        //        addFilePaths( getBasedir() + UNIT_TEST_RESOURCE_DIR, resourceDir, filePaths );
        Collection<SpdxFile> pkgFiles = pkg.getFiles();
        assertEquals( filePaths.size(), pkgFiles.size() );
        String fileWithSnippet = null;
        for ( SpdxFile sFile : pkgFiles )
        {
            if ( sFile.getName().get().equals( "./src/main/java/CommonCode.java" ) )
            {
                fileWithSnippet = sFile.getId();
                assertEquals( "Comment for CommonCode", sFile.getComment().get() );
                assertEquals( "Common Code Copyright", sFile.getCopyrightText() );
                assertEquals( "License Comment for Common Code", sFile.getLicenseComments().get() );
                assertEquals( "Notice for Commmon Code", sFile.getNoticeText().get() );
                assertEquals( "EPL-1.0", sFile.getLicenseConcluded().toString() );
                assertEquals( "ISC", sFile.getLicenseInfoFromFiles().toArray( new AnyLicenseInfo[sFile.getLicenseInfoFromFiles().size()]  )[0].toString() );
            }
            else
            {
                assertEquals( "Default file comment", sFile.getComment().get() );
                assertEquals( "Copyright (c) 2012, 2013, 2014 Source Auditor Inc.", sFile.getCopyrightText() );
                assertEquals( "Default file license comment", sFile.getLicenseComments().get() );
                assertEquals( "Default file notice", sFile.getNoticeText().get() );
                assertEquals( "Apache-2.0", sFile.getLicenseConcluded().toString() );
                assertEquals( "Apache-1.1", sFile.getLicenseInfoFromFiles().toArray( new AnyLicenseInfo[sFile.getLicenseInfoFromFiles().size()] )[0].toString() );
            }
            filePaths.remove( sFile.getName().get() );
        }
        assertEquals( 0, filePaths.size() );
        List<SpdxSnippet> snippets = new ArrayList<>();
        SpdxModelFactory.getSpdxObjects( modelStore, copyManager, SpdxConstantsCompatV2.CLASS_SPDX_SNIPPET, 
                                         null, result.getIdPrefix() ).forEach( (snippet) -> {
            snippets.add( (SpdxSnippet)snippet );
        });
        assertEquals( 2, snippets.size() );
        Collections.sort( snippets );
        assertEquals( "Snippet Comment", snippets.get( 0 ).getComment().get() );
        assertEquals( "Snippet Copyright Text", snippets.get( 0 ).getCopyrightText() );
        assertEquals( "Snippet License Comment", snippets.get( 0 ).getLicenseComments().get() );
        assertEquals( "SnippetName", snippets.get( 0 ).getName().get() );
        assertEquals( "1231:3442",
                TestSpdxV2FileCollector.startEndPointerToString( snippets.get( 0 ).getByteRange() ) );
        assertEquals( "BSD-2-Clause", snippets.get( 0 ).getLicenseConcluded().toString() );
        assertEquals( "BSD-2-Clause-FreeBSD", snippets.get( 0 ).getLicenseInfoFromFiles().toArray( new AnyLicenseInfo[snippets.get( 0 ).getLicenseInfoFromFiles().size()] )[0].toString() );
        assertEquals( "44:55", TestSpdxV2FileCollector.startEndPointerToString( snippets.get( 0 ).getLineRange().get() ) );
        assertEquals( fileWithSnippet, snippets.get( 0 ).getSnippetFromFile().getId() );

        assertEquals( "Snippet Comment2", snippets.get( 1 ).getComment().get() );
        assertEquals( "Snippet2 Copyright Text", snippets.get( 1 ).getCopyrightText() );
        assertEquals( "Snippet2 License Comment", snippets.get( 1 ).getLicenseComments().get() );
        assertEquals( "SnippetName2", snippets.get( 1 ).getName().get() );
        assertEquals( "31231:33442",
                TestSpdxV2FileCollector.startEndPointerToString( snippets.get( 1 ).getByteRange() ) );
        assertEquals( "MITNFA", snippets.get( 1 ).getLicenseConcluded().toString() );
        assertEquals( "LicenseRef-testLicense", snippets.get( 1 ).getLicenseInfoFromFiles().toArray( new AnyLicenseInfo[snippets.get( 1 ).getLicenseInfoFromFiles().size()] )[0].toString() );
        assertEquals( "444:554",
                TestSpdxV2FileCollector.startEndPointerToString( snippets.get( 1 ).getLineRange().get() ) );
        assertEquals( fileWithSnippet, snippets.get( 1 ).getSnippetFromFile().getId() );
        //TODO Test dependencies
    }

    @Test
    public void testExecuteUseGeneratePurls() throws Exception
    {
        File testPom = new File( getBasedir(), UNIT_TEST_RESOURCE_DIR + "/json-pom-generate-purl.xml" );
        //        CreateSpdxMojo mojo = (CreateSpdxMojo) configureMojo( myMojo, "spdx-maven-plugin", testPom );
        // if the below does not work due to a lookup error, run mvn test goal
        CreateSpdxMojo mojo = (CreateSpdxMojo) lookupMojo( "createSPDX", testPom );
        assertNotNull( mojo );
        mojo.execute();
        // Test SPDX filename parameter
        File spdxFile = new File( getBasedir(), SPDX_JSON_FILE_NAME );
        assertTrue( spdxFile.exists() );
        // Test output artifact file is created
        File artifactFile = getTestFile(
            "target/test-classes/unit/spdx-maven-plugin-test/spdx maven plugin test.spdx.json" );
        assertTrue( artifactFile.exists() );
        ISerializableModelStore modelStore = new MultiFormatStore( new InMemSpdxStore(), Format.JSON );
        ModelCopyManager copyManager = new ModelCopyManager();
        SpdxDocument result;
        try ( InputStream is = new FileInputStream( artifactFile.getAbsolutePath() ) )
        {
            result = (SpdxDocument)modelStore.deSerialize( is, false );
        }
        List<String> warnings = result.verify();
        assertEquals( 0, warnings.size() );
        // Test configuration parameters found in the test resources pom.xml file
        // Document namespace
        assertEquals( "http://spdx.org/documents/spdx%20toolsv2.0%20rc1", result.getDocumentUri() );
        // purls
        List<SpdxPackage> packages = new ArrayList<>();
        SpdxModelFactory.getSpdxObjects( modelStore, copyManager, SpdxConstantsCompatV2.CLASS_SPDX_PACKAGE, 
                                         null, result.getIdPrefix() ).forEach( (pkg) -> {
                                             packages.add( (SpdxPackage)pkg );
        });
        for ( SpdxPackage pkg : packages ) {
            Collection<ExternalRef> externalRefs = pkg.getExternalRefs();
            assertNotNull( externalRefs );
            assertFalse( externalRefs.isEmpty() );
            ExternalRef externalRef = pkg.getExternalRefs().stream().findFirst().get();
            assertEquals( externalRef.getReferenceCategory(), ReferenceCategory.PACKAGE_MANAGER );
            assertEquals( externalRef.getReferenceType().getIndividualURI(), "http://spdx.org/rdf/references/purl");
            assertEquals( externalRef.getReferenceLocator(),
                          "pkg:maven/" + pkg.getName().get().replace( ":", "/" ).replaceAll( " ", "+" )
                              + "@" + pkg.getVersionInfo().get() );
        }
    }
}
