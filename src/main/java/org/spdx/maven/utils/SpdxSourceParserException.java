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
     * @param arg0
     */
    public SpdxSourceParserException( String arg0 )
    {
        super( arg0 );
    }

    /**
     * @param arg0
     */
    public SpdxSourceParserException( Throwable arg0 )
    {
        super( arg0 );
    }

    /**
     * @param arg0
     * @param arg1
     */
    public SpdxSourceParserException( String arg0, Throwable arg1 )
    {
        super( arg0, arg1 );
    }

    /**
     * @param arg0
     * @param arg1
     * @param arg2
     * @param arg3
     */
    public SpdxSourceParserException( String arg0, Throwable arg1, boolean arg2, boolean arg3 )
    {
        super( arg0, arg1, arg2, arg3 );
    }

}
