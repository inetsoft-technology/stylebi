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
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
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
      requireSiteAdmin(user);
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
      requireSiteAdmin(user);
      return Map.of("backupRef", backupService.backup(body.get("transactionId")));
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT,
      resource = "settings/properties",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/admin/ai/restore")
   public Map<String, String> restore(@RequestBody Map<String, String> body, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      backupService.restore(body.get("backupRef"));
      return Map.of("status", "restored");
   }

   /**
    * Per product decision, every admin-chat endpoint is restricted to callers holding the Site
    * Administrator (system administrator) role, not merely EM_COMPONENT access.
    * {@link OrganizationManager#isSiteAdmin(Principal)} returns {@code true} for the default
    * admin principal in no-security deployments, so this guard does not lock out dev/single-user
    * setups and does not need an additional {@code isSecurityEnabled()} check.
    */
   private void requireSiteAdmin(Principal user) {
      if(!OrganizationManager.getInstance().isSiteAdmin(user)) {
         throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Site Administrator role required");
      }
   }

   /**
    * Uniformly maps client/validation errors (blank/invalid transactionId, property, action, or
    * backup/restore reference) to HTTP 400 with a structured body, across all three endpoints.
    * Scoped to {@link IllegalArgumentException} only - a catch-all {@code Exception} handler
    * would also swallow {@link ResponseStatusException} (see {@link #requireSiteAdmin}) and other
    * framework exceptions that must retain their own status codes.
    */
   @ExceptionHandler(IllegalArgumentException.class)
   @ResponseStatus(HttpStatus.BAD_REQUEST)
   @ResponseBody
   public Map<String, String> handleIllegalArgument(IllegalArgumentException ex) {
      return Map.of("status", "failed", "error", String.valueOf(ex.getMessage()));
   }

   private final AdminChangeService changeService;
   private final AdminBackupService backupService;
}
