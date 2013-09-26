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
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.xml.transform.TransformerException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.w3c.dom.Document;

import de.bbe_consulting.mavento.helper.FileUtil;
import de.bbe_consulting.mavento.helper.MagentoUtil;
import de.bbe_consulting.mavento.helper.MagentoSqlUtil;
import de.bbe_consulting.mavento.helper.MagentoXmlUtil;

/**
 * Provides a fresh Magento instance for integration tests if needed.
 * 
 * @goal setup-test
 * @requiresDependencyResolution test
 * @author Erik Dannenberg
 */
public class MagentoSetupTestMojo extends AbstractMagentoSetupMojo {

    /**
     * Location of all test dependencies.<br/>
     * 
     * @parameter expression="${php.dependencies.target.dir}"
     *            default-value="${project.build.directory}/php-deps"
     * @required
     */
    protected String phpDependenciesTargetDir;

    /**
     * Disable all tests.<br/>
     * 
     * @parameter expression="${skipTests}" default-value="false"
     */
    protected Boolean skipTests;

    /**
     * Magento instances for integration tests are kept in the project's build
     * directory.<br/>
     * The plugin will symlink the Magento instance to this location, usually a
     * htdocs/vhost directory.<br/>
     * 
     * @parameter expression="${magento.test.root.link}"
     */
    protected String magentoTestRootLink;

    /**
     * Magento base url of the integration test instance.
     * 
     * @parameter expression="${magento.test.url.base}"
     */
    protected String magentoTestUrlBase;

    /**
     * Magento https base url of the integration test instance.
     * 
     * @parameter expression="${magento.test.url.base.https}"
     */
    protected String magentoTestUrlBaseHttps;

    /**
     * Database name used for integration tests.
     * 
     * @parameter expression="${magento.test.db.name}"
     */
    protected String magentoTestDbName;

    /**
     * Database user for integration tests..
     * 
     * @parameter expression="${magento.test.db.user}"
     */
    protected String magentoTestDbUser;

    /**
     * Password for integration db user.
     * 
     * @parameter expression="${magento.test.db.passwd}"
     */
    protected String magentoTestDbPasswd;

    /**
     * Url to mysql database used for integration tests.<br/>
     * 
     * @parameter expression="${magento.test.db.host}"
     */
    protected String magentoTestDbHost;

    /**
     * Port of mysql database used for integration tests..<br/>
     * 
     * @parameter expression="${magento.test.db.port}"
     */
    protected String magentoTestDbPort;

    /**
     * Use a custom sql dump file for integration tests.<br/>
     * Only usefull when using vanilla Magento artifacts.
     * 
     * @parameter expression="${magento.test.db.dump.file.name}"
     */
    protected String magentoTestDumpFileName;

    /**
     * GroupId of a custom Magento artifact for integration tests.<br/>
     * 
     * @parameter expression="${magento.test.artifact.group.id}"
     *            default-value="com.varien"
     */
    protected String magentoTestArtifactGroupId;

    /**
     * ArtifactId of a custom Magento artifact for integration tests.<br/>
     * 
     * @parameter expression="${magento.test.artifact.id}"
     *            default-value="magento"
     */
    protected String magentoTestArtifactId;

    /**
     * Version of a custom Magento artifact for integration tests.<br/>
     * Does not need to reflect the actual Magento version of the artifact.
     * 
     * @parameter expression="${magento.test.artifact.version}"
     */
    protected String magentoTestArtifactVersion;

    /**
     * Where to look for the custom sql dump file.<br/>
     * false: dump is in $projectroot/sqldumps <br />
     * true: dump is supplied by magento artifact in
     * $artifactroot/mavento_setup/sql
     * 
     * @parameter expression="${magento.test.db.dump.supplied.by.artifact}"
     *            default-value="false"
     */
    protected Boolean magentoTestDumpSuppliedByArtifact;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        final List<String> l = session.getGoals();
        if (l.get(0).equals("eclipse:eclipse") || skipTests) {
            return;
        }

        isIntegrationTest = true;
        targetDir = Paths.get(phpDependenciesTargetDir).toAbsolutePath().toString();
        tempDir = targetDir;

        // override base urls
        if (magentoTestUrlBase != null && !magentoTestUrlBase.isEmpty()) {
            magentoUrlBase = magentoTestUrlBase;
        } else {
            if (magentoUrlBase == null || magentoUrlBase.isEmpty()) {
                final String rootDirName;
                if (magentoTestRootLink == null || magentoTestRootLink.isEmpty()) {
                    String localPath =  Paths.get(magentoRootLocal).toString();
                    if (localPath.endsWith("/")) {
                        localPath = localPath.substring(0, localPath.length()-1);
                    }
                    rootDirName = Paths.get(localPath+"_it").getFileName().toString();
                } else {
                    rootDirName = Paths.get(magentoTestRootLink).getFileName().toString();
                }
                magentoUrlBase = "127.0.0.1/"+Paths.get(rootDirName).getFileName();
            } else {
                if (magentoUrlBase.endsWith("/")) {
                    magentoUrlBase = magentoUrlBase.substring(0, magentoUrlBase.length()-1);
                }
                magentoUrlBase = magentoUrlBase+"_it";
            }
        }
        if (magentoTestUrlBaseHttps != null && !magentoTestUrlBaseHttps.isEmpty()) {
            magentoUrlBaseHttps = magentoTestUrlBaseHttps;
        } else {
            magentoUrlBaseHttps = null;
        }
        // override db settings
        if (magentoTestDbName == null || magentoTestDbName.isEmpty()) {
            magentoDbName += "_it";
        } else {
            magentoDbName = magentoTestDbName;
        }
        final String magentoFixtureDbName = magentoDbName + "_fixture";
        if (magentoTestDbHost != null && !magentoTestDbHost.isEmpty()) {
            magentoDbHost = magentoTestDbHost;
        }
        if (magentoTestDbPort != null && !magentoTestDbPort.isEmpty()) {
            magentoDbPort = magentoTestDbPort;
        }
        if (magentoTestDbUser != null && !magentoTestDbUser.isEmpty()) {
            magentoDbUser = magentoTestDbUser;
        }
        if (magentoTestDbPasswd != null && !magentoTestDbPasswd.isEmpty()) {
            magentoDbPasswd = magentoTestDbPasswd;
        }

