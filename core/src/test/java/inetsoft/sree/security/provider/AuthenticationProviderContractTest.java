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
package inetsoft.sree.security.provider;

/*
 * Intent vs implementation suspects
 *
 * [Suspect 1] authenticate(null userIdentity, ticket)
 *             intent : return false (contract C12)
 *             actual : at least VirtualAuthenticationProvider accesses userIdentity.name
 *                      without a null guard → NullPointerException at line ~84
 *             see    : VirtualAuthenticationProviderTest.authenticate_nullIdentityID_doesNotThrow
 *                      which overrides this test with @Disabled
 *
 * [Suspect 2] authenticate(sameNameDifferentOrg, ticket)
 *             intent : return false — same username in a different org is a distinct identity
 *             actual : providers that only check IdentityID.name (not orgId) would return true
 *             test   : C15 is skipped (assumeTrue) when anotherOrgId() returns null;
 *                      subclasses that support multi-org must implement anotherOrgId()
 */

/*
 * Cases deferred — require integration context:
 *
 * authenticate() → SRPrincipal / AuthenticationFailureException path
 *   -> SecurityEngine wraps Provider; covered by SecurityEngine integration tests (M4/M5)
 * concurrency / cache behaviour
 *   -> needs running SecurityEngine with cache; integration test scope
 * findIdentity() deep integration
 *   -> traverses multiple layers; integration test scope
 */

