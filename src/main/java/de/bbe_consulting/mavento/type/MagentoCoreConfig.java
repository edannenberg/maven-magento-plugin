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

package de.bbe_consulting.mavento.type;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Magento Core Config Entry
 * 
 * @author Erik Dannenberg
 */
public class MagentoCoreConfig {

	private String configPath;
	private String configValue;
	private String scopeCode = "default";
	private Integer scopeId = 0;
	
	public MagentoCoreConfig(String path) throws Exception {
		parseConfigPath(path);
	}
	
	public MagentoCoreConfig(String cPath, String cValue) throws Exception {
		parseConfigPath(cPath);
		configValue = cValue;
	}
	
	private void parseConfigPath(String cPath) throws Exception {
		Pattern pattern = Pattern.compile("((?!--)[0-9a-zA-Z/\\-_]+)-{2}?([a-z]+)?-{2}?([0-9]+)?");
		Matcher matcher = pattern.matcher(cPath);
		if (matcher.find()) {
			configPath = matcher.group(1);
	    	if (matcher.group(2) != null) {
	    		scopeCode = matcher.group(2);
	    	} 
	    	if (matcher.group(3) != null) {
	    		scopeId = Integer.parseInt(matcher.group(3));
	    	} 
		} else if (cPath != null && !cPath.isEmpty()) {
			configPath = cPath;
    	} else {
    		throw new Exception("Could not parse path of core_config_data entry.");
    	}
	}
	
	public String getPath() {
		return configPath;
	}

	public void setPath(String configPath) {
		this.configPath = configPath;
	}

	public String getValue() {
		return configValue;
	}

	public void setValue(String configValue) {
		this.configValue = configValue;
	}

	public String getScope() {
		return scopeCode;
	}

	public void setScope(String scopeCode) {
		this.scopeCode = scopeCode;
	}

	public Integer getScopeId() {
		return scopeId;
	}

	public void setScopeId(Integer scopeId) {
		this.scopeId = scopeId;
	}
	
	public String toString() {
		return "path: " + getPath() + " value: " + getValue() + " scope/iD: " + getScope() + "/" + getScopeId(); 
	}
	
}
