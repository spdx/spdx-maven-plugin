package org.spdx.maven;

/*
 * Copyright 2014 Source Auditor Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License" );
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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.License;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.model.fileset.FileSet;
import org.spdx.core.InvalidSPDXAnalysisException;
import org.spdx.core.SpdxCoreConstants.SpdxMajorVersion;
import org.spdx.library.SpdxModelFactory;
import org.spdx.library.model.v2.SpdxConstantsCompatV2;
import org.spdx.maven.utils.LicenseMapperException;
import org.spdx.maven.utils.SpdxBuilderException;
import org.spdx.maven.utils.SpdxCollectionException;
import org.spdx.maven.utils.SpdxDefaultFileInformation;
import org.spdx.maven.utils.AbstractDependencyBuilder;
import org.spdx.maven.utils.AbstractDocumentBuilder;
import org.spdx.maven.utils.AbstractFileCollector;
import org.spdx.maven.utils.LicenseManagerException;
import org.spdx.maven.utils.SpdxProjectInformation;
import org.spdx.maven.utils.SpdxV2DependencyBuilder;
import org.spdx.maven.utils.SpdxV2DocumentBuilder;
import org.spdx.maven.utils.SpdxV3DependencyBuilder;
import org.spdx.maven.utils.SpdxV3DocumentBuilder;

import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.CumulativeScopeArtifactFilter;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.Map.Entry;
import org.spdx.library.LicenseInfoFactory;
import org.spdx.library.model.v2.license.InvalidLicenseStringException;

/**
 * NOTE: Plugin for supporting SPDX in a Maven build.
 * <p>
 * Goal which creates new SPDX file(s) for the package being built.  Will replace any existing SPDX file(s).
 * <p>
 * All SPDX document and SPDX package properties are supported as parameters to the plugin.
 * <p>
 * File level data supports default parameters which are applied to all files. Future versions of this plugin will
 * support file specific parameters.
 * <p>
 * The treatment of licenses for Maven is somewhat involved.  Where possible, SPDX standard licenses ID's should be
 * used.  If no SPDX standard license is available, a nonStandardLicense must be declared as a parameter including a
 * unique license ID and the verbatim license text.
 * <p>
 * The following SPDX fields are populated from the POM project information:<ul>
 * <li>package name: project name or artifactId if the project name is not provided</li>
 * <li>package description: project description</li>
 * <li>package shortDescription: project description</li>
 * <li>package downloadUrl: distributionManager url</li>
 * <li>package homePage: project url</li>
 * <li>package supplier: project organization</li>
 * <li>package versionInfo: project version</li>
 * <li>files for analysis: build source files + project resource files</li>
 * <li>created: creation time of the SPDX document</li>
 * </ul><p>
 * Additional SPDX fields are supplied as configuration parameters to this plugin.
 */
@SuppressWarnings({"unused", "DefaultAnnotationParam"})
@Mojo( name = "createSPDX",
       defaultPhase = LifecyclePhase.VERIFY,
        requiresOnline = true,
       threadSafe = true )
public class CreateSpdxMojo extends AbstractMojo
{
    public static final String INCLUDE_ALL = "**/*";

    public static final String CREATOR_TOOL_MAVEN_PLUGIN = "Tool: spdx-maven-plugin";

    public static final String SPDX_RDF_ARTIFACT_TYPE = "spdx.rdf.xml";

    public static final String SPDX_JSON_ARTIFACT_TYPE = "spdx.json";

    public static final String JSON_OUTPUT_FORMAT = "JSON";

    public static final String RDF_OUTPUT_FORMAT = "RDF/XML";

    static
    {
        SpdxModelFactory.init();
    }

    @Parameter( defaultValue = "${project}", readonly = true )
    private MavenProject mavenProject;

    @Component
    private MavenProjectHelper projectHelper;

    @Component
    protected ProjectBuilder mavenProjectBuilder;

    @Parameter( defaultValue = "${session}", readonly = true )
    protected MavenSession session;

    @Component(hint = "default")
    protected DependencyGraphBuilder dependencyGraphBuilder;

    // Parameters for the plugin
    /**
     * SPDX File name
     */
    @Parameter( defaultValue = "${project.reporting.outputDirectory}/${project.groupId}_${project.artifactId}-${project.version}.spdx",
        property = "spdxFileName" )
    private File spdxFile;

    /**
     * Document namespace - must be unique for the artifact and SPDX file
     */
    @Parameter( defaultValue = "http://spdx.org/spdxpackages/${project.groupId}_${project.artifactId}-${project.version}" )
    private String spdxDocumentNamespace;

    @Parameter( defaultValue = "${project.basedir}" )
    private String componentName;

    /**
     * Licenses which are not SPDX listed licenses referenced within the Maven SPDX plugin configuration. All
     * non-standard licenses must be configured containing the required license ID and license text.
     * <pre>
     * &lt;configuration&gt;
     *   &lt;nonStandardLicenses&gt;
     *     &lt;nonStandardLicense&gt;
     *       &lt;licenseId&gt;LicenseRef-[idString]&lt;/licenseId&gt; &lt;!-- Required --&gt;
     *       &lt;extractedText&gt;   &lt;/extractedText&gt; &lt;!-- Required --&gt;
     *       &lt;name&gt;   &lt;/name&gt;
     *       &lt;comment&gt;   &lt;/comment&gt;
     *       &lt;crossReference&gt;
     *         &lt;crossReference&gt;https://...&lt;/crossReference&gt;
     *       &lt;/crossReference&gt;
     *     &lt;/nonStandardLicense&gt;
     *     &lt;!-- ... more ... --&gt;
     *   &lt;/nonStandardLicenses&gt;
     * &lt;/configuration&gt;
     * </pre>
     */
    @Parameter
    private NonStandardLicense[] nonStandardLicenses;

