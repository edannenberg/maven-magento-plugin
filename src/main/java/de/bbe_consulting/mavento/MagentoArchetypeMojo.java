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
import org.apache.maven.archetype.ArchetypeGenerationRequest;
import org.apache.maven.archetype.ArchetypeGenerationResult;
import org.codehaus.plexus.util.StringUtils;
import org.dom4j.Document;
import org.dom4j.io.SAXReader;
import de.bbe_consulting.mavento.helper.FileUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Create a new Magento project/module from a template (aka archetype). This
 * goal does not need an active Maven project.
 * 
 * @goal archetype
 * @aggregator false
 * @requiresProject false
 * @author Erik Dannenberg
 */
public class MagentoArchetypeMojo extends AbstractArchetypeMojo {

    public void execute() throws MojoExecutionException, MojoFailureException {
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
            rp.setProperty("magentoArchetypeIdentifier", "archetype");
            request.setProperties(rp);

            selector.selectArchetype(request, interactiveMode, archetypeCatalog);

            List<String> requiredProperties = getRequiredArchetypeProperties(
                    request, executionProperties);

            // check for existing pom in current dir, if yes try to set some defaults
            File p = new File("pom.xml");
            String defaultGroupdId = "de.bbe-consulting.magento";
            if (p.exists()) {
                final SAXReader reader = new SAXReader();
                final Document document = reader.read(p);
                final String parentArtifactId = getXmlNodeValueFromPom("/x:project/x:artifactId", document);
                defaultGroupdId = getXmlNodeValueFromPom("/x:project/x:groupId", document) + "." + parentArtifactId;
            }

            final String reqGroupdId = queryer.getPropertyValue("groupId", defaultGroupdId);
            executionProperties.put("groupId", reqGroupdId);

            final String reqArtifactId = queryer.getPropertyValue("artifactId", null);
            executionProperties.put("artifactId", reqArtifactId);

            final String reqVersion = queryer.getPropertyValue("version", "1.0-SNAPSHOT");
            executionProperties.put("version", reqVersion);

            
            boolean askModuleProperties = true; 
            if (requiredProperties.contains("magentoCreateEmptyModuleStructure")) {
                final String reqCreateEmptyModuleStructure = queryer.getPropertyValue("magentoCreateEmptyModuleStructure", "true");
                executionProperties.put("magentoCreateEmptyModuleStructure", reqCreateEmptyModuleStructure);
                if (!reqCreateEmptyModuleStructure.equals("true")) {
                    askModuleProperties = false;
                }
            }
            
            if (requiredProperties.contains("magentoModuleName")) {
                final String reqMagentoModuleName;
                if (askModuleProperties) {
                    reqMagentoModuleName = queryer.getPropertyValue("magentoModuleName", null);
                } else {
                    reqMagentoModuleName = reqArtifactId;
                }
                executionProperties.put("magentoModuleName", reqMagentoModuleName);
                executionProperties.put("magentoModuleNameLowerCase", reqMagentoModuleName.toLowerCase());
            }

            if (requiredProperties.contains("magentoNameSpace")) {
                final String reqMagentoNamespace;
                if (askModuleProperties) {
                    reqMagentoNamespace = queryer.getPropertyValue("magentoNameSpace", null);
                } else {
                    reqMagentoNamespace = "None";
                }
                executionProperties.put("magentoNameSpace", reqMagentoNamespace);
            }

            if (requiredProperties.contains("magentoModuleType")) {
                final String reqMagentoModuleType;
                if (askModuleProperties) {
                    reqMagentoModuleType = queryer.getPropertyValue("magentoModuleType", "local");
                } else {
                    reqMagentoModuleType = "local";
                }
                executionProperties.put("magentoModuleType", reqMagentoModuleType);
            }

            configurator.configureArchetype(request, false, executionProperties);

            ArchetypeGenerationResult generationResult = archetype.generateProjectFromArchetype(request);

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

        try {
            fixModuleStructure(artifactId, executionProperties);
        } catch (IOException e) {
            throw new MojoExecutionException("Error while finishing up module structure!", e);
        }

    }

    /**
     * Fixes some shortcomings of the maven archetype plugin.
     * 
     * @param artifactId
     * @param props maven properties
     * @throws IOException
     */
    private void fixModuleStructure(String artifactId, Properties props)
            throws IOException {

        final File projectBasedir = new File(basedir, artifactId);
        final String moduleName = props.getProperty("magentoModuleName");
        final String nameSpace = props.getProperty("magentoNameSpace");
        final String moduleType = props.getProperty("magentoModuleType");
        final String createEmptyFolderStructure = props.getProperty("magentoCreateEmptyModuleStructure", "false");

        if (moduleName != null && nameSpace != null && moduleType != null) {
            if (projectBasedir.exists() && !nameSpace.equals("Company") && !moduleName.equals("MyModule")) {
                final Map<String, String> fileNames = new LinkedHashMap<String, String>();

                final String magentoBasePath = projectBasedir.getAbsolutePath() + "/src/main/php/app";

                final String etcModulesConfigOld = magentoBasePath + "/etc/modules/Company_MyModule.xml";
                final String configName = props.getProperty("magentoNameSpace") + "_" 
                        + props.getProperty("magentoModuleName") + ".xml";
                final String etcModulesConfigNew = magentoBasePath + "/etc/modules/" + configName;
                fileNames.put(etcModulesConfigOld, etcModulesConfigNew);

                final String localeOld = magentoBasePath + "/locale/en_US/Company_MyModule.csv";
                final String localeName = props.getProperty("magentoNameSpace") + "_"
                        + props.getProperty("magentoModuleName") + ".csv";
                final String localeNew = magentoBasePath + "/locale/en_US/" + localeName;
                fileNames.put(localeOld, localeNew);
                
                FileUtil.renameFiles(fileNames);
                
                if (createEmptyFolderStructure.equals("true")) {
                    final List<String> folderStruc = new ArrayList<String>();
                    final String modPath= magentoBasePath + "/code/" + moduleType + "/" + nameSpace + "/" + moduleName;
                    folderStruc.add( modPath + "/Block/Adminhtml");
                    folderStruc.add( modPath + "/controllers/Adminhtml");
                    folderStruc.add( modPath + "/etc");
                    folderStruc.add( modPath + "/Helper");
                    folderStruc.add( modPath + "/Model/Api");
                    folderStruc.add( modPath + "/Model/Entity");
                    folderStruc.add( modPath + "/Model/Mysql4");
                    folderStruc.add( modPath + "/Model/sql/"+moduleName.toLowerCase()+"_setup");
                    folderStruc.add( magentoBasePath + "/design/adminhtml/default/default/layout");
                    folderStruc.add( magentoBasePath + "/design/adminhtml/default/default/template");
                    folderStruc.add( magentoBasePath + "/etc/modules");
                    folderStruc.add( magentoBasePath + "/locale/en_US");
                    for (String string : folderStruc) {
                        Files.createDirectories(Paths.get(string));
                    }
                }
            }
        }
    }

}