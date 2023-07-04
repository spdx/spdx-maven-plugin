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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;

import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.model.Checksum;
import org.spdx.library.model.ExternalRef;
import org.spdx.library.model.SpdxDocument;
import org.spdx.library.model.enumerations.Purpose;
import org.spdx.library.model.license.AnyLicenseInfo;
import org.spdx.library.model.license.SpdxNoAssertionLicense;
import org.spdx.library.referencetype.ListedReferenceTypes;

import org.spdx.maven.Annotation;
import org.spdx.maven.ExternalReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple structure to hold information about SPDX project
 *
 * @author Gary O'Neall
 */
public class SpdxProjectInformation
{
    private static final Logger LOG = LoggerFactory.getLogger( SpdxProjectInformation.class );

    private String[] creators = new String[0];
    private String creatorComment = "";
    private AnyLicenseInfo concludedLicense;
    private AnyLicenseInfo declaredLicense;
    private String description;
    private String downloadUrl;
    private String homePage;
    private String shortDescription;
    private String originator;
    private String supplier;
    private String packageArchiveFileName;
    private String versionInfo;
    private String licenseComment;
    private String name;
    private String sourceInfo;
    private String copyrightText;
    private String documentComment;
    private Annotation[] packageAnnotations;
    private Annotation[] documentAnnotations;
    private List<ExternalReference> externalRefs;
    private Set<Checksum> checksums;
    private Purpose primaryPurpose;
    
    /**
     * @return the primaryPurpose
     */
    public Purpose getPrimaryPurpose()
    {
        return primaryPurpose;
    }

    /**
     * @param primaryPurpose the primaryPurpose to set
     */
    public void setPrimaryPurpose( Purpose primaryPurpose )
    {
        this.primaryPurpose = primaryPurpose;
    }

    public SpdxProjectInformation () {
        try
        {
            this.concludedLicense = new SpdxNoAssertionLicense();
            this.declaredLicense = new SpdxNoAssertionLicense();
        }
        catch ( InvalidSPDXAnalysisException isae )
        {
            // ignore: cannot happen, why does SpdxNoAssertionLicense() constructor throw this exception?
        }
    }

    /**
     * @return the documentComment
     */
    public String getDocumentComment()
    {
        return documentComment;
    }

    /**
     * @param documentComment the documentComment to set
     */
    public void setDocumentComment( String documentComment )
    {
        this.documentComment = documentComment;
    }

    /**
     * @return checksums for the project
     */
    public Set<Checksum> getChecksums()
    {
        return checksums;
    }

    /**
     * @param checksums the checksums to set for the project
     */
    public void setChecksums( Set<Checksum> checksums )
    {
        this.checksums = checksums;
    }

    /**
     * @return the concludedLicense
     */
    public AnyLicenseInfo getConcludedLicense()
    {
        return concludedLicense;
    }

    /**
     * @param concludedLicense the concludedLicense to set
     */
    public void setConcludedLicense( AnyLicenseInfo concludedLicense )
    {
        this.concludedLicense = concludedLicense;
    }

    /**
     * @return the declaredLicense
     */
    public AnyLicenseInfo getDeclaredLicense()
    {
        return declaredLicense;
    }

    /**
     * @param declaredLicense the declaredLicense to set
     */
    public void setDeclaredLicense( AnyLicenseInfo declaredLicense )
    {
        this.declaredLicense = declaredLicense;
    }

