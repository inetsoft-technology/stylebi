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
package inetsoft.sree.schedule;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.RMICallThread;
import inetsoft.util.Tool;
import inetsoft.util.health.HealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the standalone schedule server process. Handles RMI
 * registry binding and scheduler lifecycle after the application context starts.
 */
@Configuration
public class ScheduleServerContext implements ApplicationRunner {
   @Autowired
   public ScheduleServerContext(ScheduleServer scheduleServer) {
      this.scheduleServer = scheduleServer;
   }

   @Override
   public void run(ApplicationArguments args) throws Exception {
      // Skip all runtime startup during process-aot: starting RMI, Quartz, or the Ignite
      // cluster node (via Scheduler.start() → Cluster.getInstance()) would leave non-daemon
      // threads running and prevent the forked JVM from exiting after AOT analysis.
      if("true".equals(System.getProperty("spring.aot.processing"))) {
         return;
      }

      int port = Integer.parseInt(SreeEnv.getProperty("scheduler.rmi.port"));
      String host = Tool.getRmiIP();
      String name = "//" + host + ':' + port + "/ScheduleServer";

      try {
         RMICallThread rct = new RMICallThread();

         if(rct.getRegistry(host, port, 15000L) == null) {
            LOG.error("Failed to locate RMI registry, aborting");
            System.exit(-1);
         }
      }
      catch(Exception exc) {
         LOG.error("Failed to locate RMI registry, aborting", exc);
         System.exit(-1);
      }

      try {
         RMICallThread rct = new RMICallThread();
         Schedule existing = (Schedule) rct.lookup(name, 1500L, false);

         if(existing != null) {
            LOG.error("Scheduler server is already running, aborting");
            System.exit(-1);
         }
      }
      catch(Exception ignore) {
      }

      try {
         boolean success;
         RMICallThread rct = new RMICallThread();
         success = rct.rebind(name, scheduleServer, 30000);

         if(!success) {
            LOG.debug("Start RMI registry on port: " + port);
            rct = new RMICallThread();
            rct.startRegistry(host, port, 30000);
            rct = new RMICallThread();
            success = rct.rebind(name, scheduleServer, 30000);

            if(!success) {
               throw new Exception("Failed to rebind schedule server to RMI registry");
            }
         }

         LOG.info("Schedule server bound in RMI registry.");
         HealthService.getInstance();
         scheduleServer.start();
      }
      catch(Exception exc) {
         LOG.error("Unable to bind schedule server to RMI registry.", exc);
         System.exit(-1);
      }
   }

   private final ScheduleServer scheduleServer;
   private static final Logger LOG = LoggerFactory.getLogger(ScheduleServerContext.class);
}
