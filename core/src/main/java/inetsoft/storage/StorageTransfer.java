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
package inetsoft.storage;

import inetsoft.util.config.InetsoftConfig;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

/**
 * {@code StorageTransfer} handles importing the contents from and importing contents into key-value
 * and blob stores.
 */
public interface StorageTransfer {
   /**
    * Creates a {@code StorageTransfer} appropriate for the configured key-value engine. The mapdb
    * engine is node-local, so its contents are transferred through the cluster; every other engine
    * is shared and is read from and written to directly. Going through the cluster on a non-mapdb
    * backend would fire the live storage change-listeners during a bulk import and fail on
    * not-yet-imported parent folders.
    *
    * <p>For the mapdb backend the provided engines are not used; {@code ClusterStorageTransfer}
    * routes all I/O through the cluster singletons.</p>
    *
    * @param keyValueEngine the key-value storage engine.
    * @param blobEngine     the blob storage engine.
    *
    * @return the storage transfer implementation to use.
    */
   static StorageTransfer create(KeyValueEngine keyValueEngine, BlobEngine blobEngine) {
      boolean mapdb = "mapdb".equals(InetsoftConfig.getInstance().getKeyValue().getType());
      return mapdb ? new ClusterStorageTransfer()
         : new DirectStorageTransfer(keyValueEngine, blobEngine);
   }

   /**
    * Exports the contents of the key-value and blob stores.
    *
    * @param output the output stream to which the exported contents will be written.
    *
    * @throws IOException if an I/O error occurs.
    */
   void exportContents(OutputStream output) throws IOException;

   /**
    * Import the contents in a bundled zip file into the key-value and blob storage.
    *
    * @param file the zip file containing the contents to import.
    *
    * @throws IOException if an I/O error occurs.
    */
   void importContents(Path file) throws IOException;
}
