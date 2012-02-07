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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;

/**
 * Create a new Magento project/module from a template (aka archetype). This goal does not need an active Maven project.
 *
 * @goal archetype
 * @aggregator false
 * @requiresProject false
 * @author Erik Dannenberg
 */
public class MagentoArchetypeMojo extends AbstractArchetypeMojo {

    public void execute() throws MojoExecutionException, MojoFailureException
    {
        Properties executionProperties = session.getExecutionProperties();
        
        ArchetypeGenerationRequest request = new ArchetypeGenerationRequest()
            .setArchetypeGroupId( archetypeGroupId )
            .setArchetypeArtifactId( archetypeArtifactId )
            .setArchetypeVersion( archetypeVersion )
            .setOutputDirectory( basedir.getAbsolutePath() )
            .setLocalRepository( localRepository )
            .setArchetypeRepository( archetypeRepository )
            .setRemoteArtifactRepositories( remoteArtifactRepositories );

        try
        {
            if ( interactiveMode.booleanValue() )
            {
                getLog().info( "Generating project in Interactive mode" );
            }
            else
            {
                getLog().info( "Generating project in Batch mode" );
            }
            
            Properties rp = new Properties();
            rp.setProperty("magentoArchetypeIdentifier", "archetype");
            request.setProperties(rp);
            
            selector.selectArchetype( request, interactiveMode, archetypeCatalog );
            
            List<String> requiredProperties = archetypeProperties.getRequiredProperties(request, executionProperties);
            
            // check for existing pom in current dir, if yes try to set some defaults
            File p = new File("pom.xml");
            String defaultGroupdId = "de.bbe-consulting.magento";
            if ( p.exists() ) {
    			SAXReader reader = new SAXReader();
    	        Document document = reader.read(p);
            	String parentArtifactId = getXmlNodeValueFromPom("/x:project/x:artifactId", document);
            	defaultGroupdId = getXmlNodeValueFromPom("/x:project/x:groupId", document)+"."+parentArtifactId;
            }
            
            String reqGroupdId = queryer.getPropertyValue("groupId", defaultGroupdId);
            executionProperties.put("groupId", reqGroupdId);
            
            String reqArtifactId = queryer.getPropertyValue("artifactId", null);
            executionProperties.put("artifactId", reqArtifactId);
            
            String reqVersion = queryer.getPropertyValue("version", "1.0-SNAPSHOT");
            executionProperties.put("version", reqVersion);
            
            if (requiredProperties.contains("magentoModuleName")) {
	            String reqMagentoModuleName = queryer.getPropertyValue("magentoModuleName", null);
	            executionProperties.put("magentoModuleName", reqMagentoModuleName);
	            executionProperties.put("magentoModuleNameLowerCase", reqMagentoModuleName.toLowerCase());
            }
            
            if (requiredProperties.contains("magentoNameSpace")) {
	            String reqMagentoNamespace = queryer.getPropertyValue("magentoNamespace", null);
	            executionProperties.put("magentoNameSpace", reqMagentoNamespace);
            }
            
            if (requiredProperties.contains("magentoModuleType")) {
	            String reqMagentoNamespace = queryer.getPropertyValue("magentoModuleType", "local");
	            executionProperties.put("magentoModuleType", reqMagentoNamespace);
            }
            
            configurator.configureArchetype( request, false, executionProperties );
            
            ArchetypeGenerationResult generationResult = archetype.generateProjectFromArchetype( request );

            if ( generationResult.getCause() != null )
            {
                throw new MojoFailureException( generationResult.getCause(), generationResult.getCause().getMessage(),
                                                generationResult.getCause().getMessage() );
            }
           
        }
        catch ( Exception ex )
        {
            throw (MojoFailureException) new MojoFailureException( ex.getMessage() ).initCause( ex );
        }

        String artifactId = request.getArtifactId();

        String postArchetypeGenerationGoals = request.getArchetypeGoals();

        if ( StringUtils.isEmpty( postArchetypeGenerationGoals ) )
        {
            postArchetypeGenerationGoals = goals;
        }

        if ( StringUtils.isNotEmpty( postArchetypeGenerationGoals ) )
        {
            invokePostArchetypeGenerationGoals( postArchetypeGenerationGoals, artifactId );
        }
        
        try {
			fixModuleStructure(artifactId, executionProperties);
		} catch (IOException e) {
			throw new MojoExecutionException("Error while finishing up module structure!", e);
		}
        
    }

    private void fixModuleStructure (String artifactId, Properties props) throws IOException {
    	
    	File projectBasedir = new File( basedir, artifactId );
    	String moduleName = props.getProperty("magentoModuleName");
    	String nameSpace = props.getProperty("magentoNameSpace");
    	
    	if (moduleName != null && nameSpace != null) 
    	{
	        if ( projectBasedir.exists() && !nameSpace.equals("Company") && !moduleName.equals("MyModule") )
	        {
	        	LinkedHashMap<String,String> fileNames = new LinkedHashMap<String,String>();
	        	
	        	String magentoBasePath = projectBasedir.getAbsolutePath()+"/src/main/php/app";
	        	
	        	String etcModulesConfigOld = magentoBasePath+"/etc/modules/Company_MyModule.xml";
	        	String configName = props.getProperty("magentoNameSpace")+"_"+props.getProperty("magentoModuleName")+".xml";
	        	String etcModulesConfigNew = magentoBasePath+"/etc/modules/"+configName;
	        	fileNames.put(etcModulesConfigOld, etcModulesConfigNew);
	        	
	        	String localeOld = magentoBasePath+"/locale/en_US/Company_MyModule.csv";
	        	String localeName = props.getProperty("magentoNameSpace")+"_"+props.getProperty("magentoModuleName")+".csv";
	        	String localeNew = magentoBasePath+"/locale/en_US/"+localeName;
	        	fileNames.put(localeOld, localeNew);
        	
	        	FileUtil.renameFiles(fileNames);
	        }
    	}
    }
    
}