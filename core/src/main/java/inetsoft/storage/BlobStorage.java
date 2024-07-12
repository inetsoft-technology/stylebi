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
package inetsoft.storage;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedLong;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.util.SingletonManager;
import inetsoft.util.config.InetsoftConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * {@code BlobStorage} is an interface for classes that handle storage of blob data.
 *
 * @param <T> the extended metadata type.
 */
@SingletonManager.Singleton(BlobStorage.Reference.class)
public abstract class BlobStorage<T extends Serializable> implements AutoCloseable {
   /**
    * Creates a new instance of {@code BlobStorage}.
    *
    * @param id      the unique identifier of the blob storage.
    * @param storage the key-value store for the blob metadata.
    */
   protected BlobStorage(String id, KeyValueStorage<Blob<T>> storage) {
      Objects.requireNonNull(id, "The store identifier cannot be null");
      this.id = id;
      Objects.requireNonNull(storage, "The metadata storage cannot be null");
      this.storage = storage;
      this.cluster = Cluster.getInstance();
      this.lastModified = cluster.getLong("inetsoft.storage.blob.ts." + id);
      String host = cluster.getLocalMember();
      int index = host.lastIndexOf(':');
      this.lockHost = index < 0 ? host : host.substring(0, index);
      storage.addListener(listener);
   }

   /**
    * Determines if a blob at the specified path exists.
    *
    * @param path the path to the blob.
    *
    * @return {@code true} if the blob exists or {@code false} if it does not.
    */
   public final boolean exists(String path) {
      return storage.contains(path);
   }

   /**
    * Determines if the specified path is a directory.
    *
    * @param path the path to check.
    *
    * @return {@code true} if the specified path is a directory or {@code false} if it is not.
    */
   public final boolean isDirectory(String path) {
      Blob<T> blob = storage.get(path);
      return blob != null && blob.getDigest() == null;
   }

   /**
    * Gets the length of the blob.
    *
    * @param path the path to the blob.
    *
    * @return the length in bytes.
    *
    * @throws FileNotFoundException if no blob exists at the specified path.
    */
   public final long getLength(String path) throws FileNotFoundException {
      return getBlob(path).getLength();
   }

   /**
    * Gets the date and time at which the blob was last modified.
    *
    * @param path the path to the blob.
    *
    * @return the last modified time.
    *
    * @throws FileNotFoundException if no blob exists at the specified path.
    */
   public final Instant getLastModified(String path) throws FileNotFoundException {
      return getBlob(path).getLastModified();
   }

   /**
    * Gets the date and time at which the storage was last modified.
    *
    * @return the last modified time.
    */
   public final Instant getLastModified() {
      return Instant.ofEpochMilli(lastModified.get());
   }

   /**
    * Gets the extended metadata for a blob.
    *
    * @param path the path to the blob.
    *
    * @return the metadata or {@code null} if there is none.
    *
    * @throws FileNotFoundException if no blob exists at the specified path.
    */
   public final T getMetadata(String path) throws FileNotFoundException {
      return getBlob(path).getMetadata();
   }

   /**
    * Opens an input stream to the blob at the specified path.
    *
    * @param path the path to the blob.
    *
    * @return an input stream.
    *
    * @throws IOException if an I/O error occurs.
    */
   public final InputStream getInputStream(String path) throws IOException {
      BlobLock lock;

      try {
         lock = lock(path, true);
      }
      catch(Exception e) {
         throw new IOException("Failed to acquire lock for " + path + " in " + id, e);
      }

      InputStream input;

      try {
         input = getInputStream(getBlob(path));
      }
      catch(IOException e) {
         lock.unlock();
         throw e;
      }

      return new LockInputStream(input, lock);
   }

   /**
    * Opens an input stream to a blob.
    *
    * @param blob the blob.
    *
    * @return an input stream.
    *
    * @throws IOException if an I/O error occurs.
    */
   protected abstract InputStream getInputStream(Blob<T> blob) throws IOException;

   /**
    * Opens a read-only channel to a blob.
    *
    * @param path the path to the blob.
    *
    * @return a channel.
    *
    * @throws IOException if an I/O error occurs.
    */
   public final BlobChannel getReadChannel(String path) throws IOException {
      BlobLock lock;

      try {
         lock = lock(path, true);
      }
      catch(Exception e) {
         throw new IOException("Failed to acquire lock for " + path + " in " + id, e);
      }

      BlobChannel channel;

      try {
         channel = getReadChannel(getBlob(path));
      }
      catch(IOException e) {
         lock.unlock();
         throw e;
      }

      return new LockBlobChannel(channel, lock);
   }

