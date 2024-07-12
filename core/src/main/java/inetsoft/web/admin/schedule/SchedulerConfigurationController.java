/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.web.admin.schedule;

import inetsoft.web.admin.schedule.model.CheckMailInfo;
import inetsoft.web.admin.schedule.model.ScheduleConfigurationModel;
import inetsoft.web.admin.schedule.model.ScheduleStatusModel;
import inetsoft.web.security.DeniedMultiTenancyOrgUser;

import java.security.Principal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class SchedulerConfigurationController {
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


   private final SchedulerConfigurationService configService;
}
