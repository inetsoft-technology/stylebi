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
package inetsoft.sree.security;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.util.Identity;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SecurityEngine lifecycle/query scenario table:
 *  [Provider selection] security disabled uses virtual provider                  -> vprovider returned
 *  [VPM selection]      enabled security prefers vpm_provider                    -> vpm provider returned
 *  [Org filter]         getOrgUsers keeps only users from requested org          -> filtered result
 *  [Delegation]         lightweight query methods delegate to configured provider -> provider result returned
 *  [Chain optional]     composite provider exposes AuthenticationChain           -> optional present
 *  [Chain optional]     composite provider exposes AuthorizationChain            -> optional present
 *  [Identity validity]  identity lookup delegates by identity type               -> true when provider resolves it
 *
 * Test design:
 *  - use the Spring context required by SecurityEngine and chain construction
 *  - inject internal provider references through reflection for deterministic lifecycle tests
 *  - keep all comments in English per java-unit-test-generation-prompt.md
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class SecurityEngineLifecycleTest {
   private static final IdentityID USER_A = new IdentityID("alice", "orgA");
   private static final IdentityID USER_B = new IdentityID("bob", "orgB");

   @BeforeEach
   void setUp() {
      ReflectionTestUtils.setField(engine, "provider", null);
      ReflectionTestUtils.setField(engine, "vprovider", null);
      ReflectionTestUtils.setField(engine, "vpm_provider", null);
      SreeEnv.setProperty("security.enabled", "true");
   }

   @AfterEach
   void tearDown() {
      SreeEnv.setProperty("security.enabled", "false");
   }

   // [Scenario: provider selection] security disabled uses virtual provider -> virtual provider returned
   // Setup: security is disabled and a virtual provider is available
   @Test
   void getSecurityProvider_securityDisabled_returnsVirtualProvider() {
      SecurityProvider vprovider = mock(SecurityProvider.class);
      ReflectionTestUtils.setField(engine, "vprovider", vprovider);
      SreeEnv.setProperty("security.enabled", "false");

      SecurityProvider actual = engine.getSecurityProvider();

      assertSame(vprovider, actual);
   }

   // [Scenario: VPM selection] enabled security prefers VPM provider -> VPM provider returned
   // Setup: security is enabled and both default/provider and VPM provider exist
   @Test
   void getVpmSecurityProvider_vpmProviderConfigured_returnsVpmProvider() {
      SecurityProvider provider = mock(SecurityProvider.class);
      SecurityProvider vprovider = mock(SecurityProvider.class);
      SecurityProvider vpmProvider = mock(SecurityProvider.class);
      ReflectionTestUtils.setField(engine, "provider", provider);
      ReflectionTestUtils.setField(engine, "vprovider", vprovider);
      ReflectionTestUtils.setField(engine, "vpm_provider", vpmProvider);

      SecurityProvider actual = engine.getVpmSecurityProvider();

      assertSame(vpmProvider, actual);
   }

   // [Scenario: org filter] getOrgUsers keeps only users from the requested organization -> filtered result
   // Setup: provider returns users from two organizations
   @Test
   void getOrgUsers_mixedOrganizations_returnsOnlyRequestedOrgUsers() {
      SecurityProvider provider = mock(SecurityProvider.class);
      ReflectionTestUtils.setField(engine, "provider", provider);

      when(provider.getUsers()).thenReturn(new IdentityID[] { USER_A, USER_B });

      IdentityID[] actual = engine.getOrgUsers("orgA");

      assertArrayEquals(new IdentityID[] { USER_A }, actual);
   }

   // [Scenario: delegation] lightweight query methods delegate to configured provider -> provider results returned
   // Setup: provider returns deterministic values for users, grouped users, individuals, emails, and organizations
   @Test
   void queryMethods_providerConfigured_delegateToProvider() {
      SecurityProvider provider = mock(SecurityProvider.class);
      IdentityID groupId = new IdentityID("authors", "orgA");
      ReflectionTestUtils.setField(engine, "provider", provider);

      when(provider.getUsers()).thenReturn(new IdentityID[] { USER_A, USER_B });
      when(provider.getUsers(groupId)).thenReturn(new IdentityID[] { USER_A });
      when(provider.getIndividualUsers()).thenReturn(new IdentityID[] { USER_B });
      when(provider.getEmails(USER_A)).thenReturn(new String[] { "alice@example.com" });
      when(provider.getOrganizationIDs()).thenReturn(new String[] { "orgA", "orgB" });

      assertArrayEquals(new IdentityID[] { USER_A, USER_B }, engine.getUsers());
      assertArrayEquals(new IdentityID[] { USER_A }, engine.getUsers(groupId));
      assertArrayEquals(new IdentityID[] { USER_B }, engine.getIndividualUsers());
      assertArrayEquals(new String[] { "alice@example.com" }, engine.getEmails(USER_A));
      assertArrayEquals(new String[] { "orgA", "orgB" }, engine.getOrganizations());
   }

   // [Scenario: chain optional] composite provider exposes optional chain accessors -> optional present
   // Setup: engine.newChain creates a composite provider backed by real chain implementations
   @ParameterizedTest
   @MethodSource("chainAccessorCases")
   void chainAccessors_afterNewChain_returnPresentOptional(
      Function<SecurityEngine, Optional<?>> accessor)
   {
      engine.newChain();

      Optional<?> actual = accessor.apply(engine);

      assertTrue(actual.isPresent());
   }

   private static Stream<Arguments> chainAccessorCases() {
      return Stream.of(
         // authentication accessor: composite provider should expose the backing AuthenticationChain
         Arguments.of((Function<SecurityEngine, Optional<?>>) SecurityEngine::getAuthenticationChain),
         // authorization accessor: composite provider should expose the backing AuthorizationChain
         Arguments.of((Function<SecurityEngine, Optional<?>>) SecurityEngine::getAuthorizationChain)
      );
   }

   // [Scenario: identity validity] identity lookup delegates by identity type -> true when provider resolves it
   // Setup: provider resolves a user, a group, and a role by their identity ids
   @Test
   void isValidIdentity_resolvedByProvider_returnsTrueForSupportedTypes() {
      SecurityProvider provider = mock(SecurityProvider.class);
      ReflectionTestUtils.setField(engine, "provider", provider);

      User user = new User(USER_A);
      Group group = new Group(new IdentityID("authors", "orgA"), "", new String[0], new IdentityID[0]);
      Role role = new Role(new IdentityID("Designer", "orgA"), new IdentityID[0]);

      when(provider.getUser(USER_A)).thenReturn(user);
      when(provider.getGroup(group.getIdentityID())).thenReturn(group);
      when(provider.getRole(role.getIdentityID())).thenReturn(role);

      assertTrue(engine.isValidIdentity(user));
      assertTrue(engine.isValidIdentity(group));
      assertTrue(engine.isValidIdentity(role));
      Identity unknown = mock(Identity.class);
      when(unknown.getType()).thenReturn(99);
      when(unknown.getIdentityID()).thenReturn(USER_A);

      assertFalse(engine.isValidIdentity(unknown));
   }
   
   @Autowired
   private SecurityEngine engine;
}
