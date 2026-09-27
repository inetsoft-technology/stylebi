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
 * Bug #77094: a group or role dropped from an organization's members is deleted, so the drop must
 * pass the same per-target checks as IdentityService.deleteIdentities(): admin permission on the
 * target, no system administrator target for a caller that is not a site admin, and not a role the
 * caller holds itself. A refused drop keeps the group or role.
 *
 * The REST organization update strips every group/role the caller has no admin permission on from
 * the submitted members, so an org admin's unchanged GET -> PUT round trip used to delete the org's
 * system administrator groups and roles. The stored member list of an organization is not kept up to
 * date (it is empty here, as for most real organizations), so the kept set must come from the
 * provider.
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
class OrgGroupRoleDropAuthorizationTest {
   private static final String ORG_ID = "grdrop_org";
   private static final String ORG_NAME = "GrDropOrg";
   private static final IdentityID ORG_ADMIN = new IdentityID("orgAdmin", ORG_ID);
   private static final IdentityID SITE_ADMIN = new IdentityID("siteAdmin", ORG_ID);
   private static final IdentityID PLAIN_USER = new IdentityID("plainUser", ORG_ID);
   private static final IdentityID ORG_ADMIN_ROLE = new IdentityID("oaRole", ORG_ID);
   private static final IdentityID SITE_ADMIN_ROLE = new IdentityID("siteAdmins", ORG_ID);
   private static final IdentityID SYS_ROLE = new IdentityID("orgsys", ORG_ID);
   private static final IdentityID ANALYST_ROLE = new IdentityID("analyst", ORG_ID);
   private static final IdentityID PLAIN_ROLE = new IdentityID("plainRole", ORG_ID);
   private static final IdentityID SYS_GROUP = new IdentityID("ops", ORG_ID);
   private static final IdentityID PLAIN_GROUP = new IdentityID("plainGroup", ORG_ID);

   private SecurityTestDataBuilder builder;
   private FileAuthenticationProvider fileProvider;
   private IdentityService identityService;

   @BeforeEach
   void setUp() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg(ORG_NAME, ORG_ID)
         .addOrgAdminRole(ORG_ADMIN_ROLE.name, ORG_ID)
         .addSysAdminRole(SITE_ADMIN_ROLE.name, ORG_ID)
         .addSysAdminRole(SYS_ROLE.name, ORG_ID)
         .addRole(ANALYST_ROLE.name, ORG_ID)
         .addRole(PLAIN_ROLE.name, ORG_ID)
         .addGroup(SYS_GROUP.name, ORG_ID)
         .addRoleToGroup(SYS_ROLE.name, SYS_GROUP.name, ORG_ID)
         .addGroup(PLAIN_GROUP.name, ORG_ID)
         .addUser(ORG_ADMIN.name, ORG_ID, "password")
         .addUser(SITE_ADMIN.name, ORG_ID, "password")
         .addUser(PLAIN_USER.name, ORG_ID, "password")
         .addUserToRole(ORG_ADMIN.name, ORG_ADMIN_ROLE.name, ORG_ID)
         .addUserToRole(ORG_ADMIN.name, ANALYST_ROLE.name, ORG_ID)
         .addUserToRole(SITE_ADMIN.name, SITE_ADMIN_ROLE.name, ORG_ID)
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

   @Test
   void orgAdminRestRoundTrip_sysAdminGroupAndRoleFilteredOut_kept() throws Exception {
      SRPrincipal orgAdmin = loginAs(ORG_ADMIN);
      // the REST update submits the GET's members minus those the caller has no admin permission
      // on (SecurityApiService.filterPermittedIds)
      List<IdentityModel> submitted = allOrgMembers().stream()
         .filter(m -> hasAdmin(orgAdmin, m))
         .toList();
      assertFalse(containsName(submitted, SYS_GROUP) || containsName(submitted, SYS_ROLE),
                  "precondition: the REST filter strips the system administrator group and role");
      assertTrue(containsName(submitted, PLAIN_GROUP) && containsName(submitted, PLAIN_ROLE),
                 "precondition: the REST filter keeps the ordinary group and role");

      setOrganizationInfo(model(submitted), orgAdmin);

      assertNotNull(fileProvider.getGroup(SYS_GROUP), "the system administrator group must be kept");
      assertNotNull(fileProvider.getRole(SYS_ROLE), "the system administrator role must be kept");
      assertNotNull(fileProvider.getGroup(PLAIN_GROUP), "the submitted group must be kept");
      assertNotNull(fileProvider.getRole(PLAIN_ROLE), "the submitted role must be kept");
   }

   @Test
   void orgAdminExplicitDrop_sysAdminGroupAndRole_keptWithMessage() throws Exception {
      SRPrincipal orgAdmin = loginAs(ORG_ADMIN);

      setOrganizationInfo(model(allOrgMembersExcept(SYS_GROUP, SYS_ROLE)), orgAdmin);

      assertNotNull(fileProvider.getGroup(SYS_GROUP), "the system administrator group must be kept");
      assertNotNull(fileProvider.getRole(SYS_ROLE), "the system administrator role must be kept");
      assertTrue(userMessage().contains(Catalog.getCatalog().getString(
                    "em.security.orgAdmin.identityPermissionDenied")),
                 "the refused drop must be reported, but was: " + userMessage());
   }

