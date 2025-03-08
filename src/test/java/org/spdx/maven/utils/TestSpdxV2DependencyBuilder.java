package org.spdx.maven.utils;

import static org.junit.Assert.*;

import java.io.File;
import java.net.URI;
import java.util.Optional;
import org.apache.maven.project.MavenProject;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.spdx.core.DefaultModelStore;
import org.spdx.library.LicenseInfoFactory;
import org.spdx.library.ModelCopyManager;
import org.spdx.library.SpdxModelFactory;
import org.spdx.library.model.v2.SpdxDocument;
import org.spdx.library.model.v2.license.AnyLicenseInfo;
import org.spdx.library.model.v2.license.ExtractedLicenseInfo;
import org.spdx.library.model.v2.license.SpdxListedLicense;
import org.spdx.maven.LicenseOverwrite;
import org.spdx.maven.NonStandardLicense;
import org.spdx.maven.OutputFormat;
import org.spdx.storage.simple.InMemSpdxStore;

/**
 *
 * @author Jörg Sautter
 */
public class TestSpdxV2DependencyBuilder
{

    /**
     * @throws java.lang.Exception
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception
    {
        SpdxModelFactory.init();
    }

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception
    {
        DefaultModelStore.initialize( new InMemSpdxStore(), "http://default/namespace", new ModelCopyManager() );
    }

    @Test
    public void testMavenLicenseOverwrite() throws Exception
    {
        MavenProject mavenProject = new MavenProject();
        mavenProject.setGroupId( "org.spdx.maven.utils" );
        mavenProject.setArtifactId( "testMavenLicenseOverwrite" );
        mavenProject.setVersion( "1.2.0-SNAPSHOT" );

        LicenseOverwrite licenseOverwrite = new LicenseOverwrite();
        licenseOverwrite.setTarget( "both" );
        licenseOverwrite.setGroupId( mavenProject.getGroupId() );
        licenseOverwrite.setArtifactId( mavenProject.getArtifactId() );
        licenseOverwrite.setLicenseString( "Apache-2.0" );

        Optional<AnyLicenseInfo> withoutVersion = applyLicenseOverwrites( mavenProject, licenseOverwrite );

        assertEquals( "Apache-2.0", ((SpdxListedLicense) withoutVersion.get()).getId() );

        licenseOverwrite.setVersion(mavenProject.getVersion());

        Optional<AnyLicenseInfo> withMatchingVersion = applyLicenseOverwrites( mavenProject, licenseOverwrite );

        assertEquals( "Apache-2.0", ((SpdxListedLicense) withMatchingVersion.get()).getId() );

        licenseOverwrite.setVersion("1.2.1-SNAPSHOT");

        Optional<AnyLicenseInfo> withVersionMissmatch = applyLicenseOverwrites( mavenProject, licenseOverwrite );

        assertTrue(withVersionMissmatch.isEmpty());
    }

    @Test
    public void testMavenLicenseOverwriteWithCustomLicense() throws Exception {
        MavenProject mavenProject = new MavenProject();
        mavenProject.setGroupId( "org.spdx.maven.utils" );
        mavenProject.setArtifactId( "testMavenLicenseOverwrite" );
        mavenProject.setVersion( "1.2.0-SNAPSHOT" );

        LicenseOverwrite licenseOverwrite = new LicenseOverwrite();
        licenseOverwrite.setTarget( "both" );
        licenseOverwrite.setGroupId( mavenProject.getGroupId() );
        licenseOverwrite.setArtifactId( mavenProject.getArtifactId() );
        licenseOverwrite.setLicenseString( "LicenseRef-My-Custom" );

        NonStandardLicense nonStandardLicense = new NonStandardLicense();
        nonStandardLicense.setLicenseId( "LicenseRef-My-Custom" );
        nonStandardLicense.setComment( "My fancy license" );
        nonStandardLicense.setExtractedText( "My fancy license text" );

        Optional<AnyLicenseInfo> customLicense = applyLicenseOverwrites( mavenProject, licenseOverwrite, nonStandardLicense );

        assertEquals( "LicenseRef-My-Custom", ((ExtractedLicenseInfo) customLicense.get()).getId() );
        assertEquals( "My fancy license", ((ExtractedLicenseInfo) customLicense.get()).getComment() );
        assertEquals( "My fancy license text", ((ExtractedLicenseInfo) customLicense.get()).getExtractedText() );
    }

    public Optional<AnyLicenseInfo> applyLicenseOverwrites( MavenProject mavenProject, LicenseOverwrite licenseOverwrite ) throws Exception {
        return applyLicenseOverwrites( mavenProject, licenseOverwrite, null );
    }

    public Optional<AnyLicenseInfo> applyLicenseOverwrites( MavenProject mavenProject, LicenseOverwrite licenseOverwrite, NonStandardLicense nonStandardLicense ) throws Exception {
        URI namespaceUri = URI.create( "http://spdx.org/spdxpackages/" + mavenProject.getGroupId() + "_" + mavenProject.getArtifactId() + "-" + mavenProject.getVersion() );
        File spdxFile = File.createTempFile( "testMavenLicenseOverwrite", ".spdx" );

        SpdxV2DocumentBuilder documentBuilder = new SpdxV2DocumentBuilder(
                mavenProject, false, spdxFile, namespaceUri, OutputFormat.JSON );
        SpdxV2DependencyBuilder dependencyBuilder = new SpdxV2DependencyBuilder(
                documentBuilder, true, true, true, true );
        SpdxDocument spdxDoc = documentBuilder.getSpdxDoc();
        
        SpdxV2LicenseManager licenseManager = documentBuilder.getLicenseManager();
        if (nonStandardLicense != null)
        {
            licenseManager.addExtractedLicense( nonStandardLicense );
        }

        AnyLicenseInfo parsedLicense = LicenseInfoFactory.parseSPDXLicenseStringCompatV2( 
                licenseOverwrite.getLicenseString(), spdxDoc.getModelStore(),
                spdxDoc.getDocumentUri(), spdxDoc.getCopyManager() );

        dependencyBuilder.addLicenseOverwrite( licenseOverwrite, parsedLicense );
        
        return dependencyBuilder.applyLicenseOverwrites( mavenProject, "concluded" );
    }
}

