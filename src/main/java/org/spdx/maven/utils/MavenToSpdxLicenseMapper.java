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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.model.License;

import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.model.SpdxDocument;
import org.spdx.library.model.license.AnyLicenseInfo;
import org.spdx.library.model.license.LicenseInfoFactory;
import org.spdx.library.model.license.SpdxListedLicense;
import org.spdx.library.model.license.SpdxNoAssertionLicense;
import org.spdx.storage.listedlicense.LicenseJsonTOC;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * Singleton class which maps Maven license objects to SPDX licenses.
 * <p>
 * The license mapping uses the JSON file from the spdx.org/licenses/licenses.json file
 * <p>
 * If the site spdx.org/licenses is not accessible, then static version of the file will be used
 * <p>
 * The seeAlso property of the SPDX file is matched to the Maven license URL.
 *
 * @author Gary O'Neall
 */
public class MavenToSpdxLicenseMapper
{
    private static final Logger LOG = LoggerFactory.getLogger( MavenToSpdxLicenseMapper.class );

    private static final String SPDX_LICENSE_URL_PREFIX = "https://spdx.org/licenses/";
    private static final String LISTED_LICENSE_JSON_URL = SPDX_LICENSE_URL_PREFIX + "licenses.json";
    private static final String LISTED_LICENSE_JSON_PATH = "resources/licenses.json";

    static volatile MavenToSpdxLicenseMapper instance;
    private static Object instanceMutex = new Object();
    private Map<String, String> urlStringToSpdxLicenseId;

    private MavenToSpdxLicenseMapper() throws LicenseMapperException
    {
        // Can not instantiate directly - singleton class
        InputStream is = null;
        if (!"true".equals(System.getProperty( "SPDXParser.OnlyUseLocalLicenses"))){
            try
            {
                URL listedLicenseJsonUrl = new URL( LISTED_LICENSE_JSON_URL );
                is = listedLicenseJsonUrl.openStream();
            }
            catch ( MalformedURLException e )
            {
                LOG.warn( "Invalid JSON URL for SPDX listed licenses.  Using cached version" );
            }
            catch ( IOException e )
            {
                LOG.warn( "IO Exception opening web page for JSON for SPDX listed licenses.  Using cached version" );
            }
        }
        if ( is == null )
        {
            // use the cached version
            is = LicenseManager.class.getClassLoader().getResourceAsStream( LISTED_LICENSE_JSON_PATH );
        }

        try (BufferedReader reader = new BufferedReader( new InputStreamReader( is, Charset.defaultCharset() ) ))
        {
            initializeUrlMap( reader );
        }
        catch ( IOException e )
        {
            LOG.warn( "IO error closing listed license reader", e );
        }
    }

    public static MavenToSpdxLicenseMapper getInstance() throws LicenseMapperException
    {
        MavenToSpdxLicenseMapper result = instance;
        if ( result == null )
        {
            synchronized ( instanceMutex )
            {
                result = instance;
                if ( result == null )
                {
                    instance = result = new MavenToSpdxLicenseMapper();
                }
            }
        }
        return result;
    }

    /**
     * @param url URL string for a license
     * @return SPDX ID associated with the URL
     */
    public String urlToSpdxId( String url )
    {
        return this.urlStringToSpdxLicenseId.get( url );
    }

