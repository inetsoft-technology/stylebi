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
package inetsoft.web.admin.schedule;

/*
 * Bug #77210: ScheduleTaskActionService.getBookmarks (behind both
 * GET /api/portal/schedule/task/action/bookmarks and GET /api/em/schedule/task/action/bookmarks)
 * built an AssetEntry from the client-supplied viewsheet id, whose org segment is kept, and
 * listed that viewsheet's shared bookmarks with no READ check. An org A user could list another
 * org's bookmark names and owners.
 *
 * The org B viewsheet bookmark is seeded through a real BlobIndexedStorage and read through the
 * real AbstractAssetEngine (getBookmarkUsers / getVSBookmark), so the storage path is exercised.
 * StubAssetEngine inherits AbstractAssetEngine.checkPermission(), which always grants, so the
 * same-org and site admin controls only show that the org check lets them through;
 * DenyingStubAssetEngine stands in for a same-org user without the REPORT READ ACL.
 *
 * Bug #78057: on bob's private (USER_SCOPE) viewsheet the 3-arg checkAssetPermission let any
 * same-org user through (its owner check is a no-op), so carol saw bob's shared bookmarks. The
 * service now uses checkAssetPermission(..., READ, true), the check that opening the viewsheet
 * uses.
 */

import inetsoft.report.LibManagerProvider;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.*;
import inetsoft.web.viewsheet.model.VSBookmarkInfoModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ScheduleTaskActionServiceBookmarksTest {
   private static final String ORG_A = "orga_id";
   private static final String ORG_B = "orgb_id";
   private static final String ORG_B_VS = "1^128^__NULL__^Finance/Secret77210^" + ORG_B;
   private static final String BOB_PRIVATE_VS =
      "4^128^bob~;~" + ORG_B + "^Private/Dash78057^" + ORG_B;
   private static final String SHARED = "secretShared";
   private static final String PRIVATE = "secretPrivate";

   private static SecurityTestDataBuilder builder;
   private static SRPrincipal orgAUser;
   private static SRPrincipal orgBUser;
   private static SRPrincipal bobUser;
   private static SRPrincipal siteAdmin;

   @Autowired
   private BlobStorageManager blobStorageManager;

   private BlobIndexedStorage storage;
   private MockedStatic<SUtil> sutil;

   @BeforeAll
   static void setUpAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("orga", ORG_A)
         .addOrg("orgb", ORG_B)
         .addUser("orgAUser", ORG_A, "password")
         .addUser("bob", ORG_B, "password")
         .addUser("carol", ORG_B, "password")
         .addUser("siteAdmin77210", ORG_A, "password")
         .addSysAdminRole("siteAdmins77210", ORG_A)
         .addUserToRole("siteAdmin77210", "siteAdmins77210", ORG_A)
         .setup();
      orgAUser = builder.principalOf("orgAUser", ORG_A);
      orgBUser = builder.principalOf("carol", ORG_B);
      bobUser = builder.principalOf("bob", ORG_B);
      siteAdmin = builder.principalOf("siteAdmin77210", ORG_A);
   }

   @AfterAll
   static void tearDownAll() {
      if(builder != null) {
         builder.teardown();
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      ThreadContext.setContextPrincipal(null);
      storage = new BlobIndexedStorage(blobStorageManager);

      // bob (org B) saved one shared and one private bookmark on an org B viewsheet and on
      // his own private viewsheet
      seedBookmarks(ORG_B_VS);
      seedBookmarks(BOB_PRIVATE_VS);

      sutil = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
      sutil.when(SUtil::isMultiTenant).thenReturn(true);
   }

   @AfterEach
   void tearDown() {
      AssetUtil.setAssetRepository(false, null);
      ThreadContext.setContextPrincipal(null);

      if(sutil != null) {
         sutil.close();
      }
   }

   @Test
   void otherOrgViewsheet_orgAUser_listsNoBookmarks() {
      ThreadContext.setContextPrincipal(orgAUser);
      StubAssetEngine engine = new StubAssetEngine(storage);
      ScheduleTaskActionService service = service(engine);

      // the seeded bookmark is reachable through the real storage path (unguarded sink)
      assertTrue(names(VSUtil.getBookmarks(AssetEntry.createAssetEntry(ORG_B_VS),
                                           new IdentityID("orgAUser", ORG_A))).contains(SHARED));

      assertEquals(Collections.emptyList(), service.getBookmarks(ORG_B_VS, false, orgAUser));
      assertEquals(Collections.emptyList(), service.getBookmarks(ORG_B_VS, true, orgAUser));
   }

   @Test
   void otherOrgViewsheet_siteAdmin_listsSharedBookmarks() {
      ThreadContext.setContextPrincipal(siteAdmin);
      List<String> names = modelNames(service(new StubAssetEngine(storage))
                                         .getBookmarks(ORG_B_VS, true, siteAdmin));
      assertTrue(names.contains(SHARED), names.toString());
      assertFalse(names.contains(PRIVATE), names.toString());
   }

   @Test
   void sameOrgViewsheet_orgBUserWithRead_listsSharedBookmarks() {
      ThreadContext.setContextPrincipal(orgBUser);
      List<String> names = modelNames(service(new StubAssetEngine(storage))
                                         .getBookmarks(ORG_B_VS, false, orgBUser));
      assertTrue(names.contains(SHARED), names.toString());
      assertFalse(names.contains(PRIVATE), names.toString());
   }

   @Test
   void sameOrgViewsheet_orgBUserWithoutRead_listsNoBookmarks() {
      ThreadContext.setContextPrincipal(orgBUser);
      assertEquals(Collections.emptyList(),
                   service(new DenyingStubAssetEngine(storage)).getBookmarks(ORG_B_VS, false, orgBUser));
   }

   @Test
   void otherUsersPrivateViewsheet_sameOrgUser_listsNoBookmarks() {
      ThreadContext.setContextPrincipal(orgBUser);
      StubAssetEngine engine = new StubAssetEngine(storage);
      ScheduleTaskActionService service = service(engine);

      // the seeded bookmark is reachable through the real storage path (unguarded sink)
      assertTrue(names(VSUtil.getBookmarks(AssetEntry.createAssetEntry(BOB_PRIVATE_VS),
                                           new IdentityID("carol", ORG_B))).contains(SHARED));

      assertEquals(Collections.emptyList(), service.getBookmarks(BOB_PRIVATE_VS, false, orgBUser));
      assertEquals(Collections.emptyList(), service.getBookmarks(BOB_PRIVATE_VS, true, orgBUser));
   }

   @Test
   void privateViewsheet_owner_listsOwnBookmarks() {
      ThreadContext.setContextPrincipal(bobUser);
      List<String> names = modelNames(service(new StubAssetEngine(storage))
                                         .getBookmarks(BOB_PRIVATE_VS, false, bobUser));
      assertTrue(names.contains(SHARED), names.toString());
      assertTrue(names.contains(PRIVATE), names.toString());
   }

   @Test
   void nullOrMalformedId_returnsEmptyWithoutPermissionCheck() throws Exception {
      AssetRepository repository = mock(AssetRepository.class);
      ScheduleTaskActionService service = service(repository);

      assertEquals(Collections.emptyList(), service.getBookmarks(null, false, orgAUser));
      assertEquals(Collections.emptyList(), service.getBookmarks("not-an-identifier", false, orgAUser));
      verify(repository, never()).checkAssetPermission(any(), any(), any(), anyBoolean());
   }

   @Test
   void nonXPrincipal_returnsEmptyWithoutPermissionCheck() throws Exception {
      AssetRepository repository = mock(AssetRepository.class);
      Principal plain = () -> "orgAUser~;~" + ORG_A;

      assertEquals(Collections.emptyList(), service(repository).getBookmarks(ORG_B_VS, false, plain));
      assertEquals(Collections.emptyList(), service(repository).getBookmarks(ORG_B_VS, false, null));
      verify(repository, never()).checkAssetPermission(any(), any(), any(), anyBoolean());
   }

   @Test
   void unexpectedPermissionCheckFailure_returnsEmpty() throws Exception {
      AssetRepository repository = mock(AssetRepository.class);
      doThrow(new IllegalStateException("boom"))
         .when(repository).checkAssetPermission(any(), any(), any(), anyBoolean());

      assertEquals(Collections.emptyList(),
                   service(repository).getBookmarks(ORG_B_VS, true, orgBUser));
   }

   private void seedBookmarks(String vsId) throws Exception {
      AssetEntry vsEntry = AssetEntry.createAssetEntry(vsId);
      IdentityID bob = new IdentityID("bob", ORG_B);
      AssetEntry bookmarkEntry = new AssetEntry(
         AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET_BOOKMARK,
         VSUtil.createBookmarkIdentifier(vsEntry), bob, ORG_B);
      VSBookmark bookmark = new VSBookmark(vsId, bob);
      bookmark.addBookmark(SHARED, new Viewsheet(), VSBookmarkInfo.ALLSHARE, false, false);
      bookmark.addBookmark(PRIVATE, new Viewsheet(), VSBookmarkInfo.PRIVATE, false, false);
      storage.putXMLSerializable(bookmarkEntry.toIdentifier(), bookmark);
   }

   private ScheduleTaskActionService service(AssetRepository repository) {
      if(repository instanceof AbstractAssetEngine) {
         AssetUtil.setAssetRepository(false, repository);
      }

      ScheduleService scheduleService = mock(ScheduleService.class);
      when(scheduleService.getBookmarkModel(any(), anyBoolean())).thenAnswer(inv -> {
         VSBookmarkInfo info = inv.getArgument(0);
         return VSBookmarkInfoModel.builder()
            .name(info.getName())
            .owner(info.getOwner())
            .type(info.getType())
            .label(info.getName())
            .build();
      });

      return new ScheduleTaskActionService(null, scheduleService, null, null, null, null, repository);
   }

   private static List<String> names(VSBookmarkInfo[] infos) {
      return Arrays.stream(infos).map(VSBookmarkInfo::getName).collect(Collectors.toList());
   }

   private static List<String> modelNames(List<VSBookmarkInfoModel> models) {
      return models.stream().map(VSBookmarkInfoModel::name).collect(Collectors.toList());
   }

   private static class StubAssetEngine extends AbstractAssetEngine {
      StubAssetEngine(IndexedStorage storage) {
         super((LibManagerProvider) null, (Cluster) null);
         istore = storage;
      }

      @Override
      protected boolean checkDataModelFolderPermission(String folder, String source, Principal user) {
         return false;
      }

      @Override
      protected boolean checkQueryFolderPermission(String folder, String source, Principal user) {
         return false;
      }

      @Override
      protected boolean checkQueryPermission(String query, Principal user) {
         return false;
      }

      @Override
      protected boolean checkDataSourcePermission(String dname, Principal user) {
         return false;
      }

      @Override
      protected boolean checkDataSourceFolderPermission(String folder, Principal user) {
         return false;
      }
   }

   private static final class DenyingStubAssetEngine extends StubAssetEngine {
      DenyingStubAssetEngine(IndexedStorage storage) {
         super(storage);
      }

      @Override
      public boolean checkPermission(Principal principal, ResourceType type, String resource,
                                     EnumSet<ResourceAction> action)
      {
         return false;
      }
   }
}