    /**
     * License overwrites to dependencies, used to fix incorrect or missing license infos.
     * <pre>
     * &lt;configuration&gt;
     *   &lt;licenseOverwrites&gt;
     *     &lt;licenseOverwrite&gt;
     *       &lt;target&gt;&lt;/target&gt; &lt;!-- Required, either both, concluded or declared --&gt;
     *       &lt;groupId&gt;&lt;/groupId&gt; &lt;!-- Required --&gt;
     *       &lt;artifactId&gt;&lt;/artifactId&gt; &lt;!-- Required --&gt;
     *       &lt;version&gt;&lt;/version&gt;
     *       &lt;licenseString&gt;&lt;/licenseString&gt; &lt;!-- Required, the SPDX license string --&gt;
     *     &lt;/licenseOverwrite&gt;
     *     &lt;!-- ... more ... --&gt;
     *   &lt;/licenseOverwrites&gt;
     * &lt;/configuration&gt;
     * </pre>
     */
    @Parameter
    private LicenseOverwrite[] licenseOverwrites;

    /**
     * Optional parameter if set to true will match a Maven license to an SPDX standard license if the Maven license URL
     * matches any of the cross-reference license URLs for a standard license.  Default value is true. Note: Several
     * SPDX standard licenses contain the same cross-reference license URL.  In this case, the SPDX standard license
     * used in indeterminate.
     */
    @Parameter( defaultValue = "true" )
    private boolean matchLicensesOnCrossReferenceUrls;

    /**
     * An optional field for creators of the SPDX file content to provide comments to the consumers of the SPDX
     * document.
     */
    @Parameter
    private String documentComment;

    /**
     * Optional annotations for the SPDX document
     * <pre>
     * &lt;configuration&gt;
     *   &lt;documentAnnotations&gt;
     *     &lt;documentAnnotation&gt;
     *       &lt;annotationComment&gt;   &lt;/annotationComment&gt;
     *       &lt;annotationType&gt;   &lt;/annotationType&gt;
     *       &lt;annotationDate&gt;2023-06-29T18:30:22Z&lt;/annotationDate&gt;
     *       &lt;annotator&gt;Person: ...&lt;/annotator&gt;
     *     &lt;/documentAnnotation&gt;
     *     &lt;!-- ... more ... --&gt;
     *   &lt;/documentAnnotations&gt;
     * &lt;/configuration&gt;
     * </pre>
     *
     * @since 0.5.1
     */
    @Parameter
    private Annotation[] documentAnnotations;

    /**
     * Optional annotations for the package
     * <pre>
     * &lt;configuration&gt;
     *   &lt;packageAnnotations&gt;
     *     &lt;packageAnnotation&gt;
     *       &lt;annotationComment&gt;   &lt;/annotationComment&gt;
     *       &lt;annotationType&gt;   &lt;/annotationType&gt;
     *       &lt;annotationDate&gt;2023-06-29T18:30:22Z&lt;/annotationDate&gt;
     *       &lt;annotator&gt;Person: ...&lt;/annotator&gt;
     *     &lt;/packageAnnotation&gt;
     *     &lt;!-- ... more ... --&gt;
     *   &lt;/packageAnnotations&gt;
     * &lt;/configuration&gt;
     * </pre>
     *
     * @since 0.5.1
     */
    @Parameter
    private Annotation[] packageAnnotations;

    /**
     * optional default SPDX file comment field. The file comment field provides a place for the SPDX file creator to
     * record any general comments about the file.
     */
    @Parameter
    private String defaultFileComment;

    /**
     * optional list of default file contributors. This field provides a place for the SPDX file creator to record file
     * contributors. Contributors could include names of copyright holders and/or authors who may not be copyright
     * holders, yet contributed to the file content.
     */
    @Parameter
    private String[] defaultFileContributors;

    /**
     * Default file copyright text. If no copyright text is specified, NOASSERTION will be used The copyrightText field
     * Identifies the copyright holder of the file, as well as any dates present. The text must much the copyright
     * notice found in the file. The options to populate this field are limited to: (a) any text relating to a copyright
     * notice, even if not complete; (b) NONE, if the file contains no license information whatsoever; or (c)
     * NOASSERTION, if the SPDX creator has not examined the contents of the actual file or if the SPDX creator has
     * intentionally provided no information(no meaning should be implied from the absence of an assertion).
     */
    @Parameter( defaultValue = "NOASSERTION" )
    private String defaultFileCopyright;

    /**
     * Optional default file license comment. The licenseComments property allows the preparer of the SPDX document to
     * describe why the licensing in spdx:licenseConcluded was chosen.
     */
    @Parameter
    private String defaultFileLicenseComment;

    /**
     * Optional default file notice text. This field provides a place for the SPDX file creator to record potential
     * legal notices found in the file. This may or may not include copyright statements.
     */
    @Parameter
    private String defaultFileNotice;

    /**
     * This field contains the license the SPDX file creator has concluded as governing the file or alternative values
     * if the governing license cannot be determined. If no concluded license is specified "NOASSERTION" will be used.
     */
    @Parameter( defaultValue = "NOASSERTION" )
    private String defaultFileConcludedLicense;

    /**
     * Default license information in file. If no licenseInformationInFile is specified, NOASSERTION will be used This
     * field contains the license information actually found in the file, if any. Any license information not actually
     * in the file, e.g., “COPYING.txt” file in a toplevel directory, should not be reflected in this field. This
     * information is most commonly found in the header of the file, although it may be in other areas of the actual
     * file. The options to populate this field are limited to: (a) the SPDX License List short form identifier, if the
     * license is on the SPDX License List; (b) a reference to the license, denoted by
     * LicenseRef-#LicenseRef-[idString], if the license is not on the SPDX License List; (c) NONE, if the actual file
     * contains no license information whatsoever; or (d) NOASSERTION, if the SPDX file creator has not examined the
     * contents of the actual file or the SPDX file creator has intentionally provided no information (no meaning should
     * be implied by doing so). For a license set, when there is a choice between licenses (“disjunctive license”), they
     * should be separated with “or” and enclosed in brackets. Similarly when multiple licenses need to be applied
     * (“conjunctive license”), they should be separated with “and” and enclosed in parentheses.
     */
    @Parameter( required = false )
    private String defaultLicenseInformationInFile;

