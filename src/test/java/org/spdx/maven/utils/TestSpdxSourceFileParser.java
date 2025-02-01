package org.spdx.maven.utils;

import static org.junit.Assert.*;

import java.io.File;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.spdx.core.DefaultModelStore;
import org.spdx.core.DefaultStoreNotInitializedException;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.library.LicenseInfoFactory;
import org.spdx.library.ModelCopyManager;
import org.spdx.library.SpdxModelFactory;
import org.spdx.library.model.v2.license.InvalidLicenseStringException;
import org.spdx.library.model.v3_0_1.expandedlicensing.ConjunctiveLicenseSet;
import org.spdx.library.model.v3_0_1.expandedlicensing.CustomLicense;
import org.spdx.library.model.v3_0_1.expandedlicensing.DisjunctiveLicenseSet;
import org.spdx.library.model.v3_0_1.expandedlicensing.ListedLicense;
import org.spdx.library.model.v3_0_1.simplelicensing.AnyLicenseInfo;
import org.spdx.storage.simple.InMemSpdxStore;

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
    private static final String TEST_CLASS_FILE_NAME = "target/test-classes/unit/ClassWithManySpdxIDs.java";

    @Before
    public void setUp() throws Exception
    {
        SpdxModelFactory.init();
        DefaultModelStore.initialize(new InMemSpdxStore(), "http://default/namespace", new ModelCopyManager());
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
        List<String> result = SpdxSourceFileParser.parseFileForSpdxLicenses( javaFile );
        assertEquals( 3, result.size() );
        assertEquals( APACHE_LICENSE_ID, ( (ListedLicense) parseLic( result.get( 0 ) ) ).toString() );
        assertEquals( MIT_LICENSE_ID, ( (ListedLicense) parseLic( result.get( 1 ) ) ).toString() );
        assertTrue( parseLic( result.get( 2 ) ) instanceof DisjunctiveLicenseSet );
        assertEquals( 2, ( (DisjunctiveLicenseSet) parseLic( result.get( 2 ) ) ).getMembers().size() );
    }
    
    private AnyLicenseInfo parseLic( String expression ) throws InvalidLicenseStringException, DefaultStoreNotInitializedException
    {
        return LicenseInfoFactory.parseSPDXLicenseString( expression );
    }

    @Test
    public void testParseTextForSpdxLicenses() throws SpdxSourceParserException, InvalidSPDXAnalysisException
    {
        // Empty String
        List<String> result = SpdxSourceFileParser.parseTextForSpdxLicenses( EMPTY );
        assertEquals( 0, result.size() );
        // Simple single license ID
        result = SpdxSourceFileParser.parseTextForSpdxLicenses( SIMPLE );
        assertEquals( 1, result.size() );
        assertEquals( APACHE_LICENSE_ID, ( (ListedLicense) parseLic( result.get( 0 ) ) ).toString() );
        // LicenseRef conjunctive
        result = SpdxSourceFileParser.parseTextForSpdxLicenses( CONJUNCTIVE );
        assertEquals( 1, result.size() );
        assertTrue( parseLic( result.get( 0 ) ) instanceof ConjunctiveLicenseSet );
        assertEquals( 3, ( (ConjunctiveLicenseSet) parseLic( result.get( 0 ) ) ).getMembers().size() );
        // Single Line complex
        result = SpdxSourceFileParser.parseTextForSpdxLicenses( COMPLEX );
        assertEquals( 1, result.size() );
        assertTrue( parseLic( result.get( 0 ) ) instanceof DisjunctiveLicenseSet );
        assertEquals( 2, ( (DisjunctiveLicenseSet) parseLic( result.get( 0 ) ) ).getMembers().size() );
        // Multi Line complex
        result = SpdxSourceFileParser.parseTextForSpdxLicenses( COMPLEX_MULTI );
        assertEquals( 1, result.size() );
        assertTrue( parseLic( result.get( 0 ) ) instanceof DisjunctiveLicenseSet );
        assertEquals( 2, ( (DisjunctiveLicenseSet) parseLic( result.get( 0 ) ) ).getMembers().size() );
        // Multiple SPDX ID's simple
        result = SpdxSourceFileParser.parseTextForSpdxLicenses( MULTIPLE_SIMPLE_IDS );
        assertEquals( 2, result.size() );
        assertEquals( APACHE_LICENSE_ID, ( (ListedLicense) parseLic( result.get( 0 ) ) ).toString() );
        assertEquals( MIT_LICENSE_ID, ( (ListedLicense) parseLic( result.get( 1 ) ) ).toString() );
        // Multiple SPDX ID's complex
        result = SpdxSourceFileParser.parseTextForSpdxLicenses( MULTIPLE_COMPLEX_IDS );
        assertEquals( 6, result.size() );
        assertTrue( parseLic( result.get( 0 ) ) instanceof DisjunctiveLicenseSet );
        assertEquals( 2, ( (DisjunctiveLicenseSet) parseLic( result.get( 0 ) ) ).getMembers().size() );
        assertEquals( APACHE_LICENSE_ID, ( (ListedLicense) parseLic( result.get( 1 ) ) ).toString() );
        assertEquals( MIT_LICENSE_ID, ( (ListedLicense) parseLic( result.get( 2 ) ) ).toString() );
        assertTrue( parseLic( result.get( 3 ) ) instanceof DisjunctiveLicenseSet );
        assertEquals( 2, ( (DisjunctiveLicenseSet) parseLic( result.get( 3 ) ) ).getMembers().size() );
        assertTrue( parseLic( result.get( 4 ) ) instanceof ConjunctiveLicenseSet );
        assertEquals( 3, ( (ConjunctiveLicenseSet) parseLic( result.get( 4 ) ) ).getMembers().size() );
        assertTrue( parseLic( result.get( 5 ) ) instanceof CustomLicense );
        assertEquals( LICENSE_REF_ID, ( (CustomLicense) parseLic( result.get( 5 ) ) ).toString() );
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
    }

}
