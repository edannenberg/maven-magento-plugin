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
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.FileVisitOption;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import de.bbe_consulting.mavento.helper.FileUtil;
import de.bbe_consulting.mavento.helper.MagentoSqlUtil;
import de.bbe_consulting.mavento.helper.MagentoUtil;
import de.bbe_consulting.mavento.helper.MagentoXmlUtil;
import de.bbe_consulting.mavento.helper.MavenUtil;
import de.bbe_consulting.mavento.helper.visitor.CopyFilesVisitor;
import de.bbe_consulting.mavento.helper.visitor.ExtractZipVisitor;
import de.bbe_consulting.mavento.type.MagentoVersion;

/**
 * Base class for mojos that want to setup a Magento instance
 * 
 * @author Erik Dannenberg
 */
public abstract class AbstractMagentoSetupMojo extends AbstractMagentoSqlMojo {

    /**
     * Use a custom magento sqldump instead of generating the magento database.<br/>
     * Dump is expected in /sqldumps of your project root.<br/>
     * 
     * @parameter expression="${magento.db.dump.file}"
     */
    protected String magentoDumpFileName;

    /**
     * GroupId of a custom magento artifact for integration tests.<br/>
     * 
     * @parameter expression="${magento.artifact.group.id}"
     *            default-value="com.varien"
     */
    protected String magentoArtifactGroupId;

    /**
     * Id of a custom magento artifact for integration tests.<br/>
     * 
     * @parameter expression="${magento.artifact.id}" default-value="magento"
     */
    protected String magentoArtifactId;

    /**
     * Version of a custom magento artifact for integration tests.<br/>
     * 
     * @parameter expression="${magento.artifact.version}"
     */
    protected String magentoArtifactVersion;

    /**
     * Run full setup or just update db/baseurl.<br/>
     * 
     * @parameter expression="${magento.artifact.is.custom}"
     *            default-value="false"
     */
    protected Boolean magentoArtifactIsCustom;

    /**
     * Use https for backend access? true|false<br/>
     * 
     * @parameter expression="${magento.use.https.backend}" default-value="true"
     */
    protected Boolean magentoUseHttpsBackend;

    /**
     * Use https for frontend access? true|false<br/>
     * 
     * @parameter expression="${magento.use.https.frontend}"
     *            default-value="true"
     */
    protected Boolean magentoUseHttpsFrontend;

    /**
     * Backend admin username.
     * 
     * @parameter expression="${magento.admin.username}" default-value="admin"
     */
    protected String magentoAdminUsername;

    /**
     * Backend admin password.
     * 
     * @parameter expression="${magento.admin.passwd}"
     * @required
     */
    protected String magentoAdminPasswd;

    /**
     * Want official sample data? true|false<br/>
     * 
     * @parameter expression="${magento.use.sample.data}" default-value="false"
     */
    protected Boolean magentoUseSampleData;

    /**
     * Enable exception exposing? (only Magento >=1.4.0.0) true|false<br/>
     * 
     * @parameter expression="${magento.expose.exceptions}" default-value="true"
     */
    protected Boolean magentoExposeExceptions;

    /**
     * Restrict access to specified ip(s), empty for public access.
     * 
     * @parameter expression="${magento.dev.restrict.ip}" default-value=""
     */
    protected String magentoDevRestrictIp;

    /**
     * Enable the Magento profiler? true|false<br/>
     * 
     * @parameter expression="${magento.dev.profiler}" default-value="false"
     */
    protected Boolean magentoDevProfiler;

    /**
     * Enable logging? true|false<br/>
     * 
     * @parameter expression="${magento.dev.log.active}" default-value="true"
     */
    protected Boolean magentoDevLogActive;

    /**
     * Logfile name, saved to var/log or /tmp if the former is not accessible.<br/>
     * 
     * @parameter expression="${magento.dev.log.file}"
     *            default-value="system.log"
     */
    protected String magentoDevLogFile;

    /**
     * Logfile name for exceptions, saved to var/log or /tmp if the former is
     * not accessible.<br/>
     * 
     * @parameter expression="${magento.dev.log.file.exception}"
     *            default-value="exception.log"
     */
    protected String magentoDevLogFileException;

    /**
     * Allow symlinked templates? (only Magento >=1.5.1.0) true|false<br/>
     * 
     * @parameter expression="${magento.dev.allow.symlinks}"
     *            default-value="true"
     */
    protected Boolean magentoDevAllowSymlinks;

    /**
     * Setup pear after installation? true|false<br/>
     * 
     * @parameter expression="${magento.pear.enabled}" default-value="true"
     */
    protected Boolean magentoPearEnabled;

    /**
     * Upgrade pear after installation? true|false<br/>
     * Will autorun for Magento 1.4.2.0<br/>
     * 
     * @parameter expression="${magento.pear.upgrade}" default-value="false"
     */
    protected Boolean magentoPearUpgrade;

    /**
     * Set prefered extension stability. alpha|beta|stable<br/>
     * 
     * @parameter expression="${magento.extensions.prefered.stability}"
     *            default-value="beta"
     */
    protected String magentoExtensionsPreferedStability;

    /**
     * List of core extension keys you want installed after setup.<br/>
     * 
     * <pre>
     * Interface_Frontend_Default_Blank,etc
     * </pre>
     * 
     * You can append the version string.<br/>
     * 
     * <pre>
     * Interface_Frontend_Default_Blank-1.4.0.1
     * </pre>
     * 
     * @parameter expression="${magento.extensions.core}" default-value=""
     */
    protected String magentoExtensionsCore;

    /**
     * List of community extension keys you want installed after setup.<br/>
     * 
     * <pre>
     * Locale_Mage_community_de_DE,market_ready_germany
     * </pre>
     * 
     * You can append the version string.<br/>
     * 
     * <pre>
     * Locale_Mage_community_de_DE-1.4.0.1
     * </pre>
     * 
     * @parameter expression="${magento.extensions.community}" default-value=""
     */
    protected String magentoExtensionsCommunity;

