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
package inetsoft.report.lib.physical;

import inetsoft.report.LibManager;
import inetsoft.storage.*;

import java.io.IOException;
import java.util.*;

public class StorageLibrary implements PhysicalLibrary {
   public StorageLibrary(BlobStorage<LibManager.Metadata> storage) {
      this.storage = storage;
   }

   /**
    * Creates a new entry in the library.
    *
    * @param path    the path to the entry.
    * @param properties the entry properties.
    *
    * @return a new entry.
    *
    * @throws IOException if an I/O error occurs.
    */
   public PhysicalLibraryEntry createEntry(String path, Properties properties) throws IOException {
      String folder;
      boolean isFolder = false;
      String comment = properties == null ? null : properties.getProperty("comment");

      if(path.endsWith("/")) {
         folder = path.substring(0, path.length() - 1);
         isFolder = true;
      }
      else {
         int index = path.lastIndexOf('/');
         folder = index < 0 ? null : path.substring(0, index);
      }

      if(folder != null) {
         if(folder.startsWith("/")) {
            folder = folder.substring(1);
         }

         if(!folder.isEmpty()) {
            List<String> paths = Arrays.asList(folder.split("/"));

            for(int i = 0; i < paths.size(); i++) {
               String folderPath = String.join("/", paths.subList(0, i + 1));

               if(isFolder && i == paths.size() - 1) {
                  LibManager.Metadata metadata = new LibManager.Metadata();
                  metadata.setPath(folderPath);
                  metadata.setDirectory(true);
                  metadata.setComment(comment);
                  metadata.setCommentProperties(properties);
                  storage.createDirectory(folderPath, metadata);
               }
               else if(!storage.exists(folderPath)) {
                  LibManager.Metadata metadata = new LibManager.Metadata();
                  metadata.setPath(folderPath);
                  metadata.setDirectory(true);
                  storage.createDirectory(folderPath, metadata);
               }
            }
         }
      }

      if(tx == null) {
         tx = storage.beginTransaction();
      }

      return new StorageLibraryEntry(path, comment, properties, storage, tx);
   }

   /**
    * Gets the entries in the library.
    *
    * @return the entries.
    *
    * @throws IOException if an I/O error occurs.
    */
   public Iterator<PhysicalLibraryEntry> getEntries() throws IOException {
      return storage.stream()
         .map(this::createPhysicalEntry)
         .iterator();
   }

   @Override
   public void close() throws IOException {
      if(tx != null) {
         try {
            tx.commit();
         }
         finally {
            tx = null;
         }
      }
   }

   private PhysicalLibraryEntry createPhysicalEntry(Blob<LibManager.Metadata> blob) {
      LibManager.Metadata metadata = blob.getMetadata();

      if(tx == null) {
         tx = storage.beginTransaction();
      }

      String path = "/".equals(blob.getPath()) ? "" : blob.getPath();
      return new StorageLibraryEntry(
         path, metadata.getComment(), metadata.getCommentProperties(), storage, tx);
   }

   private final BlobStorage<LibManager.Metadata> storage;
   private BlobTransaction<LibManager.Metadata> tx;
}
