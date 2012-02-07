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
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumSet;
import java.util.Map;

import javax.xml.transform.TransformerException;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.w3c.dom.Document;

import de.bbe_consulting.mavento.helper.FileUtil;
import de.bbe_consulting.mavento.helper.MagentoSqlUtil;
import de.bbe_consulting.mavento.helper.MagentoUtil;
import de.bbe_consulting.mavento.helper.MagentoXmlUtil;
import de.bbe_consulting.mavento.helper.visitor.CopyFilesVisitor;
import de.bbe_consulting.mavento.type.MagentoVersion;

/**
 * Create a dependency artifact from a running Magento instance. This goal does not need an active Maven project.<br/>
 * <pre>mvn magento:artifact -DmagentoPath=/path/to/magento/folder</pre>
 *
 * @goal artifact
 * @aggregator false
 * @requiresProject false
 * @author Erik Dannenberg
 */
public class MagentoArtifactMojo extends AbstractMojo {

    /** 
     * Root path of the magento instance you want to create an artifact of.<br/>
     * @parameter expression="${magentoPath}
     * @required
     */
    protected String magentoPath;
    
    /** 
     * Where to write the jar file. Default is Magento foldername+timestamp.<br/>
     * @parameter expression="${jarFileName}" default=""
     */    
    protected String jarFileName;
    
    /** 
     * Dd settings local.xml will not be scrambled if set to true.<br/>
     * @parameter expression="${keepDbSettings}" default-value="false"
     */
    protected Boolean magentoKeepDbSettings;
    
    /** 
     * Truncate magento log/report tables *before* inital dump.<br/>
     * @parameter expression="${truncateLogTables}" default-value="false"
     */
    protected Boolean magentoTruncateLogs;
    
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		
		if (magentoPath.endsWith("/")) {
			magentoPath = magentoPath.substring(0, magentoPath.length()-1);
		}

		getLog().info("Scanning: "+Paths.get(magentoPath).toAbsolutePath().toString());

		// try to find magento version
		Path appMage = Paths.get(magentoPath+"/app/Mage.php");
		MagentoVersion mVersion = null;
		try {
			mVersion = MagentoUtil.getMagentoVersion(appMage);
		} catch (Exception e) {
			getLog().info("..could not find Magento version.");
		} 		
		if (mVersion != null) {
			getLog().info("..found Magento "+mVersion.toString());
		}
		
		// get sql settings from local.xml
		Path localXmlPath = Paths.get(magentoPath+"/app/etc/local.xml");
		Document localXml = null;
		if ( Files.exists(localXmlPath) ) {
			localXml = MagentoXmlUtil.readXmlFile(localXmlPath.toAbsolutePath().toString());
		} else {
			throw new MojoExecutionException("Could not read or parse /app/etc/local.xml");
		}
		Map<String, String> dbSettings = MagentoXmlUtil.getDbValues(localXml);
		getLog().info("..done.");

		getLog().info("Creating snapshot..");
		Path tmpDir = null;
		try {
			tmpDir = Files.createTempDirectory("mavento_artifact_");
		} catch (IOException e) {
			throw new MojoExecutionException("Could not create tmp dir. "+e.getMessage(), e);
		}
		
		// copy magento source to tmp dir
		CopyFilesVisitor cv = new CopyFilesVisitor(Paths.get(magentoPath), tmpDir, false);
    	try {
			Files.walkFileTree(Paths.get(magentoPath),EnumSet.of(FileVisitOption.FOLLOW_LINKS),Integer.MAX_VALUE ,cv);
		} catch (IOException e) {
			throw new MojoExecutionException("Error copying to tmp dir. "+e.getMessage(), e);
		} catch (Exception e) {
			throw new MojoExecutionException("Error copying to tmp dir. "+e.getMessage(), e);
		}
    	getLog().info("..done.");
    	
    	// truncate log tables before dump
    	if (magentoTruncateLogs) {
    		getLog().info("Cleaning log tables..");
    		String jdbcUrl = MagentoSqlUtil.getJdbcUrl(dbSettings.get("host"), dbSettings.get("port"), dbSettings.get("dbname"));
    		MagentoSqlUtil.truncateLogTables(dbSettings.get("user"), dbSettings.get("password"), jdbcUrl, getLog());
    		getLog().info("..done.");
    	}
    	
    	// dump db
		try {
			Files.createDirectories(Paths.get(tmpDir.toAbsolutePath().toString()+"/mavento_setup/sql"));
		} catch (IOException e) {
			throw new MojoExecutionException("Error creating directory. "+e.getMessage(), e);
		}	
    	String dumpFile = Paths.get(tmpDir.toAbsolutePath().toString()+"/mavento_setup/sql/magento.sql").toAbsolutePath().toString();
    	MagentoSqlUtil.dumpSqlDb( dumpFile, dbSettings.get("user"), dbSettings.get("password"), dbSettings.get("host"), dbSettings.get("port"), dbSettings.get("dbname"), getLog());
    	
    	// scramble db settings in local.xml
    	if (!magentoKeepDbSettings) {
    		getLog().info("Scrambling original database settings in local.xml..");
    		Path tmpLocalXml = Paths.get(tmpDir.toAbsolutePath().toString()+"/app/etc/local.xml");
    		localXml = MagentoXmlUtil.readXmlFile(tmpLocalXml.toAbsolutePath().toString());
    		MagentoXmlUtil.updateDbValues("localhost", "heinzi", "floppel", "db_magento", localXml);
    		
    	    try {
    	    	MagentoXmlUtil.writeXmlFile(MagentoXmlUtil.transformXmlToString(localXml), tmpLocalXml.toAbsolutePath().toString());
    		} catch (TransformerException e) {
    			throw new MojoExecutionException(e.getMessage(), e);
    		}    		
    		getLog().info("..done.");
    	}
    	
    	// clean /var before we build the artifact
    	try {
    		FileUtil.deleteFile(tmpDir.toString()+"/var/cache", getLog());
    		FileUtil.deleteFile(tmpDir.toString()+"/var/session", getLog());
		} catch (IOException e) {
			throw new MojoExecutionException("Error deleting cache or session directories. "+e.getMessage(), e);
		}
    	
    	// create the jar
    	SimpleDateFormat sdf = new SimpleDateFormat();
    	sdf.applyPattern( "yyyyMMddHHmmss" );
        String defaultVersion = sdf.format(new Date())+"-SNAPSHOT";
    	if (jarFileName == null || jarFileName.equals("")) {
	        jarFileName = Paths.get(magentoPath).getFileName().toString()+"-"+defaultVersion+".jar";
    	} else if ( !jarFileName.endsWith(".jar") ) {
    		jarFileName += "-"+defaultVersion+".jar";
    	}
        getLog().info("Creating jar file: "+jarFileName+ "..");
    	FileUtil.createJar( jarFileName, tmpDir.toAbsolutePath().toString());
    	getLog().info("..done.");
    	
    	// clean up
    	try {
    		getLog().info("Cleaning up..");
			FileUtil.deleteFile(tmpDir.toString(), getLog());
			getLog().info("..done.");
		} catch (IOException e) {
			throw new MojoExecutionException("Error deleting tmp dir. "+e.getMessage(), e);
		}
    	
    	getLog().info("");
    	getLog().info("Great success! "+jarFileName+" is now ready for install.");
    	getLog().info("Use:\n");
    	getLog().info("mvn install:install-file -Dpackaging=jar -Dfile="+jarFileName+" -Dversion="+defaultVersion+" -DgroupId= -DartifactId= \n");
    	getLog().info("..to install the jar into your local maven repository.");
	}

}
