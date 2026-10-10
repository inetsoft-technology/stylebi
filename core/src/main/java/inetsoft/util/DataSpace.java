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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.Organization;
import inetsoft.sree.security.OrgScopedPaths;
import inetsoft.storage.*;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

/**
 * DataSpace object represents a data access implementation.
 *
 * @version 6.1, 06/04/2004
 * @author InetSoft Technology Corp
 */
@Service
public class DataSpace implements AutoCloseable {
   /**
    * Spring constructor — obtains the data space blob storage from the manager.
    *
    * @param blobStorageManager the blob storage manager.
    */
   @Autowired
   public DataSpace(BlobStorageManager blobStorageManager) {
      this.blobStorageManager = blobStorageManager;
      listeners = new ListenerTree();

      BlobStorage<Metadata> storage = blobStorageManager.<Metadata>getStorage(STORAGE_ID, true);

      // the security provider chains and the virtual admin are stored here, and a file that is
      // missing from an unloaded store is replaced with a default one. Starting with a store whose
      // load failed would start the node with security off and overwrite the stored files, so
      // retry the load once and fail if it still does not complete (Bug #77198)
      if(storage != null && !storage.isLoaded() && !storage.retryLoad()) {
         throw new IllegalStateException(
            "Failed to load the data space storage " + STORAGE_ID + ", the server cannot start " +
            "without its data space");
      }

      this.blobStorage = storage;

      if(storage != null) {
         storage.addListener(listeners);
      }

      String home = ConfigurationContext.getContext().getHome()
         .trim().replace('\\', '/').replace("//", "/");
      homePath = home.endsWith("/") ? home.substring(0, home.length() - 1) : home;
   }

   /**
    * Gets the current live blob storage for the data space, refreshing it if the inner
    * key-value storage has been evicted from the {@link inetsoft.storage.KeyValueStorageManager}
    * cache.
    */
   private BlobStorage<Metadata> storage() {
      if(blobStorage != null && blobStorage.isClosed()) {
         synchronized(this) {
            BlobStorage<Metadata> old = blobStorage;

            if(old != null && old.isClosed()) {
               BlobStorage<Metadata> fresh = blobStorageManager.<Metadata>getStorage(STORAGE_ID, false);

               if(fresh == null) {
                  LOG.error("Failed to obtain a fresh DataSpace blob storage after eviction");
                  return blobStorage;
               }

               // the replicated map was loaded when the data space was created and outlives the
               // eviction, so a failed reload does not leave it empty
               if(!fresh.isLoaded()) {
                  LOG.warn("Failed to reload the DataSpace blob storage after eviction");
               }

               fresh.addListener(listeners);
               blobStorage = fresh;

               // Explicitly close the old BlobStorage to shut down its eventExecutor thread.
               // Its inner KeyValueStorage was already closed (by LRU eviction), so
               // BlobStorageManager's removal listener skipped close(); we must do it here.
               try {
                  old.close();
               }
               catch(Exception e) {
                  LOG.warn("Failed to close stale DataSpace blob storage", e);
               }
            }
         }
      }

      return blobStorage;
   }

   @Override
   @PreDestroy
   public void close() throws Exception {
      dispose();
   }

   /**
    * Get an instance of a DataSpace.
    */
   public static DataSpace getDataSpace() {
      return ConfigurationContext.getContext().getSpringBean(DataSpace.class);
   }

