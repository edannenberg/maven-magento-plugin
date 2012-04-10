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

package de.bbe_consulting.mavento.helper;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.CommandLineUtils.StringStreamConsumer;
import org.apache.maven.plugin.logging.Log;

import de.bbe_consulting.mavento.type.MagentoCoreConfig;
import de.bbe_consulting.mavento.type.MysqlTable;

/**
 * Magento related SQL helpers.
 * 
 * @author Erik Dannenberg
 */
public final class MagentoSqlUtil {

    /**
     * Private constructor, only static methods in this util class
     */
    private MagentoSqlUtil() {
    }

    /**
     * Reindex magento database.
     * 
     * @param magentoDir
     * @param logger
     * @throws MojoExecutionException
     */
    public static void indexDb(String magentoDir, Log logger)
            throws MojoExecutionException {

        final Commandline cl = new Commandline();
        cl.addArguments(new String[] { "indexer.php", "--reindexall" });
        cl.setWorkingDirectory(magentoDir + "/shell");
        cl.setExecutable("php");

        final StringStreamConsumer output = new CommandLineUtils.StringStreamConsumer();
        final StringStreamConsumer error = new CommandLineUtils.StringStreamConsumer();

        try {
            logger.info("Rebuilding all magento indices..");
            final int returnValue = CommandLineUtils.executeCommandLine(cl, output, error);
            if (returnValue != 0) {
                logger.info("retval: " + returnValue);
                logger.info(output.getOutput().toString());
                logger.info(error.getOutput().toString());
                throw new MojoExecutionException("Error while reindexing magento database!");
            }
            logger.info("..done.");
        } catch (CommandLineException e) {
            throw new MojoExecutionException("Error while reindexing magento database!", e);
        }
    }

