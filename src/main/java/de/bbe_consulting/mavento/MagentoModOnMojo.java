/**
 * Copyright 2011-2013 BBe Consulting GmbH
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import javax.xml.transform.TransformerException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.w3c.dom.Document;

import de.bbe_consulting.mavento.helper.MagentoXmlUtil;

/**
 * Enable a Magento Module. This goal does not need an active Maven project. <br/>
 * It will however honor the properties in your pom.xml if called from a
 * project.<br/>
 * Use -DmagentoPath=/path/to/magento to override.<br/>
 * 
 * <pre>
 * mvn magento:modoff -DmodKey=Some_Module -DmagentoPath=/path/to/magento/folder
 * </pre>
 * 
 * @goal modon
 * @aggregator false
 * @requiresProject false
 * @author Erik Dannenberg
 */
public class MagentoModOnMojo extends AbstractMagentoSimpleMojo {

    /**
     * Module key. Use magento:info to list all installed modules.<br/>
     * 
     * @parameter expression="${modKey}"
     * @required
     */
    protected String modKey;
    
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        initMojo();
        getLog().info("Scanning: " + magentoPath);
        getLog().info("");
        if (mVersion != null) {
            getLog().info("Version: Magento " + mVersion.toString());
        }

        // read module.xml from app/etc/modules/
        final Path moduleXmlPath = Paths.get(magentoPath + "/app/etc/modules/" + modKey + ".xml");
        Document moduleXml = null;
        if (Files.exists(moduleXmlPath)) {
            moduleXml = MagentoXmlUtil.readXmlFile(moduleXmlPath.toAbsolutePath().toString());
        } else {
            throw new MojoExecutionException("Could not read or parse /app/etc/modules/" + modKey + ".xml" +
                    " Use -DmagentoPath= to set Magento dir.");
        }
        final Map<String, String> xmlNodes = new HashMap<String, String>();
        xmlNodes.put("active", "true");
        MagentoXmlUtil.updateXmlValues(xmlNodes, moduleXml);
        
        try {
            MagentoXmlUtil.writeXmlFile(
                            MagentoXmlUtil.transformXmlToString(moduleXml),
                            moduleXmlPath.toAbsolutePath().toString() );
        } catch (TransformerException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        
    }
}
