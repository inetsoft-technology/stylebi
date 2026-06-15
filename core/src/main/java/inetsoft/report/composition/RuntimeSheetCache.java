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
import inetsoft.sree.ClientInfo;
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
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdInputStream;

public class RuntimeSheetCache
   implements Map<String, RuntimeSheet>, Closeable
{
   public RuntimeSheetCache(Cluster cluster, String name) {
      this.cluster = cluster;
      this.local = new LinkedHashMap<>();
      this.cache = getCache(cluster, name);
      this.maxSheetCount = getMaxSheetCount();
      this.mapper = createObjectMapper();
      this.sheetCountMap = cluster.getReplicatedMap(LOCAL_SHEET_COUNT);
      this.accessTimeMap = getAccessTimeCache(cluster);
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
   private static IgniteCache<AffinityKey<String>, CompressedSheetState> getCache(Cluster cluster, String name) {
      Cache<String, CompressedSheetState> cache = cluster.getCache(name);
      return cache.unwrap(IgniteCache.class);
   }

   @SuppressWarnings("unchecked")
   private static IgniteCache<AffinityKey<String>, Long> getAccessTimeCache(Cluster cluster) {
      Cache<AffinityKey<String>, Long> cache = cluster.getCache(ACCESS_TIME_MAP_NAME);
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
         lock.readLock().lock();

         try {
            if(local.containsKey(id)) {
               return local.get(id);
            }
         }
         finally {
            lock.readLock().unlock();
         }

         // Use a single cache.get() outside the local lock instead of the two-step
         // containsKey()+get() pattern. This eliminates:
         //   1. The TOCTOU window between containsKey and get.
         //   2. False-negative containsKey results during Ignite DHT partition
         //      rebalancing (topology changes in AKS), where containsKey can return
         //      false for keys that are in the process of being migrated.
         //   3. Holding the local read lock across a potentially slow Ignite network
         //      call, which blocks all writers unnecessarily.
         CompressedSheetState state = cache.get(affinityKey);

         if(state != null) {
            RuntimeSheet sheet = null;
            String evictedId = null;
            lock.writeLock().lock();

            try {
               // Re-check local under the write lock: another thread may have already
               // loaded and cached this sheet between our cache.get() and lock acquire.
               if(local.containsKey(id)) {
                  return local.get(id);
               }

               sheet = toSheet(state);

               if(sheet != null) {
                  if(!isLocal(affinityKey)) {
                     LOG.error(
                        "Loading remote runtime sheet that does not belong to this instance: {}",
                        id, new Exception("Stack trace"));
                  }

                  evictedId = putLocal(affinityKey, sheet);
               }
            }
            finally {
               lock.writeLock().unlock();
            }

            evictFromIgnite(evictedId);

            // Restore last-accessed time from the distributed map outside the write lock.
            // accessTimeMap.get() is a distributed call and must not run under the lock.
            if(sheet != null) {
               Long accessTime = accessTimeMap.get(affinityKey);

               if(accessTime != null) {
                  lock.readLock().lock();

                  try {
                     // Only apply if the session is still resident — it may have been
                     // evicted between the write-lock release and now.
                     if(local.containsKey(id)) {
                        sheet.setAccessed(accessTime);
                     }
                  }
                  finally {
                     lock.readLock().unlock();
                  }
               }
            }

            return sheet;
         }
      }

      return null;
   }

   @Override
   public RuntimeSheet put(String key, RuntimeSheet value) {
      AffinityKey<String> affinityKey = getAffinityKey(key);
      CompressedSheetState compressed = null;

      try {
         compressed = compressState(value.saveState(mapper));
      }
      catch(Exception e) {
         LOG.warn("Failed to serialize sheet state to cache", e);
      }

      RuntimeSheet old;
      String evictedId;
      lock.writeLock().lock();

      try {
         old = local.get(key);
         evictedId = putLocal(affinityKey, value);

         if(compressed != null) {
            putCache(affinityKey, value, compressed);
         }
      }
      finally {
         lock.writeLock().unlock();
      }

      evictFromIgnite(evictedId);
      accessTimeMap.putAsync(affinityKey, value.getLastAccessed());
      return old;
   }

   public Future<RuntimeSheet> putSheet(String key, RuntimeSheet value) {
      AffinityKey<String> affinityKey = getAffinityKey(key);
      CompressedSheetState compressed = null;
      Exception serializeError = null;

      try {
         compressed = compressState(value.saveState(mapper));
      }
      catch(Exception e) {
         LOG.warn("Failed to serialize sheet state to cache", e);
         serializeError = e;
      }

      String evictedId;
      Future<RuntimeSheet> result;
      lock.writeLock().lock();

      try {
         evictedId = putLocal(affinityKey, value);

         if(compressed != null) {
            result = putCache(affinityKey, value, compressed);
         }
         else {
            CompletableFuture<RuntimeSheet> failed = new CompletableFuture<>();
            failed.completeExceptionally(serializeError != null ? serializeError
               : new IllegalStateException("Serialization produced null state without throwing"));
            result = failed;
         }
      }
      finally {
         lock.writeLock().unlock();
      }

      evictFromIgnite(evictedId);
      accessTimeMap.putAsync(affinityKey, value.getLastAccessed());
      return result;
   }

   /**
    * Inserts value into local. Returns the evicted preview-sheet ID if the max-count
    * limit forced an eviction, or null otherwise. Callers must call
    * cache.removeAsync() + accessTimeMap.remove() for the returned ID after releasing
    * the write lock — both are distributed operations that must not run under the lock.
    */
   private String putLocal(AffinityKey<String> key, RuntimeSheet value) {
      String evictedId = null;

      if(applyMaxCount && local.size() >= maxSheetCount && !local.containsKey(key.key())) {
         for(Iterator<String> i = local.keySet().iterator(); i.hasNext();) {
            String id = i.next();

            if(id.startsWith(WorksheetService.PREVIEW_PREFIX)) {
               i.remove();
               evictedId = id;
               break;
            }
         }
      }

      if(!isLocal(key)) {
         LOG.error(
            "Added remote runtime sheet that does not belong to this instance: {}",
            key, new Exception("Stack trace"));
      }

      local.put(key.key(), value);
      updateLocalSheetCount();
      return evictedId;
   }

   private void evictFromIgnite(String evictedId) {
      if(evictedId != null) {
         cache.removeAsync(getAffinityKey(evictedId));
         accessTimeMap.removeAsync(getAffinityKey(evictedId));
      }
   }

   private Future<RuntimeSheet> putCache(AffinityKey<String> key, RuntimeSheet value,
                                         CompressedSheetState compressed)
   {
      try {
         IgniteFuture<Void> igniteFuture = cache.putAsync(key, compressed);
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

   private CompressedSheetState compressState(RuntimeSheetState state) {
      try {
         CompressedSheetState.SheetType type = state instanceof RuntimeViewsheetState
            ? CompressedSheetState.SheetType.VIEWSHEET
            : CompressedSheetState.SheetType.WORKSHEET;

         byte[] json = mapper.writeValueAsBytes(state);
         byte[] compressed = compress(json);

         return new CompressedSheetState(compressed, type, state.getUser());
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to compress sheet state", e);
      }
   }

   private RuntimeSheetState decompressState(CompressedSheetState compressed) {
      try {
         byte[] json = decompress(compressed.getCompressedData());
         Class<? extends RuntimeSheetState> clazz =
            compressed.getType() == CompressedSheetState.SheetType.VIEWSHEET
               ? RuntimeViewsheetState.class
               : RuntimeWorksheetState.class;

         return mapper.readValue(json, clazz);
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to decompress sheet state", e);
      }
   }

   private static byte[] compress(byte[] data) {
      return Zstd.compress(data);
   }

   private static byte[] decompress(byte[] compressed) {
      long decompressedSize = Zstd.decompressedSize(compressed);

      if(decompressedSize > 0) {
         return Zstd.decompress(compressed, (int) decompressedSize);
      }
      else {
         // Fallback for data without size in frame header - use streaming decompressor
         try(ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
             ZstdInputStream zis = new ZstdInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream())
         {
            byte[] buffer = new byte[8192];
            int bytesRead;

            while((bytesRead = zis.read(buffer)) != -1) {
               baos.write(buffer, 0, bytesRead);
            }

            return baos.toByteArray();
         }
         catch(IOException e) {
            throw new RuntimeException("Failed to decompress data", e);
         }
      }
   }

   /**
    * Removes the session from both local memory and the distributed caches.
    * Note: only returns the sheet if it was resident in local memory at the time of removal.
    * If the session was held on a remote node, the return value is null. All current callers
    * discard the return value, so this is a documentation-only contract change from the
    * previous implementation which retrieved remote sessions via cache.getAndRemove().
    */
   @Override
   public RuntimeSheet remove(Object key) {
      AffinityKey<String> affinityKey = null;
      RuntimeSheet sheet;
      lock.writeLock().lock();

      try {
         sheet = local.remove(key);
         updateLocalSheetCount();

         if(key instanceof String id) {
            affinityKey = getAffinityKey(id);
         }
      }
      finally {
         lock.writeLock().unlock();
      }

      // Both Ignite calls are outside the lock — async, fire-and-forget
      if(affinityKey != null) {
         accessTimeMap.removeAsync(affinityKey);
         cache.removeAsync(affinityKey);
      }

      return sheet;
   }

   /**
    * Update the last-accessed timestamp for a session in the distributed map without
    * triggering a full sheet serialization. Called by accessSheet() on every getSheet() debounce.
    */
   public void updateAccessTime(String id, long time) {
      lock.readLock().lock();

      try {
         RuntimeSheet rs = local.get(id);

         if(rs == null) {
            return;
         }

         rs.setAccessed(time);
      }
      finally {
         lock.readLock().unlock();
      }

      // Residual race: remove() could complete between the lock release and putAsync(),
      // leaving a one-entry orphan in accessTimeMap. The orphan is bounded (8 bytes)
      // and is cleaned up by the next remove() or clear() for the same key.
      accessTimeMap.putAsync(getAffinityKey(id), time);
   }

   /**
    * Inserts all entries into local memory, the distributed session cache, and accessTimeMap.
    * This method has no active callers in the current codebase and exists only to satisfy
    * the Map interface contract.
    */
   @Override
   public void putAll(Map<? extends String, ? extends RuntimeSheet> m) {
      Map<AffinityKey<String>, CompressedSheetState> states =
         new TreeMap<>(Comparator.comparing(AffinityKey::key));

      Map<AffinityKey<String>, Long> accessTimes = new HashMap<>();

      for(Map.Entry<? extends String, ? extends RuntimeSheet> e : m.entrySet()) {
         AffinityKey<String> key = getAffinityKey(e.getKey());
         states.put(key, compressState(e.getValue().saveState(mapper)));
         accessTimes.put(key, e.getValue().getLastAccessed());
      }

      lock.writeLock().lock();

      try {
         local.putAll(m);
         updateLocalSheetCount();
      }
      finally {
         lock.writeLock().unlock();
      }

      cache.putAllAsync(states);
      accessTimeMap.putAllAsync(accessTimes);
   }

   @Override
   public void clear() {
      Set<AffinityKey<String>> affinityKeys = new HashSet<>();
      lock.writeLock().lock();

      try {
         // Snapshot affinity keys while holding the lock; Ignite calls must not occur
         // under the lock.
         for(String id : local.keySet()) {
            affinityKeys.add(getAffinityKey(id));
         }

         local.clear();
         updateLocalSheetCount();
      }
      finally {
         lock.writeLock().unlock();
      }

      accessTimeMap.removeAllAsync(affinityKeys);
      cache.removeAllAsync(affinityKeys);
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
      sheetCountMap.remove(cluster.getLocalMember());
   }

   public boolean isApplyMaxCount() {
      return applyMaxCount;
   }

   public void setApplyMaxCount(boolean applyMaxCount) {
      this.applyMaxCount = applyMaxCount;
   }


   public void flush(String key) {
      RuntimeSheet sheet;
      lock.readLock().lock();

      try {
         sheet = local.get(key);
      }
      finally {
         lock.readLock().unlock();
      }

      AffinityKey<String> affinityKey = getAffinityKey(key);

      if(sheet == null) {
         cache.removeAsync(affinityKey);
      }
      else {
         CompressedSheetState compressed = null;

         try {
            compressed = compressState(sheet.saveState(mapper));
         }
         catch(Exception e) {
            LOG.warn("Failed to serialize sheet state to cache", e);
         }

         if(compressed != null) {
            // Re-check: skip if the sheet was removed while we were serializing.
            // Write lock guards only the local containment check; the Ignite call
            // is submitted after the lock is released.
            boolean shouldWrite;
            lock.writeLock().lock();

            try {
               shouldWrite = local.containsKey(key);
            }
            finally {
               lock.writeLock().unlock();
            }

            if(shouldWrite) {
               cache.putAsync(affinityKey, compressed);
            }
         }
      }
   }

   private RuntimeSheet toSheet(CompressedSheetState compressed) {
      if(compressed == null) {
         return null;
      }

      RuntimeSheetState state = decompressState(compressed);

      if(state instanceof RuntimeViewsheetState vsState) {
         return new RuntimeViewsheet(vsState, mapper);
      }
      else if(state instanceof RuntimeWorksheetState wsState) {
         return new RuntimeWorksheet(wsState, mapper);
      }

      return null;
   }

   public List<String> getAllIds(Principal user) {
      return getAllIds(user, null);
   }

   public List<String> getAllIds(Principal user, CompressedSheetState.SheetType type) {
      Set<String> ids = new HashSet<>();
      Iterator<Cache.Entry<AffinityKey<String>, CompressedSheetState>> iter = cache.iterator();

      try {
         while(iter.hasNext()) {
            Cache.Entry<AffinityKey<String>, CompressedSheetState> e = iter.next();

            if(type != null && e.getValue().getType() != type) {
               continue;
            }

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
      catch(NoSuchElementException e) {
         // Ignite closes distributed iterators mid-scan during topology changes
         // (node join/leave/rebalance). Return whatever ids were collected so far.
         LOG.warn("Cache iterator closed during getAllIds scan, returning partial results", e);
      }
      finally {
         Tool.closeIterator(iter);
      }

      return List.copyOf(ids);
   }

   /**
    * Returns true if the user has at least {@code n} open sheets of the given type across all
    * cluster nodes, short-circuiting the cache scan as soon as the threshold is reached.
    */
   public boolean hasAtLeast(Principal user, CompressedSheetState.SheetType type, int n) {
      int count = 0;

      // Build cheap pre-filter strings from the incoming principal to avoid XML parsing
      // on every cache entry. ClientInfo.writeXML writes session as a plain CDATA value
      // and name as Tool.byteEncode(userID.convertToKey()). Both must match to count the
      // entry. secureID can be 0 in some cases so it is not used.
      String sessionTag = null;
      String nameTag = null;

      if(user instanceof SRPrincipal srp) {
         ClientInfo clientInfo = srp.getUser();

         if(clientInfo != null) {
            String session = clientInfo.getSession();
            String name = clientInfo.getUserIdentity() != null
               ? clientInfo.getUserIdentity().convertToKey() : null;

            if(session != null && !session.isEmpty()) {
               sessionTag = "<session><![CDATA[" + session + "]]></session>";
            }

            if(name != null) {
               nameTag = "<user><![CDATA[" + Tool.byteEncode(name) + "]]></user>";
            }
         }
      }

      final String fastSessionTag = sessionTag;
      final String fastNameTag = nameTag;

      Iterator<Cache.Entry<AffinityKey<String>, CompressedSheetState>> iter = cache.iterator();

      try {
         while(iter.hasNext()) {
            Cache.Entry<AffinityKey<String>, CompressedSheetState> e = iter.next();

            if(type != null && e.getValue().getType() != type) {
               continue;
            }

            if(user == null) {
               if(++count >= n) {
                  return true;
               }
            }
            else if(e.getValue().getUser() != null) {
               final String userXml = e.getValue().getUser();

               if(fastSessionTag != null && fastNameTag != null) {
                  // Fast path: session + name match avoids full XML parse
                  if(userXml.contains(fastSessionTag) && userXml.contains(fastNameTag) &&
                     ++count >= n)
                  {
                     return true;
                  }
               }
               else {
                  try {
                     Document document = Tool.parseXML(new StringReader(userXml));
                     SRPrincipal principal = new SRPrincipal();
                     principal.parseXML(document.getDocumentElement());

                     if(Objects.equals(principal, user) && ++count >= n) {
                        return true;
                     }
                  }
                  catch(Exception ex) {
                     LOG.error("Failed to parse principal", ex);
                  }
               }
            }
         }
      }
      catch(NoSuchElementException e) {
         LOG.warn("Cache iterator closed during hasAtLeast scan, returning partial results", e);
      }
      finally {
         Tool.closeIterator(iter);
      }

      return count >= n;
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
   private final IgniteCache<AffinityKey<String>, CompressedSheetState> cache;
   private final int maxSheetCount;
   private final ReadWriteLock lock = new ReentrantReadWriteLock();
   private final ObjectMapper mapper;
   private boolean applyMaxCount;
   private static final Pattern tempIdPattern = Pattern.compile(
      "^(" + WorksheetEngine.PREVIEW_WORKSHEET + "|" + ViewsheetEngine.PREVIEW_VIEWSHEET +
      ")?(.+)-temp-\\d+$");
   private final Map<String, Integer> sheetCountMap;
   private final IgniteCache<AffinityKey<String>, Long> accessTimeMap;

   public static final String LOCAL_SHEET_COUNT = RuntimeSheet.class.getName() + ".localSheetCount";
   public static final String ACCESS_TIME_MAP_NAME = RuntimeSheet.class.getName() + ".accessTime";
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
