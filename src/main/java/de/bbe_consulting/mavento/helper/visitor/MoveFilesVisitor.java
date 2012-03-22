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
import static java.nio.file.FileVisitResult.TERMINATE;

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
 * File visitor to moves files recursive.
 * 
 * @author Erik Dannenberg
 */
public class MoveFilesVisitor extends SimpleFileVisitor<Path> {

    private final Path source;
    private final Path target;

    public MoveFilesVisitor(Path source, Path target) {

        this.source = source;
        this.target = target;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {

        final Path newdir = target.resolve(source.relativize(dir));
        try {
            Files.createDirectories(newdir);
        } catch (FileAlreadyExistsException e) {
            // ignore
        } catch (IOException e) {
            System.err.format("Unable to create: %s: %s%n", newdir, e);
            return FileVisitResult.SKIP_SUBTREE;
        }
        return CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) {

        try {
            Files.delete(dir);
        } catch (IOException e) {
            return TERMINATE;
        }
        return CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {

        try {
            moveFile(file, target.resolve(source.relativize(file)));
        } catch (IOException e) {
            return TERMINATE;
        }
        return CONTINUE;
    }

    /**
     * Move source file to target.
     * 
     * @param source
     * @param target
     * @param preserve preserve file attributes?
     * @throws IOException 
     */
    static void moveFile(Path source, Path target) throws IOException {

        Files.move(source, target, new CopyOption[] { StandardCopyOption.REPLACE_EXISTING });
    }
}
