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

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import org.apache.commons.lang.mutable.MutableLong;

/**
 * FileVisitor to count directory size in bytes in a mutable long, passed via constructor.
 * 
 * @author Erik Dannenberg
 */
public class FileSizeVisitor extends SimpleFileVisitor<Path> {
    
    private MutableLong sizeTotal;

    public FileSizeVisitor(MutableLong sizeTotal) 
            throws IOException {

        this.sizeTotal = sizeTotal;
    }
    
    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

        if (attrs.isRegularFile()) {
            sizeTotal.add(attrs.size());
        }
        return FileVisitResult.CONTINUE;
    }
}
