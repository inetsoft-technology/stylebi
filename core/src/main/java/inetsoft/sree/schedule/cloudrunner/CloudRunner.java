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
package inetsoft.sree.schedule.cloudrunner;

import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MessageListener;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.Identity;
import inetsoft.util.*;
import inetsoft.util.config.InetsoftConfig;
import org.apache.ignite.*;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.springframework.web.util.UriUtils;
import picocli.CommandLine;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("CallToPrintStackTrace")
@CommandLine.Command (
   name="run-task", mixinStandardHelpOptions = true, version = "run-task 14.0",
   description = "Runs a scheduled task in a cloud runner")
public class CloudRunner implements Callable<Integer> {
   @CommandLine.Option(
      names = "--sree.home", paramLabel = "CONFIG_DIR",
      description = "The path to the configuration directory", required = true)
   private File home;

   @CommandLine.Option(
      names = "--cluster.address", paramLabel = "CLUSTER_ADDR",
      description = "The comma-separated IP addresses of the cluster nodes", required = true)
   private String clusterAddress;

   @CommandLine.Option(
      names = "--schedule.task.name", paramLabel = "TASK_NAME",
      description = "The name of the task being run", required = true)
   private String taskName;

   @CommandLine.Option(
      names = "--schedule.cycle.name", paramLabel = "CYCLE_NAME",
      description = "The name of the data cycle, if any")
   private String cycleName;

   @CommandLine.Option(
      names = "--schedule.cycle.organization", paramLabel = "CYCLE_ORG",
      description = "The ID of the organization that owns the data cycle, if any")
   private String cycleOrgId;

   public static void main(String[] args) {
      int exitCode = new CommandLine(new CloudRunner()).execute(args);
      System.exit(exitCode);
   }

   public Integer call() throws Exception {
      String decodedTask = UriUtils.decode(taskName, StandardCharsets.UTF_8);
      String decodedCycleName = null;
      String decodedCycleOrgId = null;
      String homePath = home.getAbsolutePath();

      System.out.println("sree.home: " + homePath);
      System.out.println("cluster.address: " + clusterAddress);
      System.out.println("schedule.task.name: " + decodedTask);
      System.setProperty("ScheduleTaskRunner", decodedTask);

      if(cycleName != null) {
         decodedCycleName = UriUtils.decode(cycleName, StandardCharsets.UTF_8);
         System.out.println("schedule.cycle.name: " + decodedCycleName);
      }

      if(cycleOrgId != null) {
         decodedCycleOrgId = UriUtils.decode(cycleOrgId, StandardCharsets.UTF_8);
         System.out.println("schedule.cycle.organization: " + decodedCycleOrgId);
      }

      try {
         fetchInetsoftConfig(homePath, clusterAddress);

         // After this point inetsoft can be initialized
         ConfigurationContext.getContext().setHome(homePath);
         return runScheduleTask(decodedTask, decodedCycleName, decodedCycleOrgId);
      }
      catch(Exception e) {
         e.printStackTrace();
         return 1;
      }
   }

   private static void fetchInetsoftConfig(String home, String clusterAddress) {
      // Connect to the cluster with the client to get the inetsoft configuration file
      IgniteConfiguration igniteConfig = new IgniteConfiguration();
      igniteConfig.setClientMode(true);
      igniteConfig.setPeerClassLoadingEnabled(true);

      TcpCommunicationSpi communicationSpi = new TcpCommunicationSpi();
      communicationSpi.setForceClientToServerConnections(true);
      igniteConfig.setCommunicationSpi(communicationSpi);

      TcpDiscoverySpi discoverySpi = new TcpDiscoverySpi();
      TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
      ipFinder.setAddresses(Arrays.stream(clusterAddress.split(",")).toList());
      discoverySpi.setIpFinder(ipFinder);
      igniteConfig.setDiscoverySpi(discoverySpi);
      SUtil.configBinaryTypes(igniteConfig);

      try(Ignite ignite = Ignition.start(igniteConfig)) {
         System.out.println("Connected to the InetSoft cluster");
         IgniteCache<String, InetsoftConfig> cache =
            ignite.getOrCreateCache("cloud.runner.config");
         InetsoftConfig config = cache.get("config");

         // save inetsoft.yaml file
         InetsoftConfig.save(config, Paths.get(home, "inetsoft.yaml"));
         SingletonManager.reset(InetsoftConfig.class);
      }
   }

   private static int runScheduleTask(String taskName, String cycleName, String cycleOrgId) {
      initContextPrincipal(taskName);
      ScheduleTask task = findScheduleTask(taskName, cycleName, cycleOrgId);

      // run the task
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
               principal = SUtil.getPrincipal(owner, addr, false);
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
            e.printStackTrace();
         }
      }

      try {
         cluster.sendMessage(new CloudJobResult(taskName, success, message));
      }
      catch(Exception e) {
         e.printStackTrace();
      }
      finally {
         try {
            cluster.close();
         }
         catch(Exception e) {
            e.printStackTrace();
         }
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
         for(ScheduleTask candidate : DataCycleManager.getDataCycleManager().getTasks()) {
            if(candidate.getTaskId().equals(taskId)) {
               DataCycleManager.CycleInfo info = candidate.getCycleInfo();

               if(info == null ||
                  info.getName().equals(cycleName) && info.getOrgId().equals(cycleOrgId))
               {
                  return candidate;
               }
            }
         }
      }

      return null;
   }
}
