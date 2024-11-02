/**
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Source Auditor Inc.
 */
package org.spdx.maven.utils;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.shared.dependency.graph.DependencyNode;

/**
 * Contains information about package dependencies collected from the Maven dependencies.
 * 
 * Subclasses implement dependency information specific to SPDX spec major versions
 * 
 * @author Gary O'Neall
 *
 */
public abstract class AbstractDependencyInformation
{

    /**
     * 
     */
    public AbstractDependencyInformation()
    {
        // TODO Auto-generated constructor stub
    }

    /**
     * @param mavenProjectBuilder
     * @param session
     * @param mavenProject
     * @param parentNode
     * @param projectPackage
     */
    public abstract void addMavenDependencies( ProjectBuilder mavenProjectBuilder, MavenSession session,
                                                  MavenProject mavenProject, DependencyNode parentNode,
                                                  Object projectPackage );

}
