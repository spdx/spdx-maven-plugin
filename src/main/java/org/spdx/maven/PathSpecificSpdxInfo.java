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

import java.util.List;
import org.spdx.maven.utils.SpdxDefaultFileInformation;

/**
 * Simple class to hold SPDX data for a file or directory.  The only required
 * parameter is the File.
 *
 * @author Gary O'Neall
 */
@SuppressWarnings("unused")
public class PathSpecificSpdxInfo
{
    /**
     * File or directory for which the specific file information is referred to
     */
    String directoryOrFile;
    /**
     * SPDX file comment field. The file comment field provides a place for the SPDX file creator to record any general
     * comments about the file.
     */
    private String fileComment;

    /**
     * File contributors. This field provides a place for the SPDX file creator to record file contributors.
     * Contributors could include names of copyright holders and/or authors who may not be copyright holders, yet
     * contributed to the file content.
     */
    private String[] fileContributors;

    /**
     * File copyright text. If no copyright text is specified, NOASSERTION will be used The copyrightText field
     * Identifies the copyright holder of the file, as well as any dates present. The text must much the copyright
     * notice found in the file. The options to populate this field are limited to: (a) any text relating to a copyright
     * notice, even if not complete; (b) NONE, if the file contains no license information whatsoever; or (c)
     * NOASSERTION, if the SPDX creator has not examined the contents of the actual file or if the SPDX creator has
     * intentionally provided no information(no meaning should be implied from the absence of an assertion).
     */
    private String fileCopyright;

    /**
     * File license comment. The licenseComments property allows the preparer of the SPDX document to describe why the
     * licensing in spdx:licenseConcluded was chosen.
     */
    private String fileLicenseComment;

    /**
     * File notice text. This field provides a place for the SPDX file creator to record potential legal notices found
     * in the file. This may or may not include copyright statements.
     */
    private String fileNotice;

    /**
     * This field contains the license the SPDX file creator has concluded as governing the file or alternative values
     * if the governing license cannot be determined. If no concluded license is specified "NOASSERTION" will be used.
     */
    private String fileConcludedLicense;

    /**
     * License information in file. If no licenseInformationInFile is specified, NOASSERTION will be used This field
     * contains the license information actually found in the file, if any. Any license information not actually in the
     * file, e.g., “COPYING.txt” file in a toplevel directory, should not be reflected in this field. This information
     * is most commonly found in the header of the file, although it may be in other areas of the actual file. The
     * options to populate this field are limited to: (a) the SPDX License List short form identifier, if the license is
     * on the SPDX License List; (b) a reference to the license, denoted by LicenseRef-#LicenseRef-[idString], if the
     * license is not on the SPDX License List; (c) NONE, if the actual file contains no license information whatsoever;
     * or (d) NOASSERTION, if the SPDX file creator has not examined the contents of the actual file or the SPDX file
     * creator has intentionally provided no information (no meaning should be implied by doing so). For a license set,
     * when there is a choice between licenses (“disjunctive license”), they should be separated with “or” and enclosed
     * in brackets. Similarly, when multiple licenses need to be applied (“conjunctive license”), they should be
     * separated with “and” and enclosed in parentheses.
     */
    private String licenseInformationInFile;

    private List<SnippetInfo> snippets;

    /**
     * Default constructor
     */
    public PathSpecificSpdxInfo()
    {

    }

    /**
     * Get the default file information to be used with this file path
     *
     * @param defaults  Default file information to use if the parameter was not specified for this file path

     * @return default file information to be used with this file path
     */
    public SpdxDefaultFileInformation getDefaultFileInformation( SpdxDefaultFileInformation defaults )
    {
        SpdxDefaultFileInformation retval = new SpdxDefaultFileInformation();
        if ( this.fileComment != null )
        {
            retval.setComment( fileComment );
        }
        else
        {
            retval.setComment( defaults.getComment() );
        }
        if ( this.fileConcludedLicense != null )
        {
            retval.setConcludedLicense( this.fileConcludedLicense );
        }
        else
        {
            retval.setConcludedLicense( defaults.getConcludedLicense() );
        }
        if ( this.fileContributors != null )
        {
            retval.setContributors( this.fileContributors );
        }
        else
        {
            retval.setContributors( defaults.getContributors() );
        }
        if ( this.fileCopyright != null )
        {
            retval.setCopyright( fileCopyright );
        }
        else
        {
            retval.setCopyright( defaults.getCopyright() );
        }
        if ( this.fileLicenseComment != null )
        {
            retval.setLicenseComment( this.fileLicenseComment );
        }
        else
        {
            retval.setLicenseComment( defaults.getLicenseComment() );
        }
        if ( this.fileNotice != null )
        {
            retval.setNotice( this.fileNotice );
        }
        else
        {
            retval.setNotice( defaults.getNotice() );
        }
        if ( this.licenseInformationInFile != null )
        {
            retval.setDeclaredLicense( licenseInformationInFile.trim() );
        }
        else
        {
            retval.setDeclaredLicense( defaults.getDeclaredLicense() );
        }
        if ( this.snippets != null )
        {
            retval.setSnippets( snippets );
        }
        return retval;
    }

    /**
     * @return Path for directory or file to which this SPDX data is applied
     */
    public String getPath()
    {
        return this.directoryOrFile;
    }

}
