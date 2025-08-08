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

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.*;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.schedule.model.ScheduleTaskChange;
import inetsoft.web.admin.schedule.model.ScheduleTaskModel;

import java.rmi.RemoteException;
import java.security.Principal;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

@Controller
@Scope(value = "websocket", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class ScheduleTaskChangeController {
   @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
   @Autowired
   public ScheduleTaskChangeController(AnalyticRepository repository,
                                       ScheduleService scheduleService,
                                       ScheduleManager scheduleManager,
                                       AssetRepository assetRepository, SimpMessagingTemplate messagingTemplate)
   {
      this.repository = repository;
      this.scheduleService = scheduleService;
      this.scheduleManager = scheduleManager;
      this.assetRepository = assetRepository;
      this.messagingTemplate = messagingTemplate;
      this.debouncer = new DefaultDebouncer<>();
   }

   @PostConstruct
   public void initCluster() {
      cluster = Cluster.getInstance();
   }

   @PreDestroy
   public synchronized void removeListener() {
      if(listener != null) {
         cluster.removeMessageListener(listener);
         listener = null;
         portalSubscribed = false;
         adminSubscribed = false;
      }
   }

   @SubscribeMapping(PORTAL_TOPIC)
   public synchronized void subscribePortal(Principal principal) {
      if(listener == null) {
         listener = this::messageReceived;
         subscriber = principal;
         cluster.addMessageListener(listener);
      }

      portalSubscribed = true;
   }

   @SubscribeMapping(ADMIN_TOPIC)
   public synchronized void subscribeAdmin(Principal principal) {
      if(listener == null) {
         listener = this::messageReceived;
         subscriber = principal;
         cluster.addMessageListener(listener);
      }

      adminSubscribed = true;
   }

   private void messageReceived(MessageEvent event) {
      if(event.getMessage() instanceof TaskActivityMessage) {
         dispatchPortalTaskActivity((TaskActivityMessage) event.getMessage());
         dispatchAdminTaskActivity((TaskActivityMessage) event.getMessage());
      }
      else if(event.getMessage() instanceof ScheduleTaskMessage) {
         dispatchAdminScheduleTask((ScheduleTaskMessage) event.getMessage());
         dispatchPortalScheduleTask((ScheduleTaskMessage) event.getMessage());
      }
   }

   private void dispatchPortalTaskActivity(TaskActivityMessage message) {
      String name = message.getTaskName();

      if(!shouldHandleReceivedMessage(name)) {
         return;
      }

      if(portalSubscribed && !ScheduleManager.isInternalTask(name) && checkPortalPermission(name)) {
         ScheduleTask task = scheduleManager.getScheduleTask(name, getSubscriberOrgID());
         ScheduleTaskModel taskModel = createModel(task, message);
         ScheduleTaskChange model = ScheduleTaskChange.builder()
            .name(taskModel == null ? message.getTaskName() : taskModel.name())
            .type(ScheduleTaskChange.Type.ACTIVITY)
            .task(taskModel)
            .build();
         messagingTemplate.convertAndSendToUser(
            SUtil.getUserDestination(subscriber), PORTAL_TOPIC, model);
      }
   }

   private boolean shouldHandleReceivedMessage(String taskID) {
      if(taskID == null) {
         return false;
      }

      IdentityID owner = SUtil.getTaskOwner(taskID);
      String taskOrgID = owner == null ?
         OrganizationManager.getInstance().getCurrentOrgID() : owner.getOrgID();

      return Tool.equals(taskOrgID, getSubscriberOrgID());
   }

   private String getSubscriberOrgID() {
      return OrganizationManager.getInstance().getCurrentOrgID(subscriber);
   }

   private void dispatchPortalScheduleTask(ScheduleTaskMessage message) {
      ScheduleTask task = message.getTask();
      String taskId = task == null ? null : task.getTaskId();

      if(!shouldHandleReceivedMessage(taskId)) {
         return;
      }

      if(!portalSubscribed || ScheduleManager.isInternalTask(taskId) ||
         taskId != null && !checkPortalPermission(taskId))
      {
         return;
      }

      ScheduleTaskChange.Type type;

      switch(message.getAction()) {
      case ADDED:
         type = ScheduleTaskChange.Type.ADDED;
         break;
      case REMOVED:
         type = ScheduleTaskChange.Type.REMOVED;
         break;
      case MODIFIED:
      default:
         type = ScheduleTaskChange.Type.MODIFIED;
      }

      TaskActivity activity = scheduleService.getActivity(message.getTaskName(), false);
      ScheduleTaskModel taskModel = task == null ? null : ScheduleTaskModel.builder()
         .fromTask(
            task, activity, Catalog.getCatalog(subscriber), scheduleService.isSecurityEnabled())
         .canDelete(scheduleService.canDeleteTask(task, subscriber))
         .build();
      ScheduleTaskChange model = ScheduleTaskChange.builder()
         .name(taskModel == null ? message.getTaskName() : taskModel.name())
         .type(type)
         .task(taskModel)
         .build();

      messagingTemplate.convertAndSendToUser(
         SUtil.getUserDestination(subscriber), PORTAL_TOPIC, model);
   }

   private void dispatchAdminTaskActivity(TaskActivityMessage message) {
      String name = message.getTaskName();

      if(!shouldHandleReceivedMessage(name)) {
         return;
      }

      ScheduleTask task = scheduleManager.getScheduleTask(name, getSubscriberOrgID());

      // not current org task, ignore it.
      if(task == null) {
         return;
      }

      try {
         task = scheduleService.handleInternalTaskConfiguration(task, subscriber);
      }
      catch(SecurityException e) {
         LOG.warn("Failed to check schedule task permission", e);
      }

      if(adminSubscribed && checkAdminPermission(task)) {
         ScheduleTaskChange model = ScheduleTaskChange.builder()
            .name(getTaskName(message.getTaskName()))
            .type(ScheduleTaskChange.Type.ACTIVITY)
            .task(createModel(task, message))
            .build();
         messagingTemplate.convertAndSendToUser(
            SUtil.getUserDestination(subscriber), ADMIN_TOPIC, model);
      }
   }

   private void dispatchAdminScheduleTask(ScheduleTaskMessage message) {
      ScheduleTask task = message.getTask();
      String taskID = task == null ? null : task.getTaskId();

      if(taskID == null) {
         taskID = message.getTaskName();
      }

      if(!shouldHandleReceivedMessage(taskID)) {
         return;
      }

      if(task != null) {
         try {
            task = scheduleService.handleInternalTaskConfiguration(task, subscriber);
         }
         catch(SecurityException e) {
            LOG.warn("Failed to check schedule task permission", e);
         }
      }

      if(adminSubscribed && checkAdminPermission(task)) {
         ScheduleTask scheduleTask = ScheduleManager.getScheduleManager()
            .getScheduleTask(task.getTaskId(), getSubscriberOrgID());

         if(scheduleTask == null && message.getAction() != ScheduleTaskMessage.Action.REMOVED) {
            return;
         }

         ScheduleTaskChange.Type type;

         switch(message.getAction()) {
         case ADDED:
            type = ScheduleTaskChange.Type.ADDED;
            break;
         case REMOVED:
            type = ScheduleTaskChange.Type.REMOVED;
            break;
         case MODIFIED:
         default:
            type = ScheduleTaskChange.Type.MODIFIED;
         }

         TaskActivity activity = scheduleService.getActivity(message.getTaskName(), false);
         ScheduleTaskModel taskModel = task == null ? null : ScheduleTaskModel.builder()
            .fromTask(
               task, activity, Catalog.getCatalog(subscriber), scheduleService.isSecurityEnabled())
            .canDelete(scheduleService.canDeleteTask(task, subscriber))
            .build();
         ScheduleTaskChange model = ScheduleTaskChange.builder()
            .name(taskModel == null ? message.getTaskName() : taskModel.name())
            .type(type)
            .task(taskModel)
            .build();
         messagingTemplate.convertAndSendToUser(
            SUtil.getUserDestination(subscriber), ADMIN_TOPIC, model);
      }
   }

   private boolean checkPortalPermission(String task) {
      try {
         if(repository.checkPermission(
            subscriber, ResourceType.SCHEDULE_TASK, task, ResourceAction.READ))
         {
            return true;
         }

         ScheduleTask scheduleTask = scheduleManager.getScheduleTask(task, getSubscriberOrgID());

         if(scheduleTask == null) {
            return false;
         }

         return ScheduleManager.hasShareGroupPermission(scheduleTask, subscriber);
      }
      catch(RemoteException e) {
         LOG.warn("Failed to check schedule task permission", e);
      }

      return false;
   }

   private boolean checkAdminPermission(ScheduleTask task) {
      return task == null || SUtil.getRepletEngine(repository).hasTaskPermission(task, subscriber);
   }

   private ScheduleTaskModel createModel(ScheduleTask task, TaskActivityMessage message) {
      try {
         return scheduleService.createTaskModel(
            task, message.getActivity(), subscriber, Catalog.getCatalog(subscriber));
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to create task model", e);
      }
   }

   private String getTaskName(String name) {
      if(!scheduleService.isSecurityEnabled() && !name.startsWith(DataCycleManager.TASK_PREFIX) &&
         name.contains(":"))
      {
         return name.substring(name.indexOf(':') + 1);
      }

      return name;
   }

   private Cluster cluster;
   private MessageListener listener;
   private Principal subscriber;
   private boolean portalSubscribed = false;
   private boolean adminSubscribed = false;
   private final AnalyticRepository repository;
   private final ScheduleService scheduleService;
   private final ScheduleManager scheduleManager;
   private final AssetRepository assetRepository;
   private final SimpMessagingTemplate messagingTemplate;
   private final Debouncer<String> debouncer;

   private static final String PORTAL_TOPIC = "/schedule-changed";
   private static final String ADMIN_TOPIC = "/em-schedule-changed";

   private static final Logger LOG = LoggerFactory.getLogger(ScheduleTaskChangeController.class);

}