   /**
    * Clear the cached data space.
    */
   public static void clear() {
      // no-op: DataSpace is a Spring-managed singleton; state is refreshed on demand
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
         return storage().getLength(path);
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
         return storage().getInputStream(path);
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
      return storage().stream()
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

      return storage().isDirectory(sanitizePathComponent(path));
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
      return storage().exists(getPath(dir, file));
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
         if(storage().exists(path)) {
            storage().delete(path);
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

      if(!canMoveTo(oldPath, newPath)) {
         return false;
      }

      boolean result = renameRecursively(oldPath, newPath);
      // created after the move so that a rename into its own subtree, which removes the old
      // folder marker, still leaves the ancestors of the new path as directories
      makeMissingAncestors(newPath, result);
      return result;
   }

   private boolean renameRecursively(String oldPath, String newPath) {
      boolean isDir = isDirectory(oldPath);

      if(isDir) {
         String[] children = list(oldPath);

         for(String child : children) {
            String ochild = oldPath.isEmpty() ? child : oldPath + "/" + child;
            String nchild = newPath.isEmpty() ? child : newPath + "/" + child;

            if(!renameRecursively(ochild, nchild)) {
               return false;
            }
         }
      }

      try {
         storage().rename(oldPath, newPath);
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
    * @param oorg, the oorg used to construct the org scoped paths
    * @return String[] containing org scoped paths
    */
   public String[] getOrgScopedPaths(Organization oorg) {
      return storage().paths().filter(p -> OrgScopedPaths.isOrgScopedPath(p, oorg.getId()))
         .toArray(String[]::new);
   }

   /**
    * Determines if the data space contains any path that an organization with the id would own,
    * i.e. that {@link #getOrgScopedPaths} would return for it.
    */
   public boolean hasOrgScopedPaths(String orgId) {
      return storage().paths().anyMatch(p -> OrgScopedPaths.isOrgScopedPath(p, orgId));
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

      if(!canMoveTo(oldPath, newPath)) {
         return false;
      }

      boolean result = copyRecursively(oldPath, newPath);
      makeMissingAncestors(newPath, result);
      return result;
   }

   /**
    * Determines if a file or folder can be renamed or copied to a new path. The source must
    * exist and no ancestor of the new path may be a file.
    */
   private boolean canMoveTo(String oldPath, String newPath) {
      if(oldPath == null || newPath == null || !isDirectory(oldPath) && !storage().exists(oldPath)) {
         return false;
      }

      String parent = getParentPath(newPath);

      while(parent != null) {
         if(storage().exists(parent) && !storage().isDirectory(parent)) {
            LOG.warn("Cannot move {} to {}, {} is a file", oldPath, newPath, parent);
            return false;
         }

         parent = getParentPath(parent);
      }

      return true;
   }

   /**
    * Creates the directory markers for the ancestors of a renamed or copied path that do not
    * exist. Without a marker, a folder is not listed and is not treated as a directory, so
    * deleting it leaves its children behind.
    *
    * @param newPath the target path.
    * @param moved   {@code true} if the rename or copy succeeded.
    */
   private void makeMissingAncestors(String newPath, boolean moved) {
      String parent = getParentPath(newPath);

      if(parent == null) {
         return;
      }

      // a partially failed move may still have moved some descendants to the new path
      if(!moved && !storage().exists(newPath) &&
         storage().paths().noneMatch(p -> p.startsWith(newPath + "/")))
      {
         return;
      }

      int end = 0;

      while((end = newPath.indexOf('/', end + 1)) > 0) {
         String ancestor = newPath.substring(0, end);

         // never replace an existing key, a directory marker over a file orphans its content
         if(!storage().exists(ancestor)) {
            makeDirectory(ancestor);
         }
      }
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
         storage().copy(oldPath, newPath);
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

   public String listBlobs() throws IOException {
      return storage().listBlobs();
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
         return storage().getLastModified(path).toEpochMilli();
      }
      catch(FileNotFoundException ignore) {
         return 0L;
      }
   }

   /**
    * Retrieve the digest of the content of the file, which identifies what the file holds
    * whenever it was written, unlike its modification time.
    *
    * @param dir directory name
    * @param file file name
    *
    * @return the lower-case hexadecimal MD5 hash of the file content, or {@code null} if the file
    *         does not exist or is a directory
    */
   public String getDigest(String dir, String file) {
      String path = getPath(dir, file);

      try {
         return storage().getDigest(path);
      }
      catch(FileNotFoundException ignore) {
         return null;
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
         storage().createDirectory(sanitized, new Metadata());
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
      // check the whole chain first so that a refused call creates no markers
      String file = isFile(sanitized) ? sanitized : findFileAncestor(sanitized);

      if(file != null) {
         LOG.warn("Cannot create directory {}, {} is a file", sanitized, file);
         return false;
      }

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
    * Determines if a sanitized path exists and is a file. A directory marker over a file
    * replaces the file and orphans its content.
    */
   private boolean isFile(String path) {
      return path != null && storage().exists(path) && !storage().isDirectory(path);
   }

   /**
    * Finds the nearest ancestor of a sanitized path, from the parent up to the root, that is a
    * file. The path itself is not checked.
    *
    * @return the ancestor path, or {@code null} if no ancestor is a file.
    */
   private String findFileAncestor(String path) {
      String parent = path == null ? null : getParentPath(path);

      while(parent != null) {
         if(isFile(parent)) {
            return parent;
         }

         parent = getParentPath(parent);
      }

      return null;
   }

   /**
    * Creates the missing directory markers of folders that have children but no key of their
    * own. Such a folder is not listed and is not treated as a directory, so deleting it leaves
    * its children behind. They are left by older versions and by restoring a backup taken from
    * one (Bug #77387). The passes of the cluster nodes are serialized, and no existing key is
    * replaced.
    *
    * @return the number of directory markers created.
    */
   public int repairMissingFolders() {
      Lock lock = Cluster.getInstance().getLock(REPAIR_FOLDERS_LOCK);
      lock.lock();

      try {
         Set<String> keys = storage().paths().collect(Collectors.toSet());
         // shortest first, so that a parent is created before its children
         SortedSet<String> missing = new TreeSet<>(
            Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()));

         for(String key : keys) {
            for(String parent = getParentPath(key); parent != null; parent = getParentPath(parent)) {
               // a raw key that is not in canonical form can't be reached through the data space
               if(!keys.contains(parent) && !parent.isEmpty() &&
                  parent.equals(sanitizePathComponent(parent)))
               {
                  missing.add(parent);
               }
            }
         }

         int created = 0;

         for(String folder : missing) {
            // check the live store again, the snapshot is stale if a folder was created, deleted
            // or renamed since it was taken
            if(storage().exists(folder) ||
               storage().paths().noneMatch(p -> p.startsWith(folder + "/")))
            {
               continue;
            }

            String file = findFileAncestor(folder);

            if(file != null) {
               LOG.warn("Cannot create the missing directory {}, {} is a file", folder, file);
               continue;
            }

            try {
               storage().createDirectory(folder, new Metadata());
               created++;
            }
            catch(Exception e) {
               LOG.warn("Failed to create the missing directory {}", folder, e);
            }
         }

         if(created > 0) {
            LOG.info("Created {} missing data space directories", created);
         }

         return created;
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Dispose the data space.
    */
   public void dispose() {
      BlobStorage<Metadata> s = blobStorage;

      if(s != null) {
         try {
            s.close();
         }
         catch(Exception e) {
            LOG.warn("Failed to close blob storage", e);
         }
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

      // Treat the unresolved sree.home placeholder the same as the resolved home
      // directory so FS index paths (fs.files / fs.bs.files default to
      // "$(sree.home)/fs.xml") map to home-relative keys instead of creating a
      // literal "$(sree.home)" node (e.g. in the cloud runner where the home
      // directory differs and the placeholder is left unresolved).
      if(sanitized.startsWith(HOME_PLACEHOLDER)) {
         sanitized = sanitized.substring(HOME_PLACEHOLDER.length());
      }
      else if(sanitized.startsWith(homePath)) {
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

   private volatile BlobStorage<Metadata> blobStorage;
   private final BlobStorageManager blobStorageManager;
   private final String homePath;
   private final ListenerTree listeners;

   private static final String HOME_PLACEHOLDER = "$(sree.home)";
   private static final String STORAGE_ID = "dataSpace";
   private static final String REPAIR_FOLDERS_LOCK = DataSpace.class.getName() + ".repairFolders";
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
         tx = storage().beginTransaction();
      }

      public OutputStream newStream(String dir, String file) throws IOException {
         String path = getPath(dir, file);
         return tx.newStream(path, new Metadata(), () -> makeParentDirectories(path));
      }

      public OutputStream newStream(String dir, String file, long lastModified) throws IOException {
         String path = getPath(dir, file);
         return tx.newStream(path, new Metadata(), () -> makeParentDirectories(path),
                             lastModified);
      }

      /**
       * Creates the parent directories of a file before it is committed. The write fails, and is
       * rolled back, if an ancestor is a file, or if the path itself already names a directory.
       */
      private void makeParentDirectories(String path) throws IOException {
         if(isDirectory(path)) {
            LOG.warn("Cannot write {}, it is already a directory", path);
            throw new FileAlreadyExistsException(path);
         }

         String parentPath = getParentPath(path);

         if(parentPath == null) {
            return;
         }

         String ancestor = findFileAncestor(path);

         if(ancestor != null) {
            LOG.warn("Cannot write {}, {} is a file", path, ancestor);
            throw new NotDirectoryException(ancestor);
         }

         makeDirectories(parentPath);
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

}