   /**
    * Opens a read-only channel to a blob.
    *
    * @param blob the blob.
    *
    * @return a channel.
    *
    * @throws IOException if an I/O error occurs.
    */
   protected abstract BlobChannel getReadChannel(Blob<T> blob) throws IOException;

   /**
    * Creates a new transaction that can be used to create or modify blobs.
    *
    * @return a transaction.
    */
   public BlobTransaction<T> beginTransaction() {
      return new BlobTransaction<>(this);
   }

   /**
    * Copies an existing blob to a temporary file.
    *
    * @param blob the blob to copy.
    *
    * @return the new temporary file.
    *
    * @throws IOException if an I/O error occurs.
    */
   protected abstract Path copyToTemp(Blob<T> blob) throws IOException;

   /**
    * Creates a virtual directory in the blob store.
    *
    * @param path     the path to the directory.
    * @param metadata the extended metadata for the directory or {@code null} if none.
    *
    * @throws IOException if an I/O error occurs.
    */
   public final void createDirectory(String path, T metadata) throws IOException {
      Blob<T> blob = new Blob<>(path, null, 0L, Instant.now(), metadata);

      try {
         cluster.submit(id, new CreateDirectoryTask<>(id, blob)).get();
      }
      catch(Exception e) {
         throw new IOException("Failed to create directory at " + path, e);
      }
   }

   /**
    * Commits a blob to storage.
    *
    * @param blob     the blob metadata.
    * @param tempFile the temporary file containing the blob data.
    *
    * @throws IOException if an I/O error occurs.
    */
   protected abstract void commit(Blob<T> blob, Path tempFile) throws IOException;

