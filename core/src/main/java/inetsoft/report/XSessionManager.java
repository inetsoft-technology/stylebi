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
package inetsoft.report;

import inetsoft.sree.security.IdentityID;
import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.report.composition.execution.*;
import inetsoft.report.internal.*;
import inetsoft.report.internal.table.*;
import inetsoft.report.lens.xnode.*;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.service.*;
import inetsoft.uql.util.*;
import inetsoft.util.*;
import inetsoft.util.script.ExpressionFailedException;
import inetsoft.web.admin.monitoring.MonitorLevelService;
import inetsoft.web.composer.model.BrowseDataModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * XSessionManager provides the API for processing report data queries.
 * It can be used to find all query parameters, or execute the queries
 * and populate the report with query output. It supports all queries bound to
 * a report element through the Designer.
 * <p>
 * If a report has query binding defined in the designer, the report must
 * be executed using XSessionManager before it is processed. Otherwise
 * the queries are not executed.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
@SingletonManager.Singleton(XSessionManager.Reference.class)
public class XSessionManager {
   /**
    * Cached data hint.
    */
   public static final String CACHED_DATA = "__CACHED_DATA__";

   /**
    * Get a singleton session manager. The manager is already bound to a
    * session. The bind() method does not need to be called.
    */
   public static XSessionManager getSessionManager() {
      return SingletonManager.getInstance(XSessionManager.class);
   }

   /**
    * Clears the cached session manager.
    */
   public static void clear() {
      SingletonManager.reset(XSessionManager.class);
   }

   /**
    * Restarts the session manager.
    */
   public static void restart() {
      clear();
      getSessionManager();
   }

   /**
    * Create a session manager with specific key.
    */
   public XSessionManager() throws RemoteException {
      service = XFactory.getDataService();
   }

   /**
    * Create a session manager from a data service and session. The session
    * should be the object returned by the service.bind() call.
    */
   public XSessionManager(XDataService service, Object session) {
      this.service = service;
      this.session = session;
   }

   /**
    * If the hint max row is in the inner sql, try to get it and set to the JDBCTableNode,
    * to make sure max row warning can be prompted(for Bug #45916).
    */
   private static HashMap fixHintMaxRow(XNode node, XQuery query) {
      HashMap map = new HashMap();

      // other source is ok, so only fix for jdbc query.
      if(!(query instanceof JDBCQuery)) {
         return map;
      }

      SQLDefinition sql = ((JDBCQuery) query).getSQLDefinition();

      if(!(sql instanceof UniformSQL)) {
         return map;
      }

      HashMap result = new HashMap();
      UniformSQL usql = (UniformSQL) sql;
      int hintMax = collectHintMaxRows(usql, map);

      if(hintMax == 0 && map.isEmpty()) {
         return map;
      }

      boolean hasAggregate = hasAggregate(usql);
      boolean hasCondition = usql.getWhere() != null;

      // 1. current query has maxrow.
      if(hintMax > 0 && node instanceof JDBCTableNode) {
         ((JDBCTableNode) node).setHintMaxRow(hintMax);

         // 2. current query has aggregate or condition, and has maxrow,
         // the maxrow is applied to the base query, so result of the aggregate/condition
         // query will never prompt maxrow limit warning, here we need to check the
         // max row limit for the base query, and prompt for user.
         if(hasAggregate || hasCondition) {
            result.put(Util.BASE_MAX_ROW_KEY, true);
            result.put(Util.HINT_MAX_ROW_KEY, hintMax);
         }

         return result;
      }

      // 3. collect maxrow settings for sub queries, like join tables.
      if(map.keySet().size() > 0){
         StringBuffer buffer = new StringBuffer();
         Iterator it = map.keySet().iterator();

         while(it.hasNext()) {
            Object tname = it.next();
            Object max = map.get(tname);

            if(tname == null || max == null) {
               continue;
            }

            // 4. only one table, then it should be mirror, so use the maxrow limit directly.
            if(map.keySet().size() == 1) {
               result.put(Util.HINT_MAX_ROW_KEY, max);
               return result;
            }

            if(buffer.length() > 0) {
               buffer.append(", ");
            }

            buffer.append(tname + " -> " + max + " " + Catalog.getCatalog().getString("rows"));
         }

         result.put(Util.SUB_MAX_ROW_KEY, buffer.toString());
      }

      return result;
   }

