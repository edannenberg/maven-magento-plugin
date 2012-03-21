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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import de.bbe_consulting.mavento.helper.MagentoSqlUtil;

/**
 * Drop the current Magento database.
 * 
 * @goal drop-db
 * @aggregator false
 * @requiresDependencyResolution compile
 * @author Erik Dannenberg
 */
public final class MagentoDropDbMojo extends AbstractMagentoSqlMojo {

    public void execute() throws MojoExecutionException, MojoFailureException {
        MagentoSqlUtil.dropMagentoDb(magentoDbUser, magentoDbPasswd,
                magentoDbHost, magentoDbPort, magentoDbName, getLog());
    }

}
