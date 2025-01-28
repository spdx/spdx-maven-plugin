package org.spdx.maven.utils;

import java.util.Collection;
import java.util.List;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.library.model.v3_0_1.core.ExternalIdentifier;
import org.spdx.library.model.v3_0_1.core.ExternalIdentifierType;
import org.spdx.library.model.v3_0_1.core.SpdxDocument;
import org.spdx.storage.IModelStore.IdType;

public class SpdxExternalIdBuilder
{

  private static final Logger LOG = LoggerFactory.getLogger( SpdxExternalIdBuilder.class );
  
  public static Collection<ExternalIdentifier> getDefaultExternalIdentifiers( SpdxDocument spdxDoc,
                                                                              boolean generatePurls, MavenProject project ) 
  {
      ExternalIdentifier generatedPurlExternalIdentifier = 
                      generatePurls ? generatePurlExternalIdentifier( spdxDoc, project ) : null;
    return generatedPurlExternalIdentifier == null ? List.of() : List.of( generatedPurlExternalIdentifier );
  }
  
  private static ExternalIdentifier generatePurlExternalIdentifier( SpdxDocument spdxDoc, MavenProject project )
  {
    try
    {
        return spdxDoc.createExternalIdentifier( spdxDoc.getModelStore().getNextId( IdType.Anonymous ) )
                          .setExternalIdentifierType( ExternalIdentifierType.PACKAGE_URL )
                          .setIdentifier( SpdxExternalRefBuilder.generatePurl( project ) )
                          .build();
    }
    catch ( InvalidSPDXAnalysisException e )
    {
        LOG.warn( "Invalid reference type \"purl\" for generated purl external ref");
        return null;
    }
  }

}
