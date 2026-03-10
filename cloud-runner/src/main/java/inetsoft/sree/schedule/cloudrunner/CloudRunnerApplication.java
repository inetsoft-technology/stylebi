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

import inetsoft.sree.internal.cluster.ignite.IgniteUtils;
import inetsoft.util.*;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.util.log.LogManager;
import org.apache.ignite.*;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.web.util.UriUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Spring Boot entry point for the cloud runner process.
 * <p>
 * This class replaces the legacy {@code CloudRunner} picocli-based entry point.
 * Pre-Spring configuration (home directory, InetsoftConfig bootstrap, logging) is
 * performed here before the application context is started. Task execution is
 * handled by {@link CloudRunnerContext}.
 */
@SpringBootApplication
@Import(CloudRunnerContext.class)
public class CloudRunnerApplication {
   public static void main(String[] args) {
      // During process-aot, disable AOT runtime mode so the context can be analyzed.
      if("true".equals(System.getProperty("spring.aot.processing"))) {
         System.setProperty("spring.aot.enabled", "false");
      }
      else {
         ApplicationArguments appArguments = new DefaultApplicationArguments(args);
         String home = getRequiredArg(appArguments, "sree.home");
         String clusterAddress = getRequiredArg(appArguments, "cluster.address");
         String taskName = getRequiredArg(appArguments, "schedule.task.name");

         String decodedTask = UriUtils.decode(taskName, StandardCharsets.UTF_8);
         System.setProperty("ScheduleTaskRunner", decodedTask);

         System.out.println("sree.home: " + home);
         System.out.println("cluster.address: " + clusterAddress);
         System.out.println("schedule.task.name: " + decodedTask);

         String cycleName = getOptionalArg(appArguments, "schedule.cycle.name");
         String cycleOrgId = getOptionalArg(appArguments, "schedule.cycle.organization");

         if(cycleName != null) {
            System.out.println("schedule.cycle.name: " +
               UriUtils.decode(cycleName, StandardCharsets.UTF_8));
         }

         if(cycleOrgId != null) {
            System.out.println("schedule.cycle.organization: " +
               UriUtils.decode(cycleOrgId, StandardCharsets.UTF_8));
         }

         File homeFile = new File(home);
         ConfigurationContext.getContext().setHome(homeFile.getAbsolutePath());

         // Connect to the cluster as a client to fetch the inetsoft configuration, then
         // disconnect. This must happen before SpringApplication.run() so that
         // InetsoftConfig.BOOTSTRAP_INSTANCE is populated when beans are created.
         fetchInetsoftConfig(homeFile.getAbsolutePath(), clusterAddress);

         Tool.setServer(true);
         LogManager.initializeForStartup();

         if(OperatingSystem.isUnix()) {
            String val = System.getProperty("java.awt.headless");

            if(val == null || val.isEmpty()) {
               System.setProperty("java.awt.headless", "true");
            }
         }
      }

      SpringApplication app = new SpringApplication(CloudRunnerApplication.class);
      app.setWebApplicationType(WebApplicationType.NONE);
      app.run(args);
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
      IgniteUtils.configBinaryTypes(igniteConfig);

      try(Ignite ignite = Ignition.start(igniteConfig)) {
         System.out.println("Connected to the InetSoft cluster");
         IgniteCache<String, InetsoftConfig> cache =
            ignite.getOrCreateCache("cloud.runner.config");
         InetsoftConfig config = cache.get("config");

         // save inetsoft.yaml file and update the bootstrap instance so all subsequent
         // InetsoftConfig.getInstance() calls in this process return the fetched config
         InetsoftConfig.save(config, Paths.get(home, "inetsoft.yaml"));
         InetsoftConfig.BOOTSTRAP_INSTANCE = config;
      }
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
}
