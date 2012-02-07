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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import de.bbe_consulting.mavento.helper.FileUtil;
import de.bbe_consulting.mavento.helper.MagentoUtil;

/**
 * Symlink project source to local Magento instance.
 *
 * @goal symlink
 * @requiresDependencyResolution compile
 * @author Erik Dannenberg
 */
public class MagentoSymlinkMojo extends AbstractMagentoMojo {

	public void execute() throws MojoExecutionException, MojoFailureException {
		
		String srcDirName = project.getBasedir().getAbsolutePath()+"/src/main/php";
		if (magentoDeployType.equals("local")) {
			if (Files.notExists(Paths.get(magentoRootLocal+"/app/etc/local.xml"))) {
				throw new MojoExecutionException("Could not find Magento root, did you forget to run 'mvn magento:install'? ;)");
			}
			Map<String,String> linkMap = new HashMap<String, String>();
			try {
				linkMap = MagentoUtil.collectSymlinks(srcDirName, magentoRootLocal);
			} catch (IOException e) {
				throw new MojoExecutionException("IO Error while collecting symlinks. " + e.getMessage(), e);
			}
			
			try {
				FileUtil.symlinkFiles(linkMap, getLog());
			} catch (IOException e) {
				throw new MojoExecutionException(e.getMessage(), e);
			}
			
		} else {
			throw new MojoExecutionException("Symlinking for remote deploy not implemented.");			
		}
	}
	
}
