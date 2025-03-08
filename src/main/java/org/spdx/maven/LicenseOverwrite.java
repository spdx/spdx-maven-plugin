/*
 * Copyright 2025 Source Auditor Inc.
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

import org.apache.maven.project.MavenProject;

/**
 * Overwrite the license of a specific GAV coordinate.
 *
 * @author Jörg Sautter
 */
public class LicenseOverwrite
{

    private String target;

    private String groupId;

    private String artifactId;

    private String version;

    private String licenseString;

    /**
     * Create a default, license overwrite
     */
    public LicenseOverwrite()
    {

    }

    /**
     * @return the target to overwrite, one of: both, concluded, declared
     */
    public String getTarget()
    {
        return target;
    }

    /**
     * @return the groupId of the dependency to overwrite the license of
     */
    public String getGroupId()
    {
        return groupId;
    }

    /**
     * @return the artifactId of the dependency to overwrite the license of
     */
    public String getArtifactId()
    {
        return artifactId;
    }

    /**
     * @return the version of the dependency to overwrite the license of
     */
    public String getVersion()
    {
        return version;
    }

    /**
     * @return the license string to use for the matching dependencies
     */
    public String getLicenseString()
    {
        return licenseString;
    }

    public void setTarget( String target ) throws IllegalArgumentException
    {
        if ( !target.equals("both") && !target.equals("concluded") && !target.equals("declared") ) {
            throw new IllegalArgumentException( "license overwrite target is none of: both, concluded, declared" );
        }

        this.target = target;
    }

    public void setGroupId( String groupId )
    {
        this.groupId = groupId;
    }

    public void setArtifactId( String artifactId )
    {
        this.artifactId = artifactId;
    }

    public void setVersion( String version )
    {
        this.version = version;
    }

    public void setLicenseString( String licenseString )
    {
        this.licenseString = licenseString;
    }

    public boolean appliesTo( MavenProject mavenProject, String target )
    {
        if ( !target.equals("concluded")&& !target.equals("declared") ) {
            throw new IllegalArgumentException( "license overwrite target is none of: concluded, declared" );
        }

        if ( !mavenProject.getGroupId().equals( groupId ) ) {
            return false;
        } else if ( !mavenProject.getArtifactId().equals( artifactId ) ) {
            return false;
        } else if ( version != null && !mavenProject.getVersion().equals( version ) ) {
            return false;
        }

        return "both".equals( this.target ) || target.equals( this.target );
    }

    @Override
    public String toString() {
        return "LicenseOverwrite{" + "target=" + target + ", groupId=" + groupId 
                + ", artifactId=" + artifactId + ", version=" + version 
                + ", licenseString=" + licenseString + '}';
    }
}
