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
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.model.SpdxDocument;
import org.spdx.library.model.enumerations.AnnotationType;


/**
 * Represents an SPDX Annotation class in a Maven POM file
 *
 * @author Gary O'Neall
 */
public class Annotation
{

    /**
     * Tag value text description of the annotation type
     */
    @Parameter( required = true )
    private String annotationType;
    @Parameter( required = true )
    private String annotationDate;
    @Parameter( required = true )
    private String annotator;
    @Parameter( required = true )
    private String annotationComment;

    /**
     * Create a default (empty) annotation
     */
    public Annotation()
    {

    }

    /**
     * @return the annotationType
     */
    public String getAnnotationType()
    {
        return annotationType;
    }


    /**
     * @param annotationType the annotationType to set
     */
    public void setAnnotationType( String annotationType )
    {
        this.annotationType = annotationType;
    }


    /**
     * @return the annotationDate
     */
    public String getAnnotationDate()
    {
        return annotationDate;
    }


    /**
     * @param annotationDate the annotationDate to set
     */
    public void setAnnotationDate( String annotationDate )
    {
        this.annotationDate = annotationDate;
    }


    /**
     * @return the annotator
     */
    public String getAnnotator()
    {
        return annotator;
    }


    /**
     * @param annotator the annotator to set
     */
    public void setAnnotator( String annotator )
    {
        this.annotator = annotator;
    }


    /**
     * @return the annotationComment
     */
    public String getAnnotationComment()
    {
        return annotationComment;
    }


    /**
     * @param annotationComment the annotationComment to set
     */
    public void setAnnotationComment( String annotationComment )
    {
        this.annotationComment = annotationComment;
    }


    /**
     * @param spdxDoc SPDX document which will contain the annotation
     * @return an SPDX model version of the annotation
     */
    public org.spdx.library.model.Annotation toSpdxAnnotation( SpdxDocument spdxDoc ) throws MojoExecutionException
    {
        AnnotationType annotationType = AnnotationType.OTHER;
        try
        {
            annotationType = AnnotationType.valueOf( this.annotationType );
        }
        catch ( Exception ex )
        {
            throw new MojoExecutionException( "Invalid annotation type "+this.annotationType );
        }
        try
        {
            return spdxDoc.createAnnotation( this.annotator, 
                                             annotationType,
                                             annotationDate,
                                             annotationComment );
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            throw new MojoExecutionException( "Error creating annotation.", e );
        }
    }

    public void logInfo( Log log )
    {
        log.debug(
                "Annotator: " + this.annotator + ", Date: " + this.annotationDate + ", Type: " + this.annotationType );
    }
}