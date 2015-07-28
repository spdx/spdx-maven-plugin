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

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Dependency;
import org.spdx.rdfparser.model.Relationship;
import org.spdx.rdfparser.model.Relationship.RelationshipType;
import org.spdx.rdfparser.model.SpdxPackage;

/**
 * Contains information about package dependencies collected from the 
 * Maven dependencies.
 * @author Gary O'Neall
 *
 */
public class SpdxDependencyInformation
{
    
    private ArtifactRepository artifactRepository;

    /**
     * Create an empty dependency information object
     */
    public SpdxDependencyInformation() {
        this.artifactRepository = artifactRepository;
    }

    /**
     * Add information about a Maven dependency
     * @param dependency
     */
    public void addMavenDependency( Artifact dependency )
    {
        String scope = dependency.getScope();
        RelationshipType relType = scopeToRelationshipType( scope, dependency.isOptional() );
        SpdxPackage dependencyPackage = createSpdxPackage( dependency );
    }

    private RelationshipType scopeToRelationshipType( String scope, boolean optional )
    {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * Create an SPDX Document from a POM file stored in the Maven repository
     * @param groupId Maven group ID
     * @param classifier Maven classifier
     * @param version Maven version
     * @return
     */
    private SpdxPackage createSpdxPackage( Artifact artifact )
    {
        String filePath = this.artifactRepository.pathOf( artifact );
        return null;
    }

    public List<Relationship> getPackageRelationships()
    {
        // TODO Auto-generated method stub
        return new ArrayList<Relationship>();
    }

    public List<Relationship> getDocumentRelationships()
    {
        // TODO Auto-generated method stub
        return new ArrayList<Relationship>();
    }

}