    // the following parameters are for the SPDX project

    /**
     * License declared by the originator for the package.  If no license is specified, the license information in the
     * project POM file will be mapped to a standard SPDX license if available.  If a non-standard license is used, a
     * NOASSERTION value will be used.  The format of the string follows the standard license string format for SPDX
     * files (see the defaultFileConcludedLicense parameter for a full description).
     */
    @Parameter
    private String licenseDeclared;

    /**
     * This field contains the license the SPDX file creator has concluded as governing the package or alternative
     * values, if the governing license cannot be determined. If this field is not specified, the declared license value
     * will be used for the concluded license. The format of the string follows the standard license string format for
     * SPDX files (see the defaultFileConcludedLicense parameter for a full description).
     */
    @Parameter
    private String licenseConcluded;

    /**
     * An optional field for creators of the SPDX file to provide general comments about the creation of the SPDX file
     * or any other relevant comment not included in the other fields.
     */
    @Parameter
    private String creatorComment;

    /**
     * Identify who (or what, in the case of a tool) created the SPDX file. If the SPDX file was created by an
     * individual, indicate the person's name. If the SPDX file was created on behalf of a company or organization,
     * indicate the entity name. If multiple participants or tools were involved, use multiple instances of this field.
     * Person name or organization name may be designated as “anonymous” if appropriate. Format: single line of text
     * with the following keywords: ”Person: person name” and optional “(email)” "Organization: organization” and
     * optional “(email)” "Tool: toolidentifier-version”
     * <p>
     * NOTE: the Tool: spdx-maven-plugin will automatically be added by the plugin
     */
    @Parameter
    private String[] creators;

    /**
     * This field provides a place for the SPDX file creator to record any general comments about the license.
     */
    @Parameter
    private String licenseComments;

    /**
     * The name and, optionally, contact information of the person or organization that originally created the package.
     * Note that the supplier field of SPDX is filled in by the Organization in the POM.  However, the originator may be
     * different than the supplier (e.g. a Maven POM was build by organization X containing code originating from
     * organization Y).
     * <p>
     * The default for this is the Maven organization
     */
    @Parameter
    private String originator;

    /**
     * This field provides a place for the SPDX file creator to record any relevant background information or additional
     * comments about the origin of the package. For example, this field might include comments indicating whether the
     * package been was pulled from a source code management system or has been repackaged.
     */
    @Parameter
    private String sourceInfo;

    /**
     * Identify the copyright holders of the package, as well as any dates present. This will be a free form text field
     * extracted from the package information files.  The options to populate this field are limited to: (a) any text
     * related to a copyright notice, even if not complete; (b) NONE if the package contains no license information
     * whatsoever; or (c) NOASSERTION, if the SPDX file creator has not examined the contents of the       package or if
     * the SPDX file creator has intentionally provided no iInformation(no meaning should be implied by doing so).
     */
    @Parameter( defaultValue = "NOASSERTION" )
    private String copyrightText;

    /**
     * Configure whether only locally cached license list should be used. (a) If set to true, only locally cached
     * version of license list is used. (b) otherwise, the license list is queried over the internet.
     */
    @Parameter
    private boolean onlyUseLocalLicenses;

    /**
     * File checksums provides a unique identifier to match analysis information on each specific file in a package. The
     * SHA1 algorithm is always calculated. Configure which algorithms should be to calculate the file checksum. Other
     * algorithms that can be provided optionally include SHA224, SHA256, SHA384, SHA512, MD2, MD4, MD5, MD6.
     */
    @Parameter
    private String[] checksumAlgorithms;

    // Path specific data
    /**
     * File or directories which have SPDX information different from the project defaults.  The fileOrDirectory field
     * of the PathSpecificSpdxInfo is required. All files within the directory (or just the specific file) will use the
     * SPDX data specified in the PathSpecificSpdxInfo parameters.  All of the SPDX data parameters are optional.  If
     * any SPDX field is not specified, the project level default data will be used.
     * <p>
     * If a file or directory is nested within another pathsWithSpecificSpdxInfo, the lowest level values will be used.
     * Note: in this case the non-specified SPDX fields for the lowest level PathSpecificSpdxInfo will use the default
     * project level fields NOT the higher level PathSpecificSpdxInfo.
     * <pre>
     * &lt;configuration&gt;
     *   &lt;pathsWithSpecificSpdxInfo&gt;
     *     &lt;pathsWithSpecificSpdxInfo&gt;
     *       &lt;directoryOrFile&gt;src/main/java/CommonCode.java&lt;/directoryOrFile&gt;
     *       &lt;fileComment&gt;Comment for CommonCode&lt;/fileComment&gt;
     *       &lt;fileContributors&gt;
     *         &lt;fileContributor&gt;Contributor to CommonCode&lt;/fileContributor&gt;
     *         &lt;!-- ... more ... --&gt;
     *       &lt;/fileContributors&gt;
     *       &lt;fileCopyright&gt;Common Code Copyright&lt;/fileCopyright&gt;
     *       &lt;fileLicenseComment&gt;License Comment for Common Code&lt;/fileLicenseComment&gt;
     *       &lt;fileNotice&gt;Notice for Commmon Code&lt;/fileNotice&gt;
     *       &lt;fileConcludedLicense&gt;EPL-1.0&lt;/fileConcludedLicense&gt;
     *       &lt;licenseInformationInFile&gt;ISC&lt;/licenseInformationInFile&gt;
     *       &lt;snippets&gt;
     *         &lt;snippet&gt;
     *           &lt;name&gt;SnippetName&lt;/name&gt;
     *           &lt;comment&gt;Snippet Comment&lt;/comment&gt;
     *           &lt;concludedLicense&gt;BSD-2-Clause&lt;/concludedLicense&gt;
     *           &lt;lineRange&gt;44:55&lt;/lineRange&gt;
     *           &lt;byteRange&gt;1231:3442&lt;/byteRange&gt;
     *           &lt;licenseComment&gt;Snippet License Comment&lt;/licenseComment&gt;
     *           &lt;copyrightText&gt;Snippet Copyright Text&lt;/copyrightText&gt;
     *           &lt;licenseInfoInSnippet&gt;BSD-2-Clause-FreeBSD&lt;/licenseInfoInSnippet&gt;
     *         &lt;!-- ... more ... --&gt;
     *         &lt;/snippet&gt;
     *       &lt;/snippets&gt;
     *     &lt;/pathsWithSpecificSpdxInfo&gt;
     *     &lt;!-- ... more ... --&gt;
     *   &lt;/pathsWithSpecificSpdxInfo&gt;
     * &lt;/configuration&gt;
     * </pre>
     */
    @Parameter
    private PathSpecificSpdxInfo[] pathsWithSpecificSpdxInfo;

