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

import org.spdx.rdfparser.DOAPProject;
import org.spdx.rdfparser.SPDXLicenseInfo;
import org.spdx.rdfparser.SpdxNoAssertionLicense;

/**
 * Simple structure to hold information obout default file information
 * @author Gary O'Neall
 *
 */
public class SpdxDefaultFileInformation {

	private SPDXLicenseInfo declaredLicense = new SpdxNoAssertionLicense();
	private String copyright = "NOASSERTION";
	private String notice = "";
	private String comment = "";
	private String[] contributors = new String[0];
	private DOAPProject[] artifactOf = new DOAPProject[0];
	private SPDXLicenseInfo concludedLicense = new SpdxNoAssertionLicense();;
	private String licenseComment = "";

	public SPDXLicenseInfo getDeclaredLicense() {
		return this.declaredLicense;
	}
	
	public void setDeclaredLicense(SPDXLicenseInfo license) {
		this.declaredLicense = license;
	}

	public String getCopyright() {
		return this.copyright ;
	}
	
	public void setCopyright(String copyright) {
		this.copyright = copyright;
	}

	public String getNotice() {
		return this.notice;
	}
	
	public void setNotice(String notice) {
		this.notice = notice;
	}

	public String getComment() {
		return this.comment;
	}
	
	public void setComment(String comment) {
		this.comment = comment;
	}

	public String[] getContributors() {
		return this.contributors;
	}
	
	public void setContributors(String[] contributors) {
		this.contributors = contributors;
	}

	public DOAPProject[] getArtifactOf() {
		return this.artifactOf;
	}
	
	public void setArtifactOf(DOAPProject[] projects) {
		this.artifactOf = projects;
	}

	public SPDXLicenseInfo getConcludedLicense() {
		return this.concludedLicense;
	}
	
	public void setConcludedLicense(SPDXLicenseInfo license) {
		this.concludedLicense = license;
	}

	public String getLicenseComment() {
		return this.licenseComment;
	}
	
	public void setLicenseComment(String licenseComment) {
		this.licenseComment = licenseComment;
	}
}
