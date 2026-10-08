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
package inetsoft.web.composer.vs.dialog;

/*
 * Bug #77064: GET /api/composer/vs/hyperlink-dialog-model/bookmarks/** builds an AssetEntry
 * from a client-supplied viewsheet identifier (whose 5th component is kept as the entry's
 * orgID) and VSUtil.getBookmarks then lists that org's shared bookmark names and owners. The
 * endpoint now checks READ on the entry with AssetRepository.checkAssetPermission first and
 * returns an empty list when the caller may not read the viewsheet.
 *
 * The repository is a real AbstractAssetEngine subclass (StubAssetEngine, as in
 * ScheduleActionCrossOrgViewsheetTest), so the org check, the site-admin check and the
 * exposeDefaultOrgToAll bypass in checkAssetPermission0 run unmodified. What is mocked:
 * - the per-resource ACL (AbstractAssetEngine.checkPermission): GrantingStubAssetEngine always
 *   grants, DenyingStubAssetEngine always denies;
 * - VSUtil (static), so the bookmark lookup is observable and needs no bookmark storage;
 * - SUtil.isMultiTenant() -> true (structurally false on community/core's test classpath).
 *
 * Bug #78057: for another same-org user's private (USER_SCOPE) viewsheet the 3-arg
 * checkAssetPermission passed any caller with MY_DASHBOARDS READ, so the endpoint listed the
 * owner's shared bookmarks. It now uses checkAssetPermission(..., READ, true), the check that
 * opening the viewsheet uses. The granting stub grants MY_DASHBOARDS, so only that private-asset
 * check refuses the non-owner.
 */

