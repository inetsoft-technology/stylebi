/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.storage.fs;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.Striped;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.*;
import inetsoft.util.Tool;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * {@code FilesystemKeyValueEngine} is an implementation of {@link KeyValueEngine} that stores the
 * key-value maps in JSON files in a directory on a locally mounted filesystem. This is intended to
 * be used with a network filesystem.
 */
public class FilesystemKeyValueEngine implements KeyValueEngine {
   /**
    * Creates a new instance of {@code FilesystemKeyValueEngine}.
    *
    * @param base the directory where the JSON files will be stored.
    */
   public FilesystemKeyValueEngine(Path base) {
      this(base, true);
   }

   FilesystemKeyValueEngine(Path base, boolean watchFiles) {
      Objects.requireNonNull(base, "The base directory path cannot be null");
      this.base = base;

      try {
         Files.createDirectories(base);
      }
      catch(IOException e) {
         throw new RuntimeException("Failed to create base directory", e);
      }

      if(watchFiles) {
         try {
            watchExecutor = Executors.newSingleThreadExecutor();
            watchService = base.getFileSystem().newWatchService();
            base.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
            watchExecutor.submit(this::watchFiles);
         }
         catch(IOException e) {
            throw new RuntimeException("Failed to watch base directory", e);
         }
      }
      else {
         watchExecutor = null;
         watchService = null;
      }
   }

   @Override
   public boolean contains(String id, String key) {
      return readFile(id, m -> getValue(key, m).isPresent());
   }

   @SuppressWarnings("unchecked")
   @Override
   public <T> T get(String id, String key) {
      return readFile(id, m -> (T) getValue(key, m).orElse(null));
   }

   @SuppressWarnings("unchecked")
   @Override
   public <T> T put(String id, String key, T value) {
      ValueObject wrapper = new ValueObject();
      wrapper.setValue(value);
      return (T) writeFile(id, m -> getValue(m.put(key, wrapper))).orElse(null);
   }

   @SuppressWarnings("unchecked")
   @Override
   public <T> T remove(String id, String key) {
      return (T) writeFile(id, m -> getValue(m.remove(key))).orElse(null);
   }

   @SuppressWarnings("unchecked")
   @Override
   public void deleteStorage(String id) {
      Lock lock = locks.get(id);
      lock.lock();

      Path path = base.resolve(id + ".json");
      File storageFile = path.toFile();
      clearCache(path);
      timestamps.remove(path);

      if(storageFile.exists()) {
         storageFile.delete();
      }

      lock.unlock();
   }

   @Override
   public <T> Stream<KeyValuePair<T>> stream(String id) {
      return readFile(id, m -> m.entrySet().stream().map(this::createPair));
   }

   @Override
   public Stream<String> idStream() {
      File[] files = base.toFile().listFiles((dir, name) -> name.endsWith(".json"));

      if(files != null) {
         return Arrays.stream(files)
            .map(f -> f.getName().substring(0, f.getName().length() - 5));
      }

      return Stream.empty();
   }

   @Override
   public void close() throws Exception {
      if(watchExecutor != null) {
         watchExecutor.shutdown();
      }

      if(watchService != null) {
         watchService.close();
      }
   }

   @SuppressWarnings("UnstableApiUsage")
   private <T> T readFile(String id, Function<Map<String, ValueObject>, T> fn) {
      Lock lock = locks.get(id);
      lock.lock();

      try {
         Path path = base.resolve(id + ".json");
         timestamps.put(path, getLastModified(path));
         return fn.apply(readFile(path));
      }
      finally {
         lock.unlock();
      }
   }

   @SuppressWarnings("UnstableApiUsage")
   private <T> T writeFile(String id, Function<Map<String, ValueObject>, T> fn) {
      Lock lock = locks.get(id);
      lock.lock();

      try {
         Path path = base.resolve(id + ".json");
         Map<String, ValueObject> values = readFile(path);
         T result = fn.apply(values);
         writeFile(path, values);
         timestamps.put(path, getLastModified(path));
         return result;
      }
      finally {
         lock.unlock();
      }
   }