    /**
     * <pre>
     * &lt;configuration&gt;
     *   &lt;externalReferences&gt;
     *     &lt;externalReference&gt;
     *       &lt;category&gt;   &lt;/category&gt;
     *       &lt;type&gt;   &lt;/type&gt;
     *       &lt;locator&gt;   &lt;/locator&gt;
     *       &lt;comment&gt;   &lt;/comment&gt;
     *     &lt;/externalReference&gt;
     *     &lt;!-- ... more ... --&gt;
     *   &lt;/externalReferences&gt;
     * &lt;/configuration&gt;
     * </pre>
     */
    @Parameter
    private ExternalReference[] externalReferences;

    /**
     * Output file format for the SPDX file.  One of:
     * - JSON - JSON SPDX format
     * - RDF/XML - RDF/XML format
     *
     * @since 0.6.0
     */
    @Parameter( defaultValue = "JSON" )
    private String outputFormat;

    /**
     * Type of the SPDX file.  One of:
     * - consolidated - include source code files to the license scan
     * - build - exclude source code files from the license scan
     *
     * @since 1.0.0
     */
    @Parameter( defaultValue = "consolidated" )
    private String sbomType;

    /**
     * If true, external document references will be created for any dependencies which
     * contain SPDX documents.  If false, the dependent package information will be copied
     * from the SPDX document into the generated SPDX document.
     *
     * @since 0.6.3
     */
    @Parameter( defaultValue = "true" )
    protected boolean createExternalRefs;

    /**
     * If true, all transitive dependencies will be included in the SPDX document.  If false,
     * only direct dependencies will be included.
     *
     * @since 0.6.3
     */
    @Parameter( defaultValue = "true" )
    protected boolean includeTransitiveDependencies;

     /**
      * Skip goal execution.
      *
      * @since 0.7.1
      */
    @Parameter( property = "spdx.skip" )
    private boolean skip = false;

    /**
     * If true, use ${project.groupId}:${artifactId} as the SPDX package name.
     * Otherwise, ${project.name} will be used
     */
    @Parameter( property = "spdx.useArtifactID" )
    protected boolean useArtifactID;

    /**
     * If true, adds an external reference to every package with category "PACKAGE-MANAGER", type "purl"
     * and locator "pkg:maven/${project.groupId}/${project.artifactId}@${project.version}".
     */
    @Parameter( property = "spdx.generatePurls" )
    protected boolean generatePurls = true;

    /**
     * If true, include system scope in dependency graph
     * @since 0.8.0
     */
    @Parameter( defaultValue = "true" )
    private boolean includeSystemScope;

    /**
     * If true, include test scope in dependency graph
     * @since 0.8.0
     */
    @Parameter( defaultValue = "true" )
    private boolean includeTestScope;

    /**
     * If true, include runtime scope in dependency graph
     * @since 0.8.0
     */
    @Parameter( defaultValue = "true" )
    private boolean includeRuntimeScope;

    /**
     * If true, include provided scope in dependency graph
     * @since 0.8.0
     */
    @Parameter( defaultValue = "true" )
    private boolean includeProvidedScope;

    /**
     * If true, include compile scope in dependency graph
     * @since 0.8.0
     */
    @Parameter( defaultValue = "true" )
    private boolean includeCompileScope;

    /**
     * SPDX Creation timestamp either formatted as ISO 8601
     * <code>yyyy-MM-dd'T'HH:mm:ssXXX</code> or as an int representing seconds since the epoch.
     *
     * Reference: Reproducible <a href="https://reproducible-builds.org/docs/source-date-epoch/">Code Source-Date-Epoch spec</a>
     *
     * @since 1.0.0
     */
    @Parameter( defaultValue = "${project.build.outputTimestamp}" )
    private String created;

