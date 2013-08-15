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

package de.bbe_consulting.mavento.helper.visitor;

import static java.nio.file.FileVisitResult.CONTINUE;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * File visitor for copying files recursive.
 * 
 * @author Erik Dannenberg
 */
public class CopyFilesVisitor extends SimpleFileVisitor<Path> {

    private final Path source;
    private final Path target;
    private final boolean preserve;

    public CopyFilesVisitor(Path source, Path target, boolean preserve) {

        this.source = source;
        this.target = target;
        this.preserve = preserve;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {

        final Path newdir = target.resolve(source.relativize(dir));
        try {
            Files.createDirectories(newdir);
        } catch (FileAlreadyExistsException e) {
            // ignore
        }
        return CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

        copyFile(file, target.resolve(source.relativize(file)), preserve);
        return CONTINUE;
    }
    
    /**
     * Copy a file to target path.
     * 
     * @param source
     * @param target
     * @param preserve preserve file attributes?
     */
    private static void copyFile(Path source, Path target, boolean preserve) throws IOException {

        CopyOption[] options = null;
        if (preserve) {
            options = new CopyOption[] { StandardCopyOption.COPY_ATTRIBUTES,
                    StandardCopyOption.REPLACE_EXISTING };
        } else {
            options = new CopyOption[] { StandardCopyOption.REPLACE_EXISTING };
        }
        Files.copy(source, target, options);
    }

}