    /**
     * Other artifacts you want deployed on installation, list of .zip files.<br/>
     * Put the artifacts into /extensions of your project root.<br/>
     * If you have a bunch of them you can use * instead of typing them all out.
     * 
     * @parameter expression="${magento.extensions.other}" default-value=""
     */
    protected String magentoExtensionsOther;

    /**
     * Reindex Magento database after setup? true|false<br/>
     * 
     * @parameter expression="${magento.db.reindex}" default-value="false"
     */
    protected Boolean magentoDbReindex;

    /**
     * Default locale key.<br/>
     * 
     * @parameter expression="${magento.locale}" default-value="en_US"
     */
    protected String magentoLocale;

    /**
     * Default Magento theme.<br/>
     * 
     * @parameter expression="${magento.theme}" default-value="default"
     */
    protected String magentoTheme;

    /**
     * Admin user email address.<br/>
     * 
     * @parameter expression="${magento.admin.email}"
     *            default-value="heinzi@floppel.net"
     */
    protected String magentoAdminEmail;

    /**
     * Admin user first name.<br/>
     * 
     * @parameter expression="${magento.admin.name.first}"
     *            default-value="Heinzi"
     */
    protected String magentoAdminNameFirst;

    /**
     * Admin user last name.<br/>
     * 
     * @parameter expression="${magento.admin.name.last}"
     *            default-value="Floppel"
     */
    protected String magentoAdminNameLast;

    /**
     * Default time zone.<br/>
     * 
     * @parameter expression="${magento.timezone}" default-value="Europe/Berlin"
     */
    protected String magentoTimezone;

    /**
     * Default currency.<br/>
     * 
     * @parameter expression="${magento.currency}" default-value="EUR"
     */
    protected String magentoCurrency;

    /**
     * Frontend name for backend accesss.<br/>
     * 
     * @parameter expression="${magento.backend.frontend.name}"
     *            default-value="admin"
     */
    protected String magentoBackendFrontendName;

    /**
     * Enable SEO rewriting? true|false<br/>
     * 
     * @parameter expression="${magento.seo.use.rewrites}" default-value="true"
     */
    protected Boolean magentoSeoUseRewrites;

    /**
     * Enable API cache? true|false<br/>
     * 
     * @parameter expression="${magento.cache.api}" default-value="false"
     */
    protected Boolean magentoCacheApi;

    /**
     * Enable block cache? true|false<br/>
     * 
     * @parameter expression="${magento.cache.block}" default-value="false"
     */
    protected Boolean magentoCacheBlock;

    /**
     * Enable config cache? true|false<br/>
     * 
     * @parameter expression="${magento.cache.config}" default-value="false"
     */
    protected Boolean magentoCacheConfig;

    /**
     * Enable collection cache? true|false<br/>
     * 
     * @parameter expression="${magento.cache.collections}"
     *            default-value="false"
     */
    protected Boolean magentoCacheCollections;

    /**
     * Enable EAV cache? true|false<br/>
     * 
     * @parameter expression="${magento.cache.eav}" default-value="false"
     */
    protected Boolean magentoCacheEav;

    /**
     * Enable layout cache? true|false<br/>
     * 
     * @parameter expression="${magento.cache.layout}" default-value="false"
     */
    protected Boolean magentoCacheLayout;

    /**
     * Enable translate cache? true|false<br/>
     * 
     * @parameter expression="${magento.cache.translate}" default-value="false"
     */
    protected Boolean magentoCacheTranslate;

    /**
     * Reverse proxy header1 (local.xml)<br/>
     * 
     * @parameter expression="${magento.remote.addr.header1}"
     *            default-value="HTTP_X_REAL_IP"
     */
    protected String magentoRemoteAddrHeader1;

    /**
     * Reverse proxy header2 (local.xml)<br/>
     * 
     * @parameter expression="${magento.remote.addr.header2}"
     *            default-value="HTTP_X_FORWARDED_FOR"
     */
    protected String magentoRemoteAddrHeader2;

    /**
     * Where to save sessions? files|db<br/>
     * 
     * @parameter expression="${magento.session.save}" default-value="files"
     */
    protected String magentoSessionSave;

    /**
     * Sessiondata location? files|db|memcache<br/>
     * 
     * @parameter expression="${magento.sessiondata.location}"
     *            default-value="files"
     */
    protected String magentoSessiondataLocation;

    /**
     * @parameter expression="${magento.sessiondata.savepath}" default-value=""
     */
    protected String magentoSessiondataSavepath;

    /**
     * @parameter expression="${magento.session.cache.limiter}" default-value=""
     */
    protected String magentoSessionCacheLimiter;

    /**
     * file|apc|memcached<br/>
     * 
     * @parameter expression="${magento.session.cache.backend}"
     *            default-value="files"
     */
    protected String magentoSessionCacheBackend;

    /**
     * file|apc|memcached<br/>
     * 
     * @parameter expression="${magento.session.cache.slow.backend}"
     *            default-value="files"
     */
    protected String magentoSessionCacheSlowBackend;

    /**
     * @parameter expression="${magento.session.cache.slow.backend.store.data}"
     *            default-value="false"
     */
    protected Boolean magentoSessionCacheSlowBackendStoreData;

    /**
     * @parameter expression="${magento.session.cache.auto.refresh.fast.cache}"
     *            default-value="false"
     */
    protected Boolean magentoSessionCacheAutoRefreshFastCache;

    /**
     * Memcached url.<br/>
     * 
     * @parameter expression="${magento.session.cache.memcached.host}"
     *            default-value=""
     */
    protected String magentoSessionCacheMemcachedHost;

