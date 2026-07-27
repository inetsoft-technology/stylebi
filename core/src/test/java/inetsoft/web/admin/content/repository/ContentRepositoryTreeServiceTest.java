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
package inetsoft.web.admin.content.repository;

/*
 * General-purpose test file for ContentRepositoryTreeService. Add further scenarios for this class
 * here rather than creating new per-scenario test classes -- keep each scenario's own rationale in
 * a comment block right above its test method(s), the way the 6f scenario below does, so the
 * file-level comment doesn't have to be rewritten every time a new scenario is added.
 */

import inetsoft.mv.MVManager;
import inetsoft.report.LibManagerProvider;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.uql.XRepository;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationContextHolder;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.IndexedStorage;
import inetsoft.util.ThreadContext;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.schedule.ScheduleTaskFolderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ContentRepositoryTreeServiceTest {

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      OrganizationContextHolder.setCurrentOrgId(null);
   }

   /*
    * Scenario 6f (matrix row): community/core/src/test/resources/docs/org-lifecycle-resource-matrix.md,
    * section "3.4 Autosave 文件" / "Autosave — 管理/恢复层".
    *
    * addRecycleAutoSaved() (:413-505) is called with a `userNodesList` built earlier by
    * createUserNodes() -> getOrgUsers() -> securityProvider.getUsers() (:2004). For each seeded
    * autosave file, the owning user (parsed out of the file's name, attrs[2]) is matched against
    * userNodesList; if none of the entries in that list is the file's owner, the inner loop's
    * `continue` (:459-463) skips the file for every user node, `users`/`map` never get it added, and
    * it is silently missing from the "Auto Saved Files" EM tree node -- no error, no fallback bucket.
    * This is the same root-cause SHAPE as Bug #75759 (three、3.7.1's Repository/Recycle Bin tree also
    * silently drops entries when securityProvider.getUsers() hasn't (yet) enumerated the owning user),
    * just for the sibling "Auto Saved Files" tree instead.
    *
    * addRecycleAutoSaved() itself doesn't touch any ContentRepositoryTreeService instance field (only
    * static AutoSaveUtils calls plus the two method parameters), so this test drives it directly via
    * reflection (it's private) rather than going through the full createUserNodes()/getRootNodes()
    * chain -- avoids needing to correctly wire up the other 14 constructor dependencies'
    * (RepletRegistryService/DashboardManager/etc) behavior, none of which addRecycleAutoSaved() reaches.
    * UserNodes is itself a `private static final` nested class, so building the input list also goes
    * through reflection (Class.forName + a reflective constructor call) rather than referencing the
    * type by name in this file.
    *
    * Not yet run/confirmed -- left @Disabled pending verification (see the AutoSaveServiceOrgLifecycleTest
    * class-level comment for the sibling 6e/6g/6h scenarios and the 6g correction made while writing
    * that file).
    */
   @Test
   @Disabled("6f: not yet run/confirmed -- see comment above")
   void addRecycleAutoSaved_ownerNotYetEnumerated_fileSilentlyDropped() throws Exception {
      String orgId = "sixf_org";
      Principal principal = new SRPrincipal(new IdentityID("sixf_actor", orgId), new IdentityID[0],
                                            new String[0], orgId, 1L);

      // seed a recycled autosave file (the kind the "Auto Saved Files" tree node lists) owned by a
      // user who will NOT appear in userNodesList below
      String missingUserKey = new IdentityID("sixf_missing_user", orgId).convertToKey();
      String rawFile = "8^VIEWSHEET^" + missingUserKey + "^Untitled-1^0_0_0_0_0_0_0_1~";
      AutoSaveUtils.writeAutoSaveFile("dummy".getBytes(),
                                      AutoSaveUtils.getAutoSavedByName(rawFile, true), principal);

      ContentRepositoryTreeService service = new ContentRepositoryTreeService(
         mock(SecurityProvider.class), mock(XRepository.class),
         mock(ResourcePermissionService.class), mock(RepletRegistryService.class),
         mock(ScheduleTaskFolderService.class), mock(MVManager.class),
         mock(SecurityEngine.class), mock(ScheduleManager.class),
         mock(DataSourceRegistry.class), mock(DashboardManager.class),
         mock(IndexedStorage.class), mock(DashboardRegistryManager.class),
         mock(LibManagerProvider.class), mock(RecycleBin.class),
         mock(RepletRegistryManager.class));

      // a known, already-enumerated user who is NOT the owner of the seeded file -- represents
      // "securityProvider.getUsers() hasn't (yet) returned the file's actual owner" for a freshly
      // cloned org
      List<Object> userNodesList = new ArrayList<>();
      userNodesList.add(newUserNodes(new IdentityID("sixf_known_user", orgId)));

      @SuppressWarnings("unchecked")
      List<ContentRepositoryTreeNode> result = (List<ContentRepositoryTreeNode>)
         ReflectionTestUtils.invokeMethod(service, "addRecycleAutoSaved", userNodesList, principal);

      assertNotNull(result);
      assertTrue(result.isEmpty(),
                "addRecycleAutoSaved() currently drops an autosave file silently (no error, no "
                + "fallback bucket) whenever its owning user isn't present in the already-enumerated "
                + "user-node list -- this pins the current (suspected buggy) behavior, matching Bug "
                + "#75759's root-cause pattern for the sibling Recycle Bin tree");
   }

   private static Object newUserNodes(IdentityID user) throws Exception {
      Class<?> cls = Class.forName(
         "inetsoft.web.admin.content.repository.ContentRepositoryTreeService$UserNodes");
      Constructor<?> ctor = cls.getDeclaredConstructor(IdentityID.class);
      ctor.setAccessible(true);
      return ctor.newInstance(user);
   }
}
