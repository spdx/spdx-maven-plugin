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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.model.License;
import org.spdx.core.DefaultStoreNotInitializedException;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.library.LicenseInfoFactory;
import org.spdx.library.model.v2.license.InvalidLicenseStringException;
import org.spdx.library.model.v3_0_1.core.SpdxDocument;
import org.spdx.library.model.v3_0_1.expandedlicensing.CustomLicense;
import org.spdx.library.model.v3_0_1.expandedlicensing.ListedLicense;
import org.spdx.library.model.v3_0_1.expandedlicensing.NoAssertionLicense;
import org.spdx.library.model.v3_0_1.simplelicensing.AnyLicenseInfo;
import org.spdx.maven.NonStandardLicense;
import org.spdx.storage.IModelStore.IdType;
import org.spdx.storage.listedlicense.SpdxListedLicenseModelStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the SPDX Spec Version 3 licenses for the Spdx plugin. Keeps track of any extracted licenses (added as configuration
 * parameters to the plugin).  Maps Maven licenses to SPDX licenses. Creates a Maven license from an SPDX license.
 *
 * @author Gary O'Neall
 */
public class SpdxV3LicenseManager
{
    private static final Logger LOG = LoggerFactory.getLogger( SpdxV3LicenseManager.class );

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
    Map<String, CustomLicense> extractedLicenses = new HashMap<>();

