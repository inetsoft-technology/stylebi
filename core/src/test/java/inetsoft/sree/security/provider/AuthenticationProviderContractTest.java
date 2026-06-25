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

import inetsoft.sree.security.*;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public abstract class AuthenticationProviderContractTest<T extends AuthenticationProvider> {

   protected T provider;

   /** Subclass creates and returns a fully initialized provider. */
   protected abstract T createProvider();

   /** Returns the IdentityID of a user pre-loaded by createProvider(). */
   protected abstract IdentityID validUserId();

   /** Returns the plaintext password for validUserId(). */
   protected abstract String validPassword();

   /** Returns the orgId the valid user belongs to. */
   protected abstract String validOrgId();

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

   // C1: correct credential → authenticate returns true
   @Test
   void authenticate_validCredential_returnsTrue() {
      DefaultTicket ticket = new DefaultTicket(validUserId(), validPassword());
      assertTrue(provider.authenticate(validUserId(), ticket),
         "Valid credentials must authenticate successfully");
   }

   // C2: wrong password → authenticate returns false (does NOT throw)
   @Test
   void authenticate_wrongPassword_returnsFalse() {
      DefaultTicket ticket = new DefaultTicket(validUserId(), "WRONG_PASSWORD");
      assertFalse(provider.authenticate(validUserId(), ticket),
         "Wrong password must return false, not throw");
   }

   // C3: non-existent user → getUser returns null
   @Test
   void getUser_nonExistentUser_returnsNull() {
      IdentityID ghost = new IdentityID("ghost_user_xyz", validOrgId());
      assertNull(provider.getUser(ghost),
         "Unknown user must return null");
   }

   // C4: valid user's orgId matches expected
   @Test
   void getUser_existingUser_hasCorrectOrgId() {
      User user = provider.getUser(validUserId());
      assertNotNull(user, "Valid user must not be null");
      assertEquals(validOrgId(), user.getOrganizationID(),
         "User's orgId must match the org it was created in");
   }

   // C5: valid user's roles are loaded (roles array must not be null)
   @Test
   void getUser_existingUser_rolesLoaded() {
      User user = provider.getUser(validUserId());
      assertNotNull(user, "Valid user must not be null");
      assertNotNull(user.getRoles(), "User roles array must not be null");
   }
}
