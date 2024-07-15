/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.setup;

import com.github.zafarkhaja.semver.Version;
import inetsoft.storage.*;
import inetsoft.util.*;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * {@code StorageService} allows data space files to be accessed directly in a configured storage.
 * The {@link #close()} method must be called after instances are no longer needed.
 */
public class StorageService extends AbstractStorageService {
   public StorageService(String directory) {
      super(directory);
      keyValueEngine = createKeyValueEngine();

      try {
         blobEngine = createBlobEngine();
      }
      catch(RuntimeException | Error e) {
         try {
            keyValueEngine.close();
         }
         catch(Exception ignore) {
         }

         throw e;
      }
   }

   /**
    * Determines if a file or directory exists in the data space at the specified path.
    *
    * @param path the path, relative to the data space root, of the file or directory.
    *
    * @return {@code true} if it exists or {@code false} if it does not.
    */
   public boolean exists(String path) {
      String cleanPath = path.replace('\\', '/');
      return keyValueEngine.contains("dataSpace", cleanPath);
   }

   /**
    * Creates a directory in the data space at the specified path.
    *
    * @param path the path, relative to the data space root, to the directory.
    */
   public void createDirectory(String path) {
      String cleanPath = path.replace('\\', '/');
      DataSpace.Metadata metadata = new DataSpace.Metadata();
      Blob<DataSpace.Metadata> blob = new Blob<>(
         cleanPath, null, 0L, Instant.now(), metadata);
      keyValueEngine.put("dataSpace", cleanPath, blob);
   }

   /**
    * Writes a file to the data space.
    *
    * @param path the path, relative to the data space root, where the file will be written.
    * @param file the file to write.
    *
    * @throws IOException if an I/O error occurs.
    */
   public void write(String path, File file) throws IOException {
      String digest = digest(file);
      String cleanPath = path.replace('\\', '/');

      blobEngine.write("dataSpace", digest, file.toPath());

      DataSpace.Metadata metadata = new DataSpace.Metadata();
      Blob<DataSpace.Metadata> blob = new Blob<>(
         cleanPath, digest, file.length(), Instant.ofEpochMilli(file.lastModified()),
         metadata);
      keyValueEngine.put("dataSpace", cleanPath, blob);
   }

   /**
    * Reads a file from the data space. Callers <b>must</b> close the input stream when they are
    * done with it.
    *
    * @param path the path, relative to the data space root, of the file.
    *
    * @return an input stream from which the file contents can be read.
    *
    * @throws IOException if an I/O error occurs.
    */
   public InputStream read(String path) throws IOException {
      String cleanPath = path.replace('\\', '/');
      Blob<DataSpace.Metadata> blob = keyValueEngine.get("dataSpace", cleanPath);
      Path file = Files.createTempFile("storage", ".dat");
      blobEngine.read("dataSpace", blob.getDigest(), file);
      return new TempFileInputStream(file);
   }

   /**
    * Reads an asset from the indexed storage. Callers <b>must</b> close the input stream when they
    * are done with it.
    *
    * This method is intended for internal use only.
    *
    * @param assetId the identifier of the asset to read.
    *
    * @return an input stream from which the asset contents can be read.
    *
    * @throws IOException if an I/O error occurs.
    */
   public InputStream readAsset(String assetId) throws IOException {
      Blob<BlobIndexedStorage.Metadata> blob = keyValueEngine.get("indexedStorage", assetId);
      Path file = Files.createTempFile("storage", ".dat");
      blobEngine.read("indexedStorage", blob.getDigest(), file);
      return new TempFileInputStream(file);
   }

   /**
    * Replaces an asset in the indexed storage. The asset must already exist in the indexed
    * storage.
    *
    * This method is intended for internal use only. Use of this method can corrupt your assets
    * and make them unusable.
    *
    * @param assetId the identifier of the asset to replace.
    * @param file    the file containing the new asset data.
    *
    * @throws IOException if an I/O error occurs.
    */
   public void replaceAsset(String assetId, File file) throws IOException {
      String digest = digest(file);
      blobEngine.write("indexedStorage", digest, file.toPath());

      Blob<BlobIndexedStorage.Metadata> blob = keyValueEngine.get("indexedStorage", assetId);
      BlobIndexedStorage.Metadata metadata = blob.getMetadata();
      blob = new Blob<>(
         assetId, digest, file.length(), Instant.ofEpochMilli(file.lastModified()), metadata);
      keyValueEngine.put("indexedStorage", assetId, blob);
   }

   /**
    * Dumps the contents of the entire storage (not just the data space) to a ZIP file.
    *
    * @param file the ZIP file that will contain the backup.
    *
    * @throws IOException if an I/O error occurs.
    */
   public void backup(File file) throws IOException {
      try(OutputStream output = Files.newOutputStream(file.toPath())) {
         new DirectStorageTransfer(keyValueEngine, blobEngine).exportContents(output);
      }
   }

   /**
    * Loads the contents of a backup ZIP file into storage.
    *
    * @param file a ZIP file created by {@link #backup(File)}.
    *
    * @throws IOException if an I/O error occurs.
    */
   public void restore(File file) throws IOException {
      new DirectStorageTransfer(keyValueEngine, blobEngine).importContents(file.toPath());
   }

   /**
    * Installs or upgrades a plugin into storage.
    *
    * @param file the plugin ZIP file to install.
    *
    * @throws IOException if an I/O error occurs.
    */
   public void installPlugin(File file) throws IOException {
      Plugin.Descriptor descriptor = new Plugin.Descriptor(file);
      String message = "Installed plugin ";

      if(keyValueEngine.contains("plugins", descriptor.getId())) {
         Blob<Plugin.Descriptor> blob = keyValueEngine.get("plugins", descriptor.getId());
         Version newVersion = Version.parse(descriptor.getVersion());
         Version oldVersion = Version.parse(blob.getMetadata().getVersion());

         if(newVersion.isLowerThanOrEquivalentTo(oldVersion)) {
            System.out.println(
               "Plugin is up to date " + descriptor.getId() + ":" + descriptor.getVersion());
            return;
         }

         try {
            uninstallPlugin(descriptor.getId());
         }
         catch(FileNotFoundException ignore) {
         }

         message = "Upgraded plugin ";
      }

      String digest = digest(file);
      blobEngine.write("plugins", digest, file.toPath());

      Blob<Plugin.Descriptor> blob =
         new Blob<>(descriptor.getId(), digest, file.length(), Instant.now(), descriptor);
      keyValueEngine.put("plugins", descriptor.getId(), blob);

      System.out.println(message + descriptor.getId() + ":" + descriptor.getVersion());
   }

   /**
    * Uninstalls a plugin from storage.
    *
    * @param pluginId the plugin identifier.
    *
    * @throws IOException if an I/O error occurs.
    */
   public void uninstallPlugin(String pluginId) throws IOException {
      keyValueEngine.remove("plugins", pluginId);
      blobEngine.delete("plugins", pluginId);
   }

   @Override
   public void close() throws Exception {
      keyValueEngine.close();
      blobEngine.close();
   }

   private String digest(File file) throws IOException {
      try(InputStream input = Files.newInputStream(file.toPath())) {
         return DigestUtils.md5Hex(input);
      }
   }

   private final KeyValueEngine keyValueEngine;
   private final BlobEngine blobEngine;

   private static final class TempFileInputStream extends FilterInputStream {
      TempFileInputStream(Path file) throws IOException {
         super(Files.newInputStream(file));
         this.file = file;
      }

      @Override
      public void close() throws IOException {
         try {
            Files.delete(file);
         }
         catch(Exception ignore) {
         }

         super.close();
      }

      private final Path file;
   }
}
