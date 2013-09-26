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

import java.io.File;
import java.util.List;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.project.MavenProject;
import org.apache.maven.execution.MavenSession;

/**
 * Base class for magento mojos.
 * @author Erik Dannenberg
 */
public abstract class AbstractMagentoMojo extends AbstractMojo {

    /**
     * @parameter default-value="${project}"
     * @required
     * @readonly 
     */
    protected MavenProject project;

    /**
     * The Maven Session Object
     *
     * @parameter expression="${session}"
     * @required
     * @readonly
     */
    protected MavenSession session;

    /**
     * The Maven PluginManager Object
     *
     * @component
     * @required
     */
    protected BuildPluginManager pluginManager;

    /**
     * @parameter default-value="${project.dependencies}
     * @required
     * @readonly
     */
    protected List<Dependency> dependencies;
    
    /**
     * Project build directory.
     * 
     * @parameter expression="${magento.build.directory}"
     * @required
     */
    protected File buildDirectory;
    
    /**
     * Magento version, this should always reflect the actual Magento version.<br/>
     * For available versions have a look at: <br/>
     * <pre>http://maven.bbe-consulting.de/content/repositories/releases/com/varien/magento/</pre>
     * 
     * @parameter expression="${magento.version}"
     * @required
     */
    protected String magentoVersion;

    /** 
     * Deploy type. Did not backport remote deploy yet, so only local for now. local|remote<br/>
     * 
     * @parameter expression="${magento.deploy.type}" default-value="local"
     */
    protected String magentoDeployType;
    
    /** 
     * Local root directory of Magento installation.
     * 
     * @parameter expression="${magento.root.local}" 
     * @required
     */
    protected String magentoRootLocal;
    
    /** 
     * Remote root directory of Magento installation.
     * 
     * @parameter expression="${magento.root.remote}"
     */
    protected String magentoRootRemote;
    
    /** 
     * Url for remote deploy.
     * 
     * @parameter expression="${remote.scp.host}"
     */
    protected String remoteScpHost;
    
    /**
     * Username on remote system. Expects ssh public key access.
     * 
     * @parameter expression="${remote.scp.username}"
     */
    protected String remoteScpUsername;
  
}