   /**
    * Check if the target jdbc query has aggregate.
    */
   private static boolean hasAggregate(UniformSQL sql) {
      if(sql == null) {
         return false;
      }

      return hasAggregate(sql.getSelection()) || hasAggregate(sql.getBackupSelection());
   }

   private static boolean hasAggregate(XSelection select) {
      if(select == null || !(select instanceof JDBCSelection)) {
         return false;
      }

      return ((JDBCSelection) select).hasAggregate();
   }

   /**
    * Return the max row limit for the target UniformSQL, and collect max row limit for sub tables.
    * @param sql the target UniformSQL
    * @param map the map used to store max row limit for sub tables, key->tname, value->maxrow
    */
   private static int collectHintMaxRows(UniformSQL sql, HashMap map) {
      Object maxHint = sql.getHint(UniformSQL.HINT_OUTPUT_MAXROWS, true);

      if(maxHint != null) {
         try {
            return Integer.parseInt(maxHint + "");
         }
         catch(NumberFormatException ignore) {
         }
      }

      XJoin[] joins = sql.getJoins();

      if(joins != null && joins.length > 0) {
         for(int i = 0; i < joins.length; i++) {
            XJoin join = joins[i];
            SelectTable table1 = sql.getSelectTable(join.getTable1());
            SelectTable table2 = sql.getSelectTable(join.getTable2());

            int max1 = table1 == null ? 0 : collectHintMaxRows(table1, map);
            int max2 = table1 == null ? 0 : collectHintMaxRows(table2, map);

            if(max1 > 0) {
               map.put(table1.getAlias(), max1);
            }

            if(max2 > 0) {
               map.put(table2.getAlias(), max2);
            }
         }
      }
      else if(sql.getTableCount() > 0){
         for(int i = 0; i < sql.getTableCount(); i++) {
            SelectTable table = sql.getSelectTable(i);
            int max = table == null ? 0 : collectHintMaxRows(table, map);

            if(max > 0) {
               map.put(table.getAlias(), max);
            }
         }
      }

      return 0;
   }

   /**
    * Return the max row limit for the target table, and collect max row limit for sub tables.
    * @param table the target to select table.
    * @param map   the map used to store max row limit for sub tables, key->tname, value->maxrow
    */
   private static int collectHintMaxRows(SelectTable table, HashMap map) {
      Object name = table == null ? null : table.getName();

      if(!(name instanceof UniformSQL)) {
         return 0;
      }

      return collectHintMaxRows((UniformSQL) name, map);
   }

   /**
    * Tear down manager.
    */
   public void tearDown() {
      clearCache();

      if(service != null) {
         try {
            service.close(session);
            service = null;
         }
         catch(Exception ex) {
            LOG.warn("Failed to close data service", ex);
         }
      }
   }

   /**
    * Get the data source object of this session manager.
    */
   public XDataService getDataService() {
      return service;
   }

   /**
    * Get the session object of this session. The session is either passed
    * in from the constructor, or created when bind() method is called.
    */
   public Object getSession() {
      return session;
   }

   /**
    * Set the session object of this session.
    */
   public void setSession(Object session) {
      this.session = session;
   }

   /**
    * Bind to the data service engine.
    * @param uinfo user information. Must be unique in each session.
    */
   public void bind(Object uinfo) throws RemoteException {
      session = service.bind((uinfo == null) ?
         System.getProperty("user.name") :
         uinfo);
   }

   /**
    * Execute queries for selection list parameters and populate choice list.
    */
   public void executeChoiceQuery(UserVariable var) {
      executeChoiceQuery(var, null);
   }

   /**
    * Execute queries for selection list parameters and populate choice list.
    */
   public void executeChoiceQuery(UserVariable var, String ename) {
      String qname = var.getChoiceQuery();

      if(qname != null && !qname.equals("")) {
         try {
            // @by larryl, if the name is [name].[name], it is an encoded
            // field full name that can be handled by BrowsedData
            if(BrowsedData.isFieldFullName(qname)) {
               BrowseDataModel browseData;

               // not embedded data?
               WorksheetProcessor wsproc = new ReportWorksheetProcessor();
               browseData = BrowsedData.getColumnData(null, wsproc, qname);
               final Object[] data = browseData.values();

               if(data == null || data.length == 0) {
                  return;
               }

               Object[] values;
               Object[] labels;

               if(browseData.existLabels()) {
                  values = browseData.values();
                  labels = browseData.labels();
               }
               // is one dimension array? (normal type)
               else {
                  values = browseData.values();
                  labels = browseData.values();
               }

               var.setValues(values);
               var.setChoices(labels);
               var.setDataTruncated(browseData.dataTruncated());
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to execute choice query " + qname +
               " for element " + ename, ex);
         }
      }
   }