    public void execute() throws MojoExecutionException
    {
        if ( skip )
        {
            getLog().info("Skipping SPDX");
            return;
        }

        OutputFormat outputFormatEnum = prepareOutput();
        String artifactType = outputFormatEnum.getArtifactType();

        getLog().info( "Creating SPDX File " + spdxFile.getPath() );

        AbstractDocumentBuilder builder = initSpdxDocumentBuilder( outputFormatEnum );

        // fill project information
        try
        {
            SpdxProjectInformation projectInformation = getSpdxProjectInfoFromParameters( builder );
            projectInformation.logInfo();
            builder.fillSpdxDocumentInformation( projectInformation );
        }
        catch ( InvalidSPDXAnalysisException e2 )
        {
            throw new MojoExecutionException( "Error getting project information from parameters", e2 );
        }

        // collect file-level information
        SpdxDefaultFileInformation defaultFileInformation = getDefaultFileInfoFromParameters();
        HashMap<String, SpdxDefaultFileInformation> pathSpecificInformation = getPathSpecificInfoFromParameters( defaultFileInformation );

        List<FileSet> sources = toFileSet( mavenProject.getCompileSourceRoots(), mavenProject.getResources() );
        sources.addAll( toFileSet( mavenProject.getTestCompileSourceRoots(), null ) ); // TODO: why not test resources given source resources are taken into account?

        if ( getLog().isDebugEnabled() )
        {
            logIncludedDirectories( sources );
            logNonStandardLicenses( this.nonStandardLicenses );
            defaultFileInformation.logInfo();
            logFileSpecificInfo( pathSpecificInformation );
        }

        if ( !"build".equals(sbomType) )
        {
            builder.collectSpdxFileInformation( sources, mavenProject.getBasedir().getAbsolutePath(), defaultFileInformation, 
                    pathSpecificInformation, getChecksumAlgorithms() );
        }

        // add dependencies information
        try
        {
            buildSpdxDependencyInformation( builder, outputFormatEnum );
        }
        catch ( LicenseMapperException e1 )
        {
            throw new MojoExecutionException( "Error mapping licenses for dependencies", e1 );
        }
        catch ( InvalidSPDXAnalysisException e )
        {
            throw new MojoExecutionException( "SPDX analysis error processing dependencies", e );
        }
        catch ( DependencyGraphBuilderException e )
        {
            throw new MojoExecutionException( "SPDX analysis error getting the dependencies", e );
        }

        // save result to SPDX file
        builder.saveSpdxDocumentToFile();

        // attach
        projectHelper.attachArtifact( mavenProject, artifactType, spdxFile );

        // check errors
        List<String> spdxErrors = builder.verify();
        if ( spdxErrors != null && !spdxErrors.isEmpty() )
        {
            getLog().warn( "The following errors were found in the SPDX file:\n " + String.join( "\n ", spdxErrors ) );
        }
    }

    private OutputFormat prepareOutput()
        throws MojoExecutionException
    {
        OutputFormat outputFormatEnum = OutputFormat.JSON;
        try
        {
            outputFormatEnum = OutputFormat.getOutputFormat(outputFormat, spdxFile);
        }
        catch (final IllegalArgumentException iae)
        {
            getLog().warn( "Invalid SPDX output format, defaulting to JSON format." );
        }
        if (spdxFile.getName().endsWith( ".spdx" )) {
            // add a default extension
            String spdxFileType = outputFormatEnum.getFileType();
            getLog().info( "spdx file type = "+spdxFileType );
            spdxFile = new File( spdxFile.getAbsolutePath() + spdxFileType );
        }
        File outputDir = this.spdxFile.getParentFile();
        if ( outputDir == null )
        {
            throw new MojoExecutionException(
                    "Invalid path for SPDX output file.  " + "Specify a configuration parameter spdxFile with a valid directory path to resolve." );
        }
        //noinspection ResultOfMethodCallIgnored
        outputDir.mkdirs();
        return outputFormatEnum;
    }

    private AbstractDocumentBuilder initSpdxDocumentBuilder( OutputFormat outputFormatEnum )
        throws MojoExecutionException
    {
        if ( onlyUseLocalLicenses )
        {
            System.setProperty( "SPDXParser.OnlyUseLocalLicenses", "true" );
        }
        if ( defaultLicenseInformationInFile == null ) {
            defaultLicenseInformationInFile = defaultFileConcludedLicense;
        }

        AbstractDocumentBuilder builder;
        try
        {
            if ( spdxDocumentNamespace.startsWith( "http://spdx.org/spdxpackages/" )) {
                // Fix up any URI encoding issues with the default
                spdxDocumentNamespace = spdxDocumentNamespace.replace( " ", "%20" );
            }
            URI namespaceUri = new URI( spdxDocumentNamespace );
            if ( SpdxMajorVersion.VERSION_3.equals( outputFormatEnum.getSpecVersion() ) ) {
                builder = new SpdxV3DocumentBuilder( mavenProject, generatePurls, spdxFile, namespaceUri,
                        outputFormatEnum );
            }
            else
            {
                builder = new SpdxV2DocumentBuilder( mavenProject, generatePurls, spdxFile, namespaceUri,
                        outputFormatEnum );
            }

        }
        catch ( SpdxBuilderException e )
        {
            throw new MojoExecutionException( "Error creating SPDX Document Builder", e );
        }
        catch ( LicenseMapperException e )
        {
            throw new MojoExecutionException( "License mapping error creating SPDX Document Builder", e );
        }
        catch ( URISyntaxException e )
        {
            throw new MojoExecutionException( "Invalid SPDX document namespace - not a valid URI: " + spdxDocumentNamespace, e );
        }
        if ( nonStandardLicenses != null )
        {
            try
            {
                // the following will add the license to the document container
                builder.addNonStandardLicenses( nonStandardLicenses );
            }
            catch ( SpdxBuilderException e )
            {
                throw new MojoExecutionException( "Error adding non standard licenses", e );
            }
        }
        return builder;
    }

