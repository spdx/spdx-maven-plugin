/**
 * Regression test for: https://github.com/spdx/spdx-maven-plugin/issues/251
 *
 * This test verifies that configured license overrides are applied when executing `aggregateSPDX`.
 */

File spdxFile = new File( basedir, "target/site/org.spdx.it_aggregate-with-license-overwrite-1.0-SNAPSHOT.spdx.json" )

assert spdxFile.isFile()

String content = spdxFile.text
String marker = "\"referenceLocator\" : \"pkg:maven/commons-collections/commons-collections@3.2.2\""
int packageStart = content.indexOf( marker )

assert packageStart >= 0 : "Could not find commons-collections:3.2.2 in generated SPDX output"

String packageBlock = content.substring( packageStart, Math.min( content.length(), packageStart + 400 ) )

assert packageBlock.contains( "\"licenseDeclared\" : \"LicenseRef-TEST-LICENSE\"" ) :
    "Expected commons-collections:3.2.2 to have licenseDeclared LicenseRef-TEST-LICENSE, but block was:\n" + packageBlock
assert packageBlock.contains( "\"licenseConcluded\" : \"LicenseRef-TEST-LICENSE\"" ) :
    "Expected commons-collections:3.2.2 to have licenseConcluded LicenseRef-TEST-LICENSE, but block was:\n" + packageBlock

return true
