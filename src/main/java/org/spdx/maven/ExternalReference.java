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
import org.spdx.rdfparser.InvalidSPDXAnalysisException;
import org.spdx.rdfparser.model.ExternalRef;
import org.spdx.rdfparser.model.ExternalRef.ReferenceCategory;
import org.spdx.rdfparser.referencetype.ListedReferenceTypes;
import org.spdx.rdfparser.referencetype.ReferenceType;

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

    public ExternalRef getExternalRef() throws MojoExecutionException
    {
        ReferenceCategory cat = ReferenceCategory.fromTag( category );
        if ( cat == null )
        {
            throw ( new MojoExecutionException(
                    "External reference category " + category + " is not recognized as a valid, standard category." ) );
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
        return new ExternalRef( cat, refType, locator, comment );
    }
}
