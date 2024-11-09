/*
 * Copyright 2023 Source Auditor Inc.
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

import org.spdx.library.model.v2.enumerations.Purpose;
import org.spdx.library.model.v3_0_1.software.SoftwarePurpose;

/**
 * Packaging utility enum
 *
 * @author Kevin Conner
 */
public enum Packaging
{
    POM("pom", Purpose.INSTALL, SoftwarePurpose.INSTALL),
    EJB("ejb", Purpose.LIBRARY, SoftwarePurpose.LIBRARY),
    JAR("jar", Purpose.LIBRARY, SoftwarePurpose.LIBRARY),
    MAVEN_PLUGIN("maven-plugin", Purpose.LIBRARY, SoftwarePurpose.LIBRARY),
    WAR("war", Purpose.APPLICATION, SoftwarePurpose.APPLICATION),
    EAR("ear", Purpose.APPLICATION, SoftwarePurpose.APPLICATION),
    RAR("rar", Purpose.OTHER, SoftwarePurpose.ARCHIVE);

    private final String name;
    private final Purpose v2Purpose;
    private final SoftwarePurpose softwarePurpose;

    private Packaging(final String name, final Purpose v2purpose, final SoftwarePurpose v3softwarePurpose)
    {
        this.name = name;
        this.v2Purpose = v2purpose;
        this.softwarePurpose = v3softwarePurpose;
    }

    public static Packaging valueOfPackaging(String packagingValue)
    {
        packagingValue = packagingValue.toLowerCase();
        {
            for (Packaging packaging: values())
            {
                if (packaging.name.equals(packagingValue))
                {
                    return packaging;
                }
            }
        }
        return null;
    }

    public String getName()
    {
        return name;
    }

    public Purpose getV2Purpose()
    {
        return v2Purpose;
    }

    /**
     * @return the softwarePurpose
     */
    public SoftwarePurpose getSoftwarePurpose()
    {
        return softwarePurpose;
    }
}
