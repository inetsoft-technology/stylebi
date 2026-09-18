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
package inetsoft.web.admin.ai.datasource;

import inetsoft.web.admin.datasource.*;
import inetsoft.report.internal.Util;
import inetsoft.sree.security.*;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import inetsoft.web.security.auth.MissingResourceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;

/**
 * REST controller for the data-sources admin-plugin area (01-spec.md section 10). Same {@code
 * requireSiteAdmin}/{@code AdminAiCallerGuard} shape as every prior area's own controller --
 * copied, not shared, matching the existing precedent for that duplication.
 *
 * <p>{@code @Secured} uses a fixed {@code ResourceType.EM_COMPONENT}/{@code
 * "settings/content/repository"}/{@code ResourceAction.ACCESS}, mirroring {@code
 * AdminViewsheetController} -- the sibling that administers the same asset-repository domain data
 * sources sit under -- and every other controller in this package family, all of which gate on a
 * fixed EM component rather than a per-item ACL (bug 76354: the original {@code
 * ResourceType.DATA_SOURCE} with no {@code resource()} and no {@code @PermissionPath} parameter hit
 * {@code SecuredAspect}'s unconditional "No permission path or resource specified" throw on every
 * call). This is belt-and-suspenders only (section 4a): {@code checkPermission} (which {@code
 * @Secured} ultimately resolves through) is a no-op for every admin-chat caller, so {@code
 * requireSiteAdmin}'s direct {@code isSiteAdmin} read below is the real gate for this caller
 * population, not this annotation.
 *
 * <p>Only {@code GET /data-sources/{id}} is exposed for a single-item read, matching section 10's
 * table exactly -- a name-only {@code get_data_source} call is the PLUGIN tool layer's own job to
 * resolve (call {@code list_data_sources({name})} first, per section 3's "resolves id via
 * list_data_sources's own underlying call first"), not a second Java-side lookup path.
 */
@RestController
public class AdminDataSourceController {
   @Autowired
   public AdminDataSourceController(DataSourceService dataSourceService,
                                    DataSourceChangePlanService planService,
                                    DataSourceChangesetApplyService applyService)
   {
      this.dataSourceService = dataSourceService;
      this.planService = planService;
      this.applyService = applyService;
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/data-sources")
   public DataSourceList listDataSources(
      @RequestParam(value = "name", required = false) String name, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return dataSourceService.getDataSources(name, user);
   }

   /**
    * Wraps {@code getDataSource}. Password on a JDBC hit is translated from the wrapped API's own
    * six-character {@code "******"} literal to {@link Util#PLACEHOLDER_PASSWORD} (section 9) --
    * never the real value either way.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/data-sources/{id}")
   public DataSourceProperties getDataSource(@PathVariable("id") String id, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      DataSourceProperties properties = dataSourceService.getDataSource(id, user);

      if(properties instanceof JdbcDataSourceProperties &&
         ((JdbcDataSourceProperties) properties).isRequireLogin())
      {
         ((JdbcDataSourceProperties) properties).setPassword(Util.PLACEHOLDER_PASSWORD);
      }

      return properties;
   }

   /**
    * Resolves a data-source change plan without mutating anything. See {@code
    * AdminAiController#preview} for the shape this mirrors.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/data-sources/preview")
   public ResolvedPlan preview(@RequestBody DataSourceChangePlanRequest req, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return planService.resolve(req, user);
   }

   /**
    * Live JDBC connectivity probe against a PENDING (not-yet-applied) set of connection fields --
    * performs no mutation (bug 76599, Gap 1). Recommended before {@link #preview}/{@link #apply}
    * on any plan touching url/driver/credentials. Delegates straight through to {@link
    * DataSourceChangePlanService#testConnection}, which merges {@code spec} onto the current data
    * source's real (unmasked) connection fields the same way {@link #preview}/{@link #apply}
    * already do, then calls {@code DatabaseSettingsService.testConnection} directly -- the exact
    * same JDBC probe EM's own "Test Connection" button already uses. Never returns the real
    * password: {@link ConnectionStatus} carries only a status message and a connected flag.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/data-sources/test-connection")
   public ConnectionStatus testConnection(@RequestBody DataSourceTestConnectionRequest req,
                                          Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return planService.testConnection(req, user);
   }

   /**
    * Applies a reviewed data-source change plan, all-or-nothing. Same status contract as {@code
    * AdminAiController#apply}: {@code applied}/{@code rolled-back}/{@code rollback-failed}.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/content/repository",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/data-sources/apply")
   public DataSourceApplyResult apply(@RequestBody DataSourceApplyRequest req, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      return applyService.apply(req, user);
   }

   /** Same rationale and shape as {@code AdminAiController#requireSiteAdmin} -- see there. */
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

   @ExceptionHandler(MissingResourceException.class)
   @ResponseStatus(HttpStatus.NOT_FOUND)
   @ResponseBody
   public Map<String, String> handleMissingResource(MissingResourceException ex) {
      return Map.of("status", "not-found", "error", String.valueOf(ex.getMessage()));
   }

   @ExceptionHandler(AdminChangesetApplyService.PlanHashMismatchException.class)
   @ResponseStatus(HttpStatus.CONFLICT)
   @ResponseBody
   public Map<String, Object> handlePlanHashMismatch(
      AdminChangesetApplyService.PlanHashMismatchException ex)
   {
      return Map.of("status", "conflict", "error", String.valueOf(ex.getMessage()),
                    "plan", ex.current());
   }

   @ExceptionHandler(AdminChangesetApplyService.TaskTokenMismatchException.class)
   @ResponseStatus(HttpStatus.CONFLICT)
   @ResponseBody
   public Map<String, Object> handleTaskTokenMismatch(
      AdminChangesetApplyService.TaskTokenMismatchException ex)
   {
      return Map.of("status", "conflict", "error", String.valueOf(ex.getMessage()),
                    "plan", ex.current());
   }

   private final DataSourceService dataSourceService;
   private final DataSourceChangePlanService planService;
   private final DataSourceChangesetApplyService applyService;
}
