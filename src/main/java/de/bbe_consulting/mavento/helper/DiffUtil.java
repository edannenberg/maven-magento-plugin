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

package de.bbe_consulting.mavento.helper;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import de.bbe_consulting.mavento.type.DiffPatch;
import difflib.DiffUtils;
import difflib.Patch;
import difflib.PatchFailedException;

/**
 * Static diff helpers.
 * 
 * @author Erik Dannenberg
 */
public class DiffUtil {

    /**
     * Private constructor, only static methods in this util class 
     */
    private DiffUtil() {
    }

    /**
     * Apply diff style patchFile to targetPath. dryRun will not write any changes to disk.
     * 
     * @param patchFile
     * @param targetPath
     * @param dryRun
     * @param silent
     * @param logger
     * @throws IOException
     * @throws MojoExecutionException
     * @throws PatchFailedException
     */
    public static void patchDirectory (String patchFile, String targetPath, boolean dryRun, boolean silent, Log logger) 
            throws IOException, MojoExecutionException, PatchFailedException {

        final List<DiffPatch> filteredPatchList = splitPatch(FileUtil.getFileAsLines(patchFile), targetPath);
        if (filteredPatchList.isEmpty()) {
            throw new MojoExecutionException("Patch file " + patchFile + " is not a valid diff file!");
        }
        for (DiffPatch patchEntry : filteredPatchList) {
            if (!silent) {
                logger.info(".." + patchEntry.getPatchFileName() + " " + patchEntry.getStatsLine());
            }
            
            Patch patch = DiffUtils.parseUnifiedDiff(patchEntry.getDiffContent());
            @SuppressWarnings("unchecked")
            List<String> result = (List<String>) DiffUtils.patch(FileUtil.getFileAsLines(patchEntry.getTargetFileName()), patch);
            if (!dryRun) {
                FileUtil.writeFile(result, patchEntry.getTargetFileName());
            }
        }
        if (!silent) {
            logger.info("..done.");
        }
    }
    
    /**
     * Splits a diff patch file into single patches.
     * 
     * @param patchContents
     * @return Map<String, List<String>> key: String patchFileName value: List<String> 
     */
    public static List<DiffPatch> splitPatch (List<String> patchContents, String targetDir) {

        final List<DiffPatch> r = new LinkedList<DiffPatch>();
        final Iterator<String> patchContent = patchContents.iterator();
        String currentLine = "";
        String currentPatchFile = "";
        List<String> currentPatchContent = null;
        
        boolean isIndexed = false;
        while (patchContent.hasNext()) {
            currentLine = patchContent.next();
            if (currentLine.startsWith("=====")) {
                // do nothing
            } else if (currentLine.startsWith("Index:") ) {
                isIndexed = true;
                // new patch, put previous patch content into result map if possible
                if (currentPatchFile != "" && currentPatchContent != null) {
                    r.add(new DiffPatch(currentPatchFile, targetDir, currentPatchContent));
                }
                currentPatchFile = DiffUtil.getFileName(currentLine);
                currentPatchContent = new LinkedList<String>();
            } else if (currentLine.startsWith("--- ")) {
                // handle header for single patch files
                if (!isIndexed) {
                    // new patch, put previous patch content into result map if possible
                    if (currentPatchFile != "" && currentPatchContent != null) {
                        r.add(new DiffPatch(currentPatchFile, targetDir, currentPatchContent));
                    }
                    currentPatchFile = currentLine;
                    currentPatchContent = new LinkedList<String>();
                }
                // add line to current patch content
                if (currentPatchContent != null) {
                    currentPatchContent.add(currentLine);
                }
            } else {
                // just add line to current patch content
                if (currentPatchContent != null) {
                    currentPatchContent.add(currentLine);
                }
            }
            
        }
        // add the last patch
        if (currentPatchFile != null && currentPatchContent != null) {
            r.add(new DiffPatch(currentPatchFile, targetDir, currentPatchContent));
        }
        return r;
    }
    
    // extract filename from Index: or --- line
    private static String getFileName (String line) {
        String fileName = "";
        if (line.startsWith("Index: ")) {
            fileName = line.substring(7).trim();
        } else if (line.startsWith("---")) {
            String[] t = line.substring(3).trim().split(" ");
            fileName = t[0];
        }
        return fileName;
    }
}
