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

package de.bbe_consulting.mavento.type;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.w3c.dom.Document;

import de.bbe_consulting.mavento.helper.MagentoXmlUtil;

/**
 * Magento Module Config
 * 
 * @author Erik Dannenberg
 */
public class MagentoModule {

    private String name;
    private String namespace;
    private String version;
    private String codePool;
    private Boolean active;

    public MagentoModule(Path modulesConfigXmlPath)
            throws MojoExecutionException {

        final Path magentoAppRoot = modulesConfigXmlPath.getParent().getParent().getParent().toAbsolutePath();
        final Document modulXml = MagentoXmlUtil.readXmlFile(modulesConfigXmlPath.toAbsolutePath().toString());

        // parse /etc/modules/ config
        final Map<String, String> moduleConfig = MagentoXmlUtil.getEtcModulesValues(modulXml);
        name = moduleConfig.get("moduleName");
        namespace = moduleConfig.get("nameSpace");
        codePool = moduleConfig.get("codePool");
        active = Boolean.parseBoolean(moduleConfig.get("active"));

        // parse modules config.xml for version
        final Path configXmlPath = Paths.get(magentoAppRoot + "/code/" + codePool
                + "/" + namespace + "/" + name + "/etc/config.xml");
        if (Files.exists(configXmlPath)) {
            final Document configXml = MagentoXmlUtil.readXmlFile(configXmlPath.toAbsolutePath().toString());
            version = MagentoXmlUtil.getModuleVersion(configXml, namespace + "_" + name);
        }

    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getCodePool() {
        return codePool;
    }

    public void setCodePool(String codePool) {
        this.codePool = codePool;
    }

    public Boolean isActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

}
