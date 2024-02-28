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
import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.ModelCopyManager;
import org.spdx.library.model.SpdxDocument;
import org.spdx.library.model.license.AnyLicenseInfo;
import org.spdx.library.model.license.LicenseInfoFactory;
import org.spdx.library.model.license.SpdxNoAssertionLicense;
import org.spdx.storage.simple.InMemSpdxStore;

public class TestMavenToSpdxLicenseMapper
{
    private static final String TEST_SPDX_DOCUMENT_URL = "http://www.spdx.org/documents/test";
    private static final String APACHE2_URL = "http://www.apache.org/licenses/LICENSE-2.0";
    private static final String APACHE_SPDX_ID = "Apache-2.0";

    private static final String MIT_URL = "http://opensource.org/license/mit/";
    private static final String MIT_SPDX_ID = "MIT";

    SpdxDocument spdxDoc = null;
    Log log = null;


    /**
     * @throws java.lang.Exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception
    {
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
        spdxDoc = new SpdxDocument( new InMemSpdxStore(), TEST_SPDX_DOCUMENT_URL, new ModelCopyManager(), true );
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
    public void testMavenLicenseListToSpdxLicenseNone() throws LicenseMapperException, InvalidSPDXAnalysisException
    {
        List<License> licenseList = new ArrayList<>();
        AnyLicenseInfo result = MavenToSpdxLicenseMapper.getInstance().mavenLicenseListToSpdxLicense(
                licenseList.subList( 0, 0 ), spdxDoc );
        assertEquals( new SpdxNoAssertionLicense(), result );
    }

    @Test
    public void testMavenLicenseListToSpdxLicenseUnknown() throws LicenseMapperException, InvalidSPDXAnalysisException
    {
        List<License> licenseList = new ArrayList<>();
        License license = new License();
        license.setUrl( "http://not.a.known.url" );
        licenseList.add( license );
        AnyLicenseInfo result = MavenToSpdxLicenseMapper.getInstance().mavenLicenseListToSpdxLicense(
                licenseList, spdxDoc );
        assertEquals( new SpdxNoAssertionLicense(), result );
    }

    @Test
    public void testMavenLicenseListToSpdxLicenseSingle() throws LicenseMapperException, InvalidSPDXAnalysisException
    {
        List<License> licenseList = new ArrayList<>();
        License license = new License();
        license.setUrl( APACHE2_URL );
        licenseList.add( license );
        AnyLicenseInfo result = MavenToSpdxLicenseMapper.getInstance().mavenLicenseListToSpdxLicense(
                licenseList, spdxDoc );
        AnyLicenseInfo expected = LicenseInfoFactory.parseSPDXLicenseString( APACHE_SPDX_ID );
        assertEquals( expected, result );
    }

    @Test
    public void testMavenLicenseListToSpdxLicenseConjunctive() throws LicenseMapperException, InvalidSPDXAnalysisException
    {
        List<License> licenseList = new ArrayList<>();
        License license = new License();
        license.setUrl( APACHE2_URL );
        licenseList.add( license );
        License licenseM = new License();
        licenseM.setUrl( MIT_URL );
        licenseList.add( licenseM );
        AnyLicenseInfo result = MavenToSpdxLicenseMapper.getInstance().mavenLicenseListToSpdxLicense(
                licenseList, spdxDoc );
        AnyLicenseInfo expected = LicenseInfoFactory.parseSPDXLicenseString( APACHE_SPDX_ID + " AND " + MIT_SPDX_ID );
        assertEquals( expected, result );
    }

    @Test
    public void testMavenLicenseListToSpdxLicenseConunctiveUnknown() throws LicenseMapperException, InvalidSPDXAnalysisException
    {
        List<License> licenseList = new ArrayList<>();
        License license = new License();
        license.setUrl( APACHE2_URL );
        licenseList.add( license );
        License licenseM = new License();
        licenseM.setUrl( "http://unknown.url" );
        licenseList.add( licenseM );
        AnyLicenseInfo result = MavenToSpdxLicenseMapper.getInstance().mavenLicenseListToSpdxLicense(
                licenseList, spdxDoc );
        AnyLicenseInfo expected = LicenseInfoFactory.parseSPDXLicenseString( APACHE_SPDX_ID );
        assertEquals( expected, result );
    }
}
