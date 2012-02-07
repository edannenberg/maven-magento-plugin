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

import java.util.Comparator;

public class MagentoModuleComperator implements Comparator<MagentoModule> {

	@Override
	public int compare(MagentoModule o1, MagentoModule o2) {
		String o1Name = o1.getNamespace()+"_"+o1.getName();
		String o2Name = o2.getNamespace()+"_"+o2.getName();
		return o1Name.compareTo(o2Name);
	}

}
