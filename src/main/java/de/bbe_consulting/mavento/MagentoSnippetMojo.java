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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import org.apache.maven.archetype.ArchetypeGenerationRequest;
import org.apache.maven.archetype.ArchetypeGenerationResult;
import org.codehaus.plexus.util.StringUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;

import de.bbe_consulting.mavento.helper.FileUtil;
import de.bbe_consulting.mavento.helper.MagentoUtil;

/**
 * Create Magento boiler code from snippets. Can only be executed in projects
 * with packaging type php.
 * 
 * @goal snippet
 * @requiresDependencyResolution runtime
 * @author Erik Dannenberg
 */
public class MagentoSnippetMojo extends AbstractArchetypeMojo {

    /**
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        // check for existing pom in current dir, if yes try to set some
        // defaults
        final File p = new File("pom.xml");
        String defaultGroupdId = "de.bbe-consulting.magento";
        if (p.exists()) {
            SAXReader reader = new SAXReader();
            Document document;
            try {
                document = reader.read(p);
            } catch (DocumentException e) {
                throw new MojoExecutionException("Error reading pom.xml " + e.getMessage(), e);
            }
            final String packagingType = getXmlNodeValueFromPom("/x:project/x:packaging", document);
            if (!"php".equals(packagingType)) {
                throw new MojoExecutionException("This mojo can only be run from a php project.");
            }
            final String parentArtifactId = getXmlNodeValueFromPom("/x:project/x:artifactId", document);
            defaultGroupdId = getXmlNodeValueFromPom("/x:project/x:groupId", document) + "." + parentArtifactId;
        } else {
            throw new MojoExecutionException("This mojo can only be run from a maven project root.");
        }

        final Properties executionProperties = session.getUserProperties();

        final ArchetypeGenerationRequest request = new ArchetypeGenerationRequest()
                .setArchetypeGroupId(archetypeGroupId)
                .setArchetypeArtifactId(archetypeArtifactId)
                .setArchetypeVersion(archetypeVersion)
                .setOutputDirectory(basedir.getAbsolutePath())
                .setLocalRepository(localRepository)
                .setArchetypeRepository(archetypeRepository)
                .setRemoteArtifactRepositories(remoteArtifactRepositories);

        try {
            if (interactiveMode.booleanValue()) {
                getLog().info("Generating project in Interactive mode");
            } else {
                getLog().info("Generating project in Batch mode");
            }

            final Properties rp = new Properties();
            rp.setProperty("magentoArchetypeIdentifier", "snippet");
            request.setProperties(rp);

            selector.selectArchetype(request, interactiveMode, archetypeCatalog);

            final List<String> requiredProperties = getRequiredArchetypeProperties(request, executionProperties);

            // not needed in partial mode as we don't touch any poms anyways
            executionProperties.put("groupId", defaultGroupdId);
            executionProperties.put("artifactId", "foobar");
            executionProperties.put("version", "1.0-SNAPSHOT");

            final Path srcPath = Paths.get(project.getBuild().getSourceDirectory());
            String defaultMagentoModuleName = null;
            String defaultMagentoModuleNameSpace = null;
            String defaultMagentoModuleType = null;

            // try to auto guess some defaults
            if (Files.exists(srcPath)) {
                final List<Path> modulePaths = MagentoUtil.getMagentoModuleNames(srcPath);
                if (!modulePaths.isEmpty()) {
                    final Path m = modulePaths.get(0);
                    defaultMagentoModuleName = m.getFileName().toString();
                    defaultMagentoModuleNameSpace = m.getParent().getFileName().toString();
                    defaultMagentoModuleType = m.getParent().getParent().getFileName().toString();
                }
            }

            if (requiredProperties.contains("magentoModuleName")) {
                String reqMagentoModuleName = queryer.getPropertyValue(
                        "magentoModuleName", defaultMagentoModuleName);
                executionProperties.put("magentoModuleName", reqMagentoModuleName);
                executionProperties.put("magentoModuleNameLowerCase", reqMagentoModuleName.toLowerCase());
            }

            if (requiredProperties.contains("magentoNameSpace")) {
                final String reqMagentoNamespace = queryer.getPropertyValue("magentoNamespace",
                        defaultMagentoModuleNameSpace);
                executionProperties.put("magentoNameSpace", reqMagentoNamespace);
            }

            if (requiredProperties.contains("magentoModuleType")) {
                String reqMagentoNamespace = queryer.getPropertyValue("magentoModuleType",
                        defaultMagentoModuleType);
                executionProperties.put("magentoModuleType", reqMagentoNamespace);
            }

            // query any custom required properties and fill LowerCase/LowerCamel version
            for (String property : requiredProperties) {
                if (!property.equals("artifactId")
                        && !property.equals("groupId")
                        && !property.equals("version")
                        && !property.equals("package")) {
                    if (!property.startsWith("magento")
                            && !property.endsWith("LowerCase")
                            && !property.endsWith("LowerCamel")) {

                        final String propertyValue = queryer.getPropertyValue(property, null);
                        if (propertyValue != null && !propertyValue.isEmpty()) {
                            executionProperties.put(property, propertyValue);
                            executionProperties.put(property + "LowerCase", propertyValue.toLowerCase());
                            String lowerCamel = propertyValue.substring(0, 1).toLowerCase()
                                    + propertyValue.substring(1);
                            executionProperties.put(property + "LowerCamel", lowerCamel);
                        }
                    }
                }
            }

            configurator.configureArchetype(request, false, executionProperties);

            final ArchetypeGenerationResult generationResult = archetype.generateProjectFromArchetype(request);

            if (generationResult.getCause() != null) {
                throw new MojoFailureException(generationResult.getCause(),
                        generationResult.getCause().getMessage(),
                        generationResult.getCause().getMessage());
            }

        } catch (Exception ex) {
            throw (MojoFailureException) new MojoFailureException(ex.getMessage()).initCause(ex);
        }

        final String artifactId = request.getArtifactId();

        String postArchetypeGenerationGoals = request.getArchetypeGoals();

        if (StringUtils.isEmpty(postArchetypeGenerationGoals)) {
            postArchetypeGenerationGoals = goals;
        }

        if (StringUtils.isNotEmpty(postArchetypeGenerationGoals)) {
            invokePostArchetypeGenerationGoals(postArchetypeGenerationGoals, artifactId);
        }

        // show post install message
        final Path postMessagePath = Paths.get("_post_install_msg.txt");
        if (Files.exists(postMessagePath)) {
            try {
                FileUtil.logFileContents(postMessagePath.toString(), getLog());
                Files.delete(postMessagePath);
            } catch (IOException e) {
                throw new MojoExecutionException("Error handling _post_install_msg.txt "
                                + e.getMessage(), e);
            }
        }

    }

}
