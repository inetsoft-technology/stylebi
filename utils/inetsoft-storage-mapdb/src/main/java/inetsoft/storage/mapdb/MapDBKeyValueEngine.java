/*
 * This file is part of StyleBI.
 *
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
 */
package inetsoft.storage.mapdb;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.KeyValueEngine;
import inetsoft.storage.KeyValuePair;
import inetsoft.util.Debouncer;
import inetsoft.util.DefaultDebouncer;
import org.mapdb.*;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@code MapDBKeyValueEngine} is an implementation of {@link KeyValueEngine} that is backed by a
 * MapDB database.
 */
public class MapDBKeyValueEngine implements KeyValueEngine {
   /**
    * Creates a new instance of {@code MapDBKeyValueEngine}.
    *
    * @param directory the path to the database directory.
    */
   public MapDBKeyValueEngine(Path directory) {
      Objects.requireNonNull(directory, "The database directory cannot be null");

      try {
         Files.createDirectories(directory);
      }
      catch(IOException e) {
         throw new RuntimeException("Failed to create database directory", e);
      }

      this.directory = directory;
      cacheExecutor = Executors.newSingleThreadScheduledExecutor();
      cacheExecutor.scheduleWithFixedDelay(this::flushDatabases, 1L, 1L, TimeUnit.MINUTES);
   }

   @Override
   public void close() {
      closed = true;

      try {
         commitDebouncer.close();
      }
      catch(Exception e) {
         LoggerFactory.getLogger(getClass()).warn("Failed to stop commit debouncer", e);
      }

      synchronized(dbCache) {
         for(Iterator<CacheEntry> i = dbCache.values().iterator(); i.hasNext();) {
            CacheEntry entry = i.next();

            synchronized(entry) {
               try {
                  closeDB(entry.db);
               }
               catch(Exception e) {
                  LoggerFactory.getLogger(MapDBKeyValueEngine.class)
                     .error("Failed to close database", e);
               }
               finally {
                  i.remove();
               }
            }
         }
      }

      if(cacheExecutor != null) {
         cacheExecutor.shutdown();
      }
   }

   @Override
   public boolean contains(String id, String key) {
      return read(id, map -> map.containsKey(key));
   }

   @Override
   public <T> T get(String id, String key) {
      return read(id, (ConcurrentMap<String, T> map) -> map.get(key));
   }

   @Override
   public <T> T put(String id, String key, T value) {
      return write(id, (ConcurrentMap<String, T> map) -> map.put(key, value));
   }

   @Override
   public <T> T remove(String id, String key) {
      return write(id, (ConcurrentMap<String, T> map) -> map.remove(key));
   }

   @Override
   public void deleteStorage(String id) {
      synchronized(dbCache) {
         CacheEntry entry = getDatabase(id);
         dbCache.remove(id);
         entry.db.close();
         File dbFile = directory.resolve(id + ".db").toFile();
         File tempDbFile = directory.resolve(id + ".db.wal.0").toFile();

         if(dbFile.exists()) {
            dbFile.delete();
         }

         if(tempDbFile.exists()) {
            tempDbFile.delete();
         }
      }
   }

   @Override
   public <T> Stream<KeyValuePair<T>> stream(String id) {
      return read(id, (ConcurrentMap<String, T> map) -> map.entrySet()
         .stream()
         .map(this::createPair)
         .collect(Collectors.toList()))
         .stream();
   }

   @Override
   public Stream<String> idStream() {
      List<String> ids = new ArrayList<>();
      File[] files = directory.toFile().listFiles();

      if(files != null) {
         for(File file : files) {
            if(file.isFile() && file.getName().endsWith(".db")) {
               ids.add(file.getName().substring(0, file.getName().length() - 3));
            }
         }
      }

      return ids.stream();
   }

   @Override
   public <T> void putAll(String id, Map<String, T> keyValueMap) {
      updateAll(id, keyValueMap, null);
   }

   @Override
   public void removeAll(String id, Set<String> keys) {
      updateAll(id, null, keys);
   }

