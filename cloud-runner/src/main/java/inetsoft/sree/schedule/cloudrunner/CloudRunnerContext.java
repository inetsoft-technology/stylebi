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
package inetsoft.sree.schedule.cloudrunner;

import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.Identity;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spring configuration for the cloud runner process. Handles schedule task
 * execution after the application context starts.
 */
@Configuration
public class CloudRunnerContext implements ApplicationRunner {
   @Override
   public void run(ApplicationArguments args) {
      // Skip all runtime execution during process-aot: starting the Ignite cluster node
      // (via Cluster.getInstance()) would leave non-daemon threads running and prevent
      // the forked JVM from exiting after AOT analysis.
      if("true".equals(System.getProperty("spring.aot.processing"))) {
         return;
      }

      String taskName = getRequiredArg(args, "schedule.task.name");
      String cycleName = getOptionalArg(args, "schedule.cycle.name");
      String cycleOrgId = getOptionalArg(args, "schedule.cycle.organization");

      String decodedTask = UriUtils.decode(taskName, StandardCharsets.UTF_8);
      String decodedCycleName =
         cycleName != null ? UriUtils.decode(cycleName, StandardCharsets.UTF_8) : null;
      String decodedCycleOrgId =
         cycleOrgId != null ? UriUtils.decode(cycleOrgId, StandardCharsets.UTF_8) : null;

      System.exit(runScheduleTask(decodedTask, decodedCycleName, decodedCycleOrgId));
   }

   private static int runScheduleTask(String taskName, String cycleName, String cycleOrgId) {
      initContextPrincipal(taskName);
      ScheduleTask task = findScheduleTask(taskName, cycleName, cycleOrgId);

      System.out.println("Running Schedule Task: " + task);
      AtomicInteger result = new AtomicInteger(0);
      boolean success = true;
      String message = null;
      Cluster cluster = Cluster.getInstance();

      if(task == null) {
         result.set(1);
         success = false;

         if(cycleName == null) {
            message = "Could not find schedule task named " + taskName;
         }
         else if(cycleOrgId == null) {
            message =
               "Could not find schedule task named " + taskName + " for data cycle " + cycleName;
         }
         else {
            message =
               "Could not find schedule task named " + taskName + " for data cycle " + cycleName +
               " belonging to organization " + cycleOrgId;
         }
      }
      else {
         // add cancel listener
         MessageListener listener = event -> {
            if(event.getMessage() instanceof CancelCloudJob msg) {
               if(Tool.equals(taskName, msg.getTaskName())) {
                  System.out.println("Cloud Job Cancelled!");
                  task.cancel();
                  result.set(0);
               }
            }
         };

         cluster.addMessageListener(listener);

         try {
            Principal principal;
            String addr = Tool.getIP();
            Identity identity = task.getIdentity();
            IdentityID owner = task.getOwner();

            if(identity == null) {
               principal = SUtil.getScheduleTaskOwnerPrincipal(owner, addr, false);
            }
            else {
               principal = SUtil.getPrincipal(identity, addr, false);
            }

            task.run(principal);
            System.out.println("Schedule task executed successfully!");
         }
         catch(Throwable e) {
            success = false;
            result.set(1);
            message = e.getMessage();
            LOG.error("Schedule task execution failed", e);
         }
      }

      try {
         cluster.sendMessage(new CloudJobResult(taskName, success, message));
      }
      catch(Exception e) {
         LOG.error("Failed to send task result", e);
      }

      return result.get();
   }

   private static void initContextPrincipal(String taskName) {
      String[] arr = Tool.split(taskName, '/');

      if(arr.length == 0) {
         return;
      }

      String name = arr[arr.length - 1];
      int index = name.indexOf("__" + DataCycleManager.TASK_PREFIX);

      if(index >= 0) {
         arr = new String[] { name.substring(0, index), name.substring(index) };
      }
      else {
         arr = Tool.split(name, ':');
      }

      if(arr.length == 0) {
         return;
      }

      String userKey = arr[0];
      IdentityID identityID = IdentityID.getIdentityIDFromKey(userKey);

      if(identityID != null && identityID.getOrgID() != null) {
         Principal principal = ThreadContext.getContextPrincipal();

         if(principal == null) {
            String addr = Tool.getIP();
            principal = SUtil.getPrincipal(identityID, addr, false);
            ThreadContext.setContextPrincipal(principal);
         }
         else {
            ((XPrincipal) principal).setProperty("curr_org_id", identityID.getOrgID());
         }
      }
   }

   private static ScheduleTask findScheduleTask(String taskId, String cycleName,
                                                String cycleOrgId)
   {
      if(cycleName == null) {
         return ScheduleManager.getScheduleManager().getScheduleTask(taskId);
      }
      else {
         for(ScheduleTask candidate : DataCycleManager.getDataCycleManager().getTasks(cycleOrgId)) {
            if(candidate.getTaskId().equals(taskId)) {
               DataCycleManager.CycleInfo info = candidate.getCycleInfo();

               if(info == null || info.getName().equals(cycleName)) {
                  return candidate;
               }
            }
         }
      }

      return null;
   }

   private static String getRequiredArg(ApplicationArguments args, String name) {
      List<String> values = args.getOptionValues(name);

      if(values == null || values.isEmpty()) {
         throw new IllegalArgumentException("Missing required argument: --" + name);
      }

      return values.get(0);
   }

   private static String getOptionalArg(ApplicationArguments args, String name) {
      List<String> values = args.getOptionValues(name);
      return (values == null || values.isEmpty()) ? null : values.get(0);
   }

   private static final Logger LOG = LoggerFactory.getLogger(CloudRunnerContext.class);
}