   /**
    * Deletes the blob at the specified path.
    *
    * @param path the path to blob.
    *
    * @throws IOException if an I/O error occurs.
    */
   public final void delete(String path) throws IOException {
      BlobLock lock;

      try {
         lock = lock(path, false);
      }
      catch(Exception e) {
         throw new IOException("Failed to acquire lock for " + path + " in " + id, e);
      }

      try {
         BlobReference<T> ref =
            cluster.submit(id, new DeleteBlobTask<T>(id, path, isLocal())).get();

         if(ref.getBlob() == null) {
            throw new FileNotFoundException(path);
         }

         if(ref.getCount() == 0 && ref.getBlob().getDigest() != null) {
            delete(ref.getBlob());
         }
      }
      catch(InterruptedException | ExecutionException e) {
         throw new IOException("Failed to delete blob at " + path, e);
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Renames the blob at the specified path.
    *
    * @param oldPath the current path to the blob.
    * @param newPath the new path to the blob.
    *
    * @throws IOException if an I/O error occurs.
    */
   public final void rename(String oldPath, String newPath) throws IOException {
      try {
         cluster.submit(id, new RenameBlobTask<>(id, oldPath, newPath)).get();
      }
      catch(InterruptedException e) {
         throw new IOException("Failed to rename blob " + oldPath + " to " + newPath, e);
      }
      catch(ExecutionException e) {
         Throwable thrown = e.getCause();

         if(thrown instanceof IOException) {
            throw (IOException) thrown;
         }

         throw new IOException(
            "Failed to rename blob " + oldPath + " to " + newPath, thrown == null ? e : thrown);
      }
   }

   /**
    * Copies the blob at the specified path to a new path.
    *
    * @param oldPath      the path to the source blob.
    * @param newPath      the path to the new blob.
    *
    * @throws IOException if an I/O error occurs.
    */
   public final void copy(String oldPath, String newPath) throws IOException {
      try {
         cluster.submit(id, new CopyBlobTask<>(id, oldPath, newPath)).get();
      }
      catch(InterruptedException e) {
         throw new IOException("Failed to copy blob " + oldPath + " to " + newPath, e);
      }
      catch(ExecutionException e) {
         Throwable thrown = e.getCause();

         if(thrown instanceof IOException) {
            throw (IOException) thrown;
         }

         throw new IOException(
            "Failed to copy blob " + oldPath + " to " + newPath, thrown == null ? e : thrown);
      }
   }

   /**
    * Deletes this blob storage
    *
    * @throws Exception if an error occurs.
    */
   public final void deleteBlobStorage() throws Exception {
      storage.deleteStore();
      this.close();
   }

   /**
    * Deletes a blob from storage.
    *
    * @param blob the blob metadata.
    *
    * @throws IOException if an I/O error occurs.
    */
   protected abstract void delete(Blob<T> blob) throws IOException;

   /**
    * Gets a stream of the blobs in this store.
    *
    * @return a blob stream.
    */
   public Stream<Blob<T>> stream() {
      return storage.stream().map(KeyValuePair::getValue);
   }

   /**
    * Gets a stream of the paths in this store.
    *
    * @return a path stream.
    */
   public Stream<String> paths() {
      return storage.keys();
   }

   /**
    * Adds a listener that is notified when the store is changed.
    *
    * @param listener the listener to add.
    */
   public void addListener(Listener listener) {
      listeners.add(listener);
   }

   /**
    * Removes a listener from the notification list.
    *
    * @param listener the listener to remove.
    */
   public void removeListener(Listener<T> listener) {
      listeners.remove(listener);
   }

   /**
    * Gets the local filesystem path to a blob.
    *
    * @param blob the blob.
    * @param base the path to the base directory where blobs are stored.
    *
    * @return the path to the blob file.
    *
    * @throws IOException if the blob is not a file.
    */
   protected Path getPath(Blob<T> blob, Path base) throws IOException {
      if(blob.getDigest() == null) {
         throw new IOException("The blob at " + blob.getPath() + " is a directory");
      }

      String dir = blob.getDigest().substring(0, 2);
      String name = blob.getDigest().substring(2);
      return base.resolve(dir).resolve(name);
   }

   /**
    * Creates a temporary file. It is important that temporary files be created on the same drive or
    * disk partition as the local storage directory to ensure that atomic move/renames are possible
    * without having to copy the file.
    *
    * @param prefix the prefix for the file name.
    * @param suffix the suffix for the file name.
    *
    * @return a new temporary file.
    *
    * @throws IOException if an I/O error occurs.
    */
   protected abstract Path createTempFile(String prefix, String suffix) throws IOException;

   /**
    * Determines if this instance uses local blob storage.
    *
    * @return {@code true} if local or {@code false} if not.
    */
   protected abstract boolean isLocal();

   /**
    * Gets the metadata storage.
    *
    * @return the storage.
    */
   protected final KeyValueStorage<Blob<T>> getStorage() {
      return storage;
   }

   /**
    * Puts a blob into the backing key-value storage.
    *
    * @param blob the blob to add.
    *
    * @return a reference to the replaced blob.
    *
    * @throws Exception if the blob could not be added.
    */
   protected final BlobReference<T> putBlob(Blob<T> blob) throws Exception {
      return cluster.submit(id, new PutBlobTask<>(id, blob, isLocal())).get();
   }

   /**
    * Acquires a lock on a blob.
    *
    * @param path     the path to the blob.
    * @param readOnly {@code true} for a read lock or {@code false} for a write lock.
    *
    * @return the lock.
    *
    * @throws Exception if the lock could not be acquired.
    */
   protected final BlobLock lock(String path, boolean readOnly) throws Exception {
      String lockName = lockHost + ":" + id + ":" + path;

      if(readOnly) {
         cluster.lockRead(lockName);
      }
      else {
         cluster.lockWrite(lockName);
      }

      return new BlobLock(cluster, lockName, readOnly);
   }

   protected final Cluster getCluster() {
      return cluster;
   }

   protected final String getId() {
      return id;
   }

   @Override
   public void close() throws Exception {
      storage.removeListener(listener);
      storage.close();
      isClosed = true;
   }

   public boolean isClosed() {
      return isClosed;
   }

   public static <T extends Serializable> BlobStorage<T> createBlobStorage(String id,
                                                                           boolean preload)
      throws IOException
   {
      KeyValueStorage<Blob<T>> storage =
         SingletonManager.getInstance(KeyValueStorage.class, id,
                                      (Supplier<LoadBlobsTask<?>>) () -> new LoadBlobsTask<>(id));
      InetsoftConfig config = InetsoftConfig.getInstance();
      String type = config.getBlob().getType();

      if(type == null || type.equals("local")) {
         Path base = Paths.get(config.getBlob().getFilesystem().getDirectory());
         return new LocalBlobStorage<>(id, base, storage);
      }

      Path cacheDir = Paths.get(config.getBlob().getCacheDirectory());
      return new CachedBlobStorage<>(id, cacheDir, storage, BlobEngine.getInstance(), preload);
   }

   private Blob<T> getBlob(String path) throws FileNotFoundException {
      Blob<T> blob = storage.get(path);

      if(blob == null) {
         throw new FileNotFoundException(path);
      }

      return blob;
   }

   private final String id;
   private final KeyValueStorage<Blob<T>> storage;
   private final Cluster cluster;
   private final DistributedLong lastModified;
   private final Set<Listener<T>> listeners =
      new ConcurrentSkipListSet<>(Comparator.comparing(Listener::hashCode));
   private final String lockHost;
   private boolean isClosed = false;

   private final KeyValueStorage.Listener<Blob<T>> listener = new KeyValueStorage.Listener<Blob<T>>() {
      @Override
      public void entryAdded(KeyValueStorage.Event<Blob<T>> kvEvent) {
         Event<T> event = new Event<>(this, kvEvent.getOldValue()
            , kvEvent.getNewValue(), kvEvent.getMapName());

         for(Listener<T> listener : listeners) {
            listener.blobAdded(event);
         }
      }

      @Override
      public void entryUpdated(KeyValueStorage.Event<Blob<T>> kvEvent) {
         Event<T> event = new Event<>(this, kvEvent.getOldValue()
            , kvEvent.getNewValue(), kvEvent.getMapName());

         for(Listener<T> listener : listeners) {
            listener.blobUpdated(event);
         }
      }

      @Override
      public void entryRemoved(KeyValueStorage.Event<Blob<T>> kvEvent) {
         Event<T> event = new Event<>(this, kvEvent.getOldValue()
            , kvEvent.getNewValue(), kvEvent.getMapName());

         for(Listener<T> listener : listeners) {
            listener.blobRemoved(event);
         }
      }
   };

   /**
    * {@code Event} signals that blob has changed in a store.
    *
    * @param <T> the extended metadata type.
    */
   public static final class Event<T extends Serializable> extends EventObject {
      /**
       * Creates a new instance of {@code Event}.
       *
       * @param source   the source of the event.
       * @param oldValue the old blob, if any.
       * @param newValue the new blob, if any.
       */
      Event(Object source, Blob<T> oldValue, Blob<T> newValue, String mapName) {
         super(source);
         this.oldValue = oldValue;
         this.newValue = newValue;
         this.mapName = mapName;
      }

      /**
       * Gets the old value of the entry.
       *
       * @return the old value or {@code null} if none.
       */
      public Blob<T> getOldValue() {
         return oldValue;
      }

      /**
       * Gets the new value of the entry.
       *
       * @return the new value or {@code null} if none.
       */
      public Blob<T> getNewValue() {
         return newValue;
      }

      public String getMapName() {
         return mapName;
      }

      @Override
      public String toString() {
         return "Event{" +
            ", oldValue=" + oldValue +
            ", newValue=" + newValue +
            ", mapName=" + mapName +
            "} " + super.toString();
      }

      private final Blob<T> oldValue;
      private final Blob<T> newValue;
      private final String mapName;
   }

   /**
    * {@code Listener} is an interface for classes that are notified when a blob store is modified.
    *
    * @param <T> the extended metadata type.
    */
   public interface Listener<T extends Serializable> extends EventListener {
      /**
       * Called when a new blob is added to the store.
       *
       * @param event the event object.
       */
      void blobAdded(Event<T> event);

      /**
       * Called when an existing blob is updated in the store.
       *
       * @param event the event object.
       */
      void blobUpdated(Event<T> event);

      /**
       * Called when an existing blob is removed from the store.
       *
       * @param event the event object.
       */
      void blobRemoved(Event<T> event);
   }

   private static final class LoadBlobsTask<T extends Serializable> extends LoadKeyValueTask<Blob<T>> {
      public LoadBlobsTask(String id) {
         super(id);
      }

      @Override
      protected void validate(Map<String, Blob<T>> map) throws Exception {
         if(this.getId().endsWith("__indexedStorage")) {
            String orgID = this.getId().substring(0, this.getId().length() - 16);
            initIndexedStorage(map, orgID);
         }

         Cluster cluster = Cluster.getInstance();
         DistributedLong ts = cluster.getLong("inetsoft.storage.blob.ts." + getId());
         ts.set(map.values().stream()
                   .map(Blob::getLastModified)
                   .mapToLong(Instant::toEpochMilli)
                   .max()
                   .orElse(0L));
         super.validate(map);
      }

      private void initIndexedStorage(Map<String, Blob<T>> map, String orgID) {
         initializeRoot(AssetEntry.Type.REPOSITORY_FOLDER, map, orgID);
         initializeRoot(AssetEntry.Type.FOLDER, map, orgID);
         initializeRoot(AssetEntry.Type.SCHEDULE_TASK_FOLDER, map, orgID);
         initializeRoot(AssetEntry.Type.MV_DEF_FOLDER, map, orgID);
      }

      private void initializeRoot(AssetEntry.Type type, Map<String, Blob<T>> map, String orgID) {
         AssetEntry rootAsset = new AssetEntry(
            AssetRepository.GLOBAL_SCOPE, type, "/", null, orgID);

         if(!map.containsKey(rootAsset.toIdentifier())) {
            AssetFolder value = new AssetFolder();
            String key = rootAsset.toIdentifier();
            BlobIndexedStorage.Metadata metadata = new BlobIndexedStorage.Metadata();
            metadata.setClassName(value.getClass().getName());
            metadata.setIdentifier(key);
            metadata.setFolder(value);
            Blob<T> blob = new Blob(key, null, 0L, Instant.now(), metadata);

            map.put(key, blob);
         }
      }
   }

   protected static final class BlobLock {
      public BlobLock(Cluster cluster, String lockName, boolean readOnly) {
         this.cluster = cluster;
         this.lockName = lockName;
         this.readOnly = readOnly;
      }

      public void unlock() {
         if(readOnly) {
            cluster.unlockRead(lockName);
         }
         else {
            cluster.unlockWrite(lockName);
         }
      }

      private final Cluster cluster;
      private final String lockName;
      private final boolean readOnly;
   }

   private static final class LockInputStream extends FilterInputStream {
      public LockInputStream(InputStream in, BlobLock lock) {
         super(in);
         this.lock = lock;
      }

      @Override
      public void close() throws IOException {
         try {
            super.close();
         }
         finally {
            lock.unlock();
         }
      }

      private final BlobLock lock;
   }

   private static final class LockBlobChannel implements BlobChannel {
      public LockBlobChannel(BlobChannel channel, BlobLock lock) {
         this.channel = channel;
         this.lock = lock;
      }

      @Override
      public ByteBuffer map(long pos, long size) throws IOException {
         return channel.map(pos, size);
      }

      @Override
      public void unmap(ByteBuffer buf) throws IOException {
         channel.unmap(buf);
      }

      @Override
      public int read(ByteBuffer dst) throws IOException {
         return channel.read(dst);
      }

      @Override
      public int write(ByteBuffer src) throws IOException {
         return channel.write(src);
      }

      @Override
      public long position() throws IOException {
         return channel.position();
      }

      @Override
      public SeekableByteChannel position(long newPosition) throws IOException {
         return channel.position(newPosition);
      }

      @Override
      public long size() throws IOException {
         return channel.size();
      }

      @Override
      public SeekableByteChannel truncate(long size) throws IOException {
         return channel.truncate(size);
      }

      @Override
      public boolean isOpen() {
         return channel.isOpen();
      }

      @Override
      public void close() throws IOException {
         try {
            channel.close();
         }
         finally {
            lock.unlock();
         }
      }

      private final BlobChannel channel;
      private final BlobLock lock;
   }

   public static final class Reference extends SingletonManager.Reference<BlobStorage<?>> {
      @Override
      public  BlobStorage<?> get(Object... parameters) {
         if(parameters.length < 2 || parameters.length > 3) {
            return null;
         }

         if(storages == null) {
            storages = new HashMap<>();
         }

         String storeID = (String) parameters[0];
         boolean preload = (Boolean) parameters[1];
         BlobStorage<?> storage = (BlobStorage<?>) storages.get(storeID);

         if(storage == null || storage.isClosed()) {
            try {
               storage = BlobStorage.createBlobStorage(storeID, preload);
               storages.put(storeID, storage);

               if(parameters.length == 3) {
                  Listener listener = (Listener) parameters[2];
                  storage.addListener(listener);
               }
            }
            catch(IOException e) {
               LOG.error("Failed to create blob storage with storeID " + storeID, e);
            }
         }

         return storage;
      }

      @Override
      public void dispose() {
         if(storages != null) {
            for(String storeID : storages.keySet()) {
               try {
                  BlobStorage<?> storage = storages.get(storeID);

                  if(!storage.isClosed()) {
                     storage.close();
                  }
               }
               catch(Exception e) {
                  LOG.error("Failed to close storage with storeID " + storeID, e);
               }
            }

            storages = null;
         }
      }

      private HashMap<String, BlobStorage<?>> storages;
      private final Logger LOG = LoggerFactory.getLogger(Reference.class);
   }
}