   private Map<String, ValueObject> readFile(Path path) {
      //try to get the cache.
      if(cache.get(path.toString()) != null) {
         return cache.get(path.toString());
      }

      Map<String, ValueObject> map = new HashMap<>();

      if(path.toFile().exists()) {
         try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(path.toFile());

            for(Iterator<String> i = root.fieldNames(); i.hasNext();) {
               String name = i.next();
               ValueObject value = objectMapper.convertValue(root.get(name), ValueObject.class);
               map.put(name, value);
            }

            if(map.size() > 0) {
               cache.put(path.toString(), map);
            }
         }
         catch(Exception e) {
            throw new RuntimeException(
               "Failed to read JSON file " + path.toFile().getAbsolutePath(), e);
         }
      }

      return map;
   }

   private void writeFile(Path path, Map<String, ValueObject> values) {
      Path temp =
         new File(path.toFile().getParentFile(), path.toFile().getName() + ".tmp").toPath();

      try {
         objectMapper.writeValue(temp.toFile(), values);
         Files.move(
            temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      }
      catch(Exception e) {
         throw new RuntimeException(
            "Failed to write JSON file " + path.toFile().getAbsolutePath(), e);
      }
      finally {
         Tool.deleteFile(temp.toFile());
      }
   }

   @SuppressWarnings("unchecked")
   private <T> Optional<T> getValue(String key, Map<String, ValueObject> values) {
      return Optional.ofNullable(values.get(key)).map(v -> (T) v.getValue());
   }

   @SuppressWarnings("unchecked")
   private <T> Optional<T> getValue(ValueObject value) {
      return Optional.ofNullable(value).map(v -> (T) v.getValue());
   }

   @SuppressWarnings("unchecked")
   private <T> KeyValuePair<T> createPair(Map.Entry<String, ValueObject> entry) {
      return new KeyValuePair<>(entry.getKey(), (T) entry.getValue().getValue());
   }

   private long getLastModified(Path path) {
      if(path.toFile().exists()) {
         return path.toFile().lastModified();
      }

      return 0L;
   }

   private void watchFiles() {
      try {
         WatchKey key;

         while((key = watchService.take()) != null) {
            for(WatchEvent<?> event : key.pollEvents()) {
               if(event.kind() != OVERFLOW) {
                  Path file = (Path) event.context();
                  file = base.resolve(file);
                  String filename = file.toFile().getName();

                  if(filename.endsWith(".json")) {
                     String id = filename.substring(0, filename.length() - 5);
                     Cluster cluster = Cluster.getInstance();
                     String owner = cluster.getServiceOwner(id);

                     if(cluster.getLocalMember().equals(owner)) {
                        handleFileChange(file, id, cluster);
                     }
                     else {
                        // clear the cache if others member change the file.
                        clearCache(file);
                     }
                  }
               }
            }

            key.reset();
         }
      }
      catch(InterruptedException e) {
         LoggerFactory.getLogger(getClass()).warn("Interrupted while watching files", e);
      }
   }

   @SuppressWarnings({ "UnstableApiUsage", "rawtypes" })
   private void handleFileChange(Path file, String id, Cluster cluster) {
      Lock lock = locks.get(id);
      lock.lock();

      try {
         Long timestamp = timestamps.get(file);
         long lastModified = getLastModified(file);

         if(timestamp != null && lastModified > timestamp) {
            cluster.submit(id, new LoadKeyValueTask(id, true));
         }
      }
      finally {
         lock.unlock();
      }
   }

   private void clearCache(Path path) {
      Long timestamp = timestamps.get(path);
      long lastModified = getLastModified(path);

      if(timestamp != null && lastModified > timestamp) {
         cache.remove(path.toString());
      }
   }

   private final Path base;
   @SuppressWarnings("UnstableApiUsage")
   private final Striped<Lock> locks = Striped.lazyWeakLock(1);
   private final ObjectMapper objectMapper = KeyValueEngine.createObjectMapper();
   private final WatchService watchService;
   private final ExecutorService watchExecutor;
   private final ConcurrentMap<Path, Long> timestamps = new ConcurrentHashMap<>();
   private final Map<String, Map<String, ValueObject>> cache = new ConcurrentHashMap<>();

   public static final class ValueObject {
      @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
      public Object getValue() {
         return value;
      }

      public void setValue(Object value) {
         this.value = value;
      }

      private Object value;
   }
}
