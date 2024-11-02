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
 * @author gary
 *
 */
public class SpdxV2DependencyInformation
    extends AbstractDependencyInformation
{

    /**
     * @param builer
     * @param createExternalRefs
     * @param generatePurls
     * @param useArtifactID
     * @param includeTransitiveDependencies
     */
    public SpdxV2DependencyInformation( SpdxV2DocumentBuilder builer, boolean createExternalRefs,
                                        boolean generatePurls, boolean useArtifactID,
                                        boolean includeTransitiveDependencies )
    {
        // TODO Auto-generated constructor stub
    }

    @Override
    public void addMavenDependencies( ProjectBuilder mavenProjectBuilder, MavenSession session,
                                      MavenProject mavenProject, DependencyNode parentNode, Object projectPackage )
    {
        // TODO Auto-generated method stub

    }

}
