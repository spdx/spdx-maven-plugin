/**
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Source Auditor Inc.
 */
package org.spdx.maven;

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
    public void setAlgorithm( String algorithm )
    {
        this.algorithm = algorithm;
    }
    
    

}