    /**
     * Collect dependency information from Maven dependencies and adds it to the builder SPDX document
     *
     * @param builder SPDX document builder
     * @throws LicenseMapperException on errors related to mapping Maven licenses to SPDX licenses
     * @throws InvalidSPDXAnalysisException on SPDX parsing errors
     */
    protected void buildSpdxDependencyInformation( AbstractDocumentBuilder builder, OutputFormat outputFormatEnum )
        throws LicenseMapperException, InvalidSPDXAnalysisException, DependencyGraphBuilderException
    {
        AbstractDependencyBuilder dependencyBuilder;
        if ( builder instanceof SpdxV3DocumentBuilder )
        {
            SpdxV3DocumentBuilder documentBuilder = (SpdxV3DocumentBuilder) builder;
            SpdxV3DependencyBuilder dependencyBuilderV3 = new SpdxV3DependencyBuilder(
                                                      documentBuilder, createExternalRefs,
                                                      generatePurls, useArtifactID,
                                                      includeTransitiveDependencies );
            if ( licenseOverwrites != null )
            {
                org.spdx.library.model.v3_0_1.core.SpdxDocument spdxDoc = documentBuilder.getSpdxDoc();

                for ( LicenseOverwrite licenseOverwrite : licenseOverwrites )
                {
                    org.spdx.library.model.v3_0_1.simplelicensing.AnyLicenseInfo parsedLicense;

                    try
                    {
                        // parse the licenseString before use to fail fast with a broken configuration
                        parsedLicense = LicenseInfoFactory.parseSPDXLicenseString(
                                licenseOverwrite.getLicenseString(), spdxDoc.getModelStore(),
                                spdxDoc.getIdPrefix(), spdxDoc.getCopyManager(), null );
                    }
                    catch ( InvalidLicenseStringException e )
                    {
                        // add some info the help the user fixing the configuration
                        throw new InvalidLicenseStringException( "Invalid license overwrite configuration for " + licenseOverwrite, e );
                    }

                    dependencyBuilderV3.addLicenseOverwrite( licenseOverwrite, parsedLicense );
                }
            }
            dependencyBuilder = dependencyBuilderV3;
        }
        else
        {
            SpdxV2DocumentBuilder documentBuilder = (SpdxV2DocumentBuilder) builder;
            SpdxV2DependencyBuilder dependencyBuilderV2 = new SpdxV2DependencyBuilder(
                                                      documentBuilder, createExternalRefs,
                                                      generatePurls, useArtifactID,
                                                      includeTransitiveDependencies );

            if ( licenseOverwrites != null )
            {
                org.spdx.library.model.v2.SpdxDocument spdxDoc = documentBuilder.getSpdxDoc();

                for ( LicenseOverwrite licenseOverwrite : licenseOverwrites )
                {
                    org.spdx.library.model.v2.license.AnyLicenseInfo parsedLicense;

                    try
                    {
                        // parse the licenseString before use to fail fast with a broken configuration
                        parsedLicense = LicenseInfoFactory.parseSPDXLicenseStringCompatV2(
                                licenseOverwrite.getLicenseString(), spdxDoc.getModelStore(),
                                spdxDoc.getDocumentUri(), spdxDoc.getCopyManager() );
                    }
                    catch ( InvalidLicenseStringException e )
                    {
                        // add some info the help the user fixing the configuration
                        throw new InvalidLicenseStringException( "Invalid license overwrite configuration for " + licenseOverwrite, e );
                    }

                    dependencyBuilderV2.addLicenseOverwrite( licenseOverwrite, parsedLicense );
                }
            }

            dependencyBuilder = dependencyBuilderV2;
        }
        if ( session != null )
        {
            ProjectBuildingRequest request = new DefaultProjectBuildingRequest( session.getProjectBuildingRequest() );
            request.setProject( mavenProject );
            ArtifactFilter artifactFilter = getArtifactFilter();
            DependencyNode parentNode = dependencyGraphBuilder.buildDependencyGraph( request, artifactFilter );

            dependencyBuilder.addMavenDependencies( mavenProjectBuilder, session, mavenProject, parentNode, builder.getProjectPackage() );
        }
    }

    private void logFileSpecificInfo( HashMap<String, SpdxDefaultFileInformation> fileSpecificInformation )
    {
        if ( !getLog().isDebugEnabled() )
        {
            return;
        }
        for ( Entry<String, SpdxDefaultFileInformation> entry : fileSpecificInformation.entrySet() )
        {
            getLog().debug( "File Specific Information for " + entry.getKey() );
            entry.getValue().logInfo();
        }
    }

    /**
     * Get the patch specific information
     *
     * @param projectDefault default file information if no path specific overrides are present
     * @return map path to project specific SPDX parameters
     */
    private HashMap<String, SpdxDefaultFileInformation> getPathSpecificInfoFromParameters( SpdxDefaultFileInformation projectDefault ) {
        HashMap<String, SpdxDefaultFileInformation> retval = new HashMap<>();
        if ( this.pathsWithSpecificSpdxInfo != null )
        {
            for ( PathSpecificSpdxInfo spdxInfo : this.pathsWithSpecificSpdxInfo )
            {
                SpdxDefaultFileInformation value;
                value = spdxInfo.getDefaultFileInformation( projectDefault );
                if ( retval.containsKey( spdxInfo.getPath() ) )
                {
                    getLog().warn( "Multiple file path specific SPDX data for " + spdxInfo.getPath() );
                }
                retval.put( spdxInfo.getPath(), value );
            }
        }
        return retval;
    }

    /**
     * Primarily for debugging purposes - logs nonStandardLicenses as info
     *
     * @param nonStandardLicenses non standard licenses to log
     */
    private void logNonStandardLicenses( NonStandardLicense[] nonStandardLicenses )
    {
        if (( nonStandardLicenses == null ) || !getLog().isDebugEnabled() )
        {
            return;
        }
        for ( NonStandardLicense nonStandardLicense : nonStandardLicenses )
        {
            getLog().debug( "Non standard license ID: " + nonStandardLicense.getLicenseId() );
            getLog().debug( "Non standard license Text: " + nonStandardLicense.getExtractedText() );
            getLog().debug( "Non standard license Comment: " + nonStandardLicense.getComment() );
            getLog().debug( "Non standard license Name: " + nonStandardLicense.getName() );
            String[] crossReferences = nonStandardLicense.getCrossReference();
            if ( crossReferences != null )
            {
                for ( String crossReference : crossReferences )
                {
                    getLog().debug( "Non standard license cross reference: " + crossReference );
                }
            }
        }
    }

