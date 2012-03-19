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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.CommandLineUtils.StringStreamConsumer;

import de.bbe_consulting.mavento.type.MagentoVersion;

/**
 * Magento Util Class, general stuff
 * 
 * @author Erik Dannenberg
 */
public final class MagentoUtil {

	/**
	 * Private constructor, only static methods in this util class 
	 */
	private MagentoUtil() {
	}
	
	/**
	 * Collects targets to be symlinked from Maven project to Magento installation
	 * @param String sourceBaseDir 
	 * @param String targetBaseDir
	 * @return HashMap<String,String>
	 * @throws IOException 
	 */
	public static Map<String,String> collectSymlinks(String sourceBaseDir, String targetBaseDir) throws IOException {
		
		Map<String, String> linkMap = new HashMap<String, String>();
		Map<String, String> tempMap = new HashMap<String, String>();
		// crawl some standard directories
		if (Files.exists(Paths.get(sourceBaseDir+"/app"))) {
			tempMap = getSubFileLinkMap(sourceBaseDir+"/app/code/local", targetBaseDir+"/app/code/local");
			// go one level deeper to skip namespace and only link the actual module directories
			for ( Map.Entry<String,String> fileNames : tempMap.entrySet()) {
				linkMap.putAll(getSubFileLinkMap(fileNames.getKey(), fileNames.getValue()));
			}
			tempMap = getSubFileLinkMap(sourceBaseDir+"/app/code/community", targetBaseDir+"/app/code/community");
			// go one level deeper to skip namespace and only link the actual module directories
			for ( Map.Entry<String,String> fileNames : tempMap.entrySet()) {
				linkMap.putAll(getSubFileLinkMap(fileNames.getKey(), fileNames.getValue()));
			}
			// crawl for possible layout/template/skin files
			tempMap.clear();
			tempMap.put(sourceBaseDir+"/app/design/adminhtml", targetBaseDir+"/app/design/adminhtml");
			tempMap.put(sourceBaseDir+"/app/design/frontend", targetBaseDir+"/app/design/frontend");
			tempMap.put(sourceBaseDir+"/skin/adminhtml", targetBaseDir+"/skin/adminhtml");
			tempMap.put(sourceBaseDir+"/skin/frontend", targetBaseDir+"/skin/frontend");
			linkMap.putAll(collectLayoutSymlinks(tempMap));
		
			// crawl app/etc/modules/ for files
			linkMap.putAll(getSubFileLinkMap(sourceBaseDir+"/app/etc/modules", targetBaseDir+"/app/etc/modules"));
			// crawl app/locale/en_US
			linkMap.putAll(getSubFileLinkMap(sourceBaseDir+"/app/locale/en_US", targetBaseDir+"/app/locale/en_US"));
			
		}
		// crawl js/lib folders
		linkMap.putAll(getSubFileLinkMap(sourceBaseDir+"/js", targetBaseDir+"/js"));
		linkMap.putAll(getSubFileLinkMap(sourceBaseDir+"/lib", targetBaseDir+"/lib"));
		
		// everything else, minus the base directories we already took care of
		tempMap = getSubFileLinkMap(sourceBaseDir, targetBaseDir);
		tempMap.remove(sourceBaseDir+"/app");
		tempMap.remove(sourceBaseDir+"/skin");
		tempMap.remove(sourceBaseDir+"/js");
		tempMap.remove(sourceBaseDir+"/lib");
		tempMap.remove(sourceBaseDir+"/magento_bootstrap.php");
		linkMap.putAll(tempMap);
		
		return linkMap;
	}
	
