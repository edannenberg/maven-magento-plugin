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

package de.bbe_consulting.mavento.helper.visitor;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.TERMINATE;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.CRC32;

/**
 * File visitor for creating a jar file.
 * 
 * @author Erik Dannenberg
 */
public class CreateJarVisitor extends SimpleFileVisitor<Path> {

    private Path sourcePath;
    private JarOutputStream jarOutputStream;
    private CRC32 checkSum;
    private byte[] buffer;
    private boolean includeEmptyDirs = true;

    public CreateJarVisitor(Path source, JarOutputStream jarOutputStream, Boolean includeEmpty) 
            throws IOException {

        this.sourcePath = source;
        this.jarOutputStream = jarOutputStream;
        this.buffer = new byte[1024];
        this.checkSum = new CRC32();
        this.includeEmptyDirs = includeEmpty;
    }

    public CreateJarVisitor(Path source, JarOutputStream jarOutputStream)
            throws IOException {

        this.sourcePath = source;
        this.jarOutputStream = jarOutputStream;
        this.buffer = new byte[1024];
        this.checkSum = new CRC32();
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

        int bytesRead;
        try {
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file.toFile()));
            checkSum.reset();
            while ((bytesRead = bis.read(buffer)) != -1) {
                checkSum.update(buffer, 0, bytesRead);
            }
            bis.close();
            // reset to beginning of input stream
            bis = new BufferedInputStream(new FileInputStream(file.toFile()));
            final JarEntry entry = new JarEntry(sourcePath.relativize(file).toString());
            entry.setMethod(JarEntry.STORED);
            entry.setCompressedSize(Files.size(file));
            entry.setSize(Files.size(file));
            entry.setCrc(checkSum.getValue());
            jarOutputStream.putNextEntry(entry);
            while ((bytesRead = bis.read(buffer)) != -1) {
                jarOutputStream.write(buffer, 0, bytesRead);
            }
            bis.close();
        } catch (FileNotFoundException e) {
            return FileVisitResult.TERMINATE;
        }
        return CONTINUE;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            if (!stream.iterator().hasNext() && includeEmptyDirs) {
                // add / to make it a dir entry
                final JarEntry entry = new JarEntry(sourcePath.relativize(dir).toString() + "/");
                jarOutputStream.putNextEntry(entry);
            }
        } catch (IOException e) {
            return TERMINATE;
        }
        return CONTINUE;
    }

}
