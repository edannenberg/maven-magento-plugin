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
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import de.bbe_consulting.mavento.helper.MagentoUtil;
import de.bbe_consulting.mavento.helper.visitor.CopyFilesVisitor;

/**
 * Provides a fresh Magento instance for integration tests if needed.
 * 
 * @goal setup-test
 * @requiresDependencyResolution compile
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
     * @parameter expression="${magento.test.ignore}" default-value="false"
     */
    protected Boolean magentoTestIgnore;

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
     * @parameter expression="${magento.test.db.host}" default-value="localhost"
     */
    protected String magentoTestDbHost;

    /**
     * Port of mysql database used for integration tests..<br/>
     * 
     * @parameter expression="${magento.test.db.port}" default-value="3306"
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
        if (l.get(0).equals("eclipse:eclipse") || magentoTestIgnore) {
            return;
        }

        if (magentoTestDbName == null || magentoTestDbPasswd == null
                || magentoTestDbUser == null || magentoTestRootLink == null
                || magentoTestUrlBase == null) {
            throw new MojoExecutionException(
                    "Error missing properties magento.test.db.* ,magento.test.root.link or magento.test.url.base."
                            + "Or disable tests altogether by setting magento.test.ignore to true.");
        }

        isIntegrationTest = true;
        targetDir = Paths.get(phpDependenciesTargetDir).toAbsolutePath().toString();
        tempDir = targetDir;

        // override base urls
        magentoUrlBase = MagentoUtil.validateBaseUrl(magentoTestUrlBase, false);
        magentoTestUrlBase = magentoUrlBase;
        if (magentoTestUrlBaseHttps != null && !magentoTestUrlBaseHttps.isEmpty()) {
            magentoUrlBaseHttps = magentoTestUrlBaseHttps;
        } else {
            magentoUrlBaseHttps = null;
        }
        // override db settings
        magentoDbHost = magentoTestDbHost;
        magentoDbPort = magentoTestDbPort;
        magentoDbUser = magentoTestDbUser;
        magentoDbPasswd = magentoTestDbPasswd;
        magentoDbName = magentoTestDbName;

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
        final Path setupMarker = Paths.get(buildDirectory.getAbsolutePath() + "/maven-magento-plugin/"
                + Paths.get(phpDependenciesTargetDir).getFileName() + "_setup");
        if (Files.notExists(setupMarker)) {

            setupMagento();

            // write marker so we dont run setup until a mvn clean occurs
            try {
                Files.createDirectories(setupMarker.getParent());
                Files.createFile(setupMarker);
            } catch (IOException e) {
                throw new MojoExecutionException("Error writing to build directory " + e.getMessage(), e);
            }

            // (re)create symlink for http requests
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

        // copy module source to magento instance so the autoloader can pick it up
        final Path moduleSource = Paths.get(project.getBasedir().getAbsolutePath() + "/src/main/php");
        if (Files.exists(moduleSource)) {
            final Path moduleTarget = Paths.get(tempDir);
            final CopyFilesVisitor crv = new CopyFilesVisitor(moduleSource, moduleTarget, false);
            try {
                Files.walkFileTree(moduleSource, crv);
            } catch (IOException e) {
                throw new MojoExecutionException("Error copying module source to: " + moduleTarget + " "
                                + e.getMessage(), e);
            }
        }

        // make http request to init possible db changes by the module
        getLog().info("Sending http request to " + magentoTestUrlBase + "admin");
        try {
            final URL magentoUrl = new URL(magentoTestUrlBase + "index.php/admin");
            final HttpURLConnection mc = (HttpURLConnection) magentoUrl.openConnection();
            int statusCode = mc.getResponseCode();
            if (statusCode != HttpURLConnection.HTTP_OK) {
                getLog().info("..response code: " + statusCode + " :(");
                throw new MojoExecutionException("Got http error " + statusCode + " from magento test instance.");
            } else {
                getLog().info("..response code: " + statusCode + " :)");
            }
        } catch (MalformedURLException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (IOException e) {
            throw new MojoExecutionException("Connection error: " + e.getMessage(), e);
        }
    }

}