import inetsoft.report.LibManagerProvider;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.VSBookmarkInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.ThreadContext;
import inetsoft.web.portal.controller.RepositoryTreeService;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class HyperlinkDialogControllerBookmarksTest {
   private static final String ORG_A = "orga_id";
   private static final String ORG_B = "orgb_id";
   private static final String HOST_ORG = Organization.getDefaultOrganizationID();
   private static final String ORG_A_VS = "1^128^__NULL__^Finance/Report^" + ORG_A;
   private static final String ORG_B_VS = "1^128^__NULL__^Finance/Secret^" + ORG_B;
   private static final String HOST_ORG_VS = "1^128^__NULL__^Shared/Dashboard^" + HOST_ORG;
   private static final String PRIVATE_VS =
      "4^128^bug78057Owner~;~" + HOST_ORG + "^Private/Dash^" + HOST_ORG;

   private static SecurityTestDataBuilder builder;
   private static SRPrincipal orgAUser;
   private static SRPrincipal siteAdmin;
   private static SRPrincipal privateOwner;
   private static SRPrincipal hostOrgUser;

   @BeforeAll
   static void setUpAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("orga", ORG_A)
         .addOrg("orgb", ORG_B)
         .addUser("orgAUser", ORG_A, "password")
         .addSysAdminRole("Bug77064SiteAdminRole", HOST_ORG)
         .addUser("bug77064SiteAdmin", HOST_ORG, "password")
         .addUserToRole("bug77064SiteAdmin", "Bug77064SiteAdminRole", HOST_ORG)
         .addUser("bug78057Owner", HOST_ORG, "password")
         .addUser("bug78057User", HOST_ORG, "password")
         .setup();
      orgAUser = builder.principalOf("orgAUser", ORG_A);
      siteAdmin = builder.principalOf("bug77064SiteAdmin", HOST_ORG);
      privateOwner = builder.principalOf("bug78057Owner", HOST_ORG);
      hostOrgUser = builder.principalOf("bug78057User", HOST_ORG);
   }

   @AfterAll
   static void tearDownAll() {
      if(builder != null) {
         builder.teardown();
      }
   }

   @BeforeEach
   void setUp() {
      ThreadContext.setContextPrincipal(null);
   }

   @Test
   void otherOrgViewsheet_nonSiteAdmin_returnsEmptyAndSkipsBookmarkLookup() throws Exception {
      List<String> result = withMocks(false, vsUtil -> {
         List<String> names = controller(new GrantingStubAssetEngine()).getBookmarks(ORG_B_VS, orgAUser);
         vsUtil.verify(() -> VSUtil.getBookmarks(any(AssetEntry.class), any()), never());
         vsUtil.verify(() -> VSUtil.getBookmarks(anyString(), any()), never());
         return names;
      });

      assertEquals(Collections.emptyList(), result);
   }

   @Test
   void sameOrgViewsheet_userWithRead_listsBookmarks() throws Exception {
      List<String> result = withMocks(false, vsUtil ->
         controller(new GrantingStubAssetEngine()).getBookmarks(ORG_A_VS, orgAUser));

      assertEquals(List.of("shared(owner)"), result);
   }

   @Test
   void sameOrgViewsheet_userWithoutRead_returnsEmptyAndSkipsBookmarkLookup() throws Exception {
      List<String> result = withMocks(false, vsUtil -> {
         List<String> names = controller(new DenyingStubAssetEngine()).getBookmarks(ORG_A_VS, orgAUser);
         vsUtil.verify(() -> VSUtil.getBookmarks(any(AssetEntry.class), any()), never());
         return names;
      });

      assertEquals(Collections.emptyList(), result);
   }

   @Test
   void otherOrgViewsheet_siteAdmin_listsBookmarksOfThatOrg() throws Exception {
      List<String> result = withMocks(false, vsUtil -> {
         List<String> names = controller(new GrantingStubAssetEngine()).getBookmarks(ORG_B_VS, siteAdmin);
         vsUtil.verify(() -> VSUtil.getBookmarks(
            argThat((AssetEntry e) -> e != null && ORG_B.equals(e.getOrgID())), any()));
         return names;
      });

      assertEquals(List.of("shared(owner)"), result);
   }

   @Test
   void hostOrgViewsheet_exposeDefaultOrgToAll_listsBookmarksForOtherOrgUser() throws Exception {
      // the denying ACL shows the grant comes from the exposeDefaultOrgToAll bypass
      List<String> result = withMocks(true, vsUtil ->
         controller(new DenyingStubAssetEngine()).getBookmarks(HOST_ORG_VS, orgAUser));

      assertEquals(List.of("shared(owner)"), result);
   }

   @Test
   void hostOrgViewsheet_notExposed_returnsEmptyForOtherOrgUser() throws Exception {
      List<String> result = withMocks(false, vsUtil ->
         controller(new GrantingStubAssetEngine()).getBookmarks(HOST_ORG_VS, orgAUser));

      assertEquals(Collections.emptyList(), result);
   }

   @Test
   void otherUsersPrivateViewsheet_sameOrgUser_returnsEmptyAndSkipsBookmarkLookup()
      throws Exception
   {
      List<String> result = withMocks(false, vsUtil -> {
         List<String> names =
            controller(new GrantingStubAssetEngine()).getBookmarks(PRIVATE_VS, hostOrgUser);
         vsUtil.verify(() -> VSUtil.getBookmarks(any(AssetEntry.class), any()), never());
         return names;
      });

      assertEquals(Collections.emptyList(), result);
   }

   @Test
   void privateViewsheet_owner_listsBookmarks() throws Exception {
      List<String> result = withMocks(false, vsUtil ->
         controller(new GrantingStubAssetEngine()).getBookmarks(PRIVATE_VS, privateOwner));

      assertEquals(List.of("shared(owner)"), result);
   }

   @Test
   void otherUsersPrivateViewsheet_siteAdmin_listsBookmarks() throws Exception {
      List<String> result = withMocks(false, vsUtil ->
         controller(new GrantingStubAssetEngine()).getBookmarks(PRIVATE_VS, siteAdmin));

      assertEquals(List.of("shared(owner)"), result);
   }

   @Test
   void malformedIdentifier_returnsEmptyWithoutPermissionCheck() throws Exception {
      AssetRepository repository = mock(AssetRepository.class);

      List<String> result = withMocks(false, vsUtil -> {
         List<String> names = controller(repository).getBookmarks("not-an-identifier", orgAUser);
         vsUtil.verify(() -> VSUtil.getBookmarks(any(AssetEntry.class), any()), never());
         return names;
      });

      assertEquals(Collections.emptyList(), result);
      verify(repository, never()).checkAssetPermission(any(), any(), any(), anyBoolean());
   }

   private static HyperlinkDialogController controller(AssetRepository repository) {
      return new HyperlinkDialogController(
         mock(RuntimeViewsheetRef.class), repository, mock(RepositoryTreeService.class),
         mock(HyperlinkDialogServiceProxy.class));
   }

   private static List<String> withMocks(boolean exposeDefaultOrg, ControllerCall call)
      throws Exception
   {
      IdentityID owner = new IdentityID("owner", ORG_B);
      VSBookmarkInfo shared = new VSBookmarkInfo("shared", VSBookmarkInfo.ALLSHARE, owner, false, 0L);

      if(exposeDefaultOrg) {
         SreeEnv.setProperty("security.exposeDefaultOrgToAll", "true");
         SreeEnv.save();
      }

      try(MockedStatic<SUtil> sutil = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
          MockedStatic<VSUtil> vsUtil = Mockito.mockStatic(VSUtil.class))
      {
         sutil.when(SUtil::isMultiTenant).thenReturn(true);
         vsUtil.when(() -> VSUtil.getBookmarks(any(AssetEntry.class), any()))
            .thenReturn(new VSBookmarkInfo[] { shared });
         vsUtil.when(() -> VSUtil.getBookmarks(anyString(), any()))
            .thenReturn(new VSBookmarkInfo[] { shared });
         vsUtil.when(() -> VSUtil.getUserAlias(any())).thenReturn("owner");
         return call.run(vsUtil);
      }
      finally {
         if(exposeDefaultOrg) {
            SreeEnv.remove("security.exposeDefaultOrgToAll");
            SreeEnv.save();
         }
      }
   }

   @FunctionalInterface
   private interface ControllerCall {
      List<String> run(MockedStatic<VSUtil> vsUtil) throws Exception;
   }

   private static class GrantingStubAssetEngine extends AbstractAssetEngine {
      GrantingStubAssetEngine() {
         super((LibManagerProvider) null, (Cluster) null);
      }

      @Override
      public boolean checkPermission(Principal principal, ResourceType type, String resource,
                                     EnumSet<ResourceAction> action)
      {
         return true;
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

   private static final class DenyingStubAssetEngine extends GrantingStubAssetEngine {
      @Override
      public boolean checkPermission(Principal principal, ResourceType type, String resource,
                                     EnumSet<ResourceAction> action)
      {
         return false;
      }
   }
}
