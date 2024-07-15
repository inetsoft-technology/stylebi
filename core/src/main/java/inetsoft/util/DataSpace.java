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
package inetsoft.util;

import inetsoft.storage.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.NoSuchFileException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * DataSpace object represents a data access implementation.
 *
 * @version 6.1, 06/04/2004
 * @author InetSoft Technology Corp
 */
@SingletonManager.Singleton(DataSpace.Reference.class)
public class DataSpace implements AutoCloseable {
   public DataSpace(BlobStorage<Metadata> blobStorage) {
      this.blobStorage = blobStorage;
      listeners = new ListenerTree();

      if(blobStorage != null) {
         blobStorage.addListener(listeners);
      }

      String home = ConfigurationContext.getContext().getHome()
         .trim().replace('\\', '/').replace("//", "/");
      homePath = home.endsWith("/") ? home.substring(0, home.length() - 1) : home;
   }

   @Override
   public void close() throws Exception {
      dispose();
   }

   /**
    * Get an instance of a DataSpace.
    */
   public static DataSpace getDataSpace() {
      return SingletonManager.getInstance(DataSpace.class);
   }

   /**
    * Clear the cached data space.
    */
   public static void clear() {
      SingletonManager.reset(DataSpace.class);
   }

   /**
    * Refresh last modified for the directory.
    */
   public static void updateFolder(String path) {
      if(Tool.isEmptyString(path)) {
         return;
      }

      DataSpace space = getDataSpace();
      String[] paths = path.split("/");

      if(paths.length > 0) {
         StringBuffer buffer = new StringBuffer();

         for(int i = 0; i < paths.length; i++) {
            if(i != 0) {
               buffer.append("/");
            }

            buffer.append(paths[i]);
            String folder = buffer.toString();

            if(space.isDirectory(folder)) {
               space.makeDirectory(folder);
            }
         }
      }
   }

   /**
    * Add a change listener to be notified when a file is modified.  If file
    * is null, notification should be sent if files are added or removed
    * in the directory.
    *
    * @param dir directory name
    * @param file file name
    * @param listener change listener
    */
   public void addChangeListener(String dir, String file, DataChangeListener listener) {
      listeners.addListener(getPath(dir, file), listener);
   }

   /**
    * Remove a change listener.
    *
    * @param dir directory name
    * @param file file name
    * @param listener directory name
    */
   public void removeChangeListener(String dir, String file, DataChangeListener listener) {
      listeners.removeListener(getPath(dir, file), listener);
   }

   /**
    * Get the length of the file.
    * @param dir directory name
    * @param file file name
    */
   public long getFileLength(String dir, String file) {
      String path = getPath(dir, file);

      try {
         return blobStorage.getLength(path);
      }
      catch(FileNotFoundException ignore) {
         return 0L;
      }
   }

   /**
    * Gets an input stream for the specified file.
    *
    * @param dir directory name
    * @param file file name
    *
    * @return input stream to file
    */
   public InputStream getInputStream(String dir, String file) throws IOException {
      String path = getPath(dir, file);

      try {
         return blobStorage.getInputStream(path);
      }
      catch(FileNotFoundException | NoSuchFileException ignore) {
         return null;
      }
   }

   /**
    * Creates a new transaction in which files may be created or modified.
    *
    * @return a transaction.
    */
   public Transaction beginTransaction() {
      return new TransactionImpl();
   }

   /**
    * Writes to a file in a transaction. This is a convenience method that creates a transaction,
    * creates an output stream, performs the action, and then commits the transaction.
    *
    * @param dir  the directory path.
    * @param file the file name.
    * @param op   the operation to perform.
    *
    * @throws IOException if an I/O error occurs.
    */
   public void withOutputStream(String dir, String file, OutputStreamOperation op)
      throws IOException
   {
      try(Transaction tx = beginTransaction();
          OutputStream output = tx.newStream(dir, file))
      {
         op.accept(output);
         tx.commit();
      }
   }

   public void withOutputStream(String dir, String file, long lastModified,
                                OutputStreamOperation op)
      throws IOException
   {
      try(Transaction tx = beginTransaction();
          OutputStream output = tx.newStream(dir, file, lastModified))
      {
         op.accept(output);
         tx.commit();
      }
   }

