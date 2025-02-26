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
package inetsoft.web.admin.schedule;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.storage.ExternalStorageService;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.web.admin.content.repository.ResourcePermissionService;
import inetsoft.web.admin.schedule.model.*;
import inetsoft.web.admin.schedule.model.CheckMailInfo;
import inetsoft.web.admin.security.ResourcePermissionModel;
import inetsoft.web.viewsheet.Audited;
import inetsoft.web.viewsheet.model.dialog.schedule.TimeRangeModel;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static inetsoft.sree.internal.SUtil.isCluster;
import static inetsoft.sree.internal.SUtil.isSecurityOn;

@Service
public class SchedulerConfigurationService {
   @Autowired
   public SchedulerConfigurationService(ScheduleClient scheduleClient,
                                        ResourcePermissionService permissionService)
   {
      this.scheduleClient = scheduleClient;
      this.permissionService = permissionService;
      this.externalStorageService = ExternalStorageService.getInstance();
   }

   public ScheduleConfigurationModel getConfiguration(Principal principal) throws Exception {
      Catalog catalog = Catalog.getCatalog(principal);
      List<TimeRangeModel> ranges = TimeRange.getTimeRanges().stream()
         .sorted()
         .map(r -> createModel(r, principal, catalog))
         .collect(Collectors.toList());

      return ScheduleConfigurationModel.builder()
         .concurrency(Integer.parseInt(SreeEnv.getProperty("schedule.concurrency")))
         .logFile("fluentd".equals(SreeEnv.getProperty("log.provider")) ?
                     null : SreeEnv.getProperty("schedule.log.file"))
         .rmiPort(Integer.parseInt(SreeEnv.getProperty("scheduler.rmi.port")))
         .classpath(SreeEnv.computePropertyIfAbsent(
            "scheduler.classpath", () -> SUtil.getApplicationClasspath()))
         .autoStart(getBooleanProperty("schedule.auto.start"))
         .autoStop(getBooleanProperty("schedule.auto.down"))
         .notificationEmail(getBooleanProperty("schedule.options.notificationEmail"))
         .saveToDisk(getBooleanProperty("schedule.options.saveToDisk"))
         .emailDelivery(getBooleanProperty("schedule.options.emailDelivery"))
         .enableEmailBrowser(getBooleanProperty("schedule.options.emailBrowserEnable"))
         .minMemory(Integer.parseInt(SreeEnv.getProperty("schedule.memory.min")))
         .maxMemory(Integer.parseInt(SreeEnv.getProperty("schedule.memory.max")))
         .emailAddress(SreeEnv.getProperty("schedule.status.check.email"))
         .emailSubject(SreeEnv.getProperty("schedule.status.check.email.subject"))
         .emailMessage(SreeEnv.getProperty("schedule.status.check.email.message"))
         .notifyIfDown(getBooleanProperty("schedule.options.scheduleIsDown"))
         .notifyIfTaskFailed(getBooleanProperty("schedule.options.taskFailed"))
         .shareTaskInSameGroup(getBooleanProperty("schedule.options.shareTaskInGroup"))
         .deleteTaskOnlyByOwner(getBooleanProperty("schedule.options.deleteTaskOnlyByOwner"))
         .timeRanges(ranges)
         .serverLocations(SUtil.getServerLocations())
         .saveAutoSuffix(SreeEnv.getProperty("schedule.save.autoSuffix"))
         .securityEnable(isSecurityOn())
         .cloudSecrets(Tool.isCloudSecrets())
         .build();
   }