    /**
     * Memcached port.<br/>
     * 
     * @parameter expression="${magento.session.cache.memcached.port}"
     *            default-value=""
     */
    protected String magentoSessionCacheMemcachedPort;

    /**
     * @parameter expression="${magento.session.cache.memcached.weight}"
     *            default-value=""
     */
    protected String magentoSessionCacheMemcachedWeight;

    /**
     * @parameter expression="${magento.session.cache.memcached.timeout}"
     *            default-value=""
     */
    protected String magentoSessionCacheMemcachedTimeout;

    /**
     * @parameter expression="${magento.session.cache.memcached.interval}"
     *            default-value=""
     */
    protected String magentoSessionCacheMemcachedInterval;

    /**
     * @parameter expression="${magento.session.cache.memcached.status}"
     *            default-value=""
     */
    protected String magentoSessionCacheMemcachedStatus;

    /**
     * @parameter expression="${magento.session.cache.memcached.persistent}"
     *            default-value=""
     */
    protected String magentoSessionCacheMemcachedPersistent;

    /**
     * @parameter expression="${magento.session.cache.memcached.compression}"
     *            default-value="false"
     */
    protected Boolean magentoSessionCacheMemcachedCompression;

    /**
     * @parameter expression="${magento.session.cache.memcached.cachedir}"
     *            default-value=""
     */
    protected String magentoSessionCacheMemcachedCachedir;

    /**
     * @parameter 
     *            expression="${magento.session.cache.memcached.hashed.dir.umask}"
     *            default-value=""
     */
    protected String magentoSessionCacheMemcachedHashedDirUmask;

    /**
     * @parameter expression="${magento.session.cache.memcached.file.prefix}"
     *            default-value=""
     */
    protected String magentoSessionCacheMemcachedFilePrefix;

    /**
     * Encryption key to use. If omitted a fresh one will be generated.<br/>
     * 
     * @parameter expression="${magento.encryptionkey}"
     */
    protected String magentoEncryptionkey;

    /**
     * Omit for current date, format: Tue, 13 Apr 2010 21:40:52 +0000<br/>
     * 
     * @parameter expression="${magento.install.date}"
     */
    protected String magentoInstallDate;

    /**
     * Omit for current date, format: 2010-04-13 21:40:52<br/>
     * 
     * @parameter expression="${magento.install.date.sql}"
     */
    protected String magentoInstallDateSql;

    /**
     * Database table prefix, not implemented yet.<br/>
     * 
     * @parameter expression="${magento.db.table.prefix}" default-value=""
     */
    protected String magentoDbTablePrefix;

    /**
     * @parameter 
     *            expression="${magento.session.cache.memcached.hashed.dir.level}"
     *            default-value=""
     */
    protected String magentoSessionCacheMemcachedHashedDirLevel;

    /**
     * Magento base url.<br/>
     * 
     * @parameter expression="${magento.url.base}"
     * @required
     */
    protected String magentoUrlBase;

    /**
     * Magento secure base url.<br/>
     * 
     * @parameter expression="${magento.url.base.https}"
     */
    protected String magentoUrlBaseHttps;

    protected MagentoVersion mVersion;
    protected String magentoAdminPasswdHashed = "";
    protected String tempDir;
    protected String targetDir;
    protected Boolean isIntegrationTest = false;

