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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.model.ExternalRef;
import org.spdx.library.model.ReferenceType;
import org.spdx.library.model.SpdxDocument;
import org.spdx.library.model.enumerations.ReferenceCategory;
import org.spdx.library.referencetype.ListedReferenceTypes;

/**
 * An External Reference allows a Package to reference an external source of additional information, metadata,
 * enumerations, asset identifiers, or downloadable content believed to be relevant to the Package.
 *
 * @author Gary O'Neall
 */
public class ExternalReference
{
    @Parameter( required = true )
    private String category;

    @Parameter( required = true )
    private String type;

    @Parameter( required = true )
    private String locator;

    @Parameter( required = false )
    private String comment;

    public ExternalRef getExternalRef( SpdxDocument spdxDoc ) throws MojoExecutionException
    {
        ReferenceCategory cat = null;
        
        try {
            cat = ReferenceCategory.valueOf( category.replaceAll( "-", "_" ) );
        }
        catch ( Exception ex )
        {
            throw ( new MojoExecutionException("External reference category " + category + " is not recognized as a valid, standard category." ) );
        }
        ReferenceType refType = null;
        try
        {
            refType = ListedReferenceTypes.getListedReferenceTypes().getListedReferenceTypeByName( type );
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            throw ( new MojoExecutionException( "Error getting listed reference type for " + type, e ) );
        }
        if ( refType == null )
        {
            throw ( new MojoExecutionException( "Listed reference type not found for " + type ) );
        }
        try
        {
            return spdxDoc.createExternalRef( cat, refType, locator, comment );
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            throw ( new MojoExecutionException( "Error creating External Reference: "+e.getMessage()));
        }
    }
}
