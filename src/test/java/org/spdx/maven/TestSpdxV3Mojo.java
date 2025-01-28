package org.spdx.maven;


import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.spdx.core.DefaultModelStore;
import org.spdx.library.LicenseInfoFactory;
import org.spdx.library.ModelCopyManager;
import org.spdx.library.SpdxModelFactory;
import org.spdx.library.model.v3_0_1.SpdxConstantsV3;
import org.spdx.library.model.v3_0_1.core.Agent;
import org.spdx.library.model.v3_0_1.core.AnnotationType;
import org.spdx.library.model.v3_0_1.core.Element;
import org.spdx.library.model.v3_0_1.core.ExternalIdentifier;
import org.spdx.library.model.v3_0_1.core.ExternalIdentifierType;
import org.spdx.library.model.v3_0_1.core.ExternalRef;
import org.spdx.library.model.v3_0_1.core.ExternalRefType;
import org.spdx.library.model.v3_0_1.core.Organization;
import org.spdx.library.model.v3_0_1.core.Person;
import org.spdx.library.model.v3_0_1.core.Relationship;
import org.spdx.library.model.v3_0_1.core.RelationshipType;
import org.spdx.library.model.v3_0_1.core.SpdxDocument;
import org.spdx.library.model.v3_0_1.expandedlicensing.CustomLicense;
import org.spdx.library.model.v3_0_1.simplelicensing.AnyLicenseInfo;
import org.spdx.library.model.v3_0_1.software.Sbom;
import org.spdx.library.model.v3_0_1.software.Snippet;
import org.spdx.library.model.v3_0_1.software.SpdxFile;
import org.spdx.library.model.v3_0_1.software.SpdxPackage;
import org.spdx.maven.utils.TestSpdxV3FileCollector;
import org.spdx.storage.ISerializableModelStore;
import org.spdx.storage.simple.InMemSpdxStore;
import org.spdx.v3jsonldstore.JsonLDStore;

public class TestSpdxV3Mojo extends AbstractMojoTestCase
{

    private static final String UNIT_TEST_RESOURCE_DIR = "target/test-classes/unit/spdx-maven-plugin-test";
    private static final String SPDX_FILE_NAME = UNIT_TEST_RESOURCE_DIR + "/test.json-ld.json";
    
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

    @SuppressWarnings( "unchecked" )
    @Test
    public void testExecute() throws Exception
    {
        File testPom = new File( getBasedir(), UNIT_TEST_RESOURCE_DIR + "/pom-v3.xml" );
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
                "target/test-classes/unit/spdx-maven-plugin-test/spdx maven plugin test.spdx.json-ld.json" );
        assertTrue( artifactFile.exists() );
        ISerializableModelStore modelStore = new JsonLDStore( new InMemSpdxStore() );
        ModelCopyManager copyManager = new ModelCopyManager();
        SpdxDocument result;
        try ( InputStream is = new FileInputStream( artifactFile.getAbsolutePath() ) )
        {
            result = (SpdxDocument)modelStore.deSerialize( is, false );
        }
        List<String> warnings = result.verify();
        assertEquals( 0, warnings.size() );
        // Test configuration parameters found in the test resources pom.xml file
        // Non-standard licenses
        List<CustomLicense> customLicenses = (List<CustomLicense>)SpdxModelFactory.getSpdxObjects( modelStore, copyManager, SpdxConstantsV3.EXPANDED_LICENSING_CUSTOM_LICENSE,
                                                                                                   null, result.getIdPrefix() ).collect( Collectors.toList() );
        assertEquals( 2, customLicenses.size() );
        CustomLicense testLicense1 = null;
        CustomLicense testLicense2 = null;
        for ( CustomLicense li : customLicenses )
        {
            if ( li.getObjectUri().endsWith( "LicenseRef-testLicense" ) )
            {
                testLicense1 = li;
            }
            else if ( li.getObjectUri().endsWith( "LicenseRef-testLicense2" ) )
            {
                testLicense2 = li;
            }
        }
        assertTrue( testLicense1 != null );
        assertEquals( "Test License", testLicense1.getName().get() );
        assertEquals( "Test license text", testLicense1.getLicenseText() );
        assertEquals( 1, testLicense1.getSeeAlsos().size() );
        assertEquals( "http://www.test.url/testLicense.html", testLicense1.getSeeAlsos().toArray( new String[testLicense1.getSeeAlsos().size()]  )[0] );
        assertEquals( "Test license comment", testLicense1.getComment().get() );

