package org.spdx.maven;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.spdx.core.DefaultModelStore;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.jacksonstore.MultiFormatStore;
import org.spdx.jacksonstore.MultiFormatStore.Format;
import org.spdx.library.ModelCopyManager;
import org.spdx.library.SpdxModelFactory;
import org.spdx.library.model.v3_0_1.SpdxConstantsV3;
import org.spdx.library.model.v3_0_1.core.Element;
import org.spdx.library.model.v3_0_1.core.Relationship;
import org.spdx.library.model.v3_0_1.core.RelationshipType;
import org.spdx.library.model.v3_0_1.core.SpdxDocument;
import org.spdx.library.model.v3_0_1.software.SpdxPackage;
import org.spdx.spdxRdfStore.RdfStore;
import org.spdx.storage.ISerializableModelStore;
import org.spdx.storage.simple.InMemSpdxStore;
import org.spdx.v3jsonldstore.JsonLDStore;

public class TestWithSessionSpdxV3Mojo extends AbstractMojoTestCase
{

  private static final String UNIT_TEST_RESOURCE_DIR = "target/test-classes/unit/spdx-maven-plugin-test";
  
  @Before
  protected void setUp() throws Exception
  {
      super.setUp();
      SpdxModelFactory.init();
      DefaultModelStore.initialize(new InMemSpdxStore(), "http://default/namespace", new ModelCopyManager());
  }

  @After
  protected void tearDown() throws Exception
  {
      super.tearDown();
  }

  @Test
  public void testDependencies() throws Exception
  {
    File pom = new File( getBasedir(), UNIT_TEST_RESOURCE_DIR + "/json-pom-dependencies-v3.xml" );
    SpdxDocument result = runMojoWithPom( pom );

    Set<String> packages = new HashSet<>();
    Set<String> relationships = new HashSet<>();
    SpdxModelFactory.getSpdxObjects( result.getModelStore(), result.getCopyManager(), SpdxConstantsV3.CORE_LIFECYCLE_SCOPED_RELATIONSHIP,
                                     null, result.getIdPrefix() )
        .forEach( ( element ) -> {
            try
            {
                Relationship rel = (Relationship)element;
                if ( rel.getFrom() instanceof SpdxPackage )
                {
                    SpdxPackage pkg = (SpdxPackage)rel.getFrom();
                    packages.add( pkg.getName().get() );
                    for ( Element to : rel.getTos() )
                    {
                        if ( to instanceof SpdxPackage )
                        {
                            Optional<String> pkgName = pkg.getName();
                            Optional<String> toName = to.getName();
                            relationships.add( pkgName.get() + "->" + toName.get() );
                        }
                    }
                }
            }
            catch( InvalidSPDXAnalysisException e )
            {
                throw new RuntimeException( e );
            }
        });

    assertTrue( packages.contains( "org.spdx:spdx-maven-plugin-test" ) );
    assertTrue( packages.contains( "junit" ) );
    assertTrue( relationships.contains( "org.spdx:spdx-maven-plugin-test->junit" ) );
    assertTrue( relationships.contains( "junit->hamcrest-core" ) || relationships.contains( "junit->org.hamcrest:hamcrest-core" ) );
  }

  // -- Configure mojo loader

  private SpdxDocument runMojoWithPom( File pom ) throws Exception
  {
    CreateSpdxMojo mojo = (CreateSpdxMojo) lookupConfiguredMojo( readMavenProject( pom ), "createSPDX" );
    mojo.execute();

    File artifactFile = (File) getVariableValueFromObject( mojo, "spdxFile" );
    assertTrue( artifactFile.exists() );
    String outputFormat = (String) getVariableValueFromObject( mojo, "outputFormat" );
    ISerializableModelStore modelStore = buildModelStore( outputFormat );
    try ( InputStream is = new FileInputStream( artifactFile.getAbsolutePath() ) )
    {
      return (SpdxDocument)modelStore.deSerialize( is, false );
    }
  }

  private ISerializableModelStore buildModelStore( String outputFormat )
  {
    switch ( outputFormat )
    {
      case "JSON":
        return new MultiFormatStore( new InMemSpdxStore(), Format.JSON );
      case "RDF/XML":
        return new RdfStore();
      case "JSON-LD":
          return new JsonLDStore( new InMemSpdxStore() );
      default:
        throw new IllegalArgumentException( "Unknown output format " + outputFormat );
    }
  }

  @Override
  protected MavenSession newMavenSession( MavenProject project )
  {
    MavenExecutionRequest request = new DefaultMavenExecutionRequest();
    MavenExecutionResult result = new DefaultMavenExecutionResult();

    MavenSession session = new MavenSession( getContainer(), createRepositorySystemSession(), request, result );
    session.setCurrentProject( project );
    session.setProjects( List.of( project ) );
    session.getRequest().setLocalRepository(createLocalArtifactRepository());
    return session;
  }

  private RepositorySystemSession createRepositorySystemSession() {
    DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
    RepositorySystem repositorySystem = locator.getService( RepositorySystem.class );

    LocalRepository localRepo = null;
    try
    {
      localRepo = new LocalRepository( Files.createTempDirectory("tmpDirPrefix").toFile() );
    }
    catch (IOException e)
    {
      throw new RuntimeException(e);
    }

    DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
    LocalRepositoryManager lrm = repositorySystem.newLocalRepositoryManager( session, localRepo );
    session.setLocalRepositoryManager( lrm );

    return session;
  }

  private ArtifactRepository createLocalArtifactRepository() {
    try {
      return new MavenArtifactRepository(
          "local",
          Files.createTempDirectory( "tmpDirPrefix" ).toString(),
          new DefaultRepositoryLayout(),
          new ArtifactRepositoryPolicy( true, ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS, ArtifactRepositoryPolicy.CHECKSUM_POLICY_IGNORE ),
          new ArtifactRepositoryPolicy( true, ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS, ArtifactRepositoryPolicy.CHECKSUM_POLICY_IGNORE )
      );
    }
    catch ( IOException e )
    {
      throw new RuntimeException(e);
    }
  }

  private MavenProject readMavenProject( File pom )
      throws Exception
  {
    MavenExecutionRequest request = new DefaultMavenExecutionRequest();
    request.setBaseDirectory( new File( getBasedir() ) );
    ProjectBuildingRequest configuration = request.getProjectBuildingRequest();
    configuration.setResolveDependencies( true );
    configuration.setLocalRepository( createLocalArtifactRepository() );
    configuration.setRepositorySession( createRepositorySystemSession() );
    MavenProject project = lookup( ProjectBuilder.class ).build( pom, configuration ).getProject();
    Assert.assertNotNull( project );
    return project;
  }

}
