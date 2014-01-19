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

import org.apache.maven.model.License;
import org.apache.maven.plugin.logging.Log;
import org.spdx.rdfparser.InvalidSPDXAnalysisException;
import org.spdx.rdfparser.SPDXConjunctiveLicenseSet;
import org.spdx.rdfparser.SPDXDocument;
import org.spdx.rdfparser.SPDXLicense;
import org.spdx.rdfparser.SPDXLicenseInfo;
import org.spdx.rdfparser.SPDXLicenseInfoFactory;
import org.spdx.rdfparser.SPDXNonStandardLicense;
import org.spdx.rdfparser.SPDXStandardLicense;
import org.spdx.rdfparser.SpdxNoAssertionLicense;

/**
 * Manages the SPDX licenses for the Spdx plugin.
 * Keeps track of any non standard licenses (added as configuration
 * parameters to the plugin).  Maps Maven licenses to SPDX licenses.
 * Creates a Maven license from an SPDX license.
 * @author Gary O'Neall
 *
 */
public class LicenseManager
{
    /**
     * SPDX document containing the license information collected.  All
     * non-standard licenses are added to the SPDX document
     */
    SPDXDocument spdxDoc = null;
    
    /**
     * Maps URLs to SPDX licenses.  The SPDX licenses could be an SPDX standard
     * license or a non-standard license.
     */
    HashMap<String, SPDXLicenseInfo> urlStringToSpdxLicense = null;

    Log log;

    /**
     * License manager will track any non-standard SPDX licenses and map
     * between SPDX licenses and Maven licenses.  The mapping uses the
     * license URL to uniquely identify the licenses.
     * @param spdxDoc SPDX document to add any non-standard licenses
     * @param Log plugin logger
     */
    public LicenseManager( SPDXDocument spdxDoc, Log log )
    {
        this.spdxDoc = spdxDoc;
        this.log = log;
        initializeUrlMap();
    }
    
    protected Log getLog() {
        return this.log;
    }

    /**
     * Initialize the urlSTringToSpdxLicense map with the SPDX standard licenses
     */
    private void initializeUrlMap()
    {
        urlStringToSpdxLicense = new HashMap<String, SPDXLicenseInfo>();
        String[] standardLicenseIds = SPDXLicenseInfoFactory.getStandardLicenseIds();
        for ( int i = 0; i < standardLicenseIds.length; i++ ) {
            try
            {
                SPDXStandardLicense stdLicense = 
                                SPDXLicenseInfoFactory.getStandardLicenseById( standardLicenseIds[i] );
                String[] urls = stdLicense.getSourceUrl();
                if ( urls != null ) {
                    for ( int j = 0; j < urls.length; j++ ) {
                        if (this.urlStringToSpdxLicense.containsKey( urls[j] )) {
                            String oldLicenseId = ((SPDXLicense)(this.urlStringToSpdxLicense.get( urls[j] ))).getId();
                            getLog().warn( "Duplicate URL for SPDX standard license.  Replacing " +
                                                oldLicenseId + " with " + standardLicenseIds[i] +
                                                " for " + urls[j]);
                        }
                        this.urlStringToSpdxLicense.put( urls[j], stdLicense );
                    }
                }
            }
            catch ( InvalidSPDXAnalysisException e )
            {
                getLog().warn( "Unable to get standard license ID for mapping.  LicenseID="+standardLicenseIds[i], e);               
            }
        }
    }

    /**
     * Add a non-standard license to the SPDX document.  Once added, the non-standard
     * license can be referenced by the license ID
     * @param license
     * @throws LicenseManagerException
     */
    public void addNonStandardLicense( NonStandardLicense license ) throws LicenseManagerException
    {
         SPDXNonStandardLicense spdxLicense = new SPDXNonStandardLicense(
                license.getLicenseId(), license.getExtractedText(), license.getName(),
                license.getCrossReference(), license.getComment() );
        try {
            spdxDoc.addNewExtractedLicenseInfo( spdxLicense );
        } catch ( InvalidSPDXAnalysisException e ) {
            String licenseId = license.getLicenseId();
            if ( licenseId == null ) {
                licenseId = "[NullLicenseId]";
            }
            throw( new LicenseManagerException( "Unable to add non standard license "+licenseId+": "+e.getMessage(),e ) );
        }
        // add to URL mapping
        String[] urls = license.getCrossReference();
        if ( urls != null ) {
            for ( int i = 0; i < urls.length; i++ ) {
                if (this.urlStringToSpdxLicense.containsKey( urls[i] )) {
                    String oldLicenseId = ((SPDXLicense)(this.urlStringToSpdxLicense.get( urls[i] ))).getId();
                    getLog().warn( "Duplicate URL for SPDX non standard license.  Replacing " +
                                        oldLicenseId + " with " + license.getLicenseId() +
                                        " for " + urls[i]);
                }
                this.urlStringToSpdxLicense.put( urls[i], spdxLicense );
            }
        }
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
    public SPDXLicenseInfo mavenLicenseListToSpdxLicense( List<License> licenseList ) throws LicenseManagerException {
        if ( licenseList == null ) {
            return new SpdxNoAssertionLicense();
        }
        ArrayList<SPDXLicenseInfo> spdxLicenses = new ArrayList<SPDXLicenseInfo>();
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
            SPDXLicenseInfo[] licensesInSet = spdxLicenses.toArray( new SPDXLicenseInfo[spdxLicenses.size()] );
            SPDXLicenseInfo conjunctiveLicense = new SPDXConjunctiveLicenseSet( licensesInSet );
            return conjunctiveLicense;
        }
    }

