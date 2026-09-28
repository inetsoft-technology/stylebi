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
 * Bug #77234: editing an organization must decide "did the org id change" and "which org are the
 * members moved from" from the edited org's old id, never from the caller's current org.
 *
 * updateOrganizationMembers() used to compare OrganizationManager.getCurrentOrgID() with the
 * edited org's new id. A site admin editing another org (public REST PUT, or EM without switching
 * the current org) and any org whose id has uppercase letters (getCurrentOrgID() is lowercased)
 * turned a plain edit into a pseudo-rename, whose setUser(newID) + removeUser(oldID) with
 * newID == oldID deleted every listed user, and whose group/role branches deleted every listed
 * group and role. A rename started from another current org passed that other org as the source
 * org to the dashboard registry migration and the permission re-scoping.
 *
 * These tests drive updateOrganizationMembers() directly, not through setOrganizationInfo(), so
 * they do not depend on setOrganizationInfo()'s "members changed" guard.
 */

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.favorites.FavoritesService;
import inetsoft.web.admin.security.IdentityModel;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.admin.security.user.EditOrganizationPaneModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class OrgEditFromOtherCurrentOrgTest {
   private static final String HOST_ORG_ID = "oefc_host";
   private static final String HOST_ORG_NAME = "OefcHost";
   private static final String EDITED_ORG_ID = "oefc_edited";
   private static final String EDITED_ORG_NAME = "OefcEdited";
   private static final String MIXED_ORG_ID = "OefcMixed";
   private static final String MIXED_ORG_NAME = "OefcMixedName";
   private static final String RENAMED_ORG_ID = "oefc_renamed";
   private static final String RENAMED_ORG_NAME = "OefcRenamed";
   private static final String RESOURCE = "/oefc/vs1";
   private static final String CASE_ORG_ID = "OefcCase";
   private static final String CASE_ORG_NAME = "OefcCaseName";
   private static final String CASE_RENAMED_ORG_ID = "OEFCCASE";
   private static final String SITE_ADMIN = "oefcSiteAdmin";

   private SecurityTestDataBuilder builder;
   private FileAuthenticationProvider fileProvider;
   private DashboardRegistryManager dashboardRegistryManager;
   private RepletRegistryManager repletRegistryManager;

   @BeforeEach
   void setUp() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg(HOST_ORG_NAME, HOST_ORG_ID)
         .addOrg(EDITED_ORG_NAME, EDITED_ORG_ID)
         .addOrg(MIXED_ORG_NAME, MIXED_ORG_ID)
         .addOrg(RENAMED_ORG_NAME, RENAMED_ORG_ID)
         .addUser("u1", EDITED_ORG_ID, "password")
         .addUser("u2", EDITED_ORG_ID, "password")
         .addGroup("g1", EDITED_ORG_ID)
         .addRole("r1", EDITED_ORG_ID)
         .addUserToRole("u1", "r1", EDITED_ORG_ID)
         .addUser("u1", MIXED_ORG_ID, "password")
         .addGroup("g1", MIXED_ORG_ID)
         .addRole("r1", MIXED_ORG_ID)
         .addOrg(CASE_ORG_NAME, CASE_ORG_ID)
         .addUser("u1", CASE_ORG_ID, "password")
         .addGroup("g1", CASE_ORG_ID)
         .addRole("r1", CASE_ORG_ID)
         .addSysAdminRole("oefcSiteAdmins", HOST_ORG_ID)
         .addUser(SITE_ADMIN, HOST_ORG_ID, "password")
         .addUserToRole(SITE_ADMIN, "oefcSiteAdmins", HOST_ORG_ID)
         .grantPermission(ResourceType.VIEWSHEET, RESOURCE, ResourceAction.READ,
                          "u1", Identity.USER, EDITED_ORG_ID)
         .setup();

      fileProvider = (FileAuthenticationProvider)
         ((AuthenticationChain) SecurityEngine.getSecurity().getSecurityProvider()
            .getAuthenticationProvider()).getProviders().get(0);
      dashboardRegistryManager = mock(DashboardRegistryManager.class);
      repletRegistryManager = mock(RepletRegistryManager.class);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      OrganizationContextHolder.setCurrentOrgId(null);

      // identities and grants a rename moved to the renamed org are not tracked by the builder
      if(fileProvider != null) {
         fileProvider.removeUser(new IdentityID("u1", RENAMED_ORG_ID));
         fileProvider.removeGroup(new IdentityID("g1", RENAMED_ORG_ID));
         fileProvider.removeRole(new IdentityID("r1", RENAMED_ORG_ID));
         fileProvider.removeUser(new IdentityID("u1", CASE_RENAMED_ORG_ID));
         fileProvider.removeGroup(new IdentityID("g1", CASE_RENAMED_ORG_ID));
         fileProvider.removeRole(new IdentityID("r1", CASE_RENAMED_ORG_ID));
      }

      SecurityEngine.getSecurity().getAuthorizationChain().ifPresent(
         chain -> chain.removePermission(ResourceType.VIEWSHEET, RESOURCE, RENAMED_ORG_ID));

      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   // (a) site admin whose current org is not the edited org, no rename
   @Test
   void nonRename_currentOrgIsOtherOrg_listedMembersKept() {
      setCurrentOrg(HOST_ORG_ID);

      updateOrganizationMembers(org(EDITED_ORG_ID, EDITED_ORG_NAME, "u1", "u2", "g1", "r1"),
                                EDITED_ORG_ID);

      assertMembersExist(EDITED_ORG_ID, "u1", "u2");
      assertNotNull(fileProvider.getGroup(new IdentityID("g1", EDITED_ORG_ID)),
                    "a listed group must not be deleted by a non-rename edit");
      assertNotNull(fileProvider.getRole(new IdentityID("r1", EDITED_ORG_ID)),
                    "a listed role must not be deleted by a non-rename edit");
      verifyNoInteractions(dashboardRegistryManager);
   }

   // (b) org id with uppercase letters, current org == edited org (getCurrentOrgID() lowercases)
   @Test
   void nonRename_mixedCaseOrgIdIsCurrentOrg_listedMembersKept() {
      setCurrentOrg(MIXED_ORG_ID);

      updateOrganizationMembers(org(MIXED_ORG_ID, MIXED_ORG_NAME, "u1", "g1", "r1"),
                                MIXED_ORG_ID);

      assertMembersExist(MIXED_ORG_ID, "u1");
      assertNotNull(fileProvider.getGroup(new IdentityID("g1", MIXED_ORG_ID)),
                    "a listed group of a mixed-case org must not be deleted by a non-rename edit");
      assertNotNull(fileProvider.getRole(new IdentityID("r1", MIXED_ORG_ID)),
                    "a listed role of a mixed-case org must not be deleted by a non-rename edit");
   }

   // (c) rename A -> B started while the caller's current org is a third org
   @Test
   void rename_currentOrgIsOtherOrg_membersMovedFromEditedOrg() {
      setCurrentOrg(HOST_ORG_ID);
      IdentityID oldUser = new IdentityID("u1", EDITED_ORG_ID);

      updateOrganizationMembers(org(RENAMED_ORG_ID, RENAMED_ORG_NAME, "u1", "g1", "r1"),
                                EDITED_ORG_ID);

      User moved = fileProvider.getUser(new IdentityID("u1", RENAMED_ORG_ID));
      assertNotNull(moved, "a listed user must be moved to the renamed org");
      assertNull(fileProvider.getUser(oldUser), "the user must be removed from the old org");
      assertTrue(Arrays.asList(moved.getRoles()).contains(new IdentityID("r1", RENAMED_ORG_ID)),
                 "the moved user's org role must be re-scoped to the renamed org");
      assertNotNull(fileProvider.getGroup(new IdentityID("g1", RENAMED_ORG_ID)),
                    "a listed group must be moved to the renamed org");
      assertNotNull(fileProvider.getRole(new IdentityID("r1", RENAMED_ORG_ID)),
                    "a listed role must be moved to the renamed org");
      assertNull(fileProvider.getUser(new IdentityID("u2", EDITED_ORG_ID)),
                 "an unlisted user of the old org must be removed");

      // the source org is the edited org, not the caller's current org
      verify(repletRegistryManager).changeOrgID(eq(oldUser), eq(EDITED_ORG_ID),
                                                eq(RENAMED_ORG_ID), eq(false));
      verify(dashboardRegistryManager).migrateRegistry(
         eq(oldUser), argThat(o -> o != null && EDITED_ORG_ID.equals(o.getId())), any());

      AuthorizationChain chain = SecurityEngine.getSecurity().getAuthorizationChain()
         .orElseThrow(() -> new AssertionError("expected an AuthorizationChain"));
      Permission after = chain.getPermission(ResourceType.VIEWSHEET, RESOURCE, RENAMED_ORG_ID);
      assertNotNull(after, "the user's own grant must be re-scoped from the edited org");
      assertTrue(after.getOrgScopedUserGrants(ResourceAction.READ, RENAMED_ORG_ID).stream()
                    .anyMatch(id -> id.name.equals("u1")),
                 "u1 must remain a READ grantee under the renamed org");
   }

   // (d) members changed: one user dropped from the list, current org is another org
   @Test
   void nonRename_memberRemovedFromList_onlyThatMemberRemoved() {
      setCurrentOrg(HOST_ORG_ID);

      updateOrganizationMembers(org(EDITED_ORG_ID, EDITED_ORG_NAME, "u1", "g1", "r1"),
                                EDITED_ORG_ID);

      assertMembersExist(EDITED_ORG_ID, "u1");
      assertNull(fileProvider.getUser(new IdentityID("u2", EDITED_ORG_ID)),
                 "the user dropped from the member list must be removed");
      assertNotNull(fileProvider.getGroup(new IdentityID("g1", EDITED_ORG_ID)),
                    "a listed group must be kept");
      assertNotNull(fileProvider.getRole(new IdentityID("r1", EDITED_ORG_ID)),
                    "a listed role must be kept");
   }

   // (e) the reporter's scenario through the public entry point setIdentity(): a site admin whose
   // current org is another org saves the edited org unchanged, listing its existing members
   @Test
   void setIdentity_siteAdminInOtherCurrentOrg_nonRenameEdit_listedMembersKept() throws Exception {
      SRPrincipal siteAdmin = builder.principalOf(SITE_ADMIN, HOST_ORG_ID);
      ThreadContext.setContextPrincipal(siteAdmin);
      FSOrganization oldOrg = (FSOrganization) fileProvider.getOrganization(EDITED_ORG_ID);
      EditOrganizationPaneModel model = EditOrganizationPaneModel.builder()
         .id(EDITED_ORG_ID)
         .name(EDITED_ORG_NAME)
         .oldName(EDITED_ORG_NAME)
         .members(List.of(
            member("u1", EDITED_ORG_ID, Identity.USER), member("u2", EDITED_ORG_ID, Identity.USER),
            member("g1", EDITED_ORG_ID, Identity.GROUP), member("r1", EDITED_ORG_ID, Identity.ROLE)))
         .status(true)
         .build();

      createIdentityService().setIdentity(oldOrg, model, fileProvider, siteAdmin);

      assertMembersExist(EDITED_ORG_ID, "u1", "u2");
      assertNotNull(fileProvider.getGroup(new IdentityID("g1", EDITED_ORG_ID)),
                    "a listed group must not be deleted by a non-rename edit");
      assertNotNull(fileProvider.getRole(new IdentityID("r1", EDITED_ORG_ID)),
                    "a listed role must not be deleted by a non-rename edit");
      assertNotNull(fileProvider.getOrganization(EDITED_ORG_ID), "the edited org must be kept");
   }

   // (f) a rename that only changes the case of the id is a real rename: the members are moved to
   // the new id, not left behind under the old one (an ignore-case compare would orphan them)
   @Test
   void rename_caseOnlyIdChange_membersMovedToNewId() {
      setCurrentOrg(CASE_ORG_ID);

      updateOrganizationMembers(org(CASE_RENAMED_ORG_ID, CASE_ORG_NAME, "u1", "g1", "r1"),
                                CASE_ORG_ID);

      assertNotNull(fileProvider.getUser(new IdentityID("u1", CASE_RENAMED_ORG_ID)),
                    "the user must be moved to the new-case org id");
      assertNull(fileProvider.getUser(new IdentityID("u1", CASE_ORG_ID)),
                 "the user must not be left under the old-case org id");
      assertNotNull(fileProvider.getGroup(new IdentityID("g1", CASE_RENAMED_ORG_ID)),
                    "the group must be moved to the new-case org id");
      assertNull(fileProvider.getGroup(new IdentityID("g1", CASE_ORG_ID)),
                 "the group must not be left under the old-case org id");
      assertNotNull(fileProvider.getRole(new IdentityID("r1", CASE_RENAMED_ORG_ID)),
                    "the role must be moved to the new-case org id");
      assertNull(fileProvider.getRole(new IdentityID("r1", CASE_ORG_ID)),
                 "the role must not be left under the old-case org id");
   }

   private static IdentityModel member(String name, String orgId, int type) {
      return IdentityModel.builder().identityID(new IdentityID(name, orgId)).type(type).build();
   }

   private void updateOrganizationMembers(FSOrganization org, String oldOrgID) {
      // memberModels only drives creation of brand-new members; all members here already exist
      ReflectionTestUtils.invokeMethod(createIdentityService(), "updateOrganizationMembers",
         org, new ArrayList<IdentityModel>(), oldOrgID, fileProvider);
   }

   private void assertMembersExist(String orgId, String... users) {
      for(String user : users) {
         assertNotNull(fileProvider.getUser(new IdentityID(user, orgId)),
                       "listed user " + user + " must not be deleted by a non-rename edit");
      }
   }

   private static FSOrganization org(String id, String name, String... members) {
      FSOrganization org = new FSOrganization(id);
      org.setName(name);
      org.setMembers(members);
      return org;
   }

   private static void setCurrentOrg(String orgId) {
      ThreadContext.setContextPrincipal(new SRPrincipal(new IdentityID("tester", orgId),
         new IdentityID[0], new String[0], orgId, 1L));
   }

   private IdentityService createIdentityService() {
      return new IdentityService(
         SecurityEngine.getSecurity(), SecurityEngine.getSecurity().getSecurityProvider(),
         null, null, null, mock(FavoritesService.class), mock(Cluster.class), null, null, null,
         null, mock(LicenseManager.class), null, null, Optional.empty(), null, null, null,
         dashboardRegistryManager, null, null, null, null, null, null, null, null,
         repletRegistryManager, Optional.empty());
   }
}
