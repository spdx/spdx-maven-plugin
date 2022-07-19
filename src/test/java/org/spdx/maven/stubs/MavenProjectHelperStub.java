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
package org.spdx.maven.stubs;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import org.apache.maven.project.DefaultMavenProjectHelper;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.PlexusTestCase;

/**
 * Testing stub for MavenProjectHelper
 *
 * @author Gary O'Neall
 */
public class MavenProjectHelperStub extends DefaultMavenProjectHelper
{


    @Override
    public void attachArtifact( MavenProject project, String artifactType, File file )
    {
        String outputFileName = project.getArtifactId() + "." + artifactType;
        File outputFile = new File( getBaseDir(), outputFileName );
        if ( outputFile.exists() )
        {
            outputFile.delete();
        }
        try ( InputStream is = new FileInputStream(file ) )
        {
            Files.copy( is, outputFile.toPath() );
        }
        catch ( IOException e )
        {
            throw new RuntimeException( e );
        }
    }

    public File getBaseDir()
    {
        return new File( PlexusTestCase.getBasedir() + "/src/test/resources/unit/spdx-maven-plugin-test/" );
    }

}
