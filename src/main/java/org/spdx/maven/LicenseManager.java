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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.maven.model.License;
import org.apache.maven.plugin.logging.Log;
import org.spdx.rdfparser.InvalidSPDXAnalysisException;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.ConjunctiveLicenseSet;
import org.spdx.rdfparser.license.ExtractedLicenseInfo;
import org.spdx.rdfparser.license.LicenseInfoFactory;
import org.spdx.rdfparser.license.SpdxListedLicense;
import org.spdx.rdfparser.license.SpdxNoAssertionLicense;
import org.spdx.rdfparser.model.SpdxDocument;
import org.spdx.spdxspreadsheet.InvalidLicenseStringException;

/**
 * Manages the SPDX licenses for the Spdx plugin.
 * Keeps track of any extracted licenses (added as configuration
 * parameters to the plugin).  Maps Maven licenses to SPDX licenses.
 * Creates a Maven license from an SPDX license.
 * 
 * @author Gary O'Neall
 *
 */
public class LicenseManager
{

    /**
     * SPDX document containing the license information collected.  All
     * extracted licenses are added to the SPDX document
     */
    SpdxDocument spdxDoc = null;
    
    /**
     * Maps URLs to SPDX license ID's.  The SPDX licenses could be an SPDX listed
     * license or a extracted license.
     */
    Map<String, String> urlStringToSpdxLicenseId = new HashMap<String, String>();
    
    /**
     * Map of extracted license ID's to the SPDX license
     */
    Map<String, ExtractedLicenseInfo> extractedLicenses = new HashMap<String, ExtractedLicenseInfo>();

    Log log;

    /**
     * License manager will track any extracted SPDX licenses and map
     * between SPDX licenses and Maven licenses.  The mapping uses the
     * license URL to uniquely identify the licenses.
     * @param spdxDoc SPDX document to add any extracted licenses
     * @param useStdLicenseSourceUrls if true, map any SPDX listed license source URL to license ID.  Note: significant performance degredation
     * @param Log plugin logger
     * @throws LicenseMapperException 
     */
    public LicenseManager( SpdxDocument spdxDoc, Log log, boolean useStdLicenseSourceUrls ) throws LicenseMapperException
    {
        this.spdxDoc = spdxDoc;
        this.log = log;
        initializeUrlMap();
    }
    
    /**
     * Initialize the URL map from the SPDX listed licenses
     * @throws LicenseMapperException 
     */
    private void initializeUrlMap() throws LicenseMapperException
    {
        this.urlStringToSpdxLicenseId.putAll( MavenToSpdxLicenseMapper.getInstance( log ).getMap() );
    }

    protected Log getLog() {
        return this.log;
    }



    /**
     * Add a non-listed license to the SPDX document.  Once added, the non-listed
     * license can be referenced by the license ID
     * @param license
     * @throws LicenseManagerException
     */
    public void addExtractedLicense( NonStandardLicense license ) throws LicenseManagerException
    {
         ExtractedLicenseInfo spdxLicense = new ExtractedLicenseInfo(
                license.getLicenseId(), license.getExtractedText(), license.getName(),
                license.getCrossReference(), license.getComment() );
        try {
            spdxDoc.addExtractedLicenseInfos( spdxLicense );
        } catch ( InvalidSPDXAnalysisException e ) {
            String licenseId = license.getLicenseId();
            if ( licenseId == null ) {
                licenseId = "[NullLicenseId]";
            }
            throw( new LicenseManagerException( "Unable to add non listed license "+licenseId+": "+e.getMessage(),e ) );
        }
        // add to URL mapping
        String[] urls = license.getCrossReference();
        if ( urls != null ) {
            for ( int i = 0; i < urls.length; i++ ) {
                if (this.urlStringToSpdxLicenseId.containsKey( urls[i] )) {
                    String oldLicenseId = urlStringToSpdxLicenseId.get( urls[i] );
                    getLog().warn( "Duplicate URL for SPDX extracted license.  Replacing " +
                                        oldLicenseId + " with " + license.getLicenseId() +
                                        " for " + urls[i]);
                }
                if (getLog() != null) {
                    getLog().debug( "Adding URL mapping for non-standard license "+spdxLicense.getLicenseId() );
                }
                this.urlStringToSpdxLicenseId.put( urls[i], spdxLicense.getLicenseId() );
            }
        }
        // add to extracted license cache
        extractedLicenses.put( spdxLicense.getLicenseId(), spdxLicense );
    }
    
