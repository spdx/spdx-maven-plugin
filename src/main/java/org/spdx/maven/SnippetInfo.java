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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.spdx.rdfparser.SpdxDocumentContainer;
import org.spdx.rdfparser.license.AnyLicenseInfo;
import org.spdx.rdfparser.license.LicenseInfoFactory;
import org.spdx.rdfparser.model.SpdxElement;
import org.spdx.rdfparser.model.pointer.ByteOffsetPointer;
import org.spdx.rdfparser.model.pointer.LineCharPointer;
import org.spdx.rdfparser.model.pointer.StartEndPointer;
import org.spdx.spdxspreadsheet.InvalidLicenseStringException;

/**
 * Simple class to hold information about snippets
 *
 * @author Gary O'Neall
 */
public class SnippetInfo
{
    private static final Pattern NUMBER_RANGE_PATTERN = Pattern.compile( "(\\d+):(\\d+)" );

    @Parameter( required = false )
    private String name;

    @Parameter( required = false )
    private String comment;

    @Parameter( defaultValue = "NOASSERTION" )
    private String concludedLicense;

    @Parameter( required = false )
    private String lineRange;

    @Parameter( required = true )
    private String byteRange;

    @Parameter( required = false )
    private String licenseComment;

    @Parameter( defaultValue = "NOASSERTION" )
    private String copyrightText;

    @Parameter( defaultValue = "NOASSERTION" )
    private String licenseInfoInSnippet;

    public void logInfo( Log log )
    {
        log.debug( "Snippet information follows:" );
        if ( this.name != null )
        {
            log.debug( "Name: " + this.name );
        }
        log.debug( "Byte range: " + this.byteRange );
        if ( this.comment != null )
        {
            log.debug( "Comment: " + this.comment );
        }
        log.debug( "Concluded license: " + this.concludedLicense );
        if ( this.copyrightText != null )
        {
            log.debug( "Copyright: " + this.copyrightText );
        }
        if ( this.licenseComment != null )
        {
            log.debug( "License comment: " + this.licenseComment );
        }
        log.debug( "License info in Snippet: " + this.licenseInfoInSnippet );
        if ( this.lineRange != null )
        {
            log.debug( "Line range: " + this.lineRange );
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

    public AnyLicenseInfo getLicenseConcluded( SpdxDocumentContainer container ) throws InvalidLicenseStringException
    {
        return LicenseInfoFactory.parseSPDXLicenseString( this.concludedLicense, container );
    }

    public AnyLicenseInfo[] getLicenseInfoInSnippet( SpdxDocumentContainer container ) throws InvalidLicenseStringException
    {
        return new AnyLicenseInfo[] {LicenseInfoFactory.parseSPDXLicenseString( this.licenseInfoInSnippet, container )};
    }

    public String getCopyrightText()
    {
        return this.copyrightText;
    }

    public String getLicensComment()
    {
        return this.licenseComment;
    }

    public StartEndPointer getByteRange( SpdxElement fileReference ) throws SpdxBuilderException
    {
        Matcher matcher = NUMBER_RANGE_PATTERN.matcher( byteRange.trim() );
        if ( !matcher.find() )
        {
            throw ( new SpdxBuilderException( "Invalid snippet byte range: " + byteRange ) );
        }
        ByteOffsetPointer start = null;
        try
        {
            start = new ByteOffsetPointer( fileReference, Integer.parseInt( matcher.group( 1 ) ) );
        }
        catch ( Exception ex )
        {
            throw new SpdxBuilderException( "Non integer start to snippet byte offset: " + byteRange );
        }
        ByteOffsetPointer end = null;
        try
        {
            end = new ByteOffsetPointer( fileReference, Integer.parseInt( matcher.group( 2 ) ) );
        }
        catch ( Exception ex )
        {
            throw new SpdxBuilderException( "Non integer end to snippet byte offset: " + byteRange );
        }
        return new StartEndPointer( start, end );
    }

    public StartEndPointer getLineRange( SpdxElement fileReference ) throws SpdxBuilderException
    {
        Matcher matcher = NUMBER_RANGE_PATTERN.matcher( lineRange );
        if ( !matcher.find() )
        {
            throw ( new SpdxBuilderException( "Invalid snippet line range: " + lineRange ) );
        }
        LineCharPointer start = null;
        try
        {
            start = new LineCharPointer( fileReference, Integer.parseInt( matcher.group( 1 ) ) );
        }
        catch ( Exception ex )
        {
            throw new SpdxBuilderException( "Non integer start to snippet line offset: " + lineRange );
        }
        LineCharPointer end = null;
        try
        {
            end = new LineCharPointer( fileReference, Integer.parseInt( matcher.group( 2 ) ) );
        }
        catch ( Exception ex )
        {
            throw new SpdxBuilderException( "Non integer end to snippet line offset: " + lineRange );
        }
        return new StartEndPointer( start, end );
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
