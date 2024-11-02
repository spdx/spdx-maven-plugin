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
import org.spdx.jacksonstore.MultiFormatStore;
import org.spdx.jacksonstore.MultiFormatStore.Format;
import org.spdx.library.ModelCopyManager;
import org.spdx.library.model.v2.SpdxDocument;
import org.spdx.library.model.v2.SpdxModelFactoryCompatV2;
import org.spdx.library.model.v2.license.ExtractedLicenseInfo;
import org.spdx.maven.NonStandardLicense;
import org.spdx.maven.OutputFormat;
import org.spdx.spdxRdfStore.RdfStore;
import org.spdx.storage.simple.InMemSpdxStore;

/**
 * Builder for SPDX Spec version 2 SPDX Documents
 * 
 * @author Gary O'Neall
 *
 */
public class SpdxV2DocumentBuilder
    extends AbstractDocumentBuilder
{

    protected SpdxDocument spdxDoc;
    protected SpdxV2LicenseManager licenseManager;
    
    /**
     * @param mavenProject             Maven project
     * @param generatePurls            If true, generated Package URLs for all package references
     * @param spdxFile                 File to store the SPDX document results
     * @param spdxDocumentNamespace    SPDX Document namespace - must be unique
     * @param useStdLicenseSourceUrls  if true, map any SPDX standard license source URL to license ID.  Note:
     *                                 significant performance degradation 
     * @param outputFormatEnum
     */
    public SpdxV2DocumentBuilder( MavenProject mavenProject, boolean generatePurls, File spdxFile, URI spdxDocumentNamespace,
                                  boolean useStdLicenseSourceUrls, 
                                  OutputFormat outputFormatEnum ) throws SpdxBuilderException, LicenseMapperException
    {
        super( mavenProject, generatePurls, spdxFile, outputFormatEnum );
        if ( spdxDocumentNamespace == null )
        {
            throw new SpdxBuilderException( "Missing namespaceUri" );
        }
        
        // create the SPDX document
        try
        {
            modelStore = outputFormatEnum == OutputFormat.RDF_XML ? new RdfStore() :  new MultiFormatStore( new InMemSpdxStore(), Format.JSON_PRETTY );
            copyManager = new ModelCopyManager();
            spdxDoc = SpdxModelFactoryCompatV2.createSpdxDocumentV2( modelStore, spdxDocumentNamespace.toString(), copyManager );
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            throw new SpdxBuilderException( "Error creating SPDX document", e );
        }

        // process the licenses
        licenseManager = new SpdxV2LicenseManager( spdxDoc, useStdLicenseSourceUrls );
    }

    @Override
    public CoreModelObject getSpdxDoc()
    {
        return this.spdxDoc;
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
    public SpdxV2LicenseManager getLicenseManager()
    {
        // TODO Auto-generated method stub
        return null;
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
