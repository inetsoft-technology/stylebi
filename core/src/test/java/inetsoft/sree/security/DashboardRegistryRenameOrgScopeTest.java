/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.sree.security;

/*
 * Regression coverage for Bug #77230: DashboardRegistryManager.renameDashboard() used to loop
 * over every cache-resident DashboardRegistry in the whole process with no org filter, so an
 * admin renaming org A's global dashboard also silently renamed a same-named dashboard in every
 * other org's cache-resident per-user registries. No existing test covered this scenario -- see
 * community/core/src/test/resources/docs/org-lifecycle-resource-matrix.md, section "3.2
 * Dashboard": DashboardRegistryOrgLifecycleTest (scenarios 4c-4f) covers org copy/rename/delete
 * *lifecycle* operations moving an org's own dashboard data to a new org id, not a routine
 * admin-initiated renameDashboard() call leaking into a second, already-existing, unrelated org.
 *
 * Follows the same Spring-integration pattern as DashboardRegistryOrgLifecycleTest in this
 * package: BaseTestConfiguration + @SreeHome give a real DataSpace/SecurityEngine;
 * SecurityTestDataBuilder registers real orgs/users behind a real FileAuthenticationProvider so
 * DashboardRegistry's org-id resolution (the static SecurityEngine.getSecurity()
 * .getSecurityProvider() lookup) resolves our test org ids to themselves instead of collapsing to
 * null. A local @Configuration additionally supplies real DashboardRegistryManager/DashboardManager
 * beans -- BaseTestConfiguration does not declare either (they are @Service/component-scanned
 * beans in the real app, which this minimal @Configuration-only test context does not do), and
 * DashboardRegistry.renameDashboard()'s global branch calls both
 * DashboardManager.getManager() and DashboardRegistryManager.getInstance(), both of which resolve
 * through the Spring context via ConfigurationContext.
 */

