package org.spdx.maven.utils;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.maven.model.License;
import org.apache.maven.plugin.logging.Log;
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
import org.spdx.library.model.v2.SpdxDocument;
import org.spdx.library.model.v3_0_1.core.Element;
import org.spdx.storage.simple.InMemSpdxStore;

public class TestMavenToSpdxLicenseMapper
{
    private static final String TEST_SPDX_DOCUMENT_URL = "http://www.spdx.org/documents/test";
    private static final String APACHE2_URL = "http://www.apache.org/licenses/LICENSE-2.0";
    private static final String APACHE_SPDX_ID = "Apache-2.0";

    private static final String MIT_URL = "http://opensource.org/license/mit/";
    private static final String MIT_SPDX_ID = "MIT";

    SpdxDocument spdxDoc = null;
    Element spdxV3Doc = null;
    Log log = null;


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
        spdxDoc = new SpdxDocument( new InMemSpdxStore(), TEST_SPDX_DOCUMENT_URL, new ModelCopyManager(), true );
        spdxV3Doc = new org.spdx.library.model.v3_0_1.software.SpdxPackage( new InMemSpdxStore(), TEST_SPDX_DOCUMENT_URL + "/v3doc", 
                                                                            new ModelCopyManager(), true, TEST_SPDX_DOCUMENT_URL + "/");
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception
    {
        spdxDoc = null;
    }

    @Test
    public void testUrlToSpdxId() throws LicenseMapperException
    {
        String retval = MavenToSpdxLicenseMapper.getInstance().urlToSpdxId( APACHE2_URL );
        assertEquals( APACHE_SPDX_ID, retval );
    }

    @Test
    public void testGetMap() throws LicenseMapperException
    {
        Map<String, String> retval = MavenToSpdxLicenseMapper.getInstance().getMap();
        assertEquals( MIT_SPDX_ID, retval.get( MIT_URL ) );
    }

    @Test
    public void testMavenLicenseListToSpdxLicenseNoneV2() throws LicenseMapperException, InvalidSPDXAnalysisException
    {
        List<License> licenseList = new ArrayList<>();
        org.spdx.library.model.v2.license.AnyLicenseInfo result = MavenToSpdxLicenseMapper.getInstance().mavenLicenseListToSpdxV2License(
                licenseList.subList( 0, 0 ), spdxDoc );
        assertEquals( new org.spdx.library.model.v2.license.SpdxNoAssertionLicense(), result );
    }
    
    @Test
    public void testMavenLicenseListToSpdxLicenseNoneV3() throws LicenseMapperException, InvalidSPDXAnalysisException
    {
        List<License> licenseList = new ArrayList<>();
        org.spdx.library.model.v3_0_1.simplelicensing.AnyLicenseInfo result = MavenToSpdxLicenseMapper.getInstance().mavenLicenseListToSpdxV3License(
                licenseList.subList( 0, 0 ), spdxV3Doc );
        assertEquals( new org.spdx.library.model.v3_0_1.expandedlicensing.NoAssertionLicense(), result );
    }

    @Test
    public void testMavenLicenseListToSpdxLicenseUnknownV2() throws LicenseMapperException, InvalidSPDXAnalysisException
    {
        List<License> licenseList = new ArrayList<>();
        License license = new License();
        license.setUrl( "http://not.a.known.url" );
        licenseList.add( license );
        org.spdx.library.model.v2.license.AnyLicenseInfo result = MavenToSpdxLicenseMapper.getInstance().mavenLicenseListToSpdxV2License(
                licenseList, spdxDoc );
        assertEquals( new org.spdx.library.model.v2.license.SpdxNoAssertionLicense(), result );
    }
    
    @Test
    public void testMavenLicenseListToSpdxLicenseUnknownV3() throws LicenseMapperException, InvalidSPDXAnalysisException
    {
        List<License> licenseList = new ArrayList<>();
        License license = new License();
        license.setUrl( "http://not.a.known.url" );
        licenseList.add( license );
        org.spdx.library.model.v3_0_1.simplelicensing.AnyLicenseInfo result = MavenToSpdxLicenseMapper.getInstance().mavenLicenseListToSpdxV3License(
                licenseList, spdxV3Doc );
        assertEquals( new org.spdx.library.model.v3_0_1.expandedlicensing.NoAssertionLicense(), result );
    }

