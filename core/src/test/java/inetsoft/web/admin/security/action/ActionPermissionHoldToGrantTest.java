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
package inetsoft.web.admin.security.action;

/*
 * Bug #77362: a caller who is neither a site admin nor an org admin may only grant action
 * permissions they hold themselves ("hold-to-grant"). Drives the real
 * ActionPermissionController.setPermissions() with the real action tree, the real
 * ResourcePermissionService and a real security provider, in multi-tenant mode (fixture of
 * ActionPermissionControllerRealTreeTest).
 *
 * Personas:
 *   delegate    - plain org user holding ACCESS on EM:* (needed to open /em at all) and on
 *                 settings/security/actions; member of delegGroup and delegRole; holds ADMIN on
 *                 the SECURITY_USER managedUser
 *   buddy       - plain org user, the only user an admin let into settings/security/users
 *   managedUser - also granted settings/security/users, administered by the delegate
 *   secondUser  - plain org user without grants
 *   hiddenGroup, hiddenRole - org group and role the delegate neither administers nor belongs to
 *   managedGroup, managedRole - org group and role the delegate administers but does not belong to
 *   orgAdmin    - org user holding the global "Organization Administrator" role
 *   siteAdmin   - org user holding a system administrator role
 *
 * settings/security/users and settings/security/provider are explicitly restricted
 * (hasOrgEdited) so the delegate does not inherit them from EM:*.
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.DataSpace;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.authz.ComponentAuthorizationService;
import inetsoft.web.admin.authz.ViewComponent;
import inetsoft.web.admin.content.repository.ResourcePermissionService;
import inetsoft.web.admin.security.ResourcePermissionModel;
import inetsoft.web.admin.security.ResourcePermissionTableModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ActionPermissionHoldToGrantTest {
   private static final String ORG_NAME = "holdToGrantOrg";
   private static final String ORG_ID = "hold_to_grant_org_id";
   private static final IdentityID ORG_ADMIN_ROLE = new IdentityID("Organization Administrator", null);
   private static final String SETTINGS_ACTIONS = "settings/security/actions";
   private static final String SETTINGS_USERS = "settings/security/users";
   private static final String SETTINGS_PROVIDER = "settings/security/provider";

   private static SecurityTestDataBuilder builder;
   private static SRPrincipal delegate;
   private static SRPrincipal buddy;
   private static SRPrincipal secondUser;
   private static SRPrincipal orgAdmin;
   private static SRPrincipal siteAdmin;
   private static ActionPermissionController controller;

   private MockedStatic<SUtil> sutil;
   private MockedStatic<LicenseManager> license;

   @BeforeAll
   static void setupAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg(ORG_NAME, ORG_ID)
         .addUser("delegateUser", ORG_ID, "password")
         .addUser("buddyUser", ORG_ID, "password")
         .addUser("managedUser", ORG_ID, "password")
         .addUser("secondUser", ORG_ID, "password")
         .addUser("orgAdminUser", ORG_ID, "password")
         .addUser("siteAdminUser", ORG_ID, "password")
         .addGroup("delegGroup", ORG_ID)
         .addUserToGroup("delegateUser", "delegGroup", ORG_ID)
         .addRole("delegRole", ORG_ID)
         .addUserToRole("delegateUser", "delegRole", ORG_ID)
         .addGroup("hiddenGroup", ORG_ID)
         .addRole("hiddenRole", ORG_ID)
         .addGroup("managedGroup", ORG_ID)
         .addRole("managedRole", ORG_ID)
         .addSysAdminRole("siteAdminRole", ORG_ID)
         .addUserToRole("siteAdminUser", "siteAdminRole", ORG_ID)
         .grantPermission(ResourceType.EM, "*", ResourceAction.ACCESS,
                          "delegateUser", Identity.USER, ORG_ID)
         .grantPermission(ResourceType.SECURITY_USER,
                          new IdentityID("managedUser", ORG_ID).convertToKey(),
                          ResourceAction.ADMIN, "delegateUser", Identity.USER, ORG_ID)
         .grantPermission(ResourceType.SECURITY_GROUP,
                          new IdentityID("managedGroup", ORG_ID).convertToKey(),
                          ResourceAction.ADMIN, "delegateUser", Identity.USER, ORG_ID)
         .grantPermission(ResourceType.SECURITY_ROLE,
                          new IdentityID("managedRole", ORG_ID).convertToKey(),
                          ResourceAction.ADMIN, "delegateUser", Identity.USER, ORG_ID);
      builder.setup();

      delegate = builder.principalOf("delegateUser", ORG_ID);
      buddy = builder.principalOf("buddyUser", ORG_ID);
      secondUser = builder.principalOf("secondUser", ORG_ID);
      orgAdmin = builder.principalOf("orgAdminUser", ORG_ID);
      orgAdmin.setRoles(new IdentityID[]{ ORG_ADMIN_ROLE });
      siteAdmin = builder.principalOf("siteAdminUser", ORG_ID);

      ViewComponent components;

      try(InputStream in = ActionPermissionControllerRealTreeTest.class
         .getResourceAsStream("view-components-snapshot.json"))
      {
         components = new ObjectMapper().readValue(in, ViewComponent.class);
      }

      ComponentAuthorizationService componentService =
         Mockito.mock(ComponentAuthorizationService.class);
      Mockito.when(componentService.getComponentTree()).thenReturn(components);

      PortalThemesManager themes =
         new PortalThemesManager(Cluster.getInstance(), DataSpace.getDataSpace());
      themes.loadThemes();

      SecurityEngine engine = SecurityEngine.getSecurity();
      ActionPermissionService actionService =
         new ActionPermissionService(componentService, engine, themes);
      ResourcePermissionService permissionService =
         new ResourcePermissionService(engine.getSecurityProvider(), engine, null, null);
      controller = new ActionPermissionController(actionService, permissionService, engine);
   }

   @AfterAll
   static void teardownAll() {
      if(builder != null) {
         builder.teardown();
      }
   }

   @BeforeEach
   void setUp() {
      sutil = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
      sutil.when(SUtil::isMultiTenant).thenReturn(true);
      license = Mockito.mockStatic(LicenseManager.class, Mockito.CALLS_REAL_METHODS);
      license.when(LicenseManager::isEnterprise).thenReturn(true);

      // restore the admin-configured state, a test may have changed it
      restrict(SETTINGS_ACTIONS, "delegateUser", "buddyUser");
      restrict(SETTINGS_USERS, "buddyUser", "managedUser");
      restrict(SETTINGS_PROVIDER, "buddyUser");
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      license.close();
      sutil.close();
   }

   // precondition of every refused case: the delegate does not hold settings/security/users
   @Test
   void fixture_delegateHoldsActionsButNotUsers() throws Exception {
      assertTrue(canAccess(delegate, SETTINGS_ACTIONS));
      assertFalse(canAccess(delegate, SETTINGS_USERS));
      assertTrue(canAccess(buddy, SETTINGS_USERS));
   }

   @Test
   void delegate_grantToSelf_refused() throws Exception {
      assertRefused(SETTINGS_USERS, grant(row("delegateUser", Identity.Type.USER)));
      assertFalse(canAccess(delegate, SETTINGS_USERS));
   }

   @Test
   void delegate_grantToOwnGroup_refused() throws Exception {
      assertRefused(SETTINGS_USERS, grant(row("delegGroup", Identity.Type.GROUP)));
      assertFalse(canAccess(delegate, SETTINGS_USERS));
   }

   @Test
   void delegate_grantToOwnRole_refused() throws Exception {
      assertRefused(SETTINGS_USERS, grant(row("delegRole", Identity.Type.ROLE)));
      assertFalse(canAccess(delegate, SETTINGS_USERS));
   }

   @Test
   void delegate_grantToSecondUser_refused() throws Exception {
      assertRefused(SETTINGS_USERS, grant(row("secondUser", Identity.Type.USER)));
      assertFalse(canAccess(secondUser, SETTINGS_USERS));
   }

   // an organization grant is keyed by the org name and reaches every member, the delegate too
   @Test
   void delegate_grantToOwnOrganization_refused() throws Exception {
      assertRefused(SETTINGS_USERS, grant(row(ORG_NAME, Identity.Type.ORGANIZATION)));
      assertFalse(permission(SETTINGS_USERS)
                     .getOrgScopedGrants(ResourceAction.ACCESS, Identity.ORGANIZATION, ORG_ID)
                     .stream().anyMatch(id -> ORG_NAME.equals(id.name)));
      assertFalse(canAccess(delegate, SETTINGS_USERS));
   }

   // an existing grant echoed back does not excuse a new one in the same save
   @Test
   void delegate_grantAddedToExistingRows_refused() throws Exception {
      assertRefused(SETTINGS_USERS, grant(row("buddyUser", Identity.Type.USER),
                                          row("managedUser", Identity.Type.USER),
                                          row("delegateUser", Identity.Type.USER)));
      assertFalse(canAccess(delegate, SETTINGS_USERS));
   }

   // refuter bypass: an empty displayActions set with hasOrgEdited=false turns the restricted
   // page back into "use parent permissions", which the delegate inherits from EM:*
   @Test
   void delegate_useParentFlipWithEmptyDisplayActions_refused() throws Exception {
      ResourcePermissionModel body = model(List.of(), EnumSet.noneOf(ResourceAction.class), false);

      assertRefused(SETTINGS_USERS, body);
      assertTrue(isEdited(SETTINGS_USERS), "the restriction must stay in place");
      assertFalse(canAccess(delegate, SETTINGS_USERS));
      assertTrue(canAccess(buddy, SETTINGS_USERS));
   }

   // the UI's "use parent permissions" checkbox (permissions=null, hasOrgEdited=false)
   @Test
   void delegate_useParentFlipWithNullPermissions_refused() throws Exception {
      ResourcePermissionModel body = model(null, EnumSet.of(ResourceAction.ACCESS), false);

      assertRefused(SETTINGS_USERS, body);
      assertTrue(isEdited(SETTINGS_USERS));
      assertTrue(hasUserGrant(SETTINGS_USERS, "buddyUser"));
      assertFalse(canAccess(delegate, SETTINGS_USERS));
   }

   // refuter bypass: permissions=null used to clear the grants of every identity in the org; the
   // grants of identities the delegate does not administer (buddy) must survive
   @Test
   void delegate_nullPermissionsOnHeldNode_keepsUnadministeredGrants() throws Exception {
      ThreadContext.setContextPrincipal(delegate);
      ResourcePermissionModel body = model(null, EnumSet.of(ResourceAction.ACCESS), true);

      controller.setPermissions(ResourceType.EM_COMPONENT.name(), SETTINGS_ACTIONS, true, body,
                                delegate);

      assertTrue(hasUserGrant(SETTINGS_ACTIONS, "buddyUser"), "buddy's grant was wiped");
      assertTrue(canAccess(buddy, SETTINGS_ACTIONS));
   }

   // the delegate administers managedUser, so the same save on the users page removes only that
   // grant
   @Test
   void delegate_nullPermissionsOnNotHeldNode_removesOnlyAdministeredGrants() throws Exception {
      ThreadContext.setContextPrincipal(delegate);
      ResourcePermissionModel body = model(null, EnumSet.of(ResourceAction.ACCESS), true);

      controller.setPermissions(ResourceType.EM_COMPONENT.name(), SETTINGS_USERS, true, body,
                                delegate);

      assertFalse(hasUserGrant(SETTINGS_USERS, "managedUser"));
      assertTrue(hasUserGrant(SETTINGS_USERS, "buddyUser"), "buddy's grant was wiped");
      assertFalse(canAccess(delegate, SETTINGS_USERS));
   }

   // control: removing a grant from an administered user is allowed on a node the delegate does
   // not hold
   @Test
   void delegate_removeGrantOnNotHeldNode_saved() throws Exception {
      ThreadContext.setContextPrincipal(delegate);

      controller.setPermissions(ResourceType.EM_COMPONENT.name(), SETTINGS_USERS, true,
                                grant(), delegate);

      assertFalse(hasUserGrant(SETTINGS_USERS, "managedUser"), "the grant must be removed");
      assertTrue(hasUserGrant(SETTINGS_USERS, "buddyUser"));
      assertTrue(isEdited(SETTINGS_USERS));
   }

   // control: re-saving the delegate's own view unchanged is allowed on a node the delegate does
   // not hold, and keeps the grants of the identities the view hides
   @Test
   void delegate_unchangedResaveOnNotHeldNode_saved() throws Exception {
      storeGrant(SETTINGS_USERS, "hiddenGroup", Identity.Type.GROUP);
      storeGrant(SETTINGS_USERS, "hiddenRole", Identity.Type.ROLE);
      ResourcePermissionModel view = delegateView(SETTINGS_USERS);

      assertEquals(List.of("managedUser"), rowNames(view));

      controller.setPermissions(ResourceType.EM_COMPONENT.name(), SETTINGS_USERS, true, view,
                                delegate);

      assertTrue(hasUserGrant(SETTINGS_USERS, "buddyUser"));
      assertTrue(hasUserGrant(SETTINGS_USERS, "managedUser"));
      assertTrue(hasGrant(SETTINGS_USERS, "hiddenGroup", Identity.GROUP));
      assertTrue(hasGrant(SETTINGS_USERS, "hiddenRole", Identity.ROLE));
      assertFalse(canAccess(delegate, SETTINGS_USERS));
   }

   // Bug #77461, stored grants of a group and role the delegate administers still count, so
   // re-saving a view that shows them is not a new grant
   @Test
   void delegate_resaveAdministeredGroupAndRoleOnNotHeldNode_saved() throws Exception {
      storeGrant(SETTINGS_USERS, "managedGroup", Identity.Type.GROUP);
      storeGrant(SETTINGS_USERS, "managedRole", Identity.Type.ROLE);
      ResourcePermissionModel view = delegateView(SETTINGS_USERS);

      assertEquals(Set.of("managedUser", "managedGroup", "managedRole"),
                   new HashSet<>(rowNames(view)));

      controller.setPermissions(ResourceType.EM_COMPONENT.name(), SETTINGS_USERS, true, view,
                                delegate);

      assertTrue(hasGrant(SETTINGS_USERS, "managedGroup", Identity.GROUP));
      assertTrue(hasGrant(SETTINGS_USERS, "managedRole", Identity.ROLE));
      assertTrue(hasUserGrant(SETTINGS_USERS, "buddyUser"));
      assertFalse(canAccess(delegate, SETTINGS_USERS));
   }

   // Bug #77461, a row naming an identity the delegate does not administer is a new grant whether
   // or not that identity already holds the action, so the reply does not reveal the hidden grant
   @Test
   void delegate_hiddenUserRow_refusedWhetherHeldOrNot() throws Exception {
      assertHiddenRowRefusedWhetherHeldOrNot("secondUser", Identity.Type.USER);
   }

   @Test
   void delegate_hiddenGroupRow_refusedWhetherHeldOrNot() throws Exception {
      assertHiddenRowRefusedWhetherHeldOrNot("hiddenGroup", Identity.Type.GROUP);
   }

   @Test
   void delegate_hiddenRoleRow_refusedWhetherHeldOrNot() throws Exception {
      assertHiddenRowRefusedWhetherHeldOrNot("hiddenRole", Identity.Type.ROLE);
   }

   // control: the delegate may hand out an action they hold
   @Test
   void delegate_grantHeldAction_saved() throws Exception {
      ThreadContext.setContextPrincipal(delegate);

      controller.setPermissions(ResourceType.EM_COMPONENT.name(), SETTINGS_ACTIONS, true,
                                grant(row("secondUser", Identity.Type.USER)), delegate);

      assertTrue(hasUserGrant(SETTINGS_ACTIONS, "secondUser"));
      assertTrue(canAccess(secondUser, SETTINGS_ACTIONS));
   }

   // single-tenant mode has no org admin exclusions, so the whole site tree is offered; the
   // delegate still cannot grant a page they do not hold
   @Test
   void delegate_singleTenantSelfGrantOnProvider_refused() throws Exception {
      sutil.when(SUtil::isMultiTenant).thenReturn(false);

      assertRefused(SETTINGS_PROVIDER, grant(row("delegateUser", Identity.Type.USER)));
      assertFalse(hasUserGrant(SETTINGS_PROVIDER, "delegateUser"));
   }

   // control: an org admin is not limited to what they hold
   @Test
   void orgAdmin_grantToSecondUser_saved() throws Exception {
      ThreadContext.setContextPrincipal(orgAdmin);

      controller.setPermissions(ResourceType.EM_COMPONENT.name(), SETTINGS_USERS, true,
                                grant(row("secondUser", Identity.Type.USER)), orgAdmin);

      assertTrue(hasUserGrant(SETTINGS_USERS, "secondUser"));
   }

   // control: an org admin may still switch a page to "use parent permissions"
   @Test
   void orgAdmin_useParentFlip_saved() throws Exception {
      ThreadContext.setContextPrincipal(orgAdmin);

      controller.setPermissions(ResourceType.EM_COMPONENT.name(), SETTINGS_USERS, true,
                                model(null, EnumSet.of(ResourceAction.ACCESS), false), orgAdmin);

      assertFalse(isEdited(SETTINGS_USERS));
   }

   // control: a site admin is not limited to what they hold
   @Test
   void siteAdmin_grantToSecondUser_saved() throws Exception {
      ThreadContext.setContextPrincipal(siteAdmin);

      controller.setPermissions(ResourceType.EM_COMPONENT.name(), SETTINGS_USERS, true,
                                grant(row("secondUser", Identity.Type.USER)), siteAdmin);

      assertTrue(hasUserGrant(SETTINGS_USERS, "secondUser"));
   }

   // the hidden identity holds the action, then does not; the delegate's view plus its row is
   // refused both times
   private static void assertHiddenRowRefusedWhetherHeldOrNot(String name, Identity.Type type)
      throws Exception
   {
      storeGrant(SETTINGS_USERS, name, type);
      assertTrue(hasGrant(SETTINGS_USERS, name, type.code()));
      assertHiddenRowRefused(name, type);

      restrict(SETTINGS_USERS, "buddyUser", "managedUser");
      assertFalse(hasGrant(SETTINGS_USERS, name, type.code()));
      assertHiddenRowRefused(name, type);
   }

   private static void assertHiddenRowRefused(String name, Identity.Type type) throws Exception {
      assertFalse(canAccess(delegate, SETTINGS_USERS));
      ResourcePermissionModel view = delegateView(SETTINGS_USERS);
      assertFalse(rowNames(view).contains(name), name + " must be hidden from the delegate");

      List<ResourcePermissionTableModel> rows = new ArrayList<>(view.permissions());
      rows.add(row(name, type));
      assertRefused(SETTINGS_USERS, ResourcePermissionModel.builder().from(view)
         .permissions(rows).build());
   }

   private static ResourcePermissionModel delegateView(String path) {
      ThreadContext.setContextPrincipal(delegate);
      return controller.getPermissions(ResourceType.EM_COMPONENT.name(), path, true, delegate);
   }

   private static List<String> rowNames(ResourcePermissionModel view) {
      return view.permissions().stream().map(r -> r.identityID().name).toList();
   }

   private static void assertRefused(String path, ResourcePermissionModel body) {
      ThreadContext.setContextPrincipal(delegate);
      Map<String, Boolean> before = grantState(path);

      assertThrows(java.lang.SecurityException.class, () -> controller.setPermissions(
         ResourceType.EM_COMPONENT.name(), path, true, body, delegate));

      assertEquals(before, grantState(path), "nothing may be written on " + path);
   }

   private static Map<String, Boolean> grantState(String path) {
      Map<String, Boolean> state = new TreeMap<>();

      for(String user : List.of("delegateUser", "buddyUser", "managedUser", "secondUser")) {
         state.put(user, hasUserGrant(path, user));
      }

      Permission perm = permission(path);
      state.put("#groups", perm != null &&
         !perm.getOrgScopedGrants(ResourceAction.ACCESS, Identity.GROUP, ORG_ID).isEmpty());
      state.put("#roles", perm != null &&
         !perm.getOrgScopedGrants(ResourceAction.ACCESS, Identity.ROLE, ORG_ID).isEmpty());
      state.put("#edited", isEdited(path));
      return state;
   }

   private static void restrict(String path, String... users) {
      Permission perm = new Permission();
      Set<String> names = new HashSet<>(Arrays.asList(users));
      perm.setUserGrantsForOrg(ResourceAction.ACCESS, names, ORG_ID);
      perm.updateGrantAllByOrg(ORG_ID, true);
      engine().getSecurityProvider().setPermission(ResourceType.EM_COMPONENT, path, perm, ORG_ID);
   }

   private static void storeGrant(String path, String name, Identity.Type type) {
      Permission perm = permission(path);
      Set<String> names = new HashSet<>();
      perm.getOrgScopedGrants(ResourceAction.ACCESS, type.code(), ORG_ID)
         .forEach(id -> names.add(id.name));
      names.add(name);

      switch(type) {
      case USER -> perm.setUserGrantsForOrg(ResourceAction.ACCESS, names, ORG_ID);
      case GROUP -> perm.setGroupGrantsForOrg(ResourceAction.ACCESS, names, ORG_ID);
      case ROLE -> perm.setRoleGrantsForOrg(ResourceAction.ACCESS, names, ORG_ID);
      default -> throw new IllegalArgumentException(type.toString());
      }

      engine().getSecurityProvider().setPermission(ResourceType.EM_COMPONENT, path, perm, ORG_ID);
   }

   private static boolean hasGrant(String path, String name, int type) {
      Permission perm = permission(path);
      return perm != null && perm.getOrgScopedGrants(ResourceAction.ACCESS, type, ORG_ID)
         .contains(new IdentityID(name, ORG_ID));
   }

   private static Permission permission(String path) {
      return engine().getSecurityProvider().getPermission(ResourceType.EM_COMPONENT, path, ORG_ID);
   }

   private static boolean isEdited(String path) {
      Permission perm = permission(path);
      return perm != null && perm.hasOrgEditedGrantAll(ORG_ID);
   }

   private static boolean hasUserGrant(String path, String user) {
      Permission perm = permission(path);
      return perm != null &&
         perm.getOrgScopedGrants(ResourceAction.ACCESS, Identity.USER, ORG_ID)
            .contains(new IdentityID(user, ORG_ID));
   }

   private static boolean canAccess(SRPrincipal principal, String path) throws Exception {
      return engine().checkPermission(principal, ResourceType.EM_COMPONENT, path,
                                      ResourceAction.ACCESS);
   }

   private static ResourcePermissionTableModel row(String name, Identity.Type type) {
      return ResourcePermissionTableModel.builder()
         .identityID(new IdentityID(name, ORG_ID))
         .type(type)
         .actions(EnumSet.of(ResourceAction.ACCESS))
         .build();
   }

   private static ResourcePermissionModel grant(ResourcePermissionTableModel... rows) {
      return model(List.of(rows), EnumSet.of(ResourceAction.ACCESS), true);
   }

   private static ResourcePermissionModel model(List<ResourcePermissionTableModel> rows,
                                                EnumSet<ResourceAction> displayActions,
                                                boolean hasOrgEdited)
   {
      return ResourcePermissionModel.builder()
         .permissions(rows)
         .displayActions(displayActions)
         .hasOrgEdited(hasOrgEdited)
         .securityEnabled(true)
         .requiresBoth(false)
         .derivePermissionLabel("")
         .grantReadToAllVisible(false)
         .build();
   }

   private static SecurityEngine engine() {
      return SecurityEngine.getSecurity();
   }
}
