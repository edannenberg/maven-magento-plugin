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

import java.util.ArrayList;
import java.util.Arrays;

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

    /**
     * Comma seperated list of table names.
     * 
     * @parameter expression="${tables}"
     */
    private String magentoTables;

    /**
     * Limit the dumped data via sql where syntax.
     *  
     * @parameter expression="${where}"
     */
    private String magentoDeleteCondition;

    /**
     * Drop tables instead of truncating. Default: false
     *  
     * @parameter expression="${drop}" default="false"
     */
    private boolean magentoDropTables;

    /**
     * If true the plugin will not look for eav data tables to include. Default: false
     * 
     * @parameter expression="${skipTableCompletion}" default="false"
     */
    private boolean skipEntityTableCompletion;

    public void execute() throws MojoExecutionException, MojoFailureException {

        // drop some tables or whole db?
        if (magentoTables != null && !magentoTables.isEmpty()) {
            ArrayList<String> tableNames = new ArrayList<String>(Arrays.asList(magentoTables.split(",")));
            if (!skipEntityTableCompletion) {
                tableNames = MagentoSqlUtil.getEntityDataTables(tableNames,
                        magentoDbUser, magentoDbPasswd, magentoDbHost, magentoDbPort, magentoDbName);
            }
            MagentoSqlUtil.dropSqlTables(tableNames, magentoDeleteCondition, magentoDropTables, magentoDbUser, magentoDbPasswd,
                    magentoDbHost, magentoDbPort, magentoDbName, getLog());
        } else {
                MagentoSqlUtil.dropMagentoDb(magentoDbUser, magentoDbPasswd,
                        magentoDbHost, magentoDbPort, magentoDbName, getLog());
        }
    }

}
