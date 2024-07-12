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
import inetsoft.storage.BlobStorage;
import inetsoft.storage.BlobTransaction;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Properties;

public class StorageLibraryEntry implements PhysicalLibraryEntry {
   public StorageLibraryEntry(String path, String comment, Properties properties,
                              BlobStorage<LibManager.Metadata> storage,
                              BlobTransaction<LibManager.Metadata> tx)
   {
      this.path = path;
      this.comment = comment;
      this.properties = properties;
      this.storage = storage;
      this.tx = tx;
   }

   /**
    * Gets the path to this entry.
    *
    * @return the path.
    */
   public String getPath() {
      return path;
   }

   /**
    * Determines if this entry represents a directory.
    *
    * @return <tt>true</tt> if a directory.
    */
   public boolean isDirectory() {
      return storage.isDirectory(getStorageKey());
   }

   /**
    * Gets the comment for this entry.
    */
   public String getComment() {
      return comment;
   }

   /**
    * Gets the comment properties for this entry.
    */
   public Properties getCommentProperties() {
      return properties;
   }

   /**
    * Gets the input stream for this entry.
    *
    * @return the input stream.
    *
    * @throws IOException if an I/O error occurs.
    */
   public InputStream getInputStream() throws IOException {
      return storage.getInputStream(getStorageKey());
   }

   /**
    * Gets the output stream for this entry.
    *
    * @return the output stream.
    *
    * @throws IOException if an I/O error occurs.
    */
   public OutputStream getOutputStream() throws IOException {
      LibManager.Metadata metadata = new LibManager.Metadata();
      metadata.setPath(path);
      metadata.setDirectory(isDirectory());
      metadata.setComment(comment);
      metadata.setCommentProperties(properties);
      return tx.newStream(getStorageKey(), metadata);
   }

   /**
    * Get last modified time.
    * @return time in ms or -1 if the time is not known.
    */
   public long getLastModified() {
      try {
         return storage.getLastModified(getStorageKey()).toEpochMilli();
      }
      catch(FileNotFoundException e) {
         LoggerFactory.getLogger(getClass())
            .warn("Failed to get last modified time for '{}'", path, e);
         return -1L;
      }
   }

   /**
    * Remove file for this entry.
    */
   public void remove() {
      try {
         storage.delete(getStorageKey());
      }
      catch(IOException e) {
         LoggerFactory.getLogger(getClass()).warn("Failed to delete library entry '{}'", path, e);
      }
   }

   private String getStorageKey() {
      return path == null || path.isEmpty() ? "/" : path;
   }

   private final String path;
   private final String comment;
   private final Properties properties;
   private final BlobStorage<LibManager.Metadata> storage;
   private final BlobTransaction<LibManager.Metadata> tx;
}
