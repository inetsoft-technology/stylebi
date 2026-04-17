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

import inetsoft.sree.ClientInfo;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SecurityEngine authentication scenario table:
 *  [Internal principal] provider is null                                  -> internal principal returned
 *  [Authenticated user] provider authenticates and resolves real user     -> principal cached and user remapped
 *  [Auth failed]         provider rejects the credential                  -> null returned
 *  [Active session]      cached principal with matching identity          -> active user
 *  [Login validity]      login.user requires cache presence               -> valid only when cached
 *  [Logout event]        loggedOut removes cached login principal         -> invalid after logout
 *  [Login event]         successful login event is forwarded to listeners -> listener invoked
 *
 * Test design:
 *  - keep the Spring context required by SecurityEngine initialization
 *  - inject provider/users/listeners through reflection for deterministic authentication tests
 *  - keep all comments in English per java-unit-test-generation-prompt.md
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class SecurityEngineAuthenticationTest {
   private static final IdentityID USER_ID = new IdentityID("Alice", "org1");

   @BeforeEach
   void setUp() {
      ReflectionTestUtils.setField(engine, "provider", null);
      ReflectionTestUtils.setField(engine, "vprovider", null);
      ReflectionTestUtils.setField(engine, "vpm_provider", null);
      ReflectionTestUtils.setField(engine, "users", new ConcurrentHashMap<ClientInfo, SRPrincipal>());

      @SuppressWarnings("unchecked")
      Set<LoginListener> listeners =
         (Set<LoginListener>) ReflectionTestUtils.getField(engine, "loginListeners");
      listeners.clear();

      SreeEnv.setProperty("security.enabled", "true");
   }

   @AfterEach
   void tearDown() {
      SreeEnv.setProperty("security.enabled", "false");
   }

   // [Scenario: internal principal] provider is null -> internal destination principal is returned
   // Setup: authentication runs without any configured provider
   @Test
   void authenticate_providerIsNull_returnsInternalPrincipal() {
      ClientInfo user = clientInfo(USER_ID);

      Principal principal = engine.authenticate(user, "secret", null);

      assertInstanceOf(DestinationUserNameProviderPrincipal.class, principal);
      SRPrincipal srPrincipal = (SRPrincipal) principal;
      assertEquals("true", srPrincipal.getProperty("__internal__"));
      assertEquals(Organization.getDefaultOrganizationID(), srPrincipal.getOrgId());
   }

   // [Scenario: authenticated user] provider authenticates and resolves a real user -> cached principal returned
   // Setup: provider accepts the credential, returns roles/groups, and remaps the user identity
   @Test
   void authenticate_successfulCredential_cachesPrincipalAndRemapsUserIdentity() {
      SecurityProvider provider = mock(SecurityProvider.class);
      ClientInfo user = clientInfo(USER_ID);
      IdentityID canonicalId = new IdentityID("alice", "org1");
      User realUser = new User(canonicalId, new String[0], new String[] { "authors" },
         new IdentityID[] { new IdentityID("Designer", "org1") }, "", "", true, "Alice Alias");

      when(provider.authenticate(USER_ID, "secret")).thenReturn(true);
      when(provider.getUser(USER_ID)).thenReturn(realUser);
      when(provider.getRoles(canonicalId)).thenReturn(new IdentityID[] {
         new IdentityID("Designer", "org1")
      });
      when(provider.getUserGroups(canonicalId)).thenReturn(new String[] { "authors" });

      Principal principal = engine.authenticate(user, "secret", provider);

      assertInstanceOf(DestinationUserNameProviderPrincipal.class, principal);
      SRPrincipal srPrincipal = (SRPrincipal) principal;
      assertEquals(canonicalId, user.getUserIdentity());
      assertEquals("true", srPrincipal.getProperty("__internal__"));
      assertTrue(engine.isActiveUser(srPrincipal));
      assertTrue(engine.isValidUser(srPrincipal));
      assertArrayEquals(new String[] { "authors" }, srPrincipal.getGroups());
      assertEquals("Alice Alias", srPrincipal.getAlias());

      @SuppressWarnings("unchecked")
      Map<ClientInfo, SRPrincipal> users =
         (Map<ClientInfo, SRPrincipal>) ReflectionTestUtils.getField(engine, "users");
      assertSame(srPrincipal, users.get(user.getCacheKey()));
   }

   // [Scenario: auth failed] provider rejects the credential -> null returned and no cache entry created
   // Setup: provider.authenticate returns false
   @Test
   void authenticate_failedCredential_returnsNull() {
      SecurityProvider provider = mock(SecurityProvider.class);
      ClientInfo user = clientInfo(USER_ID);

      when(provider.authenticate(USER_ID, "secret")).thenReturn(false);

      Principal principal = engine.authenticate(user, "secret", provider);

      assertNull(principal);

      @SuppressWarnings("unchecked")
      Map<ClientInfo, SRPrincipal> users =
         (Map<ClientInfo, SRPrincipal>) ReflectionTestUtils.getField(engine, "users");
      assertTrue(users.isEmpty());
   }

   // [Scenario: active session] cached principal with matching user identity -> active user
   // Setup: the principal is already stored in the engine cache
   @Test
   void isActiveUser_cachedPrincipalWithMatchingIdentity_returnsTrue() {
      SRPrincipal principal = cachedLoginPrincipal(USER_ID, 11L);

      assertTrue(engine.isActiveUser(principal));
      assertFalse(engine.isActiveUser(new SRPrincipal(new IdentityID("bob", "org1"))));
   }

   // [Scenario: login validity] non-login principals are always valid; login principals require cache presence
   // Setup: compare a non-login principal against a login principal that is not cached
   @Test
   void isValidUser_nonLoginPrincipalIsAlwaysValid_loginPrincipalRequiresCache() {
      SRPrincipal nonLoginPrincipal = new SRPrincipal(USER_ID, new IdentityID[0], new String[0],
         USER_ID.getOrgID(), 12L);
      SRPrincipal uncachedLoginPrincipal = new SRPrincipal(USER_ID, new IdentityID[0], new String[0],
         USER_ID.getOrgID(), 13L);
      uncachedLoginPrincipal.setProperty("login.user", "true");

      assertTrue(engine.isValidUser(nonLoginPrincipal));
      assertFalse(engine.isValidUser(uncachedLoginPrincipal));
   }

   // [Scenario: logout event] loggedOut removes cached login principal -> session becomes invalid
   // Setup: a cached login principal is logged out through the session event hook
   @Test
   void loggedOut_cachedPrincipal_removesUserFromCache() {
      SRPrincipal principal = cachedLoginPrincipal(USER_ID, 14L);

      engine.loggedOut(new SessionLoggedOutEvent(this, principal));

      assertFalse(engine.isActiveUser(principal));
      assertFalse(engine.isValidUser(principal));
   }

   // [Scenario: login event] successful login event is forwarded to registered listeners
   // Setup: a listener is registered and a principal is emitted through fireLoginEvent
   @Test
   void fireLoginEvent_registeredListener_receivesEvent() {
      LoginListener listener = mock(LoginListener.class);
      SRPrincipal principal = new SRPrincipal(USER_ID, new IdentityID[0], new String[0],
         USER_ID.getOrgID(), 15L);

      engine.addLoginListener(listener);

      engine.fireLoginEvent(principal);

      verify(listener).userLogin(argThat(event -> event.getPrincipal() == principal));
   }

   private ClientInfo clientInfo(IdentityID userId) {
      return new ClientInfo(userId, "127.0.0.1", "session-1");
   }

   private SRPrincipal cachedLoginPrincipal(IdentityID userId, long secureId) {
      SRPrincipal principal = new SRPrincipal(userId, new IdentityID[0], new String[0],
         userId.getOrgID(), secureId);
      principal.setProperty("login.user", "true");
      principal.setIgnoreLogin(false);

      @SuppressWarnings("unchecked")
      Map<ClientInfo, SRPrincipal> users =
         (Map<ClientInfo, SRPrincipal>) ReflectionTestUtils.getField(engine, "users");
      users.put(principal.getUser().getCacheKey(), principal);
      return principal;
   }

   @Autowired
   private SecurityEngine engine;
}
