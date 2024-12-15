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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.model.License;
import org.spdx.core.DefaultStoreNotInitialized;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.library.LicenseInfoFactory;
import org.spdx.library.model.v2.SpdxDocument;
import org.spdx.library.model.v2.license.AnyLicenseInfo;
import org.spdx.library.model.v2.license.ExtractedLicenseInfo;
import org.spdx.library.model.v2.license.InvalidLicenseStringException;
import org.spdx.library.model.v2.license.SpdxListedLicense;
import org.spdx.library.model.v2.license.SpdxNoAssertionLicense;
import org.spdx.maven.NonStandardLicense;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the SPDX Spec version 2licenses for the Spdx plugin. Keeps track of any extracted licenses (added as configuration
 * parameters to the plugin).  Maps Maven licenses to SPDX licenses. Creates a Maven license from an SPDX license.
 *
 * @author Gary O'Neall
 */
public class SpdxV2LicenseManager
{
    private static final Logger LOG = LoggerFactory.getLogger( SpdxV2LicenseManager.class );

    /**
     * SPDX document containing the license information collected.  All extracted licenses are added to the SPDX
     * document
     */
    SpdxDocument spdxDoc;

    /**
     * Maps URLs to SPDX license ID's.  The SPDX licenses could be an SPDX listed license or an extracted license.
     */
    Map<String, String> urlStringToSpdxLicenseId = new HashMap<>();

    /**
     * Map of extracted license ID's to the SPDX license
     */
    Map<String, ExtractedLicenseInfo> extractedLicenses = new HashMap<>();

    /**
     * License manager will track any extracted SPDX licenses and map between SPDX licenses and Maven licenses.  The
     * mapping uses the license URL to uniquely identify the licenses.
     *
     * @param spdxDoc                 SPDX document to add any extracted licenses
     * @throws LicenseMapperException on errors accessing SPDX listed or local licenses
     */
    public SpdxV2LicenseManager( SpdxDocument spdxDoc ) throws LicenseMapperException
    {
        this.spdxDoc = spdxDoc;
        initializeUrlMap();
    }

    /**
     * Initialize the URL map from the SPDX listed licenses
     *
     * @throws LicenseMapperException on errors accessing SPDX listed or local licenses
     */
    private void initializeUrlMap() throws LicenseMapperException
    {
        this.urlStringToSpdxLicenseId.putAll( MavenToSpdxLicenseMapper.getInstance().getMap() );
    }

    /**
     * Add a non-listed license to the SPDX document.  Once added, the non-listed license can be referenced by the
     * license ID
     *
     * @param license extracted license to add
     * @throws LicenseManagerException on errors accessing SPDX listed or local licenses
     */
    public void addExtractedLicense( NonStandardLicense license ) throws LicenseManagerException
    {
        ExtractedLicenseInfo spdxLicense;
        try
        {
            spdxLicense = new ExtractedLicenseInfo( spdxDoc.getModelStore(),
                                                     spdxDoc.getDocumentUri(),
                                                     license.getLicenseId(),
                                                     spdxDoc.getCopyManager(),
                                                     true );
            spdxLicense.setExtractedText( license.getExtractedText() );
            spdxLicense.setName( license.getName() );
            for (String crossRef:license.getCrossReference()) {
                spdxLicense.getSeeAlso().add( crossRef );
            }
            spdxLicense.setComment( license.getComment() );
            spdxDoc.addExtractedLicenseInfos( spdxLicense );
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            String licenseId = license.getLicenseId();
            if ( licenseId == null )
            {
                licenseId = "[NullLicenseId]";
            }
            throw new LicenseManagerException( "Unable to add non listed license " + licenseId, e );
        }
        // add to URL mapping
        String[] urls = license.getCrossReference();
        if ( urls != null )
        {
            for ( String url : urls )
            {
                if ( this.urlStringToSpdxLicenseId.containsKey( url ) )
                {
                    String oldLicenseId = urlStringToSpdxLicenseId.get( url );
                    LOG.warn("Duplicate URL for SPDX extracted license.  Replacing {} with {} for {}", oldLicenseId, license.getLicenseId(), url);
                }
                LOG.debug("Adding URL mapping for non-standard license {}", spdxLicense.getLicenseId());
                this.urlStringToSpdxLicenseId.put( url, spdxLicense.getLicenseId() );
            }
        }
        // add to extracted license cache
        extractedLicenses.put( spdxLicense.getLicenseId(), spdxLicense );
    }

    /**
     * Map a list of Maven licenses to an SPDX license.  If no licenses are supplied, SpdxNoAssertion license is
     * returned.  if a single license is supplied, the mapped SPDX license is returned.  If multiple licenses are
     * supplied, a conjunctive license is returned containing all mapped SPDX licenses.
     *
     * @return If no licenses are supplied, SpdxNoAssertion license is
     *         returned.  if a single license is supplied, the mapped SPDX license is returned.
     *         If multiple licenses are supplied, a conjunctive license is returned containing
     *         all mapped SPDX licenses.
     * @throws LicenseManagerException on errors accessing SPDX listed or local licenses
     */
    public AnyLicenseInfo mavenLicenseListToSpdxLicense( List<License> licenseList ) throws LicenseManagerException
    {
        try {
            if ( licenseList == null )
            {
                return new SpdxNoAssertionLicense();
            }
            List<AnyLicenseInfo> spdxLicenses = new ArrayList<>();
            for ( License license : licenseList )
            {
                spdxLicenses.add( mavenLicenseToSpdxLicense( license ) );
            }
            if (spdxLicenses.isEmpty())
            {
                return new SpdxNoAssertionLicense();
            }
            else if ( spdxLicenses.size() == 1 )
            {
                return spdxLicenses.get( 0 );
            }
            else
            {
                return spdxDoc.createConjunctiveLicenseSet( spdxLicenses );
            }
        } catch ( InvalidSPDXAnalysisException e )
        {
            throw new LicenseManagerException( "Error converting Maven license to SPDX license", e );
        }
    }

