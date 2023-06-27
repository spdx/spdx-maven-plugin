package org.spdx.maven.utils;

import static org.junit.Assert.*;

import java.io.File;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.model.license.AnyLicenseInfo;
import org.spdx.library.model.license.ConjunctiveLicenseSet;
import org.spdx.library.model.license.DisjunctiveLicenseSet;
import org.spdx.library.model.license.ExtractedLicenseInfo;
import org.spdx.library.model.license.SpdxListedLicense;

public class TestSpdxSourceFileParser
{

    static final String EMPTY = "";
    private static final String APACHE_LICENSE_ID = "Apache-2.0";
    private static final String MIT_LICENSE_ID = "MIT";
    private static final String LICENSE_REF_ID = "LicenseRef-mine";
    private static final String SIMPLE = "SPDX-License-Identifier:" + APACHE_LICENSE_ID;
    private static final String CONJUNCTIVE = "  SPDX-License-Identifier:   " + APACHE_LICENSE_ID + " AND " + MIT_LICENSE_ID + " AND " + LICENSE_REF_ID;
    private static final String COMPLEX = "SPDX-License-Identifier:((" + MIT_LICENSE_ID + " OR " + APACHE_LICENSE_ID + ") OR " + APACHE_LICENSE_ID + ")";
    private static final String COMPLEX_MULTI = "  SPDX-License-Identifier: ((" + MIT_LICENSE_ID + " OR \n" + APACHE_LICENSE_ID + ") OR \n" + APACHE_LICENSE_ID + ")";
    private static final String MULTIPLE_SIMPLE_IDS = "Now is the time\n" + "SPDX-License-Identifier:" + APACHE_LICENSE_ID + "\nFor all good men" + "  SPDX-License-Identifier:   " + MIT_LICENSE_ID + "\nto come to the aid of their country.";
    private static final String MULTIPLE_COMPLEX_IDS = COMPLEX + "\nNow is the time\n" + "SPDX-License-Identifier:" + APACHE_LICENSE_ID + "\nFor all good men" + "\n  SPDX-License-Identifier:   " + MIT_LICENSE_ID + "\nto come to the aid of their country.\n" + COMPLEX_MULTI + "\n" + CONJUNCTIVE + "\n\n\nSPDX-License-Identifier:" + LICENSE_REF_ID;
    private static final String MISSMATCHED_PARENS = "  SPDX-License-Identifier: (((" + MIT_LICENSE_ID + " OR \n" + APACHE_LICENSE_ID + ") OR \n" + APACHE_LICENSE_ID + ")";
    private static final String INVALID_EXPRESSION = "  SPDX-License-Identifier:   " + APACHE_LICENSE_ID + " NOTVALID " + MIT_LICENSE_ID + " AND " + LICENSE_REF_ID;
    private static final String TEST_CLASS_FILE_NAME = "target/test-classes/unit/ClassWithManySpdxIDs.java";

    @Before
    public void setUp() throws Exception
    {
    }

    @After
    public void tearDown() throws Exception
    {
    }

    private String getBaseDir()
    {
        String retval = System.getProperty( "basedir" );
        if ( retval == null )
        {
            retval = new File( "" ).getAbsolutePath();
        }
        return retval;
    }

    @Test
    public void testParseFileForSpdxLicenses() throws SpdxSourceParserException, InvalidSPDXAnalysisException
    {
        File javaFile = new File( getBaseDir(), TEST_CLASS_FILE_NAME );
        List<AnyLicenseInfo> result = SpdxSourceFileParser.parseFileForSpdxLicenses( javaFile );
        assertEquals( 3, result.size() );
        assertEquals( APACHE_LICENSE_ID, ( (SpdxListedLicense) result.get( 0 ) ).getLicenseId() );
        assertEquals( MIT_LICENSE_ID, ( (SpdxListedLicense) result.get( 1 ) ).getLicenseId() );
        assertTrue( result.get( 2 ) instanceof DisjunctiveLicenseSet );
        assertEquals( 2, ( (DisjunctiveLicenseSet) result.get( 2 ) ).getMembers().size() );
    }