    /**
     * @return the description
     */
    public String getDescription()
    {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription( String description )
    {
        this.description = description;
    }

    /**
     * @return the downloadUrl
     */
    public String getDownloadUrl()
    {
        return downloadUrl;
    }

    /**
     * @param downloadUrl the downloadUrl to set
     */
    public void setDownloadUrl( String downloadUrl )
    {
        this.downloadUrl = downloadUrl;
    }

    /**
     * @return the homePage
     */
    public String getHomePage()
    {
        return homePage;
    }

    /**
     * @param homePage the homePage to set
     */
    public void setHomePage( String homePage )
    {
        this.homePage = homePage;
    }

    /**
     * @return the shortDescription
     */
    public String getShortDescription()
    {
        return shortDescription;
    }

    /**
     * @param shortDescription the shortDescription to set
     */
    public void setShortDescription( String shortDescription )
    {
        this.shortDescription = shortDescription;
    }

    /**
     * @return the originator
     */
    public String getOriginator()
    {
        return originator;
    }

    /**
     * @param originator the originator to set
     */
    public void setOriginator( String originator )
    {
        this.originator = originator;
    }

    /**
     * @return the supplier
     */
    public String getSupplier()
    {
        return supplier;
    }

    /**
     * @param supplier the supplier to set
     */
    public void setSupplier( String supplier )
    {
        this.supplier = supplier;
    }

    /**
     * @return the packageArchiveFileName
     */
    public String getPackageArchiveFileName()
    {
        return packageArchiveFileName;
    }

    /**
     * @param packageArchiveFileName the packageArchiveFileName to set
     */
    public void setPackageArchiveFileName( String packageArchiveFileName )
    {
        this.packageArchiveFileName = packageArchiveFileName;
    }

    /**
     * @return the versionInfo
     */
    public String getVersionInfo()
    {
        return versionInfo;
    }

    /**
     * @param versionInfo the versionInfo to set
     */
    public void setVersionInfo( String versionInfo )
    {
        this.versionInfo = versionInfo;
    }

    /**
     * @return the licenseComment
     */
    public String getLicenseComment()
    {
        return licenseComment;
    }

    /**
     * @param licenseComment the licenseComment to set
     */
    public void setLicenseComment( String licenseComment )
    {
        this.licenseComment = licenseComment;
    }

    /**
     * @return the creators
     */
    public String[] getCreators()
    {
        return creators;
    }

    /**
     * @param creators the creators to set
     */
    public void setCreators( String[] creators )
    {
        this.creators = creators;
    }

    /**
     * @return the creatorComment
     */
    public String getCreatorComment()
    {
        return creatorComment;
    }

    /**
     * @param creatorComment the creatorComment to set
     */
    public void setCreatorComment( String creatorComment )
    {
        this.creatorComment = creatorComment;
    }

    public String getName()
    {
        return name;
    }

    public void setName( String name )
    {
        this.name = name;
    }

    /**
     * Log information on all fields - typically used for debugging
     */
    public void logInfo( SpdxDocument spdxDoc )
    {
        if ( !LOG.isDebugEnabled() ) {
            return;
        }
        LOG.debug( "SPDX Project Name: " + this.getName() );
        LOG.debug( "SPDX Document comment: " + this.getDocumentComment() );
        LOG.debug( "SPDX Creator comment: " + this.getCreatorComment() );
        LOG.debug( "SPDX Description: " + this.getDescription() );
        LOG.debug( "SPDX License comment: " + this.getLicenseComment() );
        LOG.debug( "SPDX Originator: " + this.getOriginator() );
        LOG.debug( "SPDX PackageArchiveFileName: " + this.getPackageArchiveFileName() );
        LOG.debug( "SPDX Short description: " + this.getShortDescription() );
        LOG.debug( "SPDX Supplier: " + this.getSupplier() );
        LOG.debug( "SPDX Source Info:  " + this.getSourceInfo() );
        LOG.debug( "SPDX Version info: " + this.getVersionInfo() );
        LOG.debug( "SPDX Concluded license: " + this.getConcludedLicense().toString() );
        LOG.debug( "SPDX Declared license: " + this.getDeclaredLicense().toString() );
        LOG.debug( "SPDX Download URL: " + this.getDownloadUrl() );
        LOG.debug( "SPDX Home page: " + this.getHomePage() );
        if ( this.documentAnnotations != null && this.documentAnnotations.length > 0 )
        {
            LOG.debug( "Document annotations: " );
            for ( Annotation annotation : documentAnnotations )
            {
                annotation.logInfo();
            }
        }
        if ( this.packageAnnotations != null && this.packageAnnotations.length > 0 )
        {
            LOG.debug( "Package annotations: " );
            for ( Annotation annotation : packageAnnotations )
            {
                annotation.logInfo();
            }
        }
        if ( creators != null )
        {
            for ( String creator : creators )
            {
                LOG.debug( "SPDX Creator: " + creator );
            }
        }
        if ( this.externalRefs != null )
        {
            for ( ExternalReference externalReference : externalRefs )
            {
                ExternalRef externalRef;
                try
                {
                    externalRef = externalReference.getExternalRef( spdxDoc );
                    StringBuilder externalRefString = new StringBuilder();
                    try
                    {
                        externalRefString.append( externalRef.getReferenceCategory().toString() );
                    }
                    catch ( InvalidSPDXAnalysisException e1 )
                    {
                        externalRefString.append( "Invalid Reference Category" );
                    }
                    externalRefString.append( ' ' );
                    try
                    {
                        externalRefString.append( ListedReferenceTypes.getListedReferenceTypes().getListedReferenceName(
                                new URI( externalRef.getReferenceType().getIndividualURI() ) ) );
                    }
                    catch ( InvalidSPDXAnalysisException | URISyntaxException e )
                    {
                        externalRefString.append( "Invalid Reference Type" );
                    }
                    externalRefString.append( ' ' );
                    try
                    {
                        externalRefString.append( externalRef.getReferenceLocator() );
                    }
                    catch ( InvalidSPDXAnalysisException e )
                    {
                        externalRefString.append( "Invalid Reference Locator" );
                    }
                    LOG.debug( "External Ref: " + externalRefString.toString() );
                }
                catch ( MojoExecutionException e1 )
                {
                    LOG.error( "Invalid external reference", e1 );
                }

            }
        }
        if ( checksums != null && checksums.size() > 0 )
        {
            for ( Checksum checksum : checksums )
            {
                try 
                {
                    String algorithm = SpdxFileCollector.checksumAlgorithms.get( checksum.getAlgorithm() );
                    LOG.debug( "SPDX " +  algorithm + ": " + checksum.getValue() );
                } catch ( InvalidSPDXAnalysisException e )
                {
                    LOG.debug( "Invalid SPDX checksum" );
                }
            }
        }
    }

    public String getSourceInfo()
    {
        return this.sourceInfo;
    }

    public void setSourceInfo( String sourceInformation )
    {
        this.sourceInfo = sourceInformation;
    }

    public void setCopyrightText( String copyrightText )
    {
        this.copyrightText = copyrightText;
    }

    public String getCopyrightText()
    {
        return this.copyrightText;
    }

    public void setPackageAnnotations( Annotation[] packageAnnotations )
    {
        this.packageAnnotations = packageAnnotations;

    }

    public Annotation[] getPackageAnnotations()
    {
        return this.packageAnnotations;
    }

    public void setDocumentAnnotations( Annotation[] documentAnnotations )
    {
        this.documentAnnotations = documentAnnotations;
    }

    public Annotation[] getDocumentAnnotations()
    {
        return this.documentAnnotations;
    }

    public void setExternalRefs( List<ExternalReference> externalReferences )
    {
        this.externalRefs = externalReferences;
    }

    public List<ExternalReference> getExternalRefs()
    {
        return this.externalRefs;
    }
}
