/*
 * Copyright 2014 The Apache Software Foundation.
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
package org.spdx.maven;

import static org.junit.Assert.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.spdx.rdfparser.InvalidSPDXAnalysisException;
import org.spdx.rdfparser.SPDXConjunctiveLicenseSet;
import org.spdx.rdfparser.SPDXDocument;
import org.spdx.rdfparser.SPDXLicenseInfo;
import org.spdx.rdfparser.SPDXLicenseInfoFactory;
import org.spdx.rdfparser.SPDXNonStandardLicense;
import org.spdx.rdfparser.SPDXStandardLicense;
import org.spdx.spdxspreadsheet.InvalidLicenseStringException;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;

import org.apache.maven.model.License;
import org.apache.maven.plugin.logging.Log;

/**
 * Unit tests for LicenseManager class
 * @author Gary O'Neall
 *
 */
public class TestLicenseManager
{
    private static final String TEST_SPDX_DOCUMENT_URL = "http://www.spdx.org/documents/test";
    static final String APACHE_CROSS_REF_URL = "http://www.apache.org/licenses/LICENSE-2.0";
    static final String APACHE_LICENSE_ID = "Apache-2.0";
    static final String APACHE_LICENSE_NAME = "Apache License 2.0";
    static final String APSL_CROSS_REF_URL = "http://www.opensource.apple.com/source/IOSerialFamily/IOSerialFamily-7/APPLE_LICENSE";
    static final String APSL_LICENSE_ID = "APSL-1.1";
    
    Model model = null;
    SPDXDocument spdxDoc = null;
    Log log = null;


    /**
     * @throws java.lang.Exception
     */
    @BeforeClass
    public static void setUpBeforeClass()
        throws Exception
    {
    }

