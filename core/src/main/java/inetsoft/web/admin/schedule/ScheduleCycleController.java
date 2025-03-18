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

import inetsoft.web.admin.monitoring.MonitoringDataService;
import inetsoft.web.admin.schedule.model.DataCycleListModel;
import inetsoft.web.admin.schedule.model.ScheduleCycleDialogModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@Lazy(false)
public class ScheduleCycleController {
   @Autowired
   ScheduleCycleController(ScheduleCycleService scheduleCycleService,
                           MonitoringDataService monitoringDataService)
   {
      this.scheduleCycleService = scheduleCycleService;
      this.monitoringDataService = monitoringDataService;
   }

   @SubscribeMapping("/schedule/cycles/get-cycle-names")
   public DataCycleListModel subscribeToDataCycleNames(StompHeaderAccessor stompHeaderAccessor,
                                                       Principal principal)
   {
      return this.monitoringDataService.addSubscriber(stompHeaderAccessor, () -> {
         try {
            return this.scheduleCycleService.getCycleInfos(principal);
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      });
   }

   @GetMapping("/api/em/schedule/cycle-dialog-model/{cycleName}")
   public ScheduleCycleDialogModel getDataCycleDialogModel(@PathVariable("cycleName") String cycleName,
                                                           Principal principal)
      throws Exception
   {
      return this.scheduleCycleService.getDialogModel(cycleName, principal);
   }

   @GetMapping("/api/em/schedule/add-cycle/{timeZoneId}")
   public DataCycleInfo addDataCycle(@PathVariable("timeZoneId") String timeZoneId, Principal principal) {
      String newCycleName = this.scheduleCycleService.addDataCycle(principal, timeZoneId);
      return new DataCycleInfo(newCycleName);
   }

   @PostMapping("/api/em/schedule/edit-cycle")
   public ScheduleCycleDialogModel editDataCycle(@RequestBody ScheduleCycleDialogModel model,
                                                 Principal principal)
      throws Exception
   {
      this.scheduleCycleService.editCycle(model, principal);
      return getDataCycleDialogModel(model.label(), principal);
   }

   @PostMapping("/api/em/schedule/cycles/remove-cycles")
   public void removeDataCycles(@RequestBody DataCycleListModel model,
                                Principal principal)
      throws Exception
   {
      this.scheduleCycleService.removeCycles(model.cycles(), principal);
   }

   @MessageMapping("/schedule/cycles/update-cycles")
   public void updateDataCycles(StompHeaderAccessor stompHeaderAccessor)
   {
      this.monitoringDataService.updateSession(stompHeaderAccessor);
   }

   private final ScheduleCycleService scheduleCycleService;
   private final MonitoringDataService monitoringDataService;
}
