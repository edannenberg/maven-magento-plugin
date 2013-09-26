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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import de.bbe_consulting.mavento.helper.FileUtil;

/**
 * Pre unit test tasks. 
 * 
 * @goal pre-test
 * @requiresDependencyResolution test
 * @author Erik Dannenberg
 */
public class MagentoPreTestMojo extends AbstractMagentoSetupMojo {

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
     * If false the plugin will setup a fresh Magento instance for every test run.<br/>
     * 
     * @parameter expression="${magento.test.instance.reuse}" default-value="true"
     */
    protected Boolean magentoTestInstanceReuse;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        if (skipTests) {
            return;
        }

        if (!magentoTestInstanceReuse) {
            tempDir = Paths.get(phpDependenciesTargetDir).toString();
            final Path setupMarker = Paths.get(Paths.get(tempDir).getParent() + "/maven-magento-plugin/"
                    + Paths.get(phpDependenciesTargetDir).getFileName() + "_setup");
            try {
                FileUtil.deleteFile(setupMarker.toString(), getLog());
                FileUtil.deleteFile(tempDir.toString(),getLog());
            } catch (IOException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }
    }
}