import inetsoft.sree.security.*;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public abstract class AuthenticationProviderContractTest<T extends AuthenticationProvider> {

   protected T provider;

   // -----------------------------------------------------------------------
   // Template methods — required
   // -----------------------------------------------------------------------

   /** Creates and returns a fully initialized provider with one active user pre-loaded. */
   protected abstract T createProvider();

   /** IdentityID of the active user created by createProvider(). */
   protected abstract IdentityID validUserId();

   /** Plaintext password for validUserId(). */
   protected abstract String validPassword();

   /** orgId the valid user belongs to. */
   protected abstract String validOrgId();

   // -----------------------------------------------------------------------
   // Template methods — optional capabilities (return null = not supported)
   // -----------------------------------------------------------------------

   /**
    * IdentityID of an inactive user (active=false) pre-loaded by createProvider().
    * Return null if the provider does not support inactive users — C10 will be skipped.
    */
   protected IdentityID inactiveUserId() { return null; }

   /**
    * A second orgId distinct from validOrgId().
    * Return null if the provider is single-org — C15 will be skipped.
    */
   protected String anotherOrgId() { return null; }

   // -----------------------------------------------------------------------
   // Lifecycle
   // -----------------------------------------------------------------------

   @BeforeEach
   void setUpProvider() {
      provider = createProvider();
   }

   @AfterEach
   void tearDownProvider() {
      if(provider != null) {
         provider.tearDown();
         provider = null;
      }
   }

   // -----------------------------------------------------------------------
   // C1-C2: authenticate — happy path and wrong password
   // -----------------------------------------------------------------------

   // C1: correct credential → true
   @Test
   void authenticate_validCredential_returnsTrue() {
      DefaultTicket ticket = new DefaultTicket(validUserId(), validPassword());
      assertTrue(provider.authenticate(validUserId(), ticket),
         "Valid credentials must authenticate successfully");
   }

   // C2: wrong password → false (must NOT throw)
   @Test
   void authenticate_wrongPassword_returnsFalse() {
      DefaultTicket ticket = new DefaultTicket(validUserId(), "WRONG_PASSWORD_XYZ");
      assertFalse(provider.authenticate(validUserId(), ticket),
         "Wrong password must return false without throwing");
   }

   // -----------------------------------------------------------------------
   // C3-C5: getUser — basic structure
   // -----------------------------------------------------------------------

   // C3: unknown user → null
   @Test
   void getUser_nonExistentUser_returnsNull() {
      IdentityID ghost = new IdentityID("__ghost_user_xyz__", validOrgId());
      assertNull(provider.getUser(ghost), "Unknown user must return null");
   }

   // C4: user's orgId matches expected
   @Test
   void getUser_existingUser_hasCorrectOrgId() {
      User user = provider.getUser(validUserId());
      assertNotNull(user, "Valid user must not be null");
      assertEquals(validOrgId(), user.getOrganizationID(),
         "User's orgId must match the org it was created in");
   }

   // C4b: user's name matches the queried IdentityID.name
   @Test
   void getUser_existingUser_nameMatchesQuery() {
      User user = provider.getUser(validUserId());
      assertNotNull(user, "Valid user must not be null");
      assertEquals(validUserId().name, user.getName(),
         "User's name must match the username portion of the queried IdentityID");
   }

   // C4c: active user has isActive() == true
   @Test
   void getUser_existingUser_isActive() {
      User user = provider.getUser(validUserId());
      assertNotNull(user, "Valid user must not be null");
      assertTrue(user.isActive(), "Pre-loaded user must have isActive() == true");
   }

   // C5: roles array is never null
   @Test
   void getUser_existingUser_rolesNotNull() {
      User user = provider.getUser(validUserId());
      assertNotNull(user, "Valid user must not be null");
      assertNotNull(user.getRoles(), "User roles array must not be null");
   }

   // C5b: groups array is never null
   @Test
   void getUser_existingUser_groupsNotNull() {
      User user = provider.getUser(validUserId());
      assertNotNull(user, "Valid user must not be null");
      assertNotNull(user.getGroups(), "User groups array must not be null");
   }

   // -----------------------------------------------------------------------
   // C6-C12: authenticate — special and boundary inputs
   // -----------------------------------------------------------------------

   // C6 [Risk 3]: null credential → false, must NOT throw
   @Test
   void authenticate_nullCredential_returnsFalse() {
      assertDoesNotThrow(
         () -> assertFalse(provider.authenticate(validUserId(), null),
            "null credential must return false"),
         "null credential must not throw an exception");
   }

   // C7 [Risk 3]: non-DefaultTicket credential (plain String) → must NOT throw
   // Providers whose interface accepts Object must handle foreign credential types gracefully.
   @Test
   void authenticate_nonDefaultTicketCredential_doesNotThrow() {
      String rawCredential = validUserId().name + ":" + validPassword();
      assertDoesNotThrow(
         () -> provider.authenticate(validUserId(), rawCredential),
         "String credential must not throw ClassCastException — provider must parse or reject gracefully");
   }

   // C8 [Risk 3]: userIdentity and ticket name mismatch → false
   // Prevents identity-confusion attacks where ticket belongs to a different user.
   @Test
   void authenticate_identityAndTicketNameMismatch_returnsFalse() {
      IdentityID otherUser = new IdentityID("__other_user__", validOrgId());
      DefaultTicket mismatchedTicket = new DefaultTicket(otherUser, validPassword());
      assertFalse(provider.authenticate(validUserId(), mismatchedTicket),
         "authenticate must return false when userIdentity differs from ticket's identity");
   }

   // C9 [Risk 3]: non-existent user → false, must NOT throw
   // Some providers (LDAP, DB) throw NamingException/SQLException instead — this is a defect.
   @Test
   void authenticate_nonExistentUser_returnsFalseWithoutThrowing() {
      IdentityID ghost = new IdentityID("__no_such_user__", validOrgId());
      DefaultTicket ticket = new DefaultTicket(ghost, validPassword());
      assertDoesNotThrow(
         () -> assertFalse(provider.authenticate(ghost, ticket),
            "non-existent user must return false"),
         "authenticate for non-existent user must not throw");
   }

   // C10 [Risk 3]: inactive user → false  (skipped if provider does not support inactive users)
   @Test
   void authenticate_inactiveUser_returnsFalse() {
      assumeTrue(inactiveUserId() != null,
         "Provider does not pre-load an inactive user — C10 skipped");
      DefaultTicket ticket = new DefaultTicket(inactiveUserId(), validPassword());
      assertFalse(provider.authenticate(inactiveUserId(), ticket),
         "Inactive user must not authenticate even with correct credentials");
   }

   // C11 [Risk 2]: empty password string → false
   @Test
   void authenticate_emptyPassword_returnsFalse() {
      DefaultTicket ticket = new DefaultTicket(validUserId(), "");
      assertFalse(provider.authenticate(validUserId(), ticket),
         "Empty password must be rejected");
   }

   // C12 [Risk 3 Suspect — see file header]: null userIdentity → must NOT throw
   // Known defect: VirtualAuthenticationProvider accesses userIdentity.name without null guard.
   @Test
   void authenticate_nullIdentityID_doesNotThrow() {
      DefaultTicket ticket = new DefaultTicket(validUserId(), validPassword());
      assertDoesNotThrow(
         () -> provider.authenticate(null, ticket),
         "authenticate(null identity) must not throw — return false or handle gracefully");
   }

   // -----------------------------------------------------------------------
   // C13-C14: getOrganization
   // -----------------------------------------------------------------------

   // C13 [Risk 2]: valid orgId → non-null Organization
   @Test
   void getOrganization_validOrgId_returnsNonNull() {
      Organization org = provider.getOrganization(validOrgId());
      assertNotNull(org, "getOrganization(validOrgId) must return a non-null Organization");
   }

   // C14 [Risk 2]: unknown orgId → null
   @Test
   void getOrganization_unknownOrgId_returnsNull() {
      Organization org = provider.getOrganization("__no_such_org_id__");
      assertNull(org, "getOrganization for an unknown orgId must return null");
   }

   // -----------------------------------------------------------------------
   // C15: multi-org isolation  (skipped if provider does not support multi-org)
   // -----------------------------------------------------------------------

   // C15 [Risk 3]: same username, different orgId → false
   // A user from orgA must NOT authenticate as a user from orgB.
   @Test
   void authenticate_sameNameDifferentOrg_returnsFalse() {
      assumeTrue(anotherOrgId() != null,
         "Provider does not support multi-org — C15 skipped");
      IdentityID crossOrgId = new IdentityID(validUserId().name, anotherOrgId());
      DefaultTicket ticket = new DefaultTicket(crossOrgId, validPassword());
      assertFalse(provider.authenticate(crossOrgId, ticket),
         "User from orgA must not authenticate under a different orgId");
   }
}