        // override custom magento artifact settings
        if (magentoTestArtifactGroupId != null && !magentoTestArtifactGroupId.isEmpty()) {
            magentoArtifactGroupId = magentoTestArtifactGroupId;
        } else {
            magentoArtifactGroupId = null;
        }
        if (magentoTestArtifactId != null && !magentoTestArtifactId.isEmpty()) {
            magentoArtifactId = magentoTestArtifactId;
        } else {
            magentoArtifactId = null;
        }
        if (magentoTestDumpFileName != null && !magentoTestDumpFileName.isEmpty()) {
            magentoDumpFileName = magentoTestDumpFileName;
        } else {
            magentoDumpFileName = null;
        }

        // run setup if marker does not exist
        final Path setupMarker = Paths.get(Paths.get(tempDir).getParent() + "/maven-magento-plugin/"
                + Paths.get(phpDependenciesTargetDir).getFileName() + "_setup");
        if (Files.notExists(setupMarker)) {

            setupMagento();
            
            // setup ecomdev phpunit
            final Path ecomDevConfig = Paths.get(tempDir + "/app/etc/local.xml.phpunit");
            if (Files.exists(ecomDevConfig)) {
                final Document localXmlPhpUnit = MagentoXmlUtil.readXmlFile(ecomDevConfig.toString());
                MagentoXmlUtil.updateDbValues(magentoDbHost+":"+magentoDbPort, magentoDbUser, magentoDbPasswd,
                        magentoFixtureDbName, localXmlPhpUnit);
                MagentoXmlUtil.updateBaseUrls(magentoUrlBase, magentoUrlBaseHttps, magentoSeoUseRewrites,
                        localXmlPhpUnit);
                try {
                    MagentoXmlUtil.writeXmlFile(
                                    MagentoXmlUtil.transformXmlToString(localXmlPhpUnit),
                                    ecomDevConfig.toString());
                } catch (TransformerException e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
                MagentoSqlUtil.recreateMagentoDb(magentoDbUser, magentoDbPasswd, magentoDbHost, magentoDbPort,
                        magentoFixtureDbName, getLog());
            }
            
            // write marker so we dont run setup until a mvn clean occurs
            try {
                Files.createDirectories(setupMarker.getParent());
                Files.createFile(setupMarker);
            } catch (IOException e) {
                throw new MojoExecutionException("Error writing to build directory " + e.getMessage(), e);
            }

            // (re)create symlink for http requests
            if (magentoTestRootLink == null || magentoTestRootLink.isEmpty()) {
                if (magentoRootLocal.endsWith(File.pathSeparator)) {
                    magentoRootLocal = magentoRootLocal.substring(0, magentoRootLocal.length()-1);
                }
                magentoTestRootLink = magentoRootLocal + "_it";
            }
            getLog().info("Linking magento test instance to " + magentoTestRootLink + "..");
            final Path magentoSource = Paths.get(tempDir);
            final Path magentoTarget = Paths.get(magentoTestRootLink);
            try {
                if (Files.isSymbolicLink(magentoTarget)) {
                    Files.delete(magentoTarget);
                }
                if (Files.exists(magentoTarget, LinkOption.NOFOLLOW_LINKS)) {
                    throw new MojoExecutionException("Could not relink to: " + magentoTarget
                            + " ,check it's either a symlink or non existing.");
                }

                if (Files.notExists(magentoTarget)) {
                    Files.createSymbolicLink(magentoTarget, magentoSource);
                }
            } catch (IOException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
            getLog().info("..done.");

        }

        // copy module (test)(re)sources to magento instance so the autoloader can pick it up
        final Path moduleSource = Paths.get(project.getBuild().getOutputDirectory());
        final Path moduleTestSource = Paths.get(project.getBuild().getTestSourceDirectory());
        final Path moduleTestOutput = Paths.get(project.getBuild().getTestOutputDirectory());
        final Path buildDir = Paths.get(tempDir);
        FileUtil.copyFile(moduleSource, buildDir);
        FileUtil.copyFile(moduleTestSource, buildDir);
        FileUtil.copyFile(moduleTestOutput, buildDir);
        
        // make http request to init possible db changes by the module
        String backendUrl = MagentoUtil.validateBaseUrl(magentoUrlBase, false) + magentoBackendFrontendName;
        getLog().info("Sending http request to " + backendUrl );
        try {
            final URL magentoUrl = new URL(MagentoUtil.validateBaseUrl(backendUrl,false));
            final HttpURLConnection mc = (HttpURLConnection) magentoUrl.openConnection();
            int statusCode = mc.getResponseCode();
            if (statusCode == HttpURLConnection.HTTP_OK || statusCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                getLog().info("..response code: " + statusCode + " :)");
            } else {
                getLog().info("..response code: " + statusCode + " :(");
                throw new MojoExecutionException("Got http error " + statusCode + " from magento test instance.");
            }
        } catch (MalformedURLException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (IOException e) {
            throw new MojoExecutionException("Connection error: " + e.getMessage(), e);
        }
    }

}
