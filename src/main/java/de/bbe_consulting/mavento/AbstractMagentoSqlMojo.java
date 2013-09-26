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

/**
 * Abstract class for mojos that want to access the magento database.
 * 
 * @author Erik Dannenberg
 */

public abstract class AbstractMagentoSqlMojo extends AbstractMagentoMojo {

    /** 
     * Database name.
     * @parameter expression="${magento.db.name}"
     * @required
     */
    protected String magentoDbName;

    /** 
     * Database user.
     * @parameter expression="${magento.db.user}"
     * @required
     */
    protected String magentoDbUser;

    /** 
     * Password for db user.
     * @parameter expression="${magento.db.passwd}"
     */
    protected String magentoDbPasswd;

    /**
     * Url to mysql database.<br/>
     * @parameter expression="${magento.db.host}" default-value="localhost"
     */
    protected String magentoDbHost;

    /** 
     * Port of mysql database.<br/>
     * @parameter expression="${magento.db.port}" default-value="3306"
     */
    protected String magentoDbPort;

}
