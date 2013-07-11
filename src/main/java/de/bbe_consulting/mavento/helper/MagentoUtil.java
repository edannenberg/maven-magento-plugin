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
import org.codehaus.plexus.util.cli.WriterStreamConsumer;
import org.codehaus.plexus.util.cli.CommandLineUtils.StringStreamConsumer;

import de.bbe_consulting.mavento.type.MagentoVersion;

/**
 * Magento related helpers.
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
     * Collects possible symlink targets for project source.
     * 
     * @param sourceBaseDir
     * @param targetBaseDir
     * @return Map<String, String> with source file and target link
     * @throws IOException
     */
    public static Map<String, String> collectSymlinks(String sourceBaseDir, String targetBaseDir)
            throws IOException {

        Map<String, String> linkMap = new HashMap<String, String>();
        Map<String, String> tempMap = new HashMap<String, String>();
        // crawl some standard directories
        if (Files.exists(Paths.get(sourceBaseDir + "/app"))) {
            tempMap = getSubFileLinkMap(sourceBaseDir + "/app/code/local",
                    targetBaseDir + "/app/code/local");
            // go one level deeper to skip namespace and only link the actual module directories
            for (Map.Entry<String, String> fileNames : tempMap.entrySet()) {
                linkMap.putAll(getSubFileLinkMap(fileNames.getKey(), fileNames.getValue()));
            }
            tempMap = getSubFileLinkMap(sourceBaseDir + "/app/code/community",
                    targetBaseDir + "/app/code/community");
            // go one level deeper to skip namespace and only link the actual module directories
            for (Map.Entry<String, String> fileNames : tempMap.entrySet()) {
                linkMap.putAll(getSubFileLinkMap(fileNames.getKey(), fileNames.getValue()));
            }
            tempMap = getSubFileLinkMap(sourceBaseDir + "/app/locale", targetBaseDir + "/app/locale");
            Map<String, String> localeMap = new HashMap<String, String>();
            // go one level deeper to skip namespace and only link the actual locale files
            for (Map.Entry<String, String> fileNames : tempMap.entrySet()) {
                localeMap = getSubFileLinkMap(fileNames.getKey(), fileNames.getValue());
                for (Map.Entry<String, String> localeNames : localeMap.entrySet()) {
                    // symlink everything but the template folder
                    if (!localeNames.getKey().endsWith("template")) {
                        linkMap.put(localeNames.getKey(), localeNames.getValue());
                    } else {
                        // handle email templates
                        Map<String, String> tMap = new HashMap<String, String>();
                        tMap = getSubFileLinkMap(localeNames.getKey(), localeNames.getValue());
                        for (Map.Entry<String, String> fNames : tMap.entrySet()) {
                            linkMap.putAll(getSubFileLinkMap(fNames.getKey(), fNames.getValue()));
                        }
                    }
                }
            }

            // crawl for possible layout/template/skin files
            tempMap.clear();
            tempMap.put(sourceBaseDir + "/app/design/adminhtml", targetBaseDir
                    + "/app/design/adminhtml");
            tempMap.put(sourceBaseDir + "/app/design/frontend", targetBaseDir
                    + "/app/design/frontend");
            linkMap.putAll(collectLayoutSymlinks(tempMap, new String[] {"/layout", "/locale", "/template"}));
            tempMap.clear();
            tempMap.put(sourceBaseDir + "/skin/adminhtml", targetBaseDir
                    + "/skin/adminhtml");
            tempMap.put(sourceBaseDir + "/skin/frontend", targetBaseDir
                    + "/skin/frontend");
            linkMap.putAll(collectLayoutSymlinks(tempMap, new String[] {"/css", "/images", "/js"}));

            // crawl app/etc/modules/ for files
            linkMap.putAll(getSubFileLinkMap(
                    sourceBaseDir + "/app/etc/modules", targetBaseDir
                            + "/app/etc/modules"));

        }
        // crawl some base directories
        linkMap.putAll(getSubFileLinkMap(sourceBaseDir + "/js", targetBaseDir + "/js"));
        linkMap.putAll(getSubFileLinkMap(sourceBaseDir + "/lib", targetBaseDir + "/lib"));
        linkMap.putAll(getSubFileLinkMap(sourceBaseDir + "/media", targetBaseDir + "/media"));
        linkMap.putAll(getSubFileLinkMap(sourceBaseDir + "/shell", targetBaseDir + "/shell"));
        linkMap.putAll(getSubFileLinkMap(sourceBaseDir + "/var", targetBaseDir + "/var"));

        // everything else, minus the base directories we already took care of
        tempMap = getSubFileLinkMap(sourceBaseDir, targetBaseDir);
        tempMap.remove(sourceBaseDir + "/app");
        tempMap.remove(sourceBaseDir + "/skin");
        tempMap.remove(sourceBaseDir + "/js");
        tempMap.remove(sourceBaseDir + "/lib");
        tempMap.remove(sourceBaseDir + "/media");
        tempMap.remove(sourceBaseDir + "/shell");
        tempMap.remove(sourceBaseDir + "/var");
        tempMap.remove(sourceBaseDir + "/magento_bootstrap.php");
        linkMap.putAll(tempMap);

        return linkMap;
    }

    /**
     * Crawls magento layout/skin directories for possible symlink targets.
     * 
     * @param baseDirectories
     * @return HashMap<String,String> key: original file, value: symlink target
     * @throws IOException
     */
    private static Map<String, String> collectLayoutSymlinks(Map<String, String> baseDirectories, String[] crawlTargets)
             throws IOException {

        Map<String, String> linkMap = new HashMap<String, String>();
        Map<String, String> firstLevelMap = new HashMap<String, String>();
        // looks like eye cancer but oh well
        for (Map.Entry<String, String> firstLevelEntry : baseDirectories.entrySet()) {
            firstLevelMap = getSubFileLinkMap(firstLevelEntry.getKey(), firstLevelEntry.getValue());
            for (Map.Entry<String, String> secondLevelEntry : firstLevelMap.entrySet()) {
                Map<String, String> secondLevelMap = new HashMap<String, String>();
                secondLevelMap = getSubFileLinkMap(secondLevelEntry.getKey(), secondLevelEntry.getValue());
                for (Map.Entry<String, String> finalLevelEntry : secondLevelMap.entrySet()) {
                    Map<String, String> finalLevelMap = new HashMap<String, String>();
                    finalLevelMap = getSubFileLinkMap(finalLevelEntry.getKey(), finalLevelEntry.getValue());
                    for (String crawlTarget : crawlTargets) {
                        if (finalLevelMap.containsKey(finalLevelEntry.getKey() + crawlTarget)) {
                            finalLevelMap.remove(finalLevelEntry.getKey() + crawlTarget);
                            linkMap.putAll(getSubFileLinkMap(
                                    finalLevelEntry.getKey() + crawlTarget,
                                    finalLevelEntry.getValue() + crawlTarget));
                        }
                    }
                    linkMap.putAll(finalLevelMap);
                }
            }
        }
        return linkMap;
    }

    /**
     * Get direct subfiles/folders of baseDirName and put them into a hash map.
     * 
     * @param baseDirName
     * @param targetBaseDir
     * @return HashMap<String,String> key: original file, value: symlink target
     * @throws IOException
     */
    private static Map<String, String> getSubFileLinkMap(String baseDirName, String targetBaseDir)
             throws IOException {

        final Path base = Paths.get(baseDirName);
        final HashMap<String, String> r = new HashMap<String, String>();
        if (Files.isDirectory(base)) {
            DirectoryStream<Path> files = null;
            try {
                files = Files.newDirectoryStream(base);
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
     * Execute a pear command.
     * 
     * @param executable
     * @param arguments
     * @param targetDir
     * @param magentoVersion
     * @param logger
     * @throws MojoExecutionException
     */
    public static void executePearCommand(String executable,
            String[] arguments, String targetDir, MagentoVersion magentoVersion, Log logger)
            throws MojoExecutionException {

        final Commandline cl = new Commandline();
        cl.addArguments(arguments);
        cl.setWorkingDirectory(targetDir);
        cl.setExecutable(executable);

        final StringStreamConsumer output = new CommandLineUtils.StringStreamConsumer();
        final StringStreamConsumer error = new CommandLineUtils.StringStreamConsumer();

        try {
            int returnValue = CommandLineUtils.executeCommandLine(cl, output,
                    error);
            if (returnValue != 0) {
                // Magento 1.4.2.0 pear script seems to be bugged, returns 1
                // even tho there was no error?
                if (magentoVersion.getMajorVersion() != 1
                        || magentoVersion.getMinorVersion() != 4
                        || magentoVersion.getRevisionVersion() != 2
                        || !arguments[0].equals("mage-setup")) {
                    logger.info("retval: " + returnValue);
                    logger.info(output.getOutput().toString());
                    logger.info(error.getOutput().toString());
                    throw new MojoExecutionException("Error while executing pear command!");
                }
            }
            if (output.getOutput().toString().startsWith("Error:")) {
                throw new MojoExecutionException(output.getOutput().toString());
            }
        } catch (CommandLineException e) {
            throw new MojoExecutionException("Error while executing pear command!", e);
        }
    }

    /**
     * Replace @tag@ tokens in payload with values from tags map.
     * 
     * @param payload   string with tokens to replace
     * @param tags      map with token/value pairs
     * @return String the processed string
     */
    public static String replaceTags(String payload, Map<String, String> tags) {
        final Pattern p = Pattern.compile("@(\\w+)@");
        final Matcher m = p.matcher(payload);
        String processedPayload = payload;
        boolean result = m.find();
        if (result) {
            final StringBuffer sb = new StringBuffer();
            do {
                m.appendReplacement(sb, tags.containsKey(m.group(1)) ? tags.get(m.group(1)) : "");
                result = m.find();
            } while (result);
            m.appendTail(sb);
            processedPayload = sb.toString();
        }
        return processedPayload;
    }

    /**
     * Creates magento admin pw hash.
     * 
     * @param payload
     * @return String salted md5 hash
     * @throws UnsupportedEncodingException
     * @throws NoSuchAlgorithmException
     */
    public static String getSaltedMd5Hash(String payload)
            throws UnsupportedEncodingException, NoSuchAlgorithmException {

        // create salt
        final char[] saltRange = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
        final StringBuilder sb = new StringBuilder();
        final Random random = new Random();
        for (int i = 0; i < 2; i++) {
            char c = saltRange[random.nextInt(saltRange.length)];
            sb.append(c);
        }
        final String salt = sb.toString();
        return getMd5Hash(salt + payload) + ":" + salt;
    }

    /**
     * Creates php compatible md5 hash from payload.
     * 
     * @param payload
     * @return String md5 hash
     * @throws UnsupportedEncodingException
     * @throws NoSuchAlgorithmException
     */
    public static String getMd5Hash(String payload)
            throws UnsupportedEncodingException, NoSuchAlgorithmException {

        // md5 the payload
        final byte[] hashBytes = payload.getBytes("UTF-8");
        final MessageDigest md = MessageDigest.getInstance("MD5");
        final byte[] digestedHash = md.digest(hashBytes);

        // we want hex output
        final StringBuffer sbHex = new StringBuffer();
        for (int i = 0; i < digestedHash.length; i++) {
            sbHex.append(Integer.toString((digestedHash[i] & 0xff) + 0x100, 16).substring(1));
        }

        return sbHex.toString();
    }

    /**
     * Validates url string and adds missing boilerplate if needed.
     * 
     * @param url
     * @param secure
     * @return String the validated/corrected url
     */
    public static String validateBaseUrl(String url, Boolean secure) {
        String prefix = "http://";
        String validatedUrl = url;
        if (secure) {
            if (url.startsWith("http://")) {
                url = url.substring(7, url.length()); 
            }
            prefix = "https://";
        }

        if (!url.startsWith(prefix)) {
            validatedUrl = prefix + url;
        }

        // append missing / to baseUrl if missing
        if (!url.endsWith("/")) {
            validatedUrl += "/";
        }
        return validatedUrl;
    }

    /**
     * Extract magento version from Mage.php
     * 
     * @param appMagePath path to Mage.php
     * @return MagentoVersion
     * @throws Exception
     */
    public static MagentoVersion getMagentoVersion(Path appMagePath)
            throws Exception {

        final String appMage = new String(Files.readAllBytes(appMagePath));
        final HashMap<String, String> versionParts = new HashMap<String, String>();
        versionParts.put("major", "0");
        versionParts.put("minor", "0");
        versionParts.put("revision", "0");
        versionParts.put("patch", "0");
        versionParts.put("stability", "");
        versionParts.put("number", "0");
        // regex the version parts and put them into a string
        for (Map.Entry<String, String> versionPart : versionParts.entrySet()) {
            final Pattern pattern = Pattern.compile("'" + versionPart.getKey()
                    + "'[ \\t]+=>[ \\t]+'(([0-9a-zA-Z]+)?)',");
            final Matcher matcher = pattern.matcher(appMage);
            if (matcher.find()) {
                versionParts.put(versionPart.getKey(), matcher.group(1));
            }
        }
        String version = null;
        // magento versions <1.4.x use a different format, lets try that as a last resort
        if (versionParts.get("major").equals("0")) {
            final Pattern pattern = Pattern.compile("return '([0-9].[0-9].[0-9].[0-9])';");
            final Matcher matcher = pattern.matcher(appMage);
            if (matcher.find()) {
                version = matcher.group(1);
            } else {
                throw new MojoExecutionException("Could not parse Magento version.");
            }
        } else {
            version = versionParts.get("major") + ".";
            version += versionParts.get("minor") + ".";
            version += versionParts.get("revision") + ".";
            version += versionParts.get("patch");
            if (!versionParts.get("stability").equals("")) {
                version += "-" + (String) versionParts.get("stability");
            }
            if (!versionParts.get("number").equals("")) {
                version += (String) versionParts.get("number");
            }
        }

        return new MagentoVersion(version);
    }

    /**
     * Returns list with magento module paths.
     * 
     * @param srcPath
     * @return List<Path>
     * @throws IOException
     */
    public static List<Path> getMagentoModuleNames(Path srcPath)
            throws IOException {

        final List<Path> moduleNames = new ArrayList<Path>();
        final Path codeLocal = Paths.get(srcPath.toString() + "/app/code/local");

        List<Path> tempList = FileUtil.getDirectoryList(codeLocal);
        for (Path m : tempList) {
            final List<Path> mList = FileUtil.getDirectoryList(m);
            for (Path module : mList) {
                if (Files.isDirectory(module)) {
                    moduleNames.add(module);
                }
            }
        }

        final Path codeCommunity = Paths.get(srcPath.toString() + "/app/code/community");
        tempList = FileUtil.getDirectoryList(codeCommunity);
        for (Path m : tempList) {
            final List<Path> mList = FileUtil.getDirectoryList(m);
            for (Path module : mList) {
                if (Files.isDirectory(module)) {
                    moduleNames.add(module);
                }
            }
        }
        return moduleNames;
    }
    
    /**
     * Execute magento install.php with some bogus values.<br/>
     * Only used for vanilla artifact creation.
     * 
     * @param magentoRoot
     * @param magentoDbUser
     * @param magentoDbPasswd
     * @param magentoDbHost
     * @param magentoDbName
     * @param logger
     * @throws MojoExecutionException
     */
    public static void execMagentoInstall (Path magentoRoot, String magentoDbUser,
            String magentoDbPasswd, String magentoDbHost, String magentoDbName, Log logger)
                    throws MojoExecutionException {

        final Commandline cl = new Commandline();
        cl.addArguments(new String[] { "install.php", "--license_agreement_accepted", "yes",
                "--locale", "de_DE", "--timezone", "\"Europe/Berlin\"",
                "--default_currency", "EUR",
                "--db_user", magentoDbUser,
                "--db_host", magentoDbHost,
                "--db_name", magentoDbName,
                "--url", "\"http://mavento.local/\"",
                "--secure_base_url", "\"https://mavento.local/\"",
                "--skip_url_validation", "yes",
                "--use_secure", "yes",
                "--use_secure_admin", "yes",
                "--use_rewrites", "yes",
                "--admin_lastname", "Floppel",
                "--admin_firstname", "Heinzi",
                "--admin_email", "\"heinzi@floppel.net\"",
                "--admin_username", "admin",
                "--admin_password", "123test"
                });
        if (magentoDbPasswd != null && !magentoDbPasswd.isEmpty()) {
            cl.addArguments(new String[] {"--db_pass", magentoDbPasswd});
        }
        cl.setExecutable("php");
        cl.setWorkingDirectory(magentoRoot.toString()+"/magento");

        final StringStreamConsumer error = new CommandLineUtils.StringStreamConsumer();
        WriterStreamConsumer output = null;
        try {
            logger.info("Executing install.php on db " + magentoDbName + "..");
            final int returnValue = CommandLineUtils.executeCommandLine(cl, output, error);
            if (returnValue != 0) {
                logger.info(error.getOutput().toString());
                logger.info("retval: " + returnValue);
                throw new MojoExecutionException("Error while executing install.php.");
            }
            logger.info("..done.");
        } catch (CommandLineException e) {
            throw new MojoExecutionException("Error while executing install.php.", e);
        }
    }

}
