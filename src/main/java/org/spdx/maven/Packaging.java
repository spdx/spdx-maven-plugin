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

import org.spdx.library.model.enumerations.Purpose;

/**
 * Packaging utility enum
 *
 * @author Kevin Conner
 */
public enum Packaging
{
    POM("pom", Purpose.INSTALL),
    EJB("ejb", Purpose.LIBRARY),
    JAR("jar", Purpose.LIBRARY),
    MAVEN_PLUGIN("maven-plugin", Purpose.LIBRARY),
    WAR("war", Purpose.APPLICATION),
    EAR("ear", Purpose.APPLICATION),
    RAR("rar", Purpose.OTHER);

    private String name;
    private Purpose purpose;

    private Packaging(final String name, final Purpose purpose)
    {
        this.name = name;
        this.purpose = purpose;
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

    public Purpose getPurpose()
    {
        return purpose;
    }
}
