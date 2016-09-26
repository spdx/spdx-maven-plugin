SPDX Maven Plugin is a plugin to Maven which produces Software Package Data Exchange (SPDX) documents for artifacts described in the POM file.
See http://spdx.org for information on SPDX.
# Goal Overview
createSPDX creates an SPDX document for artifacts defined in the POM file.  Will replace any exsiting SPDX documents.

# Usage
In the build plugins section add the plugin with a goal of createSPDX:
```xml
                <plugin>
                    <groupId>org.spdx</groupId>
                    <artifactId>spdx-maven-plugin</artifactId>
                    <version>0.2.3-SNAPSHOT</version>
                    <executions>
                        <execution>
                            <id>build-spdx</id>
                            <goals>
                                <goal>createSPDX</goal>
                            </goals>
                        </execution>
                    </executions>
                    <configuration>
                      <excludedFilePatterns>
                        <param>*.spdx</param>
                      </excludedFilePatterns>
                      <!-- See documentation below for additional configuration -->
                    </configuration>
                </plugin>
```

Then invoke with *mvn spdx:createSPDX* and your spdx file will be generated in *target/site/{artifactId}-{version}.spdx*.

# Additional Configuration

All SPDX document and SPDX package properties are supported.  Some of the properties
are taken from existing POM properties while others are specified in the configuration
section.

File level data supports default parameters which are applied to all files.

File specific parameters can be specified in the configuration parameter pathsWithSpecificSpdxInfo which
includes a directoryOrFile configuration parameter in addition to the SPDX file level
parameters. 

A mapping of POM properties and configuration parameters can be found in the spreadsheet
SPDX-fields-maven-mapping.xlsx.

The treatment of licenses for Maven is somewhat involved.  Where possible,
SPDX standard licenses ID's should be used.  If no SPDX standard license
is available, a nonStandardLicense must be declared as a parameter including 
a unique license ID and the verbatim license text.

# Example
See the file SpdxTools-POM-FIle-Example.xml for an example project using the spdx-maven-plugin.

# License
This project is licensed under the Apache 2.0 License
