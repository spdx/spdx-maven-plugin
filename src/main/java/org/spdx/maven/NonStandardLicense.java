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
package org.spdx.maven;

import java.net.URL;
import java.util.Objects;

/**
 * Non-standard license (e.g. license which is not in the <a href="http://spdx.org/licenses">SPDX standard license list</a>)
 *
 * @author Gary O'Neall
 */
public class NonStandardLicense
{

    /**
     * Required license ID.  Must be of the form "LicenseRef-"[idString] where [idString] is a unique string containing
     * letters, numbers, “.”, “-” or “+”. Note that this is required for the Maven use of non-standard licenses.  The
     * SPDX standard does not generally require this parameter.
     */
    private String licenseId;

    /**
     * Required verbatim license or licensing notice text
     */
    private String extractedText;

    /**
     * Optional: Common name of the license not on the SPDX list. If there is no common name, or it is not known, please
     * use NOASSERTION.
     */
    private String name;
    /**
     *
     */
    private String comment;

    /**
     * Provide a pointer to the official source of a license that is not included in the SPDX table, that is referenced
     * by the id.
     */
    URL[] crossReference;

    /**
     * Create a default, non standard license
     */
    public NonStandardLicense()
    {

    }

    /**
     * @return the licenseId
     */
    public String getLicenseId()
    {
        return licenseId;
    }

    /**
     * @return the extractedText
     */
    public String getExtractedText()
    {
        return extractedText;
    }

    /**
     * @return the name
     */
    public String getName()
    {
        if ( name == null )
        {
            return "";
        }
        return name;
    }

    /**
     * @return the comment
     */
    public String getComment()
    {
        return Objects.requireNonNullElse( comment, "" );
    }

    /**
     * @return the crossReference
     */
    public String[] getCrossReference()
    {
        if ( this.crossReference == null )
        {
            return new String[0];
        }
        String[] retval = new String[this.crossReference.length];
        for ( int i = 0; i < retval.length; i++ )
        {
            retval[i] = this.crossReference[i].toString();
        }
        return retval;
    }

    public void setComment( String comment )
    {
        this.comment = comment;

    }

    public void setCrossReference( URL[] crossRefs )
    {
        this.crossReference = crossRefs;
    }

    public void setExtractedText( String extractedText )
    {
        this.extractedText = extractedText;
    }

    public void setLicenseId( String licenseId )
    {
        this.licenseId = licenseId;
    }

    public void setName( String name )
    {
        this.name = name;
    }

}
