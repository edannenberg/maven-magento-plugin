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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import de.bbe_consulting.mavento.helper.MagentoUtil;

/**
 * Install a Magento Module. This goal does not need an active Maven project. <br/>
 * It will however honor the properties in your pom.xml if called from a
 * project.<br/>
 * Use -DmagentoPath=/path/to/magento to override.<br/>
 * 
 * <pre>
 * mvn magento:insmod -DmodKey=Some_Module -DmodChannel=community -DmagentoPath=/path/to/magento/folder
 * </pre>
 * 
 * @goal insmod
 * @aggregator false
 * @requiresProject false
 * @author Erik Dannenberg
 */
public class MagentoInsModMojo extends AbstractMagentoSimpleMojo {

    /**
     * Module key.<br/>
     * 
     * @parameter expression="${modKey}"
     * @required
     */
    protected String modKey;
    
    /**
     * Magento Connect Channel. community|core<br/>
     * 
     * @parameter expression="${modChannel}" default-value="community"
     * @required
     */
    protected String modChannel;
    
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        initMojo();
        getLog().info("Scanning: " + magentoPath);
        getLog().info("");
        if (mVersion != null) {
            getLog().info("Version: Magento " + mVersion.toString());
        }

        File pearExecutable = null;
        if (mVersion.getMajorVersion() >= 1 && mVersion.getMinorVersion() >= 5) {
            pearExecutable = new File(magentoPath + "/mage");
        } else {
            pearExecutable = new File(magentoPath + "/pear");
        }

        pearExecutable.setExecutable(true, true);
        
        if (!modKey.isEmpty()) {
            String[] params = null;
            if (mVersion.getMajorVersion() == 1 && mVersion.getMinorVersion() <= 4) {
                String realChannel = "magento-core/";
                if (modChannel.equals("community")) {
                    realChannel = "magento-community/";
                }
                params = new String[] { "install", realChannel + modKey };
            } else {
                String realChannel = "http://connect20.magentocommerce.com/core";
                if (modChannel.equals("community")) {
                    realChannel = "http://connect20.magentocommerce.com/community";
                }
                if (modKey.contains("-")) {
                    final String[] s = modKey.split("-");
                    String extensionVersion = "";
                    if (s.length == 2) {
                        modKey = s[0];
                        extensionVersion = s[1];
                    }
                    getLog().info("Installing magento-" + modChannel + "/"
                                    + modKey + "-" + extensionVersion
                                    + "..");
                    params = new String[] { "install", realChannel, modKey, extensionVersion };
                } else {
                    getLog().info("Installing magento-" + modChannel + "/" + modKey + "..");
                    params = new String[] { "install", realChannel, modKey };
                }
            }
            MagentoUtil.executePearCommand(
                    pearExecutable.getAbsolutePath(), params, magentoPath,
                    mVersion, getLog());
            getLog().info("..done.");
        }
    }
}