    @Test
    public void testMavenLicenseListToSpdxLicenseSingleV2() throws LicenseMapperException, InvalidSPDXAnalysisException
    {
        List<License> licenseList = new ArrayList<>();
        License license = new License();
        license.setUrl( APACHE2_URL );
        licenseList.add( license );
        org.spdx.library.model.v2.license.AnyLicenseInfo result = MavenToSpdxLicenseMapper.getInstance().mavenLicenseListToSpdxV2License(
                licenseList, spdxDoc );
        org.spdx.library.model.v2.license.AnyLicenseInfo expected = LicenseInfoFactory.parseSPDXLicenseStringCompatV2( APACHE_SPDX_ID );
        assertEquals( expected, result );
    }
    
    @Test
    public void testMavenLicenseListToSpdxLicenseSingleV3() throws LicenseMapperException, InvalidSPDXAnalysisException
    {
        List<License> licenseList = new ArrayList<>();
        License license = new License();
        license.setUrl( APACHE2_URL );
        licenseList.add( license );
        org.spdx.library.model.v3_0_1.simplelicensing.AnyLicenseInfo result = MavenToSpdxLicenseMapper.getInstance().mavenLicenseListToSpdxV3License(
                licenseList, spdxV3Doc );
        org.spdx.library.model.v3_0_1.simplelicensing.AnyLicenseInfo expected = LicenseInfoFactory.parseSPDXLicenseString( APACHE_SPDX_ID );
        assertEquals( expected, result );
    }

    @Test
    public void testMavenLicenseListToSpdxLicenseConjunctiveV2() throws LicenseMapperException, InvalidSPDXAnalysisException
    {
        List<License> licenseList = new ArrayList<>();
        License license = new License();
        license.setUrl( APACHE2_URL );
        licenseList.add( license );
        License licenseM = new License();
        licenseM.setUrl( MIT_URL );
        licenseList.add( licenseM );
        org.spdx.library.model.v2.license.AnyLicenseInfo result = MavenToSpdxLicenseMapper.getInstance().mavenLicenseListToSpdxV2License(
                licenseList, spdxDoc );
        org.spdx.library.model.v2.license.AnyLicenseInfo expected = LicenseInfoFactory.parseSPDXLicenseStringCompatV2( APACHE_SPDX_ID + " AND " + MIT_SPDX_ID );
        assertEquals( expected, result );
    }
    
    @Test
    public void testMavenLicenseListToSpdxLicenseConjunctiveV3() throws LicenseMapperException, InvalidSPDXAnalysisException
    {
        List<License> licenseList = new ArrayList<>();
        License license = new License();
        license.setUrl( APACHE2_URL );
        licenseList.add( license );
        License licenseM = new License();
        licenseM.setUrl( MIT_URL );
        licenseList.add( licenseM );
        org.spdx.library.model.v3_0_1.simplelicensing.AnyLicenseInfo result = MavenToSpdxLicenseMapper.getInstance().mavenLicenseListToSpdxV3License(
                licenseList, spdxV3Doc );
        org.spdx.library.model.v3_0_1.simplelicensing.AnyLicenseInfo expected = LicenseInfoFactory.parseSPDXLicenseString( APACHE_SPDX_ID + " AND " + MIT_SPDX_ID );
        assertEquals( expected, result );
    }

    @Test
    public void testMavenLicenseListToSpdxLicenseConunctiveUnknownV2() throws LicenseMapperException, InvalidSPDXAnalysisException
    {
        List<License> licenseList = new ArrayList<>();
        License license = new License();
        license.setUrl( APACHE2_URL );
        licenseList.add( license );
        License licenseM = new License();
        licenseM.setUrl( "http://unknown.url" );
        licenseList.add( licenseM );
        org.spdx.library.model.v2.license.AnyLicenseInfo result = MavenToSpdxLicenseMapper.getInstance().mavenLicenseListToSpdxV2License(
                licenseList, spdxDoc );
        org.spdx.library.model.v2.license.AnyLicenseInfo expected = LicenseInfoFactory.parseSPDXLicenseStringCompatV2( APACHE_SPDX_ID );
        assertEquals( expected, result );
    }
    
    @Test
    public void testMavenLicenseListToSpdxLicenseConunctiveUnknownV3() throws LicenseMapperException, InvalidSPDXAnalysisException
    {
        List<License> licenseList = new ArrayList<>();
        License license = new License();
        license.setUrl( APACHE2_URL );
        licenseList.add( license );
        License licenseM = new License();
        licenseM.setUrl( "http://unknown.url" );
        licenseList.add( licenseM );
        org.spdx.library.model.v3_0_1.simplelicensing.AnyLicenseInfo result = MavenToSpdxLicenseMapper.getInstance().mavenLicenseListToSpdxV3License(
                licenseList, spdxV3Doc );
        org.spdx.library.model.v3_0_1.simplelicensing.AnyLicenseInfo expected = LicenseInfoFactory.parseSPDXLicenseString( APACHE_SPDX_ID );
        assertEquals( expected, result );
    }
}
