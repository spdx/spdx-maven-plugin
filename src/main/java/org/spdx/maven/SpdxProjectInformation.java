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
import org.spdx.rdfparser.SPDXLicenseInfo;
import org.spdx.rdfparser.SpdxNoAssertionLicense;

/**
 * Simple structure to hold information about SPDX project
 * @author Gary O'Neall
 *
 */
class SpdxProjectInformation {
    String[] creators = new String[0];
    String creatorComment = "";
    SPDXLicenseInfo concludedLicense = new SpdxNoAssertionLicense();
    SPDXLicenseInfo declaredLicense = new SpdxNoAssertionLicense();
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
    public SPDXLicenseInfo getConcludedLicense() {
        return concludedLicense;
    }
    /**
     * @param concludedLicense the concludedLicense to set
     */
    public void setConcludedLicense( SPDXLicenseInfo concludedLicense ) {
        this.concludedLicense = concludedLicense;
    }
    /**
     * @return the declaredLicense
     */
    public SPDXLicenseInfo getDeclaredLicense() {
        return declaredLicense;
    }
    /**
     * @param declaredLicense the declaredLicense to set
     */
    public void setDeclaredLicense( SPDXLicenseInfo declaredLicense ) {
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
        log.info( "SPDX Project Name: "+this.getName() );
        log.info( "SPDX Creator comment: "+this.getCreatorComment() );
        log.info( "SPDX Description: "+this.getDescription() );
        log.info( "SPDX License comment: "+this.getLicenseComment() );
        log.info( "SPDX Originator: "+this.getOriginator() );
        log.info( "SPDX PackageArchiveFileName: "+this.getPackageArchiveFileName() );
        log.info( "SPDX SHA1: "+this.getSha1() );
        log.info( "SPDX Short description: "+this.getShortDescription() );
        log.info( "SPDX Supplier: "+this.getSupplier() );
        log.info( "SPDX Version info: "+this.getVersionInfo() );
        log.info( "SPDX Concluded license: "+this.getConcludedLicense().toString() );
        log.info( "SPDX Declared license: "+this.getDeclaredLicense().toString() );
        log.info( "SPDX Download URL: "+this.getDownloadUrl() );
        log.info( "SPDX Home page: "+this.getHomePage() );
        String[] creators = this.getCreators();
        if ( creators != null ) {
            for ( int i = 0; i < creators.length; i++ ) {
                log.info( "SPDX Creator: "+creators[i] );
            }
        }
    }
}