   /**
    * Cancel report and clean up background queries if supported.
    */
   public void cancel(ReportSheet report) {
      executemap.remove(report);
      Enumeration elems = ElementIterator.elements(report);

      while(elems.hasMoreElements()) {
         ReportElement elem = (ReportElement) elems.nextElement();
         TableLens table = null;

         if(elem instanceof TableElement) {
            TableElementDef telem = (TableElementDef) elem;
            // @by stephenwebster, For bug1430947680293
            // Mark the table canceled before getTable so that call
            // will have the knowledge of the cancel request.
            telem.cancel();
            table = telem.getTable();
         }
         else if(elem instanceof SectionElement) {
            table = ((SectionElement) elem).getTable();
         }
         else if(elem instanceof ChartElementDef) {
            table = ((ChartElementDef) elem).getTable();
            ((ChartElementDef) elem).getDataSet().dispose();
         }
         else if(elem instanceof NonScalar) {
            table = ((NonScalar) elem).getTable();
         }
         else if(elem instanceof TextBased) {
            XTableNode node = ((TextBased) elem).getTableNode();

            if(node != null) {
               node.cancel();
            }
         }

         if(table != null) {
            CancellableTableLens lens = getCancellableTableLens(table);

            if(lens != null) {
               lens.cancel();
            }
         }
      }
   }

   /**
    * Recursively find the XNodeTableLens for a report element.
    */
   private CancellableTableLens getCancellableTableLens(TableLens lens) {
      if(lens instanceof CancellableTableLens) {
         return (CancellableTableLens) lens;
      }
      else if(lens instanceof TableFilter) {
         return getCancellableTableLens(((TableFilter) lens).getTable());
      }

      return null;
   }

   /**
    * Get the XNodeTableLens from a query.
    * @param query the specified query.
    * @param qvars the specified variable table.
    * @param user the specified principal.
    */
   public TableLens getXNodeTableLens(XQuery query, VariableTable qvars, Principal user)
      throws Exception
   {
      return getXNodeTableLens(query, qvars, user, null, null);
   }

   /**
    * Get the XNodeTableLens from a query.
    * @param query the specified query.
    * @param qvars the specified variable table.
    * @param user the specified principal.
    * @param rep the specified local query repository.
    * @param queries the queries
    */
   public TableLens getXNodeTableLens(XQuery query, VariableTable qvars,
                                           Principal user, XQueryRepository rep,
                                           Hashtable queries)
      throws Exception
   {
      return getXNodeTableLens(query, qvars, user, rep, queries, -1);
   }

   /**
    * Get the XNodeTableLens from a query.
    * @param query the specified query.
    * @param qvars the specified variable table.
    * @param user the specified principal.
    * @param rep the specified local query repository.
    * @param queries the queries.
    * @param ts touch timestamp of data changes.
    */
   public TableLens getXNodeTableLens(final XQuery query,
                                           final VariableTable qvars,
                                           final Principal user,
                                           final XQueryRepository rep,
                                           final Hashtable queries, long ts)
      throws Exception
   {
      if(query == null) {
         return null;
      }

      DataCacheResult item = getCacheVisitor(XNodeTableLens.class, qvars, ts, query, user);
      boolean useCache = item.usecache;

      String queryId = null;

      try {
         queryId = addQueryInfo(query.getName(), user);
         TableLens table = null;
         XNode node;

         if(user instanceof XPrincipal) {
            qvars.copyParameters((XPrincipal) user);
         }

         try {
            node = getXNode(qvars, query, user, queries, item);
            HashMap map = fixHintMaxRow(node, query);
            table = buildXTable(query, node, queryId, item, useCache, qvars);

            XNodeTableLens xNodeTableLens =
               (XNodeTableLens) Util.getNestedTable(table, XNodeTableLens.class);
            xNodeTableLens.setMaxRowHintMap(map);
         }
         catch(SQLExpressionFailedException sqlExpressionException) {
            LOG.error("A SQL Expression failed for query: {}", query);

            Tool.addUserWarning(Catalog.getCatalog().getString("common.table.getDataFailed") +
                                   ": " + sqlExpressionException.getMessage());
            throw sqlExpressionException;
         }
         catch(ExpressionFailedException | MessageException esEx) {
            throw esEx;
         }
         catch(CancelledException cancelledException) {
            if(LOG.isDebugEnabled()) {
               LOG.debug("Query {}, was cancelled for user, {}", query.getName(), user);
            }

            Tool.addUserMessage(Catalog.getCatalog().getString("common.table.queryCancelled"));
         }
         catch(Exception ex) {
            LOG.error("Failed to get table data for query: {}, parameters: {}, user: {}",
                      query, qvars, user, ex);
            Tool.addUserWarning(Catalog.getCatalog().getString("common.table.getDataFailed") +
                                ": " + ex.getMessage());

            if(Boolean.TRUE.equals(qvars.get("__is_scheduler__"))) {
               throw ex;
            }
         }

         return table;
      }
      finally {
         removeQueryInfo(queryId);
      }
   }