   @Test
   void orgAdminDropsOwnRoles_keptWithMessage() throws Exception {
      SRPrincipal orgAdmin = loginAs(ORG_ADMIN);

      setOrganizationInfo(model(allOrgMembersExcept(ORG_ADMIN_ROLE, ANALYST_ROLE)), orgAdmin);

      assertNotNull(fileProvider.getRole(ORG_ADMIN_ROLE), "the org admin's own role must be kept");
      assertNotNull(fileProvider.getRole(ANALYST_ROLE), "the org admin's own role must be kept");
      assertTrue(Arrays.asList(fileProvider.getUser(ORG_ADMIN).getRoles()).contains(ORG_ADMIN_ROLE),
                 "the org admin must still hold its org admin role");
      assertTrue(userMessage().contains(Catalog.getCatalog().getString("em.security.delself")),
                 "the refused drop must be reported, but was: " + userMessage());
   }

   @Test
   void orgAdminDropsOrdinaryGroupAndRole_deleted() throws Exception {
      SRPrincipal orgAdmin = loginAs(ORG_ADMIN);

      setOrganizationInfo(model(allOrgMembersExcept(PLAIN_GROUP, PLAIN_ROLE)), orgAdmin);

      assertNull(fileProvider.getGroup(PLAIN_GROUP), "an ordinary dropped group must be deleted");
      assertNull(fileProvider.getRole(PLAIN_ROLE), "an ordinary dropped role must be deleted");
      assertNotNull(fileProvider.getGroup(SYS_GROUP), "the kept group must remain");
      assertNotNull(fileProvider.getRole(SYS_ROLE), "the kept role must remain");
   }

   @Test
   void siteAdminDropsSysAdminGroupAndRole_deleted() throws Exception {
      SRPrincipal siteAdmin = loginAs(SITE_ADMIN);
      assertTrue(OrganizationManager.getInstance().isSiteAdmin(siteAdmin),
                 "precondition: siteAdmin must be a site admin");

      setOrganizationInfo(model(allOrgMembersExcept(SYS_GROUP, SYS_ROLE)), siteAdmin);

      assertNull(fileProvider.getGroup(SYS_GROUP), "a site admin may drop a system administrator group");
      assertNull(fileProvider.getRole(SYS_ROLE), "a site admin may drop a system administrator role");
   }

   @Test
   void callerWithoutAdminOnOrdinaryGroupAndRole_keptWithMessage() throws Exception {
      SRPrincipal plainUser = loginAs(PLAIN_USER);
      assertFalse(hasAdmin(plainUser, member(PLAIN_GROUP, Identity.GROUP)),
                  "precondition: the caller has no admin permission on the group");

      setOrganizationInfo(model(allOrgMembersExcept(PLAIN_GROUP, PLAIN_ROLE)), plainUser);

      assertNotNull(fileProvider.getGroup(PLAIN_GROUP), "the group must be kept");
      assertNotNull(fileProvider.getRole(PLAIN_ROLE), "the role must be kept");
      assertTrue(userMessage().contains("Unauthorized access to resource(s)"),
                 "the refused drop must be reported, but was: " + userMessage());
   }

   @Test
   void storedMemberListEmpty_providerGroupAndRoleStillKept() throws Exception {
      SRPrincipal orgAdmin = loginAs(ORG_ADMIN);
      String[] storedMembers = fileProvider.getOrganization(ORG_ID).getMembers();
      assertTrue(storedMembers == null || storedMembers.length == 0,
                 "precondition: the stored member list does not list the org's identities");
      assertNotNull(fileProvider.getGroup(SYS_GROUP), "precondition: the provider holds the group");

      setOrganizationInfo(model(allOrgMembersExcept(SYS_GROUP)), orgAdmin);

      assertNotNull(fileProvider.getGroup(SYS_GROUP), "the group must be kept");
   }

   private SRPrincipal loginAs(IdentityID user) {
      SRPrincipal principal = builder.principalOf(user.name, ORG_ID);
      ThreadContext.setContextPrincipal(principal);
      return principal;
   }

   private boolean hasAdmin(Principal principal, IdentityModel member) {
      ResourceType type = member.type() == Identity.USER ? ResourceType.SECURITY_USER :
         member.type() == Identity.GROUP ? ResourceType.SECURITY_GROUP : ResourceType.SECURITY_ROLE;
      return SecurityEngine.getSecurity().getSecurityProvider().checkPermission(
         principal, type, member.identityID().convertToKey(), ResourceAction.ADMIN);
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

   private static boolean containsName(List<IdentityModel> members, IdentityID id) {
      return members.stream().anyMatch(m -> m.identityID().name.equals(id.name));
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
