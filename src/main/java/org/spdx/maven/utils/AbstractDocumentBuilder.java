/**
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Source Auditor Inc.
 */
package org.spdx.maven.utils;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.apache.maven.model.License;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;
import org.spdx.core.CoreModelObject;
import org.spdx.library.ModelCopyManager;
import org.spdx.library.model.v2.SpdxConstantsCompatV2;
import org.spdx.maven.NonStandardLicense;
import org.spdx.maven.OutputFormat;
import org.spdx.storage.ISerializableModelStore;

/**
 * Abstract class to create SPDX documents.
 * <p>
 * Subclasses of this class implement specific SPDX specification versions of the document
 * 
 * @author Gary O'Neall
 */
public abstract class AbstractDocumentBuilder
{
    protected static final String UNSPECIFIED = "UNSPECIFIED";
    public static final String NULL_SHA1 = "cf23df2207d99a74fbe169e3eba035e633b65d94";
    
    protected MavenProject project;
    protected boolean generatePurls;
    protected File spdxFile;
    protected OutputFormat outputFormatEnum;
    protected ISerializableModelStore modelStore;
    protected ModelCopyManager copyManager;
    protected DateFormat format = new SimpleDateFormat( SpdxConstantsCompatV2.SPDX_DATE_FORMAT );
    

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
     * @param projectInformation Information about project extracted from Maven metadata and parameters
     * @throws SpdxBuilderException on errors adding document level information
     */
    public abstract void fillSpdxDocumentInformation( SpdxProjectInformation projectInformation ) throws SpdxBuilderException;

    /**
     * Collect information at the file level, fill in the SPDX document
     *
     * @param sources                     Source directories to be included in the document
     * @param baseDir                     project base directory used to construct the relative paths for the SPDX
     *                                    files
     * @param pathSpecificInformation     Map of path to file information used to override the default file information
     * @param checksumAlgorithms          algorithms to use to generate checksums
     * @throws SpdxBuilderException       on errors collecting files
     */
    public abstract void collectSpdxFileInformation( List<FileSet> sources, String baseDir,
                                                        SpdxDefaultFileInformation defaultFileInformation,
                                                        HashMap<String, SpdxDefaultFileInformation> pathSpecificInformation,
                                                        Set<String> checksumAlgorithms ) throws SpdxBuilderException;

    /**
     * Saves the SPDX document to the file
     * @throws SpdxBuilderException On any error saving the file
     * 
     */
    public abstract void saveSpdxDocumentToFile() throws SpdxBuilderException;

    /**
     * @param nonStandardLicenses non standard licenses to add
     */
    public abstract void addNonStandardLicenses( NonStandardLicense[] nonStandardLicenses ) throws SpdxBuilderException;

    /**
     * @return package representing the Mave project
     */
    public abstract CoreModelObject getProjectPackage();

    /**
     * @param  mavenLicenses list of licenses 
     * @return license expression representing the list of mavenLicenses
     * @throws LicenseManagerException On error converting license
     */
    public abstract String mavenLicenseListToSpdxLicenseExpression( List<License> mavenLicenses ) throws LicenseManagerException;

    /**
     * Verifies the top level document
     * @return list of any errors or warnings
     */
    public abstract List<String> verify();

}
