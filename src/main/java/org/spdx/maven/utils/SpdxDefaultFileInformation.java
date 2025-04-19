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
import java.util.List;

import org.spdx.maven.SnippetInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple structure to hold information about default file information
 *
 * @author Gary O'Neall
 */
public class SpdxDefaultFileInformation
{
    private static final Logger LOG = LoggerFactory.getLogger( SpdxDefaultFileInformation.class );

    private String declaredLicense;
    private String copyright = "NOASSERTION";
    private String notice = "";
    private String comment = "";
    private String[] contributors = new String[0];
    private String concludedLicense;
    private String licenseComment = "";
    private List<SnippetInfo> snippets = new ArrayList<>();
    
    public SpdxDefaultFileInformation()
    {
        declaredLicense = "NOASSERTION";
        concludedLicense = "NOASSERTION";
    }

    public String getDeclaredLicense()
    {
        return this.declaredLicense;
    }

    public void setDeclaredLicense( String license )
    {
        this.declaredLicense = license;
    }

    public String getCopyright()
    {
        return this.copyright;
    }

    public void setCopyright( String copyright )
    {
        this.copyright = copyright;
    }

    public String getNotice()
    {
        return this.notice;
    }

    public void setNotice( String notice )
    {
        this.notice = notice;
    }

    public String getComment()
    {
        return this.comment;
    }

    public void setComment( String comment )
    {
        this.comment = comment;
    }

    public String[] getContributors()
    {
        return this.contributors;
    }

    public void setContributors( String[] contributors )
    {
        this.contributors = contributors;
    }

    public String getConcludedLicense()
    {
        return this.concludedLicense;
    }

    public void setConcludedLicense( String license )
    {
        this.concludedLicense = license;
    }

    public String getLicenseComment()
    {
        return this.licenseComment;
    }

    public void setLicenseComment( String licenseComment )
    {
        this.licenseComment = licenseComment;
    }


    /**
     * @return the snippets
     */
    public List<SnippetInfo> getSnippets()
    {
        return snippets;
    }

    /**
     * @param snippets the snippets to set
     */
    public void setSnippets( List<SnippetInfo> snippets )
    {
        this.snippets = snippets;
    }

    /**
     * Primarily for debugging purposes.  Dump all the field information to the log Info
     */
    public void logInfo()
    {
        LOG.debug( "Default File Comment: {}", getComment() );
        LOG.debug( "Default File Copyright: {}", getCopyright() );
        LOG.debug( "Default File License Comment: {}", getLicenseComment() );
        LOG.debug( "Default File Notice: {}", getNotice() );
        LOG.debug( "Default File Concluded License: {}", getConcludedLicense() );
        LOG.debug( "Default File Declared License: {}", getDeclaredLicense() );
        if ( contributors != null )
        {
            for ( String contributor : contributors )
            {
                LOG.debug( "Default File Contributors: {}", contributor );
            }
        }
        if ( this.snippets != null )
        {
            for ( SnippetInfo snippet : snippets )
            {
                snippet.logInfo();
            }
        }
    }
}
