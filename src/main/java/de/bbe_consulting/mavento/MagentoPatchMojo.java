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
import java.nio.file.Paths;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import de.bbe_consulting.mavento.helper.DiffUtil;
import difflib.PatchFailedException;

/**
 * Apply a diff style patch to an magento instance. Does not need a active Maven project.
 * It will however honor the properties in your pom.xml if called from a project root.
 * Use -DmagentoPath=/path/to/magento to override.<br/><br/>
 * 
 * Per default the plugin will start a dry run to see if all files can be patched successfully.
 * Use -DskipDryRun to disable. Not recommended on production systems. <br/>
 * 
 * <pre>
 * mvn magento:patch -DpatchFile=/path/to/patch.diff -DmagentoPath=/path/to/magento/folder
 * </pre>
 * 
 * @goal patch
 * @aggregator false
 * @requiresProject false
 * @author Erik Dannenberg
 */
public class MagentoPatchMojo extends AbstractMagentoSimpleMojo {

    /**
     * The diff style patch file.<br/>
     * 
     * @parameter expression="${patchFile}"
     * @required
     */
    protected String patchFile;
    
    /**
     * Run all patches without a complete test run. Not recommended for production systems.<br/> 
     * 
     * @parameter expression="${skipDryRun}"
     */
    protected boolean skipDryRun = false;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        initMojo();
        getLog().info("Scanning: " + magentoPath);
        getLog().info("");
        if (mVersion != null) {
            getLog().info("Version: Magento " + mVersion.toString());
        }

        getLog().info("");
        getLog().info("Applying " + Paths.get(patchFile).getFileName() + " to " +magentoPath);
        getLog().info("");

        try {
            if (!skipDryRun) {
                getLog().info("Starting dry run, no changes will be written to disk..");
                DiffUtil.patchDirectory(patchFile, magentoPath, true, false, getLog());
                getLog().info("");
            }
            getLog().info("Dry run successfull, applying patch..");
            DiffUtil.patchDirectory(patchFile, magentoPath, false, false, getLog());
        } catch (PatchFailedException | IOException e) {
            throw new MojoExecutionException("Error applying patch: " + e.getMessage(), e);
        }
    }

}