   /**
    * List the files in a directory.
    *
    * @param dir directory name
    *
    * @return array of files and directory update dir
    */
   public String[] list(String dir) {
      String path = sanitizePathComponent(dir);
      String prefix = path == null || path.isEmpty() ? "" : path + "/";
      return blobStorage.stream()
         .map(Blob::getPath)
         .filter(p -> isChildPath(prefix, p))
         .map(p -> p.substring(prefix.length()))
         .toArray(String[]::new);
   }

   /**
    * Check if path is a directory.
    *
    * @param path path
    *
    * @return true if path is a directory
    */
   public boolean isDirectory(String path) {
      if(path == null || path.isEmpty() || path.equals("/")) {
         return true;
      }

      return blobStorage.isDirectory(sanitizePathComponent(path));
   }

   /**
    * Get path.
    *
    * @param dir directory name
    * @param file file name
    */
   public String getPath(String dir, String file) {
      StringBuilder path = new StringBuilder();
      String sanitizedDir = sanitizePathComponent(dir);
      String sanitizedFile = sanitizePathComponent(file);

      if(sanitizedDir != null && !sanitizedDir.isEmpty()) {
         path.append(sanitizedDir);
      }

      if(sanitizedFile != null && !sanitizedFile.isEmpty()) {
         if(path.length() > 0) {
            path.append('/');
         }

         path.append(sanitizedFile);
      }

      return path.toString();
   }

   /**
    * Check if path file or directory exists.
    *
    * @param dir directory name
    * @param file file name
    *
    * @return true if path exists
    */
   public boolean exists(String dir, String file) {
      return blobStorage.exists(getPath(dir, file));
   }

   /**
    * Delete the file from the DataSpace.
    *
    * @param dir directory name
    * @param file file name
    */
   public boolean delete(String dir, String file) {
      return deleteRecursively(getPath(dir, file));
   }

   private boolean deleteRecursively(String path) {
      if(isDirectory(path)) {
         for(String child : list(path)) {
            String childPath = path.isEmpty() ? child : path + "/" + child;

            if(!deleteRecursively(childPath)) {
               return false;
            }
         }
      }

      try {
         if(blobStorage.exists(path)) {
            blobStorage.delete(path);
         }

         return true;
      }
      catch(IOException e) {
         LOG.warn("Failed to delete file {}", path, e);
      }

      return false;
   }

   /**
    * Rename a file/folder in the DataSpace.
    *
    * @param opath old path name
    * @param npath new path name
    */
   public boolean rename(String opath, String npath) {
      String oldPath = sanitizePathComponent(opath);
      String newPath = sanitizePathComponent(npath);
      return renameRecursively(oldPath, newPath);
   }

   private boolean renameRecursively(String oldPath, String newPath) {
      if(isDirectory(oldPath)) {
         for(String child : list(oldPath)) {
            String ochild = oldPath.isEmpty() ? child : oldPath + "/" + child;
            String nchild = newPath.isEmpty() ? child : newPath + "/" + child;

            if(!renameRecursively(ochild, nchild)) {
               return false;
            }
         }
      }

      try {
         blobStorage.rename(oldPath, newPath);
         return true;
      }
      catch(FileNotFoundException ignore) {
         return false;
      }
      catch(IOException e) {
         LOG.warn("Failed to rename {} to {}", oldPath, newPath, e);
      }

      return false;
   }

   /**
    * returns a list of org scoped paths in the dataspace
    * @param orgID, the orgID used to construct the org scoped paths
    * @return String[] containing org scoped paths
    */
   public static String[] getOrgScopedPaths(String orgID) {
      //currently limited to portal/orgID
      return new String[]{("portal/"+orgID)};
   }

   /**
    * Copies a file or folder in the data space.
    *
    * @param opath the source path name.
    * @param npath the target path name.
    */
   public boolean copy(String opath, String npath) {
      String oldPath = sanitizePathComponent(opath);
      String newPath = sanitizePathComponent(npath);
      return copyRecursively(oldPath, newPath);
   }

   private boolean copyRecursively(String oldPath, String newPath) {
      if(isDirectory(oldPath)) {
         for(String child : list(oldPath)) {
            String ochild = oldPath.isEmpty() ? child : oldPath + "/" + child;
            String nchild = newPath.isEmpty() ? child : newPath + "/" + child;

            if(!copyRecursively(ochild, nchild)) {
               return false;
            }
         }
      }

      try {
         blobStorage.copy(oldPath, newPath);
         return true;
      }
      catch(FileNotFoundException ignore) {
         return false;
      }
      catch(IOException e) {
         LOG.warn("Failed to copy {} to {}", oldPath, newPath, e);
      }

      return false;
   }