    /**
     * Primarily for debugging purposes - logs includedDirectories as info
     *
     * @param includedDirectories included directory fileSet to log
     */
    private void logIncludedDirectories( List<FileSet> includedDirectories )
    {
        if (( includedDirectories == null ) || !getLog().isDebugEnabled() )
        {
            return;
        }
        getLog().debug( "Logging " + includedDirectories.size() + " filesets." );
        for ( FileSet includedDirectory : includedDirectories )
        {
            StringBuilder sb = new StringBuilder( "Included Directory: " + includedDirectory.getDirectory() );
            List<String> includes = includedDirectory.getIncludes();
            if ( !includes.isEmpty() )
            {
                sb.append( "; Included=" );
                sb.append( String.join(",", includes) );
            }
            List<String> excludes = includedDirectory.getExcludes();
            if ( !excludes.isEmpty() )
            {
                sb.append( "; Excluded=" );
                sb.append( String.join(",", excludes) );
            }
            getLog().debug( sb.toString() );
        }
    }

    /**
     * @return default file information from the plugin parameters
     */
    private SpdxDefaultFileInformation getDefaultFileInfoFromParameters() {
        SpdxDefaultFileInformation retval;
        retval = new SpdxDefaultFileInformation();
        retval.setComment( defaultFileComment );
        retval.setConcludedLicense( defaultFileConcludedLicense.trim() );
        retval.setContributors( defaultFileContributors );
        retval.setCopyright( defaultFileCopyright );
        retval.setDeclaredLicense( defaultLicenseInformationInFile.trim() );
        retval.setLicenseComment( defaultFileLicenseComment );
        retval.setNotice( defaultFileNotice );
        return retval;
    }

    /**
     * Get the SPDX project level information from the parameters The following project level information is taken from
     * the POM project description: declaredLicense - mapped by the license parameter in the project.  Can be overridden
     * by specifying a plugin configuration declaredLicense string concludedLicense - same as the declared license
     * unless overridden by the plugin configuration parameter concludedLicense name - name of the project.  If not
     * provided, the artifactId is used downloadUrl - distributionManagement().downloadUrl - If not provided, a default
     * value of 'NOASSERTION' is used packageFileName is the artifact().getFile fileName if it can be found.  The
     * checksum is also calculated from this value.  If no file could be determined, a 'NOASSERTION' value is used
     * [currently not implemented] description, summary - The project description is used for the SPDX package
     * description and SPDX package summary supplier - the project organization is used for the supplier. "ORGANIZATION:
     * " is prepended
     *
     * @param builder      SPDX document builder
     * @return             SPDX project level information
     */
    private SpdxProjectInformation getSpdxProjectInfoFromParameters( AbstractDocumentBuilder builder ) throws InvalidSPDXAnalysisException
    {
        SpdxProjectInformation retval = new SpdxProjectInformation();
        if ( this.documentComment != null )
        {
            retval.setDocumentComment( this.documentComment );
        }
        String declaredLicense;
        if ( this.licenseDeclared == null )
        {
            List<License> mavenLicenses = mavenProject.getLicenses();
            try
            {
                declaredLicense = builder.mavenLicenseListToSpdxLicenseExpression( mavenLicenses );
            }
            catch ( LicenseManagerException e )
            {
                getLog().warn( "Unable to map maven licenses to a declared license.  Using NOASSERTION" );
                declaredLicense = "NOASSERTION";
            }
        }
        else
        {
            declaredLicense = this.licenseDeclared.trim();
        }
        String concludedLicense;
        if ( this.licenseConcluded == null )
        {
            concludedLicense = declaredLicense;
        }
        else
        {
            concludedLicense = this.licenseConcluded.trim();
        }
        retval.setConcludedLicense( concludedLicense );
        retval.setCreatorComment( this.creatorComment );
        if ( this.creators == null )
        {
            this.creators = new String[0];
        }
        String[] allCreators = Arrays.copyOf( creators, creators.length + 1 );
        allCreators[allCreators.length - 1] = CREATOR_TOOL_MAVEN_PLUGIN;
        retval.setCreators( allCreators );
        retval.setCopyrightText( this.copyrightText );
        retval.setDeclaredLicense( declaredLicense );
        String projectName = mavenProject.getName();
        if ( projectName == null || projectName.isEmpty() || useArtifactID )
        {
            projectName = getDefaultProjectName();
        }
        retval.setName( projectName );
        retval.setDescription( mavenProject.getDescription() );
        String downloadUrl = "NOASSERTION";
        DistributionManagement distributionManager = mavenProject.getDistributionManagement();
        if ( distributionManager != null )
        {
            if ( distributionManager.getDownloadUrl() != null && !distributionManager.getDownloadUrl().isEmpty() )
            {
                downloadUrl = distributionManager.getDownloadUrl();
            }
        }
        retval.setDownloadUrl( downloadUrl );
        retval.setHomePage( mavenProject.getUrl() );
        retval.setLicenseComment( this.licenseComments );
        if ( this.originator == null )
        {
            // use the POM organization as the default
            if ( this.mavenProject.getOrganization() != null && this.mavenProject.getOrganization().getName() != null && !this.mavenProject.getOrganization().getName().isEmpty() )
            {
                this.originator = "Organization:" + this.mavenProject.getOrganization().getName();
            }
        }
        retval.setOriginator( this.originator );
        String packageFileName = null;
        File packageFile = null;
        Artifact mainArtifact = mavenProject.getArtifact();

        if ( mainArtifact != null && mainArtifact.getFile() != null )
        {
            packageFileName = mainArtifact.getArtifactId() + "-" + mainArtifact.getVersion() + "." + mainArtifact.getType();
            packageFile = new File( mainArtifact.getFile().getParent() + File.separator + packageFileName );
        }

        Set<Checksum> checksums = null;
        if ( packageFile != null && packageFile.exists() )
        {
            try
            {
                getLog().debug( "Generating checksum for file " + packageFile.getAbsolutePath() );
                Set<String> algorithms = getChecksumAlgorithms();
                checksums = AbstractFileCollector.generateChecksum( packageFile, algorithms );
            }
            catch ( SpdxCollectionException | InvalidSPDXAnalysisException e )
            {
                getLog().warn( "Unable to compute checksum for " + packageFile.getName() + ":" + e.getMessage() );
                getLog().debug( "Exception information for checksum error", e );
            }
        }
        else
        {
            getLog().warn( packageFile == null ? "Null package file" : "Package file " + packageFile.getAbsolutePath() + " does not exist" );
            packageFileName = "NOASSERTION";
        }
        retval.setPackageArchiveFileName( packageFileName );
        retval.setChecksums( checksums );
        retval.setShortDescription( mavenProject.getDescription() );
        if ( mavenProject.getOrganization() != null )
        {
            String supplier = mavenProject.getOrganization().getName();
            if ( supplier != null && !supplier.isEmpty() )
            {
                supplier = "Organization: " + supplier;
                retval.setSupplier( supplier );
            }
        }
        retval.setSourceInfo( this.sourceInfo );
        retval.setVersionInfo( mavenProject.getVersion() );
        retval.setDocumentAnnotations( this.documentAnnotations );
        retval.setPackageAnnotations( this.packageAnnotations );
        retval.setExternalRefs( this.externalReferences );

        final Packaging packaging = Packaging.valueOfPackaging( mavenProject.getPackaging() );
        retval.setPackaging( packaging != null ? packaging : Packaging.JAR );
        Date createdDate;
        if ( this.created == null || this.created.isEmpty() )
        {
            this.created = mavenProject.getModel().getProperties().getProperty( "project.build.outputTimestamp" );
        }
        if ( this.created == null || this.created.isEmpty() )
        {
            createdDate = new Date();
        }
        else
        {
            // The created parameter can be either a seconds from epoch or an ISO 8601 format
            // We'll check for the int value first
            if ( created.matches( "\\d+" ) )
            {
                createdDate = new Date( Long.parseLong( created ) * 1000 );
            }
            else
            {
                try
                {
                    createdDate = Date.from( Instant.parse( created ) );
                }
                catch ( DateTimeParseException ex)
                {
                    getLog().warn( "Invalid created date " + created + ".  Using current date-time" );
                    createdDate = new Date();
                }

            }

        }
        SimpleDateFormat dateFormat = new SimpleDateFormat( SpdxConstantsCompatV2.SPDX_DATE_FORMAT);
        dateFormat.setTimeZone( TimeZone.getTimeZone("GMT") );
        retval.setCreated( dateFormat.format( createdDate ) );
        return retval;
    }

