/**
 * Regression test for: https://github.com/spdx/spdx-maven-plugin/issues/239
 *
 * This test verifies that dependencies with 'test' scope are excluded when
 * includeTestScope is set to false in the aggregateSPDX goal.
 */

File spdxFile = new File(basedir, "target/site/org.spdx.it_simple-it-1.0-SNAPSHOT.spdx.json")

println "Checking for SPDX file: " + spdxFile.absolutePath

assert spdxFile.isFile()

// Use a simple string search to check for the presence of junit
// In a real scenario, we'd parse the JSON, but since we expect failure
// and the presence of "junit" is what we're looking for, this is sufficient.
String content = spdxFile.text

// junit:junit is a test dependency in the IT's pom.xml
// We have <includeTestScope>false</includeTestScope> in the configuration.
// So junit should NOT be present.
if (content.contains("junit")) {
    throw new AssertionError("Found the 'junit' dependency in the aggregate SPDX file while <includeTestScope> is was set to false in the POM file .");
}

return true