   /**
    * Retrieve the last modification time of the file.
    *
    * @param dir directory name
    * @param file file name
    *
    * @return modification time
    */
   public long getLastModified(String dir, String file) {
      String path = getPath(dir, file);

      try {
         return blobStorage.getLastModified(path).toEpochMilli();
      }
      catch(FileNotFoundException ignore) {
         return 0L;
      }
   }

   /**
    * Create the directory named by a path.
    *
    * @param path the specified path
    *
    * @return true if successful, false otherwise
    */
   public boolean makeDirectory(String path) {
      String sanitized = sanitizePathComponent(path);

      try {
         blobStorage.createDirectory(sanitized, new Metadata());
         return true;
      }
      catch(IOException e) {
         LOG.debug("Failed to create directory {}", sanitized, e);
         return false;
      }
   }

   /**
    * Creates the directory named by a path, including any necessary but
    * nonexistent parent directories.
    *
    * @param path the specified path
    *
    * @return true if successful, false otherwise
    */
   public boolean makeDirectories(String path) {
      String sanitized = sanitizePathComponent(path);
      int start = 0;
      int end = 0;

      while(end < sanitized.length() && (end = sanitized.indexOf('/', start)) >= 0) {
         String parent = sanitized.substring(0, end);

         if(!isDirectory(parent)) {
            makeDirectory(parent);
         }

         start = end + 1;
      }

      if(!isDirectory(sanitized)) {
         makeDirectory(sanitized);
      }

      return true;
   }

   /**
    * Dispose the data space.
    */
   public void dispose() {
      try {
         blobStorage.close();
      }
      catch(Exception e) {
         LOG.warn("Failed to close blob storage", e);
      }
   }

   private String getParentPath(String path) {
      int index = path.lastIndexOf('/');

      if(index < 0) {
         return null;
      }

      return path.substring(0, index);
   }

   private String sanitizePathComponent(String path) {
      if(path == null) {
         return null;
      }

      String sanitized = path.trim().replace('\\', '/').replace("//", "/");

      if(sanitized.startsWith(homePath)) {
         sanitized = sanitized.substring(homePath.length());
      }

      if(sanitized.startsWith("/")) {
         sanitized = sanitized.substring(1);
      }

      if(sanitized.startsWith("./")) {
         sanitized = sanitized.substring(2);
      }

      if(sanitized.endsWith("/")) {
         sanitized = sanitized.substring(0, sanitized.length() - 1);
      }

      if(sanitized.equals(".")) {
         sanitized = "";
      }

      return sanitized;
   }

   private boolean isChildPath(String prefix, String path) {
      return path.startsWith(prefix) && path.indexOf('/', prefix.length()) < 0;
   }

   private final BlobStorage<Metadata> blobStorage;
   private final String homePath;
   private final ListenerTree listeners;

   private static final Logger LOG = LoggerFactory.getLogger(DataSpace.class);

   public static final class Metadata implements Serializable {
   }

   /**
    * {@code OutputStreamOperation} is a function interface used to modify a file in a transaction.
    */
   @FunctionalInterface
   public interface OutputStreamOperation {
      /**
       * Writes data to an output stream.
       *
       * @param output the output stream.
       *
       * @throws IOException if an I/O error occurs.
       */
      void accept(OutputStream output) throws IOException;
   }

   /**
    * {@code Transaction} wraps one or more file writes in a transaction.
    */
   public interface Transaction extends Closeable {
      /**
       * Creates a new output stream to write to a file.
       *
       * @param dir  the directory path.
       * @param file the file name.
       *
       * @return an output stream.
       *
       * @throws IOException if an I/O error occurs.
       */
      OutputStream newStream(String dir, String file) throws IOException;

      /**
       * Creates a new output stream to write to a file.
       *
       * @param dir  the directory path.
       * @param file the file name.
       * @param lastModified the file modify time.
       *
       * @return an output stream.
       *
       * @throws IOException if an I/O error occurs.
       */
      OutputStream newStream(String dir, String file, long lastModified) throws IOException;

      /**
       * Commits all changes made by output streams created since the last commit.
       *
       * @throws IOException if an I/O error occurs.
       */
      void commit() throws IOException;
   }

   public final class TransactionImpl implements Transaction {
      TransactionImpl() {
         tx = blobStorage.beginTransaction();
      }

