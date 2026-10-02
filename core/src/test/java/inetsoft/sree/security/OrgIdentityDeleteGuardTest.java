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
 * Bug #77119: the identity delete guards of IdentityService.deleteIdentities() and the organization
 * member update must agree.
 *
 * (a) A group dropped from an organization's members is deleted, which strips it from its users. Like
 *     deleteIdentities(), the member update must keep a group that still has users, where a user
 *     remains if it is still a member or it is the requester (the requester is never deleted). Users
 *     dropped in the same save are deleted first, so they don't count.
 * (c) A role held through one of the requester's groups (or their ancestor groups) is a role the
 *     requester holds, and the requester's own group (or an ancestor of it) is its own, so neither may
 *     be deleted by the requester. The requester's groups are those of its own organization, so a site
 *     admin of another organization with identically named groups and roles is not the requester.
 *
 * In this harness syncIdentity() fails on a missing dashboard manager after every guard has passed,
 * so deleteIdentities() reports "Failed to delete identity" instead of deleting. The assertions on
 * deleteIdentities() are therefore on its refusal warnings.
 */

import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.util.Identity;
import inetsoft.util.*;
import inetsoft.web.admin.favorites.FavoritesService;
import inetsoft.web.admin.security.IdentityModel;
import inetsoft.web.admin.security.IdentityService;
import inetsoft.web.admin.security.user.EditOrganizationPaneModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class OrgIdentityDeleteGuardTest {
   private static final String ORG_ID = "idguard_org";
   private static final String ORG_NAME = "IdGuardOrg";
   private static final String OTHER_ORG_ID = "idguard_other";
   private static final String OTHER_ORG_NAME = "IdGuardOther";

   private static final IdentityID ORG_ADMIN = new IdentityID("orgAdmin", ORG_ID);
   private static final IdentityID SITE_ADMIN = new IdentityID("siteAdmin", ORG_ID);
   private static final IdentityID BOB = new IdentityID("bob", ORG_ID);
   private static final IdentityID CAROL = new IdentityID("carol", ORG_ID);
   private static final IdentityID ORG_ADMIN_ROLE = new IdentityID("oaRole", ORG_ID);
   private static final IdentityID SITE_ADMIN_ROLE = new IdentityID("siteAdmins", ORG_ID);
   // sales: bob, salesRole
   private static final IdentityID SALES = new IdentityID("sales", ORG_ID);
   private static final IdentityID SALES_ROLE = new IdentityID("salesRole", ORG_ID);
   // viaGroup: orgAdmin, viaRole; child of parentGroup, which has no users and holds parentRole
   private static final IdentityID VIA_GROUP = new IdentityID("viaGroup", ORG_ID);
   private static final IdentityID VIA_ROLE = new IdentityID("viaRole", ORG_ID);
   private static final IdentityID PARENT_GROUP = new IdentityID("parentGroup", ORG_ID);
   private static final IdentityID PARENT_ROLE = new IdentityID("parentRole", ORG_ID);
   // teamB: carol, teamRole; not held by orgAdmin in any way
   private static final IdentityID TEAM_GROUP = new IdentityID("teamB", ORG_ID);
   private static final IdentityID TEAM_ROLE = new IdentityID("teamRole", ORG_ID);
   private static final IdentityID EMPTY_GROUP = new IdentityID("emptyGroup", ORG_ID);
   // a site admin of another organization, in groups named like viaGroup and parentGroup there
   private static final IdentityID OTHER_SITE_ADMIN = new IdentityID("otherSiteAdmin", OTHER_ORG_ID);

   private SecurityTestDataBuilder builder;
   private FileAuthenticationProvider fileProvider;
   private IdentityService identityService;

   @BeforeEach
   void setUp() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg(ORG_NAME, ORG_ID)
         .addOrgAdminRole(ORG_ADMIN_ROLE.name, ORG_ID)
         .addSysAdminRole(SITE_ADMIN_ROLE.name, ORG_ID)
         .addRole(SALES_ROLE.name, ORG_ID)
         .addRole(VIA_ROLE.name, ORG_ID)
         .addRole(PARENT_ROLE.name, ORG_ID)
         .addRole(TEAM_ROLE.name, ORG_ID)
         .addGroup(SALES.name, ORG_ID)
         .addGroup(VIA_GROUP.name, ORG_ID)
         .addGroup(PARENT_GROUP.name, ORG_ID)
         .addGroup(TEAM_GROUP.name, ORG_ID)
         .addGroup(EMPTY_GROUP.name, ORG_ID)
         .addRoleToGroup(SALES_ROLE.name, SALES.name, ORG_ID)
         .addRoleToGroup(VIA_ROLE.name, VIA_GROUP.name, ORG_ID)
         .addRoleToGroup(PARENT_ROLE.name, PARENT_GROUP.name, ORG_ID)
         .addRoleToGroup(TEAM_ROLE.name, TEAM_GROUP.name, ORG_ID)
         .addGroupParent(VIA_GROUP.name, PARENT_GROUP.name, ORG_ID)
         .addUser(ORG_ADMIN.name, ORG_ID, "password")
         .addUser(SITE_ADMIN.name, ORG_ID, "password")
         .addUser(BOB.name, ORG_ID, "password")
         .addUser(CAROL.name, ORG_ID, "password")
         .addUserToRole(ORG_ADMIN.name, ORG_ADMIN_ROLE.name, ORG_ID)
         .addUserToRole(SITE_ADMIN.name, SITE_ADMIN_ROLE.name, ORG_ID)
         .addUserToGroup(ORG_ADMIN.name, VIA_GROUP.name, ORG_ID)
         .addUserToGroup(BOB.name, SALES.name, ORG_ID)
         .addUserToGroup(CAROL.name, TEAM_GROUP.name, ORG_ID)
         .addOrg(OTHER_ORG_NAME, OTHER_ORG_ID)
         .addSysAdminRole(SITE_ADMIN_ROLE.name, OTHER_ORG_ID)
         .addRole(VIA_ROLE.name, OTHER_ORG_ID)
         .addRole(PARENT_ROLE.name, OTHER_ORG_ID)
         .addGroup(VIA_GROUP.name, OTHER_ORG_ID)
         .addGroup(PARENT_GROUP.name, OTHER_ORG_ID)
         .addRoleToGroup(VIA_ROLE.name, VIA_GROUP.name, OTHER_ORG_ID)
         .addRoleToGroup(PARENT_ROLE.name, PARENT_GROUP.name, OTHER_ORG_ID)
         .addGroupParent(VIA_GROUP.name, PARENT_GROUP.name, OTHER_ORG_ID)
         .addUser(OTHER_SITE_ADMIN.name, OTHER_ORG_ID, "password")
         .addUserToRole(OTHER_SITE_ADMIN.name, SITE_ADMIN_ROLE.name, OTHER_ORG_ID)
         .addUserToGroup(OTHER_SITE_ADMIN.name, VIA_GROUP.name, OTHER_ORG_ID)
         .setup();

      fileProvider = (FileAuthenticationProvider)
         ((AuthenticationChain) SecurityEngine.getSecurity().getSecurityProvider()
            .getAuthenticationProvider()).getProviders().get(0);
      identityService = createIdentityService();
      Tool.clearUserMessage();
   }

   @AfterEach
   void tearDown() {
      Tool.clearUserMessage();
      ThreadContext.setContextPrincipal(null);
      OrganizationContextHolder.setCurrentOrgId(null);

      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   // (a) group with remaining users

   @Test
   void orgAdminDropsGroupWithRemainingUser_keptWithMessage() throws Exception {
      SRPrincipal orgAdmin = loginAs(ORG_ADMIN, ORG_ID);

      setOrganizationInfo(model(allOrgMembersExcept(SALES)), orgAdmin);

      assertNotNull(fileProvider.getGroup(SALES), "a group whose user stays must be kept");
      assertTrue(Arrays.asList(fileProvider.getUser(BOB).getGroups()).contains(SALES.name),
                 "the remaining user must stay in the group");
      assertTrue(userMessage().contains(catalog("em.security.delgroup")),
                 "the refused drop must be reported, but was: " + userMessage());
   }

   @Test
   void siteAdminDropsGroupWithRemainingUser_keptWithMessage() throws Exception {
      SRPrincipal siteAdmin = loginAs(SITE_ADMIN, ORG_ID);
      assertTrue(OrganizationManager.getInstance().isSiteAdmin(siteAdmin),
                 "precondition: siteAdmin must be a site admin");

      setOrganizationInfo(model(allOrgMembersExcept(SALES)), siteAdmin);

      assertNotNull(fileProvider.getGroup(SALES), "a group whose user stays must be kept");
      assertTrue(userMessage().contains(catalog("em.security.delgroup")),
                 "the refused drop must be reported, but was: " + userMessage());
   }

   @Test
   void dropsGroupTogetherWithAllItsUsers_deleted() throws Exception {
      SRPrincipal orgAdmin = loginAs(ORG_ADMIN, ORG_ID);

      setOrganizationInfo(model(allOrgMembersExcept(SALES, BOB)), orgAdmin);

      assertNull(fileProvider.getUser(BOB), "the dropped user must be deleted");
      assertNull(fileProvider.getGroup(SALES),
                 "a group dropped together with all its users must be deleted");
      assertFalse(userMessage().contains(catalog("em.security.delgroup")),
                  "nothing may be reported, but was: " + userMessage());
   }

   @Test
   void requesterDropsOwnNameAndOwnGroup_groupKept() throws Exception {
      SRPrincipal orgAdmin = loginAs(ORG_ADMIN, ORG_ID);

      setOrganizationInfo(model(allOrgMembersExcept(ORG_ADMIN, VIA_GROUP)), orgAdmin);

      assertNotNull(fileProvider.getUser(ORG_ADMIN), "the requester is never deleted");
      assertNotNull(fileProvider.getGroup(VIA_GROUP), "the requester's group must be kept");
      assertTrue(Arrays.asList(fileProvider.getUser(ORG_ADMIN).getGroups()).contains(VIA_GROUP.name),
                 "the requester must stay in its group");
   }

   // (c) roles and groups held through the requester's groups

   @Test
   void deleteIdentities_roleHeldThroughGroup_refused() {
      List<String> warnings = deleteIdentities(loginAs(ORG_ADMIN, ORG_ID),
                                               member(VIA_ROLE, Identity.ROLE));

      assertEquals(List.of(catalog("em.security.delself")), warnings,
                   "a role held through the requester's group must be refused");
   }

   @Test
   void deleteIdentities_ancestorGroupAndItsRole_refused() {
      List<String> warnings = deleteIdentities(loginAs(ORG_ADMIN, ORG_ID),
                                               member(PARENT_ROLE, Identity.ROLE),
                                               member(PARENT_GROUP, Identity.GROUP));

      assertEquals(List.of(catalog("em.security.delself"), catalog("em.security.delself")),
                   warnings,
                   "the role of an ancestor group and the ancestor group itself must be refused");
   }

   @Test
   void orgPaneDropsRoleHeldThroughGroup_keptWithMessage() throws Exception {
      SRPrincipal orgAdmin = loginAs(ORG_ADMIN, ORG_ID);

      setOrganizationInfo(model(allOrgMembersExcept(VIA_ROLE)), orgAdmin);

      assertNotNull(fileProvider.getRole(VIA_ROLE), "a role held through a group must be kept");
      assertTrue(userMessage().contains(catalog("em.security.delself")),
                 "the refused drop must be reported, but was: " + userMessage());
   }

   @Test
   void orgPaneDropsOwnGroup_keptWithMessage() throws Exception {
      SRPrincipal orgAdmin = loginAs(ORG_ADMIN, ORG_ID);

      setOrganizationInfo(model(allOrgMembersExcept(VIA_GROUP)), orgAdmin);

      assertNotNull(fileProvider.getGroup(VIA_GROUP), "the requester's group must be kept");
      assertTrue(Arrays.asList(fileProvider.getUser(ORG_ADMIN).getGroups()).contains(VIA_GROUP.name),
                 "the requester must stay in its group");
      assertTrue(userMessage().contains(catalog("em.security.delself")),
                 "the refused drop must be reported, but was: " + userMessage());
   }

   @Test
   void orgPaneDropsAncestorGroupAndItsRole_keptWithMessage() throws Exception {
      SRPrincipal orgAdmin = loginAs(ORG_ADMIN, ORG_ID);

      setOrganizationInfo(model(allOrgMembersExcept(PARENT_GROUP, PARENT_ROLE)), orgAdmin);

      assertNotNull(fileProvider.getGroup(PARENT_GROUP), "the requester's ancestor group must be kept");
      assertNotNull(fileProvider.getRole(PARENT_ROLE), "the ancestor group's role must be kept");
      assertTrue(userMessage().contains(catalog("em.security.delself")),
                 "the refused drop must be reported, but was: " + userMessage());
   }

   @Test
   void orgPaneDropsGroupAndRoleNotHeld_deleted() throws Exception {
      SRPrincipal orgAdmin = loginAs(ORG_ADMIN, ORG_ID);

      setOrganizationInfo(model(allOrgMembersExcept(TEAM_ROLE, EMPTY_GROUP)), orgAdmin);

      assertNull(fileProvider.getRole(TEAM_ROLE),
                 "a role held only by another user's group must be deleted");
      assertNull(fileProvider.getGroup(EMPTY_GROUP), "a group without users must be deleted");
      String message = userMessage();
      assertFalse(message.contains(catalog("em.security.delself")) ||
                  message.contains(catalog("em.security.delgroup")),
                  "nothing may be refused, but was: " + message);
   }

   @Test
   void otherOrgSiteAdminWithSameNamedGroups_notTreatedAsSelf() throws Exception {
      SRPrincipal otherSiteAdmin = loginAs(OTHER_SITE_ADMIN, OTHER_ORG_ID);
      assertTrue(OrganizationManager.getInstance().isSiteAdmin(otherSiteAdmin),
                 "precondition: otherSiteAdmin must be a site admin");
      // the site admin has switched into the edited organization
      OrganizationContextHolder.setCurrentOrgId(ORG_ID);

      List<String> warnings = deleteIdentities(otherSiteAdmin, member(VIA_ROLE, Identity.ROLE),
                                               member(PARENT_GROUP, Identity.GROUP));

      assertFalse(warnings.contains(catalog("em.security.delself")),
                  "another organization's same-named groups and roles are not the requester's, " +
                  "but was: " + warnings);

      setOrganizationInfo(model(allOrgMembersExcept(VIA_ROLE, PARENT_GROUP)), otherSiteAdmin);

      assertNull(fileProvider.getRole(VIA_ROLE), "the organization's role must be deleted");
      assertNull(fileProvider.getGroup(PARENT_GROUP), "the organization's group must be deleted");
      assertFalse(userMessage().contains(catalog("em.security.delself")),
                  "nothing may be refused as self, but was: " + userMessage());
   }

   private SRPrincipal loginAs(IdentityID user, String orgID) {
      SRPrincipal principal = builder.principalOf(user.name, orgID);
      ThreadContext.setContextPrincipal(principal);
      return principal;
   }

   private List<String> deleteIdentities(Principal principal, IdentityModel... models) {
      return identityService.deleteIdentities(models, fileProvider.getProviderName(), principal);
   }

   private List<IdentityModel> allOrgMembers() {
      return Stream.of(
            Arrays.stream(fileProvider.getUsers()).map(id -> member(id, Identity.USER)),
            Arrays.stream(fileProvider.getGroups()).map(id -> member(id, Identity.GROUP)),
            Arrays.stream(fileProvider.getRoles()).map(id -> member(id, Identity.ROLE)))
         .flatMap(s -> s)
         .filter(m -> ORG_ID.equals(m.identityID().orgID))
         .toList();
   }

   private List<IdentityModel> allOrgMembersExcept(IdentityID... dropped) {
      List<IdentityID> drop = Arrays.asList(dropped);
      return allOrgMembers().stream().filter(m -> !drop.contains(m.identityID())).toList();
   }

   private static String catalog(String key) {
      return Catalog.getCatalog().getString(key);
   }

   private static String userMessage() {
      UserMessage message = Tool.getUserMessage();
      String text = message == null ? "" : message.getMessage();

      if(message != null) {
         Tool.addUserMessage(message);
      }

      return text;
   }

   // the stored organization is used as is: its member list is empty, as in a real deployment
   private void setOrganizationInfo(EditOrganizationPaneModel model, Principal principal)
      throws Exception
   {
      FSOrganization oldOrg = (FSOrganization) fileProvider.getOrganization(ORG_ID);
      Method setOrganizationInfo = IdentityService.class.getDeclaredMethod(
         "setOrganizationInfo", FSOrganization.class, EditOrganizationPaneModel.class,
         EditableAuthenticationProvider.class, Principal.class);
      setOrganizationInfo.setAccessible(true);
      setOrganizationInfo.invoke(identityService, oldOrg, model, fileProvider, principal);
   }

   private static EditOrganizationPaneModel model(List<IdentityModel> members) {
      return EditOrganizationPaneModel.builder()
         .id(ORG_ID)
         .name(ORG_NAME)
         .oldName(ORG_NAME)
         .members(members)
         .status(true)
         .build();
   }

   private static IdentityModel member(IdentityID id, int type) {
      return IdentityModel.builder().identityID(id).type(type).build();
   }

   private static IdentityService createIdentityService() {
      return new IdentityService(
         SecurityEngine.getSecurity(), SecurityEngine.getSecurity().getSecurityProvider(),
         null, null, null, mock(FavoritesService.class), mock(Cluster.class), null, null, null,
         null, null, mock(ScheduleManager.class), mock(IndexedStorage.class), Optional.empty(),
         null, null, null, mock(DashboardRegistryManager.class), null, null, null, null, null,
         null, null, null, mock(RepletRegistryManager.class), Optional.empty());
   }
}
