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

package de.bbe_consulting.mavento.helper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

/**
 * Maven related helpers.
 * 
 * @author Erik Dannenberg
 */
public final class MavenUtil {

    /**
     * Private constructor, only static methods in this util class
     */
    private MavenUtil() {
    }

    /**
     * Extracts all compile dependencies.
     * 
     * @param targetDirectory
     * @param project
     * @param logger
     * @throws MojoExecutionException
     * @throws IOException
     */
    public static void extractCompileDependencies(String targetDirectory, MavenProject project, Log logger)
            throws MojoExecutionException, IOException {

        final Set<Artifact> projectDependencies = project.getDependencyArtifacts();
        // create temp dir if it doesn't exist
        final Path f = Paths.get(targetDirectory);
        if (!Files.exists(f)) {
            Files.createDirectories(f);
        }

        final Properties p = project.getProperties();
        final String testId = (String)p.get("magento.test.artifact.id");
        final String testGroupdId = (String)p.get("magento.test.artifact.group.id");
        final String testVersion = (String)p.get("magento.test.artifact.version");
        
        if (testId == null && testGroupdId == null && testVersion == null) {
            throw new MojoExecutionException("Error: One of the magento.test.artifact.* properties" +
                    " is not defined. Please fix your pom.xml.");
        }
        
        // cycle through project dependencies
        for (Iterator<Artifact> artifactIterator = projectDependencies.iterator(); artifactIterator.hasNext();) {
            Artifact artifact = artifactIterator.next();
            if ("compile".equals(artifact.getScope())) {
                if (!artifact.getArtifactId().equals(testId) &&
                        !artifact.getGroupId().equals(testGroupdId) &&
                        !artifact.getVersion().equals(testVersion)) {
                    logger.info("Extracting " + artifact.getGroupId() + ":"
                            + artifact.getArtifactId() + ":"
                            + artifact.getVersion() + "..");
                    String artifactPath = artifact.getFile().getPath();
                    FileUtil.unzipFile(artifactPath, targetDirectory);
                }
            }
        }
    }

    /**
     * Filter pom.xml properties for magento.config entries and convert them into
     * magento core_config format.
     * 
     * @param project
     * @param tokenMap
     * @param logger
     * @return Map<String, String> tokenMap with added magento.misc properties 
     */
    public static Map<String, String> addMagentoMiscProperties(
            MavenProject project, Map<String, String> tokenMap, Log logger) {
        final Properties p = project.getProperties();
        for (Enumeration<?> e = p.keys(); e.hasMoreElements();) {
            String propertyKey = (String) e.nextElement();
            if (propertyKey.startsWith("magento.config.")) {
                String finalToken = propertyKey.substring("magento.config.".length()).replace(".", "/");
                tokenMap.put(finalToken, p.getProperty(propertyKey));
            }
        }
        return tokenMap;
    }

}