   private TimeRangeModel createModel(TimeRange range, Principal user, Catalog catalog) {
      ResourcePermissionModel permissions = permissionService.getTableModel(
         range.getName(), ResourceType.SCHEDULE_TIME_RANGE, EnumSet.of(ResourceAction.ACCESS),
         user);
      return TimeRangeModel.builder()
         .from(range, catalog)
         .permissions(permissions)
         .build();
   }

   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "Scheduler-Settings",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void setConfiguration(ScheduleConfigurationModel model, Principal principal)
      throws Exception
   {
      SreeEnv.setProperty("schedule.concurrency", Integer.toString(model.concurrency()));
      SreeEnv.setProperty("scheduler.rmi.port", Integer.toString(model.rmiPort()));
      SreeEnv.setProperty("scheduler.classpath", model.classpath());
      SreeEnv.setProperty("schedule.status.check.email", model.emailAddress());
      SreeEnv.setProperty("schedule.status.check.email.subject", model.emailSubject());
      SreeEnv.setProperty("schedule.status.check.email.message", model.emailMessage());
      SreeEnv.setProperty("schedule.auto.start", Boolean.toString(model.autoStart()));
      SreeEnv.setProperty("schedule.auto.down", Boolean.toString(model.autoStop()));
      SreeEnv.setProperty("schedule.options.notificationEmail", Boolean.toString(model.notificationEmail()));
      SreeEnv.setProperty("schedule.options.saveToDisk", Boolean.toString(model.saveToDisk()));
      SreeEnv.setProperty("schedule.options.emailDelivery", Boolean.toString(model.emailDelivery()));
      SreeEnv.setProperty("schedule.options.emailBrowserEnable", Boolean.toString(model.enableEmailBrowser()));
      SreeEnv.setProperty("schedule.options.scheduleIsDown", Boolean.toString(model.notifyIfDown()));
      SreeEnv.setProperty("schedule.options.taskFailed", Boolean.toString(model.notifyIfTaskFailed()));
      SreeEnv.setProperty("schedule.memory.min", Integer.toString(model.minMemory()));
      SreeEnv.setProperty("schedule.memory.max", Integer.toString(model.maxMemory()));
      SreeEnv.setProperty("schedule.options.shareTaskInGroup", Boolean.toString(model.shareTaskInSameGroup()));
      SreeEnv.setProperty("schedule.options.deleteTaskOnlyByOwner", Boolean.toString(model.deleteTaskOnlyByOwner()));
      setTimeRanges(model.timeRanges(), principal);
      setServerLocations(model.serverLocations());
      SreeEnv.setProperty("schedule.save.autoSuffix", model.saveAutoSuffix());
      SreeEnv.save();

      if(InetsoftConfig.getInstance().getCloudRunner() != null) {
         Cluster.getInstance().sendMessage(new RestartSchedulerMessage());
      }
   }

   public ScheduleStatusModel getStatus() {
      boolean running = this.scheduleClient.isReady();
      Catalog catalog = Catalog.getCatalog();
      boolean cluster = isClusterServer();
      ScheduleStatusModel.Builder builder = ScheduleStatusModel.builder().cluster(cluster)
         .externalStorageLocation(externalStorageService.getStorageLocation());

      if(cluster) {
         List<ScheduleClusterStatusModel> clusterList = new ArrayList<>();
         Set<String> servers = new HashSet<>();
         String[] nodes = scheduleClient.getScheduleServers();
         final Instant now = Instant.now();

         for(int i = 0; i < nodes.length; i++) {
            if(servers.contains(nodes[i])) {
               continue;
            }

            ScheduleClusterStatusModel.Builder clusterBuilder = ScheduleClusterStatusModel.builder();
            servers.add(nodes[i]);
            clusterBuilder = clusterBuilder.server(nodes[i]);
            Date startTime = ScheduleClient.getScheduleStartDate(nodes[i]);

            if(startTime == null) {
               clusterBuilder = clusterBuilder.uptime(catalog.getString("Not ready"));
            }
            else {
               final Duration duration = Duration.between(
                  Instant.ofEpochMilli(startTime.getTime()), now);
               clusterBuilder.uptime(DurationFormatUtils.formatDuration(duration.toMillis(),
                                                               "d'd' H'h' m'm' s's'"));
            }

            clusterList.add(clusterBuilder.build());
         }

         builder = builder.clusterStatusTable(clusterList);
      }
      else {
         builder = builder.status(Catalog.getCatalog().getString(running ? "Running" : "Stopped"))
            .running(running);
      }

      return builder.build();
   }

   public void setStatus(ScheduleStatusModel status) throws Exception {
      setStatus(status.action());
   }

   public void setStatus(String action) throws Exception {
      switch(Objects.requireNonNull(action)) {
      case "start":
         SUtil.stopScheduler();
         SUtil.startScheduler();
         break;
      case "stop":
         SUtil.stopScheduler(true, true);
         break;
      case "restart":
         SUtil.stopScheduler();
         long start = System.currentTimeMillis();

         do {
            Thread.sleep(500L);
         }
         while(scheduleClient.isRunning() && System.currentTimeMillis() - start < 30000L);

         if(scheduleClient.isRunning()) {
            throw new IllegalStateException(
               "Time out waiting for scheduler to stop");
         }

         SUtil.startScheduler();
         break;
      }
   }

