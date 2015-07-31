/*
 * Copyright 2014 The Apache Software Foundation.
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
 * Errors related to mapping Maven licenses to SPDX licenses
 * @author Gary O'Neall
 *
 */
public class LicenseMapperException
    extends Exception
{

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    /**
     * 
     */
    public LicenseMapperException()
    {
        // default constructor
    }

    /**
     * @param message
     */
    public LicenseMapperException( String message )
    {
        super( message );
    }

    /**
     * @param cause
     */
    public LicenseMapperException( Throwable cause )
    {
        super( cause );
    }

    /**
     * @param message
     * @param cause
     */
    public LicenseMapperException( String message, Throwable cause )
    {
        super( message, cause );
    }

}