    @Test
    public void testParseTextForSpdxLicenses() throws SpdxSourceParserException, InvalidSPDXAnalysisException
    {
        // Empty String
        List<AnyLicenseInfo> result = SpdxSourceFileParser.parseTextForSpdxLicenses( EMPTY );
        assertEquals( 0, result.size() );
        // Simple single license ID
        result = SpdxSourceFileParser.parseTextForSpdxLicenses( SIMPLE );
        assertEquals( 1, result.size() );
        assertEquals( APACHE_LICENSE_ID, ( (SpdxListedLicense) result.get( 0 ) ).getLicenseId() );
        // LicenseRef conjunctive
        result = SpdxSourceFileParser.parseTextForSpdxLicenses( CONJUNCTIVE );
        assertEquals( 1, result.size() );
        assertTrue( result.get( 0 ) instanceof ConjunctiveLicenseSet );
        assertEquals( 3, ( (ConjunctiveLicenseSet) result.get( 0 ) ).getMembers().size() );
        // Single Line complex
        result = SpdxSourceFileParser.parseTextForSpdxLicenses( COMPLEX );
        assertEquals( 1, result.size() );
        assertTrue( result.get( 0 ) instanceof DisjunctiveLicenseSet );
        assertEquals( 2, ( (DisjunctiveLicenseSet) result.get( 0 ) ).getMembers().size() );
        // Multi Line complex
        result = SpdxSourceFileParser.parseTextForSpdxLicenses( COMPLEX_MULTI );
        assertEquals( 1, result.size() );
        assertTrue( result.get( 0 ) instanceof DisjunctiveLicenseSet );
        assertEquals( 2, ( (DisjunctiveLicenseSet) result.get( 0 ) ).getMembers().size() );
        // Multiple SPDX ID's simple
        result = SpdxSourceFileParser.parseTextForSpdxLicenses( MULTIPLE_SIMPLE_IDS );
        assertEquals( 2, result.size() );
        assertEquals( APACHE_LICENSE_ID, ( (SpdxListedLicense) result.get( 0 ) ).getLicenseId() );
        assertEquals( MIT_LICENSE_ID, ( (SpdxListedLicense) result.get( 1 ) ).getLicenseId() );
        // Multiple SPDX ID's complex
        result = SpdxSourceFileParser.parseTextForSpdxLicenses( MULTIPLE_COMPLEX_IDS );
        assertEquals( 6, result.size() );
        assertTrue( result.get( 0 ) instanceof DisjunctiveLicenseSet );
        assertEquals( 2, ( (DisjunctiveLicenseSet) result.get( 0 ) ).getMembers().size() );
        assertEquals( APACHE_LICENSE_ID, ( (SpdxListedLicense) result.get( 1 ) ).getLicenseId() );
        assertEquals( MIT_LICENSE_ID, ( (SpdxListedLicense) result.get( 2 ) ).getLicenseId() );
        assertTrue( result.get( 3 ) instanceof DisjunctiveLicenseSet );
        assertEquals( 2, ( (DisjunctiveLicenseSet) result.get( 3 ) ).getMembers().size() );
        assertTrue( result.get( 4 ) instanceof ConjunctiveLicenseSet );
        assertEquals( 3, ( (ConjunctiveLicenseSet) result.get( 4 ) ).getMembers().size() );
        assertTrue( result.get( 5 ) instanceof ExtractedLicenseInfo );
        assertEquals( LICENSE_REF_ID, ( (ExtractedLicenseInfo) result.get( 5 ) ).getLicenseId() );
        // Miss matched parens (should error)
        try
        {
            result = SpdxSourceFileParser.parseTextForSpdxLicenses( MISSMATCHED_PARENS );
            fail( "miss-matched parens did not fail like it should" );
        }
        catch ( SpdxSourceParserException ex )
        {
            //IGNORE - this is success
        }
        // Invalid expression (should error)
        try
        {
            result = SpdxSourceFileParser.parseTextForSpdxLicenses( INVALID_EXPRESSION );
            fail( "Invalid expression did not fail like it should" );
        }
        catch ( SpdxSourceParserException ex )
        {
            //IGNORE - this is success
        }
    }

}
