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