        assertTrue( testLicense2 != null );
        assertEquals( "Second Test License", testLicense2.getName().get() );
        assertEquals( "Second est license text", testLicense2.getLicenseText() );
        assertEquals( 2, testLicense2.getSeeAlsos().size() );
        boolean foundSeeAlso1 = false;
        boolean foundSeeAlso2 = false;
        for ( String seeAlso : testLicense2.getSeeAlsos() )
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
        assertEquals( "Second Test license comment", testLicense2.getComment().get() );
        // documentComment
        assertEquals( "Document Comment", result.getComment().get() );
        // documentAnnotations
        List<org.spdx.library.model.v3_0_1.core.Annotation> allAnnotations = (List<org.spdx.library.model.v3_0_1.core.Annotation>)SpdxModelFactory.getSpdxObjects( modelStore, copyManager, SpdxConstantsV3.CORE_ANNOTATION,
                                                                                             null, result.getIdPrefix() ).collect( Collectors.toList() );
        int numDocAnnotations = 0;
        org.spdx.library.model.v3_0_1.core.Annotation annotation1 = null;
        org.spdx.library.model.v3_0_1.core.Annotation annotation2 = null;
        for ( org.spdx.library.model.v3_0_1.core.Annotation annotation : allAnnotations )
        {
            if ( annotation.getSubject().equals( result ) )
            {
                numDocAnnotations++;
                if ( annotation.getStatement().get().equals( "Annotation1" ) )
                {
                    annotation1 = annotation;
                }
                else if ( annotation.getStatement().get().equals( "Annotation2" ) )
                {
                    annotation2 = annotation;
                }
            }
        }
        assertEquals( 2, numDocAnnotations );
        
        assertTrue( annotation1 != null );
        assertEquals( "2010-01-29T18:30:22Z", annotation1.getCreationInfo().getCreated() );
        Collection<Agent> annotators = annotation1.getCreationInfo().getCreatedBys();
        assertEquals( 1, annotators.size() );
        assertEquals( "Test Person", annotators.iterator().next().getName().get() );
        assertEquals( AnnotationType.REVIEW, annotation1.getAnnotationType() );

        assertTrue( annotation2 != null );
        annotators = annotation2.getCreationInfo().getCreatedBys();
        assertEquals( 1, annotators.size() );
        assertEquals( "2012-11-29T18:30:22Z", annotation2.getCreationInfo().getCreated() );
        assertEquals( "Test Organization", annotators.iterator().next().getName().get() );
        assertEquals( AnnotationType.OTHER, annotation2.getAnnotationType() );
        //creatorComment
        assertEquals( "Creator comment", result.getCreationInfo().getComment().get() );
        // creators
        assertEquals( 2, result.getCreationInfo().getCreatedBys().size() );
        boolean foundCreator1 = false;
        boolean foundCreator2 = false;
        for ( Agent creator : result.getCreationInfo().getCreatedBys() )
        {
            if ( creator instanceof Person && creator.getName().get().equals( "Creator1" ) )
            {
                foundCreator1 = true;
            }
            else if ( creator instanceof Person && creator.getName().get().equals( "Creator2" ) )
            {
                foundCreator2 = true;
            }
        }
        assertTrue( foundCreator1 );
        assertTrue( foundCreator2 );
        assertEquals( 1, result.getCreationInfo().getCreatedUsings().size() );
        assertEquals( "spdx-maven-plugin", result.getCreationInfo().getCreatedUsings().iterator().next().getName().get() );
        // package parameters
        assertEquals( 1, result.getRootElements().size() );
        Element sbom = result.getRootElements().toArray( new Element[result.getRootElements().size()] )[0];
        assertTrue( sbom instanceof Sbom );
        assertEquals(1,  ((Sbom)sbom).getRootElements().size() );
        Element described = ((Sbom)sbom).getRootElements().iterator().next();
        assertTrue( described instanceof SpdxPackage );
        SpdxPackage pkg = (SpdxPackage) described;
        // packageAnnotations
        int numPkgAnnotations = 0;
        org.spdx.library.model.v3_0_1.core.Annotation pkgAnnotation = null;
        
