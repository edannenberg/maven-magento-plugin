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

package de.bbe_consulting.mavento.type;

/**
 * Mysql table details.
 * 
 * @author Erik Dannenberg
 */
public class MysqlTable {
    
    /**
     * Database name this table belongs to.
     */
    private String dbName;
    /**
     * The table name.
     */
    private String tableName;
    /**
     * Table row count.
     */
    private int tableRows;
    /**
     * Table data size in bytes.
     */
    private int tableLength;
    /**
     * Table index size in bytes.
     */
    private int tableIndexLength;

    public String getDbName() {
        return dbName;
    }
    public void setDbName(String dbName) {
        this.dbName = dbName;
    }
    public String getTableName() {
        return tableName;
    }
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }
    public int getTableRows() {
        return tableRows;
    }
    public void setTableRows(int tableRows) {
        this.tableRows = tableRows;
    }
    public int getTableLength() {
        return tableLength;
    }
    public void setTableLength(int tableLength) {
        this.tableLength = tableLength;
    }
    public int getTableIndexLength() {
        return tableIndexLength;
    }
    public void setTableIndexLength(int tableIndexLength) {
        this.tableIndexLength = tableIndexLength;
    }

    public int getTableSizeInMb() {
        return (getTableLength() + getTableIndexLength())/1024/1024;
    }

    public String getFormatedTableSizeInMb() {
        return String.format("%,8d", getTableSizeInMb()).trim();
    }

    public String getFormatedTableEntries() {
        return String.format("%,8d", getTableRows()).trim();
    }
}
