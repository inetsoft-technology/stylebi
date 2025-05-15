/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.report.composition;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.util.Tool;
import inetsoft.web.json.ThirdPartySupportModule;
import org.apache.ignite.IgniteCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import javax.cache.Cache;
import java.io.*;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RuntimeSheetCache
   implements Map<String, RuntimeSheet>, Closeable, CacheRebalanceListener
{
   public RuntimeSheetCache(String name) {
      this.cluster = Cluster.getInstance();
      this.local = new LinkedHashMap<>();
      this.cache = getCache(cluster, name);
      this.maxSheetCount = getMaxSheetCount();
      this.lock = new ReentrantReadWriteLock();
      this.executor = Executors.newSingleThreadScheduledExecutor();
      this.mapper = createObjectMapper();

      this.cluster.addCacheRebalanceListener(name, this);
      this.executor.schedule(this::flushAll, 30L, TimeUnit.SECONDS);
   }

   private static int getMaxSheetCount() {
      String property = SreeEnv.getProperty("asset.worksheet.max");
      int max = 500;

      try {
         max = Integer.parseInt(property);
      }
      catch(Exception ex) {
         LOG.warn(
            "Invalid value for maximum number of open worksheets (asset.worksheet.max): {}",
            property, ex);
      }

      return max;
   }

   @SuppressWarnings("unchecked")
   private static IgniteCache<String, RuntimeSheetState> getCache(Cluster cluster, String name) {
      Cache<String, RuntimeSheetState> cache = cluster.getCache(name);
      return cache.unwrap(IgniteCache.class);
   }

   private static ObjectMapper createObjectMapper() {
      StreamReadConstraints defaults = StreamReadConstraints.defaults();
      JsonFactory jsonFactory = new MappingJsonFactory();
      jsonFactory.setStreamReadConstraints(StreamReadConstraints.builder()
                                              .maxDocumentLength(defaults.getMaxDocumentLength())
                                              .maxNameLength(defaults.getMaxNameLength())
                                              .maxNumberLength(defaults.getMaxNumberLength())
                                              .maxNestingDepth(defaults.getMaxNestingDepth())
                                              .maxStringLength(1073741824)
                                              .build());
      ObjectMapper mapper = new ObjectMapper(jsonFactory);
      mapper.registerModule(new Jdk8Module());
      mapper.registerModule(new JavaTimeModule());
      mapper.registerModule(new ThirdPartySupportModule());
      mapper.registerModule(new GuavaModule());
      mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
      return mapper;
   }

   @Override
   public int size() {
      return local.size();
   }

   @Override
   public boolean isEmpty() {
      return local.isEmpty();
   }

   @Override
   public boolean containsKey(Object key) {
      return local.containsKey(key) || key instanceof String id && cache.containsKey(id);
   }

   @Override
   public boolean containsValue(Object value) {
      return local.containsValue(value);
   }

   @Override
   public RuntimeSheet get(Object key) {
      if(key instanceof String id) {
         boolean loadFromCache = false;
         lock.readLock().lock();

         try {
            if(local.containsKey(id)) {
               return local.get(id);
            }

            loadFromCache = cache.containsKey(id);
         }
         finally {
            lock.readLock().unlock();
         }

         if(loadFromCache) {
            lock.writeLock().lock();

            try {
               if(!local.containsKey(id) && cache.containsKey(id)) {
                  RuntimeSheet sheet = toSheet(cache.get(id));

                  if(sheet != null) {
                     local.put(id, sheet);
                  }

                  return sheet;
               }
            }
            finally {
               lock.writeLock().unlock();
            }
         }
      }

      return null;
   }

   @Override
   public RuntimeSheet put(String key, RuntimeSheet value) {
      lock.writeLock().lock();

      try {
         if(applyMaxCount && local.size() >= maxSheetCount && !local.containsKey(key)) {
            for(Iterator<String> i = local.keySet().iterator(); i.hasNext(); ) {
               String id = i.next();

               if(id.startsWith(WorksheetService.PREVIEW_PREFIX)) {
                  i.remove();
                  cache.removeAsync(id);
                  break;
               }
            }
         }

         RuntimeSheet sheet = local.put(key, value);
         cache.putAsync(key, value.saveState(mapper));
         return sheet;
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   @Override
   public RuntimeSheet remove(Object key) {
      lock.writeLock().lock();

      try {
         RuntimeSheet sheet = local.remove(key);

         if(key instanceof String id) {
            if(sheet == null) {
               sheet = toSheet(cache.getAndRemove(id));
            }
            else {
               cache.removeAsync(id);
            }
         }

         return sheet;
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   @Override
   public void putAll(Map<? extends String, ? extends RuntimeSheet> m) {
      Map<String, RuntimeSheetState> states = new HashMap<>();

      for(Map.Entry<? extends String, ? extends RuntimeSheet> e : m.entrySet()) {
         states.put(e.getKey(), e.getValue().saveState(mapper));
      }

      lock.writeLock().lock();

      try {
         local.putAll(m);
         cache.putAllAsync(states);
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   @Override
   public void clear() {
      lock.writeLock().lock();

      try {
         Set<String> ids = getLocalKeys();
         local.clear();
         cache.removeAllAsync(ids);
      }
      finally {
         lock.readLock().lock();
      }
   }

   @Override
   public Set<String> keySet() {
      return new KeySet();
   }

   @Override
   public Collection<RuntimeSheet> values() {
      return new ValueCollection();
   }

   @Override
   public Set<Entry<String, RuntimeSheet>> entrySet() {
      return new EntrySet();
   }

   @Override
   public void close() throws IOException {
      cluster.removeCacheRebalanceListener(cache.getName(), this);
      executor.close();
   }

   public boolean isApplyMaxCount() {
      return applyMaxCount;
   }

   public void setApplyMaxCount(boolean applyMaxCount) {
      this.applyMaxCount = applyMaxCount;
   }

   // this should be called in a locked code section
   private Set<String> getLocalKeys() {
      Set<String> allIds = new HashSet<>(local.keySet());

      for(Cache.Entry<String, RuntimeSheetState> e : cache) {
         allIds.add(e.getKey());
      }

      return Set.copyOf(cluster.getLocalCacheKeys(cache, allIds));
   }

   public void flush(String key) {
      lock.writeLock().lock();

      try {
         RuntimeSheet sheet = local.get(key);

         if(sheet == null) {
            cache.removeAsync(key);
         }
         else {
            cache.putAsync(key, sheet.saveState(mapper));
         }
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   private void flushAll() {
      lock.readLock().lock();

      try {
         Map<String, RuntimeSheetState> changeset = new HashMap<>();

         for(String id : getLocalKeys()) {
            RuntimeSheet sheet = local.get(id);

            if(sheet != null) {
               changeset.put(id, sheet.saveState(mapper));
            }
         }

         cache.putAllAsync(changeset);
      }
      finally {
         lock.readLock().unlock();
      }
   }

   @Override
   public void cacheRebalanced(CacheRebalanceEvent event) {
      lock.writeLock().lock();

      try {
         Set<String> keys = getLocalKeys();
         local.keySet().removeIf(k -> !keys.contains(k));

         for(String key : keys) {
            if(!local.containsKey(key) && cache.containsKey(key)) {
               local.put(key, toSheet(cache.get(key)));
            }
         }
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   private RuntimeSheet toSheet(RuntimeSheetState state) {
      RuntimeSheet sheet = null;

      if(state instanceof RuntimeViewsheetState vsState) {
         sheet = new RuntimeViewsheet(vsState, mapper);
      }
      else if(state instanceof RuntimeWorksheetState wsState) {
         sheet = new RuntimeWorksheet(wsState, mapper);
      }

      return sheet;
   }

   public List<String> getAllIds(Principal user) {
      Set<String> ids = new HashSet<>();

      for(Iterator<Cache.Entry<String, RuntimeSheetState>> i = cache.iterator(); i.hasNext(); ) {
         Cache.Entry<String, RuntimeSheetState> e = i.next();

         if(user == null) {
            ids.add(e.getKey());
         }
         else if(e.getValue().getUser() != null) {
            try {
               Document document = Tool.parseXML(new StringReader(e.getValue().getUser()));
               SRPrincipal principal = new SRPrincipal();
               principal.parseXML(document.getDocumentElement());

               if(Objects.equals(principal, user)) {
                  ids.add(e.getKey());
               }
            }
            catch(Exception ex) {
               LOG.error("Failed to parse principal", ex);
            }
         }
      }

      return List.copyOf(ids);
   }

   private final Cluster cluster;
   private final Map<String, RuntimeSheet> local;
   private final IgniteCache<String, RuntimeSheetState> cache;
   private final int maxSheetCount;
   private final ReadWriteLock lock;
   private final ScheduledExecutorService executor;
   private final ObjectMapper mapper;
   private boolean applyMaxCount;

   private static final Logger LOG = LoggerFactory.getLogger(RuntimeSheetCache.class);

   private abstract class CacheIterator<T> implements Iterator<T> {
      protected abstract T map(Map.Entry<String, RuntimeSheet> entry);

      @Override
      public boolean hasNext() {
         return iterator.hasNext();
      }

      @Override
      public T next() {
         current = iterator.next();
         return map(current);
      }

      @Override
      public void remove() {
         if(current == null) {
            throw new IllegalStateException();
         }

         RuntimeSheetCache.this.remove(current.getKey());
         current = null;
      }

      private final Iterator<Map.Entry<String, RuntimeSheet>> iterator = local.entrySet().iterator();
      private Map.Entry<String, RuntimeSheet> current = null;
   }

   private abstract class CacheSet<T> extends AbstractSet<T> {
      @Override
      public int size() {
         return local.size();
      }
   }

   private final class EntryIterator extends CacheIterator<Map.Entry<String, RuntimeSheet>> {
      @Override
      protected Entry<String, RuntimeSheet> map(Entry<String, RuntimeSheet> entry) {
         return entry;
      }
   }

   private final class EntrySet extends CacheSet<Map.Entry<String, RuntimeSheet>> {
      @Override
      public Iterator<Entry<String, RuntimeSheet>> iterator() {
         return new EntryIterator();
      }
   }

   private final class KeyIterator extends CacheIterator<String> {
      @Override
      protected String map(Entry<String, RuntimeSheet> entry) {
         return entry.getKey();
      }
   }

   private final class KeySet extends CacheSet<String> {
      @Override
      public Iterator<String> iterator() {
         return new KeyIterator();
      }
   }

   private final class ValueCollection extends CacheSet<RuntimeSheet> {
      @Override
      public Iterator<RuntimeSheet> iterator() {
         return new ValueIterator();
      }
   }

   private final class ValueIterator extends CacheIterator<RuntimeSheet> {
      @Override
      protected RuntimeSheet map(Entry<String, RuntimeSheet> entry) {
         return entry.getValue();
      }
   }
}