    /**
     * Creates a new mysql database.
     * 
     * @param magentoDbUser
     * @param magentoDbPasswd
     * @param magentoDbHost
     * @param magentoDbPort
     * @throws MojoExecutionException
     */
    public static void createMagentoDb(String magentoDbUser, String magentoDbPasswd,
            String magentoDbHost, String magentoDbPort, String magentoDbName, Log logger) 
                    throws MojoExecutionException {

        final Commandline cl = getMysqlCommandLine(magentoDbUser, magentoDbPasswd,
                magentoDbHost, magentoDbPort);
        InputStream input = null;

        try {
            input = new ByteArrayInputStream(
                    ("CREATE DATABASE IF NOT EXISTS " + magentoDbName).getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new MojoExecutionException("Error while creating database!", e);
        }

        final StringStreamConsumer output = new CommandLineUtils.StringStreamConsumer();
        final StringStreamConsumer error = new CommandLineUtils.StringStreamConsumer();

        try {
            logger.info("Creating database " + magentoDbName + "..");
            final int returnValue = CommandLineUtils.executeCommandLine(cl, input, output, error);
            if (returnValue != 0) {
                logger.info("retval: " + returnValue);
                logger.info(output.getOutput().toString());
                logger.info(error.getOutput().toString());
                throw new MojoExecutionException("Error while creating database!");
            } else {
                logger.info("..done.");
            }
        } catch (CommandLineException e) {
            throw new MojoExecutionException("Error while creating database!", e);
        }
    }

    /**
     * Drops a mysql database.
     * 
     * @param magentoDbUser
     * @param magentoDbPasswd
     * @param magentoDbHost
     * @param magentoDbPort
     * @param magentoDbName
     * @param logger
     * @throws MojoExecutionException
     */
    public static void dropMagentoDb(String magentoDbUser,
            String magentoDbPasswd, String magentoDbHost, String magentoDbPort,
            String magentoDbName, Log logger) throws MojoExecutionException {

        final Commandline cl = getMysqlCommandLine(magentoDbUser, magentoDbPasswd, magentoDbHost, magentoDbPort);
        InputStream input = null;

        try {
            input = new ByteArrayInputStream(
                    ("DROP DATABASE " + magentoDbName).getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new MojoExecutionException("Error while dropping database!", e);
        }

        final StringStreamConsumer output = new CommandLineUtils.StringStreamConsumer();
        final StringStreamConsumer error = new CommandLineUtils.StringStreamConsumer();

        try {
            logger.info("Dropping database " + magentoDbName + "..");
            final int returnValue = CommandLineUtils.executeCommandLine(cl, input, output, error);
            if (returnValue != 0) {
                final String e = error.getOutput().toString();
                if (e.startsWith("ERROR 1008")) {
                    logger.warn("..Database does not exist!");
                } else {
                    logger.info(output.getOutput().toString());
                    logger.info(error.getOutput().toString());
                    logger.info("retval: " + returnValue);
                }
            }
            logger.info("..done.");
        } catch (CommandLineException e) {
            throw new MojoExecutionException("Error while dropping database", e);
        }
    }

    /**
     * Wrapper to drop and create a mysql database.
     * 
     * @param magentoDbUser
     * @param magentoDbPasswd
     * @param magentoDbHost
     * @param magentoDbPort
     * @param magentoDbName
     * @param logger
     * @throws MojoExecutionException
     */
    public static void recreateMagentoDb(String magentoDbUser, String magentoDbPasswd,
            String magentoDbHost, String magentoDbPort, String magentoDbName, Log logger) 
                    throws MojoExecutionException {

        dropMagentoDb(magentoDbUser, magentoDbPasswd, magentoDbHost, magentoDbPort, magentoDbName, logger);
        createMagentoDb(magentoDbUser, magentoDbPasswd, magentoDbHost, magentoDbPort, magentoDbName, logger);
    }
    
    /**
     * Imports a mysql dump.
     * 
     * @param sqlDump
     * @param magentoDbUser
     * @param magentoDbPasswd
     * @param magentoDbHost
     * @param magentoDbPort
     * @param magentoDbName
     * @param logger
     * @throws MojoExecutionException
     */
    public static void importSqlDump(String sqlDump, String magentoDbUser,
            String magentoDbPasswd, String magentoDbHost, String magentoDbPort,
            String magentoDbName, Log logger) throws MojoExecutionException {

        final Commandline cl = MagentoSqlUtil.getMysqlCommandLine(magentoDbUser,
                magentoDbPasswd, magentoDbHost, magentoDbPort, magentoDbName);
        final InputStream input;
        FileChannel channel = null;
        try {
            channel = new FileInputStream(Paths.get(sqlDump).toFile()).getChannel();
            input = Channels.newInputStream(channel);

            final StringStreamConsumer output = new CommandLineUtils.StringStreamConsumer();
            final StringStreamConsumer error = new CommandLineUtils.StringStreamConsumer();

            logger.info("Importing sql dump into database " + magentoDbName + "..");
            final int returnValue = CommandLineUtils.executeCommandLine(cl, input, output, error);
            if (returnValue != 0) {
                logger.info(output.getOutput().toString());
                logger.info(error.getOutput().toString());
                logger.info("retval: " + returnValue);
                throw new MojoExecutionException("Error while importing sql dump.");
            }
            logger.info("..done.");
        } catch (CommandLineException e) {
            throw new MojoExecutionException("Error while importing sql dump.", e);
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException("Error while reading sql dump.", e);
        } finally {
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Dumps a mysql database via mysqldump exec.
     * 
     * @param sqlDump
     * @param magentoDbUser
     * @param magentoDbPasswd
     * @param magentoDbHost
     * @param magentoDbPort
     * @param magentoDbName
     * @param logger
     * @throws MojoExecutionException
     */
    public static void dumpSqlDb(String sqlDump, String magentoDbUser,
            String magentoDbPasswd, String magentoDbHost, String magentoDbPort,
            String magentoDbName, Log logger) throws MojoExecutionException {

        final Commandline cl = getMysqlCommandLine(magentoDbUser, magentoDbPasswd, magentoDbHost, magentoDbPort);
        cl.setExecutable("mysqldump");
        cl.addArguments(new String[] { "-C", magentoDbName });
        cl.addArguments(new String[] {"--result-file=\"" + sqlDump + "\""});

        final StringStreamConsumer output = new CommandLineUtils.StringStreamConsumer();
        final StringStreamConsumer error = new CommandLineUtils.StringStreamConsumer();

        try {
            logger.info("Dumping database " + magentoDbName + " to " + sqlDump + "..");
            final int returnValue = CommandLineUtils.executeCommandLine(cl, output, error);
            if (returnValue != 0) {
                logger.info(error.getOutput().toString());
                logger.info("retval: " + returnValue);
                throw new MojoExecutionException("Error while exporting sql dump.");
            }
            logger.info("..done.");
        } catch (CommandLineException e) {
            throw new MojoExecutionException("Error while dumping from database " + magentoDbName + ".", e);
        }
    }

    /**
     * Gets a commandline object for mysql exec calls.
     * 
     * @param magentoDbUser
     * @param magentoDbPasswd
     * @param magentoDbHost
     * @param magentoDbPort
     * @param magentoDbName
     * @return Commandline object configured for mysql exec
     */
    public static Commandline getMysqlCommandLine(String magentoDbUser,
            String magentoDbPasswd, String magentoDbHost, String magentoDbPort, String magentoDbName) {

        final Commandline cl = getMysqlCommandLine(magentoDbUser, magentoDbPasswd, magentoDbHost, magentoDbPort);
        cl.addArguments(new String[] {magentoDbName});
        return cl;
    }

    /**
     * Gets a commandline object for mysql exec calls.
     * 
     * @param magentoDbUser
     * @param magentoDbPasswd
     * @param magentoDbHost
     * @param magentoDbPort
     * @return Commandline object configured for mysql exec
     */
    public static Commandline getMysqlCommandLine(String magentoDbUser,
            String magentoDbPasswd, String magentoDbHost, String magentoDbPort) {

        final Commandline cl = getMysqlCommandLine(magentoDbUser, magentoDbHost, magentoDbPort);
        if (magentoDbPasswd != null && !magentoDbPasswd.isEmpty()) {
            cl.addArguments(new String[] {"--password=" + magentoDbPasswd});
        }
        return cl;
    }

    /**
     * Get a commandline object for mysql exec calls.
     * 
     * @param magentoDbUser
     * @param magentoDbHost
     * @param magentoDbPort
     * @return
     */
    public static Commandline getMysqlCommandLine(String magentoDbUser,
            String magentoDbHost, String magentoDbPort) {

        final Commandline cl = new Commandline("mysql");
        cl.addArguments(new String[] { "--user=" + magentoDbUser,
                "--host=" + magentoDbHost, "--port=" + magentoDbPort });
        return cl;
    }

    /**
     * Constructs a mysql jdbc connection url.
     * 
     * @param magentoDbHost
     * @param magentoDbPort
     * @param magentoDbName
     * @return String with jdbc url
     */
    public static String getJdbcUrl(String magentoDbHost, String magentoDbPort, String magentoDbName) {
        return "jdbc:mysql://" + magentoDbHost + ":" + magentoDbPort + "/" + magentoDbName;
    }

    /**
     * Get jdbc connection.
     * 
     * @param magentoDbUser
     * @param magentoDbPasswd
     * @param jdbcUrl
     * @return
     * @throws MojoExecutionException
     */
    private static Connection getJdbcConnection(String magentoDbUser, String magentoDbPasswd, String jdbcUrl)
             throws MojoExecutionException {

        Connection c = null;
        try {
            final String mysqlDriver = "com.mysql.jdbc.Driver";
            Class.forName(mysqlDriver);
            c = DriverManager.getConnection(jdbcUrl, magentoDbUser, magentoDbPasswd);
        } catch (SQLException e) {
            throw new MojoExecutionException("SQL error. " + e.getMessage(), e);
        } catch (ClassNotFoundException e) {
            throw new MojoExecutionException("Could not find MySQL driver. " + e.getMessage(), e);
        }
        return c;
    }

    /**
     * Read values from magento's core_config_data table.
     * 
     * @param configData
     * @param magentoDbUser
     * @param magentoDbPasswd
     * @param jdbcUrl
     * @param logger
     * @return MagentoCoreConfig with config value.
     * @throws MojoExecutionException
     */
    public static MagentoCoreConfig getCoreConfigData(MagentoCoreConfig configData, String magentoDbUser,
            String magentoDbPasswd, String jdbcUrl, Log logger)
            throws MojoExecutionException {
        
        final Connection c = getJdbcConnection(magentoDbUser, magentoDbPasswd, jdbcUrl);

        try {
            final String query = "SELECT value FROM core_config_data WHERE path=? AND scope=? AND scope_id=?";
            final PreparedStatement st = c.prepareStatement(query);
            st.setString(1, configData.getPath());
            st.setString(2, configData.getScope());
            st.setInt(3, configData.getScopeId());
            final ResultSet r = st.executeQuery();
            if (r.next()) {
                configData.setValue(r.getString(1));
            } else {
                throw new MojoExecutionException(
                        "Could not find config entry for: "
                                + configData.getPath() + " with scope/id: "
                                + configData.getScope() + "/"
                                + configData.getScopeId());
            }
        } catch (SQLException e) {
            throw new MojoExecutionException("SQL error. " + e.getMessage(), e);
        } catch (Exception e) {
            throw new MojoExecutionException("Error. " + e.getMessage(), e);
        } finally {
            try {
                c.close();
            } catch (SQLException e) {
                throw new MojoExecutionException("Error closing database connection. " + e.getMessage(), e);
            }
        }
        return configData;
    }

    /**
     * Update/insert entries in magento's core_config_data table.
     * 
     * @param configData
     * @param magentoDbUser
     * @param magentoDbPasswd
     * @param jdbcUrl
     * @param logger
     * @throws MojoExecutionException
     */
    public static void setCoreConfigData(Map<String, String> configData,
            String magentoDbUser, String magentoDbPasswd, String jdbcUrl,
            Log logger) throws MojoExecutionException {
        
        final Connection c = getJdbcConnection(magentoDbUser, magentoDbPasswd, jdbcUrl);
        PreparedStatement st = null;
        final ArrayList<MagentoCoreConfig> newEntries = new ArrayList<MagentoCoreConfig>();
        final ArrayList<MagentoCoreConfig> existingEntries = new ArrayList<MagentoCoreConfig>();

        // filter non/existing entries for further processing
        try {
            String query = "SELECT value FROM core_config_data WHERE path=? AND scope=? AND scope_id=?";
            st = c.prepareStatement(query);
            MagentoCoreConfig configEntry = null;
            for (Map.Entry<String, String> rawConfigEntry : configData.entrySet()) {
                configEntry = new MagentoCoreConfig(rawConfigEntry.getKey(), rawConfigEntry.getValue());
                st.setString(1, configEntry.getPath());
                st.setString(2, configEntry.getScope());
                st.setInt(3, configEntry.getScopeId());
                ResultSet r = st.executeQuery();
                if (r.next()) {
                    existingEntries.add(configEntry);
                } else {
                    newEntries.add(configEntry);
                }
            }

            // insert new config entries
            if (!newEntries.isEmpty()) {
                c.setAutoCommit(false);
                final String insertQuery = "INSERT INTO core_config_data (scope, scope_id, path, value) VALUES(?,?,?,?)";
                st = c.prepareStatement(insertQuery);
                for (MagentoCoreConfig newConfigEntry : newEntries) {
                    st.setString(1, newConfigEntry.getScope());
                    st.setInt(2, newConfigEntry.getScopeId());
                    st.setString(3, newConfigEntry.getPath());
                    st.setString(4, newConfigEntry.getValue());
                    st.addBatch();
                }
                final int[] insertCounts = st.executeBatch();
                for (int i = 0; i < insertCounts.length; i++) {
                    switch (insertCounts[i]) {
                    case Statement.SUCCESS_NO_INFO:
                        break;
                    case Statement.EXECUTE_FAILED:
                        throw new MojoExecutionException("Error inserting entries in core_config_data");
                    default:
                        break;
                    }
                }
                c.commit();
            }

            // update existing config entries
            if (!existingEntries.isEmpty()) {
                c.setAutoCommit(false);
                final String updateQuery = "UPDATE core_config_data SET value=? WHERE scope=? AND scope_id=? AND path=?";
                st = c.prepareStatement(updateQuery);
                for (MagentoCoreConfig oldConfigEntry : existingEntries) {
                    st.setString(1, oldConfigEntry.getValue());
                    st.setString(2, oldConfigEntry.getScope());
                    st.setInt(3, oldConfigEntry.getScopeId());
                    st.setString(4, oldConfigEntry.getPath());
                    st.addBatch();
                }
                final int[] updateCounts = st.executeBatch();
                for (int i = 0; i < updateCounts.length; i++) {
                    switch (updateCounts[i]) {
                    case Statement.SUCCESS_NO_INFO:
                        break;
                    case Statement.EXECUTE_FAILED:
                        throw new MojoExecutionException("Error updating entries in core_config_data");
                    default:
                        break;
                    }
                }
                c.commit();
            }

        } catch (SQLException e) {
            throw new MojoExecutionException("SQL error. " + e.getMessage(), e);
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } finally {
            try {
                c.close();
            } catch (SQLException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }

    }

    /**
     * Update magento admin user/role.
     * 
     * @param configData
     * @param magentoDbUser
     * @param magentoDbPasswd
     * @param jdbcUrl
     * @param logger
     * @throws MojoExecutionException
     */
    public static void updateAdminUser(Map<String, String> configData,
            String magentoDbUser, String magentoDbPasswd, String jdbcUrl,
            Log logger) throws MojoExecutionException {

        final Connection c = getJdbcConnection(magentoDbUser, magentoDbPasswd, jdbcUrl);

        try {
            int userId = 0;

            String query = "SELECT * FROM admin_role WHERE role_type='U' ORDER BY user_id ASC";
            PreparedStatement st = c.prepareStatement(query);
            ResultSet r = st.executeQuery();

            if (r.next()) {
                userId = r.getInt("user_id");
                query = "UPDATE admin_role SET role_name=? WHERE role_id=?";
                st = c.prepareStatement(query);
                st.setString(1, configData.get("ADMIN_NAME_FIRST"));
                st.setInt(2, r.getInt("role_id"));
                st.executeUpdate();
            }

            query = "SELECT * FROM admin_user WHERE user_id=?";
            st = c.prepareStatement(query);
            st.setInt(1, userId);
            r = st.executeQuery();

            final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
            final Date d = sdf.parse(configData.get("INSTALL_DATESQL"));
            final Timestamp ts = new Timestamp(d.getTime());

            if (r.next()) {
                query = "UPDATE admin_user SET firstname=?, lastname=?, email=?, username=?, password=?, created=?, modified=?, logdate=? WHERE user_id=?";
                st = c.prepareStatement(query);
                st.setString(1, configData.get("ADMIN_NAME_FIRST"));
                st.setString(2, configData.get("ADMIN_NAME_LAST"));
                st.setString(3, configData.get("ADMIN_EMAIL"));
                st.setString(4, configData.get("ADMIN_USERNAME"));
                st.setString(5, configData.get("ADMIN_PASSWD"));
                st.setTimestamp(6, ts);
                st.setTimestamp(7, ts);
                st.setTimestamp(8, ts);
                st.setInt(9, userId);
                st.executeUpdate();
            }

        } catch (SQLException e) {
            throw new MojoExecutionException("SQL error. " + e.getMessage(), e);
        } catch (ParseException e) {
            throw new MojoExecutionException("Error parsing install date. " + e.getMessage(), e);
        } finally {
            try {
                c.close();
            } catch (SQLException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }
    }

    /**
     * Update cache options for magento>1.4.0.0
     * 
     * @param configData
     * @param magentoDbUser
     * @param magentoDbPasswd
     * @param jdbcUrl
     * @param logger
     * @throws MojoExecutionException
     */
    public static void updateCacheConfig(Map<String, String> configData,
            String magentoDbUser, String magentoDbPasswd, String jdbcUrl,
            Log logger) throws MojoExecutionException {

        final Connection c = getJdbcConnection(magentoDbUser, magentoDbPasswd, jdbcUrl);

        try {
            c.setAutoCommit(false);
            final String query = "UPDATE core_cache_option SET value=? WHERE code=?";
            final PreparedStatement st = c.prepareStatement(query);

            for (Map.Entry<String, String> configEntry : configData.entrySet()) {
                st.setString(1, configEntry.getValue());
                st.setString(2, configEntry.getKey());
                st.addBatch();
            }
            final int[] updateCounts = st.executeBatch();
            for (int i = 0; i < updateCounts.length; i++) {
                switch (updateCounts[i]) {
                case Statement.SUCCESS_NO_INFO:
                    break;
                case Statement.EXECUTE_FAILED:
                    break;
                default:
                    break;
                }
            }
            c.commit();
        } catch (SQLException e) {
            throw new MojoExecutionException("SQL error. " + e.getMessage(), e);
        } finally {
            try {
                c.close();
            } catch (SQLException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }
    }

    /**
     * Truncate magento's log tables.<br/>
     * Affected tables: <br/>
     *      dataflow_batch_export<br/>
     *      dataflow_batch_import<br/>
     *      log_url<br/>
     *      log_url_info<br/>
     *      log_visitor<br/>
     *      log_visitor_info<br/>
     *      report_event<br/>
     *
     * @param magentoDbUser
     * @param magentoDbPasswd
     * @param jdbcUrl
     * @param logger
     * @throws MojoExecutionException
     */
    public static void truncateLogTables(String magentoDbUser, String magentoDbPasswd, String jdbcUrl,
            boolean includeViewedProduct, Log logger) throws MojoExecutionException {
        
        final List<String> tableData = new ArrayList<String> ();
        tableData.add("dataflow_batch_export");
        tableData.add("dataflow_batch_import");
        tableData.add("log_url");
        tableData.add("log_url_info");
        tableData.add("log_visitor");
        tableData.add("log_visitor_info");
        tableData.add("report_event");
        if (includeViewedProduct) {
            tableData.add("report_viewed_product_index");
        }
        truncateTables(tableData, magentoDbUser, magentoDbPasswd, jdbcUrl, logger);
    }
    
    /**
     * Truncates magento's sales tables.
     * 
     * @param magentoDbUser
     * @param magentoDbPasswd
     * @param jdbcUrl
     * @param logger
     * @throws MojoExecutionException
     */
    public static void truncateSalesTables(String magentoDbUser, String magentoDbPasswd, String jdbcUrl, Log logger)
            throws MojoExecutionException {

        final List<String> tableData = new ArrayList<String> ();
        tableData.add("sales_flat_creditmemo");
        tableData.add("sales_flat_creditmemo_comment");
        tableData.add("sales_flat_creditmemo_grid");
        tableData.add("sales_flat_creditmemo_item");
        tableData.add("sales_flat_invoice");
        tableData.add("sales_flat_invoice_comment");
        tableData.add("sales_flat_invoice_grid");
        tableData.add("sales_flat_invoice_item");
        tableData.add("sales_flat_order");
        tableData.add("sales_flat_order_address");
        tableData.add("sales_flat_order_grid");
        tableData.add("sales_flat_order_item");
        tableData.add("sales_flat_order_payment");
        tableData.add("sales_flat_order_status_history");
        tableData.add("sales_flat_quote");
        tableData.add("sales_flat_quote_address");
        tableData.add("sales_flat_quote_address_item");
        tableData.add("sales_flat_quote_item");
        tableData.add("sales_flat_quote_item_option");
        tableData.add("sales_flat_quote_payment");
        tableData.add("sales_flat_quote_shipping_rate");
        tableData.add("sales_flat_shipment");
        tableData.add("sales_flat_shipment_comment");
        tableData.add("sales_flat_shipment_grid");
        tableData.add("sales_flat_shipment_item");
        tableData.add("sales_flat_shipment_track");
        tableData.add("sales_invoiced_aggregated");
        tableData.add("sales_invoiced_aggregated_order");
        tableData.add("log_quote");
        tableData.add("downloadable_link_purchased");
        tableData.add("downloadable_link_purchased_item");
        tableData.add("eav_entity_store");
        truncateTables(tableData, magentoDbUser, magentoDbPasswd, jdbcUrl, logger);
    }
    
    /**
     * Truncates magento's customer tables.
     * 
     * @param magentoDbUser
     * @param magentoDbPasswd
     * @param jdbcUrl
     * @param logger
     * @throws MojoExecutionException
     */
    public static void truncateCustomerTables(String magentoDbUser, String magentoDbPasswd, String jdbcUrl, Log logger)
            throws MojoExecutionException {

        final List<String> tableData = new ArrayList<String> ();
        tableData.add("customer_address_entity");
        tableData.add("customer_address_entity_datetime");
        tableData.add("customer_address_entity_decimal");
        tableData.add("customer_address_entity_int");
        tableData.add("customer_address_entity_text");
        tableData.add("customer_address_entity_varchar");
        tableData.add("customer_entity");
        tableData.add("customer_entity_datetime");
        tableData.add("customer_entity_decimal");
        tableData.add("customer_entity_int");
        tableData.add("customer_entity_text");
        tableData.add("customer_entity_varchar");
        tableData.add("tag");
        tableData.add("tag_relation");
        tableData.add("tag_summary");
        tableData.add("tag_properties");
        tableData.add("wishlist");
        tableData.add("log_customer");
        tableData.add("report_viewed_product_index");
        tableData.add("sendfriend_log");

        truncateTables(tableData, magentoDbUser, magentoDbPasswd, jdbcUrl, logger);
    }

    /**
     * Mass truncate magento db tables.
     * 
     * @param tableNames
     * @param magentoDbUser
     * @param magentoDbPasswd
     * @param jdbcUrl
     * @param logger
     * @throws MojoExecutionException
     */
    public static void truncateTables(List<String> tableNames, String magentoDbUser, String magentoDbPasswd,
            String jdbcUrl, Log logger) throws MojoExecutionException {
        
        final Connection c = getJdbcConnection(magentoDbUser, magentoDbPasswd, jdbcUrl);

        try {
            c.setAutoCommit(false);
            final Statement st = c.createStatement();
            for (String tableName : tableNames) {
                st.addBatch("TRUNCATE TABLE " + tableName);
            }
            final int[] updateCounts = st.executeBatch();
            for (int i = 0; i < updateCounts.length; i++) {
                switch (updateCounts[i]) {
                case Statement.SUCCESS_NO_INFO:
                    break;
                case Statement.EXECUTE_FAILED:
                    break;
                default:
                    break;
                }
            }
            c.commit();
        } catch (SQLException e) {
            throw new MojoExecutionException("SQL error. " + e.getMessage(), e);
        } finally {
            try {
                c.close();
            } catch (SQLException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }
    }
    
    /**
     * Get mysql database size in mb.
     *  
     * @param dbNameToCheck
     * @param magentoDbUser
     * @param magentoDbPasswd
     * @param jdbcUrl
     * @param logger
     * @return String size in mb
     * @throws MojoExecutionException
     */
    public static Map<String,Integer> getDbSize (String dbNameToCheck, String magentoDbUser, String magentoDbPasswd, String jdbcUrl, Log logger)
            throws MojoExecutionException {
        
        final Connection c = getJdbcConnection(magentoDbUser, magentoDbPasswd, jdbcUrl);
        final HashMap<String, Integer> result = new HashMap<String, Integer>();
        try {
            final String query = "SELECT SUM( ROUND( ( (DATA_LENGTH + INDEX_LENGTH) /1024 /1024 ) , 0 ))" +
                    " 'db_size_in_mb', " +
                    "SUM(table_rows) FROM INFORMATION_SCHEMA.TABLES " +
                    "WHERE TABLE_SCHEMA = ?";
            final PreparedStatement st = c.prepareStatement(query);
            st.setString(1, dbNameToCheck);
            final ResultSet r = st.executeQuery();
            if (r.next()) {
                result.put("totalSize", r.getInt(1));
                result.put("totalRows", r.getInt(2));
            } else {
                throw new MojoExecutionException("Error fetching db details.");
            }
        } catch (SQLException e) {
            throw new MojoExecutionException("SQL error. " + e.getMessage(), e);
        } catch (Exception e) {
            throw new MojoExecutionException("Error. " + e.getMessage(), e);
        } finally {
            try {
                c.close();
            } catch (SQLException e) {
                throw new MojoExecutionException("Error closing database connection. " + e.getMessage(), e);
            }
        }
        return result;
    }

    /**
     * Get magento log tables details.
     * 
     * @param dbNameToCheck
     * @param magentoDbUser
     * @param magentoDbPasswd
     * @param jdbcUrl
     * @param logger
     * @return List<MysqlTable>
     * @throws MojoExecutionException
     */
    public static List<MysqlTable> getLogTablesSize (String dbNameToCheck, String magentoDbUser, String magentoDbPasswd, String jdbcUrl, Log logger)
            throws MojoExecutionException {

        final Connection c = getJdbcConnection(magentoDbUser, magentoDbPasswd, jdbcUrl);
        final ArrayList<MysqlTable> tableList = new ArrayList<MysqlTable>();
        try {
            final String query = "SELECT TABLE_NAME, table_rows, data_length, index_length " +
                    "FROM information_schema.TABLES WHERE table_schema = ? " +
                    "AND (TABLE_NAME like ? OR TABLE_NAME like ?) " +
                    "ORDER BY table_rows DESC";
            final PreparedStatement st = c.prepareStatement(query);
            st.setString(1, dbNameToCheck);
            st.setString(2, "log_%");
            st.setString(3, "report_%");
            final ResultSet r = st.executeQuery();
            while (r.next()) {
                MysqlTable m = new MysqlTable();
                m.setDbName(dbNameToCheck);
                m.setTableName(r.getString(1));
                m.setTableRows(r.getInt(2));
                m.setTableLength(r.getInt(3));
                m.setTableIndexLength(r.getInt(4));
                tableList.add(m);
            }
        } catch (SQLException e) {
            throw new MojoExecutionException("SQL error. " + e.getMessage(), e);
        } catch (Exception e) {
            throw new MojoExecutionException("Error. " + e.getMessage(), e);
        } finally {
            try {
                c.close();
            } catch (SQLException e) {
                throw new MojoExecutionException("Error closing database connection. " + e.getMessage(), e);
            }
        }
        return tableList;
    }

}
