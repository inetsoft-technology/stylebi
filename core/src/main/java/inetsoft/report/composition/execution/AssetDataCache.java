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
package inetsoft.report.composition.execution;

import com.google.common.util.concurrent.Striped;
import inetsoft.mv.RuntimeMV;
import inetsoft.report.TableFilter;
import inetsoft.report.TableLens;
import inetsoft.report.composition.WorksheetService;
import inetsoft.report.filter.BinaryTableFilter;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.XNodeMetaTable;
import inetsoft.report.internal.table.CancellableTableLens;
import inetsoft.report.lens.SetTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.QueryManager;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import inetsoft.web.admin.monitoring.MonitorLevelService;
import inetsoft.web.messaging.MessageAttributes;
import inetsoft.web.messaging.MessageContextHolder;
import inetsoft.web.vswizard.recommender.execution.WizardDataExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeListener;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

/**
 * AssetDataCache, the cache of asset data. It only caches runtime data, so that
 * two same queries will not run twice.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
@SingletonManager.Singleton(AssetDataCache.Reference.class)
public class AssetDataCache extends DataCache<DataKey, TableLens> {
   /**
    * Get the data. If the data doesn't exist, execute the query and add to
    * cache.
    * @param id the specified box id, null if nonexistent.
    * @param table the specified table.
    * @param box the specified asset query sandbox.
    * @param qmgr the specified query manager.
    */
   public static TableLens getData(String id, TableAssembly table,
                                   AssetQuerySandbox box, QueryManager qmgr)
      throws Exception
   {
      return getData(id, table, box, null, AssetQuerySandbox.RUNTIME_MODE, true, -1, qmgr);
   }

   /**
    * Get the data. If the data doesn't exist, execute the query and add to
    * cache.
    * @param id the specified box id, null if nonexistent.
    * @param table the specified table.
    * @param box the specified asset query sandbox.
    * @param ignoredVars ignored variables.
    * @param mode the specified mode.
    * @param limit true to check for query time limitations
    * @param ts if > 0, the creation timestamp of data. data older than the timestamp is discarded.
    * @param qmgr the specified query manager.
    */
   public static TableLens getData(String id, TableAssembly table,
                                   AssetQuerySandbox box, Set ignoredVars,
                                   int mode, boolean limit, long ts,
                                   QueryManager qmgr)
      throws Exception
   {
      AssetDataCache cache = getCache();
      return cache.getData0(id, table, box, ignoredVars, mode, limit, ts, qmgr);
   }

   /**
    * Get the cached data.
    * @param key the data key in the cache.
    * @param ts if > 0, the creation timestamp of data. data older than the timestamp is discarded.
    * @return cached data or null if cache doesn't exist.
    */
   public static TableLens getCachedData(DataKey key, long ts) {
      AssetDataCache cache = getCache();

      // if the cache is cleared for the depended data source, don't use cached data.
      // run a new query to fetch fresh data
      if(!cache.dependenceMap.containsKey(key)) {
         cache.remove(key);
      }

      TableFilter2 filter = (TableFilter2) cache.get(key, ts);

      if(filter != null && (filter.isChanged() || isCancelled(filter) ||
         isFailedQueryDefaultMetaTable(filter)))
      {
         filter = null;
      }

      return filter == null ? null : (TableFilter2) filter.clone();
   }

   private static boolean isFailedQueryDefaultMetaTable(XTable lens) {
      List<XTable> metaTables = new ArrayList<>();
      Util.listNestedTable(lens, XNodeMetaTable.class, metaTables);

      for(XTable table : metaTables) {
         if(table instanceof XNodeMetaTable metaTable && metaTable.isFailedQueryDefault()) {
            return true;
         }
      }

      return false;
   }

   // Check if table or sub-table cancelled
   public static boolean isCancelled(TableLens lens) {
      if(lens instanceof CancellableTableLens && ((CancellableTableLens) lens).isCancelled()) {
         return true;
      }

      if(lens instanceof SetTableLens) {
         for(int i = 0; i < ((SetTableLens) lens).getTableCount(); i++) {
            if(isCancelled(((SetTableLens) lens).getTable(i))) {
               return true;
            }
         }

         return false;
      }
      else if(lens instanceof BinaryTableFilter) {
         return isCancelled(((BinaryTableFilter) lens).getLeftTable()) ||
            isCancelled(((BinaryTableFilter) lens).getRightTable());
      }
      else if(lens instanceof TableFilter) {
         return isCancelled(((TableFilter) lens).getTable());
      }

      return false;
   }

   /**
    * Set the cached data.
    * @param key the data key in the cache.
    * @param data to add to the cache.
    * @param table the specified table assembly.
    */
   public static TableLens setCachedData(DataKey key, TableLens data, TableAssembly table) {
      if(key == null || table instanceof DataTableAssembly) {
         return data;
      }

      AssetDataCache cache = getCache();
      data = new TableFilter2(data);
      resetMV(table, (TableFilter2) data);
      addCache(cache, key, data, table);
      return data;
   }

   /**
    * Remove a data item from the cache.
    * @param key the data key in the cache.
    */
   public static Object removeCachedData(DataKey key) {
      AssetDataCache cache = getCache();
      return removeCache(cache, key);
   }

   /**
    * Remove cached data with dependency on table, directly or indirectly.
    * !! this is currently only used to handle dependency from embedded table that
    * !! is modified from input vs assembly. enhancements may be needed if it's
    * !! used for more general cases.
    */
   public static void removeCacheDependence(TableAssembly table) {
      if(table == null) {
         return;
      }

      AssemblyEntry entry = table.getAssemblyEntry();
      Worksheet ws = table.getWorksheet();
      List<AssemblyEntry> removed = removeCacheDependence(entry + "");

      // remove all cached data of tables depending on this table, as recorded in TableAssembly
      Arrays.stream(ws.getDependings(entry)).forEach(ref -> {
         removeCacheDependence(ref.getEntry() + "");
      });

      // remove all cached data of tables depending on this table, as recorded in AssetDataCache
      removed.stream().forEach(a -> removeCacheDependence((TableAssembly) ws.getAssembly(a)));
   }

   /**
    * Remove cached data for a source (query) or a table assembly.
    * @param source name of the query or table assembly AssemblyEntry.
    * @return the entries that is removed from the cache.
    */
   public static List<AssemblyEntry> removeCacheDependence(String source) {
      AssetDataCache cache = getCache();

      // find entries matching the source
      List<DataKey> entries = cache.dependenceMap.entrySet().stream()
         .filter(entry -> source != null && source.equals(entry.getValue()))
         .map(entry -> entry.getKey())
         .collect(Collectors.toList());

      // remove the entries
      entries.stream().forEach(key -> {
         cache.dependenceMap.remove(key);
         // recursively remove the parent dependency
         removeCacheDependence(key.getAssemblyEntry().toString());
      });

      return entries.stream().map(key -> key.getAssemblyEntry()).collect(Collectors.toList());
   }

   private static void addCache(AssetDataCache cache, DataKey key, TableLens data,
                                TableAssembly table)
   {
      cache.put(key, data, key.getTimeout());
      cache.putDependence(key, table);
   }

   private static Object removeCache(AssetDataCache cache, DataKey key) {
      cache.dependenceMap.remove(key);
      return cache.remove(key);
   }

   /**
    * Cancel the pending queries for id.
    */
   public static void cancel(String id) {
      AssetDataCache cache = getCache();
      cache.cancel0(id, true);
   }

   /**
    * Cancel the pending queries for id.
    */
   public static void cancel(String id, boolean force) {
      AssetDataCache cache = getCache();
      cache.cancel0(id, force);
   }

   /**
    * Clear the cached data.
    */
   public static void clearCache() {
      AssetDataCache cache = getCache();
      cache.clear();
   }

   /**
    * Get the key to be used in the cache for the specified table.
    * @param formatted <tt>true</tt> if cache formatted table.
    */
   public static DataKey getCacheKey(TableAssembly table, AssetQuerySandbox box,
                                     Set ignoredVars, int mode, boolean formatted)
      throws Exception
   {
      return getCacheKey(table, box, ignoredVars, mode, formatted, null);
   }

   /**
    * Get the key to be used in the cache for the specified table.
    * @param formatted <tt>true</tt> if cache formatted table.
    */
   public static DataKey getCacheKey(TableAssembly table, AssetQuerySandbox box, Set ignoredVars,
                                     int mode, boolean formatted, VariableTable extraVars)
      throws Exception
   {
      return getCacheKey(table, box, ignoredVars, mode, formatted, extraVars, null);
   }

   /**
    * Get the key to be used in the cache for the specified table.
    * @param formatted <tt>true</tt> if cache formatted table.
    */
   public static DataKey getCacheKey(TableAssembly table, AssetQuerySandbox box, Set ignoredVars,
                                     int mode, boolean formatted, VariableTable extraVars,
                                     VariableTable runtimeVars)
      throws Exception
   {
      if(table instanceof DataTableAssembly) {
         return null;
      }

      // explicitly set no cache?
      if("true".equals(table.getProperty("no_cache"))) {
         return null;
      }

      // @by billh, do not cache data for mv. If we do like that, mv data
      // will always be stored in memory, which occupies much memory and
      // might cause OOM
      // @by jasons, this was causing a separate copy of the data to be held for
      // each viewsheet instance. One user could open the same viewsheet
      // repeatedly, creating a new instance each time. This was causing massive
      // memory consumption for large tables and unresponsiveness when swapping
      // occurred. Note that MVQuery has been modified to use an XSwappableTable
      // for *any* table over 10,000 rows to prevent memory issues in the data
      // cache.
      if("false".equals(SreeEnv.getProperty("mv.cache.data")) && containsMV(table)) {
         return null;
      }

      // if cache is not enabled, cache 15 seconds for temporary usage
      // see bug1319492898101, if the viewsheet contains form assembly,
      // we should cache with short timeout so that the data will update
      // immediately after update the database when reopen the viewsheet
      long timeout = -1L;

      if("true".equals(box.getVariableTable().get("_FORM_"))) {
         timeout = 5000L;
      }
      else if(!isCacheEnabled(mode)) {
         timeout = 15000L;
      }

      Principal user = box.getUser();
      WorksheetInfo winfo = box.getWorksheet().getWorksheetInfo();
      return DataKey.create(table, getVariableTable(box, ignoredVars, extraVars, runtimeVars),
                            user, mode, formatted, winfo.getDesignMaxRows(),
                            winfo.getPreviewMaxRow(), box.isIgnoreFiltering(), timeout);
   }

   /**
    * Check if cache is enabled by the given mode.
    */
   private static boolean isCacheEnabled(int mode) {
      // live mode will always enable cache; runtime mode will check
      // whether cache option is turned on
      return mode != AssetQuerySandbox.RUNTIME_MODE ||
         !"false".equals(SreeEnv.getProperty("query.cache.data"));
   }

   /**
    * Disable all caching to force data query to execute on every refresh.
    */
   public static boolean isDebugData() {
      return "true".equals(SreeEnv.getProperty("debug.data"));
   }

   /**
    * Get variable table.
    */
   private static VariableTable getVariableTable(AssetQuerySandbox box, Set ignored,
                                                 VariableTable extraVars, VariableTable runtimeVars)
   {
      VariableTable vtable = box.getVariableTable();

      if(vtable == null) {
         return null;
      }

      vtable = vtable.clone();

      if(runtimeVars != null) {
         try {
            vtable.addAll(runtimeVars);
         }
         catch(Exception ignore) {
         }
      }

      XUtil.copyDBCredentials((XPrincipal) box.getUser(), vtable);

      if(ignored != null) {
         for(Object name : ignored) {
            vtable.remove(name + "");
         }
      }

      if(box.getAdditionalVariableProvider() != null) {
         vtable.put("additionalVarProvider", true);
      }

      try {
         vtable.addAll(extraVars);
      }
      catch(Exception ex) {
         LOG.warn("failed to merge variable", ex);
      }

      return vtable;
   }

   /**
    * Get the data cache.
    * @return the data cache.
    */
   public static synchronized AssetDataCache getCache() {
      return SingletonManager.getInstance(AssetDataCache.class);
   }

   /**
    * <p>This method should be invoked prior to executing a query if the cache is active.
    * <p>Following execution, {@link AssetDataCache#markExecutingFinished} should be called to
    * notify any waiting threads.
    *
    * <p>This method has three principal execution paths:
    *
    *<ol>
    *    <li>Table lens is already in cache and is immediately returned.
    *
    *    <li>Table lens is not in cache and is not being executed. Mark the data key as executing
    *    and return null.
    *
    *    <li>Table lens is not in cache and is being executed. Wait until execution is finished or
    *    timeout occurs. Attempt to retrieve table lens from cache when finished waiting.
    *</ol>
    *
    * @param key       the data key for the table lens.
    * @param touchTime touch timestamp of data changes.
    *
    * @return the table lens if in the cache, null otherwise.
    *
    * @throws InterruptedException if this thread is interrupted while waiting.
    */
   static TableLens getOrMarkExecutingOrWait(DataKey key, long touchTime)
      throws InterruptedException
   {
      if(key == null) {
         return null;
      }

      final AssetDataCache cache = getCache();
      // Check cache before locking to avoid needless lock contention.
      TableLens data = getCachedData(key, touchTime);

      if(data != null) {
         return data;
      }

      final Lock lock = cache.dataKeyLocks.get(key);
      boolean locked = true;
      lock.lock();

      try {
         // Need to check cache a second time while guarded inside of the lock to avoid race
         // condition.
         data = getCachedData(key, touchTime);

         if(data != null) {
            return data;
         }

         if(cache.isExecuting(key)) {
            final DataKey executingKey = cache.getExecutingKey(key);

            synchronized(executingKey) {
               lock.unlock();
               locked = false;

               try {
                  final Instant timeout = Instant.now().plus(EXECUTION_WAITING_TIMEOUT);

                  do {
                     executingKey.wait(EXECUTION_WAIT_TIME.toMillis());
                  }
                  while(cache.isExecuting(executingKey) && Instant.now().isBefore(timeout));
               }
               catch(InterruptedException e) {
                  LOG.error("Interrupted while waiting for executing key: {}", key);
                  throw e;
               }
               catch(Exception e) {
                  LOG.error("Exception occurred while waiting on executing key: {}", key);
                  throw e;
               }
            }

            data = getCachedData(executingKey, touchTime);

            if(data == null) {
               LOG.info("TableLens was null after waiting on an executor.");
            }
         }
         else {
            cache.markExecuting(key);
         }
      }
      finally {
         if(locked) {
            lock.unlock();
         }
      }

      return data;
   }

   /**
    * Remove a data key from the executing set and notify threads that may be listening on the key.
    *
    * @param key the data key to mark finished.
    */
   static void markExecutingFinished(DataKey key) {
      if(key == null) {
         return;
      }

      final AssetDataCache cache = getCache();
      final Lock lock = cache.dataKeyLocks.get(key);
      lock.lock();

      try {
         final DataKey executingKey = cache.getExecutingKey(key);

         if(executingKey != null) {
            synchronized(executingKey) {
               cache.unmarkExecuting(executingKey);
               executingKey.notifyAll();
            }
         }
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Check if contains mv.
    */
   private static boolean containsMV(TableAssembly table) {
      if(table == null) {
         return false;
      }

      RuntimeMV rinfo = table.getRuntimeMV();

      if(rinfo != null) {
         return true;
      }

      if(!(table instanceof ComposedTableAssembly)) {
         return false;
      }

      ComposedTableAssembly ctable = (ComposedTableAssembly) table;
      TableAssembly[] tables = ctable.getTableAssemblies(false);

      for(int i = 0; tables != null && i < tables.length; i++) {
         boolean contained = containsMV(tables[i]);

         if(contained) {
            return true;
         }
      }

      return false;
   }

   /**
    * Add all the used materialized views to table lens. In this way
    * we could clear the cached table lens when new data arrives.
    */
   private static boolean resetMV(TableAssembly table, TableFilter2 data) {
      RuntimeMV rinfo = table == null ? null : table.getRuntimeMV();
      String mvname = rinfo == null ? null : rinfo.getMV();

      if(mvname != null && mvname.length() > 0) {
         data.setMV(mvname);
         return true;
      }

      if(!(table instanceof ComposedTableAssembly)) {
         return false;
      }

      ComposedTableAssembly ctable = (ComposedTableAssembly) table;
      TableAssembly[] tables = ctable.getTableAssemblies(false);

      for(int i = 0; tables != null && i < tables.length; i++) {
         boolean success = resetMV(tables[i], data);

         if(success) {
            return true;
         }
      }

      return false;
   }

   /**
    * Create an asset data cache.
    */
   private AssetDataCache() {
      super();
      String prop = SreeEnv.getProperty("query.cache.limit", "100");

      if(prop != null) {
         setLimit(Integer.parseInt(prop));
      }

      prop = SreeEnv.getProperty("query.cache.timeout", "600000");

      if(prop != null) {
         setTimeout(Long.parseLong(prop));
      }

      // default 6 threads per cpu for viewsheet might execute many queries
      pool = new ThreadPool(
         "AssetData", 6, "assetData.thread.count", 2);
      LOG.debug(
         "Max number of asset data cache processors: {}, {}",
         pool.getSoftLimit(), pool.getHardLimit());
   }

   /**
    * Remove all entries from the cache.
    */
   @Override
   public synchronized void clear() {
      for(DataKey key : keySet()) {
         TableFilter2 data = (TableFilter2) get(key);

         if(data != null) {
            data.dispose2();
         }
      }

      super.clear();
   }

   /**
    * Get the data.
    * @param id the specified box id, null if nonexistent.
    * @param table the specified table.
    * @param box the specified asset query sandbox.
    * @param ignoredVars ignored variables.
    * @param mode the specified mode.
    * @param limit true to check query time limitations.
    * @param ts touch timestamp of data changes.
    * @param qmgr the specified query manager.
    */
   private TableLens getData0(String id, TableAssembly table,
                              AssetQuerySandbox box, Set ignoredVars,
                              int mode, boolean limit,
                              long ts, QueryManager qmgr)
      throws Exception
   {
      DataKey key = null;

      try {
         if(table != null) {
            table.update();
         }

         int hcnt = hits.get();
         int nhcnt = noHits.get();
         int acnt = hcnt + nhcnt;

         // turn off cache if could not hit
         if(cache && acnt > 500 && (hcnt / (double) acnt < 0.1)) {
            cache = false;
         }

         key = cache ? getCacheKey(table, box, ignoredVars, mode, false) : null;
      }
      catch(Exception ex) {
         // cache is not allowed
         LOG.debug("Failed to get cached data", ex);
      }

      if(!cache) {
         long now = System.currentTimeMillis();

         // reset cache every 5 minutes
         if(now - lts >= 300000) {
            lts = now;
            hits.set(0);
            noHits.set(0);
            cache = true;
         }
      }

      // only runtime mode will be cached
      TableLens data = key == null ? null : getOrMarkExecutingOrWait(key, ts);

      // ignore table not attempted on cache (e.g. mirror, embedded)
      if(key != null) {
         if(data != null) {
            hits.incrementAndGet();
         }
         else {
            noHits.incrementAndGet();
         }
      }

      // clear cached data if data changed or not completed
      if(data instanceof TableFilter2) {
         TableFilter2 data2 = (TableFilter2) data;

         if(data2.isChanged()) {
            data = null;
            ts = System.currentTimeMillis();
         }
      }

      if(isDebugData()) {
         data = null;
      }

      if(data != null && (!"true".equals(SreeEnv.getProperty("mv.debug")))) {
         final String tableName = table == null ? "null" : table.getAbsoluteName();
         LOG.debug("Using cached result set: {} rows: {}", tableName, data.getRowCount());
      }
      else {
         Processor processor = null;

         try {
            processor = new Processor(id, table, mode, box, qmgr, limit,
                                      ts, lockEntries.get(), XUtil.VS_ASSEMBLY.get(),
                                      Thread.currentThread());
            addProcessor(id, processor);
            pool.add(processor);
            processor.join(); // waiting until data is executed
            List<Exception> exceptions = processor.getExceptions();

            // copy exceptions from thread pool thread to parent thread
            if(exceptions != null && !exceptions.isEmpty()) {
               List<Exception> mexcepts = WorksheetService.ASSET_EXCEPTIONS.get();

               if(mexcepts != null) {
                  mexcepts.addAll(exceptions);
               }
            }

            // copy errors from thread pool thread to parent thread
            List<?> errors = processor.getErrors();

            if(errors != null && !errors.isEmpty()) {
               List<Object> merrors = (List<Object>) AssetRepository.ASSET_ERRORS.get();

               if(merrors != null) {
                  merrors.addAll(errors);
               }
            }

            UserMessage userMessage = processor.getUserMessage();
            Tool.addUserMessage(userMessage);

            Exception ex = processor.getException();

            if(ex != null) {
               throw ex;
            }

            data = processor.getData();

            if(userMessage == null) {
               userMessage = processor.getUserMessage();
               Tool.addUserMessage(userMessage);
            }

            if(key != null && data != null) {
               if(data instanceof TableFilter2) {
                  data = (TableLens) ((TableFilter2) data).clone();
               }
               else {
                  data = new TableFilter2(data);
               }

               resetMV(table, (TableFilter2) data);
               put(key, data, key.getTimeout());
               putDependence(key, table);
            }
         }
         finally {
            if(processor != null) {
               removeProcessor(id, processor);
            }

            if(key != null) {
               markExecutingFinished(key);
            }
         }
      }

      if(key != null && (data instanceof TableFilter2)) {
         TableFilter2 filter = (TableFilter2) data;
         data = (TableFilter2) filter.clone();
      }

      return data;
   }

   /**
    * Add processor.
    */
   private synchronized void addProcessor(String id, Processor proc) {
      if(id == null) {
         return;
      }

      List<Processor> list = pmap.computeIfAbsent(id, k -> new ArrayList<>());
      list.add(proc);
   }

   /**
    * Remove processor.
    */
   private synchronized void removeProcessor(String id, Processor proc) {
      if(id == null) {
         return;
      }

      List<Processor> list = pmap.get(id);

      if(list != null) {
         list.remove(proc);

         if(list.isEmpty()) {
            pmap.remove(id);
         }
      }
   }

   /**
    * Cancel the pending queries for id.
    */
   private void cancel0(String id, boolean force) {
      if(id == null) {
         return;
      }

      List<Processor> list;

      synchronized(this) {
         list = pmap.remove(id);
      }

      if(list == null) {
         return;
      }

      // remove the pending processors from thread pool
      for(Processor proc : list) {
         if(force || proc.isCancelable()) {
            proc.complete();
            pool.remove(proc);
         }
      }
   }

   /**
    * Mark {@code key} as executing. This should only be called if the caller is sure that the
    * equivalent key is not already marked.
    *
    * @param key the key to mark executing.
    */
   private void markExecuting(DataKey key) {
      final DataKey keyInSet = executings.putIfAbsent(key, key);

      if(keyInSet != null) {
         final String message = String.format(
            "Attempted to overwrite data key in executing set: %s", key);
         throw new IllegalStateException(message);
      }
   }

   private void unmarkExecuting(DataKey key) {
      executings.remove(key);
   }

   /**
    * @param key the equivalent key to get the executing object reference of.
    *
    * @return the specific key object reference in {@code executings} equal to {@code key} if it
    * exists, null otherwise.
    */
   private DataKey getExecutingKey(DataKey key) {
      return executings.get(key);
   }

   private boolean isExecuting(DataKey key) {
      return executings.containsKey(key);
   }

   /**
    * Data Processor.
    */
   private static class Processor extends ThreadPool.AbstractContextRunnable {
      Processor(String id, TableAssembly table, int mode,
                AssetQuerySandbox box, QueryManager mgr, boolean limit,
                long ts, LockHolder lockHolder, String vsobj, Thread parent)
      {
         super();

         this.id = id;
         this.table = table;
         this.mode = mode;
         this.box = box;
         this.qmgr = mgr;
         this.limit = limit;
         this.ts = ts;
         this.created = System.currentTimeMillis();
         this.lockHolder = lockHolder;
         this.vsobj = vsobj;
         this.parentThread = parent;
         this.messageContext = MessageContextHolder.getMessageAttributes();
      }

      protected boolean isCancelable() {
         return qmgr != null ? qmgr.isCancelable() : true;
      }

      /**
       * Get context id, used for log(log_pending), most case, it is the
       * report id or viewsheet id.
       */
      @Override
      public String getContextID() {
         return id;
      }

      @Override
      public void run() {
         if(completed) {
            return;
         }

         try {
            // share same LockHolder for current thread and parent thread
            getCache().lockEntries.set(lockHolder);
            XUtil.VS_ASSEMBLY.set(vsobj);

            // if this query is cancelled by query manager, to execute the query
            // is meaningless. Here we avoid executing it to save memory and cpu
            if(qmgr != null) {
               long lastCancelled = qmgr.lastCancelled();

               if(lastCancelled > created) {
                  return;
               }
            }

            // initialize thread local variables
            WorksheetService.ASSET_EXCEPTIONS.set(new ArrayList<>());
            AssetRepository.ASSET_ERRORS.set(new ArrayList<>());
            XUtil.QUERY_INFOS.set(new ArrayList<>());
            MessageContextHolder.setMessageAttributes(messageContext);
            String asset = table.getProperty("assetName");

            // initialize thread local variable for session manager
            if(MonitorLevelService.getMonitorLevel() > 0 && asset != null) {
               List<String> infos = XUtil.QUERY_INFOS.get();
               infos.add(0, asset);
            }

            processorThread.set(true);
            ThreadContext.inheritSession(parentThread);
            PostProcessor.setCreatingMV(box.isCreatingMV());
            run0();
         }
         catch(Exception ex) {
            throwable = ex;
         }
         catch(Throwable ex) {
            throwable = new RuntimeException(ex);
         }
         finally {
            processorThread.set(false);
            complete();
            getCache().lockEntries.remove();
            exceptions = WorksheetService.ASSET_EXCEPTIONS.get();
            errors = (List<Object>) AssetRepository.ASSET_ERRORS.get();
            userMessage = Tool.getUserMessage();

            // release thread local variables to avoid memory leak
            WorksheetService.ASSET_EXCEPTIONS.set(new ArrayList<>());
            AssetRepository.ASSET_ERRORS.set(new ArrayList<>());
            XUtil.QUERY_INFOS.set(new ArrayList<>());
            XUtil.VS_ASSEMBLY.set(null);
            MessageContextHolder.setMessageAttributes(null);
            PostProcessor.setCreatingMV(false);
         }
      }

      private void run0() throws Exception {
         if(completed) {
            return;
         }

         AssetQuery query = AssetQuery.createAssetQuery(table, mode, box, false, ts, true, false);
         query.setTimeLimited(limit);
         query.setQueryManager(qmgr);
         VariableTable vtable = new VariableTable();

         // queries for wizard recommendation algorithm should using
         // the data without applying variables.
         if(id == null || id.indexOf(WizardDataExecutor.CACHE_ID_PREFIX) == -1) {
            vtable = getVariableTable(box, null, null, null);
         }

         // if this query is cancelled by query manager, to execute the query
         // is meaningless. Here we avoid executing it to save memory and cpu
         if(qmgr != null) {
            long lastCancelled = qmgr.lastCancelled();

            if(lastCancelled > created) {
               return;
            }
         }

         data = query.getTableLens(vtable == null ? new VariableTable() : vtable);

         if(data == null) {
            if((box != null && box.getViewsheetSandbox() != null &&
               box.getViewsheetSandbox().isScheduleAction()) || (vtable != null &&
               Boolean.TRUE.equals(vtable.get("__is_scheduler__"))))
            {
               throw new Exception("Failed to execute query for " + table.getAbsoluteName());
            }
         }

         else {
            data = AssetQuery.shuckOffFormat(data);

            /* @by larryl not sure why this is necessary. it would cause this method to
            block until sub tables are finished in case of SummaryFilter/SortFilter
            // here call moreRows() to force data to be ready
            if(!isReportData() && !(data instanceof DFWrapper &&
                                    ((DFWrapper) data).isSparkProcessing()))
            {
               data.moreRows(1);
            }
            */
         }
      }

      public void complete() {
         synchronized(lock) {
            completed = true;
            lock.notifyAll();
         }
      }

      public void join() {
         synchronized(lock) {
            while(!completed) {
               try {
                  lock.wait(10000);
               }
               catch(Exception ex) {
                  // ignore it
               }
            }
         }
      }

      public TableLens getData() {
         return data;
      }

      /**
       * Get the uncaught exception.
       */
      public Exception getException() {
         return throwable;
      }

      /**
       * Get the caught asset exceptions.
       */
      public List<Exception> getExceptions() {
         return exceptions;
      }

      /**
       * Get the cached asset errors.
       */
      public List<Object> getErrors() {
         return errors;
      }

      /**
       * Get the cached user message.
       */
      public UserMessage getUserMessage() {
         return userMessage;
      }

      private final Object lock = new Object();
      private Exception throwable;
      private boolean completed;
      private String id;
      private TableLens data;
      private TableAssembly table;
      private int mode;
      private AssetQuerySandbox box;
      private QueryManager qmgr;
      private boolean limit;
      private long ts;
      private long created;
      private LockHolder lockHolder;
      private List<Exception> exceptions;
      private List<Object> errors;
      private UserMessage userMessage;
      private String vsobj;
      private Thread parentThread;
      private final MessageAttributes messageContext;
   }

   /**
    * Monitoring the data cache.
    */
   public static void monitor(boolean monitoring) {
      getCache().monitor0(monitoring);
   }

   private void putDependence(DataKey key, TableAssembly table) {
      String source = table.getSource();

      // if bound to query
      if(source != null) {
         dependenceMap.put(key, source);
      }
      // this is for handling dependency from embedded table to vs assembly.
      // see comments is removeCachedDependence()
      else if(table instanceof MirrorTableAssembly) {
         TableAssembly child = ((MirrorTableAssembly) table).getTableAssembly();

         if(child != null) {
            dependenceMap.put(key, child.getAssemblyEntry() + "");
         }
         else {
            dependenceMap.put(key, table.getAssemblyEntry() + "");
         }
      }
      // having an entry for key means the data is valid.
      else {
         dependenceMap.put(key, table.getAssemblyEntry() + "");
      }
   }

   public static boolean isProcessorThread() {
      return processorThread.get();
   }

   @Override
   public synchronized CacheEntry<DataKey, TableLens> put(DataKey key, TableLens data, long timeout)
   {
      CacheEntry<DataKey, TableLens> entry = super.put(key, data, timeout);
      lockEntries.get().monitor(entry);
      return entry;
   }

   @Override
   protected boolean isOutOfDate(CacheEntry<DataKey, TableLens> entry) {
      return super.isOutOfDate(entry) &&
         (!lockEntries.get().isMonitoring() || entry.counter() == 0);
   }

   @Override
   protected boolean touchEntry(CacheEntry<DataKey, TableLens> entry) {
      super.touchEntry(entry);
      lockEntries.get().monitor(entry);
      return true;
   }

   @Override
   protected boolean demote(CacheEntry<DataKey, TableLens> entry) {
      // clean up when corresponding entry removed from cache
      dependenceMap.remove(entry.getKey());
      return super.demote(entry);
   }

   private void monitor0(boolean monitoring) {
      lockEntries.get().monitor(monitoring);
   }

   private static class LockHolder {
      public void monitor(boolean monitoring) {
         if(!monitoring) {
            for(CacheEntry<DataKey, TableLens> entry : entries) {
               entry.getAndDecrement();
            }

            entries.clear();
         }

         this.monitoring = monitoring;
      }

      public boolean isMonitoring() {
         return this.monitoring;
      }

      public void monitor(CacheEntry<DataKey, TableLens> entry) {
         if(monitoring) {
            entry.getAndIncrement();
            entries.add(entry);
         }
      }

      private boolean monitoring = false;
      private final List<CacheEntry<DataKey, TableLens>> entries = new ArrayList<>();
   }

   private boolean cache = true;
   private long lts = System.currentTimeMillis();

   private final Map<DataKey, DataKey> executings = new ConcurrentHashMap<>();
   private final Striped<Lock> dataKeyLocks = Striped.lazyWeakLock(256);
   private final Map<String, List<Processor>> pmap = new HashMap<>();
   // key -> source (e.g. data source name)
   // if a key doesn't exist in dependenceMap (removed), then its cache is invalid.
   private final Map<DataKey, String> dependenceMap = new ConcurrentHashMap<>();
   private final ThreadPool pool;
   private final AtomicInteger hits = new AtomicInteger();
   private final AtomicInteger noHits = new AtomicInteger();
   private final ThreadLocal<LockHolder> lockEntries = ThreadLocal.withInitial(LockHolder::new);
   private static final Duration EXECUTION_WAITING_TIMEOUT = Duration.ofMinutes(10);
   private static final Duration EXECUTION_WAIT_TIME = Duration.ofSeconds(5);
   private static final ThreadLocal<Boolean> processorThread =
      ThreadLocal.withInitial(() -> Boolean.FALSE);
   private static final Logger LOG = LoggerFactory.getLogger(AssetDataCache.class);

   public static final class Reference extends SingletonManager.Reference<AssetDataCache> {
      @Override
      public synchronized AssetDataCache get(Object ... parameters) {
         if(cache == null) {
            cache = new AssetDataCache();

            dataSourceRegistry = DataSourceRegistry.getRegistry();
            dataSourceRegistry.addRefreshedListener(listener);
            dataSourceRegistry.addModifiedListener(listener);
         }

         return cache;
      }

      @Override
      public synchronized void dispose() {
         if(cache != null) {
            dataSourceRegistry.removeRefreshedListener(listener);
            dataSourceRegistry.removeModifiedListener(listener);
            dataSourceRegistry = null;

            cache.clear();
            cache = null;
         }
      }

      private AssetDataCache cache;
      private DataSourceRegistry dataSourceRegistry;
      private final PropertyChangeListener listener = evt -> clearCache();
   }
}
