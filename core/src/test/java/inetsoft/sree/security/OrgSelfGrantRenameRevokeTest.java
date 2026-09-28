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

import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.security.IdentityModel;
import inetsoft.web.admin.security.IdentityService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Bug #77185: an org's SECURITY_ORGANIZATION self grant is keyed by (org name, org id), so it
 * embeds the mutable org name. Renaming an org (EM org pane, name-only) re-keys the grant from
 * (id, id) to (new name, id) through IdentityService.setIdentityPermissions(), which used to leave
 * the (id, id) copy behind. DefaultCheckPermissionStrategy's inherited org-admin fallback still
 * read that stale copy, so revoking the grantee after the rename had no effect.
 *
 * Runs against the real FileAuthenticationProvider/FileAuthorizationProvider and a real
 * IdentityService, with the copy-on-read cluster from PermissionMatrixOrgLifecycleTest so a
 * Permission fetched and mutated for one key does not also change the one stored under another.
 *
 * Also covers the generic re-key in setIdentityPermissions() removing the old key for user grants.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  PermissionMatrixOrgLifecycleTest.CopyOnReadClusterConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class OrgSelfGrantRenameRevokeTest {
   private static final String ORG_ID = "org77185";
   private static final String RENAMED_ORG_NAME = "Renamed77185";

   private SecurityTestDataBuilder builder;
   private AuthorizationChain chain;
   private FileAuthenticationProvider fileProvider;
   private IdentityService identityService;
   private SRPrincipal admin;

   @BeforeEach
   void setUp() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg(ORG_ID, ORG_ID)
         .addSysAdminRole("Administrator", ORG_ID)
         .addUser("admin", ORG_ID, "password")
         .addUserToRole("admin", "Administrator", ORG_ID)
         .addUser("grantee", ORG_ID, "password")
         .addUser("target", ORG_ID, "password")
         .addUser("stale", ORG_ID, "password")
         .setup();

      chain = SecurityEngine.getSecurity().getAuthorizationChain()
         .orElseThrow(() -> new AssertionError("expected an AuthorizationChain"));
      fileProvider = (FileAuthenticationProvider)
         ((AuthenticationChain) SecurityEngine.getSecurity().getSecurityProvider()
            .getAuthenticationProvider()).getProviders().get(0);

      // SecurityTestDataBuilder.teardown() does not clear the org self grant keys this test
      // writes, so start every case from an empty slate
      wipeOrgSelfGrants();

      identityService = new IdentityService(
         SecurityEngine.getSecurity(), SecurityEngine.getSecurity().getSecurityProvider(),
         null, null, null, null, null, null, null, null, null, null, null, null, // positions 3-14
         Optional.empty(),                                                    // position 15
         null, null, null, null, null, null, null, null, null, null, null, null, null, // 16-28
         Optional.empty());                                                   // position 29

      admin = builder.principalOf("admin", ORG_ID);
      // the grant is written to the ambient org's bucket, which is the edited org in the EM flow
      ThreadContext.setContextPrincipal(admin);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);

      if(chain != null) {
         wipeOrgSelfGrants();
      }

      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   @Test
   void revokeWithoutRenameRemovesOrgAdminPermission() throws Exception {
      IdentityID orgKey = new IdentityID(ORG_ID, ORG_ID);

      grant(orgKey, orgKey, List.of(granteeModel()));
      assertTrue(granteeIsAdmin(), "precondition: the org self grant must make grantee an admin");

      grant(orgKey, orgKey, List.of());
      assertFalse(granteeIsAdmin(), "revoking the org self grant must remove grantee's admin");
   }

   @Test
   void revokeAfterNameOnlyRenameRemovesOrgAdminPermission() throws Exception {
      IdentityID orgKey = new IdentityID(ORG_ID, ORG_ID);
      IdentityID renamedKey = new IdentityID(RENAMED_ORG_NAME, ORG_ID);

      grant(orgKey, orgKey, List.of(granteeModel()));
      assertTrue(granteeIsAdmin(), "precondition: the org self grant must make grantee an admin");

      // EM org pane save with a new name and the same id and grantees
      renameOrg();
      grant(orgKey, renamedKey, List.of(granteeModel()));

      assertTrue(granteeIsAdmin(), "the rename must keep the org self grant");
      assertNull(chain.getPermission(ResourceType.SECURITY_ORGANIZATION, orgKey, ORG_ID),
                 "the rename must not leave the pre-rename (id, id) grant behind");

      // EM org pane save that clears the grantees
      grant(renamedKey, renamedKey, List.of());
      assertFalse(granteeIsAdmin(),
                  "revoking the org self grant after a rename must remove grantee's admin");
   }

   @Test
   void saveAfterEarlierRenameDropsStaleIdKeyGrant() throws Exception {
      IdentityID orgKey = new IdentityID(ORG_ID, ORG_ID);
      IdentityID renamedKey = new IdentityID(RENAMED_ORG_NAME, ORG_ID);

      // data written before this fix: the rename left the grant at (id, id) and nothing at
      // (name, id), so the legacy fallback still honors it
      grant(orgKey, orgKey, List.of(granteeModel()));
      renameOrg();
      assertTrue(granteeIsAdmin(), "precondition: the (id, id)-only grant is still honored");

      // the next EM org pane save, which shows no grantees, must drop the stale key (its
      // grantees are not carried over)
      grant(renamedKey, renamedKey, List.of());
      assertNull(chain.getPermission(ResourceType.SECURITY_ORGANIZATION, orgKey, ORG_ID),
                 "saving the renamed org must drop the stale (id, id) grant");
      assertFalse(granteeIsAdmin(), "the stale (id, id) grant must not survive the save");
   }

   @Test
   void saveWithBothKeysDropsStaleIdKeyAndKeepsLiveGrant() throws Exception {
      IdentityID orgKey = new IdentityID(ORG_ID, ORG_ID);
      IdentityID renamedKey = new IdentityID(RENAMED_ORG_NAME, ORG_ID);

      // data written before this fix: a live (name, id) grant plus a stale (id, id) grant with
      // different grantees. The (id, id) save is a same-key save, so it removes nothing.
      grant(renamedKey, renamedKey, List.of(granteeModel()));
      grant(orgKey, orgKey, List.of(userModel("stale")));
      renameOrg();
      assertNotNull(chain.getPermission(ResourceType.SECURITY_ORGANIZATION, renamedKey, ORG_ID),
                    "precondition: the (name, id) grant must be stored");
      assertNotNull(chain.getPermission(ResourceType.SECURITY_ORGANIZATION, orgKey, ORG_ID),
                    "precondition: the stale (id, id) grant must be stored");

      // EM org pane save of the renamed org with its current grantees
      grant(renamedKey, renamedKey, List.of(granteeModel()));

      assertNull(chain.getPermission(ResourceType.SECURITY_ORGANIZATION, orgKey, ORG_ID),
                 "saving the renamed org must drop the stale (id, id) grant");
      assertTrue(granteeIsAdmin(), "the save must keep the (name, id) grant");
      assertFalse(isAdmin("stale"), "the stale (id, id) grantee must not be an admin");
   }

   @Test
   void deletedOrgGrantNotInheritedByOrgReusingItsId() throws Exception {
      IdentityID orgKey = new IdentityID(ORG_ID, ORG_ID);

      // a stale (id, id) grant left by a rename, as in deployments hit by the bug
      grant(orgKey, orgKey, List.of(granteeModel()));
      Organization org = renameOrg();

      // org delete, as IdentityService.syncIdentity() does it
      fileProvider.removeOrganization(ORG_ID);
      chain.cleanOrganizationFromPermissions(ORG_ID);

      // EM auto ids are reused: a new org gets the same id, with name == id
      FSOrganization reused = new FSOrganization(ORG_ID);
      reused.setName(ORG_ID);
      reused.setMembers(org.getMembers());
      fileProvider.addOrganization(reused);

      // the org delete also removed its users, so recreate the grantee and target in the new org
      // to make the ADMIN check depend only on the grant
      addUser("grantee");
      addUser("target");

      assertNull(chain.getPermission(ResourceType.SECURITY_ORGANIZATION, orgKey, ORG_ID),
                 "deleting the org must remove its stale (id, id) grant");
      assertFalse(granteeIsAdmin(), "an org reusing a deleted org's id must not inherit its grant");

      // control: a grant made in the new org does make grantee an admin
      grant(orgKey, orgKey, List.of(granteeModel()));
      assertTrue(granteeIsAdmin(), "control: a grant in the new org must make grantee an admin");
   }

   @Test
   void userRenameThroughSetIdentityPermissionsDropsOldKey() {
      IdentityID oldUser = new IdentityID("target", ORG_ID);
      IdentityID newUser = new IdentityID("target2", ORG_ID);

      try {
         identityService.setIdentityPermissions(
            oldUser, oldUser, ResourceType.SECURITY_USER, admin, List.of(granteeModel()), ORG_ID);
         assertNotNull(chain.getPermission(ResourceType.SECURITY_USER, oldUser, ORG_ID),
                       "precondition: the user grant must be stored");

         identityService.setIdentityPermissions(
            oldUser, newUser, ResourceType.SECURITY_USER, admin, List.of(granteeModel()), ORG_ID);
         assertNotNull(chain.getPermission(ResourceType.SECURITY_USER, newUser, ORG_ID),
                       "the grant must be stored under the new key");
         assertNull(chain.getPermission(ResourceType.SECURITY_USER, oldUser, ORG_ID),
                    "re-keying the grant must remove the old key");
      }
      finally {
         chain.removePermission(ResourceType.SECURITY_USER, oldUser, ORG_ID);
         chain.removePermission(ResourceType.SECURITY_USER, newUser, ORG_ID);
      }
   }

   // name-only rename of the org, as IdentityService.setOrganizationInfo() does it
   private Organization renameOrg() {
      Organization org = fileProvider.getOrganization(ORG_ID);
      FSOrganization renamedOrg = new FSOrganization(ORG_ID);
      renamedOrg.setName(RENAMED_ORG_NAME);
      renamedOrg.setMembers(org.getMembers());
      fileProvider.setOrganization(ORG_ID, renamedOrg);
      return org;
   }

   private void grant(IdentityID oldID, IdentityID newID, List<IdentityModel> grantees) {
      identityService.setIdentityPermissions(
         oldID, newID, ResourceType.SECURITY_ORGANIZATION, admin, grantees, ORG_ID);
   }

   private void addUser(String userName) {
      FSUser user = new FSUser(new IdentityID(userName, ORG_ID));
      user.setActive(true);
      fileProvider.addUser(user);
   }

   private boolean granteeIsAdmin() throws Exception {
      return isAdmin("grantee");
   }

   private boolean isAdmin(String userName) throws Exception {
      SRPrincipal user = builder.principalOf(userName, ORG_ID);

      try {
         ThreadContext.setContextPrincipal(user);
         return SecurityEngine.getSecurity().checkPermission(
            user, ResourceType.SECURITY_USER, new IdentityID("target", ORG_ID).convertToKey(),
            ResourceAction.ADMIN);
      }
      finally {
         ThreadContext.setContextPrincipal(admin);
      }
   }

   private static IdentityModel granteeModel() {
      return userModel("grantee");
   }

   private static IdentityModel userModel(String userName) {
      return IdentityModel.builder()
         .identityID(new IdentityID(userName, ORG_ID))
         .type(Identity.USER)
         .build();
   }

   private void wipeOrgSelfGrants() {
      chain.removePermission(ResourceType.SECURITY_ORGANIZATION, new IdentityID(ORG_ID, ORG_ID), ORG_ID);
      chain.removePermission(ResourceType.SECURITY_ORGANIZATION,
                             new IdentityID(RENAMED_ORG_NAME, ORG_ID), ORG_ID);
   }
}