   public CheckMailInfo checkMail(CheckMailInfo mailParams, Principal principal) {
      Catalog catalog = Catalog.getCatalog(principal);
      String result;

      try {
         String emails = null;
         String queryNode = mailParams.sourceInfo();
         boolean checkUser = true;

         // For burst action, get email addresses from query. Have checked
         // user's address in burstAction.
         if(queryNode == null) {
            emails = mailParams.toAddresses();
         }

         SUtil.checkMail(emails, checkUser);
         result = catalog.getString("Test Mail Success");
      }
      catch(Exception e) {
         result = e.getMessage();
         LOG.debug("Failed to send email message", e);

         if(result == null || result.trim().length() == 0) {
            result = catalog.getString("Test Mail Failed");
         }
         else {
            result = catalog.getString("Test Mail Failed2", result);
         }
      }

      return CheckMailInfo.builder()
         .resultMessage(result)
         .build();
   }

   private boolean getBooleanProperty(String property) {
      return SreeEnv.getBooleanProperty(property, "true", "CHECKED");
   }

   private boolean isClusterServer() {
      return isCluster() || scheduleClient.isCluster();
   }

   private void setTimeRanges(List<TimeRangeModel> models, Principal principal) throws Exception {
      List<TimeRange> ranges = new ArrayList<>();

      for(TimeRangeModel model : models) {
         TimeRange range = new TimeRange(
            model.name(), model.startTime(), model.endTime(), model.defaultRange());
         ranges.add(range);

         if(model.modified() != null) {
            permissionService.setResourcePermissions(
               range.getName(), ResourceType.SCHEDULE_TIME_RANGE, model.permissions(), principal);
         }
      }

      ranges.sort(Comparator.naturalOrder());
      List<TimeRange> oldRanges = TimeRange.getTimeRanges();
      oldRanges.sort(Comparator.naturalOrder());

      TimeRange.setTimeRanges(ranges);

      if(!oldRanges.equals(ranges)) {
         //time ranges changed, redistribute
         try {
            new TaskBalancer().balanceTasks();
         }
         catch(Exception e) {
            LOG.error("Failed to balance schedule tasks", e);
         }
      }
   }

   private void setServerLocations(List<ServerLocation> locations) {
      if(locations == null || locations.isEmpty()) {
         SreeEnv.setProperty("server.save.locations", null);
      }
      else {
         ArrayList<String> paths = new ArrayList<>();

         for(ServerLocation location : locations) {
            String path = location.path();
            String label = location.label();
            StringBuilder pathInfo = new StringBuilder();
            ServerPathInfoModel infoModel = location.pathInfoModel();

            if(infoModel != null) {
               if(infoModel.ftp()) {
                  if(infoModel.useCredential()) {
                     boolean emptySecretId = Tool.isEmptyString(infoModel.secretId());

                     if(!emptySecretId) {
                        path = path + "?useSecretId=" + infoModel.useCredential();
                     }

                     pathInfo.append(path).append("|").append(label);

                     if(!emptySecretId) {
                        pathInfo.append("|").append(infoModel.secretId());
                     }
                  }
                  else {
                     String username =
                        !Tool.isEmptyString(infoModel.username()) ? infoModel.username() : null;
                     String password = username != null &&
                        !Tool.isEmptyString(infoModel.password()) ? infoModel.password() : null;
                     pathInfo.append(path).append("|").append(label);

                     if(username != null) {
                        pathInfo.append("|").append(username).append("|").append(password);
                     }
                  }
               }
               else {
                  pathInfo.append(path).append("|").append(label);
               }
            }

            paths.add(pathInfo.toString());
         }

         String property = String.join(";", paths);
         SreeEnv.setProperty("server.save.locations", property);
      }
   }

   private final ScheduleClient scheduleClient;
   private final ResourcePermissionService permissionService;
   private final ExternalStorageService externalStorageService;

   private static final Logger LOG = LoggerFactory.getLogger(SchedulerConfigurationService.class);
}
