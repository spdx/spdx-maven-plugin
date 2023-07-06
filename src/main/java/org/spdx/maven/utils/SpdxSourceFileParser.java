/*
 * Copyright 2019 Source Auditor Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License" );
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.spdx.library.model.license.AnyLicenseInfo;
import org.spdx.library.model.license.InvalidLicenseStringException;
import org.spdx.library.model.license.LicenseInfoFactory;

/**
 * Helper class with static methods to parse SPDX source files
 *
 * @author Gary O'Neall
 */
public class SpdxSourceFileParser
{
    public static final long MAXIMUM_SOURCE_FILE_LENGTH = 300000; // anything over this will not be parsed for SPDX license IDs
    protected static final Pattern SPDX_LICENSE_PATTERN = Pattern.compile(
            "SPDX-License-Identifier:\\s*([^\\n^\\r]+)(\\n|\\r|$)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE );

    /**
     * Parses a text file for matches to SPDX-License-Identifier:
     *
     * @param file Text file to parse
     * @return list of all license expressions found following SPDX-License-Identifier:
     */
    public static List<AnyLicenseInfo> parseFileForSpdxLicenses( File file ) throws SpdxSourceParserException
    {
        try
        {
            return parseTextForSpdxLicenses( FileUtils.readFileToString( file, "utf-8" ) );
        }
        catch ( IOException e )
        {
            throw new SpdxSourceParserException( "I/O error reading text for source file " + file.getName(), e );
        }
        catch ( SpdxSourceParserException e )
        {
            throw new SpdxSourceParserException( "Error parsing license text for file " + file.getName(), e );
        }
    }

    public static List<AnyLicenseInfo> parseTextForSpdxLicenses( String text ) throws SpdxSourceParserException
    {
        List<AnyLicenseInfo> retval = new ArrayList<>();
        Matcher match = SPDX_LICENSE_PATTERN.matcher( text );
        int pos = 0;
        while ( pos < text.length() && match.find( pos ) )
        {
            String matchingLine = match.group( 1 ).trim();
            if ( matchingLine.startsWith( "(" ) )
            {
                // This could be a multi-line expression, so we need to parse until we get to the last )
                int parenCount = 1;
                StringBuilder sb = new StringBuilder( "(" );
                pos = match.start( 1 ) + 1;
                while ( parenCount > 0 && pos < text.length() )
                {
                    char ch = text.charAt( pos );
                    if ( ch == '(' )
                    {
                        parenCount++;
                    }
                    else if ( ch == ')' )
                    {
                        parenCount--;
                    }
                    if ( ch == '\n' || ch == '\r' )
                    {
                        sb.append( ' ' );
                    }
                    else
                    {
                        sb.append( ch );
                    }
                    pos++;
                }
                if ( parenCount > 0 )
                {
                    throw new SpdxSourceParserException( "Miss-matched parenthesis for expression" );
                }
                matchingLine = sb.toString();
            }
            else
            {
                pos = match.end() + 1;
            }
            try
            {
                retval.add( LicenseInfoFactory.parseSPDXLicenseString( matchingLine ) );
            }
            catch ( InvalidLicenseStringException e )
            {
                throw new SpdxSourceParserException( "Invalid SPDX license string '" + matchingLine + "'." );
            }
        }
        return retval;
    }

}
