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

package de.bbe_consulting.mavento.helper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import de.bbe_consulting.mavento.helper.visitor.CopyFilesVisitor;
import de.bbe_consulting.mavento.helper.visitor.DeleteFilesVisitor;
import de.bbe_consulting.mavento.helper.visitor.CreateJarVisitor;

/**
 * Static file helpers.
 * 
 * @author Erik Dannenberg
 */
public final class FileUtil {

    /**
     * Private constructor, only static methods in this util class 
     */
    private FileUtil() {
    }

    /**
     * Create a jar file.
     * 
     * @param fileName
     * @param sourcePath
     * @throws MojoExecutionException
     */
    public static void createJar(String fileName, String sourcePath)
            throws MojoExecutionException {

        final OutputStream outputStream;
        FileChannel channel = null;
        try {
            channel = new RandomAccessFile(Paths.get(fileName).toFile(), "rw").getChannel();
            outputStream = Channels.newOutputStream(channel);
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException("Error: " + e.getMessage(), e);
        }
        try (JarOutputStream jarOutputStream = new JarOutputStream(outputStream)) {
            CreateJarVisitor jv = new CreateJarVisitor(Paths.get(sourcePath), jarOutputStream);
            Files.walkFileTree(Paths.get(sourcePath), jv);
        } catch (IOException e) {
            throw new MojoExecutionException("Error: " + e.getMessage(), e);
        } finally {
            try {
                channel.close();
            } catch (IOException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }
    }

    /**
     * Unzips a zip.
     * 
     * @param fileName
     * @param targetDirName
     * @throws IOException
     */
    public static void unzipFile(String fileName, String targetPath)
            throws IOException {

        final File targetDir = new File(targetPath);
        final ZipFile sourceZip = new ZipFile(fileName);

        @SuppressWarnings("unchecked")
        final Enumeration<ZipEntry> entries = (Enumeration<ZipEntry>) sourceZip.entries();

        while (entries.hasMoreElements()) {
            ZipEntry currentEntry = entries.nextElement();
            File targetFile = new File(targetDir, currentEntry.getName());
            // create sub directories if needed
            targetFile.getParentFile().mkdirs();
            // write file if it's not a directory
            if (!currentEntry.isDirectory()) {
                writeFileFromZip(targetFile, currentEntry, sourceZip);
            }
        }
    }

    // helper class for unzipFile()
    private static void writeFileFromZip(File targetFile, ZipEntry zipEntry, ZipFile archive) 
             throws IOException {

        InputStream input = null;
        OutputStream output = null;
        FileChannel channel = null;
        try {
            final InputStream rawIn = archive.getInputStream(zipEntry);
            input = new BufferedInputStream(rawIn);
            
            channel = new RandomAccessFile(targetFile, "rw").getChannel();
            final OutputStream rawOut = Channels.newOutputStream(channel);
            output = new BufferedOutputStream(rawOut);

            // pump data from zip file into new files
            final byte[] buf = new byte[2048];
            int len;
            while ((len = input.read(buf)) > 0) {
                output.write(buf, 0, len);
            }
        } finally {
            if (input != null) {
                input.close();
            }
            if (output != null) {
                output.close();
            }
            if (channel != null) {
                channel.close();
            }
        }
    }

    /**
     * Mass rename files/folders.
     * 
     * @param fileNameMap map with source/target file
     * @throws IOException
     */
    public static void renameFiles(Map<String, String> fileNameMap)
            throws IOException {

        for (Map.Entry<String, String> fileNames : fileNameMap.entrySet()) {
            final Path oldFile = Paths.get(fileNames.getKey());
            final Path newFile = Paths.get(fileNames.getValue());
            if (Files.exists(oldFile)) {
                Files.move(oldFile, newFile);
            }
        }
    }

    /**
     * Mass symlink files, wraps symlinkFile.
     * 
     * @param fileNameMap map with source/target link
     * @param logger
     * @throws MojoExecutionException
     * @throws IOException
     */
    public static void symlinkFiles(Map<String, String> fileNameMap, Log logger)
            throws MojoExecutionException, IOException {

        for (Map.Entry<String, String> fileNames : fileNameMap.entrySet()) {
            logger.info("..linking: " + fileNames.getKey());
            symlinkFile(fileNames.getKey(), fileNames.getValue(), logger);
        }
    }

    /**
     * Symlink a file.
     * 
     * @param srcFileName
     * @param targetFileName
     * @param logger
     * @throws MojoExecutionException
     * @throws IOException
     */
    public static void symlinkFile(String srcFileName, String targetFileName, Log logger) 
            throws MojoExecutionException, IOException {

        deleteFile(targetFileName, logger);

        final Path t = Paths.get(targetFileName);
        final Path targetBaseDir = Paths.get(t.getParent().toString());
        // create parent folders if needed
        if (Files.notExists(targetBaseDir)) {
            Files.createDirectories(targetBaseDir);
        }

        final Path linkSource = Paths.get(srcFileName);
        final Path linkTarget = Paths.get(targetFileName);

        try {
            Files.createSymbolicLink(linkTarget, linkSource);
        } catch (IOException e) {
            throw new MojoExecutionException("Error while linking "
                    + srcFileName + " Exception: " + e.getMessage(), e);
        } catch (UnsupportedOperationException e) {
            throw new MojoExecutionException("Looks like your filesystem does not support symlinks. :(", e);
        }
    }

    /**
     * Delete file/directory. Does not follow symlinks.
     * 
     * @param fileName
     * @param logger
     * @throws MojoExecutionException
     * @throws IOException
     */
    public static void deleteFile(String fileName, Log logger)
            throws MojoExecutionException, IOException {

        final Path t = Paths.get(fileName);

        if (Files.isDirectory(t, LinkOption.NOFOLLOW_LINKS)) {
            final DeleteFilesVisitor v = new DeleteFilesVisitor();
            Files.walkFileTree(t, v);
        } else {
            Files.deleteIfExists(t);
        }
    }

    /**
     * Copy a file or directory to target.
     * 
     * @param sourceFile
     * @param targetFile
     * @throws MojoExecutionException
     */
    public static void copyFile(Path sourceFile, Path targetFile)
            throws MojoExecutionException {

        // copy module source to magento instance so the autoloader can pick it up
        if (Files.exists(sourceFile)) {
            final CopyFilesVisitor crv = new CopyFilesVisitor(sourceFile, targetFile, true);
            try {
                Files.walkFileTree(sourceFile, crv);
            } catch (IOException e) {
                throw new MojoExecutionException("Error copying file(s) to: " + targetFile + " " +
                                e.getMessage(), e);
            }
        }
    }

    /**
     * Read directory content.
     * 
     * @param basePath
     * @return List<Path> with directory entries.
     * @throws IOException
     */
    public static List<Path> getDirectoryList(Path basePath) throws IOException {
        
        final List<Path> directoryNames = new ArrayList<Path>();
        if (Files.exists(basePath) && Files.isDirectory(basePath)) {
            DirectoryStream<Path> files = null;
            try {
                files = Files.newDirectoryStream(basePath);
                for (Path path : files) {
                    if (Files.isDirectory(path)) {
                        directoryNames.add(path.toAbsolutePath());
                    }
                }
            } finally {
                files.close();
            }
        }
        return directoryNames;
    }

    /**
     * Dump file contents to console.
     * 
     * @param filePath
     * @param logger
     * @throws IOException
     */
    public static void logFileContents(String filePath, Log logger)
            throws IOException {

        final Reader reader;
        FileChannel channel = null;
        try {
            channel = new RandomAccessFile(Paths.get(filePath).toFile(), "r").getChannel();
            reader = Channels.newReader(channel, "utf-8");
            final BufferedReader input = new BufferedReader(reader);
            String line;

            while ((line = input.readLine()) != null) {
                System.out.println(line);   
                // logger.info(line);
            }
        } finally {
            if (channel != null) {
                channel.close();
            }
        }
    }

}
