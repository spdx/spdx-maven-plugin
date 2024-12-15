/**
 *
 */
package org.spdx.maven.utils;

/**
 * Exceptions related to parsing source files for SPDX identifiers
 *
 * @author Gary O'Neall
 *
 */
public class SpdxSourceParserException extends Exception
{

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    /**
     * @param msg message
     */
    public SpdxSourceParserException( String msg )
    {
        super( msg );
    }

    /**
     * @param cause inner exception
     */
    public SpdxSourceParserException( Throwable cause )
    {
        super( cause );
    }

    /**
     * @param msg message
     * @param cause inner exception
     */
    public SpdxSourceParserException( String msg, Throwable cause )
    {
        super( msg, cause );
    }

}
