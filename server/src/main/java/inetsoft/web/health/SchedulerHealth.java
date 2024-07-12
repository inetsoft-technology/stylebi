/*
 * inetsoft-server - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.health;

import inetsoft.sree.schedule.Schedule;
import inetsoft.util.health.HealthStatus;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public final class SchedulerHealth {
   private SchedulerHealth() {
   }

   public static void main(String[] args) {
      try {
         String host;
         String command;

         if(args.length > 1) {
            host = args[0];
            command = args[1];
         }
         else if(args.length == 1) {
            host = "localhost";
            command = args[0];
         }
         else {
            host = "localhost";
            command = "readiness";
         }

         Registry registry = LocateRegistry.getRegistry(host, 1099);
         Schedule schedule = (Schedule) registry.lookup("ScheduleServer");

         if("liveness".equals(command)) {
            // check liveness
            HealthStatus status = schedule.getHealth();
            System.out.println(status);
            System.exit(status.isDown() ? 1 : 0);
         }
         else {
            // check readiness
            String status = schedule.ping();
            System.out.println(status);
            System.exit("OK".equals(status) ? 0 : 1);
         }
      }
      catch(Exception e) {
         e.printStackTrace();
         System.exit(2);
      }
   }
}
