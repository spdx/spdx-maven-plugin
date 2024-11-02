/**
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Source Auditor Inc.
 */
package org.spdx.maven.utils;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.apache.maven.model.License;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;
import org.spdx.core.CoreModelObject;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.library.model.v3_0_1.SpdxModelClassFactoryV3;
import org.spdx.library.model.v3_0_1.core.CreationInfo;
import org.spdx.library.model.v3_0_1.core.SpdxDocument;
import org.spdx.library.model.v3_0_1.software.Sbom;
import org.spdx.maven.NonStandardLicense;
import org.spdx.maven.OutputFormat;
import org.spdx.storage.simple.InMemSpdxStore;
import org.spdx.v3jsonldstore.JsonLDStore;

/**
 * Builder for SPDX Spec version 3 SBOMs
 * 
 * @author Gary O'Neall
 *
 */
public class SpdxV3DocumentBuilder
    extends AbstractDocumentBuilder
{
    protected CreationInfo creationInfo;
    protected Sbom sbom;
    protected SpdxDocument spdxDoc;
    protected SpdxV3LicenseManager licenseManager;

    /**
     * @param mavenProject             Maven project
     * @param generatePurls            If true, generated Package URLs for all package references
     * @param spdxFile                 File to store the SPDX document results
     * @param namespaceUri             Namspace prefix for generated SPDX URIs document - must be unique
     * @param useStdLicenseSourceUrls  if true, map any SPDX standard license source URL to license ID.  Note:
     *                                 significant performance degradation 
     * @param outputFormatEnum
     */
    public SpdxV3DocumentBuilder( MavenProject mavenProject, boolean generatePurls, File spdxFile, URI namespaceUri,
                                  boolean useStdLicenseSourceUrls, 
                                  OutputFormat outputFormatEnum ) throws SpdxBuilderException, LicenseMapperException
    {
        super( mavenProject, generatePurls, spdxFile, outputFormatEnum );
        if ( namespaceUri == null )
        {
            throw new SpdxBuilderException( "Missing namespaceUri" );
        }
        
        if ( !OutputFormat.JSON_LD.equals( outputFormatEnum )) {
            throw new SpdxBuilderException( String.format( "Unsupported output format for SPDX spec version 3: %s",
                                                           outputFormatEnum.toString() ));
        }
        // create the SPDX document
        try
        {
            modelStore = new JsonLDStore( new InMemSpdxStore() );
            String supplier = ( mavenProject.getOrganization() != null && 
                            mavenProject.getOrganization().getName() != null 
                            && !mavenProject.getOrganization().getName().isEmpty() ) ? mavenProject.getOrganization().getName() : project.getName() ;

            creationInfo = SpdxModelClassFactoryV3.createCreationInfo( modelStore, namespaceUri + "Agent/supplier", supplier,
                                                                       copyManager);
            creationInfo.getCreatedUsings().add( creationInfo.createTool( namespaceUri + "Agent/SpdxMavenPlugin" )
                                                 .setName( "SPDX Maven Plugin" )
                                                 .setCreationInfo( creationInfo )
                                                 .build() );
            sbom = creationInfo.createSbom( namespaceUri + "sbom" ).build();
            spdxDoc = sbom.createSpdxDocument( namespaceUri + "/Document" )
                            .addRootElement( sbom )
                            .build();
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            throw new SpdxBuilderException( "Error creating SPDX SBOM", e );
        }

        // process the licenses
        licenseManager = new SpdxV3LicenseManager( spdxDoc, useStdLicenseSourceUrls );
    }

    @Override
    public CoreModelObject getSpdxDoc()
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void fillSpdxDocumentInformation( SpdxProjectInformation projectInformation )
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void collectSpdxFileInformation( List<FileSet> sources, String absolutePath,
                                            SpdxDefaultFileInformation defaultFileInformation,
                                            HashMap<String, SpdxDefaultFileInformation> pathSpecificInformation,
                                            Set<String> checksumAlgorithms )
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void addDependencyInformation( AbstractDependencyInformation dependencyInformation )
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void saveSpdxDocumentToFile()
    {
        // TODO Auto-generated method stub

    }

    @Override
    public void addNonStandardLicenses( NonStandardLicense[] nonStandardLicenses )
    {
        // TODO Auto-generated method stub

    }

    @Override
    public Object getProjectPackage()
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Object createChecksum( String algorithm, String checksum )
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String mavenLicenseListToSpdxLicenseExpression( List<License> mavenLicenses )
    {
        // TODO Auto-generated method stub
        return null;
    }

}
