/*
 * Copyright 2023 Source Auditor Inc.
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

import java.io.File;

/**
 * OutputFormat utility enum
 *
 * @author Kevin Conner
 */
public enum OutputFormat
{
    RDF_XML("RDF/XML", "spdx.rdf.xml", ".rdf.xml"),
    JSON("JSON", "spdx.json", ".json");

    private final String value;
    private final String artifactType;
    private final String fileType;

    private OutputFormat(final String value, final String artifactType, final String fileType)
    {
        this.value = value;
        this.artifactType = artifactType;
        this.fileType = fileType;
    }

    public static OutputFormat getOutputFormat(final String format, final File file)
        throws IllegalArgumentException
    {
        if (format == null)
        {
            if (file != null)
            {
                return file.getName().toLowerCase().endsWith(".rdf.xml") ? RDF_XML : JSON;
            }
            throw new IllegalArgumentException("Could not determine output file");
        }
        final String upperCaseFormat = format.toUpperCase();
        if (RDF_XML.value.equals(upperCaseFormat))
        {
            return RDF_XML;
        }
        else if (JSON.value.equals(upperCaseFormat))
        {
            return JSON;
        }
        throw new IllegalArgumentException("Invalid SPDX output format: " + format);
    }

    public String getArtifactType()
    {
        return artifactType;
    }

    public String getFileType()
    {
        return fileType;
    }
}
