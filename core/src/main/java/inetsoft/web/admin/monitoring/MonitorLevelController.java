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
package inetsoft.web.admin.monitoring;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class MonitorLevelController {
   @Autowired
   public MonitorLevelController(MonitoringDataService monitoringDataService) {
      this.monitoringDataService = monitoringDataService;
   }

   @SubscribeMapping("/monitoring/monitor-level")
   public int getMonitoringLevel(StompHeaderAccessor stompHeaderAccessor) {
      return this.monitoringDataService.addSubscriber(stompHeaderAccessor, () -> {
         try {
            return MonitorLevelService.getMonitorLevel();
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      });
   }

   @GetMapping("/api/em/monitoring/level")
   @ResponseBody
   public int getMonitoringLevel(){
      try {
         return MonitorLevelService.getMonitorLevel();
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }
   }

   private final MonitoringDataService monitoringDataService;
}
