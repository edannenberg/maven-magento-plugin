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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import de.bbe_consulting.mavento.helper.MagentoSqlUtil;

/**
 * Dump current Magento database timestamped to sqldumps/ of project base dir.<br/>
 * To override filename use:<br/>
 * 
 * <pre>
 * mvn magento:dump-db -Dmagento.db.dump.file=yourdump.sql
 * </pre>
 * 
 * @goal dump-db
 * @aggregator false
 * @requiresDependencyResolution compile
 * @author Erik Dannenberg
 */
public class MagentoDumpDbMojo extends AbstractMagentoSqlMojo {

    /**
     * @parameter expression="${magento.db.dump.file}"
     */
    private String magentoDumpFile;

    public void execute() throws MojoExecutionException, MojoFailureException {

        final File f = new File(project.getBasedir() + "/sqldumps");
        if (!f.exists()) {
            f.mkdirs();
        }
        if (magentoDumpFile == null || magentoDumpFile.isEmpty()) {
            final SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmm", new Locale("en", "EN"));
            final String magentoInstallDate = format.format(new Date());
            magentoDumpFile = project.getBasedir() + "/sqldumps/"
                    + magentoDbName + "-" + magentoInstallDate + ".sql";
        } else {
            magentoDumpFile = project.getBasedir() + "/sqldumps/" + magentoDumpFile;
        }
        MagentoSqlUtil.dumpSqlDb(magentoDumpFile, magentoDbUser, magentoDbPasswd,
                magentoDbHost, magentoDbPort, magentoDbName, getLog());
    }
}
