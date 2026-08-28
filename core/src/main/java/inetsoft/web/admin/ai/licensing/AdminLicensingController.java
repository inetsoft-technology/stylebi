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
package inetsoft.web.admin.ai.licensing;

import inetsoft.report.internal.license.License;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.security.*;
import inetsoft.web.admin.ai.AdminAiCallerGuard;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST controller for the licensing admin-plugin area (01-spec.md section 10). Placed in
 * {@code community/core}, not {@code enterprise/} -- {@code LicenseKeySettingsService}/
 * {@code LicenseManager} both already live in {@code community/core}, only the
 * {@code LicenseStrategy} implementation ({@code EnterpriseLicenseStrategy}) lives in
 * {@code enterprise/}, loaded reflectively (01-spec.md section 10).
 *
 * <p><b>03-reconcile.md addition 1, build-blocking:</b> unlike every other community-placed area
 * (providers, cluster), community placement here does NOT mean this area works correctly on a
 * community-only deployment. {@code LicenseManager} falls back to {@code NoopLicenseStrategy} when
 * the enterprise module is absent, whose {@code parseLicense} ignores its argument and returns a
 * default {@code License} with null {@code type()}/{@code expires()} -- calling
 * {@code License.valid()} on that object throws an unhandled NPE
 * ({@code LocalDateTime.now().isBefore(null)}), and its {@code getInstalledLicenses()} returns a
 * permanent phantom single null-key entry, never empty. {@link #requireEnterprise()} refuses loud,
 * on all four endpoints (reads included), before any of that code is ever reached.
 */
@RestController
public class AdminLicensingController {
   @Autowired
   public AdminLicensingController(LicenseManager licenseManager,
                                   LicenseChangePlanService planService,
                                   LicenseChangesetApplyService applyService)
   {
      this.licenseManager = licenseManager;
      this.planService = planService;
      this.applyService = applyService;
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/general",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/licensing/keys")
   public LicenseKeyListResponse listLicenseKeys(Principal user) {
      requireSiteAdmin(user);
      requireEnterprise();
      return new LicenseKeyListResponse(licenseManager.getInstalledLicenses().stream()
         .map(LicenseKeyProjection::of)
         .collect(Collectors.toList()));
   }

   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/general",
      actions = ResourceAction.ACCESS))
   @GetMapping("/api/wiz/v1/admin/licensing/keys/{key}")
   public LicenseKeyLookupResult getLicenseKey(@PathVariable("key") String key, Principal user) {
      requireSiteAdmin(user);
      requireEnterprise();
      Optional<License> found = licenseManager.getInstalledLicenses().stream()
         .filter(l -> Objects.equals(l.key(), key))
         .findFirst();
      return found.map(license -> new LicenseKeyLookupResult(true, LicenseKeyProjection.of(license)))
         .orElseGet(() -> new LicenseKeyLookupResult(false, null));
   }

   /**
    * Resolves a license key change plan (add/remove) without mutating anything. See
    * {@code AdminAiController#preview} for the shape this mirrors.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/general",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/licensing/preview")
   public ResolvedPlan preview(@RequestBody LicenseChangePlanRequest req, Principal user) {
      requireSiteAdmin(user);
      requireEnterprise();
      return planService.resolve(req);
   }

   /**
    * Applies a reviewed license change plan. Every verb in this area requires a Tier-2 backup
    * (01-spec.md section 4/6/7/14 D4 -- unconditional), taken synchronously inside
    * {@link LicenseChangesetApplyService#apply} before any mutation.
    */
   @Secured(@RequiredPermission(
      resourceType = ResourceType.EM_COMPONENT, resource = "settings/general",
      actions = ResourceAction.ACCESS))
   @PostMapping("/api/wiz/v1/admin/licensing/apply")
   public LicenseApplyResult apply(@RequestBody LicenseApplyRequest req, Principal user)
      throws Exception
   {
      requireSiteAdmin(user);
      requireEnterprise();
      return applyService.apply(req, user);
   }

   /** Same rationale and shape as {@code AdminProviderController#requireSiteAdmin} -- see there. */
   private void requireSiteAdmin(Principal user) {
      AdminAiCallerGuard.requireBearerAuthenticatedRequest();

      if(!OrganizationManager.getInstance().isSiteAdmin(user)) {
         throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Site Administrator role required");
      }
   }

   /** 03-reconcile.md addition 1 -- checked after authentication/authorization so an
    * unauthenticated or non-site-admin caller learns nothing about the deployment's licensing tier
    * before being refused for the more fundamental reason. 404, matching every other
    * enterprise-only area's HTTP contract even though the mechanism differs (those areas' classes
    * simply do not exist on community; this area's classes do, so the check is explicit). */
   private static void requireEnterprise() {
      if(!LicenseManager.isEnterprise()) {
         throw new ResponseStatusException(HttpStatus.NOT_FOUND,
            "licensing management requires the enterprise module");
      }
   }

   @ExceptionHandler(IllegalArgumentException.class)
   @ResponseStatus(HttpStatus.BAD_REQUEST)
   @ResponseBody
   public Map<String, String> handleIllegalArgument(IllegalArgumentException ex) {
      return Map.of("status", "failed", "error", String.valueOf(ex.getMessage()));
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

   private final LicenseManager licenseManager;
   private final LicenseChangePlanService planService;
   private final LicenseChangesetApplyService applyService;
}
