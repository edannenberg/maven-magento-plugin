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
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import de.bbe_consulting.mavento.helper.visitor.DeleteFilesVisitor;
import de.bbe_consulting.mavento.helper.visitor.CreateJarVisitor;

/**
 * File Util Class
 * 
 * @author Erik Dannenberg
 */
public final class FileUtil {
	
	// Private constructor, only static methods in this help class
	private FileUtil() {
	}
	
	/** Create a jar file
	 * @param fileName
	 * @param sourcePath
	 */
	public static void createJar(String fileName, String sourcePath) throws MojoExecutionException  {
		FileOutputStream outputStream = null;
		try {
			outputStream = new FileOutputStream(fileName);
		} catch (FileNotFoundException e) {
			throw new MojoExecutionException("Error: "+e.getMessage(), e);
		}
		try (JarOutputStream jarOutputStream = new JarOutputStream(outputStream)) {
			CreateJarVisitor jv = new CreateJarVisitor(Paths.get(sourcePath), jarOutputStream);
			Files.walkFileTree(Paths.get(sourcePath), jv);
		} catch (IOException e) {
			throw new MojoExecutionException("Error: "+e.getMessage(), e);
		}
	}
	
	/** Unzips a zip
	 * @param String fileName
	 * @param String targetDirName
	 */
	public static void unzipFile(String fileName, String targetPath)	throws IOException {

		File targetDir = new File(targetPath);
		ZipFile sourceZip = new ZipFile(fileName);

		@SuppressWarnings("unchecked")
		Enumeration<ZipEntry> entries = (Enumeration<ZipEntry>) sourceZip.entries();

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
	
	// extract file from within a zip
	private static void writeFileFromZip(File targetFile, ZipEntry zipEntry, ZipFile archive) throws IOException {
		InputStream input = null;
		OutputStream output = null;
		try {
			InputStream rawIn = archive.getInputStream(zipEntry);
			input = new BufferedInputStream(rawIn);
						
			FileOutputStream rawOut = new FileOutputStream(targetFile);
			output = new BufferedOutputStream(rawOut);
						
			// pump data from zip file into new files
			byte[] buf = new byte[2048];
			int len;
			while ((len = input.read(buf)) > 0) {
				output.write(buf, 0, len);
			}
		}
		finally {
			if(input != null) {
				input.close();
			}
			if(output != null) {
				output.close();
			}
		}
	}
	
	/**
	 * Mass rename files/folders
	 * @param LinkedHashMap<String fileName,String newFileName>
	 */
	public static void renameFiles(Map<String,String> fileNameMap) throws IOException {
		for ( Map.Entry<String,String> fileNames : fileNameMap.entrySet()) {
			Path oldFile = Paths.get(fileNames.getKey());
			Path newFile = Paths.get(fileNames.getValue());
			if (Files.exists(oldFile)) {
				Files.move(oldFile, newFile);
			}
		}
	}
	
	/** Mass symlink files, wraps symlinkFile
	 * @param LinkedHashMap<String,String>
	 */
	public static void symlinkFiles(Map<String,String> fileNameMap, Log logger) throws MojoExecutionException, IOException {
		for ( Map.Entry<String,String> fileNames : fileNameMap.entrySet()) {
			logger.info("linking: "+fileNames.getKey());
			symlinkFile(fileNames.getKey(), fileNames.getValue(), logger);
		}
	}
	
	/** Symlink a file
	 * @param String srcFileName
	 * @param String targetFileName
	 * @param Log logger
	 */
	public static void symlinkFile(String srcFileName, String targetFileName, Log logger) throws MojoExecutionException, IOException {

		deleteFile(targetFileName, logger);
		
		Path t = Paths.get(targetFileName);
		Path targetBaseDir = Paths.get(t.getParent().toString());
		// create parent folders if needed
		if (Files.notExists(targetBaseDir)) {
			Files.createDirectories(targetBaseDir);
		}
		
		Path linkSource = Paths.get(srcFileName);
		Path linkTarget = Paths.get(targetFileName);
		
		try {
		    Files.createSymbolicLink(linkTarget, linkSource);
		} catch (IOException e) {
			throw new MojoExecutionException("Error while linking "+srcFileName+" Exception: "+e.getMessage(), e);
		} catch (UnsupportedOperationException e) {
			throw new MojoExecutionException("Looks like your filesystem does not support symlinks. :(", e);
		}

	}
	
	public static void deleteFile(String fileName, Log logger) throws MojoExecutionException, IOException {

		Path t = Paths.get(fileName);
		
		if (Files.isDirectory(t, LinkOption.NOFOLLOW_LINKS)) {
			DeleteFilesVisitor v = new DeleteFilesVisitor();
			Files.walkFileTree(t, v);
		} else {
			Files.deleteIfExists(t);
		}
		
	}
	
	public static List<Path> getDirectoryList( Path basePath ) throws IOException {
		List<Path> directoryNames = new ArrayList<Path>();
		if ( Files.exists(basePath) && Files.isDirectory(basePath) ) {
			DirectoryStream<Path> files = null;
			try {
				files = Files.newDirectoryStream( basePath );
				for ( Path path : files ) {
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
	
	public static void logFileContents( String filePath, Log logger ) throws IOException {
		
		FileReader reader = null;
		try {
			File fp = new File( filePath );
			reader = new FileReader( fp );
			BufferedReader input = new BufferedReader( reader );
			String line;
	
			while( (line = input.readLine()) != null )
			{
				System.out.println( line );
				//logger.info(line);
			}
		} finally {
			if (reader != null) {
				reader.close();
			}
		}
	}
	
}