    /**
     * @throws java.lang.Exception
     */
    @AfterClass
    public static void tearDownAfterClass()
        throws Exception
    {
    }

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp()
        throws Exception
    {
        model = ModelFactory.createDefaultModel();
        spdxDoc = new SPDXDocument(model);
        spdxDoc.createSpdxAnalysis( TEST_SPDX_DOCUMENT_URL );
        spdxDoc.createSpdxPackage();
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown()
        throws Exception
    {
        spdxDoc = null;
        model = null;
    }

    /**
     * Test method for {@link org.spdx.maven.LicenseManager#LicenseManager(org.spdx.rdfparser.SPDXDocument, org.apache.maven.plugin.logging.Log)}.
     */
    @Test
    public void testLicenseManager()
    {
        LicenseManager licenseManager = new LicenseManager(spdxDoc, log, false);
    }

    /**
     * Test method for {@link org.spdx.maven.LicenseManager#addNonStandardLicense(org.spdx.maven.NonStandardLicense)}.
     * @throws MalformedURLException 
     * @throws LicenseManagerException 
     * @throws InvalidSPDXAnalysisException 
     */
    @Test
    public void testAddNonStandardLicense() throws MalformedURLException, LicenseManagerException, InvalidSPDXAnalysisException
    {
        LicenseManager licenseManager = new LicenseManager(spdxDoc, log, false);
        NonStandardLicense lic = new NonStandardLicense();
        final String COMMENT = "comment";
        final String[] CROSS_REF_STR = new String[] {"http://www.licenseRef1", "http://www.licenseref2"};
        final URL[] CROSS_REF = new URL[] {new URL(CROSS_REF_STR[0]), new URL(CROSS_REF_STR[1])};
        final String EXTRACTED_TEXT = "extracted text";
        final String LICENSE_ID = "LicenseRef-licenseId";
        final String LICENSE_NAME = "licenseName";
        lic.setComment( COMMENT );
        lic.setCrossReference( CROSS_REF );
        lic.setExtractedText( EXTRACTED_TEXT );
        lic.setLicenseId( LICENSE_ID );
        lic.setName( LICENSE_NAME );
        
        licenseManager.addNonStandardLicense( lic );
        
        SPDXNonStandardLicense[] result = spdxDoc.getExtractedLicenseInfos();
        assertEquals( 1, result.length );
        assertEquals( COMMENT, result[0].getComment() );
        assertArraysEqual( CROSS_REF_STR, result[0].getSourceUrls() );
        assertEquals( EXTRACTED_TEXT, result[0].getText() );
        assertEquals( LICENSE_ID, result[0].getId() );
        assertEquals( LICENSE_NAME, result[0].getLicenseName() );
        
        NonStandardLicense lic2 = new NonStandardLicense();
        final String LICENSE_ID2 = "LicenseRef-licenseId2";
        final String EXTRACTED_TEXT2 = "Second extracted text";
        lic2.setLicenseId( LICENSE_ID2 );
        lic2.setExtractedText( EXTRACTED_TEXT2 );
        
        licenseManager.addNonStandardLicense( lic2 );
        
        result = spdxDoc.getExtractedLicenseInfos();
        assertEquals( 2, result.length );
        SPDXNonStandardLicense licResult = result[1];
        if ( !licResult.getId().equals( LICENSE_ID2 ) ) {
            licResult = result[0];
        }
        assertEquals( EXTRACTED_TEXT2, licResult.getText() );
        assertEquals( LICENSE_ID2, licResult.getId() );

    }

    public static void assertArraysEqual( Object[] a1, Object[] a2 )
    {
        if ( a1 == null ) {
            if ( a2 == null ) {
                return;
            } else {
                fail( "Arrays not equal - a1 is null" );
            }
        }
        if ( a2 == null ) {
            fail( "Arrays not equal - a2 is null" );
        }
        assertEquals( a1.length, a2.length );
        for ( int i = 0; i < a1.length; i++ ) {
            boolean found = false;
            for ( int j = 0; j < a2.length; j++ ) {
                if ( a1[i].equals( a2[j] ) ) {
                    found = true;
                    break;
                }
            }
            assertTrue( found );
        }
    }

    /**
     * Test method for {@link org.spdx.maven.LicenseManager#mavenLicenseListToSpdxLicense(java.util.List)}.
     * @throws LicenseManagerException 
     */
    @Test
    public void testMavenLicenseListToSpdxLicense() throws LicenseManagerException
    {
        final String LICENSE1_NAME = "Apachelicense1";
        final String LICENSE2_NAME = "APSLlicense2";
        
        License apache = new License();
        apache.setName( LICENSE1_NAME );
        apache.setUrl( APACHE_CROSS_REF_URL );
        License apsl = new License();
        apsl.setName( LICENSE2_NAME );
        apsl.setUrl( APSL_CROSS_REF_URL );
        
        ArrayList<License> licenseList = new ArrayList<License>();
        licenseList.add( apache );
        licenseList.add( apsl );
        
        LicenseManager licenseManager = new LicenseManager( spdxDoc, log, true );
        
        SPDXLicenseInfo result = licenseManager.mavenLicenseListToSpdxLicense( licenseList );
        assertTrue( result instanceof SPDXConjunctiveLicenseSet );
        SPDXLicenseInfo[] resultLicenses = ((SPDXConjunctiveLicenseSet)result).getSPDXLicenseInfos();
        assertEquals( 2, resultLicenses.length );
        assertTrue( resultLicenses[0] instanceof SPDXStandardLicense );
        if ( !((SPDXStandardLicense)resultLicenses[0]).getId().equals( APACHE_LICENSE_ID ) &&
                        !((SPDXStandardLicense)resultLicenses[0]).getId().equals( APSL_LICENSE_ID )) {
            fail( "Unrecognized first license "+((SPDXStandardLicense)resultLicenses[0]).getId());
        }
        assertTrue( resultLicenses[1] instanceof SPDXStandardLicense );
        if ( !((SPDXStandardLicense)resultLicenses[1]).getId().equals( APACHE_LICENSE_ID ) &&
                        !((SPDXStandardLicense)resultLicenses[1]).getId().equals( APSL_LICENSE_ID )) {
            fail( "Unrecognized second license "+((SPDXStandardLicense)resultLicenses[1]).getId());
        }
    }

    /**
     * Test method for {@link org.spdx.maven.LicenseManager#mavenLicenseToSpdxLicense(org.apache.maven.model.License)}.
     * @throws LicenseManagerException 
     * @throws MalformedURLException 
     */
    @Test
    public void testMavenLicenseToSpdxLicense() throws LicenseManagerException, MalformedURLException
    {
        // unmapped license - can not test without valid log
        // standard license
        final String LICENSE1_NAME = "Apachelicense1";
        License apache = new License();
        apache.setName( LICENSE1_NAME );
        apache.setUrl( APACHE_CROSS_REF_URL );
        LicenseManager licenseManager = new LicenseManager( spdxDoc, log, true );
        
        SPDXLicenseInfo result = licenseManager.mavenLicenseToSpdxLicense( apache );
        assertTrue( result instanceof SPDXStandardLicense );
        assertEquals( APACHE_LICENSE_ID, ((SPDXStandardLicense)result).getId() );
        
        // nonstandard license
        NonStandardLicense lic = new NonStandardLicense();
        final String COMMENT = "comment";
        final String[] CROSS_REF_STR = new String[] {"http://www.licenseRef1"};
        final URL[] CROSS_REF = new URL[] {new URL(CROSS_REF_STR[0])};
        final String EXTRACTED_TEXT = "extracted text";
        final String LICENSE_ID = "LicenseRef-licenseId";
        final String LICENSE_NAME = "licenseName";
        lic.setComment( COMMENT );
        lic.setCrossReference( CROSS_REF );
        lic.setExtractedText( EXTRACTED_TEXT );
        lic.setLicenseId( LICENSE_ID );
        lic.setName( LICENSE_NAME );       
        licenseManager.addNonStandardLicense( lic );
        License nonStd = new License();
        nonStd.setComments( COMMENT );
        nonStd.setName( LICENSE_NAME );
        nonStd.setUrl( CROSS_REF_STR[0] );
        result = licenseManager.mavenLicenseToSpdxLicense( nonStd );
        
        assertTrue( result instanceof SPDXNonStandardLicense );
        SPDXNonStandardLicense nonStdResult = (SPDXNonStandardLicense)result;
        assertEquals( COMMENT, nonStdResult.getComment() );
        assertArraysEqual( CROSS_REF_STR, nonStdResult.getSourceUrls() );
        assertEquals( EXTRACTED_TEXT, nonStdResult.getText() );
        assertEquals( LICENSE_ID, nonStdResult.getId() );
        assertEquals( LICENSE_NAME, nonStdResult.getLicenseName() );
    }

    /**
     * Test method for {@link org.spdx.maven.LicenseManager#spdxLicenseToMavenLicense(org.spdx.rdfparser.SPDXLicenseInfo)}.
     * @throws InvalidLicenseStringException 
     * @throws LicenseManagerException 
     */
    @Test
    public void testSpdxLicenseToMavenLicense() throws InvalidLicenseStringException, LicenseManagerException
    {
        LicenseManager licenseManager = new LicenseManager( spdxDoc, log, false );
        // standard license
        SPDXLicenseInfo licenseInfo = SPDXLicenseInfoFactory.parseSPDXLicenseString( APACHE_LICENSE_ID );
        License result = licenseManager.spdxLicenseToMavenLicense( licenseInfo );
        assertEquals( result.getName(), ((SPDXStandardLicense)licenseInfo).getName() );
        assertEquals( result.getUrl(), APACHE_CROSS_REF_URL );
        
        // non standard license
        final String LICENSE_ID = "LicenseRef-nonStd1";
        final String LICENSE_TEXT = "License Text";
        final String[] CROSS_REF_URLS = new String[] {"http://nonStd.url"};
        final String LICENSE_NAME = "License Name";
        final String LICENSE_COMMENT = "License Comment";
        SPDXLicenseInfo nonStd = new SPDXNonStandardLicense( LICENSE_ID, LICENSE_TEXT, 
                                                             LICENSE_NAME, CROSS_REF_URLS, LICENSE_COMMENT );
        result = licenseManager.spdxLicenseToMavenLicense( nonStd );
        assertEquals( LICENSE_NAME, result.getName() );
        assertEquals( CROSS_REF_URLS[0], result.getUrl() );
        
        // non standard without name
        final String LICENSE_ID2 = "LicenseRef-second";
        final String LICENSE_TEXT2 = "second text";
        SPDXLicenseInfo noName = new SPDXNonStandardLicense( LICENSE_ID2, LICENSE_TEXT2 );
        result = licenseManager.spdxLicenseToMavenLicense( noName );
        assertEquals( LICENSE_ID2, result.getName() );
    }

}