   private TableLens buildXTable(XQuery query, XNode node, String queryId,
                                      DataCacheResult item, boolean useCache,
                                      VariableTable qvars)
      throws Exception
   {
      XNodeTableLens table = null;
      node.setAttribute("queryId", queryId);

      if(item.isDataCached()) {
         table = (XNodeTableLens) item.getCachedData();

         if(table != null) {
            LOG.debug("Using cached result set: " + query.getName() + " rows: " +
                     table.getRowCount());
         }
      }

      if(table == null) {
         table = new XNodeTableLens(node);

         if(table.isCancelled()) {
            throw new CancelledException(
               Catalog.getCatalog().getString("Query abandoned") + ".");
         }

         if(useCache) {
            CEntry cacheEntry =
               new CEntry(table, System.currentTimeMillis());
            addCacheData(item.getCacheKey(), cacheEntry,
               qvars.get("_FORM_"));
         }
      }

      if(useCache) {
         table = table.cloneShared();
      }

      if(item.getCacheNormalizer() != null) {
         return item.getCacheNormalizer().transformTableLens(table);
      }

      return table;
   }

   /**
    * Add data to the cache.
    * @param key the key in cachemap.
    * @param data the data item to add into cache.
    * @param timeout if need to set short timeout.
    */
   private void addCacheData(String key, CEntry data, Object timeout) {
      // if contains form assembly, we should cache with short timeout
      // so that the data will update immediately after update the
      // database when reopen the viewsheet, and on the other hand, we
      // should avoid to clear other cache data which do not need to
      // cache with short timeout.
      if("true".equals(timeout)) {
         dataCache.put(key, data, 5000);
      }
      else {
         dataCache.put(key, data);
      }
   }

   public void removeCacheData(String key) {
      dataCache.remove(key);
   }

   public void removeQueryCacheData(XQuery query, VariableTable vars, Principal user, Class<?> type) throws Exception {
      if(service instanceof XEngine) {
         ((XEngine) service).removeQueryCache(session, query, vars, user, type);
      }
   }

   /**
    * Get the cache visitor.
    */
   private DataCacheResult getCacheVisitor(Class<?> type, VariableTable qvars, long ts,
                                           XQuery query, Principal user)
         throws Exception
   {
      boolean useCache = this.useCache;
      final Object cachedData = qvars.get(CACHED_DATA);

      // try fetching cached data option from variable table
      if(!useCache && "true".equals(cachedData)) {
         useCache = true;
      }
      else if(useCache && "false".equals(cachedData)) {
         useCache = false;
      }

      Long touched = (Long) qvars.get("___TOUCH_TIME___");

      if(touched != null) {
         ts = touched;
      }

      boolean refresh = "true".equals(qvars.get("__refresh_report__"));
      JDBCQueryCacheNormalizer cacheNormalizer = getJDBCQueryCacheNormalizer(query, qvars, user);
      return new DataCacheResult(dataCache, type, useCache, refresh, ts, cacheNormalizer);
   }

   private JDBCQueryCacheNormalizer getJDBCQueryCacheNormalizer(XQuery query, VariableTable qvars,
                                                                Principal user)
      throws Exception
   {
      if(!(query instanceof JDBCQuery)) {
         return null;
      }

      query = VpmProcessor.getInstance().applyHiddenColumns(query, qvars, user);
      return new JDBCQueryCacheNormalizer((JDBCQuery) query);
   }

