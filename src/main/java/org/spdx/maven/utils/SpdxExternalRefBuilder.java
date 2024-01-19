package org.spdx.maven.utils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spdx.library.InvalidSPDXAnalysisException;
import org.spdx.library.model.ExternalRef;
import org.spdx.library.model.ReferenceType;
import org.spdx.library.model.SpdxDocument;
import org.spdx.library.model.enumerations.ReferenceCategory;

public class SpdxExternalRefBuilder
{

  private static final Logger LOG = LoggerFactory.getLogger( SpdxExternalRefBuilder.class );

  public static Collection<ExternalRef> getDefaultExternalRefs( SpdxDocument spdxDoc, boolean generatePurls, MavenProject project ) {
    ExternalRef generatedPurlExternalRef = generatePurls ? generatePurlExternalRef( spdxDoc, project ) : null;
    return generatedPurlExternalRef == null ? List.of() : List.of( generatedPurlExternalRef );
  }

  private static ExternalRef generatePurlExternalRef( SpdxDocument spdxDoc, MavenProject project )
  {
    try
    {
      return spdxDoc.createExternalRef( ReferenceCategory.PACKAGE_MANAGER, new ReferenceType("http://spdx.org/rdf/references/purl"),
                                        generatePurl( project ), null );
    }
    catch ( InvalidSPDXAnalysisException e )
    {
      LOG.warn( "Invalid reference type \"purl\" for generated purl external ref");
      return null;
    }
  }

  private static String generatePurl( MavenProject project )
  {
    return "pkg:maven/" + project.getGroupId() + "/"
        + URLEncoder.encode( project.getArtifactId(), StandardCharsets.UTF_8 )
        + "@" + project.getVersion();
  }

}
