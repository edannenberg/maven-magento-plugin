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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.project.MavenProject;

import de.bbe_consulting.mavento.helper.MagentoUtil;
import de.bbe_consulting.mavento.type.MagentoVersion;

/**
 * Base class for simple mojos that don't need a Maven Project
 * 
 * @author Erik Dannenberg
 */
public abstract class AbstractMagentoSimpleMojo extends AbstractMojo {

    /**
     * @parameter default-value="${project}"
     * @required
     */
    protected MavenProject project;

    /**
     * Root path of the magento instance you want to scan.<br/>
     * 
     * @parameter expression="${magentoPath}
     */
    protected String magentoPath;
    
    protected MagentoVersion mVersion;
    
    protected void initMojo() {

        // try to use existing project if no magentoPath is specified
        if (magentoPath == null && project != null) {
            final Properties projectProperties = project.getProperties();
            if (projectProperties.containsKey("magento.root.local")) {
                magentoPath = (String) projectProperties.get("magento.root.local");
            }
        }
        if (magentoPath == null) {
            magentoPath = Paths.get(".").toString();
        }
        magentoPath = Paths.get(magentoPath).toAbsolutePath().toString();

        if (magentoPath.endsWith("/")) {
            magentoPath = magentoPath.substring(0, magentoPath.length() - 1);
        } else if (magentoPath.endsWith("/.")) {
            magentoPath = magentoPath.substring(0, magentoPath.length() - 2);
        }

        // try to find magento version
        final Path appMage = Paths.get(magentoPath + "/app/Mage.php");
        try {
            mVersion = MagentoUtil.getMagentoVersion(appMage);
        } catch (Exception e) {
            getLog().info("..could not find Magento version.");
        }

    }

}
