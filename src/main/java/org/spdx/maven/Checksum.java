/**
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Source Auditor Inc.
 */
package org.spdx.maven;

import java.util.Objects;

/**
 * Holds the value and algorithm of a checksum used in the SPDX document
 * 
 * @author Gary O'Neall
 *
 */
public class Checksum
{
    
    private String value;
    private String algorithm;
    
    /**
     * @param algorithm checksum algorithm
     * @param value checksum result
     */
    public Checksum( String algorithm, String value )
    {
        this.algorithm = algorithm;
        this.value = value;
    }

    /**
     * @return the value
     */
    public String getValue()
    {
        return value;
    }

    /**
     * @param value the value to set
     */
    public void setValue( String value )
    {
        this.value = value;
    }

    /**
     * @return the algorithm
     */
    public String getAlgorithm()
    {
        return algorithm;
    }

    /**
     * @param algorithm the algorithm to set
     */
    @SuppressWarnings("unused")
    public void setAlgorithm( String algorithm )
    {
        this.algorithm = algorithm;
    }
    
    @Override
    public boolean equals(Object o)
    {
        if ( !( o instanceof Checksum ) )
        {
            return false;
        }
        Checksum compare = (Checksum)o;
        return Objects.equals( compare.getAlgorithm(), this.getAlgorithm() ) &&
                        Objects.equals( compare.getValue(), this.getValue() );
    }
    
    @Override
    public int hashCode()
    {
        return ( Objects.isNull( algorithm ) ? 11 : algorithm.hashCode() ) ^ ( Objects.isNull( value ) ? 17 : value.hashCode() );
    }

}
