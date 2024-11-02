/**
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Source Auditor Inc.
 */
package org.spdx.maven.utils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.apache.maven.model.License;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;
import org.spdx.core.CoreModelObject;
import org.spdx.library.ModelCopyManager;
import org.spdx.maven.NonStandardLicense;
import org.spdx.maven.OutputFormat;
import org.spdx.storage.IModelStore;

/**
 * Abstract class to create SPDX documents.
 * 
 * Subclasses of this class implement specific SPDX specification versions of the document
 * 
 * @author Gary O'Neall
 */
public abstract class AbstractDocumentBuilder
{
    
    protected MavenProject project;
    protected boolean generatePurls;
    protected File spdxFile;
    protected OutputFormat outputFormatEnum;
    protected boolean matchLicensesOnCrossReferenceUrls;
    protected IModelStore modelStore;
    protected ModelCopyManager copyManager;

    /**
     * 
     */
    public AbstractDocumentBuilder()
    {
        // TODO Auto-generated constructor stub
    }

    /**
     * @param project                  Maven Project                      
     * @param generatePurls            If true, generated Package URLs for all package references
     * @param spdxFile                 File to store the SPDX document results
     * @param outputFormatEnum         File format for the SPDX file
     * @throws SpdxBuilderException    On errors building the document
     */
    public AbstractDocumentBuilder( MavenProject project, boolean generatePurls, File spdxFile,
                                    OutputFormat outputFormatEnum ) throws SpdxBuilderException
    {
        this.project = project;
        this.generatePurls = generatePurls;
        this.spdxFile = spdxFile;
        this.outputFormatEnum = outputFormatEnum;
        copyManager = new ModelCopyManager();
        
        // Handle the SPDX file
        if ( !spdxFile.exists() )
        {
            File parentDir = spdxFile.getParentFile();
            if ( parentDir != null && !parentDir.exists() )
            {
                if ( !parentDir.mkdirs() )
                {
                    throw new SpdxBuilderException( "Unable to create directories for SPDX file" );
                }
            }

            try
            {
                if ( !spdxFile.createNewFile() )
                {
                    throw new SpdxBuilderException( "Unable to create the SPDX file" );
                }
            }
            catch ( IOException e )
            {
                throw new SpdxBuilderException( "IO error creating the SPDX file", e );
            }
        }
        if ( !spdxFile.canWrite() )
        {
            throw new SpdxBuilderException( "Unable to write to SPDX file - check permissions: " + spdxFile.getPath() );
        }
        
    }

    /**
     * @return
     */
    public abstract CoreModelObject getSpdxDoc();

    /**
     * @param projectInformation
     */
    public abstract void fillSpdxDocumentInformation( SpdxProjectInformation projectInformation );

    /**
     * @param sources
     * @param absolutePath
     * @param defaultFileInformation
     * @param pathSpecificInformation
     * @param checksumAlgorithms
     */
    public abstract void collectSpdxFileInformation( List<FileSet> sources, String absolutePath,
                                                        SpdxDefaultFileInformation defaultFileInformation,
                                                        HashMap<String, SpdxDefaultFileInformation> pathSpecificInformation,
                                                        Set<String> checksumAlgorithms );

    /**
     * @param dependencyInformation
     */
    public abstract void addDependencyInformation( AbstractDependencyInformation dependencyInformation );

    /**
     * 
     */
    public abstract void saveSpdxDocumentToFile();

    /**
     * @param nonStandardLicenses
     */
    public abstract void addNonStandardLicenses( NonStandardLicense[] nonStandardLicenses ) throws SpdxBuilderException;

    /**
     * @return
     */
    public abstract Object getProjectPackage();

    /**
     * @param algorithm
     * @param checksum
     * @return
     */
    public abstract Object createChecksum( String algorithm, String checksum );

    /**
     * @param mavenLicenses
     * @return
     */
    public abstract String mavenLicenseListToSpdxLicenseExpression( List<License> mavenLicenses );

}
