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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import de.bbe_consulting.mavento.helper.FileUtil;

/**
 * Enable sql query logs in Magento instance. Will act as a trigger when called with no arguments.<br/>
 * 
 * -DlogSql - Enable sql logging? <br/>
 * -DlogAllSql - Ignore execution time threshold and log everything? Does *not* override logSql. <br/>
 * -DqueryTime - Query execution time threshold in seconds. <br/>
 * -DcallStack - Add call stack information for logged queries? <br/>
 * -DlogFile - Sql Log file path, default: var/log/debug.log <br/>
 * 
 * @goal logsql
 * @aggregator false
 * @requiresProject false
 * @author Erik Dannenberg
 */
public class MagentoLogSqlMojo extends AbstractMagentoSimpleMojo {

    /**
     * Display current sql logging settings only.<br/>
     * 
     * @parameter expression="${status}" default-value="false"
     */
    protected Boolean status;

    /**
     * Explicitly enable or disable sql logging.<br/>
     * 
     * @parameter expression="${logSql}"
     */
    protected Boolean logSql;

    /**
     * Ignore queryTime property and log everything. Does *not* override logSql.<br/>
     * 
     * @parameter expression="${logAllSql}"
     */
    protected Boolean logAllSql;

    /**
     * Query execution time threshold for logging.<br/>
     * 
     * @parameter expression="${queryTime}"
     */
    protected Float queryTime;

    /**
     * Add call stack information to logged queries.<br/>
     * 
     * @parameter expression="${callStack}"
     */
    protected Boolean callStack;

    /**
     * Location and name of the file sql queries are logged to. Relative paths are starting from magento root.<br/>
     * 
     * @parameter expression="${logFile}"
     */
    protected String logFile;

    protected final String logSqlMatch = "protected\\s\\$_debug\\s*=\\s(false|true);";
    protected final String logAllSqlMatch = "protected\\s\\$_logAllQueries\\s*=\\s(false|true);";
    protected final String queryTimeMatch = "protected\\s\\$_logQueryTime\\s*=\\s([0-9]*\\.[0-9]*);";
    protected final String callStackMatch = "protected\\s\\$_logCallStack\\s*=\\s(false|true);";
    protected final String logFileMatch = "protected\\s\\$_debugFile\\s*=\\s'(.*)';";
    
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        initMojo();
        getLog().info("Scanning: " + magentoPath);
        getLog().info("");
        if (mVersion != null) {
            getLog().info("Version: Magento " + mVersion.toString());
        }

        // slurp php source
        Path phpFile = Paths.get(magentoPath + "/lib/Varien/Db/Adapter/Pdo/Mysql.php");
        String phpSource;
        try {
            phpSource = FileUtil.getFileAsString(phpFile.toString());
        } catch (IOException e) {
            throw new MojoFailureException(e.getMessage());
        }

        getLog().info("");
        // display all sql logging values and exit
        if (status) {
            getLog().info("Parsing " + phpFile.toString());
            getLog().info("");

            Matcher m = findMatch(logSqlMatch, phpSource);
            getLog().info("logSql   : " + m.group(1));

            m = findMatch(logAllSqlMatch, phpSource);
            getLog().info("logAllSql: " + m.group(1));

            m = findMatch(queryTimeMatch, phpSource);
            getLog().info("queryTime: " + m.group(1) + "s");

            m = findMatch(callStackMatch, phpSource);
            getLog().info("callStack: " + m.group(1));

            m = findMatch(logFileMatch, phpSource);
            getLog().info("logFile  : " + m.group(1));

            getLog().info("");
            return;
        }

        // if no parameters are given parse _debug value and invert it
        if (logSql == null && logAllSql == null && queryTime == null && callStack == null && logFile == null) {
            Matcher m = findMatch(logSqlMatch, phpSource);
            logSql = !Boolean.parseBoolean(m.group(1));
        }

        // update stuff
        if (logSql != null) {
            getLog().info((logSql ? "Enable" : "Disable") + " sql query logging..");
            phpSource = phpSource.replaceAll(logSqlMatch,
                    "protected \\$_debug               = " + logSql.toString() + ";");
            getLog().info("..done.");
        }

        if (logAllSql != null) {
            getLog().info((!logAllSql ? "Enable" : "Disable") + " execution time threshold..");
            phpSource = phpSource.replaceAll(logAllSqlMatch,
                    "protected \\$_logAllQueries       = " + logAllSql.toString() + ";");
            getLog().info("..done.");
        }

        if (queryTime != null) {
            getLog().info("Set query execution time threshold to: " + logAllSql.toString() + "s");
            phpSource = phpSource.replaceAll(queryTimeMatch,
                    "protected \\$_logQueryTime        = " + queryTime + ";");
            getLog().info("..done.");
        }

        if (callStack != null) {
            getLog().info((callStack ? "Enable" : "Disable") + " call stack information..");
            phpSource = phpSource.replaceAll(callStackMatch,
                    "protected \\$_logCallStack        = " + callStack.toString() + ";");
            getLog().info("..done.");
        }

        if (logFile != null) {
            getLog().info("Set sql log file path to: " + logFile);
            phpSource = phpSource.replaceAll(logFileMatch,
                    "protected \\$_debugFile           = '" + logFile + "';");
            getLog().info("..done.");
        }

        // write changes back
        try {
            FileUtil.writeFile(phpSource, phpFile);
        } catch (IOException e) {
            throw new MojoFailureException(e.getMessage());
        }
    }

    private Matcher findMatch(String regex, String text) throws MojoFailureException {
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(text);
        if (m.find()) {
            return m;
        } else {
            throw new MojoFailureException("Could not find reference for " + regex + " in php source.");
        }
    }
}
