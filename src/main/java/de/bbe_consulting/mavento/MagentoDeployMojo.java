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
import java.util.HashMap;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import de.bbe_consulting.mavento.helper.FileUtil;
import de.bbe_consulting.mavento.helper.MagentoUtil;

/**
 * Deploy current build artifact to Magento instance.<br/>
 * If run manually call it together with the package phase:<br/>
 * <pre>mvn package magento:deploy</pre>
 * 
 * @goal deploy
 * @requiresDependencyResolution compile
 * @author Erik Dannenberg
 */
public final class MagentoDeployMojo extends AbstractMagentoMojo {

	public void execute() throws MojoExecutionException, MojoFailureException {
		
		File buildArtifact = new File(project.getBuild().getDirectory()+"/"+project.getArtifactId()+"-"+project.getVersion()+".zip");
		if ( buildArtifact.exists() ) {
			if (magentoDeployType.equals("local")) {
				
				getLog().info("Checking for symlinks..");
				String srcDirName = project.getBasedir().getAbsolutePath()+"/src/main/php";
				File f = new File(magentoRootLocal+"/app/etc/local.xml");
				if (!f.exists()) {
					throw new MojoExecutionException("Could not find Magento root, did you forget to run 'mvn magento:install'? ;)");
				}
				Map<String,String> linkMap = new HashMap<String, String>();
				try {
					linkMap = MagentoUtil.collectSymlinks(srcDirName, magentoRootLocal);
				} catch (IOException e) {
					throw new MojoExecutionException(e.getMessage(), e);
				}
				
				for ( Map.Entry<String,String> fileNames : linkMap.entrySet()) {
					File t = new File(fileNames.getValue());
					if (t.exists()) {
						getLog().info("..deleting: "+fileNames.getValue());
						t.delete();
					}
				}
				getLog().info("..done.");
				
				getLog().info("Deploying local to: "+magentoRootLocal);
				try {
					FileUtil.unzipFile(buildArtifact.getAbsolutePath(), magentoRootLocal);
				} catch (IOException e) {
					throw new MojoExecutionException(e.getMessage(), e);
				}
				
				getLog().info("..extracting: "+buildArtifact.getName());
				getLog().info("..done.");
				
			} else {
				getLog().info("Deploying remote to: "+magentoRootRemote);
				throw new MojoExecutionException("oops, remote deploy not implemented yet :(");
			}
		} else if (project.getPackaging().equals("php")) {
			throw new MojoExecutionException("Could not find build artifact, forgot 'mvn package'? ;)");
		}
	}
	
}