   /**
    * Helper method for execute().
    */
   private XNode getXNode(VariableTable vars, XQuery xquery,
                          Principal user,
                          Hashtable queries, DataCacheVisitor item)
      throws Exception
   {
      String qname = xquery.getName();

      if(queries != null && qname == null) {
         String q = (String) queries.get(qname);

         if(q == null) {
            IdentityID[] roles = null;

            if(user instanceof XPrincipal) {
               roles = ((XPrincipal) user).getRoles();
            }

            StringBuilder rolestr = new StringBuilder("[");

            if(roles != null) {
               for(int i = 0; i < roles.length; i++) {
                  rolestr.append(roles[i]);
               }
            }

            rolestr.append("]");

            LOG.warn("Access denied to query: {} for roles {}", qname, rolestr);
            return null;
         }
      }

      return service.execute(session, xquery, vars, user, false, item);
   }

   /**
    * Clean up.
    */
   @Override
   protected void finalize() {
      tearDown();
   }

   /**
    * Clear data cache.
    */
   public void clearCache() {
      dataCache.clear();
   }

   /**
    * Set if query result should be cached.
    * @param useCache - true to cache query result
    */
   public void setCacheData(boolean useCache) {
      this.useCache = useCache;
   }

   /**
    * Add the query info into the current static query map.
    */
   private String addQueryInfo(String queryName, Principal principal) {
      if(MonitorLevelService.getMonitorLevel() <= 0 || queryName == null) {
         return null;
      }

      List<String> infos = XUtil.QUERY_INFOS.get();

      if(infos == null) {
         infos = new ArrayList<>();
         XUtil.QUERY_INFOS.set(infos);
      }

      if(infos.size() < 1) {
         infos.add(0, null);
      }

      // record query by the user who is running the query instead of MV user, which may
      // be used for the purpose of VPM. (61361)
      Principal principal0 = ThreadContext.getContextPrincipal();
      IdentityID user = principal0 == null ? null : IdentityID.getIdentityIDFromKey(principal0.getName());

      if(user == null) {
         principal0 = principal;
         user = principal != null ? IdentityID.getIdentityIDFromKey(principal.getName()) : null;
      }

      String asset = infos.size() > 0 ? infos.get(0) : null;
      String queryId = XSessionService.createSessionID(XSessionService.QUERY, queryName);
      infos.add(1, queryId);
      QueryInfo qinfo = new QueryInfo(queryId,
         "Thread" + Thread.currentThread().getId(), queryName, user, asset, 0, new Date());

      if(principal0 instanceof XPrincipal) {
         String task = ((XPrincipal) principal0).getProperty("__TASK_NAME__");

         if(task != null) {
            qinfo.setTask(task);
         }
      }

      XUtil.queryMap.put(queryId, qinfo);
      emap.addObject(queryId);

      fireQueryExecutionEvent(queryId, true);
      return queryId;
   }

   /**
    * Remove the query info from the current static query map. Mark the query
    * as a completed query.
    */
   public static void removeQueryInfo(String queryId) {
      if(queryId != null) {
         XUtil.queryMap.remove(queryId);
         emap.setCompleted(queryId);
         fireQueryExecutionEvent(queryId, false);
      }
   }

   /**
    * An entry holding information related to a cache.
    */
   private static class CEntry {
      public CEntry(XNodeTableLens lens, long touchtime) {
         this.lens = lens;
         this.touchtime = touchtime;
         this.type = lens.getClass();
      }

      private final XNodeTableLens lens;
      private final long touchtime;
      private final Class<? extends XNodeTableLens> type;
   }

   /**
    * Get the ids of all executing queries during a sample period.
    */
   public static List getExecutingQueries() {
      return emap.getObjects();
   }

   public static void addQueryExecutionListener(QueryExecutionListener l) {
      synchronized(queryExecutionListeners) {
         queryExecutionListeners.add(l);
      }
   }

   public static void removeQueryExecutionListener(QueryExecutionListener l) {
      synchronized(queryExecutionListeners) {
         queryExecutionListeners.remove(l);
      }
   }

