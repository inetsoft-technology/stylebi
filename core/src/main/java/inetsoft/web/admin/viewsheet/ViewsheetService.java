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
package inetsoft.web.admin.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.schedule.ScheduleClient;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import inetsoft.web.admin.monitoring.*;
import inetsoft.web.admin.schedule.ScheduleViewsheetsStatus;
import inetsoft.web.admin.user.UserResourceCalculator;
import inetsoft.web.cluster.ServerClusterClient;

import java.rmi.RemoteException;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

@Service
public class ViewsheetService
   extends MonitorLevelService implements MessageListener, StatusUpdater
{

   @Autowired
   public ViewsheetService(inetsoft.analytic.composition.ViewsheetService engine,
                           ServerClusterClient client,
                           ViewsheetLifecycleMessageChannel viewsheetLifecycleMessageChannel,
                           MonitoringDataService monitoringDataService)
   {
      super(lowAttrs, medAttrs, new String[0]);
      this.engine = engine;
      this.client = client;
      this.viewsheetLifecycleMessageChannel = viewsheetLifecycleMessageChannel;
      this.monitoringDataService = monitoringDataService;
      listener = this::onViewsheetLifecycle;
   }

   @PostConstruct
   public void addListener() {
      cluster = Cluster.getInstance();
      cluster.addMessageListener(this);
      viewsheetLifecycleMessageChannel.subscribe(listener);
   }

   @PreDestroy
   public void removeListener() {
      if(cluster != null) {
         cluster.removeMessageListener(this);
      }

      viewsheetLifecycleMessageChannel.unsubscribe(listener);
   }

   @Override
   public void updateStatus(long timestamp) {
      ViewsheetMetrics.Builder builder = ViewsheetMetrics.builder();
      ViewsheetMetrics oldMetrics = client.getMetrics(StatusMetricsType.VIEWSHEET_METRICS, null);

      if(oldMetrics != null) {
         builder.from(oldMetrics);
      }

      updateViewsheetStatus(builder);
      int executing;

      synchronized(executingViewsheets) {
         executing = executingViewsheets.size();
         executingViewsheets.values().removeIf(count -> count <= 0);
      }

      ViewsheetHistory history = ViewsheetHistory.builder()
         .timestamp(timestamp)
         .executingViewsheets(executing)
         .build();

      client.addStatusHistory(StatusMetricsType.VIEWSHEET_METRICS, null, null, history);
      client.setMetrics(StatusMetricsType.VIEWSHEET_METRICS, builder.build());
   }

   public void onViewsheetLifecycle(ViewsheetLifecycleMessage event) {
      if(event.getState() == ViewsheetLifecycleState.EXECUTION_STARTED) {
         synchronized(executingViewsheets) {
            int count = executingViewsheets.getOrDefault(event.getRuntimeId(), 0);
            executingViewsheets.put(event.getRuntimeId(), count + 1);
         }
      }
      else if(event.getState() == ViewsheetLifecycleState.EXECUTION_COMPLETED) {
         synchronized(executingViewsheets) {
            executingViewsheets.computeIfPresent(event.getRuntimeId(), (key, count) -> count - 1);
         }
      }

      updateViewsheetStatus();
   }

   private void updateViewsheetStatus() {
      ServerClusterClient.getDebouncer()
         .debounce("ViewsheetService.updateMetrics", 2, TimeUnit.SECONDS, () ->
         {
            ViewsheetMetrics.Builder builder = ViewsheetMetrics.builder();
            updateViewsheetStatus(builder);
            client.setMetrics(StatusMetricsType.VIEWSHEET_METRICS, builder.build());
            monitoringDataService.update();
         });
   }

   private void updateViewsheetStatus(ViewsheetMetrics.Builder builder) {
      List<ViewsheetModel> active = getViewsheets(ViewsheetModel.State.OPEN);
      List<ViewsheetModel> executing = getViewsheets(ViewsheetModel.State.EXECUTING);

      builder.activeCount(active.size());
      builder.executingCount(executing.size());
      builder.activeViewsheets(active);
      builder.executingViewsheets(executing);
   }

   @Override
   public void messageReceived(MessageEvent event) {
      if(event.getMessage() instanceof DestroyViewsheetMessage) {
         handleDestroyViewsheetMessage((DestroyViewsheetMessage) event.getMessage());
      }
   }

   private void handleDestroyViewsheetMessage(DestroyViewsheetMessage reqMsg) {
      try {
         destroy(reqMsg.getIds());
      }
      catch(Exception e) {
         LOG.warn("Failed to destroy the viewsheet", e);
      }
   }

   /**
    * Get the count of vsinfo according the specified state.
    */
   public int getCount(ViewsheetModel.State state) {
      Objects.requireNonNull(state, "The viewsheet state cannot be null");

      if(!isLevelQualified("count") || engine == null) {
         return 0;
      }

      RuntimeViewsheet[] viewsheets = engine.getRuntimeViewsheets(null);

      if(viewsheets == null) {
         return 0;
      }

      int executed = Arrays.stream(viewsheets)
         .mapToInt(rvs -> engine.getExecutingThreads(rvs.getID()).size())
         .sum();

      if(state == ViewsheetModel.State.OPEN && viewsheets.length >= executed) {
         return viewsheets.length - executed;
      }

      return executed;
   }

   /**
    * Get the count of vsinfo according the specified state.
    */
   public int getCount(ViewsheetModel.State state, String node) {
      ViewsheetMetrics metrics = client.getMetrics(StatusMetricsType.VIEWSHEET_METRICS, node);

      if(metrics == null) {
         return 0;
      }

      if(state == ViewsheetModel.State.EXECUTING) {
         return metrics.executingCount();
      }
      else {
         return metrics.activeCount();
      }
   }

   /**
    * Destroy the viewsheet.
    */
   public void destroy(String id) throws Exception {
      if(id == null || id.trim().isEmpty()) {
         throw new IllegalArgumentException("The viewsheet ID is required");
      }

      checkVSExisted(id);

      if(engine != null) {
         engine.closeViewsheet(id, null);
      }
   }

   /**
    * Destroy the viewsheet.
    */
   public void destroy(String[] ids, String node) throws Exception {
      DestroyViewsheetMessage reqMsg = new DestroyViewsheetMessage(ids);
      cluster.sendMessage(node, reqMsg);
   }

   /**
    * Destroy the viewsheets.
    */
   public void destroy(String[] ids) throws Exception {
      destroyClusterNodeViewsheets(null, ids);
   }

   public void destroyClusterNodeViewsheets(String address, String[] ids) throws Exception {
      if(ObjectUtils.isEmpty(ids)) {
         throw new IllegalArgumentException("One or more viewsheet IDs is required");
      }

      if(StringUtils.isEmpty(address)) {
         for(String id : ids) {
            destroy(id);
         }
      }
      else {
         destroy(ids, address);
      }
   }

   /**
    * Get the executing thread infos.
    */
   public List<ViewsheetThreadModel> getThreads(String id) {
      if(id == null || id.trim().isEmpty()) {
         throw new IllegalArgumentException("The viewsheet ID is required");
      }


      if(engine == null) {
         return Collections.emptyList();
      }

      checkVSExisted(id);

      Vector<?> threads = engine.getExecutingThreads(id);

      // synchronize on returned vector to prevent concurrent modification exception
      synchronized(threads) {
         return threads.stream()
            .filter(o -> o instanceof WorksheetEngine.ThreadDef)
            .map(o -> (WorksheetEngine.ThreadDef) o)
            .map(t -> ViewsheetThreadModel.builder().from(t).build())
            .collect(Collectors.toList());
      }
   }

   /**
    * Get the executing thread infos.
    */
   public List<ViewsheetThreadModel> getThreads(String id, String node) {
      ViewsheetMetrics metrics = client.getMetrics(StatusMetricsType.VIEWSHEET_METRICS, node);

      if(metrics == null) {
         return Collections.emptyList();
      }

      return metrics.executingViewsheets().stream()
         .filter(vs -> vs.id().equals(id))
         .map(ViewsheetModel::threads)
         .findFirst()
         .orElse(Collections.emptyList());
   }

   /**
    * Get VSInfos according the viewsheet state, executing or open.
    */
   public List<ViewsheetModel> getViewsheets(ViewsheetModel.State state) {
      Objects.requireNonNull(state, "The viewsheet state cannot be null");

      if(engine == null) {
         return Collections.emptyList();
      }

      RuntimeViewsheet[] viewsheets = engine.getRuntimeViewsheets(null);
      List<ViewsheetModel> results = new ArrayList<>();

      for(RuntimeViewsheet rvs : viewsheets) {
         List<ViewsheetThreadModel> threads = getThreads(rvs.getID());

         if(state == ViewsheetModel.State.OPEN ||
            !threads.isEmpty() && state == ViewsheetModel.State.EXECUTING)
         {
            results.add(ViewsheetModel.builder()
               .from(rvs)
               .threads(threads)
               .state(state)
               .build());
         }
      }

      return results;
   }

   /**
    * Get VSInfos according the viewsheet state, executing or open.
    */
   public List<ViewsheetModel> getViewsheets(ViewsheetModel.State state, String node) {
      List<ViewsheetModel> viewsheets = new ArrayList<>();
      ScheduleViewsheetsStatus scheduleViewsheets = getScheduleViewsheets(node);

      if(node == null) {
         viewsheets.addAll(getViewsheets(state));

         if(scheduleViewsheets != null) {
            if(state == ViewsheetModel.State.EXECUTING) {
               viewsheets.addAll(scheduleViewsheets.getExecutingViewsheets());
            }
            else {
               viewsheets.addAll(scheduleViewsheets.getOpenViewsheets());
            }
         }

         // Filter viewsheets
         String orgName = OrganizationManager.getInstance().getCurrentOrgName();
         viewsheets = viewsheets.stream()
            .filter(vs -> vs.monitorUser() != null &&
               Tool.equals(vs.monitorUser().getOrganization(), orgName))
            .collect(Collectors.toList());

         return viewsheets;
      }

      ViewsheetMetrics metrics = client.getMetrics(StatusMetricsType.VIEWSHEET_METRICS, node);

      if(state == ViewsheetModel.State.EXECUTING) {
         if(metrics != null) {
            viewsheets.addAll(metrics.executingViewsheets());
         }

         if(scheduleViewsheets != null) {
            viewsheets.addAll(scheduleViewsheets.getExecutingViewsheets());
         }
      }
      else {
         if(metrics != null) {
            viewsheets.addAll(metrics.activeViewsheets());
         }

         if(scheduleViewsheets != null) {
            viewsheets.addAll(scheduleViewsheets.getOpenViewsheets());
         }
      }

      // Filter viewsheets
      List<IdentityID> users = getOrgUsers();
      Principal principal = ThreadContext.getContextPrincipal();
      boolean ssoUser = principal instanceof XPrincipal &&
         !"true".equals(((XPrincipal) principal).getProperty("__internal__"));

      if(ssoUser) {
         IdentityID id = ((XPrincipal) principal).getIdentityID();

         if(!users.contains(id)) {
            users.add(id);
         }
      }

      viewsheets = viewsheets.stream()
         .filter(vs -> users.contains(vs.monitorUser()))
         .collect(Collectors.toList());

      return viewsheets;
   }

   public List<ViewsheetMonitoringTableModel> getExecutingViewsheets(String server,
                                                                     Principal principal)
   {
      List<ViewsheetMonitoringTableModel> tableModels = new ArrayList<>();
      List<IdentityID> users = getOrgUsers(principal);
      String currentOrgName = OrganizationManager.getInstance().getCurrentOrgName(principal);

      for(ViewsheetModel info : getViewsheets(ViewsheetModel.State.EXECUTING, server)) {
         if(info.user() == null || !Tool.equals(info.user().organization, currentOrgName)) {
            continue;
         }

         List<ViewsheetThreadModel> threads = info.threads();

         for(ViewsheetThreadModel thread : threads) {
            ViewsheetMonitoringTableModel.Builder builder =
               ViewsheetMonitoringTableModel.builder();
            builder.from(info)
                   .thread("Thread" + thread.id());
            tableModels.add(builder.build());
         }
      }

      return tableModels;
   }

   List<ViewsheetMonitoringTableModel> getOpenViewsheets(String server, Principal principal) {
      String orgName = OrganizationManager.getInstance().getCurrentOrgName();

      return getViewsheets(ViewsheetModel.State.OPEN, server).stream()
         .filter(vs -> vs.user() != null && Tool.equals(vs.user().getOrganization(), orgName))
         .map(vs -> ViewsheetMonitoringTableModel.builder().from(vs).build())
         .collect(Collectors.toList());
   }

   public List<ViewsheetHistory> getHistory() {
      return getHistory(null);
   }

   public List<ViewsheetHistory> getHistory(String node) {
      Queue<ViewsheetHistory> history = client
         .getStatusHistory(StatusMetricsType.VIEWSHEET_METRICS, node, null);

      return history == null ? Collections.emptyList() : new ArrayList<>(history);
   }

   private void checkVSExisted(String id) {
      if(engine == null) {
         throw new IllegalStateException("The viewsheet engine has not been initialized");
      }

      if(engine.getSheet(id, null) == null) {
         throw new IllegalArgumentException("No viewsheet with ID \"" + id + "\" exists");
      }
   }

   /**
    * Calculate the user resource.
    */
   public void calculateUserResource(UserResourceCalculator cal) {
      if(!isLevelQualified("count")) {
         return;
      }

      for(ViewsheetModel info : getViewsheets(ViewsheetModel.State.OPEN)) {
         cal.addActiveVSCount(info.user());
      }

      for(ViewsheetModel info : getViewsheets(ViewsheetModel.State.EXECUTING)) {
         cal.addExecutingVSCount(info.user());
      }
   }

   private ScheduleViewsheetsStatus getScheduleViewsheets(String address) {
      return getScheduleMetrics(
         address,
         client,
         server -> {
            try {
               return ScheduleClient.getViewsheets(null, server);
            }
            catch(RemoteException e) {
               throw new RuntimeException(e);
            }
         },
         metrics -> metrics.getViewsheets());
   }

   private final inetsoft.analytic.composition.ViewsheetService engine;
   private final ServerClusterClient client;
   private final ViewsheetLifecycleMessageChannel viewsheetLifecycleMessageChannel;
   private final ViewsheetLifecycleEventListener listener;
   private final MonitoringDataService monitoringDataService;
   private Cluster cluster;
   private final Map<String, Integer> executingViewsheets = new HashMap<>();

   private static final String[] medAttrs = { "dateAccessed" };
   private static final String[] lowAttrs = {
      "count", "id", "state", "threadId", "name", "user", "dateCreated"
   };
   private static final Logger LOG = LoggerFactory.getLogger(ViewsheetService.class);
}
