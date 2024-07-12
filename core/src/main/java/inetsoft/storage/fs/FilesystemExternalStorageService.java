/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.storage.fs;

import inetsoft.storage.ExternalStorageService;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class FilesystemExternalStorageService implements ExternalStorageService {
   public FilesystemExternalStorageService(Path base) {
      this.base = base;
   }

   @Override
   public void write(String path, Path file) throws IOException {
      Path targetPath = Paths.get(path);

      if(targetPath.isAbsolute()) {
         targetPath = targetPath.getRoot().relativize(targetPath);
      }

      Path target = base.resolve(targetPath).toAbsolutePath();
      Files.createDirectories(target.getParent());
      Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
   }

   @Override
   public String getAvailableFile(String path, int start) {
      Path filePath = base.resolve(path).toAbsolutePath();

      if(!filePath.toFile().exists()) {
         return path;
      }

      String parent = filePath.getParent().toString();
      String fileName = filePath.getFileName().toString();
      String fileExtension = fileName.substring(fileName.lastIndexOf('.'));
      fileName = fileName.substring(0, fileName.lastIndexOf('.'));

      for(int counter = start; filePath.toFile().exists(); counter++) {
         filePath = Paths.get(parent, fileName + "(" + counter + ")" + fileExtension);
      }

      return base.relativize(filePath).toString();
   }

   @Override
   public List<String> listFiles(String directory) {
      Path dirPath = base.resolve(directory).toAbsolutePath();

      if(!dirPath.toFile().exists()) {
         return Collections.emptyList();
      }

      File[] files = dirPath.toFile().listFiles();

      if(files == null) {
         return Collections.emptyList();
      }

      return Arrays.stream(files)
         .filter(File::isFile)
         .map(File::getName)
         .collect(Collectors.toList());
   }

   @Override
   public void delete(String path) throws IOException {
      Path filePath = base.resolve(path).toAbsolutePath();

      if(filePath.toFile().isFile()) {
         Files.delete(filePath);
      }
   }

   private final Path base;
}
