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
package inetsoft.web.admin.ai;

import inetsoft.sree.security.*;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.Map;

@RestController
public class AdminAiController {
   @Autowired
   public AdminAiController(AdminChangeService changeService, AdminBackupService backupService) {
      this.changeService = changeService;
      this.backupService = backupService;
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT,
      resource = "settings/properties",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/admin/ai/change")
   public AdminChangeResult change(@RequestBody AdminChangeRequest req, Principal user) {
      return changeService.applyChange(req, user);
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT,
      resource = "settings/properties",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/admin/ai/backup")
   public Map<String, String> backup(@RequestBody Map<String, String> body, Principal user)
      throws Exception
   {
      return Map.of("backupRef", backupService.backup(body.get("transactionId")));
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT,
      resource = "settings/properties",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/admin/ai/restore")
   public Map<String, String> restore(@RequestBody Map<String, String> body, Principal user) {
      try {
         backupService.restore(body.get("backupRef"));
         return Map.of("status", "restored");
      }
      catch(Exception ex) {
         return Map.of("status", "failed", "error", String.valueOf(ex.getMessage()));
      }
   }

   private final AdminChangeService changeService;
   private final AdminBackupService backupService;
}
