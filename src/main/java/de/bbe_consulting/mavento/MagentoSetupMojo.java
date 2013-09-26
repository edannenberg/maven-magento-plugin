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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/**
 * Setup a local Magento instance for development.
 * 
 * @goal setup
 * @aggregator false
 * @requiresDependencyResolution runtime
 * @author Erik Dannenberg
 */
public final class MagentoSetupMojo extends AbstractMagentoSetupMojo {

    public void execute() throws MojoExecutionException, MojoFailureException {
        tempDir = "target/magento_setup";
        targetDir = magentoRootLocal;
        setupMagento();
    }

}