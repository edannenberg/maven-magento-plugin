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
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;

import de.bbe_consulting.mavento.helper.FileUtil;

/**
 * File visitor for copying files recursive.
 * 
 * @author Erik Dannenberg
 */
public class CopyFilesVisitor extends SimpleFileVisitor<Path> {

    private final Path sourcePath;
    private final Path targetPath;
    private final boolean preserveAttrs;
    
    private final UserPrincipal targetUser;
    private final GroupPrincipal targetGroup;
    private final Set<PosixFilePermission> targetFilePermissions;
    private final Set<PosixFilePermission> targetDirPermissions;

    public CopyFilesVisitor(Path source, Path target, boolean preserve) {

        this.sourcePath = source;
        this.targetPath = target;
        this.preserveAttrs = preserve;
        this.targetUser = this.targetGroup = null;
        this.targetFilePermissions = null;
        this.targetDirPermissions = null;
    }

    public CopyFilesVisitor(Path source, Path target, String octalFilePerms, String octalDirPerms) throws MojoExecutionException, IOException {

        this.sourcePath = source;
        this.targetPath = target;
        this.preserveAttrs = false;
        this.targetUser = null;
        this.targetGroup = null;
        if (octalFilePerms != null) {
            this.targetFilePermissions = PosixFilePermissions.fromString(FileUtil.octalPermissionsToSymbolic(octalFilePerms));
        } else {
            this.targetFilePermissions = null;
        }
        if (octalDirPerms != null) {
            this.targetDirPermissions = PosixFilePermissions.fromString(FileUtil.octalPermissionsToSymbolic(octalDirPerms));
        } else {
            this.targetDirPermissions = null;
        }
    }

    public CopyFilesVisitor(Path source, Path target, String octalFilePerms, String octalDirPerms, String newUser, String newGroup) throws MojoExecutionException, IOException {

        this.sourcePath = source;
        this.targetPath = target;
        this.preserveAttrs = false;
        if (newUser != null) {
            this.targetUser = target.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName(newUser);
        } else {
            this.targetUser = null;
        }
        if (newGroup != null) {
            this.targetGroup = target.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByGroupName(newGroup);
        } else {
            this.targetGroup = null;
        }
        if (octalFilePerms != null) {
            this.targetFilePermissions = PosixFilePermissions.fromString(FileUtil.octalPermissionsToSymbolic(octalFilePerms));
        } else {
            this.targetFilePermissions = null;
        }
        if (octalDirPerms != null) {
            this.targetDirPermissions = PosixFilePermissions.fromString(FileUtil.octalPermissionsToSymbolic(octalDirPerms));
        } else {
            this.targetDirPermissions = null;
        }
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {

        final Path targetDir = targetPath.resolve(sourcePath.relativize(dir));
        try {
            Files.createDirectories(targetDir);
        } catch (FileAlreadyExistsException e) {
            // ignore
        }
        setUserAndGroup(targetDir);
        if (targetDirPermissions != null) {
            Files.setPosixFilePermissions(targetDir, targetDirPermissions);
        }
        return CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

        Path targetFile = targetPath.resolve(sourcePath.relativize(file));
        copyFile(file, targetFile);
        setUserAndGroup(targetFile);
        if (targetFilePermissions != null) {
            Files.setPosixFilePermissions(targetFile, targetFilePermissions);
        }
        return CONTINUE;
    }
    
    /**
     * Copy a file to target path.
     * 
     * @param source
     * @param target
     * @param preserveAttrs preserve file attributes?
     */
    private void copyFile(Path source, Path target) throws IOException {

        CopyOption[] options = null;
        if (this.preserveAttrs) {
            options = new CopyOption[] { StandardCopyOption.COPY_ATTRIBUTES,
                    StandardCopyOption.REPLACE_EXISTING };
        } else {
            options = new CopyOption[] { StandardCopyOption.REPLACE_EXISTING };
        }
        Files.copy(source, target, options);
    }

    /**
     * Set user and/or group of file.
     * 
     * @param file
     * @throws IOException
     */
    private void setUserAndGroup(Path file) throws IOException {
        if (targetUser != null) {
            Files.setOwner(file, targetUser);
        }
        if (targetGroup != null) {
            Files.getFileAttributeView(file, PosixFileAttributeView.class).setGroup(targetGroup);
        }
    }

}