        for ( org.spdx.library.model.v3_0_1.core.Annotation annotation : allAnnotations )
        {
            if ( annotation.getSubject().equals( pkg ) )
            {
                numPkgAnnotations++;
                pkgAnnotation = annotation;
            }
        }
        assertEquals( 1, numPkgAnnotations );
        assertEquals( "PackageAnnotation", pkgAnnotation.getStatement().get() );
        assertEquals( "2015-01-29T18:30:22Z", pkgAnnotation.getCreationInfo().getCreated() );
        annotators = pkgAnnotation.getCreationInfo().getCreatedBys();
        assertEquals( 1, annotators.size() );
        Agent annotator = annotators.iterator().next();
        assertTrue( annotator instanceof Person );
        assertEquals( "Test Package Person", annotator.getName().get() );
        assertEquals( AnnotationType.REVIEW, pkgAnnotation.getAnnotationType() );
        List<Relationship> allRelationships = (List<Relationship>)SpdxModelFactory.getSpdxObjects( modelStore, copyManager, SpdxConstantsV3.CORE_RELATIONSHIP,
                                                                                                   null, result.getIdPrefix() ).collect( Collectors.toList() );
        AnyLicenseInfo pkgLicenseDeclared = null;
        AnyLicenseInfo pkgLicenseConcluded = null;
        
