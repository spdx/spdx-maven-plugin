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

import org.apache.maven.plugin.logging.Log;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.SpdxNoAssertionLicense;

/**
 * Simple structure to hold information about SPDX project
 * @author Gary O'Neall
 *
 */
class SpdxProjectInformation {
    String[] creators = new String[0];
    String creatorComment = "";
    AnyLicenseInfo concludedLicense = new SpdxNoAssertionLicense();
    AnyLicenseInfo declaredLicense = new SpdxNoAssertionLicense();
    String description;
    String downloadUrl;
    String homePage;
    String shortDescription;
    String originator;
    String supplier;
    String packageArchiveFileName;
    String versionInfo;
    String licenseComment;
    String sha1;
    String name;
    private String sourceInfo;
    private String copyrightText;
    private String documentComment;
    private Annotation[] packageAnnotations;
    private Annotation[] documentAnnotations;
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
     * @return the sha1
     */
    public String getSha1() {
        return sha1;
    }
    /**
     * @param sha1 the sha1 to set
     */
    public void setSha1( String sha1 ) {
        this.sha1 = sha1;
    }
    /**
     * @return the concludedLicense
     */
    public AnyLicenseInfo getConcludedLicense() {
        return concludedLicense;
    }
    /**
     * @param concludedLicense the concludedLicense to set
     */
    public void setConcludedLicense( AnyLicenseInfo concludedLicense ) {
        this.concludedLicense = concludedLicense;
    }
    /**
     * @return the declaredLicense
     */
    public AnyLicenseInfo getDeclaredLicense() {
        return declaredLicense;
    }
    /**
     * @param declaredLicense the declaredLicense to set
     */
    public void setDeclaredLicense( AnyLicenseInfo declaredLicense ) {
        this.declaredLicense = declaredLicense;
    }
    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }
    /**
     * @param description the description to set
     */
    public void setDescription( String description ) {
        this.description = description;
    }
    /**
     * @return the downloadUrl
     */
    public String getDownloadUrl() {
        return downloadUrl;
    }
    /**
     * @param downloadUrl the downloadUrl to set
     */
    public void setDownloadUrl( String downloadUrl ) {
        this.downloadUrl = downloadUrl;
    }
    /**
     * @return the homePage
     */
    public String getHomePage() {
        return homePage;
    }
    /**
     * @param homePage the homePage to set
     */
    public void setHomePage( String homePage ) {
        this.homePage = homePage;
    }
    /**
     * @return the shortDescription
     */
    public String getShortDescription() {
        return shortDescription;
    }
    /**
     * @param shortDescription the shortDescription to set
     */
    public void setShortDescription( String shortDescription ) {
        this.shortDescription = shortDescription;
    }
    /**
     * @return the originator
     */
    public String getOriginator() {
        return originator;
    }
    /**
     * @param originator the originator to set
     */
    public void setOriginator( String originator ) {
        this.originator = originator;
    }
    /**
     * @return the supplier
     */
    public String getSupplier() {
        return supplier;
    }
    /**
     * @param supplier the supplier to set
     */
    public void setSupplier( String supplier ) {
        this.supplier = supplier;
    }
    /**
     * @return the packageArchiveFileName
     */
    public String getPackageArchiveFileName() {
        return packageArchiveFileName;
    }
    /**
     * @param packageArchiveFileName the packageArchiveFileName to set
     */
    public void setPackageArchiveFileName( String packageArchiveFileName ) {
        this.packageArchiveFileName = packageArchiveFileName;
    }
    /**
     * @return the versionInfo
     */
    public String getVersionInfo() {
        return versionInfo;
    }
    /**
     * @param versionInfo the versionInfo to set
     */
    public void setVersionInfo( String versionInfo ) {
        this.versionInfo = versionInfo;
    }
    /**
     * @return the licenseComment
     */
    public String getLicenseComment() {
        return licenseComment;
    }
    /**
     * @param licenseComment the licenseComment to set
     */
    public void setLicenseComment( String licenseComment ) {
        this.licenseComment = licenseComment;
    }
    /**
     * @return the creators
     */
    public String[] getCreators() {
        return creators;
    }
    /**
     * @param creators the creators to set
     */
    public void setCreators( String[] creators ) {
        this.creators = creators;
    }
    /**
     * @return the creatorComment
     */
    public String getCreatorComment() {
        return creatorComment;
    }
    /**
     * @param creatorComment the creatorComment to set
     */
    public void setCreatorComment( String creatorComment ) {
        this.creatorComment = creatorComment;
    }
    public String getName() {
        return name;
    }
    
    public void setName( String name ) {
        this.name = name;
    }
    
    /**
     * Log information on all fields - typically used for debugging
     * @param log
     */
    public void logInfo( Log log ) {
        log.debug( "SPDX Project Name: "+this.getName() );
        log.debug( "SPDX Document comment: "+this.getDocumentComment() );
        log.debug( "SPDX Creator comment: "+this.getCreatorComment() );
        log.debug( "SPDX Description: "+this.getDescription() );
        log.debug( "SPDX License comment: "+this.getLicenseComment() );
        log.debug( "SPDX Originator: "+this.getOriginator() );
        log.debug( "SPDX PackageArchiveFileName: "+this.getPackageArchiveFileName() );
        log.debug( "SPDX SHA1: "+this.getSha1() );
        log.debug( "SPDX Short description: "+this.getShortDescription() );
        log.debug( "SPDX Supplier: "+this.getSupplier() );
        log.debug( "SPDX Source Info:  "+this.getSourceInfo() );
        log.debug( "SPDX Version info: "+this.getVersionInfo() );
        log.debug( "SPDX Concluded license: "+this.getConcludedLicense().toString() );
        log.debug( "SPDX Declared license: "+this.getDeclaredLicense().toString() );
        log.debug( "SPDX Download URL: "+this.getDownloadUrl() );
        log.debug( "SPDX Home page: "+this.getHomePage() );
        if ( this.documentAnnotations != null && this.documentAnnotations.length > 0 ) {
            log.debug( "Document annotations: " );
            for ( Annotation annotation:documentAnnotations ) {
                annotation.logInfo( log );
            }
        }
        if ( this.packageAnnotations != null && this.packageAnnotations.length > 0 ) {
            log.debug( "Package annotations: " );
            for ( Annotation annotation:packageAnnotations ) {
                annotation.logInfo( log );
            }
        }
        String[] creators = this.getCreators();
        if ( creators != null ) {
            for ( int i = 0; i < creators.length; i++ ) {
                log.debug( "SPDX Creator: "+creators[i] );
            }
        }
    }
    public String getSourceInfo()
    {
        return this.sourceInfo;
    }
    
    public void setSourceInfo( String sourceInformation ) {
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
    public Annotation[] getPackageAnnotations() {
        return this.packageAnnotations;
    }
    public void setDocumentAnnotations( Annotation[] documentAnnotations )
    {
        this.documentAnnotations = documentAnnotations;
    }
    public Annotation[] getDocumentAnnotations() {
        return this.documentAnnotations;
    }
}
