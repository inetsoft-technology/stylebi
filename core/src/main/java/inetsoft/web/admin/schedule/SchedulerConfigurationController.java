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

import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.schedule.RestartSchedulerMessage;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.web.admin.schedule.model.*;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class SchedulerConfigurationController implements MessageListener {
   @Autowired
   public SchedulerConfigurationController(SchedulerConfigurationService configService,
                                           Cluster cluster)
   {
      this.configService = configService;
      this.cluster = cluster;
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/settings",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/settings/schedule/configuration")
   public ScheduleConfigurationModel getConfiguration(Principal principal) throws Exception {
      return this.configService.getConfiguration(principal);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/settings",
         actions = ResourceAction.ACCESS
      )
   )
   @PutMapping("/api/em/settings/schedule/configuration")
   public void setConfiguration(@RequestBody ScheduleConfigurationModel model, Principal principal)
      throws Exception
   {
      this.configService.setConfiguration(model, principal);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/settings",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/settings/schedule/status")
   public ScheduleStatusModel getStatus() {
      return this.configService.getStatus();
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/settings",
         actions = ResourceAction.ACCESS
      )
   )
   @PutMapping("/api/em/settings/schedule/status")
   public void setStatus(@RequestBody ScheduleStatusModel status) throws Exception {
      this.configService.setStatus(status);
   }

   @Secured(
      value = {
         @RequiredPermission(resourceType = ResourceType.EM_COMPONENT, resource = "settings/general", actions = ResourceAction.ACCESS),
         @RequiredPermission(resourceType = ResourceType.EM_COMPONENT, resource = "settings/schedule/tasks", actions = ResourceAction.ACCESS),
         @RequiredPermission(resourceType = ResourceType.EM_COMPONENT, resource = "settings/security/users", actions = ResourceAction.ACCESS)
      },
      operator = "OR"
   )
   @PostMapping("/api/em/settings/schedule/check-mail")
   public CheckMailInfo checkMail(@RequestBody CheckMailInfo mailParams, Principal principal) {
      return this.configService.checkMail(mailParams, principal);
   }

   @GetMapping("/api/em/settings/schedule/cloudRunner")
   public boolean isCloudRunner() {
      return InetsoftConfig.getInstance().getCloudRunner() != null;
   }

   @PostConstruct
   public void addListeners() throws Exception {
      cluster.addMessageListener(this);
   }

   @PreDestroy
   public void removeListeners() {
      try {
         cluster.removeMessageListener(this);
      }
      catch(Exception e) {
         LOG.debug("Failed to remove listeners during shutdown", e);
      }
   }

   @Override
   public void messageReceived(MessageEvent event) {
      if(event.getMessage() instanceof RestartSchedulerMessage) {
         try {
            configService.setStatus("restart");
         }
         catch(Exception e) {
            LOG.error("Failed to restart scheduler", e);
         }
      }
   }

   private final SchedulerConfigurationService configService;
   private final Cluster cluster;

   private static final Logger LOG = LoggerFactory.getLogger(SchedulerConfigurationController.class);
}