    /**
     * Kinda obsolete, may return if we want to support token mapping in sql dumps again
     * 
     * @throws MojoExecutionException
     */
    protected void createFinalSqlDump() throws MojoExecutionException {

        // get params for regex replace in the sql dump
        Map<String, String> sqlTags = getSqlTagMap();
        File sqlDumpFiltered = new File(tempDir + "/sql", "magento.sql.f");

        String sqlDumpFileName = "magento.sql";
        if (magentoUseSampleData) {
            sqlDumpFileName = "magento_with_sample_data.sql";
        }

        String sqlDumpOrg = null;
        try {
            sqlDumpOrg = FileUtils.fileRead(new File(tempDir + "/sql/"
                    + sqlDumpFileName));
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        FileWriter w = null;
        try {
            w = new FileWriter(sqlDumpFiltered);
            w.write(MagentoUtil.replaceTags(sqlDumpOrg, sqlTags));
        } catch (IOException e) {
            throw new MojoExecutionException("Error creating file "
                    + sqlDumpFiltered, e);
        } finally {
            if (w != null) {
                try {
                    w.close();
                } catch (IOException e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Creates local.xml file.
     * 
     * @throws MojoExecutionException
     */
    protected void createLocalXml() throws MojoExecutionException {
 
        if (magentoEncryptionkey == null || magentoEncryptionkey.isEmpty()) {
            try {
                getLog().info("Generating fresh encryption key..");
                magentoEncryptionkey = MagentoUtil.getMd5Hash(new Date().toString());
                getLog().info("-> " + magentoEncryptionkey);
            } catch (UnsupportedEncodingException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            } catch (NoSuchAlgorithmException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }

        if (magentoInstallDate == null || magentoInstallDate.isEmpty()) {
            final SimpleDateFormat format = new SimpleDateFormat(
                    "EEE, d MMM yyyy HH:mm:ss Z", new Locale("en", "EN"));
            magentoInstallDate = format.format(new Date());
        }

        final Path localXmlPath = Paths.get(tempDir + "/app/etc/local.xml");
        if (Files.notExists(localXmlPath)) {
            final Path localXmlPathTemp = Paths.get(tempDir + "/app/etc/local.xml.template");
            if (Files.notExists(localXmlPathTemp)) {
                throw new MojoExecutionException("Could read neither local.xml or local.xml.template!");
            }
            try {
                Files.move(localXmlPathTemp, localXmlPath);
            } catch (IOException e) {
                throw new MojoExecutionException("Error renaming local.xml.template! " + e.getMessage(), e);
            }
        }

        // get params for regex replace in local.xml
        final Map<String, String> localTags = getLocalXmlTagMap();

        final Document localXml = MagentoXmlUtil.readXmlFile(tempDir + "/app/etc/local.xml");
        final Document localXmlAdditional = MagentoXmlUtil.readXmlFile(tempDir
                + "/app/etc/local.xml.additional");

        if (magentoSessiondataSavepath != null && !magentoSessiondataSavepath.isEmpty()) {
            // find cache nodes in local.xml.additional
            final NodeList cacheNodes = localXmlAdditional.getElementsByTagName("session_save_path");
            // import them to a new node
            final Node n = localXml.importNode(cacheNodes.item(0), true);
            // find global node of local.xml
            final NodeList globalNodes = localXml.getElementsByTagName("global");
            // append imported nodes to local.xml
            final Node g = globalNodes.item(0);
            g.appendChild(n);
        }

        if (magentoSessionCacheLimiter != null && !magentoSessionCacheLimiter.isEmpty()) {
            // find cache nodes in local.xml.additional
            final NodeList cacheNodes = localXmlAdditional.getElementsByTagName("session_cache_limiter");
            // import them to a new node
            final Node n = localXml.importNode(cacheNodes.item(0), true);
            // find global node of local.xml
            final NodeList globalNodes = localXml.getElementsByTagName("global");
            // append imported nodes to local.xml
            final Node g = globalNodes.item(0);
            g.appendChild(n);
        }

        if (magentoSessionCacheBackend.equals("memcached")
                || magentoSessionCacheBackend.equals("apc")
                || magentoSessionCacheBackend.equals("xcache")) {
            // find cache nodes in local.xml.additional
            final NodeList cacheNodes = localXmlAdditional.getElementsByTagName("cache");
            // import them to a new node
            final Node n = localXml.importNode(cacheNodes.item(0), true);
            // find global node of local.xml
            final NodeList globalNodes = localXml.getElementsByTagName("global");
            // append imported nodes to local.xml
            final Node g = globalNodes.item(0);
            g.appendChild(n);
        }

        if (mVersion.getMajorVersion() <= 1 && mVersion.getMinorVersion() < 6) {
            if (magentoRemoteAddrHeader1 != null && !magentoRemoteAddrHeader1.isEmpty()) {
                // find cache nodes in local.xml.additional
                final NodeList cacheNodes = localXmlAdditional.getElementsByTagName("remote_addr_headers");
                // import them to a new node
                final Node n = localXml.importNode(cacheNodes.item(0), true);
                // find global node of local.xml
                final NodeList globalNodes = localXml.getElementsByTagName("global");
                // append imported nodes to local.xml
                final Node g = globalNodes.item(0);
                g.appendChild(n);
            }
        }

        MagentoXmlUtil.updateXmlValues(localTags, localXml);

        String finalLocalXml = null;
        try {
            finalLocalXml = MagentoUtil.replaceTags(MagentoXmlUtil.transformXmlToString(localXml), localTags);
        } catch (TransformerException e) {
            throw new MojoExecutionException("Error while creating local.xml", e);
        }

        MagentoXmlUtil.writeXmlFile(finalLocalXml, tempDir + "/app/etc/local.xml");
    }

    /**
     * Updates local.xml with properties from active maven profile.
     * 
     * @throws MojoExecutionException
     */
    protected void updateLocalXml() throws MojoExecutionException {

        final String localXmlPath = tempDir + "/app/etc/local.xml";
        final Document localXml = MagentoXmlUtil.readXmlFile(localXmlPath);

        MagentoXmlUtil.updateDbValues(magentoDbHost, magentoDbUser,
                magentoDbPasswd, magentoDbName, localXml);

        try {
            MagentoXmlUtil.writeXmlFile(
                            MagentoXmlUtil.transformXmlToString(localXml),
                            localXmlPath);
        } catch (TransformerException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    /**
     * Find xml node via xpath.
     * 
     * @param obj
     * @param xpathExpression
     * @return NodeList
     * @throws XPathExpressionException
     */
    protected NodeList findXmlNode(Object obj, String xpathExpression)
            throws XPathExpressionException {

        final XPath xPath = XPathFactory.newInstance().newXPath();
        final XPathExpression expression = xPath.compile(xpathExpression);
        return (NodeList) expression.evaluate(obj, XPathConstants.NODESET);
    }

    /**
     * Creates magento cache config in old format. (pre 1.4)
     * 
     * @throws MojoExecutionException
     */
    protected void createOldCacheConfig() throws MojoExecutionException {

        // get params for regex replace in local.xml
        final Map<String, String> cacheTags = getOldCacheTagMap();

        final String cacheConfigTemplate = "a:7:{s:6:\"config\";i:@CACHE_CONFIG@;s:6:\"layout\";i:@CACHE_LAYOUT@;s:10:\"block_html\";i:@CACHE_BLOCK@;s:9:\"translate\";i:@CACHE_TRANSLATE@;s:11:\"collections\";i:@CACHE_COLLECTIONS@;s:3:\"eav\";i:@CACHE_EAV@;s:10:\"config_api\";i:@CACHE_API@;}";

        final String finalCacheConfig = MagentoUtil.replaceTags(cacheConfigTemplate, cacheTags);
        final File cacheConfigFiltered = new File(tempDir + "/app/etc/use_cache.ser");
        FileWriter cacheWriter = null;
        try {
            cacheWriter = new FileWriter(cacheConfigFiltered);
            cacheWriter.write(finalCacheConfig);
        } catch (IOException e) {
            throw new MojoExecutionException("Error writing use_cache.ser", e);
        } finally {
            if (cacheWriter != null) {
                try {
                    cacheWriter.close();
                } catch (IOException e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Setup an magento instance.
     * 
     * @throws MojoFailureException
     * @throws MojoExecutionException
     */
    protected void setupMagento() throws MojoFailureException, MojoExecutionException {

        try {
            mVersion = new MagentoVersion(magentoVersion);
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        if (!magentoArtifactGroupId.equals("com.varien")) {
            magentoArtifactIsCustom = true;
        }

        magentoUrlBase = MagentoUtil.validateBaseUrl(magentoUrlBase, false);
        if (magentoUrlBaseHttps != null && !magentoUrlBaseHttps.isEmpty()) {
            magentoUrlBaseHttps = MagentoUtil.validateBaseUrl(
                    magentoUrlBaseHttps, true);
        } else {
            magentoUrlBaseHttps = magentoUrlBase;
        }

        // extract magento artifact
        if (!isIntegrationTest) {
            try {
                getLog().info("Resolving dependencies..");
                MavenUtil.extractCompileDependencies(tempDir, project, getLog());
                getLog().info("..done.");
            } catch (IOException e) {
                throw new MojoExecutionException("Error extracting artifact: " + e.getMessage(), e);
            }
        }

        // drop db if existing
        MagentoSqlUtil.dropMagentoDb(magentoDbUser, magentoDbPasswd,
                magentoDbHost, magentoDbPort, magentoDbName, getLog());

        // create db
        MagentoSqlUtil.createMagentoDb(magentoDbUser, magentoDbPasswd,
                magentoDbHost, magentoDbPort, magentoDbName, getLog());

        String dumpFileName = tempDir + "/mavento_setup/sql/magento.sql";
        if (magentoDumpFileName != null && !magentoDumpFileName.isEmpty()) {
            // use premade dump
            dumpFileName = project.getBasedir() + "/sqldumps/" + magentoDumpFileName;
            if (!new File(dumpFileName).exists()) {
                throw new MojoExecutionException("Could not find custom sql dump file. Make sure to place it in /sqldumps of your project root.");
            }
            getLog().info("Using custom dump: " + dumpFileName);
        } else if (magentoUseSampleData && !magentoArtifactIsCustom) {
            dumpFileName = tempDir + "/mavento_setup/sql/magento_with_sample_data.sql";
        }

        // inject dump into database
        MagentoSqlUtil.importSqlDump(dumpFileName, magentoDbUser,
                magentoDbPasswd, magentoDbHost, magentoDbPort, magentoDbName,
                getLog());

        String jdbcUrl = MagentoSqlUtil.getJdbcUrl(magentoDbHost, magentoDbPort, magentoDbName);
        Map<String, String> config = null;

        getLog().info("Generating admin pw hash..");
        try {
            magentoAdminPasswdHashed = MagentoUtil.getSaltedMd5Hash(magentoAdminPasswd);
        } catch (UnsupportedEncodingException e) {
            throw new MojoExecutionException("Error while creating admin password hash." + e.getMessage(), e);
        } catch (NoSuchAlgorithmException e) {
            throw new MojoExecutionException("Error while creating admin password hash." + e.getMessage(), e);
        }
        getLog().info("-> " + magentoAdminPasswdHashed);

        // fix https url if not configured
        if (magentoUrlBaseHttps == null || magentoUrlBaseHttps.isEmpty()) {
            magentoUrlBaseHttps = magentoUrlBase;
        }

        // generate new install date if not configured
        if (magentoInstallDateSql == null || magentoInstallDateSql.isEmpty()) {
            final SimpleDateFormat fo = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            magentoInstallDateSql = fo.format(new Date());
        }

        // check if ip restrictions are not null
        if (magentoDevRestrictIp == null) {
            magentoDevRestrictIp = "";
        }

        if (magentoArtifactIsCustom || (magentoDumpFileName != null && !magentoDumpFileName.isEmpty())) {
            config = getSqlAdminTagMap();
            MagentoSqlUtil.updateAdminUser(config, magentoDbUser, magentoDbPasswd, jdbcUrl, getLog());
            // only update baseurl and dev settings for custom magento artifacts
            getLog().info("Setting baseUrl to: " + magentoUrlBase);
            config = getSqlTagMapBasic();
        } else {
            // update everything
            getLog().info("Updating database..");
            config = getSqlAdminTagMap();
            MagentoSqlUtil.updateAdminUser(config, magentoDbUser, magentoDbPasswd, jdbcUrl, getLog());
            if (mVersion.getMajorVersion() == 1 && mVersion.getMinorVersion() > 3) {
                config = getSqlCacheTagMap();
                MagentoSqlUtil.updateCacheConfig(config, magentoDbUser, magentoDbPasswd, jdbcUrl, getLog());
            }
            config = getSqlTagMap();
        }
        MagentoSqlUtil.setCoreConfigData(config, magentoDbUser, magentoDbPasswd, jdbcUrl, getLog());
        getLog().info("..done.");

        // update db settings in local.xml for custom artifacts, else do a full setup
        if (magentoArtifactIsCustom) {
            getLog().info("Updating db settings in local.xml..");
            updateLocalXml();
            getLog().info("..done.");
        } else {
            // prepare local.xml
            createLocalXml();
            updateLocalXml();

            // create cache config file for magento <1.4.x
            if (mVersion.getMajorVersion() == 1 && mVersion.getMinorVersion() < 4) {
                getLog().info("Generating pre 1.4 Magento cache file..");
                createOldCacheConfig();
                getLog().info("..done.");
            }

            // handle exception exposing
            if (magentoExposeExceptions && (mVersion.getMajorVersion() == 1 
                    && mVersion.getMinorVersion() > 3)) {
                getLog().info("Enabling exception printing..");
                try {
                    Files.move(Paths.get(tempDir + "/errors/local.xml.sample"),
                            Paths.get(tempDir + "/errors/local.xml"));
                } catch (IOException e) {
                    throw new MojoExecutionException("Error while enabling exception printing! "
                            + e.getMessage(), e);
                }
                getLog().info("..done.");
            }

            // copy sample data product images
            if (magentoUseSampleData && !magentoArtifactIsCustom) {
                final Path magentoSourcePath = Paths.get(tempDir + "/mavento_setup/sample_data");
                final Path magentoTargetPath = Paths.get(tempDir);
                try {
                    getLog().info("Copying sample data..");
                    final CopyFilesVisitor cv = new CopyFilesVisitor(magentoSourcePath, magentoTargetPath, false);
                    Files.walkFileTree(magentoSourcePath, cv);
                    getLog().info("..done.");
                } catch (IOException e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
            }
        }

        // extract local extensions
        if (magentoExtensionsOther != null && !magentoExtensionsOther.isEmpty()) {
            if (magentoExtensionsOther.equals("*")) {
                final Path eDir = Paths.get("extensions");
                final Path tDir = Paths.get(tempDir);
                try {
                    Files.createDirectories(eDir);
                    final ExtractZipVisitor ev = new ExtractZipVisitor(tDir, getLog());
                    EnumSet<FileVisitOption> options = EnumSet.of(FileVisitOption.FOLLOW_LINKS);
                    Files.walkFileTree(eDir, options, 1, ev);
                } catch (IOException e) {
                    throw new MojoExecutionException("Error: " + e.getMessage(), e);
                }
            } else {
                final String[] k = magentoExtensionsOther.split(",");
                for (String extensionKey : k) {
                    final String fPath = "extensions/" + extensionKey;
                    Path sourceFile = Paths.get(fPath);
                    if (!Files.exists(sourceFile)) {
                        if (extensionKey.endsWith(".zip")) {
                            sourceFile = Paths.get(fPath + ".zip");
                            if (!Files.exists(sourceFile)) {
                                throw new MojoExecutionException("Could not find " + extensionKey + " in extensions/");
                            }
                        } else {
                            throw new MojoExecutionException("Could not find " + extensionKey + " in extensions/");
                        }
                    } else {
                        try {
                            getLog().info("Extracting " + sourceFile.getFileName());
                            FileUtil.unzipFile(sourceFile.toAbsolutePath().toString(), tempDir);
                        } catch (IOException e) {
                            throw new MojoExecutionException("Error: " + e.getMessage(), e);
                        }
                    }
                }
            }
            getLog().info("..done.");
        }

        try {
            FileUtil.deleteFile(tempDir + "/var/cache", getLog());
            FileUtil.deleteFile(tempDir + "/mavento_setup", getLog());
            FileUtil.deleteFile(tempDir + "/META-INF", getLog());
        } catch (IOException e) {
            throw new MojoExecutionException("Error deleting directories. " + e.getMessage(), e);
        }

        if (!isIntegrationTest) {
            // copy prepared magento to final destination unless this an integration test
            Path magentoSourcePath = Paths.get(tempDir);
            Path magentoTargetPath = Paths.get(magentoRootLocal);
            getLog().info("Everything is prepared, copying to " + magentoRootLocal);
            try {
                FileUtil.deleteFile(magentoRootLocal, getLog());
                Files.createDirectories(magentoTargetPath);

                final CopyFilesVisitor cv = new CopyFilesVisitor(magentoSourcePath, magentoTargetPath, false);
                Files.walkFileTree(magentoSourcePath, cv);
            } catch (IOException e) {
                throw new MojoExecutionException("Error while copying to: "
                        + magentoTargetPath.toAbsolutePath() + " "
                        + e.getMessage(), e);
            }
            getLog().info("..done.");
        }

        setupPear();

        // add any core_config_data properties after extension install, as the
        // extension might overwrite them else
        getLog().info("Updating core_config_data..");
        config = MavenUtil.addMagentoMiscProperties(project, new HashMap<String, String>(), getLog());
        MagentoSqlUtil.setCoreConfigData(config, magentoDbUser, magentoDbPasswd, jdbcUrl, getLog());
        getLog().info("..done.");

        // finally reindex the magento db
        indexDb();

    }

    /**
     * Run magento pear setup.
     * 
     * @throws MojoExecutionException
     */
    private void setupPear() throws MojoExecutionException {

        // handle pear setup and possible additional extensions
        if (magentoPearEnabled) {
            final File magentoTargetPath = new File(targetDir);
            File pearExecutable = null;
            if (mVersion.getMajorVersion() >= 1 && mVersion.getMinorVersion() >= 5) {
                pearExecutable = new File(magentoTargetPath.getAbsolutePath() + "/mage");
            } else {
                pearExecutable = new File(magentoTargetPath.getAbsolutePath() + "/pear");
            }

            pearExecutable.setExecutable(true, true);

            getLog().info("Initializing pear..");
            MagentoUtil.executePearCommand(pearExecutable.getAbsolutePath(), new String[] { "mage-setup" },
                    magentoTargetPath.getAbsolutePath(), mVersion, getLog());
            getLog().info("..done.");

            if (magentoPearUpgrade || (mVersion.getMajorVersion() == 1 
                    && mVersion.getMinorVersion() == 4 && mVersion.getRevisionVersion() == 2)) {

                getLog().info("Updating pear channel..");
                MagentoUtil.executePearCommand(
                                pearExecutable.getAbsolutePath(),
                                new String[] { "channel-update", "pear.php.net" },
                                magentoTargetPath.getAbsolutePath(), mVersion,
                                getLog());
                if (mVersion.getMajorVersion() <= 1 && mVersion.getMinorVersion() < 6) {
                    getLog().info("Upgrading pear..");
                    MagentoUtil.executePearCommand(
                            pearExecutable.getAbsolutePath(), new String[] {"upgrade", "--force", "PEAR" },
                            magentoTargetPath.getAbsolutePath(), mVersion,
                            getLog());
                }
                getLog().info("..done.");
            }

            // set prefered extension stability
            if (magentoExtensionsPreferedStability != null && !magentoExtensionsPreferedStability.isEmpty()) {
                getLog().info("Setting preferred extension stability to "
                                + magentoExtensionsPreferedStability + "..");
                MagentoUtil.executePearCommand(pearExecutable.getAbsolutePath(),
                                new String[] { "config-set", "preferred_state", magentoExtensionsPreferedStability },
                                magentoTargetPath.getAbsolutePath(), mVersion,
                                getLog());
                getLog().info("..done.");
            }

            // install core extensions
            String[] extensionKeys = null;
            if (magentoExtensionsCore != null) {
                extensionKeys = magentoExtensionsCore.split(",");
                installPearModules("core", extensionKeys, pearExecutable);
            }

            // install community extensions
            if (magentoExtensionsCommunity != null) {
                extensionKeys = magentoExtensionsCommunity.split(",");
                installPearModules("community", extensionKeys, pearExecutable);
            }
        }
    }

    /**
     * Install magento connect modules.
     * 
     * @param channel
     * @param extensionKeys
     * @param pearExecutable
     * @throws MojoExecutionException
     */
    protected void installPearModules(String channel, String[] extensionKeys, File pearExecutable)
            throws MojoExecutionException {

        for (String extensionKey : extensionKeys) {
            if (!extensionKey.isEmpty()) {
                String[] params = null;
                if (mVersion.getMajorVersion() == 1 && mVersion.getMinorVersion() <= 4) {
                    String realChannel = "magento-core/";
                    if (channel.equals("community")) {
                        realChannel = "magento-community/";
                    }
                    params = new String[] { "install", realChannel + extensionKey };
                } else {
                    String realChannel = "http://connect20.magentocommerce.com/core";
                    if (channel.equals("community")) {
                        realChannel = "http://connect20.magentocommerce.com/community";
                    }
                    if (extensionKey.contains("-")) {
                        final String[] s = extensionKey.split("-");
                        String extensionVersion = "";
                        if (s.length == 2) {
                            extensionKey = s[0];
                            extensionVersion = s[1];
                        }
                        getLog().info("Installing magento-" + channel + "/"
                                        + extensionKey + "-" + extensionVersion
                                        + "..");
                        params = new String[] { "install", realChannel, extensionKey, extensionVersion };
                    } else {
                        getLog().info("Installing magento-" + channel + "/" + extensionKey + "..");
                        params = new String[] { "install", realChannel, extensionKey };
                    }
                }
                MagentoUtil.executePearCommand(
                        pearExecutable.getAbsolutePath(), params, targetDir,
                        mVersion, getLog());
                getLog().info("..done.");
            }
        }
    }
    
    /**
     * Reindex magento database.
     * 
     * @throws MojoExecutionException
     */
    private void indexDb() throws MojoExecutionException {
        // reindex db
        if (magentoDbReindex && (mVersion.getMajorVersion() >= 1 && mVersion.getMinorVersion() >= 4)) {
            if (magentoDeployType.equals("local")) {
                MagentoSqlUtil.indexDb(targetDir, getLog());
            } else {
                throw new MojoExecutionException("Oops, remote indexing not implemented yet. Skipping..");
            }
        }
    }

    /**
     * Get map with core_config_data configuration.
     * 
     * @return Map<String, String>
     */
    private Map<String, String> getSqlTagMap() {
        final Map<String, String> tokenMap = getSqlTagMapBasic();
        tokenMap.put("general/locale/timezone", magentoTimezone);
        tokenMap.put("currency/options/base", magentoCurrency);
        tokenMap.put("currency/options/default", magentoCurrency);
        tokenMap.put("currency/options/allow", magentoCurrency);
        tokenMap.put("general/locale/code", magentoLocale);
        tokenMap.put("design/theme/default", magentoTheme);
        tokenMap.put("web/seo/use_rewrites", magentoSeoUseRewrites ? "1" : "0");
        return tokenMap;
    }

    /**
     * Get map with basic core_config_data configuration. (url, dev settings)
     * 
     * @return Map<String, String>
     */
    private Map<String, String> getSqlTagMapBasic() {
        final Map<String, String> tokenMap = new HashMap<String, String>();
        tokenMap.put("web/unsecure/base_url", magentoUrlBase);
        tokenMap.put("web/secure/base_url", magentoUrlBaseHttps);
        tokenMap.put("web/secure/use_in_adminhtml", magentoUseHttpsBackend ? "1" : "0");
        tokenMap.put("web/secure/use_in_frontend", magentoUseHttpsFrontend ? "1" : "0");
        tokenMap.put("web/unsecure/base_skin_url", "{{unsecure_base_url}}skin/");
        tokenMap.put("web/unsecure/base_media_url", "{{unsecure_base_url}}media/");
        tokenMap.put("web/unsecure/base_link_url", "{{unsecure_base_url}}");
        tokenMap.put("web/unsecure/base_js_url", "{{unsecure_base_url}}js/");
        tokenMap.put("web/secure/base_skin_url", "{{secure_base_url}}skin/");
        tokenMap.put("web/secure/base_media_url", "{{secure_base_url}}media/");
        tokenMap.put("web/secure/base_link_url", "{{secure_base_url}}");
        tokenMap.put("web/secure/base_js_url", "{{secure_base_url}}js/");
        // dev stuff
        tokenMap.put("dev/restrict/allow_ips", magentoDevRestrictIp);
        tokenMap.put("dev/debug/profiler", magentoDevProfiler ? "1" : "0");
        tokenMap.put("dev/log/active", magentoDevLogActive ? "1" : "0");
        tokenMap.put("dev/log/file", magentoDevLogFile);
        tokenMap.put("dev/log/exception_file", magentoDevLogFileException);
        tokenMap.put("dev/template/allow_symlink", magentoDevAllowSymlinks ? "1" : "0");
        return tokenMap;
    }

    /**
     * Get map with admin tags.
     * 
     * @return Map<String, String>
     */
    private Map<String, String> getSqlAdminTagMap() {
        final Map<String, String> tokenMap = new HashMap<String, String>();
        tokenMap.put("ADMIN_USERNAME", magentoAdminUsername);
        tokenMap.put("ADMIN_PASSWD", magentoAdminPasswdHashed);
        tokenMap.put("ADMIN_NAME_FIRST", magentoAdminNameFirst);
        tokenMap.put("ADMIN_NAME_LAST", magentoAdminNameLast);
        tokenMap.put("ADMIN_EMAIL", magentoAdminEmail);
        tokenMap.put("INSTALL_DATESQL", magentoInstallDateSql);
        return tokenMap;
    }

    /**
     * Get map with cache config values.
     * 
     * @return Map<String, String>
     */
    private Map<String, String> getSqlCacheTagMap() {
        final Map<String, String> tokenMap = new HashMap<String, String>();
        // only magento >=1.4.x.x
        tokenMap.put("config", magentoCacheConfig ? "1" : "0");
        tokenMap.put("layout", magentoCacheLayout ? "1" : "0");
        tokenMap.put("block_html", magentoCacheBlock ? "1" : "0");
        tokenMap.put("translate", magentoCacheTranslate ? "1" : "0");
        tokenMap.put("collections", magentoCacheCollections ? "1" : "0");
        tokenMap.put("eav", magentoCacheEav ? "1" : "0");
        tokenMap.put("config_api", magentoCacheApi ? "1" : "0");
        return tokenMap;
    }

    /**
     * Get map with old cache config tokens.
     * 
     * @return Map<String, String>
     */
    private Map<String, String> getOldCacheTagMap() {
        final Map<String, String> tokenMap = new HashMap<String, String>();
        // only magento <1.4.x
        tokenMap.put("CACHE_CONFIG", magentoCacheConfig ? "1" : "0");
        tokenMap.put("CACHE_LAYOUT", magentoCacheLayout ? "1" : "0");
        tokenMap.put("CACHE_BLOCK", magentoCacheBlock ? "1" : "0");
        tokenMap.put("CACHE_TRANSLATE", magentoCacheTranslate ? "1" : "0");
        tokenMap.put("CACHE_COLLECTIONS", magentoCacheCollections ? "1" : "0");
        tokenMap.put("CACHE_EAV", magentoCacheEav ? "1" : "0");
        tokenMap.put("CACHE_API", magentoCacheApi ? "1" : "0");
        return tokenMap;
    }

    /**
     * Get map with local.xml config tokens.
     * 
     * @return Map<String, String>
     */
    private Map<String, String> getLocalXmlTagMap() {
        final Map<String, String> tokenMap = new HashMap<String, String>();

        tokenMap.put("host", magentoDbHost);
        tokenMap.put("dbname", magentoDbName);
        tokenMap.put("username", magentoDbUser);
        if (magentoDbPasswd == null) {
            magentoDbPasswd = "";
        }
        tokenMap.put("password", magentoDbPasswd);
        if (magentoDbTablePrefix == null) {
            magentoDbTablePrefix = "";
        }
        tokenMap.put("table_prefix", magentoDbTablePrefix);
        tokenMap.put("session_save", magentoSessionSave);
        tokenMap.put("key", magentoEncryptionkey);
        tokenMap.put("frontName", magentoBackendFrontendName);
        tokenMap.put("date", magentoInstallDate);

        // local.xml.additional
        // TODO: handle local.xml.additional stuff via xml in createLocalXml()
        // tokenMap.put("session_save", magentoSessionSave);
        // tokenMap.put("session_save", magentoSessiondataLocation);
        // tokenMap.put("session_save_path", magentoSessiondataSavepath);
        // tokenMap.put("session_cache_limiter", magentoSessionCacheLimiter);
        // tokenMap.put("backend", magentoSessionCacheBackend);
        // tokenMap.put("slow_backend", magentoSessionCacheSlowBackend);
        // tokenMap.put("auto_refresh_fast_cache",
        // magentoSessionCacheAutoRefreshFastCache ? "1" : "0");
        // tokenMap.put("slow_backend_store_data",
        // magentoSessionCacheSlowBackendStoreData ? "1" : "0");
        // tokenMap.put("host", magentoSessionCacheMemcachedHost);
        // tokenMap.put("port", magentoSessionCacheMemcachedPort);
        // tokenMap.put("persistent", magentoSessionCacheMemcachedPersistent);
        // tokenMap.put("compression", magentoSessionCacheMemcachedCompression ?
        // "1" : "0");
        // tokenMap.put("cache_dir", magentoSessionCacheMemcachedCachedir);
        // tokenMap.put("hashed_directory_level",
        // magentoSessionCacheMemcachedHashedDirLevel);
        // tokenMap.put("hashed_directory_umask",
        // magentoSessionCacheMemcachedHashedDirUmask);
        // tokenMap.put("file_name_prefix",
        // magentoSessionCacheMemcachedFilePrefix);
        // tokenMap.put("weight", magentoSessionCacheMemcachedWeight);
        // tokenMap.put("retry_interval", magentoSessionCacheMemcachedInterval);
        // tokenMap.put("status", magentoSessionCacheMemcachedStatus);
        // tokenMap.put("timeout", magentoSessionCacheMemcachedTimeout);
        // tokenMap.put("header1", magentoRemoteAddrHeader1);
        // tokenMap.put("header2", magentoRemoteAddrHeader2);

        return tokenMap;
    }

}
