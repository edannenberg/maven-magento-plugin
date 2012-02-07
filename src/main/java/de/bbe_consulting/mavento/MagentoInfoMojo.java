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

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.w3c.dom.Document;

import de.bbe_consulting.mavento.helper.MagentoSqlUtil;
import de.bbe_consulting.mavento.helper.MagentoUtil;
import de.bbe_consulting.mavento.helper.MagentoXmlUtil;
import de.bbe_consulting.mavento.type.MagentoCoreConfig;
import de.bbe_consulting.mavento.type.MagentoModule;
import de.bbe_consulting.mavento.type.MagentoModuleComperator;
import de.bbe_consulting.mavento.type.MagentoVersion;

/**
 * Display some basic information of a Magento instance. This goal does not need an active Maven project. <br/>
 * It will however honor the properties in your pom.xml if called from a project.<br/>
 * To specify any Magento folder you can set the magentoPath property.<br/>
 * 
 * <pre>mvn magento:info -DmagentoPath=/path/to/magento/folder</pre>
 *
 * @goal info
 * @aggregator false
 * @requiresProject false
 * @author Erik Dannenberg
 */
public class MagentoInfoMojo extends AbstractMojo {

    /**
     * @parameter default-value="${project}"
     * @required
     */
    protected MavenProject project;
	
    /** 
     * Root path of the magento instance you want to scan.<br/>
     * @parameter expression="${magentoPath}
     */
    protected String magentoPath;
    
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		
		// try to use existing project if no magentoPath is specified
		if (magentoPath == null && project != null) {
			Properties projectProperties = project.getProperties();
			if ( projectProperties.containsKey("magento.root.local") ) {
				magentoPath = (String) projectProperties.get("magento.root.local");
			}
		}
		if (magentoPath == null) {
			throw new MojoExecutionException("Error: no magentoPath specified. Use -DmagentoPath=/your/path");
		}
		
		if (magentoPath.endsWith("/")) {
			magentoPath = magentoPath.substring(0, magentoPath.length()-1);
		}

		getLog().info("Scanning: "+Paths.get(magentoPath).toAbsolutePath().toString());
		getLog().info("");
		
		// try to find magento version
		Path appMage = Paths.get(magentoPath+"/app/Mage.php");
		MagentoVersion mVersion = null;
		try {
			mVersion = MagentoUtil.getMagentoVersion(appMage);
		} catch (Exception e) {
			getLog().info("..could not find Magento version.");
		} 		
		if (mVersion != null) {
			getLog().info("Version: Magento "+mVersion.toString());
		}
		
		// parse sql properties from local.xml
		Path localXmlPath = Paths.get(magentoPath+"/app/etc/local.xml");
		Document localXml = null;
		if ( Files.exists(localXmlPath) ) {
			localXml = MagentoXmlUtil.readXmlFile(localXmlPath.toAbsolutePath().toString());
		} else {
			throw new MojoExecutionException("Could not read or parse /app/etc/local.xml");
		}
		Map<String, String> dbSettings = MagentoXmlUtil.getDbValues(localXml);
		String jdbcUrl = MagentoSqlUtil.getJdbcUrl(dbSettings.get("host"), dbSettings.get("port"), dbSettings.get("dbname"));

		// fetch installdate
		String magentoInstallDate = MagentoXmlUtil.getMagentoInstallData(localXml);
		getLog().info("Installed: " + magentoInstallDate);
		getLog().info("");
		
		// read baseUrl
		MagentoCoreConfig baseUrl = null;
		try {
			baseUrl = new MagentoCoreConfig("web/unsecure/base_url");
		} catch (Exception e) {
			throw new MojoExecutionException("Error creating config entry. "+e.getMessage(), e);
		}
		
		String sqlError = "valid";
		try {
			baseUrl = MagentoSqlUtil.getCoreConfigData(baseUrl, dbSettings.get("user"), dbSettings.get("password"), jdbcUrl, getLog());
			getLog().info("URL: " + baseUrl.getValue());
			getLog().info("");
		} catch (MojoExecutionException e) {
			sqlError = e.getMessage();
		}
		
		getLog().info("Database: "+dbSettings.get("dbname")+" via "+dbSettings.get("user")+"@"+dbSettings.get("host")+":"+dbSettings.get("port"));
		getLog().info("Connection: "+sqlError);
		getLog().info("");
		
		// parse modules
		Path modulesXmlPath = Paths.get(magentoPath+"/app/etc/modules");
		if (!Files.exists(modulesXmlPath)) {
			throw new MojoExecutionException("Could not find /app/etc/modules directory.");
		}
		
		DirectoryStream<Path> files = null;
		ArrayList<MagentoModule> localModules = new ArrayList<MagentoModule>();
		ArrayList<MagentoModule> communityModules = new ArrayList<MagentoModule>();
		try {
			files = Files.newDirectoryStream( modulesXmlPath );
			for ( Path path : files ) {
				if ( !path.getFileName().toString().startsWith("Mage") ) {
					MagentoModule m = new MagentoModule(path);
					if (m.getCodePool().equals("local")) {
						localModules.add(m);
					} else {
						communityModules.add(m);
					}
				}
			}
		} catch (IOException e) {
			throw new MojoExecutionException("Could not read modules directory. " + e.getMessage(), e);
		} finally {
		  try {
			files.close();
		  } catch (IOException e) {
			  throw new MojoExecutionException("Error closing directory stream. " + e.getMessage(), e);
		  }
		}
		
		// print module sorted module list
		MagentoModuleComperator mmc = new MagentoModuleComperator();
		Collections.sort(localModules, mmc);
		Collections.sort(communityModules, mmc);
		
		getLog().info("Installed modules in..");
		
		getLog().info("..local: ");
		for (MagentoModule m : localModules) {
			getLog().info(m.getNamespace()+"_"+m.getName()+" version: "+m.getVersion()+" active: "+m.isActive());
		}
		if (localModules.size() == 0) { getLog().info("--none--"); }
		getLog().info("");
		
		getLog().info("..community: ");
		for (MagentoModule m : communityModules) {
			getLog().info(m.getNamespace()+"_"+m.getName()+" version: "+m.getVersion()+" active: "+m.isActive());
		}
		if (communityModules.size() == 0) { getLog().info("--none--"); }
		getLog().info("");
		
		// check local overlays for content
		getLog().info("Overlay status..");
		int fileCount = -1;
		File localMage = new File(magentoPath+"/app/code/local/Mage");
		if (localMage.exists()) {
			fileCount = localMage.list().length;
			if (fileCount > 0) { getLog().info("local/Mage: "+localMage.list().length+" file(s)"); }
		}
		File localVarien = new File(magentoPath+"/app/code/local/Varien");
		
		if (localVarien.exists()) {
			fileCount = localVarien.list().length;
			if (fileCount > 0) { getLog().info("local/Varien: "+localVarien.list().length+" file(s)"); }
		}
		
		if (fileCount == -1) {
			getLog().info("..not in use.");
		}
		
	}
	
}
