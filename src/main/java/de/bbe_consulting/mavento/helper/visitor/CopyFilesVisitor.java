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
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        // before visiting entries in a directory we copy the directory
        // (okay if directory already exists).
        CopyOption[] options = (preserve) ?
            new CopyOption[] { StandardCopyOption.COPY_ATTRIBUTES } : new CopyOption[0];

        Path newdir = target.resolve(source.relativize(dir));
        try {
            Files.copy(dir, newdir, options);
        } catch (FileAlreadyExistsException x) {
            // ignore
        } catch (IOException x) {
            System.err.format("Unable to create: %s: %s%n", newdir, x);
            return FileVisitResult.SKIP_SUBTREE;
        }
        return CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        copyFile(file, target.resolve(source.relativize(file)), preserve);
        return CONTINUE;
    }
    
    static void copyFile(Path source, Path target, boolean preserve) {
        CopyOption[] options = (preserve) ?
            new CopyOption[] { StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING } :
            new CopyOption[] { StandardCopyOption.REPLACE_EXISTING };
        try {
            Files.copy(source, target, options);
        } catch (IOException x) {
            System.err.format("Unable to copy: %s: %s%n", source, x);
        }
    }
    
}