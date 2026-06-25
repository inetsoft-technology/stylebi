/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.sree.security.provider;

/*
 * Intent vs implementation suspects (VirtualAuthenticationProvider-specific)
 *
 * [Suspect 2] getUser(null userIdentity)
 *             intent : return null (unknown user)
 *             actual : VirtualAuthenticationProvider:140 accesses userIdentity.name before
 *                      null check → NullPointerException
 *             see    : getUser_nullIdentity_doesNotThrow (below, @Disabled)
 *
 * [Suspect 3] addUser(nonAdminUser)
 *             intent : add user or throw UnsupportedOperationException
 *             actual : silently ignored — VirtualAuthenticationProvider:265 only branches on
 *                      "admin"; all other users are dropped without error or exception
 *             see    : addUser_nonAdminUser_doesNotSilentlyIgnore (below, @Disabled)
 */

/*
 * Cases deferred — require integration context:
 *
 * tearDown() — verifies dmgr.clear() removes the DataChangeListener from DataSpace;
 *   needs DataChangeListenerManager introspection; deferred to integration test
 *
 * load() file-not-found path — INETSOFT_ADMIN_PASSWORD env var absent → IllegalStateException;
 *   needs process-level environment isolation; not testable in-JVM
 */

import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.util.DataSpace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mindrot.BCrypt;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

