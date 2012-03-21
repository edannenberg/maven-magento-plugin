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

import de.bbe_consulting.mavento.helper.MagentoSqlUtil;

/**
 * Import a sql dump into database. Use magento.dump.file to specify the dump.<br/>
 * The dump file is expected in /sqldumps of your project root directory.<br/>
 * 
 * <pre>
 * mvn magento:import-db -Dmagento.dump.file=mydump.sql
 * </pre>
 * 
 * @goal import-db
 * @aggregator false
 * @requiresDependencyResolution compile
 * @author Erik Dannenberg
 */
public class MagentoImportDbMojo extends AbstractMagentoSqlMojo {

    /**
     * Mysql dump filename, dump is expected in /sqldumps of your project root.<br/>
     * 
     * @parameter expression="${magento.dump.file}"
     * @required
     */
    private String magentoDumpFile;

    public void execute() throws MojoExecutionException, MojoFailureException {

        File f = null;
        if (magentoDumpFile.startsWith("sqldumps/")) {
            f = new File(project.getBasedir() + "/" + magentoDumpFile);
        } else {
            f = new File(project.getBasedir() + "/sqldumps/" + magentoDumpFile);
        }
        if (!f.exists()) {
            throw new MojoExecutionException(
                    "Could not find dump file. Make sure it is placed in /sqldumps of your project root.");
        }
        MagentoSqlUtil.importSqlDump(f.getAbsolutePath(), magentoDbUser, magentoDbPasswd,
                magentoDbHost, magentoDbPort, magentoDbName, getLog());

    }

}
