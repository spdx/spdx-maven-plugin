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
package org.spdx.maven.utils;

import org.apache.maven.plugin.MojoExecutionException;

/**
 * Exceptions relating to the building of SPDX Documents
 *
 * @author Gary O'Neall
 */
public class SpdxBuilderException extends MojoExecutionException
{

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    /**
     * @param message message
     */
    public SpdxBuilderException( String message )
    {
        super( message );
    }

    /**
     * @param message message
     * @param cause   inner exception
     */
    public SpdxBuilderException( String message, Throwable cause )
    {
        super( message, cause );
    }


}