    /**
     * Map a Maven license to an SPDX license based on the URL
     *
     * @param mavenLicense license to map to a listed SPDX license
     * @return SPDX license
     * @throws LicenseManagerException thrown if no SPDX listed or extracted license exists with the same URL
     */
    public AnyLicenseInfo mavenLicenseToSpdxLicense( License mavenLicense ) throws LicenseManagerException
    {
        if ( mavenLicense.getUrl() == null )
        {
            throw new LicenseManagerException(
                    "Can not map maven license " + mavenLicense.getName() + "  No URL exists to provide a mapping" );
        }
        String licenseId = this.urlStringToSpdxLicenseId.get( mavenLicense.getUrl().replaceAll("https:", "http:") );
        if ( licenseId == null )
        {
            throw new LicenseManagerException(
                    "Can not map maven license " + mavenLicense.getName() + "  No listed or extracted license matches the URL " + mavenLicense.getUrl() );
        }
        AnyLicenseInfo retval = extractedLicenses.get( licenseId );
        if ( retval == null )
        {
            try
            {
                retval = LicenseInfoFactory.parseSPDXLicenseStringCompatV2( licenseId, spdxDoc.getModelStore(),
                                                                            spdxDoc.getDocumentUri(),
                                                                            spdxDoc.getCopyManager() );
            }
            catch ( InvalidLicenseStringException e )
            {
                throw new LicenseManagerException(
                        "Can not map maven license " + mavenLicense.getName() + "  Invalid listed or extracted license id matching the URL " + mavenLicense.getUrl() );
            }
            catch ( DefaultStoreNotInitialized e )
            {
                throw new LicenseManagerException( "Default model store not initialized" );
            }
        }
        return retval;
    }

    /**
     * Create a Maven license from the SPDX license
     *
     * @param spdxLicense SPDX license to convert
     * @return a Maven license from the SPDX license
     * @throws LicenseManagerException on errors accessing SPDX listed or local licenses
     */
    public License spdxLicenseToMavenLicense( AnyLicenseInfo spdxLicense ) throws LicenseManagerException
    {
        if ( spdxLicense instanceof ExtractedLicenseInfo )
        {
            return spdxNonStdLicenseToMavenLicense( (ExtractedLicenseInfo) spdxLicense );
        }
        else if ( spdxLicense instanceof SpdxListedLicense )
        {
            return spdxStdLicenseToMavenLicense( (SpdxListedLicense) spdxLicense );
        }
        else
        {
            throw new LicenseManagerException(
                    "Can not create a Maven license from this SPDX license type.  " + "Must be an ExtractedLicenseInfo or an SpdxListedLicense " );
        }
    }

    private License spdxStdLicenseToMavenLicense( SpdxListedLicense spdxLicense ) throws LicenseManagerException
    {
        try 
        {
            License retval = new License();
            // name
            if ( spdxLicense.getName() != null && !spdxLicense.getName().isEmpty() )
            {
                retval.setName( spdxLicense.getName() );
            }
            else
            {
                retval.setName( spdxLicense.getLicenseId() );
            }
            // comment
            if ( spdxLicense.getComment() != null && !spdxLicense.getComment().isEmpty() )
            {
                retval.setComments( spdxLicense.getComment() );
            }
            // url
            for (String url:spdxLicense.getSeeAlso()) {
                retval.setUrl( url );
            }
            if ( spdxLicense.getSeeAlso().size() > 1 )
            {
                //noinspection LoggingSimilarMessage
                LOG.warn("SPDX license {} contains multiple URLs.  Only the first URL will be preserved in the Maven license created.", spdxLicense.getLicenseId());
            }
            return retval; 
        } catch ( InvalidSPDXAnalysisException e )
        {
            throw new LicenseManagerException( "Error converting SPDX Listed License to Maven license", e );
        }
    }

    private License spdxNonStdLicenseToMavenLicense( ExtractedLicenseInfo spdxLicense ) throws LicenseManagerException
    {
        try
        {
            License retval = new License();
            // name
            if ( spdxLicense.getName() != null && !spdxLicense.getName().isEmpty() )
            {
                retval.setName( spdxLicense.getName() );
            }
            else
            {
                retval.setName( spdxLicense.getLicenseId() );
            }
            // comment
            if ( spdxLicense.getComment() != null && !spdxLicense.getComment().isEmpty() )
            {
                retval.setComments( spdxLicense.getComment() );
            }
            // url
            for (String url:spdxLicense.getSeeAlso()) {
                retval.setUrl( url );
            }
            if ( spdxLicense.getSeeAlso().size() > 1 )
            {
                //noinspection LoggingSimilarMessage
                LOG.warn("SPDX license {} contains multiple URLs.  Only the first URL will be preserved in the Maven license created.", spdxLicense.getLicenseId());
            }
            return retval;
        } 
        catch ( InvalidSPDXAnalysisException e )
        {
            throw new LicenseManagerException( "Error converting SPDX non-standard license to Maven license", e );
        }
    }
}