    /**
     * License manager will track any extracted SPDX licenses and map between SPDX licenses and Maven licenses.  The
     * mapping uses the license URL to uniquely identify the licenses.
     *
     * @param spdxDoc                 SPDX document to add any extracted licenses
     * @throws LicenseMapperException on errors accessing SPDX listed or local licenses
     */
    public SpdxV3LicenseManager( SpdxDocument spdxDoc ) throws LicenseMapperException
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
     * @param license license to add to extracted license map
     * @throws LicenseManagerException on errors accessing SPDX listed or local licenses
     */
    public void addExtractedLicense( NonStandardLicense license ) throws LicenseManagerException
    {
        CustomLicense spdxLicense;
        try
        {
            spdxLicense = spdxDoc.createCustomLicense( spdxDoc.getIdPrefix() + license.getLicenseId() )
                            .setLicenseText( license.getExtractedText() )
                            .setName( license.getName() )
                            .setComment( license.getComment() )
                            .build();
            for (String crossRef:license.getCrossReference()) {
                spdxLicense.getSeeAlsos().add( crossRef );
            }
            spdxDoc.getElements().add( spdxLicense );
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
                    LOG.warn( "Duplicate URL for SPDX extracted license.  Replacing {} with {} for {}", oldLicenseId, license.getLicenseId(), url );
                }
                LOG.debug( "Adding URL mapping for non-standard license {}", license.getLicenseId() );
                this.urlStringToSpdxLicenseId.put( url, license.getLicenseId() );
            }
        }
        // add to extracted license cache
        extractedLicenses.put( license.getLicenseId(), spdxLicense );
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
            if ( licenseList == null || licenseList.isEmpty() )
            {
                return new NoAssertionLicense();
            } 
            else if ( licenseList.size() == 1 )
            {
                return mavenLicenseToSpdxLicense( licenseList.get( 0 ) );
            } 
            else
            {
                Set<AnyLicenseInfo> spdxLicenses = new HashSet<>();
                for ( License license : licenseList )
                {
                    spdxLicenses.add( mavenLicenseToSpdxLicense( license ) );
                }
                return spdxDoc.createConjunctiveLicenseSet( spdxDoc.getModelStore().getNextId( IdType.Anonymous ) )
                                .addAllMember( spdxLicenses )
                                .build();
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
                retval = LicenseInfoFactory.parseSPDXLicenseString( licenseId, spdxDoc.getModelStore(),
                                                                    spdxDoc.getIdPrefix(),
                                                                    spdxDoc.getCopyManager(), null );
            }
            catch ( InvalidLicenseStringException e )
            {
                throw new LicenseManagerException(
                        "Can not map maven license " + mavenLicense.getName() + "  Invalid listed or extracted license id matching the URL " + mavenLicense.getUrl() );
            }
            catch ( DefaultStoreNotInitializedException e )
            {
                throw new LicenseManagerException( "Default model store not initialized" );
            }
        }
        return retval;
    }

    /**
     * Create a Maven license from the SPDX license
     *
     * @param spdxLicense source SPDX license to convert
     * @return a Maven license from the SPDX license
     * @throws LicenseManagerException thrown if no SPDX listed or extracted license exists with the same UR
     */
    public License spdxLicenseToMavenLicense( AnyLicenseInfo spdxLicense ) throws LicenseManagerException
    {
        if ( spdxLicense instanceof CustomLicense )
        {
            return spdxNonStdLicenseToMavenLicense( (CustomLicense) spdxLicense );
        }
        else if ( spdxLicense instanceof ListedLicense )
        {
            return spdxStdLicenseToMavenLicense( (ListedLicense) spdxLicense );
        }
        else
        {
            throw new LicenseManagerException(
                    "Can not create a Maven license from this SPDX license type.  " + "Must be an ExtractedLicenseInfo or an SpdxListedLicense " );
        }
    }

    private License spdxStdLicenseToMavenLicense( ListedLicense spdxLicense ) throws LicenseManagerException
    {
        try 
        {
            License retval = new License();
            // name
            if ( spdxLicense.getName().isPresent() && !spdxLicense.getName().get().isEmpty() )
            {
                retval.setName( spdxLicense.getName().get() );
            }
            else
            {
                retval.setName( SpdxListedLicenseModelStore.objectUriToLicenseOrExceptionId( spdxLicense.getObjectUri() ) );
            }
            // comment
            if ( spdxLicense.getComment().isPresent() && !spdxLicense.getComment().get().isEmpty() )
            {
                retval.setComments( spdxLicense.getComment().get() );
            }
            // url
            for (String url:spdxLicense.getSeeAlsos()) {
                retval.setUrl( url );
            }
            if ( spdxLicense.getSeeAlsos().size() > 1 )
            {
                LOG.warn( "SPDX license {} contains multiple URLs.  Only the first URL will be preserved in the Maven license created.", SpdxListedLicenseModelStore.objectUriToLicenseOrExceptionId( spdxLicense.getObjectUri() ) );
            }
            return retval; 
        } catch ( InvalidSPDXAnalysisException e )
        {
            throw new LicenseManagerException( "Error converting SPDX Listed License to Maven license", e );
        }
    }

    private License spdxNonStdLicenseToMavenLicense( CustomLicense spdxLicense ) throws LicenseManagerException
    {
        try
        {
            License retval = new License();
            // license ID
            int prefixLen = spdxLicense.getIdPrefix() == null ? 0 : spdxLicense.getIdPrefix().length();
            String licenseId = spdxLicense.getObjectUri().substring( prefixLen );
            // name
            if ( spdxLicense.getName().isPresent() && !spdxLicense.getName().get().isEmpty() )
            {
                retval.setName( spdxLicense.getName().get() );
            }
            else
            {
                retval.setName( licenseId );
            }
            // comment
            if ( spdxLicense.getComment().isPresent() && !spdxLicense.getComment().get().isEmpty() )
            {
                retval.setComments( spdxLicense.getComment().get() );
            }
            // url
            for (String url:spdxLicense.getSeeAlsos()) {
                retval.setUrl( url );
            }
            if ( spdxLicense.getSeeAlsos().size() > 1 )
            {
                LOG.warn( "SPDX license {} contains multiple URLs.  Only the first URL will be preserved in the Maven license created.", licenseId );
            }
            return retval;
        } 
        catch ( InvalidSPDXAnalysisException e )
        {
            throw new LicenseManagerException( "Error converting SPDX non-standard license to Maven license", e );
        }
    }
}
