/*
 * Copyright 2014 Source Auditor Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

import static org.junit.Assert.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

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
import org.spdx.library.model.v3_0_1.SpdxConstantsV3;
import org.spdx.library.model.v3_0_1.core.SpdxDocument;
import org.spdx.library.model.v3_0_1.expandedlicensing.ConjunctiveLicenseSet;
import org.spdx.library.model.v3_0_1.expandedlicensing.CustomLicense;
import org.spdx.library.model.v3_0_1.expandedlicensing.ListedLicense;
import org.spdx.library.model.v3_0_1.simplelicensing.AnyLicenseInfo;
import org.spdx.maven.NonStandardLicense;
import org.spdx.storage.simple.InMemSpdxStore;
import org.apache.maven.model.License;

/**
 * Unit tests for SpdxV3LicenseManager class
 *
 * @author Gary O'Neall
 */
public class TestSpdxV3LicenseManager
{
    private static final String TEST_SPDX_DOCUMENT_URL = "http://www.spdx.org/documents/test";
    static final String APACHE_CROSS_REF_URL2 = "http://www.apache.org/licenses/LICENSE-2.0";
    static final String APACHE_CROSS_REF_URL3 = "http://opensource.org/licenses/Apache-2.0";
    static final String APACHE_LICENSE_ID = "Apache-2.0";
    static final String APACHE_LICENSE_NAME = "Apache License 2.0";
    static final String APSL_CROSS_REF_URL = "http://www.opensource.apple.com/source/IOSerialFamily/IOSerialFamily-7/APPLE_LICENSE";
    static final String APSL_LICENSE_ID = "APSL-1.1";

    SpdxDocument spdxDoc = null;


