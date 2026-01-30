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
import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.util.Tool;
import inetsoft.web.json.ThirdPartySupportModule;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.affinity.AffinityKey;
import org.apache.ignite.lang.IgniteFuture;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RuntimeSheetCache
   implements Map<String, RuntimeSheet>, Closeable
{
   public RuntimeSheetCache(String name) {
      this.cluster = Cluster.getInstance();
      this.local = new LinkedHashMap<>();
      this.cache = getCache(cluster, name);
      this.maxSheetCount = getMaxSheetCount();
      this.executor = Executors.newSingleThreadScheduledExecutor();
      this.mapper = createObjectMapper();
      this.sheetCountMap = cluster.getReplicatedMap(LOCAL_SHEET_COUNT);

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
   private static IgniteCache<AffinityKey<String>, RuntimeSheetState> getCache(Cluster cluster, String name) {
      Cache<String, RuntimeSheetState> cache = cluster.getCache(name);
      return cache.unwrap(IgniteCache.class);
   }

   protected static ObjectMapper createObjectMapper() {
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
      return local.containsKey(key) ||
         key instanceof String id && cache.containsKey(getAffinityKey(id));
   }

   @Override
   public boolean containsValue(Object value) {
      return local.containsValue(value);
   }

   @Override
   public RuntimeSheet get(Object key) {
      if(key instanceof String id) {
         AffinityKey<String> affinityKey = getAffinityKey(id);
         boolean loadFromCache;
         lock.readLock().lock();

         try {
            if(local.containsKey(id)) {
               return local.get(id);
            }

            loadFromCache = cache.containsKey(affinityKey);
         }
         finally {
            lock.readLock().unlock();
         }

         if(loadFromCache) {
            lock.writeLock().lock();

            try {
               RuntimeSheet sheet = toSheet(cache.get(affinityKey));

               if(sheet != null) {
                  if(!isLocal(affinityKey)) {
                     LOG.error(
                        "Loading remote runtime sheet that does not belong to this instance: {}",
                        id, new Exception("Stack trace"));
                  }

                  putLocal(affinityKey, sheet);
               }

               return sheet;
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
         AffinityKey<String> affinityKey = getAffinityKey(key);
         RuntimeSheet sheet = putLocal(affinityKey, value);
         putCache(affinityKey, value);
         return sheet;
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   public Future<RuntimeSheet> putSheet(String key, RuntimeSheet value) {
      lock.writeLock().lock();

      try {
         AffinityKey<String> affinityKey = getAffinityKey(key);
         putLocal(affinityKey, value);
         return putCache(affinityKey, value);
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   private RuntimeSheet putLocal(AffinityKey<String> key, RuntimeSheet value) {
      if(applyMaxCount && local.size() >= maxSheetCount && !local.containsKey(key.key())) {
         for(Iterator<String> i = local.keySet().iterator(); i.hasNext();) {
            String id = i.next();

            if(id.startsWith(WorksheetService.PREVIEW_PREFIX)) {
               i.remove();
               cache.removeAsync(getAffinityKey(id));
               break;
            }
         }
      }

      if(!isLocal(key)) {
         LOG.error(
            "Added remote runtime sheet that does not belong to this instance: {}",
            key, new Exception("Stack trace"));
      }

      RuntimeSheet result = local.put(key.key(), value);
      updateLocalSheetCount();
      return result;
   }

   private Future<RuntimeSheet> putCache(AffinityKey<String> key, RuntimeSheet value) {
      try {
         IgniteFuture<Void> igniteFuture = cache.putAsync(key, value.saveState(mapper));
         CompletableFuture<RuntimeSheet> future = new CompletableFuture<>();

         igniteFuture.listen(f -> {
            try {
               f.get();
               future.complete(value);
            }
            catch (Exception ex) {
               LOG.warn("Failed to save sheet state to cache", ex);
               future.completeExceptionally(ex);
            }
         });

         return future;
      }
      catch(Exception e) {
         LOG.warn("Failed to save sheet state to cache", e);
         CompletableFuture<RuntimeSheet> failed = new CompletableFuture<>();
         failed.completeExceptionally(e);

         return failed;
      }
   }

   @Override
   public RuntimeSheet remove(Object key) {
      lock.writeLock().lock();

      try {
         RuntimeSheet sheet = local.remove(key);
         updateLocalSheetCount();

         if(key instanceof String id) {
            AffinityKey<String> affinityKey = getAffinityKey(id);

            if(sheet == null) {
               sheet = toSheet(cache.getAndRemove(affinityKey));
            }
            else {
               cache.removeAsync(affinityKey);
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
      Map<AffinityKey<String>, RuntimeSheetState> states = new HashMap<>();

      for(Map.Entry<? extends String, ? extends RuntimeSheet> e : m.entrySet()) {
         states.put(getAffinityKey(e.getKey()), e.getValue().saveState(mapper));
      }

      lock.writeLock().lock();

      try {
         local.putAll(m);
         updateLocalSheetCount();
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
         Set<AffinityKey<String>> ids = getLocalKeys();
         local.clear();
         updateLocalSheetCount();
         cache.removeAllAsync(ids);
      }
      finally {
         lock.writeLock().unlock();
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
      executor.close();
      sheetCountMap.remove(cluster.getLocalMember());
   }

   public boolean isApplyMaxCount() {
      return applyMaxCount;
   }

   public void setApplyMaxCount(boolean applyMaxCount) {
      this.applyMaxCount = applyMaxCount;
   }

   // this should be called in a locked code section
   private Set<AffinityKey<String>> getLocalKeys() {
      Set<AffinityKey<String>> allIds = new HashSet<>();

      local.keySet().stream()
         .map(this::getAffinityKey)
         .forEach(allIds::add);

      Iterator<Cache.Entry<AffinityKey<String>, RuntimeSheetState>> iter = cache.iterator();

      try {
         while(iter.hasNext()) {
            allIds.add(iter.next().getKey());
         }
      }
      finally {
         Tool.closeIterator(iter);
      }

      return Set.copyOf(cluster.getLocalCacheKeys(cache, allIds));
   }

   public void flush(String key) {
      lock.writeLock().lock();

      try {
         RuntimeSheet sheet = local.get(key);
         AffinityKey<String> affinityKey = getAffinityKey(key);

         if(sheet == null) {
            cache.removeAsync(affinityKey);
         }
         else {
            cache.putAsync(affinityKey, sheet.saveState(mapper));
         }
      }
      finally {
         lock.writeLock().unlock();
      }
   }

   private void flushAll() {
      lock.writeLock().lock();

      try {
         Map<AffinityKey<String>, RuntimeSheetState> changeset = new HashMap<>();

         for(AffinityKey<String> key : getLocalKeys()) {
            RuntimeSheet sheet = local.get(key.key());

            if(sheet != null) {
               changeset.put(key, sheet.saveState(mapper));
            }
         }

         cache.putAllAsync(changeset);
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
      Iterator<Cache.Entry<AffinityKey<String>, RuntimeSheetState>> iter = cache.iterator();

      try {
         while(iter.hasNext()) {
            Cache.Entry<AffinityKey<String>, RuntimeSheetState> e = iter.next();

            if(user == null) {
               ids.add(e.getKey().key());
            }
            else if(e.getValue().getUser() != null) {
               try {
                  Document document = Tool.parseXML(new StringReader(e.getValue().getUser()));
                  SRPrincipal principal = new SRPrincipal();
                  principal.parseXML(document.getDocumentElement());

                  if(Objects.equals(principal, user)) {
                     ids.add(e.getKey().key());
                  }
               }
               catch(Exception ex) {
                  LOG.error("Failed to parse principal", ex);
               }
            }
         }
      }
      finally {
         Tool.closeIterator(iter);
      }

      return List.copyOf(ids);
   }

   public boolean isLocal(String id) {
      if(cluster.isLocalCall()) {
         return true;
      }

      AffinityKey<String> key = getAffinityKey(id);
      return cluster.isLocalCacheKey(cache.getName(), key);
   }

   public boolean isLocal(AffinityKey<String> key) {
      if(cluster.isLocalCall()) {
         return true;
      }

      return cluster.isLocalCacheKey(cache.getName(), key);
   }

   AffinityKey<String> getAffinityKey(String id) {
      return new AffinityKey<>(id, getOriginalId(id));
   }

   private void updateLocalSheetCount() {
      sheetCountMap.put(cluster.getLocalMember(), local.size());
   }

   static String getOriginalId(String id) {
      if(id == null) {
         return null;
      }

      Matcher matcher = tempIdPattern.matcher(id);

      if(matcher.matches()) {
         return matcher.group(2);
      }

      return id;
   }

   private final Cluster cluster;
   private final Map<String, RuntimeSheet> local;
   private final IgniteCache<AffinityKey<String>, RuntimeSheetState> cache;
   private final int maxSheetCount;
   private final ReadWriteLock lock = new ReentrantReadWriteLock();
   private final ScheduledExecutorService executor;
   private final ObjectMapper mapper;
   private boolean applyMaxCount;
   private static final Pattern tempIdPattern = Pattern.compile(
      "^(" + WorksheetEngine.PREVIEW_WORKSHEET + "|" + ViewsheetEngine.PREVIEW_VIEWSHEET +
      ")?(.+)-temp-\\d+$");
   private final Map<String, Integer> sheetCountMap;

   public static final String LOCAL_SHEET_COUNT = RuntimeSheet.class.getName() + ".localSheetCount";
   private static final Logger LOG = LoggerFactory.getLogger(RuntimeSheetCache.class);

   private abstract class CacheIterator<T> implements Iterator<T> {
      protected abstract T map(Map.Entry<String, RuntimeSheet> entry);

      public CacheIterator() {
         lock.readLock().lock();

         try {
            iterator = new ArrayList<>(local.entrySet()).iterator();
         }
         finally {
            lock.readLock().unlock();
         }
      }

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

      private final Iterator<Map.Entry<String, RuntimeSheet>> iterator;
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
