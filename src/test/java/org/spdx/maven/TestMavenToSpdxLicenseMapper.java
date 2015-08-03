package org.spdx.maven;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.maven.model.License;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.LicenseInfoFactory;
import org.spdx.rdfparser.license.SpdxNoAssertionLicense;
import org.spdx.spdxspreadsheet.InvalidLicenseStringException;

public class TestMavenToSpdxLicenseMapper
{

    private static final String APACHE2_URL = "http://www.apache.org/licenses/LICENSE-2.0";
    private static final String APACHE_SPDX_ID = "Apache-2.0";
    
    private static final String MIT_URL = "http://www.opensource.org/licenses/MIT";
    private static final String MIT_SPDX_ID = "MIT";

    @Before
    public void setUp()
        throws Exception
    {
    }

    @After
    public void tearDown()
        throws Exception
    {
    }
    
    @Test
    public void testUrlToSpdxId() throws LicenseMapperException {
        String retval = MavenToSpdxLicenseMapper.getInstance( null ).urlToSpdxId( APACHE2_URL );
        assertEquals( APACHE_SPDX_ID, retval );
    }

    @Test
    public void testGetMap() throws LicenseMapperException
    {
        Map<String, String> retval = MavenToSpdxLicenseMapper.getInstance( null ).getMap();
        assertEquals( MIT_SPDX_ID, retval.get( MIT_URL ) );
    }

    @Test
    public void testMavenLicenseListToSpdxLicenseNone() throws LicenseMapperException
    {
        List<License> licenseList = new ArrayList<License>();
        AnyLicenseInfo result = MavenToSpdxLicenseMapper.getInstance( null ).mavenLicenseListToSpdxLicense( licenseList );
        assertEquals( new SpdxNoAssertionLicense(), result );
    }
    
    @Test
    public void testMavenLicenseListToSpdxLicenseUnknown() throws LicenseMapperException
    {
        List<License> licenseList = new ArrayList<License>();
        License license = new License();
        license.setUrl( "http://not.a.known.url" );
        licenseList.add( license );
        AnyLicenseInfo result = MavenToSpdxLicenseMapper.getInstance( null ).mavenLicenseListToSpdxLicense( licenseList );
        assertEquals( new SpdxNoAssertionLicense(), result );
    }
    
    @Test
    public void testMavenLicenseListToSpdxLicenseSingle() throws LicenseMapperException, InvalidLicenseStringException
    {
        List<License> licenseList = new ArrayList<License>();
        License license = new License();
        license.setUrl( APACHE2_URL );
        licenseList.add( license );
        AnyLicenseInfo result = MavenToSpdxLicenseMapper.getInstance( null ).mavenLicenseListToSpdxLicense( licenseList );
        AnyLicenseInfo expected = LicenseInfoFactory.parseSPDXLicenseString( APACHE_SPDX_ID );
        assertEquals( expected, result );
    }
    
    @Test
    public void testMavenLicenseListToSpdxLicenseConjunctive() throws LicenseMapperException, InvalidLicenseStringException
    {
        List<License> licenseList = new ArrayList<License>();
        License license = new License();
        license.setUrl( APACHE2_URL );
        licenseList.add( license );
        License licenseM = new License();
        licenseM.setUrl( MIT_URL );
        licenseList.add( licenseM );
        AnyLicenseInfo result = MavenToSpdxLicenseMapper.getInstance( null ).mavenLicenseListToSpdxLicense( licenseList );
        AnyLicenseInfo expected = LicenseInfoFactory.parseSPDXLicenseString( APACHE_SPDX_ID + " AND " + MIT_SPDX_ID );
        assertEquals( expected, result );
    }
    
    @Test
    public void testMavenLicenseListToSpdxLicenseConunctiveUnknown() throws LicenseMapperException, InvalidLicenseStringException
    {
        List<License> licenseList = new ArrayList<License>();
        License license = new License();
        license.setUrl( APACHE2_URL );
        licenseList.add( license );
        License licenseM = new License();
        licenseM.setUrl( "http://unknown.url" );
        licenseList.add( licenseM );
        AnyLicenseInfo result = MavenToSpdxLicenseMapper.getInstance( null ).mavenLicenseListToSpdxLicense( licenseList );
        AnyLicenseInfo expected = LicenseInfoFactory.parseSPDXLicenseString( APACHE_SPDX_ID );
        assertEquals( expected, result );
    }
}