    /**
     * @throws java.lang.Exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception
    {
        SpdxModelFactory.init();
    }

    /**
     * @throws java.lang.Exception
     */
    @AfterClass
    public static void tearDownAfterClass() throws Exception
    {
    }

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception
    {
        DefaultModelStore.initialize(new InMemSpdxStore(), "http://default/namespace", new ModelCopyManager());
        spdxDoc = new SpdxDocument( new InMemSpdxStore(), TEST_SPDX_DOCUMENT_URL + "#DOCUMENT", new ModelCopyManager(),
                                    true, TEST_SPDX_DOCUMENT_URL );
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception
    {
        spdxDoc = null;
    }

    /**
     * Test method for {@link org.spdx.maven.utils.SpdxV3LicenseManager#LicenseManager(org.spdx.rdfparser.SpdxDocument,boolean)}.
     *
     * @throws LicenseMapperException
     */
    @Test
    public void testLicenseManager() throws LicenseMapperException
    {
        @SuppressWarnings( "unused" )
        SpdxV3LicenseManager licenseManager = new SpdxV3LicenseManager( spdxDoc );
    }

    /**
     * Test method for {@link org.spdx.maven.utils.SpdxV3LicenseManager#addExtractedLicense(org.spdx.maven.NonStandardLicense)}.
     *
     * @throws MalformedURLException
     * @throws LicenseManagerException
     * @throws InvalidSPDXAnalysisException
     * @throws LicenseMapperException
     */
    @SuppressWarnings( "unchecked" )
    @Test
    public void testAddNonStandardLicense() throws MalformedURLException, LicenseManagerException, InvalidSPDXAnalysisException, LicenseMapperException
    {
        SpdxV3LicenseManager licenseManager = new SpdxV3LicenseManager( spdxDoc );
        NonStandardLicense lic = new NonStandardLicense();
        final String COMMENT = "comment";
        final String[] CROSS_REF_STR = new String[] {"http://www.licenseRef1", "http://www.licenseref2"};
        final URL[] CROSS_REF = new URL[] {new URL( CROSS_REF_STR[0] ), new URL( CROSS_REF_STR[1] )};
        final String EXTRACTED_TEXT = "extracted text";
        final String LICENSE_ID = "LicenseRef-licenseId";
        final String LICENSE_NAME = "licenseName";
        lic.setComment( COMMENT );
        lic.setCrossReference( CROSS_REF );
        lic.setExtractedText( EXTRACTED_TEXT );
        lic.setLicenseId( LICENSE_ID );
        lic.setName( LICENSE_NAME );

        licenseManager.addExtractedLicense( lic );
        List<CustomLicense> result = (List<CustomLicense>) SpdxModelFactory.getSpdxObjects( spdxDoc.getModelStore(), 
                                                                      spdxDoc.getCopyManager(), 
                                                                      SpdxConstantsV3.EXPANDED_LICENSING_CUSTOM_LICENSE, 
                                                                      null, TEST_SPDX_DOCUMENT_URL )
                        .collect( Collectors.toList() );
        assertEquals( 1, result.size() );
        assertEquals( COMMENT, result.get( 0 ).getComment().get() );
        assertArrayEquals( CROSS_REF_STR, TestUtils.toSortedArray( result.get( 0 ).getSeeAlsos() ) );
        assertEquals( EXTRACTED_TEXT, result.get( 0 ).getLicenseText() );
        assertEquals( spdxDoc.getIdPrefix() + LICENSE_ID, result.get( 0 ).getObjectUri() );
        assertEquals( LICENSE_NAME, result.get( 0 ).getName().get() );

        NonStandardLicense lic2 = new NonStandardLicense();
        final String LICENSE_ID2 = "LicenseRef-licenseId2";
        final String EXTRACTED_TEXT2 = "Second extracted text";
        lic2.setLicenseId( LICENSE_ID2 );
        lic2.setExtractedText( EXTRACTED_TEXT2 );

        licenseManager.addExtractedLicense( lic2 );

        result = (List<CustomLicense>) SpdxModelFactory.getSpdxObjects( spdxDoc.getModelStore(), 
                                                                        spdxDoc.getCopyManager(), 
                                                                        SpdxConstantsV3.EXPANDED_LICENSING_CUSTOM_LICENSE, 
                                                                        null, TEST_SPDX_DOCUMENT_URL )
                          .collect( Collectors.toList() );assertEquals( 2, result.size() );
        CustomLicense licResult = result.get( 1 );
        if ( !licResult.getObjectUri().endsWith( LICENSE_ID2 ) )
        {
            licResult = result.get( 0 );
        }
        assertEquals( EXTRACTED_TEXT2, licResult.getLicenseText() );
        assertEquals( spdxDoc.getIdPrefix() + LICENSE_ID2, licResult.getObjectUri() );

    }

    /**
     * Test method for {@link org.spdx.maven.utils.SpdxV3LicenseManager#mavenLicenseListToSpdxLicense(java.util.List)}.
     *
     * @throws LicenseManagerException
     * @throws LicenseMapperException
     * @throws InvalidSPDXAnalysisException 
     */
    @Test
    public void testMavenLicenseListToSpdxLicense() throws LicenseManagerException, LicenseMapperException, InvalidSPDXAnalysisException
    {
        final String LICENSE1_NAME = "Apachelicense1";
        final String LICENSE2_NAME = "APSLlicense2";

        License apache = new License();
        apache.setName( LICENSE1_NAME );
        apache.setUrl( APACHE_CROSS_REF_URL2 );
        License apsl = new License();
        apsl.setName( LICENSE2_NAME );
        apsl.setUrl( APSL_CROSS_REF_URL );

        ArrayList<License> licenseList = new ArrayList<>();
        licenseList.add( apache );
        licenseList.add( apsl );

        SpdxV3LicenseManager licenseManager = new SpdxV3LicenseManager( spdxDoc );

        AnyLicenseInfo result = licenseManager.mavenLicenseListToSpdxLicense( licenseList );
        assertTrue( result instanceof ConjunctiveLicenseSet );
        Collection<AnyLicenseInfo> members = ( (ConjunctiveLicenseSet) result ).getMembers();
        AnyLicenseInfo[] resultLicenses = members.toArray( new AnyLicenseInfo[members.size()] );
        assertEquals( 2, resultLicenses.length );
        assertTrue( resultLicenses[0] instanceof ListedLicense );
        if ( !( (ListedLicense) resultLicenses[0] ).getObjectUri().endsWith(
                APACHE_LICENSE_ID ) && !( (ListedLicense) resultLicenses[0] ).getObjectUri().endsWith(
                APSL_LICENSE_ID ) )
        {
            fail( "Unrecognized first license " + ( (ListedLicense) resultLicenses[0] ).getObjectUri() );
        }
        assertTrue( resultLicenses[1] instanceof ListedLicense );
        if ( !( (ListedLicense) resultLicenses[1] ).getObjectUri().endsWith(
                APACHE_LICENSE_ID ) && !( (ListedLicense) resultLicenses[1] ).getObjectUri().endsWith(
                APSL_LICENSE_ID ) )
        {
            fail( "Unrecognized second license " + ( (ListedLicense) resultLicenses[1] ).getObjectUri() );
        }
    }

    /**
     * Test method for {@link org.spdx.maven.utils.SpdxV3LicenseManager#mavenLicenseToSpdxLicense(org.apache.maven.model.License)}.
     *
     * @throws LicenseManagerException
     * @throws MalformedURLException
     * @throws LicenseMapperException
     * @throws InvalidSPDXAnalysisException 
     */
    @Test
    public void testMavenLicenseToSpdxLicense() throws LicenseManagerException, MalformedURLException, LicenseMapperException, InvalidSPDXAnalysisException
    {
        // unmapped license - can not test without valid log
        // standard license
        final String LICENSE1_NAME = "Apachelicense1";
        License apache = new License();
        apache.setName( LICENSE1_NAME );
        apache.setUrl( APACHE_CROSS_REF_URL2 );
        SpdxV3LicenseManager licenseManager = new SpdxV3LicenseManager( spdxDoc );

        AnyLicenseInfo result = licenseManager.mavenLicenseToSpdxLicense( apache );
        assertTrue( result instanceof ListedLicense );
        assertEquals( SpdxConstantsV3.SPDX_LISTED_LICENSE_NAMESPACE + APACHE_LICENSE_ID, ( (ListedLicense) result ).getObjectUri() );

        // nonstandard license
        NonStandardLicense lic = new NonStandardLicense();
        final String COMMENT = "comment";
        final String[] CROSS_REF_STR = new String[] {"http://www.licenseRef1"};
        final URL[] CROSS_REF = new URL[] {new URL( CROSS_REF_STR[0] )};
        final String EXTRACTED_TEXT = "extracted text";
        final String LICENSE_ID = "LicenseRef-licenseId";
        final String LICENSE_NAME = "licenseName";
        lic.setComment( COMMENT );
        lic.setCrossReference( CROSS_REF );
        lic.setExtractedText( EXTRACTED_TEXT );
        lic.setLicenseId( LICENSE_ID );
        lic.setName( LICENSE_NAME );
        licenseManager.addExtractedLicense( lic );
        License nonStd = new License();
        nonStd.setComments( COMMENT );
        nonStd.setName( LICENSE_NAME );
        nonStd.setUrl( CROSS_REF_STR[0] );
        result = licenseManager.mavenLicenseToSpdxLicense( nonStd );

        assertTrue( result instanceof CustomLicense );
        CustomLicense nonStdResult = (CustomLicense) result;
        assertEquals( COMMENT, nonStdResult.getComment().get() );
        assertArrayEquals( CROSS_REF_STR, TestUtils.toSortedArray( nonStdResult.getSeeAlsos() ) );
        assertEquals( EXTRACTED_TEXT, nonStdResult.getLicenseText() );
        assertEquals( spdxDoc.getIdPrefix() + LICENSE_ID, nonStdResult.getObjectUri() );
        assertEquals( LICENSE_NAME, nonStdResult.getName().get() );
    }

    /**
     * Test method for {@link org.spdx.maven.utils.SpdxV3LicenseManager#spdxLicenseToMavenLicense(org.spdx.rdfparser.AnyLicenseInfo)}.
     *
     * @throws LicenseManagerException
     * @throws LicenseMapperException
     * @throws InvalidSPDXAnalysisException 
     */
    @Test
    public void testSpdxLicenseToMavenLicense() throws LicenseManagerException, LicenseMapperException, InvalidSPDXAnalysisException
    {
        SpdxV3LicenseManager licenseManager = new SpdxV3LicenseManager( spdxDoc );
        // standard license
        AnyLicenseInfo licenseInfo = LicenseInfoFactory.parseSPDXLicenseString( APACHE_LICENSE_ID );
        License result = licenseManager.spdxLicenseToMavenLicense( licenseInfo );
        assertEquals( result.getName(), ( (ListedLicense) licenseInfo ).getName().get() );
        String resultUrl = result.getUrl().replace( "https", "http" );
        assertTrue( APACHE_CROSS_REF_URL2.equals( resultUrl ) || APACHE_CROSS_REF_URL3.equals( resultUrl ) );

        // non standard license
        final String LICENSE_ID = "LicenseRef-nonStd1";
        final String LICENSE_TEXT = "License Text";
        final ArrayList<String> CROSS_REF_URLS = new ArrayList<>();
        CROSS_REF_URLS.add( "http://nonStd.url" );
        final String LICENSE_NAME = "License Name";
        final String LICENSE_COMMENT = "License Comment";
        CustomLicense nonStd = new CustomLicense( spdxDoc.getModelStore(), spdxDoc.getIdPrefix() + LICENSE_ID, 
                                                  spdxDoc.getCopyManager(), true, spdxDoc.getIdPrefix() );
        nonStd.setLicenseText( LICENSE_TEXT );
        nonStd.getSeeAlsos().addAll( CROSS_REF_URLS );
        nonStd.setName( LICENSE_NAME );
        nonStd.setComment( LICENSE_COMMENT );
        result = licenseManager.spdxLicenseToMavenLicense( nonStd );
        assertEquals( LICENSE_NAME, result.getName() );
        assertEquals( CROSS_REF_URLS.get( 0 ), result.getUrl() );

        // non-standard without name
        final String LICENSE_ID2 = "LicenseRef-second";
        final String LICENSE_TEXT2 = "second text";
        CustomLicense noName = new CustomLicense( spdxDoc.getModelStore(), spdxDoc.getIdPrefix() + LICENSE_ID2, 
                                                   spdxDoc.getCopyManager(), true, spdxDoc.getIdPrefix() );
        noName.setLicenseText( LICENSE_TEXT2 );
        result = licenseManager.spdxLicenseToMavenLicense( noName );
        assertEquals( LICENSE_ID2, result.getName() );
    }
}
