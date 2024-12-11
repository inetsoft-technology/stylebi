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
package inetsoft.web.admin.query;

import inetsoft.report.XSessionManager;
import inetsoft.report.internal.Util;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.schedule.ScheduleClient;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.util.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.admin.monitoring.*;
import inetsoft.web.admin.schedule.ScheduleQueriesStatus;
import inetsoft.web.cluster.ServerClusterClient;

import java.rmi.RemoteException;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class QueryService
   extends MonitorLevelService implements MessageListener, StatusUpdater, QueryExecutionListener
{
   @Autowired
   public QueryService(ServerClusterClient clusterClient,
                       MonitoringDataService monitoringDataService)
   {
      super(lowAttrs, medAttrs, new String[0]);
      this.clusterClient = clusterClient;
      this.monitoringDataService = monitoringDataService;
   }

   @PostConstruct
   public void addListener() {
      cluster = Cluster.getInstance();
      cluster.addMessageListener(this);
      XSessionManager.addQueryExecutionListener(this);
      XNodeTable.addQueryExecutionListener(this);
   }

   @PreDestroy
   public void removeListener() {
      if(cluster != null) {
         cluster.removeMessageListener(this);
      }

      XSessionManager.removeQueryExecutionListener(this);
      XNodeTable.removeQueryExecutionListener(this);
   }

   @Override
   public void updateStatus(long timestamp) {
      int queryCount = (int) Stream.concat(
         XSessionManager.getExecutingQueries().stream(),
         XNodeTable.getExecutingQueries().stream())
         .count();
      QueryHistory history = QueryHistory.builder()
         .timestamp(timestamp)
         .queryCount(queryCount)
         .build();

      clusterClient.addStatusHistory(StatusMetricsType.QUERY_METRICS, null, null, history);
      updateQueryMetrics();
   }

   private void updateQueryMetrics() {
      ServerClusterClient.getDebouncer().debounce(
         "QueryService.updateMetrics", 2, TimeUnit.SECONDS, this::updateQueryMetrics0);
   }

   private void updateQueryMetrics0() {
      QueryMetrics.Builder builder = QueryMetrics.builder();
      QueryMetrics oldMetrics = clusterClient.getMetrics(StatusMetricsType.QUERY_METRICS, null);

      if(oldMetrics != null) {
         builder.from(oldMetrics);
         Deque<Integer> executedRows = new ArrayDeque<>(oldMetrics.executedRows());

         executedRows.addLast(XNodeTable.getExecutedRows());
         XNodeTable.resetExecutedRows();

         if(executedRows.size() > 12) {
            executedRows.removeFirst();
         }

         builder.executedRows(executedRows);
      }

      builder.count(isLevelQualified("count") ? getCount() : 0);
      builder.queries(getQueries());
      clusterClient.setMetrics(StatusMetricsType.QUERY_METRICS, builder.build(),
         metrics -> monitoringDataService.update());
   }

   @Override
   public void messageReceived(MessageEvent event) {
      String sender = event.getSender();

      if(event.getMessage() instanceof DestroyQueriesMessage) {
         handleDestroyQueriesMessage(sender, (DestroyQueriesMessage) event.getMessage());
      }
   }

   private void handleDestroyQueriesMessage(String address, DestroyQueriesMessage destroyMessage) {
      DestroyQueriesCompleteMessage message = new DestroyQueriesCompleteMessage();

      try {
         destroy(destroyMessage.getIds());
         cluster.sendMessage(address, message);
      }
      catch(Exception e) {
         LOG.warn("Failed to send destroy queries.", e);
      }
   }

   public int getThroughput() {
      QueryMetrics metrics = clusterClient.getMetrics(StatusMetricsType.QUERY_METRICS, null);

      if(metrics == null) {
         return 0;
      }

      return metrics.throughput();
   }

   /**
    * Get the number of queries currently being executed.
    */
   public int getCount() {
      if(!isLevelQualified("count")) {
         throw new RuntimeException(catalog.getString("monitor.monitorDisabled"));
      }

      Set<String> ids = XNodeTable.queryMap.keySet();
      return ids.size();
   }

   /**
    * Gets information about the executing queries of cluster node address.
    *
    * @return the query details.
    */
   public List<QueryMonitoringTableModel> getQueries(String server, Principal principal) {
      List<QueryModel> queryModels;

      if(StringUtils.hasText(server)) {
         QueryMetrics metrics = clusterClient.getMetrics(StatusMetricsType.QUERY_METRICS, server);

         if(metrics != null) {
            queryModels = metrics.queries();
         }
         else {
            queryModels = Collections.emptyList();
         }
      }
      else {
         queryModels = this.getQueries();
      }

      ScheduleQueriesStatus scheduleQueries = getScheduleQueries(server);

      if(scheduleQueries != null) {
         queryModels = new ArrayList<>(queryModels);
         queryModels.addAll(scheduleQueries.getQueries());
      }

      // Filter viewsheets
      String orgID = OrganizationManager.getInstance().getInstance().getCurrentOrgID(principal);
      queryModels = queryModels.stream()
         .filter(model -> model.user() != null && Tool.equals(model.user().orgID, orgID))
         .collect(Collectors.toList());

      return queryModels.stream().map(q -> QueryMonitoringTableModel.builder().from(q).build())
         .collect(Collectors.toList());
   }

   private ScheduleQueriesStatus getScheduleQueries(String address) {
      return getScheduleMetrics(
         address,
         clusterClient,
         server -> {
            try {
               return ScheduleClient.getQueries(null, server);
            }
            catch(RemoteException e) {
               throw new RuntimeException(e);
            }
         },
         metrics -> metrics.getQueries());
   }

   /**
    * Gets information about the executing queries.
    *
    * @return the query details.
    */
   public List<QueryModel> getQueries() {
      boolean includeRowCount = isLevelQualified("rowCount");
      return getQueries(getInfo(), includeRowCount);
   }

   public List<QueryHistory> getHistory(String clusterNode) {
      Queue<QueryHistory> history = clusterClient
         .getStatusHistory(StatusMetricsType.QUERY_METRICS, clusterNode, null);

      return history == null ? Collections.emptyList() : new ArrayList<>(history);
   }

   /**
    * Get the detail infos of all excuting queries.
    *
    * @return the query infos.
    */
   private List<QueryInfo> getInfo() {
      if(!isLevelQualified("info")) {
         return Collections.emptyList();
      }

      return getQueryInfos();
   }

   public static List<QueryModel> getQueries(List<QueryInfo> infos, boolean includeRowCount) {
      return infos.stream()
         .map(i -> QueryModel.builder().from(i, includeRowCount).build())
         .collect(Collectors.toList());
   }

   public static List<QueryInfo> getQueryInfos() {
      List<QueryInfo> qlist = new ArrayList<>();

      for(Map.Entry<String, QueryInfo> entry : XNodeTable.queryMap.entrySet()) {
         if(entry.getValue() != null) {
            qlist.add((QueryInfo) entry.getValue().clone());
         }
      }

      for(Map.Entry<String, QueryInfo> entry : XUtil.queryMap.entrySet()) {
         if(entry.getValue() != null && !XNodeTable.queryMap.containsKey(entry.getKey())) {
            qlist.add((QueryInfo) entry.getValue().clone());
         }
      }
      return qlist;
   }

   /**
    * Destroy an executing query.
    *
    * @param id the unique identifier for the query.
    */
   public void destroy(String id) throws Exception {
      if(id == null || "".equals(id.trim())) {
         throw new Exception(catalog.getString("queryMonitor.inputQueryId"));
      }

      List<QueryInfo> infos = getInfo();
      boolean found = false;

      for(QueryInfo info : infos) {
         if(Tool.equals(info.getId(), id)) {
            found = true;

            if(XNodeTable.queryMap.containsKey(id)) {
               info = XNodeTable.queryMap.get(id);
            }
            else if(XUtil.queryMap.containsKey(id)) {
               info = XUtil.queryMap.get(id);
            }

            QueryManager qmr = info != null ? info.getQueryManager() : null;

            if(qmr != null) {
               qmr.cancel();

               // remove the query info from the map in the XNodeTable
               if(XNodeTable.queryMap.containsKey(id)) {
                  XNodeTable.removeQueryInfo(id);
               }

               // remove the query info from the map in the session
               if(XUtil.queryMap.containsKey(id)) {
                  XSessionManager.removeQueryInfo(id);
               }
            }

            break;
         }
      }

      if(!found) {
         LOG.debug("Could not destroy query, does not exist: {}", id);
      }
   }

   public void destroyClusterQueries(String address, String[] ids) throws Exception {
      if(StringUtils.isEmpty(address)) {
         destroy(ids);
      }
      else {
         try {
            cluster.exchangeMessages(
               address, new DestroyQueriesMessage(ids), DestroyQueriesCompleteMessage.class);
         }
         catch(Exception e) {
            LOG.warn("Failed to destroy queries from cluster", e);
         }
      }
   }

   /**
    * Destroy some executing queries.
    *
    * @param ids the unique identifier arrays for the query.
    */
   public void destroy(String[] ids) throws Exception {
      if(ids == null || ids.length == 0) {
         throw new Exception(catalog.getString("queryMonitor.inputQueryId"));
      }

      for(String id : ids) {
         destroy(id);
      }
   }

   /**
    * Get Composer Data Max Rows of the manager.
    *
    * @return the maxRows of Composer Data Max Rows.
    */
   public int getComposerDataMaxRows() {
      return Util.getQueryPreviewMaxrow();
   }

   /**
    * Get Composer Data Timeout of the manager.
    *
    * @return the timeout of Composer Data Timeout.
    */
   public long getComposerDataTimeout() {
      return Long.parseLong(SreeEnv.getProperty("query.preview.timeout"));
   }

   /**
    * Set Composer Data Max Rows to the manager.
    *
    * @param maxRows the Composer Data Max Rows.
    */
   public void setComposerDataMaxRows(int maxRows) throws Exception {
      if(maxRows < 0) {
         throw new Exception(Catalog.getCatalog().getString(
            "queryManager.invalidValue"));
      }

      SreeEnv.setProperty("query.preview.maxrow", maxRows + "");

      try {
         SreeEnv.save();
      }
      catch(Exception e) {
         throw new Exception(Catalog.getCatalog().getString(
            "monitor.setFail", "Composer Data Max Rows"), e);
      }
   }

   /**
    * Set Composer Data Timeout to the manager.
    *
    * @param timeout the Composer Data Timeout.
    */
   public void setComposerDataTimeout(long timeout) throws Exception {
      if(timeout < 0) {
         throw new Exception(Catalog.getCatalog().getString(
            "queryManager.invalidValue"));
      }

      SreeEnv.setProperty("query.preview.timeout", timeout + "");

      try {
         SreeEnv.save();
      }
      catch(Exception e) {
         throw new Exception(Catalog.getCatalog().getString(
            "monitor.setFail", "Composer Data Timeout"), e);
      }
   }

   @Override
   public void queryExecutionStarted(QueryExecutionEvent event) {
      updateStatus();
   }

   @Override
   public void queryExecutionFinished(QueryExecutionEvent event) {
      updateStatus();
   }

   private void updateStatus() {
      updateQueryMetrics();
   }

   private final ServerClusterClient clusterClient;
   private final MonitoringDataService monitoringDataService;
   private Cluster cluster;
   private final Catalog catalog = Catalog.getCatalog();

   private static final Logger LOG = LoggerFactory.getLogger(QueryService.class);
   private static final String[] medAttrs = { "rowCount" };
   private static final String[] lowAttrs = {
      "count", "info", "id", "threadId", "name", "user", "asset", "dateCreated"
   };
}