    /**
     * Map a Maven license to an SPDX license based on the URL
     * @param mavenLicense license to map to a standard SPDX license
     * @return SPDX license
     * @throws LicenseManagerException thrown if no SPDX standard or non standard license exists with the same URL
     */
    public SPDXLicenseInfo mavenLicenseToSpdxLicense( License mavenLicense ) throws LicenseManagerException
    {
        if ( mavenLicense.getUrl() == null ) {
            throw( new LicenseManagerException( "Can not map maven license " + mavenLicense.getName() +
                                                "No URL exists to provide a mapping" ) );
        }
        SPDXLicenseInfo retval = this.urlStringToSpdxLicense.get( mavenLicense.getUrl() );
        if ( retval == null ) {
            throw( new LicenseManagerException( "Can not map maven license " + mavenLicense.getName() +
                                                "No standard or non-standard license matches the URL " + mavenLicense.getUrl() ) );
        }
        return retval;
    }
    
    /**
     * Create a Maven license from the SPDX license
     * @param spdxLicense
     * @return
     * @throws LicenseManagerException 
     */
    public License spdxLicenseToMavenLicense( SPDXLicenseInfo spdxLicense ) throws LicenseManagerException {
        if ( spdxLicense instanceof SPDXNonStandardLicense ) {
            return spdxNonStdLicenseToMavenLicense( (SPDXNonStandardLicense)spdxLicense );
        } else if ( spdxLicense instanceof SPDXStandardLicense ) {
            return spdxStdLicenseToMavenLicense( (SPDXStandardLicense)spdxLicense );
        } else {
            throw( new LicenseManagerException( "Can not create a Maven license from this SPDX license type.  " +
                            "Must be an SPDXNonStandardLicense or an SPDXStandardLicense "));
        }
    }

    private License spdxStdLicenseToMavenLicense( SPDXStandardLicense spdxLicense )
    {
        License retval = new License();
        // name
        if ( spdxLicense.getName() != null && !spdxLicense.getName().isEmpty() ) {
            retval.setName( spdxLicense.getName() );
        } else {
            retval.setName( spdxLicense.getId() );
        }
        // comment
        if ( spdxLicense.getComment() != null && !spdxLicense.getComment().isEmpty() ) {
            retval.setComments( spdxLicense.getComment() );
        }
        // url
        if ( spdxLicense.getSourceUrl() != null && spdxLicense.getSourceUrl().length > 0 ) {
            retval.setUrl( spdxLicense.getSourceUrl()[0] );
            if ( spdxLicense.getSourceUrl().length > 1 ) {
                getLog().warn( "SPDX license "+spdxLicense.getId() +
                               " contains multiple URLs.  Only the first URL will be preserved in the Maven license created." );
            }
        }
        return retval;
    }

    private License spdxNonStdLicenseToMavenLicense( SPDXNonStandardLicense spdxLicense )
    {
        License retval = new License();
        // name
        if ( spdxLicense.getLicenseName() != null && !spdxLicense.getLicenseName().isEmpty() ) {
            retval.setName( spdxLicense.getLicenseName() );
        } else {
            retval.setName( spdxLicense.getId() );
        }
        // comment
        if ( spdxLicense.getComment() != null && !spdxLicense.getComment().isEmpty() ) {
            retval.setComments( spdxLicense.getComment() );
        }
        // url
        if ( spdxLicense.getSourceUrls() != null && spdxLicense.getSourceUrls().length > 0 ) {
            retval.setUrl( spdxLicense.getSourceUrls()[0] );
            if ( spdxLicense.getSourceUrls().length > 1 ) {
                getLog().warn( "SPDX license "+spdxLicense.getId() +
                               " contains multiple URLs.  Only the first URL will be preserved in the Maven license created." );
            }
        }
        return retval;
    }

}
