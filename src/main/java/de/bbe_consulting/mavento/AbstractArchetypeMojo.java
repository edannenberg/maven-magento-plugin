/**
 * Copyright 2011-2012 BBe Consulting GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.bbe_consulting.mavento;

import org.apache.maven.archetype.ui.generation.ArchetypeGenerationConfigurator;
import org.apache.maven.archetype.ui.generation.ArchetypeGenerationQueryer;
import org.apache.maven.archetype.ui.generation.ArchetypeSelector;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.ContextEnabled;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.archetype.ArchetypeGenerationRequest;
import org.apache.maven.archetype.ArchetypeManager;
import org.apache.maven.archetype.common.ArchetypeArtifactManager;
import org.apache.maven.archetype.exception.ArchetypeGenerationConfigurationFailure;
import org.apache.maven.archetype.exception.ArchetypeNotConfigured;
import org.apache.maven.archetype.exception.ArchetypeNotDefined;
import org.apache.maven.archetype.exception.UnknownArchetype;
import org.apache.maven.archetype.generator.ArchetypeGenerator;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.codehaus.plexus.util.StringUtils;
import org.dom4j.Document;
import org.dom4j.Node;
import org.dom4j.xpath.DefaultXPath;

import org.apache.maven.archetype.ui.ArchetypeConfiguration;
import org.apache.maven.archetype.ui.ArchetypeDefinition;
import org.apache.maven.archetype.ui.ArchetypeFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Create a new Magento project/module from a template (aka archetype).
 * 
 * @author Erik Dannenberg
 */
public abstract class AbstractArchetypeMojo extends AbstractMojo implements
        ContextEnabled {

    /** @component */
    protected ArchetypeManager archetype;

    /** @component role-hint="magento-archetype-selector" */
    protected ArchetypeSelector selector;

    /** @component */
    ArchetypeGenerationConfigurator configurator;

    /** @component */
    ArchetypeGenerator generator;

    /** @component */
    ArchetypeGenerationQueryer queryer;

    /** @component */
    protected Invoker invoker;

    /**
     * @component
     */
    private ArchetypeArtifactManager archetypeArtifactManager;

    /**
     * @component
     */
    private ArchetypeFactory archetypeFactory;

    /**
     * The archetype's artifactId.
     * 
     * @parameter expression="${archetypeArtifactId}"
     */
    protected String archetypeArtifactId;

    /**
     * The archetype's groupId.
     * 
     * @parameter expression="${archetypeGroupId}"
     */
    protected String archetypeGroupId;

    /**
     * The archetype's version.
     * 
     * @parameter expression="${archetypeVersion}"
     */
    protected String archetypeVersion;

    /**
     * The archetype's repository.
     * 
     * @parameter expression="${archetypeRepository}"
     */
    protected String archetypeRepository;

    /**
     * The archetype's catalogs. It is a comma separated list of catalogs.
     * Catalogs use scheme:
     * <ul>
     * <li>'<code>file://...</code>' with <code>archetype-catalog.xml</code>
     * automatically appended when pointing to a directory</li>
     * <li>'<code>http://...</code>' with <code>archetype-catalog.xml</code>
     * always appended</li>
     * <li>'<code>local</code>' which is the shortcut for '
     * <code>file://~/.m2/archetype-catalog.xml</code>'</li>
     * <li>'<code>remote</code>' which is the shortcut for Maven Central
     * repository, ie '<code>http://repo1.maven.org/maven2</code>'</li>
     * <li>'<code>internal</code>' which is an internal catalog</li>
     * </ul>
     * 
     * Since 2.0-alpha-5, default value is no longer <code>internal,local</code>
     * but <code>remote,local</code>. If Maven Central repository catalog file
     * is empty, <code>internal</code> catalog is used instead.
     * 
     * @parameter expression="${archetypeCatalog}" 
     *            default-value="http://maven.bbe-consulting.de/content/repositories/releases/,local"
     */
    protected String archetypeCatalog;

    /**
     * Local Maven repository.
     * 
     * @parameter expression="${localRepository}"
     * @required
     * @readonly
     */
    protected ArtifactRepository localRepository;

    /**
     * List of remote repositories used by the resolver.
     * 
     * @parameter expression="${project.remoteArtifactRepositories}"
     * @readonly
     * @required
     */
    protected List<ArtifactRepository> remoteArtifactRepositories;

    /**
     * User settings use to check the interactiveMode.
     * 
     * @parameter expression="${interactiveMode}"
     *            default-value="${settings.interactiveMode}"
     * @required
     */
    protected Boolean interactiveMode;

    /** @parameter expression="${basedir}" */
    protected File basedir;

    /**
     * @parameter expression="${session}"
     * @readonly
     */
    protected MavenSession session;

    /**
     * Additional goals that can be specified by the user during the creation of
     * the archetype.
     * 
     * @parameter expression="${goals}"
     */
    protected String goals;

    protected void invokePostArchetypeGenerationGoals(String goals,
            String artifactId) throws MojoExecutionException,
            MojoFailureException {
        File projectBasedir = new File(basedir, artifactId);

        if (projectBasedir.exists()) {
            InvocationRequest request = new DefaultInvocationRequest()
                    .setBaseDirectory(projectBasedir).setGoals(
                            Arrays.asList(StringUtils.split(goals, ",")));

            try {
                invoker.execute(request);
            } catch (MavenInvocationException e) {
                throw new MojoExecutionException("Cannot run additions goals.",
                        e);
            }
        }
    }

    protected String getXmlNodeValueFromPom(String xpathNode, Document pom)
            throws MojoExecutionException {
        DefaultXPath path = new DefaultXPath(xpathNode);
        Map<String, String> namespaces = new TreeMap<String, String>();
        namespaces.put("x", "http://maven.apache.org/POM/4.0.0");

        path.setNamespaceURIs(namespaces);
        Node n = path.selectSingleNode(pom.getRootElement());

        return n.getStringValue();
    }

    public List<String> getRequiredArchetypeProperties(
            ArchetypeGenerationRequest request, Properties commandLineProperties)
            throws ArchetypeNotDefined, UnknownArchetype,
            ArchetypeNotConfigured, IOException,
            ArchetypeGenerationConfigurationFailure {

        ArtifactRepository archetypeRepository = null;

        ArchetypeDefinition ad = new ArchetypeDefinition(request);
        request.setArchetypeVersion(ad.getVersion());

        ArchetypeConfiguration archetypeConfiguration;

        org.apache.maven.archetype.metadata.ArchetypeDescriptor archetypeDescriptor = archetypeArtifactManager
                .getFileSetArchetypeDescriptor(ad.getGroupId(),
                        ad.getArtifactId(), ad.getVersion(),
                        archetypeRepository, request.getLocalRepository(),
                        request.getRemoteArtifactRepositories());

        archetypeConfiguration = archetypeFactory.createArchetypeConfiguration(
                archetypeDescriptor, commandLineProperties);

        return archetypeConfiguration.getRequiredProperties();
    }

}