    /**
     * Initialize the urlStringToSpdxLicense map with the SPDX listed licenses
     *
     * @param jsonReader Reader for the JSON input file containing the listed licenses
     * @throws LicenseMapperException
     */
    private void initializeUrlMap( BufferedReader jsonReader ) throws LicenseMapperException
    {
        LicenseJsonTOC jsonToc;
        try
        {
            Gson gson = new Gson();
            StringBuilder tocJsonStr = new StringBuilder();
            String line;
            while((line = jsonReader.readLine()) != null) {
                tocJsonStr.append(line);
            }
            jsonToc = gson.fromJson(tocJsonStr.toString(), LicenseJsonTOC.class);
        }
        catch ( IOException e1 )
        {
            throw new LicenseMapperException( "I/O Error parsing listed licenses", e1 );
        }

        urlStringToSpdxLicenseId = new HashMap<>();
        List<String> urlsWithMultipleIds = new ArrayList<>();
        for ( LicenseJsonTOC.LicenseJson licenseJson:jsonToc.getLicenses() )
        {
            String licenseId = licenseJson.getLicenseId();
            this.urlStringToSpdxLicenseId.put( SPDX_LICENSE_URL_PREFIX + licenseId, licenseId );
            if ( licenseJson.getSeeAlso() != null )
            {
                for ( String url:licenseJson.getSeeAlso() )
                {
                    url = url.replaceAll( "https", "http" );
                    if ( this.urlStringToSpdxLicenseId.containsKey( url ) )
                    {
                        urlsWithMultipleIds.add( url );
                    }
                    else
                    {
                        this.urlStringToSpdxLicenseId.put( url, licenseId );
                    }
                }
            }
        }
        // Remove any mappings which have ambiguous URL mappings
        for ( String redundantUrl : urlsWithMultipleIds )
        {
            this.urlStringToSpdxLicenseId.remove( redundantUrl );
        }
        addManualMappings();
    }

    /**
     * This is a bit of an override on the official SPDX license list Add some specific URL mappings that are commonly
     * used in SPDX files
     */
    private void addManualMappings()
    {
        // TODO: Request these be added to the SPDX license list and remove once they
        // have been added
        this.urlStringToSpdxLicenseId.put( "http://www.apache.org/licenses/LICENSE-2.0.txt", "Apache-2.0" );
        this.urlStringToSpdxLicenseId.put( "http://www.opensource.org/licenses/cpl1.0.txt", "CPL-1.0" );
        this.urlStringToSpdxLicenseId.put( "http://www.opensource.org/licenses/mit-license.php", "MIT" );
        // The following is in the listed licenses, but is duplicated in multiple SPDX license ID's
        // adding it back for the license it was originally targeted for
        this.urlStringToSpdxLicenseId.put( "http://www.mozilla.org/MPL/MPL-1.0.txt", "MPL-1.0" );
    }

    /**
     * Map a list of Maven licenses to an SPDX license.  If no licenses are supplied, SpdxNoAssertion license is
     * returned.  if a single license is supplied, and a URL can be found matching a listed license, the listed license
     * is returned.  if a single license is supplied, and a URL can not be found matching a listed license,
     * SpdxNoAssertion is returned.  If multiple licenses are supplied, a conjunctive license is returned containing all
     * mapped SPDX licenses.
     *
     * @param licenseList list of licenses
     * @param spdxDoc     SPDX document which will hold the licenses
     * @return
     * @throws InvalidSPDXAnalysisException 
     */
    public AnyLicenseInfo mavenLicenseListToSpdxLicense( List<License> licenseList, SpdxDocument spdxDoc ) throws InvalidSPDXAnalysisException
    {
        if ( licenseList == null )
        {
            return new SpdxNoAssertionLicense();
        }
        List<AnyLicenseInfo> spdxLicenses = new ArrayList<>();
        for ( License license : licenseList )
        {
            SpdxListedLicense listedLicense = mavenLicenseToSpdxListedLicense( license );
            if ( listedLicense != null )
            {
                spdxLicenses.add( listedLicense );
            }
        }
        if ( spdxLicenses.size() < 1 )
        {
            return new SpdxNoAssertionLicense();
        }
        else if ( spdxLicenses.size() == 1 )
        {
            return spdxLicenses.get( 0 );
        }
        else
        {
            AnyLicenseInfo conjunctiveLicense = spdxDoc.createConjunctiveLicenseSet( spdxLicenses );
            return conjunctiveLicense;
        }
    }

    private SpdxListedLicense mavenLicenseToSpdxListedLicense( License license )
    {
        if ( license == null )
        {
            return null;
        }
        if ( license.getUrl() == null || license.getUrl().isEmpty() )
        {
            return null;
        }
        String spdxId = this.urlStringToSpdxLicenseId.get( license.getUrl().replaceAll( "https", "http" ) );
        if ( spdxId == null )
        {
            return null;
        }
        try
        {
            return LicenseInfoFactory.getListedLicenseById( spdxId );
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            return null;
        }
    }

    /**
     * @return Map of URL's to listed license ID's
     */
    public Map<String, String> getMap()
    {
        return this.urlStringToSpdxLicenseId;
    }


}
