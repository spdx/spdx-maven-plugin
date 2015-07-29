package org.spdx.maven;


import java.io.File;

import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

public class TestSpdxMojo
    extends AbstractMojoTestCase
{

    @AfterClass
    public static void tearDownAfterClass()
        throws Exception
    {
    }

    @Before
    protected void setUp()
        throws Exception
    {
        super.setUp();
    }

    @After
    protected void tearDown()
        throws Exception
    {
        super.tearDown();
    }

    @Test
    public void testExecute() throws Exception
    {
        File testPom = new File( getBasedir(),
                        "src/test/resources/unit/spdx-maven-plugin-test/pom.xml" );
               
        CreateSpdxMojo myMojo = new CreateSpdxMojo();
//        CreateSpdxMojo mojo = (CreateSpdxMojo) configureMojo( myMojo, "spdx-maven-plugin", testPom );
        CreateSpdxMojo mojo = (CreateSpdxMojo) lookupMojo( "createSPDX", testPom );
        assertNotNull( mojo );
        mojo.execute();
    }

}
