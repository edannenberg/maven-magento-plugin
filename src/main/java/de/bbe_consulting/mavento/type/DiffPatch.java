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

import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;

/**
 * Diff style patch
 * 
 * @author Erik Dannenberg
 */
public class DiffPatch {

    private String targetDirectory;
    private String targetFileName = "";
    private String patchFileName;
    private List<String> diffContent;
    private String statsLine = "";
    
    public DiffPatch(String fileName, String targetDir, List<String> patch) {
        targetDirectory = targetDir;
        patchFileName = fileName;
        diffContent = patch;
    }

    public String getTargetDirectory() {
        return targetDirectory;
    }

    public void setTargetDirectory(String targetDirectory) {
        this.targetDirectory = targetDirectory;
    }

    public String getPatchFileName() {
        return patchFileName;
    }

    public void setPatchFileName(String patchFileName) {
        this.patchFileName = patchFileName;
    }

    public List<String> getDiffContent() {
        return diffContent;
    }

    public void setDiffContent(List<String> diffContent) {
        this.diffContent = diffContent;
    }

    public String getTargetFileName() {
        if (targetFileName.isEmpty()) {
            targetFileName = Paths.get(targetDirectory).resolve(Paths.get(patchFileName)).toString();
        }
        return targetFileName;
    }

    public String getStatsLine() {
        if (statsLine.isEmpty()) {
            final Iterator<String> patchContent = diffContent.iterator();
            String currentLine = "";
            int addCount = 0;
            int delCount = 0;
            int blockCount = 0;
            while (patchContent.hasNext()) {
                currentLine = patchContent.next();
                if (currentLine.startsWith("@@")) {
                    ++blockCount;
                } else if (currentLine.startsWith("- ")) {
                    ++delCount;
                } else if (currentLine.startsWith("+ ")) {
                    ++addCount;
                }
            }
            statsLine = "[@" + blockCount + "/+" + addCount + "/-" + delCount + "]";
        }
        return statsLine;
    }
    
}
