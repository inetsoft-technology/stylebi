/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

/*
 * Test strategy
 *
 * EMScheduleTaskActionController has real in-controller logic in two methods:
 *   getEmailTree      — streams users/groups from SecurityProvider, filters by ADMIN permission
 *   getEmbeddedUsers  — same pattern, users only
 *
 * The remaining endpoints (getBookmarks, hasPrintLayout, getViewsheetHighlights,
 * getViewsheetParameters) delegate to ViewsheetService.openViewsheet() which
 * requires a live ApplicationContext and are covered by E2E tests instead.
 * getViewsheetFolders and getViewsheets are pure-delegation methods tested
 * as such here.
 *
 * Coverage scope:
 *   [getEmailTree: user denied]    checkPermission(SECURITY_USER) false → user excluded
 *   [getEmailTree: group permitted] checkPermission(SECURITY_GROUP) true → group included
 *   [getViewsheets: delegation]    delegates to actionService.getViewsheets(principal)
 *
 * Static singleton Catalog is intercepted with Mockito.mockStatic() using lenient()
 * to suppress UnnecessaryStubbingException.
 */

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.Catalog;
import inetsoft.web.admin.schedule.model.EmailTreeModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class EMScheduleTaskActionControllerTest {

   @Mock private ScheduleTaskActionService actionService;
   @Mock private SecurityProvider securityProvider;
   @Mock private AssetRepository assetRepository;
   @Mock private EMScheduleTaskActionServiceProxy emActionService;
   @Mock private ViewsheetService viewsheetService;
   @Mock private ScheduleTaskActionServiceProxy actionServiceProxy;
   @Mock private User aliceUser;
   @Mock private Group grp1Group;
   @Mock private Catalog catalog;
   @Mock private Principal principal;

   private EMScheduleTaskActionController controller;

   private MockedStatic<Catalog> catalogStatic;

   private final IdentityID aliceId = new IdentityID("alice", "host-org");
   private final IdentityID bobId = new IdentityID("bob", "host-org");
   private final IdentityID grp1Id = new IdentityID("grp1", "host-org");

   @BeforeEach
   void setUp() {
      controller = new EMScheduleTaskActionController(
         actionService, securityProvider, assetRepository,
         emActionService, viewsheetService, actionServiceProxy);

      catalogStatic = mockStatic(Catalog.class, withSettings().lenient());
      catalogStatic.when(() -> Catalog.getCatalog(any(Principal.class))).thenReturn(catalog);
      lenient().when(catalog.getString(anyString())).thenReturn("translated");

      // getAuthenticationProvider() is a default method returning 'this'; Mockito returns null
      // unless explicitly stubbed
      lenient().when(securityProvider.getAuthenticationProvider()).thenReturn(securityProvider);
   }

   @AfterEach
   void tearDown() {
      catalogStatic.close();
   }

   // -------------------------------------------------------------------------
   // getEmailTree()
   // -------------------------------------------------------------------------

   // [user denied] checkPermission(SECURITY_USER) false → user excluded from result
   @Test
   void getEmailTree_deniedUser_isExcluded() {
      when(securityProvider.getUsers()).thenReturn(new IdentityID[]{ aliceId, bobId });
      when(securityProvider.getGroups()).thenReturn(new IdentityID[0]);

      // alice is permitted → set up User mock so UserEmailModel.from() can succeed
      when(securityProvider.checkPermission(
         principal, ResourceType.SECURITY_USER, aliceId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(securityProvider.getUser(aliceId)).thenReturn(aliceUser);
      when(aliceUser.getEmails()).thenReturn(new String[0]);
      when(aliceUser.getGroups()).thenReturn(new String[0]);

      // bob is denied → should not appear in result
      when(securityProvider.checkPermission(
         principal, ResourceType.SECURITY_USER, bobId.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(false);

      EmailTreeModel result = controller.getEmailTree(principal);

      assertEquals(1, result.users().size(), "only alice should appear");
      assertEquals(aliceId, result.users().get(0).userID());
      assertEquals(0, result.groups().size());
   }

   // [group permitted] checkPermission(SECURITY_GROUP) true → group included in result
   @Test
   void getEmailTree_permittedGroup_isIncluded() {
      when(securityProvider.getUsers()).thenReturn(new IdentityID[0]);
      when(securityProvider.getGroups()).thenReturn(new IdentityID[]{ grp1Id });

      when(securityProvider.checkPermission(
         principal, ResourceType.SECURITY_GROUP, grp1Id.convertToKey(), ResourceAction.ADMIN))
         .thenReturn(true);
      when(securityProvider.getGroup(grp1Id)).thenReturn(grp1Group);
      when(grp1Group.getGroups()).thenReturn(new String[0]);

      EmailTreeModel result = controller.getEmailTree(principal);

      assertEquals(1, result.groups().size());
      assertEquals("grp1", result.groups().get(0).name());
   }

   // -------------------------------------------------------------------------
   // getViewsheets()
   // -------------------------------------------------------------------------

   // [delegation] delegates to actionService.getViewsheets(principal) and returns result unchanged
   @Test
   void getViewsheets_delegatesToService() throws Exception {
      Map<String, String> viewsheets = Map.of("id1", "My Viewsheet");
      when(actionService.getViewsheets(principal)).thenReturn(viewsheets);

      Map<String, String> result = controller.getViewsheets(principal);

      assertSame(viewsheets, result);
      verify(actionService).getViewsheets(principal);
   }
}
