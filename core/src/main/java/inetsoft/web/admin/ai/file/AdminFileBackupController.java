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
package inetsoft.web.admin.ai.file;

import inetsoft.sree.security.*;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;

/**
 * REST controller for the {@code backup_storage} admin-chat tool (Track C.5 03-reconcile.md
 * Correction 1). Placed in {@code community/core}, not {@code enterprise/}: it wraps
 * {@link inetsoft.web.admin.general.DataSpaceSettingsService#doBackup}, a community-tier
 * dependency, so this area is not enterprise-gated - it works on a community-only deployment too,
 * matching {@code AdminClusterController}/{@code AdminProviderController}'s own placement, not
 * the enterprise-tier areas (Data Sources/Viewsheets/Licensing).
 *
 * <p>{@code @Secured} names {@code settings/content/data-space}, the same resource
 * {@code DataSpaceFileSettingsController} uses. Unlike Cluster's {@code monitoring/cluster}, this
 * resource sets {@code hiddenForMultiTenancy: true}, so {@code @Secured}'s
 * {@code isComponentAccessible} half is a real, non-inert {@code isSiteAdmin} check on a
 * multi-tenant deployment - but every tool still adds its own independent
 * {@link #requireSiteAdmin} gate, the same belt-and-suspenders posture every prior area uses.
 */
@RestController
public class AdminFileBackupController {
   @Autowired
   public AdminFileBackupController(AdminFileBackupService backupService) {
      this.backupService = backupService;
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT,
      resource = "settings/content/data-space",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/file/backup")
   public Map<String, String> backup(@RequestBody Map<String, String> body, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return Map.of("backupPath", backupService.backup(body.get("task")));
   }

   /**
    * Same rationale and shape as {@code AdminAiController#requireSiteAdmin} - see there.
    */
   private void requireSiteAdmin(Principal user) {
      AdminAiCallerGuard.requireBearerAuthenticatedRequest();

      if(!OrganizationManager.getInstance().isSiteAdmin(user)) {
         throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Site Administrator role required");
      }
   }

   @ExceptionHandler(IllegalArgumentException.class)
   @ResponseStatus(HttpStatus.BAD_REQUEST)
   @ResponseBody
   public Map<String, String> handleIllegalArgument(IllegalArgumentException ex) {
      return Map.of("status", "failed", "error", String.valueOf(ex.getMessage()));
   }

   private final AdminFileBackupService backupService;
}
