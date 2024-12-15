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

import java.util.Set;

import org.spdx.maven.Annotation;
import org.spdx.maven.Checksum;
import org.spdx.maven.ExternalReference;
import org.spdx.maven.Packaging;
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
    private String concludedLicense;
    private String declaredLicense;
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
    private ExternalReference[] externalRefs;
    private Set<Checksum> checksums;
    private Packaging packaging;
    
    

    /**
     * @return the packaging
     */
    public Packaging getPackaging()
    {
        return packaging;
    }

    /**
     * @param packaging the packaging to set
     */
    public void setPackaging( Packaging packaging )
    {
        this.packaging = packaging;
    }

    public SpdxProjectInformation ()
    {
        this.concludedLicense = "NOASSERTION";
        this.declaredLicense = "NOASSERTION";
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
    public String getConcludedLicense()
    {
        return concludedLicense;
    }

    /**
     * @param concludedLicense the concludedLicense to set
     */
    public void setConcludedLicense( String concludedLicense )
    {
        this.concludedLicense = concludedLicense;
    }

    /**
     * @return the declaredLicense
     */
    public String getDeclaredLicense()
    {
        return declaredLicense;
    }

    /**
     * @param declaredLicense the declaredLicense to set
     */
    public void setDeclaredLicense( String declaredLicense )
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
    public void logInfo()
    {
        if ( !LOG.isDebugEnabled() ) {
            return;
        }
        LOG.debug("SPDX Project Name: {}", this.getName());
        LOG.debug("SPDX Document comment: {}", this.getDocumentComment());
        LOG.debug("SPDX Creator comment: {}", this.getCreatorComment());
        LOG.debug("SPDX Description: {}", this.getDescription());
        LOG.debug("SPDX License comment: {}", this.getLicenseComment());
        LOG.debug("SPDX Originator: {}", this.getOriginator());
        LOG.debug("SPDX PackageArchiveFileName: {}", this.getPackageArchiveFileName());
        LOG.debug("SPDX Short description: {}", this.getShortDescription());
        LOG.debug("SPDX Supplier: {}", this.getSupplier());
        LOG.debug("SPDX Source Info:  {}", this.getSourceInfo());
        LOG.debug("SPDX Version info: {}", this.getVersionInfo());
        LOG.debug("SPDX Concluded license: {}", this.getConcludedLicense());
        LOG.debug("SPDX Declared license: {}", this.getDeclaredLicense());
        LOG.debug("SPDX Download URL: {}", this.getDownloadUrl());
        LOG.debug("SPDX Home page: {}", this.getHomePage());
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
                LOG.debug("SPDX Creator: {}", creator);
            }
        }
        if ( this.externalRefs != null )
        {
            for ( ExternalReference externalReference : externalRefs )
            {
                LOG.debug("External Ref: {} {} {}", externalReference.getCategory(), externalReference.getType(), externalReference.getLocator());
            }
        }
        if ( checksums != null && !checksums.isEmpty())
        {
            for ( Checksum checksum : checksums )
            {
                LOG.debug("SPDX {}: {}", checksum.getAlgorithm(), checksum.getValue());
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

    public void setExternalRefs( ExternalReference[] externalReferences )
    {
        this.externalRefs = externalReferences;
    }

    public ExternalReference[] getExternalRefs()
    {
        return this.externalRefs;
    }
}
