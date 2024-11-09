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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Simple class to hold an SPDX Annotation.
 *
 * @author Gary O'Neall
 * @see org.spdx.library.model.Annotation
 * @see AnnotationType
 */
public class Annotation
{
    private static final Logger LOG = LoggerFactory.getLogger( Annotation.class );

    /**
     * Tag value text description of the annotation type
     */
    private String annotationType;

    private String annotationDate;

    private String annotator;

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

    public void logInfo()
    {
        LOG.debug(
                "Annotator: " + this.annotator + ", Date: " + this.annotationDate + ", Type: " + this.annotationType );
    }
}