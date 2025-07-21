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
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.schedule.model.ScheduleTaskChange;
import inetsoft.web.admin.schedule.model.ScheduleTaskModel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.*;

import java.rmi.RemoteException;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class ScheduleTaskChangeController {
   @Autowired
   public ScheduleTaskChangeController(AnalyticRepository repository,
                                       ScheduleService scheduleService,
                                       ScheduleManager scheduleManager,
                                       SimpMessagingTemplate messagingTemplate)
   {
      this.repository = repository;
      this.scheduleService = scheduleService;
      this.scheduleManager = scheduleManager;
      this.messagingTemplate = messagingTemplate;
   }

   @PostConstruct
   public void initCluster() {
      cluster = Cluster.getInstance();
      cluster.addMessageListener(listener);
   }

   @PreDestroy
   public synchronized void removeListeners() {
      cluster.removeMessageListener(listener);
   }

   @SubscribeMapping(PORTAL_TOPIC)
   public synchronized void subscribePortal(StompHeaderAccessor header, Principal principal) {
      final MessageHeaders messageHeaders = header.getMessageHeaders();
      final String subscriptionId =
         (String) messageHeaders.get(SimpMessageHeaderAccessor.SUBSCRIPTION_ID_HEADER);
      portalSubscribers.put(subscriptionId, principal);
   }

   @SubscribeMapping(ADMIN_TOPIC)
   public synchronized void subscribeAdmin(StompHeaderAccessor header, Principal principal) {
      final MessageHeaders messageHeaders = header.getMessageHeaders();
      final String subscriptionId =
         (String) messageHeaders.get(SimpMessageHeaderAccessor.SUBSCRIPTION_ID_HEADER);
      adminSubscribers.put(subscriptionId, principal);
   }

   @EventListener
   public void handleUnsubscribe(SessionUnsubscribeEvent event) {
      removeSubscription(event);
   }

   @EventListener
   public void handleDisconnect(SessionDisconnectEvent event) {
      removeSubscription(event);
   }

   private void removeSubscription(AbstractSubProtocolEvent event) {
      final Message<byte[]> message = event.getMessage();
      final MessageHeaders headers = message.getHeaders();
      final String subscriptionId =
         (String) headers.get(SimpMessageHeaderAccessor.SUBSCRIPTION_ID_HEADER);

      if(subscriptionId != null) {
         adminSubscribers.remove(subscriptionId);
         portalSubscribers.remove(subscriptionId);
      }
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

      if(name == null) {
         return;
      }

      for(Principal subscriber : portalSubscribers.values()) {
         if(!ScheduleManager.isInternalTask(name) && checkPortalPermission(subscriber, name) && shouldHandleReceivedMessage(name, subscriber)) {
            ScheduleTask task = scheduleManager.getScheduleTask(name, getSubscriberOrgId(subscriber));
            ScheduleTaskModel taskModel = createModel(subscriber, task, message);
            ScheduleTaskChange model = ScheduleTaskChange.builder()
               .name(taskModel == null ? message.getTaskName() : taskModel.name())
               .type(ScheduleTaskChange.Type.ACTIVITY)
               .task(taskModel)
               .build();
            messagingTemplate.convertAndSendToUser(
               SUtil.getUserDestination(subscriber), PORTAL_TOPIC, model);
         }
      }
   }

   private boolean shouldHandleReceivedMessage(String taskId, Principal subscriber) {
      IdentityID owner = SUtil.getTaskOwner(taskId);
      String taskOrgId = owner == null ?
         OrganizationManager.getInstance().getCurrentOrgID() : owner.getOrgID();

      return Tool.equals(taskOrgId, getSubscriberOrgId(subscriber));
   }

   private String getSubscriberOrgId(Principal subscriber) {
      return OrganizationManager.getInstance().getCurrentOrgID(subscriber);
   }

   private void dispatchPortalScheduleTask(ScheduleTaskMessage message) {
      ScheduleTask task = message.getTask();
      String taskId = task == null ? null : task.getTaskId();

      if(taskId == null) {
         return;
      }

      for(Principal subscriber : portalSubscribers.values()) {
         if(ScheduleManager.isInternalTask(taskId) ||
            !checkPortalPermission(subscriber, taskId) ||
            !shouldHandleReceivedMessage(taskId, subscriber))
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
   }

   private void dispatchAdminTaskActivity(TaskActivityMessage message) {
      String name = message.getTaskName();

      if(name == null) {
         return;
      }

      ScheduleTask task = scheduleManager.getScheduleTask(name);

      for(Principal subscriber : adminSubscribers.values()) {
         if(!shouldHandleReceivedMessage(name, subscriber)) {
            return;
         }

         if(task == null) {
            task = scheduleManager.getScheduleTask(name, OrganizationManager.getInstance().getCurrentOrgID(subscriber));
         }

         // not current org task, ignore it.
         if(task == null) {
            return;
         }

         try {
            task = scheduleService.handleInternalTaskConfiguration(task, subscriber);
            TaskActivity activity = message.getActivity();

            if(activity != null && activity.getLastRunStatus() != null && activity.getLastRunStatus().equals("Finished")) {
               String actionName = ActionRecord.ACTION_NAME_FINISH;
               String objectType = ActionRecord.OBJECT_TYPE_TASK;

               ActionRecord finishActionRecord = SUtil.getActionRecord(
                  subscriber, actionName, task.getTaskId(), objectType);
               finishActionRecord.setObjectUser(task.getOwner() != null ? task.getOwner().getName() : "");
               String seeScheduleLog = Catalog.getCatalog().getString("em.task.runStatus");
               finishActionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
               finishActionRecord.setActionError(seeScheduleLog);
               Audit.getInstance().auditAction(finishActionRecord, subscriber);
            }
         }
         catch(SecurityException e) {
            LOG.warn("Failed to check schedule task permission", e);
         }

         if(checkAdminPermission(subscriber, task)) {
            ScheduleTaskChange model = ScheduleTaskChange.builder()
               .name(getTaskName(message.getTaskName()))
               .type(ScheduleTaskChange.Type.ACTIVITY)
               .task(createModel(subscriber, task, message))
               .build();
            messagingTemplate.convertAndSendToUser(
               SUtil.getUserDestination(subscriber), ADMIN_TOPIC, model);
         }
      }
   }

   private void dispatchAdminScheduleTask(ScheduleTaskMessage message) {
      ScheduleTask task = message.getTask();
      String taskID = task == null ? null : task.getTaskId();

      if(taskID == null) {
         return;
      }

      for(Principal subscriber : adminSubscribers.values()) {
         if(!shouldHandleReceivedMessage(taskID, subscriber)) {
            continue;
         }

         if(task != null) {
            try {
               task = scheduleService.handleInternalTaskConfiguration(task, subscriber);
            }
            catch(SecurityException e) {
               LOG.warn("Failed to check schedule task permission", e);
            }
         }

         if(checkAdminPermission(subscriber, task)) {
            if(task != null) {
               ScheduleTask scheduleTask = ScheduleManager.getScheduleManager()
                  .getScheduleTask(task.getTaskId(), getSubscriberOrgId(subscriber));

               if(scheduleTask == null) {
                  return;
               }
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
   }

   private boolean checkPortalPermission(Principal subscriber, String task) {
      try {
         if(repository.checkPermission(
            subscriber, ResourceType.SCHEDULE_TASK, task, ResourceAction.READ))
         {
            return true;
         }

         ScheduleTask scheduleTask = scheduleManager.getScheduleTask(task, getSubscriberOrgId(subscriber));

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

   private boolean checkAdminPermission(Principal subscriber, ScheduleTask task) {
      return task == null || SUtil.getRepletEngine(repository).hasTaskPermission(task, subscriber);
   }

   private ScheduleTaskModel createModel(Principal subscriber, ScheduleTask task,
                                         TaskActivityMessage message)
   {
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
   private final MessageListener listener = this::messageReceived;
   private final Map<String, Principal> adminSubscribers = new ConcurrentHashMap<>();
   private final Map<String, Principal> portalSubscribers = new ConcurrentHashMap<>();

   private final AnalyticRepository repository;
   private final ScheduleService scheduleService;
   private final ScheduleManager scheduleManager;
   private final SimpMessagingTemplate messagingTemplate;

   private static final String PORTAL_TOPIC = "/schedule-changed";
   private static final String ADMIN_TOPIC = "/em-schedule-changed";

   private static final Logger LOG = LoggerFactory.getLogger(ScheduleTaskChangeController.class);

}