	/**
	 * Crawls magento layout directories for possible symlink targets
	 * @param baseDirectories
	 * @return HashMap<String,String> key: original file, value: symlink target
	 * @throws IOException
	 */
	private static Map<String,String> collectLayoutSymlinks(Map<String,String> baseDirectories) throws IOException {
		Map<String, String> linkMap = new HashMap<String, String>();
		Map<String, String> firstLevelMap = new HashMap<String, String>();
		// looks like eye cancer but oh well
		for ( Map.Entry<String,String> firstLevelEntry : baseDirectories.entrySet()) {
			firstLevelMap = getSubFileLinkMap(firstLevelEntry.getKey(), firstLevelEntry.getValue());
			for ( Map.Entry<String,String> secondLevelEntry : firstLevelMap.entrySet()) {
				Map<String, String> secondLevelMap = new HashMap<String, String>();
				secondLevelMap = getSubFileLinkMap(secondLevelEntry.getKey(), secondLevelEntry.getValue());
				for ( Map.Entry<String,String> finalLevelEntry : secondLevelMap.entrySet()) {
					Map<String, String> finalLevelMap = new HashMap<String, String>();
					finalLevelMap = getSubFileLinkMap(finalLevelEntry.getKey(), finalLevelEntry.getValue());
					if (finalLevelMap.containsKey(finalLevelEntry.getKey()+"/template")) {
						finalLevelMap.remove(finalLevelEntry.getKey()+"/template");
						linkMap.putAll(getSubFileLinkMap(finalLevelEntry.getKey()+"/template", finalLevelEntry.getValue()+"/template"));
					}
					if (finalLevelMap.containsKey(finalLevelEntry.getKey()+"/layout")) {
						finalLevelMap.remove(finalLevelEntry.getKey()+"/layout");
						linkMap.putAll(getSubFileLinkMap(finalLevelEntry.getKey()+"/layout", finalLevelEntry.getValue()+"/layout"));
					}
					linkMap.putAll(finalLevelMap);
				}
			}
		}
		return linkMap;
	}
	
	/**
	 * Get direct subfiles/folders of baseDirName and put them into a hash map
	 * @param baseDirName
	 * @param targetBaseDir
	 * @return HashMap<String,String> key: original file, value: symlink target
	 * @throws IOException
	 */
	private static Map<String,String> getSubFileLinkMap(String baseDirName, String targetBaseDir) throws IOException {
		Path base = Paths.get(baseDirName);
		HashMap<String, String> r = new HashMap<String, String>();
		if (Files.isDirectory(base)) {
			DirectoryStream<Path> files = null;
			try {
				files = Files.newDirectoryStream( base );
                for (Path path : files) {
                    if (!path.toString().contains(".svn")) {
                        r.put(path.toAbsolutePath().toString(), targetBaseDir + "/" + path.getFileName());
                    }
                }
			} finally {
			  files.close();
			}
        }
		return r;
	}
	
    /**
     * Execute a pear command
     *
     * @param Full path to pear executable
     * @param Arguments
     * @param Working directory
     * @param Maven mojo logger instance
     * @throws MojoExecutionException
     */	
	public static void executePearCommand(String executable, String[] arguments, String targetDir, MagentoVersion magentoVersion, Log logger) throws MojoExecutionException {
		
		Commandline cl = new Commandline();
   		cl.addArguments( arguments );
   		cl.setWorkingDirectory(targetDir);
		cl.setExecutable(executable);
   		
        StringStreamConsumer output = new CommandLineUtils.StringStreamConsumer();
        StringStreamConsumer error = new CommandLineUtils.StringStreamConsumer();
        
        try {
 			int returnValue = CommandLineUtils.executeCommandLine(cl, output, error);
 			if (returnValue != 0) {
 				// Magento 1.4.2.0 pear script seems to be bugged, returns 1 even tho there was no error?
 				if (magentoVersion.getMajorVersion() != 1 || magentoVersion.getMinorVersion() != 4 || magentoVersion.getRevisionVersion() != 2 || !arguments[0].equals("mage-setup")) {
 				logger.info("retval: "+returnValue);
 				logger.info(output.getOutput().toString() );
 				logger.info(error.getOutput().toString());
 				throw new MojoExecutionException( "Error while executing pear command!");
 				}
 			}
 			if (output.getOutput().toString().startsWith("Error:")) {
 				throw new MojoExecutionException(output.getOutput().toString());
 			}
 		} catch (CommandLineException e) {
 			throw new MojoExecutionException( "Error while executing pear command!", e );
 		}	
	}
	
    /**
     * Replace @tag@ tokens in payload with values from tags map
     *
     * @param payload String with tokens to replace
     * @param tags Map with token/value pairs
     * @return The processed string
     */	
	public static String replaceTags(String payload, Map<String, String> tags) {
    	  Pattern p = Pattern.compile("@(\\w+)@");
    	  Matcher m = p.matcher(payload);
    	  String processedPayload = payload;
    	  boolean result = m.find();
    	  if (result) {
    	    StringBuffer sb = new StringBuffer();
    	    do {
    	      m.appendReplacement(sb, tags.containsKey(m.group(1)) ? tags.get(m.group(1)) : "");
    	      result = m.find();
    	    } while (result);
    	    m.appendTail(sb);
    	    processedPayload = sb.toString();
    	  }
    	  return processedPayload;
   	}
    
