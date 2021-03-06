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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

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
     * Wrapper for Files.createDirectories() that handles existing symlinks with missing target dir.
     * 
     * @param targetDir
     * @param followSymlink
     * @throws MojoExecutionException
     */
    public static void createDirectories(String targetDir, boolean followSymlink) throws MojoExecutionException {

        final Path f = Paths.get(targetDir);
        if (Files.notExists(f) && !Files.isSymbolicLink(f)) {
            try {
                Files.createDirectories(f);
            } catch (IOException e) {
                throw new MojoExecutionException("Error creating " + f + " : " + e.getMessage(), e);
            }
        } else if (followSymlink && Files.isSymbolicLink(f)) {
            final Path linkTarget;
            try {
                linkTarget = Files.readSymbolicLink(f);
            } catch (IOException e) {
                throw new MojoExecutionException("Error reading link target of " + f + " : " + e.getMessage(), e);
            }
            if (Files.notExists(linkTarget)) {
                try {
                    Files.createDirectories(linkTarget);
                } catch (IOException e) {
                    throw new MojoExecutionException("Error creating " + linkTarget + " : " + e.getMessage(), e);
                }
            }
        }
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
            channel = new FileOutputStream(Paths.get(fileName).toFile()).getChannel();
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

        FileChannel output = null;
        FileOutputStream rawOut = null;
        try {
            final InputStream rawIn = archive.getInputStream(zipEntry);
            rawOut = new FileOutputStream(targetFile);
            output = rawOut.getChannel();
            output.transferFrom(Channels.newChannel(rawIn), 0, zipEntry.getSize());
        } finally {
            if (output != null) {
                output.close();
            }
            if (rawOut != null) {
                rawOut.close();
            }
        }
    }

    /**
     * Writes content to targetFile.
     * 
     * @param List<String> content
     * @param Path targetFile
     * @throws IOException
     */
    public static void writeFile(List<String> content, String targetFile) throws IOException {
        final PrintWriter writer = new PrintWriter(new FileWriter(new File(targetFile)));
        try {
            for (String line : content) {
                writer.println(line);
            }
        } finally {
            writer.close();
        }
    }

    /**
     * Writes string to targetFile. Replaces file if it exists or creates a new one if not.
     * 
     * @param String text
     * @param Path targetFile
     * @throws IOException
     */
    public static void writeFile(String text, Path targetFile) throws IOException {
        if (Files.exists(targetFile)) {
            InputStream istream = new ByteArrayInputStream(text.getBytes());
            Files.copy(istream, targetFile, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Path file = Files.createFile(targetFile);
            Files.write(file, text.getBytes(), StandardOpenOption.WRITE);
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
     * Read file content and return it as a list of strings.
     * 
     * @param filePath
     * @return List<String>
     * @throws IOException
     */
    public static List<String> getFileAsLines(String filePath) throws IOException {
        final List<String> lines = new LinkedList<String>();
        String line = "";
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(filePath));
            while ((line = in.readLine()) != null) {
                lines.add(line);
            }
        } finally {
            if (in != null) {
                in.close();
            }
        }
        return lines;
    }

    /**
     * Read file content with system charset and return it as a string.
     * 
     * @param filePath
     * @return List<String>
     * @throws IOException
     */
    public static String getFileAsString(String sourceFile) throws IOException 
    {
        Charset encoding = Charset.defaultCharset();
        return getFileAsString(sourceFile, encoding);
    }

    /**
     * Read file content and return it as a string. (c) http://stackoverflow.com/users/3474/erickson
     * 
     * @param filePath
     * @return List<String>
     * @throws IOException
     */
    public static String getFileAsString(String sourceFile, Charset encoding) throws IOException 
    {
        byte[] encoded = Files.readAllBytes(Paths.get(sourceFile));
        return encoding.decode(ByteBuffer.wrap(encoded)).toString();
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
        FileInputStream rawIn = null;
        FileChannel channel = null;
        try {
            rawIn = new FileInputStream(Paths.get(filePath).toFile());
            channel = rawIn.getChannel();
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
            if (rawIn != null) {
                rawIn.close();
            }
        }
    }

    /**
     * Converts a octal posix permission string into it's symbolic form.
     * 
     * @param octalPermissions
     * @return String
     * @throws MojoExecutionException
     */
    public static String octalPermissionsToSymbolic (String octalPermissions) throws MojoExecutionException {
        if (octalPermissions.length() != 3) {
            throw new MojoExecutionException("Error: Invalid length of octal permission string: " + octalPermissions);
        }
        char[] permTemplate = "rwxrwxrwx".toCharArray();
        String permsBinary = "";
        for (char c : octalPermissions.toCharArray()) {
            try {
                int i = Integer.parseInt(Character.toString(c), 8);
                permsBinary += String.format("%3s", Integer.toBinaryString(i)).replace(' ', '0');
            } catch (NumberFormatException e) {
                throw new MojoExecutionException("Error parsing file/dir permissions: " + e.getMessage()); 
            }
        }
        for (int i = 0; i < permTemplate.length; i++) {
            if (permsBinary.charAt( i ) == '0') {
                permTemplate[i] = '-';
            }
        }
        return String.valueOf(permTemplate);
    }

}