        for ( Relationship relationship : allRelationships )
        {
            if ( relationship.getFrom().equals( pkg ) )
            {
                if ( relationship.getRelationshipType().equals( RelationshipType.HAS_DECLARED_LICENSE ))
                {
                    assertTrue( pkgLicenseDeclared == null );
                    assertEquals( 1, relationship.getTos().size() );
                    pkgLicenseDeclared = (AnyLicenseInfo)relationship.getTos().iterator().next();
                }
                if ( relationship.getRelationshipType().equals( RelationshipType.HAS_CONCLUDED_LICENSE ))
                {
                    assertTrue( pkgLicenseConcluded == null );
                    assertEquals( 1, relationship.getTos().size() );
                    pkgLicenseConcluded = (AnyLicenseInfo)relationship.getTos().iterator().next();
                }
            }
        }
        //licenseDeclared
        AnyLicenseInfo licenseDeclared = LicenseInfoFactory.getListedLicenseById( "BSD-2-Clause" );
        assertEquals( licenseDeclared, pkgLicenseDeclared );
        //licenseConcluded
        AnyLicenseInfo licenseConcluded = LicenseInfoFactory.getListedLicenseById( "BSD-3-Clause" );
        assertEquals( licenseConcluded, pkgLicenseConcluded );
        //licenseComments
        assertTrue( pkg.getComment().get().endsWith( "License comments" ) );
        //originator
        assertEquals( 1, pkg.getOriginatedBys().size() );
        Agent originator = pkg.getOriginatedBys().iterator().next();
        assertTrue( originator instanceof Organization );
        assertEquals( "Originating org.", originator.getName().get() );
        // sourceInfo
        assertEquals( "Source info", pkg.getSourceInfo().get() );
        //copyrightText
        assertEquals( "Copyright Text for Package", pkg.getCopyrightText().get() );
        //external Refs
        ExternalRef[] externalRefs = pkg.getExternalRefs().toArray( new ExternalRef[pkg.getExternalRefs().size()] );
        assertEquals( 1, externalRefs.length );
        assertEquals( ExternalRefType.MAVEN_CENTRAL, externalRefs[0].getExternalRefType().get() );
        assertEquals( "extref comment2", externalRefs[0].getComment().get() );
        assertEquals( 1, externalRefs[0].getLocators().size() );
        assertEquals( "org.apache.tomcat:tomcat:9.0.0.M4", externalRefs[0].getLocators().iterator().next() );
        ExternalIdentifier[] externalIds = pkg.getExternalIdentifiers().toArray( new ExternalIdentifier[pkg.getExternalIdentifiers().size()] );
        assertEquals( 1, externalIds.length );
        assertEquals( ExternalIdentifierType.CPE22, externalIds[0].getExternalIdentifierType() );
        assertEquals( "extref comment1", externalIds[0].getComment().get() );
        assertEquals( "example-locator-CPE22Type", externalIds[0].getIdentifier() );           
            
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
        List<SpdxFile> pkgFiles = new ArrayList<>();
        for ( Relationship relationship : allRelationships )
        {
            if ( relationship.getFrom().equals( pkg ) )
            {
                if ( relationship.getRelationshipType().equals( RelationshipType.CONTAINS ))
                {
                   for ( Element to : relationship.getTos() )
                   {
                       if ( to instanceof SpdxFile )
                       {
                           pkgFiles.add( (SpdxFile)to );
                       }
                   }
                }
            }
        }
        assertEquals( filePaths.size(), pkgFiles.size() );
        String fileWithSnippet = null;
        for ( SpdxFile sFile : pkgFiles )
        {
            AnyLicenseInfo fileLicenseConcluded = null;
            AnyLicenseInfo fileLicenseDeclared = null;
            for ( Relationship relationship : allRelationships )
            {
                if ( relationship.getFrom().equals( sFile ) )
                {
                    if ( relationship.getRelationshipType().equals( RelationshipType.HAS_DECLARED_LICENSE ))
                    {
                        assertTrue( fileLicenseDeclared == null );
                        assertEquals( 1, relationship.getTos().size() );
                        fileLicenseDeclared = (AnyLicenseInfo)relationship.getTos().iterator().next();
                    }
                    if ( relationship.getRelationshipType().equals( RelationshipType.HAS_CONCLUDED_LICENSE ))
                    {
                        assertTrue( fileLicenseConcluded == null );
                        assertEquals( 1, relationship.getTos().size() );
                        fileLicenseConcluded = (AnyLicenseInfo)relationship.getTos().iterator().next();
                    }
                }
            }
            if ( sFile.getName().get().equals( "./src/main/java/CommonCode.java" ) )
            {
                fileWithSnippet = sFile.getObjectUri();
                assertTrue( sFile.getComment().get().startsWith( "Comment for CommonCode" ) );
                assertEquals( "Common Code Copyright", sFile.getCopyrightText().get() );
                assertTrue( sFile.getComment().get().endsWith( "License Comment for Common Code" ) );
                assertEquals( 1, sFile.getAttributionTexts().size() );
                assertEquals( "Notice for Commmon Code", sFile.getAttributionTexts().iterator().next() );
                assertEquals( "EPL-1.0", fileLicenseConcluded.toString() );
                assertEquals( 1, sFile.getOriginatedBys().size() );
                Agent fileOriginator = sFile.getOriginatedBys().iterator().next();
                assertTrue( fileOriginator instanceof Person );
                assertEquals( "Contributor to CommonCode", fileOriginator.getName().get() );
                assertEquals( "ISC", fileLicenseDeclared.toString() );
            }
            else
            {
                assertTrue( sFile.getComment().get().startsWith( "Default file comment" ) );
                assertEquals( "Copyright (c) 2012, 2013, 2014 Source Auditor Inc.", sFile.getCopyrightText().get() );
                assertTrue( sFile.getComment().get().endsWith( "Default file license comment" ) );
                assertEquals( 1, sFile.getAttributionTexts().size() );
                assertEquals( "Default file notice", sFile.getAttributionTexts().iterator().next() );
                assertEquals( "Apache-2.0", fileLicenseConcluded.toString() );
                assertEquals( 2, sFile.getOriginatedBys().size() );
                assertEquals( "Apache-1.1", fileLicenseDeclared.toString() );
            }
            filePaths.remove( sFile.getName().get() );
        }
        assertEquals( 0, filePaths.size() );
        List<Snippet> snippets = (List<Snippet>)SpdxModelFactory.getSpdxObjects( modelStore, copyManager, SpdxConstantsV3.SOFTWARE_SNIPPET,
                                                                  null, result.getIdPrefix() ).collect( Collectors.toList() );
        assertEquals( 2, snippets.size() );
        Snippet snippet1;
        Snippet snippet2;
        if ( snippets.get( 0 ).getComment().get().startsWith( "Snippet Comment2" ) )
        {
            snippet1 = snippets.get( 1 );
            snippet2 = snippets.get( 0 );
        }
        else
        {
            snippet1 = snippets.get( 0 );
            snippet2 = snippets.get( 1 );
        }
        AnyLicenseInfo snippet1LicenseConcluded = null;
        AnyLicenseInfo snippet1LicenseDeclared = null;
        AnyLicenseInfo snippet2LicenseConcluded = null;
        AnyLicenseInfo snippet2LicenseDeclared = null;
        for ( Relationship relationship : allRelationships )
        {
            if ( relationship.getFrom().equals( snippet1 ) )
            {
                if ( relationship.getRelationshipType().equals( RelationshipType.HAS_DECLARED_LICENSE ))
                {
                    assertTrue( snippet1LicenseDeclared == null );
                    assertEquals( 1, relationship.getTos().size() );
                    snippet1LicenseDeclared = (AnyLicenseInfo)relationship.getTos().iterator().next();
                }
                if ( relationship.getRelationshipType().equals( RelationshipType.HAS_CONCLUDED_LICENSE ))
                {
                    assertTrue( snippet1LicenseConcluded == null );
                    assertEquals( 1, relationship.getTos().size() );
                    snippet1LicenseConcluded = (AnyLicenseInfo)relationship.getTos().iterator().next();
                }
            } else if ( relationship.getFrom().equals( snippet2 ) )
            {
                if ( relationship.getRelationshipType().equals( RelationshipType.HAS_DECLARED_LICENSE ))
                {
                    assertTrue( snippet2LicenseDeclared == null );
                    assertEquals( 1, relationship.getTos().size() );
                    snippet2LicenseDeclared = (AnyLicenseInfo)relationship.getTos().iterator().next();
                }
                if ( relationship.getRelationshipType().equals( RelationshipType.HAS_CONCLUDED_LICENSE ))
                {
                    assertTrue( snippet2LicenseConcluded == null );
                    assertEquals( 1, relationship.getTos().size() );
                    snippet2LicenseConcluded = (AnyLicenseInfo)relationship.getTos().iterator().next();
                }
            }
        }
        assertTrue( snippet1.getComment().get().startsWith( "Snippet Comment" ) );
        assertEquals( "Snippet Copyright Text", snippet1.getCopyrightText().get() );
        assertTrue( snippet1.getComment().get().endsWith( "Snippet License Comment" ) );
        assertEquals( "SnippetName", snippet1.getName().get() );
        assertEquals( "1231:3442", TestSpdxV3FileCollector.positiveIntegerRangeToString( snippet1.getByteRange().get() ) );
        assertEquals( "BSD-2-Clause", snippet1LicenseConcluded.toString() );
        assertEquals( "BSD-2-Clause-FreeBSD", snippet1LicenseDeclared.toString() );
        assertEquals( "44:55", TestSpdxV3FileCollector.positiveIntegerRangeToString( snippet1.getLineRange().get() ) );
        assertEquals( fileWithSnippet, snippet1.getSnippetFromFile().getObjectUri() );

        assertTrue( snippet2.getComment().get().startsWith( "Snippet Comment2" ) );
        assertEquals( "Snippet2 Copyright Text", snippet2.getCopyrightText().get() );
        assertTrue( snippet2.getComment().get().endsWith( "Snippet2 License Comment" ) );
        assertEquals( "SnippetName2", snippet2.getName().get() );
        assertEquals( "31231:33442",
                      TestSpdxV3FileCollector.positiveIntegerRangeToString( snippet2.getByteRange().get() ) );
        assertEquals( "MITNFA", snippet2LicenseConcluded.toString() );
        assertEquals( "LicenseRef-testLicense", snippet2LicenseDeclared.toString() );
        assertEquals( "444:554",
                      TestSpdxV3FileCollector.positiveIntegerRangeToString( snippet2.getLineRange().get() ) );
        assertEquals( fileWithSnippet, snippet2.getSnippetFromFile().getObjectUri() );
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
}