    /**
     * Map a list of Maven licenses to an SPDX license.  If no licenses
     * are supplied, SpdxNoAssertion license is returned.  if a single
     * license is supplied, the mapped SPDX license is returned.  If
     * multiple licenses are supplied, a conjunctive license is returned
     * containing all mapped SPDX licenses.
     * @return
     * @throws LicenseManagerException 
     */
    public AnyLicenseInfo mavenLicenseListToSpdxLicense( List<License> licenseList ) throws LicenseManagerException {
        if ( licenseList == null ) {
            return new SpdxNoAssertionLicense();
        }
        List<AnyLicenseInfo> spdxLicenses = new ArrayList<AnyLicenseInfo>();
        Iterator<License> iter = licenseList.iterator();
        while( iter.hasNext() ) {
            License license = iter.next();
            spdxLicenses.add( mavenLicenseToSpdxLicense( license ) );
        }
        if ( spdxLicenses.size() < 1) {
            return new SpdxNoAssertionLicense();
        } else if ( spdxLicenses.size() == 1 ) {
            return spdxLicenses.get( 0 );
        } else {
            AnyLicenseInfo[] licensesInSet = spdxLicenses.toArray( new AnyLicenseInfo[spdxLicenses.size()] );
            AnyLicenseInfo conjunctiveLicense = new ConjunctiveLicenseSet( licensesInSet );
            return conjunctiveLicense;
        }
    }

    /**
     * Map a Maven license to an SPDX license based on the URL
     * @param mavenLicense license to map to a listed SPDX license
     * @return SPDX license
     * @throws LicenseManagerException thrown if no SPDX listed or extracted license exists with the same URL
     */
    public AnyLicenseInfo mavenLicenseToSpdxLicense( License mavenLicense ) throws LicenseManagerException
    {
        if ( mavenLicense.getUrl() == null ) {
            throw( new LicenseManagerException( "Can not map maven license " + mavenLicense.getName() +
                                                "  No URL exists to provide a mapping" ) );
        }
        String licenseId = this.urlStringToSpdxLicenseId.get( mavenLicense.getUrl() );
        if ( licenseId == null ) {
            throw( new LicenseManagerException( "Can not map maven license " + mavenLicense.getName() +
                                                "  No listed or extracted license matches the URL " + mavenLicense.getUrl() ) );
        }
        AnyLicenseInfo retval = extractedLicenses.get( licenseId );
        if (retval == null) {
            try
            {
                retval = LicenseInfoFactory.parseSPDXLicenseString( licenseId );
            }
            catch ( InvalidLicenseStringException e )
            {
                throw( new LicenseManagerException( "Can not map maven license " + mavenLicense.getName() +
                                                    "  Invalid listed or extracted license id matching the URL " + mavenLicense.getUrl() ) );
            }
        }
        return retval;
    }
    
    /**
     * Create a Maven license from the SPDX license
     * @param spdxLicense
     * @return
     * @throws LicenseManagerException 
     */
    public License spdxLicenseToMavenLicense( AnyLicenseInfo spdxLicense ) throws LicenseManagerException {
        if ( spdxLicense instanceof ExtractedLicenseInfo ) {
            return spdxNonStdLicenseToMavenLicense( (ExtractedLicenseInfo)spdxLicense );
        } else if ( spdxLicense instanceof SpdxListedLicense ) {
            return spdxStdLicenseToMavenLicense( (SpdxListedLicense)spdxLicense );
        } else {
            throw( new LicenseManagerException( "Can not create a Maven license from this SPDX license type.  " +
                            "Must be an ExtractedLicenseInfo or an SpdxListedLicense "));
        }
    }

    private License spdxStdLicenseToMavenLicense( SpdxListedLicense spdxLicense )
    {
        License retval = new License();
        // name
        if ( spdxLicense.getName() != null && !spdxLicense.getName().isEmpty() ) {
            retval.setName( spdxLicense.getName() );
        } else {
            retval.setName( spdxLicense.getLicenseId() );
        }
        // comment
        if ( spdxLicense.getComment() != null && !spdxLicense.getComment().isEmpty() ) {
            retval.setComments( spdxLicense.getComment() );
        }
        // url
        if ( spdxLicense.getSeeAlso() != null && spdxLicense.getSeeAlso().length > 0 ) {
            retval.setUrl( spdxLicense.getSeeAlso()[0] );
            if ( spdxLicense.getSeeAlso().length > 1 ) {
                if ( getLog() != null ) {
                    getLog().warn( "SPDX license "+spdxLicense.getLicenseId() +
                                    " contains multiple URLs.  Only the first URL will be preserved in the Maven license created." );
                }
            }
        }
        return retval;
    }

    private License spdxNonStdLicenseToMavenLicense( ExtractedLicenseInfo spdxLicense )
    {
        License retval = new License();
        // name
        if ( spdxLicense.getName() != null && !spdxLicense.getName().isEmpty() ) {
            retval.setName( spdxLicense.getName() );
        } else {
            retval.setName( spdxLicense.getLicenseId() );
        }
        // comment
        if ( spdxLicense.getComment() != null && !spdxLicense.getComment().isEmpty() ) {
            retval.setComments( spdxLicense.getComment() );
        }
        // url
        if ( spdxLicense.getSeeAlso() != null && spdxLicense.getSeeAlso().length > 0 ) {
            retval.setUrl( spdxLicense.getSeeAlso()[0] );
            if ( spdxLicense.getSeeAlso().length > 1 ) {
                getLog().warn( "SPDX license "+spdxLicense.getLicenseId() +
                               " contains multiple URLs.  Only the first URL will be preserved in the Maven license created." );
            }
        }
        return retval;
    }

}
