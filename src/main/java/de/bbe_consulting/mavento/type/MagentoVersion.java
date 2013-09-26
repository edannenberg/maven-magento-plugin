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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Magento Version
 * 
 * @author Erik Dannenberg
 */
public class MagentoVersion {

    private Integer magentoVersionMajor;
    private Integer magentoVersionMinor;
    private Integer magentoVersionRevision;
    private Integer magentoVersionPatch;
    private String magentoVersionStability = "";
    private Integer magentoVersionNumber = 0;

    public MagentoVersion(String magentoVersion) throws Exception {
        final Pattern pattern = Pattern.compile("([0-9]+).([0-9]+).([0-9]+).([0-9]+)-?([a-zA-Z]+)?([0-9]+)?");
        final Matcher matcher = pattern.matcher(magentoVersion);
        if (matcher.find()) {
            magentoVersionMajor = Integer.parseInt(matcher.group(1));
            magentoVersionMinor = Integer.parseInt(matcher.group(2));
            magentoVersionRevision = Integer.parseInt(matcher.group(3));
            magentoVersionPatch = Integer.parseInt(matcher.group(4));
            if (matcher.group(5) != null) {
                magentoVersionStability = matcher.group(5);
            }
            if (matcher.group(6) != null) {
                magentoVersionNumber = Integer.parseInt(matcher.group(6));
            }
        } else {
            throw new Exception("Could not parse Magento version. Check your pom.xml");
        }
    }

    public int getMajorVersion() {
        return magentoVersionMajor;
    }

    public int getMinorVersion() {
        return magentoVersionMinor;
    }

    public int getRevisionVersion() {
        return magentoVersionRevision;
    }

    public int getPatchVersion() {
        return magentoVersionPatch;
    }

    public String getStabilityVersion() {
        return magentoVersionStability;
    }

    public int getNumberVersion() {
        return magentoVersionNumber;
    }

    public String toString() {
        String v = magentoVersionMajor + "." + magentoVersionMinor + "."
                + magentoVersionRevision + "." + magentoVersionPatch;
        if (!magentoVersionStability.equals("")) {
            v += "-" + magentoVersionStability;
        }
        if (magentoVersionNumber != 0) {
            v += magentoVersionNumber;
        }
        return v;
    }

}
