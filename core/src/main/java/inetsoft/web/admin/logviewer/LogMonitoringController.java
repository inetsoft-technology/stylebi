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
package inetsoft.web.admin.logviewer;

import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.web.admin.monitoring.MonitoringDataService;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
public class LogMonitoringController {
   @Autowired
   public LogMonitoringController(LogMonitoringService logMonitoringService,
                                  MonitoringDataService monitoringDataService) {
      this.logMonitoringService = logMonitoringService;
      this.monitoringDataService = monitoringDataService;
   }

   @GetMapping("/em/monitoring/logviewer/all-logs")
   public LogMonitoringModel getLogs() {
      return logMonitoringService.getLogs();
   }

   @GetMapping("/em/monitoring/logviewer/refresh/{clusterNode}/{logFileName}/{offset}/{length}")
   public List<String> refreshLogViewer(
      @PathVariable("clusterNode") String clusterNode,
      @PathVariable("logFileName") String logFileName,
      @PathVariable("offset") int offset,
      @PathVariable("length") int length) {
      return logMonitoringService.getLog(clusterNode, logFileName, offset, length);
   }

   @SubscribeMapping("/monitoring/logviewer/auto_refresh/{clusterNode}/{logFileName}/{offset}/{length}")
   public List<String> subscribeToLogRefresh(StompHeaderAccessor stompHeaderAccessor,
                                       @DestinationVariable("clusterNode") String clusterNode,
                                       @DestinationVariable("logFileName") String logFileName,
                                       @DestinationVariable("offset") int offset,
                                       @DestinationVariable("length") int length)
   {
      return this.monitoringDataService.addSubscriber(stompHeaderAccessor, () -> {
         try {
            return logMonitoringService.getLog(clusterNode, logFileName, offset, length);
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      });
   }

   @GetMapping("/em/monitoring/logviewer/rotate")
   public LogMonitoringModel rotateLogFile(
      @RequestParam("clusterNode") String clusterNode,
      @RequestParam("logFileName") String logFileName) throws Exception
   {
      return logMonitoringService.rotateLogFile(clusterNode, logFileName);
   }

   @GetMapping("/em/monitoring/logviewer/download")
   public void downloadLogs(HttpServletResponse response) {
      try {
         logMonitoringService.downloadLogs(response);
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "monitoring/log",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/monitoring/log/links")
   public LogViewLinks getLogLinks(@SuppressWarnings("unused") Principal principal) {
      return logMonitoringService.getLinks(principal);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "auditing",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/monitoring/audit/links")
   public LogViewLinks getAuditLinks(@SuppressWarnings("unused") Principal principal) {
      return logMonitoringService.getLinks(principal);
   }

   private final LogMonitoringService logMonitoringService;
   private final MonitoringDataService monitoringDataService;
}
