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
import inetsoft.util.config.InetsoftConfig;
import inetsoft.web.admin.schedule.model.CheckMailInfo;
import inetsoft.web.admin.schedule.model.ScheduleConfigurationModel;
import inetsoft.web.admin.schedule.model.ScheduleStatusModel;
import inetsoft.web.security.DeniedMultiTenancyOrgUser;

import java.security.Principal;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.web.bind.annotation.*;

@RestController
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class SchedulerConfigurationController implements MessageListener {
   @Autowired
   public SchedulerConfigurationController(SchedulerConfigurationService configService) {
      this.configService = configService;
   }

   @DeniedMultiTenancyOrgUser
   @GetMapping("/api/em/settings/schedule/configuration")
   public ScheduleConfigurationModel getConfiguration(Principal principal) throws Exception {
      return this.configService.getConfiguration(principal);
   }

   @DeniedMultiTenancyOrgUser
   @PutMapping("/api/em/settings/schedule/configuration")
   public void setConfiguration(@RequestBody ScheduleConfigurationModel model, Principal principal)
      throws Exception
   {
      this.configService.setConfiguration(model, principal);
   }

   @DeniedMultiTenancyOrgUser
   @GetMapping("/api/em/settings/schedule/status")
   public ScheduleStatusModel getStatus() {
      return this.configService.getStatus();
   }

   @DeniedMultiTenancyOrgUser
   @PutMapping("/api/em/settings/schedule/status")
   public void setStatus(@RequestBody ScheduleStatusModel status) throws Exception {
      this.configService.setStatus(status);
   }

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
      Cluster.getInstance().addMessageListener(this);
   }

   @PreDestroy
   public void removeListeners() throws Exception {
      Cluster.getInstance().removeMessageListener(this);
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

   private static final Logger LOG = LoggerFactory.getLogger(SchedulerConfigurationController.class);
}
