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

/**
 * Exceptions for the collection of SPDX information
 *
 * @author Gary O'Neall
 */
public class SpdxCollectionException extends Exception
{

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    /**
     *
     */
    public SpdxCollectionException()
    {
        super();
    }

    /**
     * @param message message
     */
    public SpdxCollectionException( String message )
    {
        super( message );
    }

    /**
     * @param cause inner exception
     */
    public SpdxCollectionException( Throwable cause )
    {
        super( cause );
    }

    /**
     * @param message message
     * @param cause   inner exception
     */
    public SpdxCollectionException( String message, Throwable cause )
    {
        super( message, cause );
    }
}