import inetsoft.sree.ViewsheetEntry;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistry;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.sree.web.dashboard.VSDashboard;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.DependencyHandler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  DashboardRegistryRenameOrgScopeTest.DashboardBeansConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DashboardRegistryRenameOrgScopeTest {

   @Autowired
   private DashboardRegistryManager dashboardRegistryManager;

   private SecurityTestDataBuilder builder;

   @AfterEach
   void tearDown() {
      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   // ── primary scenario: cross-org isolation, AND same-org rename still happens ──

   @Test
   void rename_globalDashboard_scopesToOwningOrg_crossOrgUntouched_sameOrgRenamed() throws Exception {
      String orgA = "bug77230_orgA";
      String orgB = "bug77230_orgB";
      IdentityID alice = new IdentityID("alice", orgA);
      IdentityID bob = new IdentityID("bob", orgB);

      builder = SecurityTestDataBuilder.create()
         .addOrg("Bug77230OrgA", orgA)
         .addOrg("Bug77230OrgB", orgB)
         .addUser("alice", orgA, "password")
         .addUser("bob", orgB, "password")
         .setup();

      // Org A's own global (admin) registry -- this is the registry the rename is performed on.
      DashboardRegistry adminA = dashboardRegistryManager.getRegistry(orgA);
      adminA.addDashboard("Sales__GLOBAL", newVsDashboard(orgA, null));
      adminA.save();
      detachFileWatch(adminA);

      // Two DIFFERENT orgs' per-user registries, both cache-resident (via getRegistry()) at the
      // same time, each independently seeded with a same-named dashboard -- exactly the
      // collision shape the bug report describes.
      DashboardRegistry aliceReg = dashboardRegistryManager.getRegistry(alice);
      aliceReg.addDashboard("Sales__GLOBAL", newVsDashboard(orgA, "alice"));
      aliceReg.save();
      detachFileWatch(aliceReg);

      DashboardRegistry bobReg = dashboardRegistryManager.getRegistry(bob);
      bobReg.addDashboard("Sales__GLOBAL", newVsDashboard(orgB, "bob"));
      bobReg.save();
      detachFileWatch(bobReg);

      // Act: rename org A's global dashboard -- the exact production call chain (admin
      // DashboardRegistry.renameDashboard() -> DashboardRegistryManager.renameDashboard()).
      adminA.renameDashboard("Sales__GLOBAL", "Renamed__GLOBAL");

      // Org A's OWN per-user registry must be correctly renamed. This is the assertion that
      // would fail if the rename's *source* org id (DashboardRegistry.getEffectiveOrgId() on
      // the admin registry driving the rename) resolved to null instead of orgA -- a null
      // source would make every UserDashboardRegistry candidate's non-null getEffectiveOrgId()
      // compare unequal, and the loop would rename nothing at all, including org A's own data
      // (the gap the refuter's round-2 recheck found and the fixer closed).
      assertNull(aliceReg.getDashboard("Sales__GLOBAL"),
                "org A's own per-user registry must no longer have the old dashboard name");
      assertNotNull(aliceReg.getDashboard("Renamed__GLOBAL"),
                   "org A's own per-user registry must be renamed by its own org's global "
                   + "rename (Bug #77230)");

      // Org B's per-user registry -- a completely unrelated org, never referenced by the
      // rename call -- must be untouched.
      assertNotNull(bobReg.getDashboard("Sales__GLOBAL"),
                    "org B's unrelated per-user registry must be untouched by org A's rename "
                    + "(Bug #77230)");
      assertNull(bobReg.getDashboard("Renamed__GLOBAL"),
                "org B's registry must not pick up org A's new dashboard name");
   }

   // ── copyRegistry() per-user branch: organizationId must be populated so the rename filter
   //    can find it (the 4-arg-constructor fix at DashboardRegistryManager.java:~152) ──

   @Test
   void rename_afterCopyRegistryPerUserBranch_copiedInRegistryIsRenamed() throws Exception {
      String orgSrc = "bug77230_src";
      String orgNew = "bug77230_new";
      IdentityID carolSrc = new IdentityID("carol", orgSrc);

      builder = SecurityTestDataBuilder.create()
         .addOrg("Bug77230Src", orgSrc)
         .addOrg("Bug77230New", orgNew)
         .addUser("carol", orgSrc, "password")
         .setup();

      DashboardRegistry carolSrcReg = dashboardRegistryManager.getRegistry(carolSrc);
      carolSrcReg.addDashboard("Sales__GLOBAL", newVsDashboard(orgSrc, "carol"));
      carolSrcReg.save();
      detachFileWatch(carolSrcReg);

      Organization orgSrcObj = new Organization(orgSrc);
      Organization orgNewObj = new Organization(orgNew);

      // Act 1: copy carol's per-user registry into the new org -- the exact call
      // IdentityService.copyDashboardRegistry()'s per-user loop makes during a real org copy.
      dashboardRegistryManager.copyRegistry(carolSrc, orgSrcObj, orgNewObj);

      IdentityID carolNew = new IdentityID("carol", orgNew);
      DashboardRegistry carolNewReg = dashboardRegistryManager.getRegistry(carolNew);
      assertNotNull(carolNewReg.getDashboard("Sales__GLOBAL"),
                    "copyRegistry()'s per-user branch must have cloned the dashboard into the "
                    + "new org's cache-resident registry");
      detachFileWatch(carolNewReg);

      // Direct, white-box check on the protected field the line-~152 fix populates. This is
      // deliberately independent of the behavioral rename assertions below: those go through
      // UserDashboardRegistry.getEffectiveOrgId()'s user.orgID fallback, which (as verified by
      // surgically reverting just the line-~152 constructor-argument fix while keeping the rest
      // of Bug #77230's fix in place) already happens to mask this specific gap for the
      // rename-scoping code path, because copyRegistry() always constructs the destination
      // IdentityID with the correct target org id. That makes the *behavioral* assertions below
      // insufficient on their own to pin down line ~152 specifically -- so this field-level
      // check is what actually confirms the organizationId-population fix, independent of the
      // fallback that happens to compensate for it today.
      String rawOrganizationId = readOrganizationIdField(carolNewReg);
      assertEquals(orgNew, rawOrganizationId,
                  "copyRegistry()'s per-user branch must populate organizationId directly via "
                  + "the 4-arg UserDashboardRegistry constructor (Bug #77230) -- before the fix "
                  + "this field was permanently null despite the registry genuinely belonging "
                  + "to orgNew by cache key and file path");

      // The new org's own global dashboard, same name, seeded independently -- this is what an
      // admin of orgNew renames.
      DashboardRegistry adminNew = dashboardRegistryManager.getRegistry(orgNew);
      adminNew.addDashboard("Sales__GLOBAL", newVsDashboard(orgNew, null));
      adminNew.save();
      detachFileWatch(adminNew);

      // Act 2: rename orgNew's global dashboard.
      adminNew.renameDashboard("Sales__GLOBAL", "Renamed__GLOBAL");

      // The copied-in per-user registry must be renamed too. Before the fix, copyRegistry()'s
      // per-user branch constructed UserDashboardRegistry via the 3-arg constructor, leaving
      // organizationId permanently null despite the registry genuinely belonging to orgNew by
      // cache key and file path -- so the rename filter would have silently skipped it.
      assertNull(carolNewReg.getDashboard("Sales__GLOBAL"),
                "the copied-in per-user registry must no longer have the old dashboard name");
      assertNotNull(carolNewReg.getDashboard("Renamed__GLOBAL"),
                   "the copied-in per-user registry (populated via copyRegistry()'s per-user "
                   + "branch) must be renamed -- confirms organizationId was correctly "
                   + "populated at copy time (Bug #77230)");
   }

   // ── fixture helpers (mirrors DashboardRegistryOrgLifecycleTest's helpers in this package) ──

   /**
    * Remove the seeded registry's data space change listener (Bug #75793). Without this, an
    * asynchronously-delivered change event for the seeding save() above can land at an arbitrary
    * later point and reset()/reload the in-memory dashboardsMap out from under the rename call,
    * making dashboards vanish instead of renaming -- a known, pre-existing, unrelated timing
    * issue in this test harness (see DashboardRegistryOrgLifecycleTest.detachFileWatch()'s
    * fuller explanation and the refuter's independent rediscovery of the same confound), not a
    * sign that the Bug #77230 fix itself is broken. clear() unregisters the listener; the
    * instance stays in the manager's cache so the code under test still operates on this same
    * object.
    */
   private static void detachFileWatch(DashboardRegistry registry) {
      registry.clear();
   }

   /**
    * Reads the protected {@code DashboardRegistry.organizationId} field via reflection (this
    * test lives in a different package than the field's declaring class, unlike the fixer's and
    * refuter's throwaway tests, which were package-local to {@code inetsoft.sree.web.dashboard}
    * and could read it directly).
    */
   private static String readOrganizationIdField(DashboardRegistry registry) throws Exception {
      java.lang.reflect.Field field = DashboardRegistry.class.getDeclaredField("organizationId");
      field.setAccessible(true);
      return (String) field.get(registry);
   }

   private VSDashboard newVsDashboard(String orgId, String userName) {
      VSDashboard dashboard = new VSDashboard();
      IdentityID owner = userName == null ? null : new IdentityID(userName, orgId);
      int scope = owner == null ? AssetRepository.GLOBAL_SCOPE : AssetRepository.USER_SCOPE;
      String path = (userName == null ? "" : userName + "/") + "myvs";
      AssetEntry entry = new AssetEntry(scope, AssetEntry.Type.VIEWSHEET, path, owner, orgId);

      ViewsheetEntry viewsheetEntry = new ViewsheetEntry(path, owner);
      viewsheetEntry.setIdentifier(entry.toIdentifier());
      dashboard.setViewsheet(viewsheetEntry);
      return dashboard;
   }

   // ── beans BaseTestConfiguration does not declare on its own ──

   @Configuration
   public static class DashboardBeansConfig {
      @Bean
      public DashboardRegistryManager dashboardRegistryManager(
         ApplicationEventPublisher eventPublisher, SecurityEngine securityEngine,
         DependencyHandler dependencyHandler, inetsoft.util.DataSpace dataSpace)
      {
         return new DashboardRegistryManager(eventPublisher, securityEngine, dependencyHandler, dataSpace);
      }

      @Bean
      public DashboardManager dashboardManager(SecurityEngine securityEngine,
         DashboardRegistryManager dashboardRegistryManager,
         KeyValueStorageManager keyValueStorageManager)
      {
         return new DashboardManager(securityEngine, dashboardRegistryManager, keyValueStorageManager);
      }
   }
}