   private <T> void updateAll(String id, Map<String, T> keyValueMap, Set<String> keys) {
      if(closed) {
         throw new IllegalStateException("Key value engine is closed");
      }

      CacheEntry entry = getDatabase(id);

      synchronized(entry) {
         ConcurrentMap<String, Object> map = getMap(id, entry.db);

         if(keys == null && keyValueMap != null) {
            map.putAll(keyValueMap);
         }
         else if(keys != null) {
            for(String key : keys) {
               map.remove(key);
            }
         }

         commitDebouncer.debounce(id, 1L, TimeUnit.SECONDS, new CommitTask(id));
      }
   }

   private CacheEntry openDB(String id) {
      try {
         DB db = DBMaker
            .fileDB(directory.resolve(id + ".db").toFile())
            .transactionEnable()
            .fileChannelEnable()
            .closeOnJvmShutdown()
            .make();
         return new CacheEntry(db);
      }
      catch(DBException.FileLocked e) {
         Cluster cluster = Cluster.getInstance();

         LoggerFactory.getLogger(getClass()).error(
            "Concurrent access to MapDB {} attempted in cluster from member {}",
            id, cluster.getLocalMember(), e);

         throw e;
      }
   }

   private void closeDB(DB db) {
      if(db != null && !db.isClosed()) {
         try(db) {
            db.commit();
            db.getStore().compact();
         }
      }
   }

   private <T, R> R read(String id, Function<ConcurrentMap<String, T>, R> fn) {
      if(closed) {
         throw new IllegalStateException("Key value engine is closed");
      }

      CacheEntry entry = getDatabase(id);

      synchronized(entry) {
         return fn.apply(getMap(id, entry.db));
      }
   }

   private <T, R> R write(String id, Function<ConcurrentMap<String, T>, R> fn) {
      if(closed) {
         throw new IllegalStateException("Key value engine is closed");
      }

      CacheEntry entry = getDatabase(id);

      synchronized(entry) {
         R result = fn.apply(getMap(id, entry.db));
         commitDebouncer.debounce(id, 1L, TimeUnit.SECONDS, new CommitTask(id));
         return result;
      }
   }

   private CacheEntry getDatabase(String id) {
      synchronized(dbCache) {
         CacheEntry entry = dbCache.computeIfAbsent(id, this::openDB);
         entry.lastAccess = Instant.now();
         return entry;
      }
   }

   private void flushDatabases() {
      synchronized(dbCache) {
         Instant now = Instant.now();

         for(Iterator<CacheEntry> i = dbCache.values().iterator(); i.hasNext();) {
            CacheEntry entry = i.next();

            synchronized(entry) {
               if(now.isAfter(entry.lastAccess.plus(1L, ChronoUnit.MINUTES))) {
                  try {
                     closeDB(entry.db);
                  }
                  catch(Exception e) {
                     LoggerFactory.getLogger(MapDBKeyValueEngine.class)
                        .error("Failed to close database", e);
                  }
                  finally {
                     i.remove();
                  }
               }
            }
         }
      }
   }

   @SuppressWarnings("unchecked")
   private <T> ConcurrentMap<String, T> getMap(String id, DB db) {
      return db.hashMap(id, Serializer.STRING, Serializer.ELSA).createOrOpen();
   }

   private <T> KeyValuePair<T> createPair(Map.Entry<String, T> entry) {
      return new KeyValuePair<>(entry.getKey(), entry.getValue());
   }

   private final Path directory;
   private final ScheduledExecutorService cacheExecutor;
   private final Map<String, CacheEntry> dbCache = new HashMap<>();
   private final Debouncer<String> commitDebouncer = new DefaultDebouncer<>(false, Thread::new);
   private volatile boolean closed = false;

   private static final class CacheEntry {
      CacheEntry(DB db) {
         this.db = db;
         this.lastAccess = Instant.now();
      }

      private final DB db;
      private Instant lastAccess;
   }

   private final class CommitTask implements Runnable {
      CommitTask(String id) {
         this.id = id;
      }

      @Override
      public void run() {
         try {
            CacheEntry entry;

            synchronized(dbCache) {
               entry = dbCache.get(id);
            }

            if(entry != null) {
               synchronized(entry) {
                  entry.db.commit();
               }
            }
         }
         catch(Exception e) {
            LoggerFactory.getLogger(MapDBKeyValueEngine.class)
               .error("Failed to commit database", e);
         }
      }

      private final String id;
   }
}