   private static void fireQueryExecutionEvent(String queryId, boolean started) {
      QueryExecutionEvent event = new QueryExecutionEvent(XSessionManager.class, queryId);
      List<QueryExecutionListener> queryExecutionListeners;

      synchronized(XSessionManager.queryExecutionListeners) {
         queryExecutionListeners = new ArrayList<>(XSessionManager.queryExecutionListeners);
      }

      for(QueryExecutionListener l : queryExecutionListeners) {
         if(started) {
            l.queryExecutionStarted(event);
         }
         else {
            l.queryExecutionFinished(event);
         }
      }
   }

   public static class DataCacheResult implements DataCacheVisitor {
      public DataCacheResult(DataCache<String, CEntry> datacache, Class<?> type, boolean usecache,
                             boolean refresh, long touched, JDBCQueryCacheNormalizer cacheNormalizer)
      {
         this.datacache = datacache;
         this.type = type;
         this.usecache = usecache;
         this.refresh = refresh;
         this.touched = touched;
         this.cacheNormalizer = cacheNormalizer;
      }

      @Override
      public boolean visitCache(String key) {
         this.key = key + type;

          if(usecache && !refresh) {
            CEntry entry = datacache.get(this.key);
            XNodeTableLens table = null;

            if(entry != null && type.isAssignableFrom(entry.type) &&
               (touched == -1 || touched < entry.touchtime))
            {
               table = entry.lens;
            }

            if(table != null) {
               // if the table is broken, don't share it since it would be
               // incomplete
               if(!table.isCancelled() && table.isValid()) {
                  this.lens = table;
                  return true;
               }
            }
         }

         return false;
      }

      public String getCacheKey() {
         return key;
      }

      public boolean isDataCached() {
         return lens != null;
      }

      public Object getCachedData() {
         return lens;
      }

      public JDBCQueryCacheNormalizer getCacheNormalizer() {
         return cacheNormalizer;
      }

      protected DataCache<String, CEntry> datacache;
      protected boolean usecache;
      protected long touched;
      protected boolean refresh;
      protected Class<?> type;

      protected String key;
      protected Object lens;
      private final JDBCQueryCacheNormalizer cacheNormalizer;
   }

   private static final ExecutionMap emap = new ExecutionMap();
   private XDataService service;
   private Object session;
   private final Map<ReportSheet, String> executemap = new ConcurrentHashMap<>();
   private final DataCache<String, CEntry> dataCache = new DataCache<>();
   private boolean useCache = false;
   private static final Set<QueryExecutionListener> queryExecutionListeners = new HashSet<>();

   private static final Logger LOG =
      LoggerFactory.getLogger(XSessionManager.class);

   public static final class Reference
      extends SingletonManager.Reference<XSessionManager>
   {
      @Override
      public synchronized XSessionManager get(Object ... parameters) {
         if(manager == null) {
            try {
               manager = new XSessionManager();
               String prop = SreeEnv.getProperty("query.cache.limit");

               if(prop != null) {
                  manager.dataCache.setLimit(Integer.parseInt(prop));
               }

               prop = SreeEnv.getProperty("query.cache.timeout");

               if(prop != null) {
                  manager.dataCache.setTimeout(Long.parseLong(prop));
               }

               prop = SreeEnv.getProperty("query.cache.data");

               // @by larryl, defaults to cache with short timeout so at least a
               // query used multiple times in one report is cached
               if(prop == null) {
                  manager.setCacheData(true);
                  manager.dataCache.setTimeout(30000);
               }
               else {
                  manager.setCacheData(prop.equalsIgnoreCase("true"));
               }

               DataSourceRegistry.getRegistry().addRefreshedListener(
                  evt -> {
                     try {
                        XSessionManager.getSessionManager().clearCache();
                     }
                     catch(Exception ex) {
                        LOG.warn("Failed to clear the cache " +
                           "after the data source registry was refreshed", ex);
                     }
                  }
               );

               DataSourceRegistry.getRegistry().addModifiedListener(
                  evt -> {
                     try {
                        XSessionManager.getSessionManager().clearCache();
                     }
                     catch(Exception ex) {
                        LOG.warn("Failed to clear the cache " +
                           "after the data source registry was modified", ex);
                     }
                  }
               );

               manager.bind(System.getProperty("user.name"));
            }
            catch(Exception ex) {
               LOG.error("Failed to initialize the session manager", ex);
               throw new RuntimeException("Failed to initialize the session manager", ex);
            }
         }

         return manager;
      }

      @Override
      public synchronized void dispose() {
         if(manager != null) {
            manager.tearDown();
            manager = null;
         }
      }

      private XSessionManager manager;
   }
}
