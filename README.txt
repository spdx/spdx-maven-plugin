NOTE: Currently this is a PROTOTYPE plugin for supporting SPDX in a Maven build.
 
Goal which creates a new SPDX file for the package being built.  Will replace
any existing SPDX file.

All SPDX document and SPDX package properties are supported as parameters
to the plugin.

File level data supports default parameters which are applied to all files.
Future versions of this plugin will support file specific parameters.

The treatment of licenses for Maven is somewhat involved.  Where possible,
SPDX standard licenses ID's should be used.  If no SPDX standard license
is available, a nonStandardLicense must be declared as a parameter including 
a unique license ID and the verbatim license text.

This project is licensed under the Apache 2.0 License

Remaining implementation work for 2.0:
Add document annotations - SpdxDocumentBuilder.java
Add document name - SpdxDocumentBuilder.java
Add package annotation - SpdxDocumentBuilder.java
Add document relationships - contains SpdxDocumentBuilder.java
add optional checksum types for package - SpdxDocumentBuilder.java
Add package relationships, annotations, others - dependencies SpdxDocumentBuilder.java
Add file annotation, relationships - SpdxFileCollector
Add optional checksums for file - SpdxFileCollector
Add new file types for 2.0 - SpdxFileCollector
Add file level specific information (override defaults) - SpdxFileCollector
Add dependencies as relationships
Support aggregation (multi-module)