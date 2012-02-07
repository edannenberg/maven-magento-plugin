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

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;

/**
 * Maven Util Class
 * 
 * @author Erik Dannenberg
 */
public final class MavenUtil {

	/*
	 * Private constructor, only static methods in this util class 
	 */
	private MavenUtil() {
	}
	
	public static void extractCompileDependencies(String targetDirectory, MavenProject project, Log logger) throws MojoExecutionException, IOException {
		
    	Set<?> projectDependencies = project.getDependencyArtifacts();
    	// create temp dir if it doesn't exist
        File f = new File(targetDirectory);
        if ( !f.exists() )
        {
            f.mkdirs();
        } else {
        	try {
				FileUtils.cleanDirectory(f);
			} catch (IOException e) {
				throw new MojoExecutionException(e.getMessage(), e);
			}
        }
    	
        // cycle through project dependencies
    	for (Iterator<?> artifactIterator = projectDependencies.iterator() ; artifactIterator.hasNext() ;) {
    	  Artifact artifact = (Artifact) artifactIterator.next();
    		  if ( "compile".equals(artifact.getScope()) ) {
	    	  	  logger.info("Extracting "+artifact.getGroupId()+":"+artifact.getArtifactId()+":"+artifact.getVersion()+"..");
	    		  String artifactPath = artifact.getFile().getPath();
	    		  FileUtil.unzipFile(artifactPath, targetDirectory);
    		  }
    	}
	}
	
	// filter pom.xml properties for magento.config entries and put them into magento core_config format
	public static Map<String, String> addMagentoMiscProperties(MavenProject project, Map<String, String> tokenMap, Log logger) {
		Properties p = project.getProperties();
		for (Enumeration<?> e = p.keys() ; e.hasMoreElements() ;) {
			String propertyKey = (String) e.nextElement();
			if (propertyKey.startsWith("magento.config.")) {
				String finalToken = propertyKey.substring("magento.config.".length()).replace(".", "/");
				tokenMap.put(finalToken, p.getProperty(propertyKey) );
			}
	     }
		return tokenMap;
	}
	
}
