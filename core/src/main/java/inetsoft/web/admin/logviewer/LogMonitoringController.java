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

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
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
                                  MonitoringDataService monitoringDataService,
                                  SecurityEngine securityEngine)
   {
      this.logMonitoringService = logMonitoringService;
      this.monitoringDataService = monitoringDataService;
      this.securityEngine = securityEngine;
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "monitoring/log",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/em/monitoring/logviewer/all-logs")
   public LogMonitoringModel getLogs(Principal principal) {
      checkFileLogAccess(principal);
      return logMonitoringService.getLogs();
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "monitoring/log",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/em/monitoring/logviewer/refresh/{clusterNode}/{logFileName}/{offset}/{length}")
   public List<String> refreshLogViewer(
      @PathVariable("clusterNode") String clusterNode,
      @PathVariable("logFileName") String logFileName,
      @PathVariable("offset") int offset,
      @PathVariable("length") int length,
      Principal principal)
   {
      checkFileLogAccess(principal);
      return logMonitoringService.getLog(clusterNode, logFileName, offset, length);
   }

   @SubscribeMapping("/monitoring/logviewer/auto_refresh/{clusterNode}/{logFileName}/{offset}/{length}")
   public List<String> subscribeToLogRefresh(StompHeaderAccessor stompHeaderAccessor,
                                       @DestinationVariable("clusterNode") String clusterNode,
                                       @DestinationVariable("logFileName") String logFileName,
                                       @DestinationVariable("offset") int offset,
                                       @DestinationVariable("length") int length,
                                       Principal principal)
      throws SecurityException
   {
      if(!securityEngine.getSecurityProvider().checkPermission(
         principal, ResourceType.EM_COMPONENT, "monitoring/log", ResourceAction.ACCESS) ||
         !isFileLogAccessAllowed(principal))
      {
         throw new SecurityException("Unauthorized access to log viewer by user " + principal.getName());
      }

      return this.monitoringDataService.addSubscriber(stompHeaderAccessor, () -> {
         try {
            return logMonitoringService.getLog(clusterNode, logFileName, offset, length);
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      });
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "monitoring/log",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/em/monitoring/logviewer/rotate")
   public LogMonitoringModel rotateLogFile(
      @RequestParam("clusterNode") String clusterNode,
      @RequestParam("logFileName") String logFileName,
      Principal principal) throws Exception
   {
      checkFileLogAccess(principal);
      return logMonitoringService.rotateLogFile(clusterNode, logFileName);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "monitoring/log",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/em/monitoring/logviewer/download")
   public void downloadLogs(HttpServletResponse response,
                            @RequestParam(value = "clusterNode", required = false) String clusterNode,
                            Principal principal)
   {
      checkFileLogAccess(principal);

      try {
         logMonitoringService.downloadLogs(response, clusterNode);
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

   /**
    * The log files served by the file endpoints are global to the server (they contain the log
    * records of every organization), so in a multi-tenant deployment only site administrators may
    * access them. The monitoring/log permission alone is not sufficient, because it is granted to
    * organization administrators when fluentd logging with log.fluentd.orgAdminAccess is enabled,
    * which is only intended to expose the organization-scoped external log viewer link.
    */
   private static boolean isFileLogAccessAllowed(Principal principal) {
      return !SUtil.isMultiTenant() || OrganizationManager.getInstance().isSiteAdmin(principal);
   }

   private static void checkFileLogAccess(Principal principal) {
      if(!isFileLogAccessAllowed(principal)) {
         // unchecked java.lang.SecurityException is mapped to a sanitized 403 by AdminExceptionHandler
         throw new java.lang.SecurityException("Unauthorized access to server log files");
      }
   }

   private final LogMonitoringService logMonitoringService;
   private final MonitoringDataService monitoringDataService;
   private final SecurityEngine securityEngine;
}
