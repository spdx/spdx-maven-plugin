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

/**
 * An External Reference allows a Package to reference an external source of additional information, metadata,
 * enumerations, asset identifiers, or downloadable content believed to be relevant to the Package.
 *
 * @author Gary O'Neall
 *
 */
@SuppressWarnings("unused")
public class ExternalReference
{
    private String category;

    private String type;

    private String locator;

    private String comment;

    /**
     * @return the category
     */
    public String getCategory()
    {
        return category;
    }

    /**
     * @return the type
     */
    public String getType()
    {
        return type;
    }

    /**
     * @return the locator
     */
    public String getLocator()
    {
        return locator;
    }

    /**
     * @return the comment
     */
    public String getComment()
    {
        return comment;
    }
}
