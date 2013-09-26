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

import java.util.Comparator;

/**
 * Comperator implementation for MagentoModule type.
 * 
 * @author Erik Dannenberg
 * @see MagentoModule
 */
public class MagentoModuleComperator implements Comparator<MagentoModule> {

    @Override
    public int compare(MagentoModule m1, MagentoModule m2) {
        String m1Name = m1.getNamespace() + "_" + m1.getName();
        String m2Name = m2.getNamespace() + "_" + m2.getName();
        return m1Name.compareTo(m2Name);
    }

}
