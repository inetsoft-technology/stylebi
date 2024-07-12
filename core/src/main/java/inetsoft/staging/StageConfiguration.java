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
package inetsoft.staging;

import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ServiceLoader;
import java.util.stream.Stream;

/**
 * {@code StageConfiguration} handles staging an external configuration source to a local directory.
 */
public final class StageConfiguration {
   /**
    * Stages an external configuration.
    *
    * @param options the options that describe the external configuration source.
    *
    * @throws Exception if the configuration could not be staged.
    */
   public void stage(StagingOptions options) throws Exception {
      if(options.url() == null || options.url().isEmpty()) {
         return;
      }

      Path stagingDirectory = options.stagingDirectory();
      Files.createDirectories(stagingDirectory);
      Path downloadDirectory = options.path()
         .filter(p -> !p.isEmpty())
         .map(p -> createDownloadDirectory())
         .orElseGet(() -> {
            // check if staging dir is empty
            try(Stream<Path> entries = Files.list(stagingDirectory)) {
               if(entries.findFirst().isPresent()) {
                  return createDownloadDirectory();
               }
               else  {
                  return stagingDirectory;
               }
            }
            catch(IOException ignore) {
            }

            return stagingDirectory;
         });

      StagingProvider provider = null;

      for(StagingProvider sp : ServiceLoader.load(StagingProvider.class)) {
         if(sp.isUrlSupported(options.url())) {
            provider = sp;
            break;
         }
      }

      if(provider == null) {
         throw new IllegalArgumentException("Unsupported URL: " + options.url());
      }

      StagingOptions tempOptions = StagingOptions.builder()
         .from(options)
         .stagingDirectory(downloadDirectory)
         .build();
      provider.stage(tempOptions);

      options.path().filter(p -> !p.isEmpty()).ifPresent(
         path -> copyDownloadedFiles(stagingDirectory, downloadDirectory.resolve(path)));
   }

   private Path createDownloadDirectory() {
      try {
         return Files.createTempDirectory("inetsoft");
      }
      catch(IOException e) {
         throw new RuntimeException("Failed to create temporary directory for download", e);
      }
   }

   private void copyDownloadedFiles(Path stagingDirectory, Path downloadDirectory) {
      try {
         try(Stream<Path> paths = Files.walk(downloadDirectory)) {
            paths.forEach(source -> copyDownloadedFiles(source, stagingDirectory, downloadDirectory));
         }

         try(Stream<Path> paths = Files.walk(downloadDirectory)) {
            paths
               .sorted(Comparator.reverseOrder())
               .forEach(this::deleteDownloadedFile);
         }
      }
      catch(IOException e) {
         throw new RuntimeException(
            "Failed to copy files from " + downloadDirectory + " to " + stagingDirectory, e);
      }
   }

   private void copyDownloadedFiles(Path source, Path stagingDirectory,
                                           Path downloadDirectory)
   {
      Path target = stagingDirectory.resolve(downloadDirectory.relativize(source));

      try {
         if(source.toFile().isFile()) {
            FileUtils.copyFile(source.toFile(), target.toFile());
         }
         else {
            FileUtils.copyDirectory(source.toFile(), target.toFile());
         }
      }
      catch(IOException e) {
         throw new UncheckedIOException(e);
      }
   }

   private void deleteDownloadedFile(Path file) {
      try {
         Files.delete(file);
      }
      catch(IOException e) {
         throw new UncheckedIOException(e);
      }
   }
}