    // creates magento admin pw hash
    public static String getSaltedMd5Hash(String payload) throws UnsupportedEncodingException, NoSuchAlgorithmException {
    	// create salt
		char[] saltRange = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
		StringBuilder sb = new StringBuilder();
		Random random = new Random();
		for (int i = 0; i < 2; i++) {
		    char c = saltRange[random.nextInt(saltRange.length)];
		    sb.append(c);
		}
		String salt = sb.toString();
		return getMd5Hash(salt+payload)+":"+salt;	
    }
    
    // creates php compatible md5 hash
	public static String getMd5Hash(String payload) throws UnsupportedEncodingException, NoSuchAlgorithmException {
    	// md5 the payload
    	byte[] hashBytes = payload.getBytes("UTF-8");
		MessageDigest md = MessageDigest.getInstance("MD5");
		byte[] digestedHash = md.digest(hashBytes);
		
		// we want hex output
		StringBuffer sbHex = new StringBuffer();
        for (int i = 0; i < digestedHash.length; i++) {
        	sbHex.append(Integer.toString((digestedHash[i] & 0xff) + 0x100, 16).substring(1));
        }

        return sbHex.toString();	
    }
	
	// validates url string and adds missing boilerplate if needed
	public static String validateBaseUrl(String url, Boolean secure) {
		String prefix = "http://";
		String validatedUrl = url;
		if (secure) {
			prefix = "https://";
		} 
		
		if (!url.startsWith(prefix)) {
			validatedUrl = prefix+url;
		}
		
		// append missing / to baseUrl if missing
		if (!url.endsWith("/")) {
			validatedUrl += "/";
		}
		return validatedUrl;
	}
	
	// extract magento version from Mage.php
	public static MagentoVersion getMagentoVersion(Path appMagePath) throws Exception {
		String appMage = new String(Files.readAllBytes(appMagePath));
		HashMap<String, String> versionParts = new HashMap<String,String>();
		versionParts.put("major", "0");
		versionParts.put("minor", "0");
		versionParts.put("revision", "0");
		versionParts.put("patch", "0");
		versionParts.put("stability", "");
		versionParts.put("number", "0");
		// regex the version parts and put them into a string
		for (Map.Entry<String, String> versionPart : versionParts.entrySet()) {
			Pattern pattern = Pattern.compile("'"+versionPart.getKey()+"'[ \\t]+=>[ \\t]+'(([0-9a-zA-Z]+)?)',");
			Matcher matcher = pattern.matcher(appMage);
			if (matcher.find()) {
				versionParts.put(versionPart.getKey(), matcher.group(1));
			}
		}
		String version = null;
		// magento versions <1.4.x use a different format, lets try that as a last resort
		if (versionParts.get("major").equals("0")) {
			Pattern pattern = Pattern.compile("return '([0-9].[0-9].[0-9].[0-9])';");
			Matcher matcher = pattern.matcher(appMage);
			if (matcher.find()) {
				version = matcher.group(1);
			} else {
				throw new MojoExecutionException("Could not parse Magento version.");
			}
		} else {
			version = versionParts.get("major")+".";
			version += versionParts.get("minor")+".";
			version += versionParts.get("revision")+".";
			version += versionParts.get("patch");
			if ( !versionParts.get("stability").equals("") ) {
				version += "-"+(String)versionParts.get("stability");
			}
			if ( !versionParts.get("number").equals("") ) {
				version += (String)versionParts.get("number");
			}
		}
		
		return new MagentoVersion(version);
	}
	
	public static List<Path> getMagentoModuleNames(Path srcPath) throws IOException {
		List<Path> moduleNames = new ArrayList<Path>();
		Path codeLocal = Paths.get(srcPath.toString()+"/app/code/local");
		
		List<Path> tempList = FileUtil.getDirectoryList(codeLocal);
		for (Path m : tempList) {
			List<Path> mList = FileUtil.getDirectoryList(m);
			for (Path module : mList) {
				if (Files.isDirectory(module)) {
					moduleNames.add(module);
				}
			}
		}
		
		Path codeCommunity = Paths.get(srcPath.toString()+"/app/code/community");
		tempList = FileUtil.getDirectoryList(codeCommunity);
		for (Path m : tempList) {
			List<Path> mList = FileUtil.getDirectoryList(m);
			for (Path module : mList) {
				if (Files.isDirectory(module)) {
					moduleNames.add(module);
				}
			}
		}
		return moduleNames;
	}
	


}