      public OutputStream newStream(String dir, String file) throws IOException {
         String path = getPath(dir, file);
         String parentPath = getParentPath(path);
         return tx.newStream(path, new Metadata(), () -> {
            if(parentPath != null) {
               makeDirectories(parentPath);
            }
         });
      }

      public OutputStream newStream(String dir, String file, long lastModified) throws IOException {
         String path = getPath(dir, file);
         String parentPath = getParentPath(path);
         return tx.newStream(path, new Metadata(), () -> {
            if(parentPath != null) {
               makeDirectories(parentPath);
            }
         }, lastModified);
      }

      public void commit() throws IOException {
         tx.commit();
      }

      @Override
      public void close() throws IOException {
         tx.close();
      }

      private final BlobTransaction<Metadata> tx;
   }

   private static final class ListenerTree implements BlobStorage.Listener<Metadata> {
      @Override
      public void blobAdded(BlobStorage.Event<Metadata> event) {
         Blob<Metadata> blob = event.getNewValue();
         fireEvent(blob.getPath(), createEvent(blob, blob.getLastModified().toEpochMilli()));
      }

      @Override
      public void blobUpdated(BlobStorage.Event<Metadata> event) {
         Blob<Metadata> blob = event.getNewValue();
         fireEvent(blob.getPath(), createEvent(blob, blob.getLastModified().toEpochMilli()));
      }

      @Override
      public void blobRemoved(BlobStorage.Event<Metadata> event) {
         Blob<Metadata> blob = event.getOldValue();

         if(blob != null) {
            fireEvent(blob.getPath(), createEvent(blob, System.currentTimeMillis()));
         }
      }

      void addListener(String path, DataChangeListener listener) {
         root.addListener(getPath(path), listener);
      }

      void removeListener(String path, DataChangeListener listener) {
         root.removeListener(getPath(path), listener);
      }

      private void fireEvent(String path, DataChangeEvent event) {
         root.fireEvent(getPath(path), event);
      }

      private List<String> getPath(String path) {
         return path == null || path.isEmpty() || path.equals("/") ?
            Collections.emptyList() : Arrays.asList(path.split("/"));
      }

      private DataChangeEvent createEvent(Blob<Metadata> blob, long timestamp) {
         String path = blob.getPath();
         int index = path.lastIndexOf('/');
         String dir = index < 0 ? null : path.substring(0, index);
         String file = index < 0 ? path : path.substring(index + 1);
         return new DataChangeEvent(dir, file, timestamp);
      }

      private final ListenerTreeNode root = new ListenerTreeNode();
   }

   private static final class ListenerTreeNode {
      void addListener(List<String> path, DataChangeListener listener) {
         if(path.isEmpty()) {
            listeners.add(listener);
         }
         else {
            ListenerTreeNode node =
               children.computeIfAbsent(path.get(0), k -> new ListenerTreeNode());
            node.addListener(path.subList(1, path.size()), listener);
         }
      }

      void removeListener(List<String> path, DataChangeListener listener) {
         if(path.isEmpty()) {
            listeners.remove(listener);
         }
         else {
            ListenerTreeNode node = children.get(path.get(0));

            if(node != null) {
               node.removeListener(path.subList(1, path.size()), listener);
            }
         }
      }

      void fireEvent(List<String> path, DataChangeEvent event) {
         for(DataChangeListener listener : listeners) {
            listener.dataChanged(event);
         }

         if(path.isEmpty()) {
            for(ListenerTreeNode node : children.values()) {
               node.fireEvent(Collections.emptyList(), event);
            }
         }
         else {
            ListenerTreeNode node = children.get(path.get(0));

            if(node != null) {
               node.fireEvent(path.subList(1, path.size()), event);
            }
         }
      }

      private final Map<String, ListenerTreeNode> children = new ConcurrentHashMap<>();
      private final Set<DataChangeListener> listeners =
         new ConcurrentSkipListSet<>(Comparator.comparing(DataChangeListener::hashCode));
   }

   public static final class Reference extends SingletonManager.Reference<DataSpace> {
      @Override
      public DataSpace get(Object... parameters) {
         if(dataSpace == null) {
            dataSpace = new DataSpace(
               SingletonManager.getInstance(BlobStorage.class, "dataSpace", true)
            );

            if(dataSpace == null) {
               throw new RuntimeException("Failed to create data space");
            }
         }

         return dataSpace;
      }

      @Override
      public void dispose() {
         if(dataSpace != null) {
            dataSpace.dispose();
            dataSpace = null;
         }
      }

      private DataSpace dataSpace;
   }
}
