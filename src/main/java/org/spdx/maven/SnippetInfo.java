/*
 * Copyright 2016 Source Auditor Inc.
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
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.spdx.core.DefaultStoreNotInitialized;
import org.spdx.library.LicenseInfoFactory;
import org.spdx.library.model.v2.SpdxDocument;
import org.spdx.library.model.v2.license.AnyLicenseInfo;
import org.spdx.library.model.v2.license.InvalidLicenseStringException;
import org.spdx.maven.utils.SpdxBuilderException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple class to hold information about snippets
 *
 * @author Gary O'Neall
 */
public class SnippetInfo
{
    private static final Logger LOG = LoggerFactory.getLogger( SnippetInfo.class );
    private static final Pattern NUMBER_RANGE_PATTERN = Pattern.compile( "(\\d+):(\\d+)" );

    private String name;

    private String comment;

    private String concludedLicense;

    private String lineRange;

    private String byteRange;

    private String licenseComment;

    private String copyrightText;

    private String licenseInfoInSnippet;

    public void logInfo()
    {
        if ( !LOG.isDebugEnabled() ) {
            return;
        }
        LOG.debug( "Snippet information follows:" );
        if ( this.name != null )
        {
            LOG.debug( "Name: " + this.name );
        }
        LOG.debug( "Byte range: " + this.byteRange );
        if ( this.comment != null )
        {
            LOG.debug( "Comment: " + this.comment );
        }
        LOG.debug( "Concluded license: " + this.concludedLicense );
        if ( this.copyrightText != null )
        {
            LOG.debug( "Copyright: " + this.copyrightText );
        }
        if ( this.licenseComment != null )
        {
            LOG.debug( "License comment: " + this.licenseComment );
        }
        LOG.debug( "License info in Snippet: " + this.licenseInfoInSnippet );
        if ( this.lineRange != null )
        {
            LOG.debug( "Line range: " + this.lineRange );
        }
    }

    public String getName()
    {
        return this.name;
    }

    public String getComment()
    {
        return this.comment;
    }

    public AnyLicenseInfo getLicenseConcluded( SpdxDocument spdxDoc ) throws InvalidLicenseStringException, DefaultStoreNotInitialized
    {
        return LicenseInfoFactory.parseSPDXLicenseStringCompatV2( this.concludedLicense, spdxDoc.getModelStore(), 
                                                          spdxDoc.getDocumentUri(), spdxDoc.getCopyManager() );
    }

    public Collection<AnyLicenseInfo> getLicenseInfoInSnippet( SpdxDocument spdxDoc ) throws InvalidLicenseStringException, DefaultStoreNotInitialized
    {
        List<AnyLicenseInfo> retval = new ArrayList<>();
        retval.add( LicenseInfoFactory.parseSPDXLicenseStringCompatV2( this.licenseInfoInSnippet, spdxDoc.getModelStore(), 
                                                                                spdxDoc.getDocumentUri(), spdxDoc.getCopyManager() ));
        return retval;                                                                    
    }

    public String getCopyrightText()
    {
        return this.copyrightText;
    }

    public String getLicensComment()
    {
        return this.licenseComment;
    }

    public int getByteRangeStart() throws SpdxBuilderException
    {
        Matcher matcher = NUMBER_RANGE_PATTERN.matcher( byteRange.trim() );
        if ( !matcher.find() )
        {
            throw new SpdxBuilderException( "Invalid snippet byte range: " + byteRange );
        }
        try
        {
            return Integer.parseInt( matcher.group( 1 ) );
        }
        catch ( Exception ex )
        {
            throw new SpdxBuilderException( "Non integer start to snippet byte offset: " + byteRange );
        }
    }

    public int getByteRangeEnd() throws SpdxBuilderException
    {
        Matcher matcher = NUMBER_RANGE_PATTERN.matcher( byteRange.trim() );
        if ( !matcher.find() )
        {
            throw new SpdxBuilderException( "Invalid snippet byte range: " + byteRange );
        }
        try
        {
            return Integer.parseInt( matcher.group( 2 ) );
        }
        catch ( Exception ex )
        {
            throw new SpdxBuilderException( "Non integer end to snippet byte offset: " + byteRange );
        }
    }

    public int getLineRangeStart() throws SpdxBuilderException
    {
        Matcher matcher = NUMBER_RANGE_PATTERN.matcher( lineRange );
        if ( !matcher.find() )
        {
            throw new SpdxBuilderException( "Invalid snippet line range: " + lineRange );
        }
        try
        {
            return Integer.parseInt( matcher.group( 1 ) );
        }
        catch ( Exception ex )
        {
            throw new SpdxBuilderException( "Non integer end to snippet line offset: " + lineRange );
        }
    }

    public int getLineRangeEnd() throws SpdxBuilderException
    {
        Matcher matcher = NUMBER_RANGE_PATTERN.matcher( lineRange );
        if ( !matcher.find() )
        {
            throw new SpdxBuilderException( "Invalid snippet line range: " + lineRange );
        }
        try
        {
            return Integer.parseInt( matcher.group( 2 ) );
        }
        catch ( Exception ex )
        {
            throw new SpdxBuilderException( "Non integer end to snippet line offset: " + lineRange );
        }
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
     * @return the licenseInfoInSnippet
     */
    public String getLicenseInfoInSnippet()
    {
        return licenseInfoInSnippet;
    }

    /**
     * @param licenseInfoInSnippet the licenseInfoInSnippet to set
     */
    public void setLicenseInfoInSnippet( String licenseInfoInSnippet )
    {
        this.licenseInfoInSnippet = licenseInfoInSnippet;
    }

    /**
     * @param name the name to set
     */
    public void setName( String name )
    {
        this.name = name;
    }

    /**
     * @param comment the comment to set
     */
    public void setComment( String comment )
    {
        this.comment = comment;
    }

    /**
     * @param lineRange the lineRange to set
     */
    public void setLineRange( String lineRange )
    {
        this.lineRange = lineRange;
    }

    /**
     * @param byteRange the byteRange to set
     */
    public void setByteRange( String byteRange )
    {
        this.byteRange = byteRange;
    }

    /**
     * @param copyrightText the copyrightText to set
     */
    public void setCopyrightText( String copyrightText )
    {
        this.copyrightText = copyrightText;
    }
}