// VirtualAuthenticationProvider serves one hardcoded admin user only;
// inactiveUserId() and anotherOrgId() return null → C10 and C15 are skipped by assumeTrue.

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VirtualAuthenticationProviderTest
   extends AuthenticationProviderContractTest<VirtualAuthenticationProvider>
{
   static final String ADMIN_PASSWORD = "Admin@123!";
   static final IdentityID ADMIN_ID =
      new IdentityID("admin", Organization.getDefaultOrganizationID());

   @BeforeAll
   static void writeVirtualConfig() throws Exception {
      String defaultOrgId = Organization.getDefaultOrganizationID();
      String bcryptHash = BCrypt.hashpw(ADMIN_PASSWORD, BCrypt.gensalt());
      String adminRoleKey = new IdentityID("Administrator", defaultOrgId).convertToKey();

      // Build virtual_security.xml so VirtualAuthenticationProvider can load
      // without requiring the INETSOFT_ADMIN_PASSWORD environment variable.
      String xml =
         "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
         "<virtualSecurityProvider>\n" +
         "<FSUser class=\"inetsoft.sree.security.FSUser\">" +
         "<name><![CDATA[admin]]></name>" +
         "<active><![CDATA[true]]></active>" +
         "<organization><![CDATA[" + defaultOrgId + "]]></organization>" +
         "<password algorithm=\"bcrypt\"><![CDATA[" + bcryptHash + "]]></password>" +
         "<emails></emails>" +
         "<roles><role><![CDATA[" + adminRoleKey + "]]></role></roles>" +
         "<groups></groups>" +
         "</FSUser>\n" +
         "</virtualSecurityProvider>\n";

      DataSpace space = DataSpace.getDataSpace();

      try(DataSpace.Transaction tx = space.beginTransaction();
          OutputStream out = tx.newStream(null, "virtual_security.xml"))
      {
         out.write(xml.getBytes(StandardCharsets.UTF_8));
         tx.commit();
      }
   }

   @Override
   protected VirtualAuthenticationProvider createProvider() {
      return new VirtualAuthenticationProvider();
   }

   @Override protected IdentityID validUserId()  { return ADMIN_ID; }
   @Override protected String    validPassword()  { return ADMIN_PASSWORD; }
   @Override protected String    validOrgId()     { return Organization.getDefaultOrganizationID(); }

   // -----------------------------------------------------------------------
   // isVirtual / roles / org IDs / users — structural checks
   // -----------------------------------------------------------------------

   @Test
   void isVirtual_returnsTrue() {
      assertTrue(provider.isVirtual(), "VirtualAuthenticationProvider must report isVirtual()=true");
   }

   @Test
   void getRoles_containsAdministratorAndEveryone() {
      IdentityID[] roles = provider.getRoles();
      assertNotNull(roles);
      boolean hasAdmin = false, hasEveryone = false;

      for(IdentityID r : roles) {
         if("Administrator".equals(r.name)) hasAdmin    = true;
         if("Everyone".equals(r.name))      hasEveryone = true;
      }

      assertTrue(hasAdmin,    "Must expose Administrator role");
      assertTrue(hasEveryone, "Must expose Everyone role");
   }

   @Test
   void getOrganizationIDs_returnsDefaultOrg() {
      String[] orgIds = provider.getOrganizationIDs();
      assertNotNull(orgIds);
      assertEquals(1, orgIds.length);
      assertEquals(Organization.getDefaultOrganizationID(), orgIds[0]);
   }

   @Test
   void getUsers_containsAdminAndAnonymous() {
      IdentityID[] users = provider.getUsers();
      assertNotNull(users);
      boolean hasAdmin = false, hasAnonymous = false;

      for(IdentityID u : users) {
         if("admin".equals(u.name))     hasAdmin     = true;
         if("anonymous".equals(u.name)) hasAnonymous = true;
      }

      assertTrue(hasAdmin,     "getUsers() must include admin");
      assertTrue(hasAnonymous, "getUsers() must include anonymous");
   }

   // -----------------------------------------------------------------------
   // authenticate() — VirtualAuthenticationProvider-specific ticket branches
   // Lines 82–89: userid == null, userid.name.length() == 0
   // -----------------------------------------------------------------------

   // [Risk 3] Line 85: ticket carries null userid → return false, must NOT throw
   @Test
   void authenticate_nullUserIdInTicket_returnsFalse() {
      DefaultTicket ticket = new DefaultTicket(null, ADMIN_PASSWORD);
      assertFalse(provider.authenticate(ADMIN_ID, ticket),
         "Ticket with null userid must return false");
   }

   // [Risk 3] Line 85: empty userid.name — distinct from C11 (empty password)
   @Test
   void authenticate_emptyUserNameInTicket_returnsFalse() {
      DefaultTicket ticket = new DefaultTicket(
         new IdentityID("", Organization.getDefaultOrganizationID()), ADMIN_PASSWORD);
      assertFalse(provider.authenticate(ADMIN_ID, ticket),
         "Ticket with empty username must return false");
   }

   // -----------------------------------------------------------------------
   // getUser() — system and anonymous built-in identity branches
   // Lines 144–150
   // -----------------------------------------------------------------------

   // [Risk 2] Line 144: XPrincipal.SYSTEM branch — initialized in constructor
   @Test
   void getUser_systemIdentityName_returnsNonNull() {
      User system = provider.getUser(new IdentityID(XPrincipal.SYSTEM, null));
      assertNotNull(system, "System identity must return a non-null User");
      assertEquals(XPrincipal.SYSTEM, system.getName(),
         "Returned user must have the system identity name");
   }

   // [Risk 2] Line 148: XPrincipal.ANONYMOUS branch — initialized in constructor
   @Test
   void getUser_anonymousIdentityName_returnsNonNull() {
      User anon = provider.getUser(
         new IdentityID(XPrincipal.ANONYMOUS, Organization.getDefaultOrganizationID()));
      assertNotNull(anon, "Anonymous identity must return a non-null User");
      assertEquals(XPrincipal.ANONYMOUS, anon.getName(),
         "Returned user must have the anonymous identity name");
   }

   // [Suspect 2 — @Disabled]: Line 140 accesses userIdentity.name before null guard → NPE
   @Test
   @Disabled("Suspect 2: getUser(null) throws NPE — " +
      "VirtualAuthenticationProvider:140 accesses userIdentity.name before null check; " +
      "Fix: add `if (userIdentity == null) return null;` at method entry")
   void getUser_nullIdentity_doesNotThrow() {
      assertDoesNotThrow(
         () -> provider.getUser(null),
         "getUser(null) must not throw — return null for unknown/null identity");
   }

   // -----------------------------------------------------------------------
   // addUser() — admin is accepted; non-admin is silently dropped
   // Lines 264–269
   // -----------------------------------------------------------------------

   // [Risk 2] Line 265: admin user → admin field replaced, credential change takes effect
   // DataSpace is always restored in the finally block so subsequent tests continue to
   // authenticate with ADMIN_PASSWORD.
   @Test
   void addUser_adminUser_updatesAdminCredential() throws Exception {
      String newPassword = "NewAdmin@456!";

      FSUser newAdmin = new FSUser(ADMIN_ID);
      newAdmin.setPassword(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
      newAdmin.setPasswordAlgorithm("bcrypt");
      newAdmin.setActive(true);
      newAdmin.setRoles(new IdentityID[] {
         new IdentityID("Administrator", Organization.getDefaultOrganizationID())
      });

      try {
         provider.addUser(newAdmin);

         assertTrue(provider.authenticate(ADMIN_ID, new DefaultTicket(ADMIN_ID, newPassword)),
            "New password must succeed after addUser replaces admin credential");
         assertFalse(provider.authenticate(ADMIN_ID, new DefaultTicket(ADMIN_ID, ADMIN_PASSWORD)),
            "Old password must be rejected after admin credential update");
      }
      finally {
         // Restore DataSpace so subsequent tests continue to use ADMIN_PASSWORD
         writeVirtualConfig();
      }
   }

   // [Suspect 3 — @Disabled]: non-admin addUser() is silently ignored — contract violation
   // EditableAuthenticationProvider.addUser() must either add the user or throw
   // UnsupportedOperationException; VirtualAuthenticationProvider does neither.
   @Test
   @Disabled("Suspect 3: addUser(nonAdmin) silently ignores the request — " +
      "VirtualAuthenticationProvider:265 only processes admin; Fix: throw " +
      "UnsupportedOperationException for non-admin to make the contract violation explicit")
   void addUser_nonAdminUser_doesNotSilentlyIgnore() {
      IdentityID newUserId = new IdentityID("testuser", Organization.getDefaultOrganizationID());
      FSUser newUser = new FSUser(newUserId);
      newUser.setActive(true);

      provider.addUser(newUser);

      assertNotNull(provider.getUser(newUserId),
         "addUser must either add the user or throw — silent discard violates EditableAuthenticationProvider contract");
   }

   // -----------------------------------------------------------------------
   // getOrgIdFromName() — loop + getName() match
   // -----------------------------------------------------------------------

   static Stream<Arguments> orgIdFromNameCases() {
      return Stream.of(
         // known org name → default org ID
         Arguments.of(Organization.getDefaultOrganizationName(), Organization.getDefaultOrganizationID()),
         // unknown name → null (loop falls through)
         Arguments.of("__no_such_org_name__", null)
      );
   }

   @ParameterizedTest
   @MethodSource("orgIdFromNameCases")
   void getOrgIdFromName_varyingInput(String orgName, String expectedId) {
      assertEquals(expectedId, provider.getOrgIdFromName(orgName));
   }

   // -----------------------------------------------------------------------
   // getOrgNameFromID() — null-guarded ternary
   // -----------------------------------------------------------------------

   static Stream<Arguments> orgNameFromIdCases() {
      return Stream.of(
         // valid org ID → org name (getOrganization(id).name via ternary)
         Arguments.of(Organization.getDefaultOrganizationID(), Organization.getDefaultOrganizationName()),
         // unknown org ID → null (ternary null branch)
         Arguments.of("__no_such_org_id__", null)
      );
   }

   @ParameterizedTest
   @MethodSource("orgNameFromIdCases")
   void getOrgNameFromID_varyingInput(String orgId, String expectedName) {
      assertEquals(expectedName, provider.getOrgNameFromID(orgId));
   }

   // -----------------------------------------------------------------------
   // C12 Suspect override (Suspect 1 — declared in AuthenticationProviderContractTest)
   // -----------------------------------------------------------------------

   @Override
   @Test
   @Disabled("Suspect 1: authenticate(null identity) throws NPE — " +
      "VirtualAuthenticationProvider accesses userIdentity.name before null check; " +
      "Fix: add `if (userIdentity == null) return false;` before the Objects.equals call")
   void authenticate_nullIdentityID_doesNotThrow() {
      super.authenticate_nullIdentityID_doesNotThrow();
   }
}