    /**
     * Get the default project name if no project name is specified in the POM
     *
     * @return the default project name if no project name is specified in the POM
     */
    private String getDefaultProjectName()
    {
        return this.mavenProject.getGroupId() + ":" + this.mavenProject.getArtifactId();
    }

    /**
     * FileSets are all normalized to include the full (absolute) path and use filtering.
     *
     * @param roots the source roots as Strings
     * @param resources the resources
     * @return the source roots and resources as FileSets
     */
    private static List<FileSet> toFileSet( List<String> roots, List<Resource> resources )
    {
        List<FileSet> result = new ArrayList<>();
        if ( roots != null )
        {
            for ( String root : roots )
            {
                FileSet fileSet = new FileSet();
                File dir = new File( root );
                fileSet.setDirectory( dir.getAbsolutePath() );
                fileSet.addInclude( INCLUDE_ALL );
                result.add( fileSet );
            }
        }

        if ( resources != null )
        {
            for ( Resource resource : resources )
            {
                FileSet fileSet = new FileSet();
                File dir = new File( resource.getDirectory() );
                fileSet.setDirectory( dir.getAbsolutePath() );
                fileSet.setExcludes( resource.getExcludes() );
                fileSet.setIncludes( resource.getIncludes() );
                result.add( fileSet );
            }
        }
        return result;
    }

    /**
     * Map user input algorithms to Checksum.ChecksumAlgorithm values. {@code SHA1}
     * is always added to the set because it is mandatory to include the SHA1 checksum.
     * @return set of algorithms to calculate checksum with
     */
    private Set<String> getChecksumAlgorithms()
    {
        Set<String> algorithms = new HashSet<>();
        algorithms.add( "SHA1" );
        if ( checksumAlgorithms != null )
        {
            Collections.addAll( algorithms, checksumAlgorithms );
        }
        return algorithms;
    }

    /**
     * Create an ArtifactFilter based on the provided scopes
     */
    private ArtifactFilter getArtifactFilter()
    {
        getLog().debug( "Creating Artifact filter" );
        List<String> scopes = new ArrayList<>();
        if (includeCompileScope) scopes.add("compile");
        if (includeProvidedScope) scopes.add("provided");
        if (includeRuntimeScope) scopes.add("runtime");
        if (includeSystemScope) scopes.add("system");
        if (includeTestScope) scopes.add("test");

        getLog().debug( scopes.toString() );
        return new CumulativeScopeArtifactFilter(scopes);
    }
}
