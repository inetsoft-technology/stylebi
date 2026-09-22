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
package inetsoft.web.admin.ai;

import inetsoft.sree.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers the fallback {@code SecurityEngine.getSecurityProvider()} takes when security is not
 * initialized. A virtual-backed provider discards writes silently and reads back a canned
 * {@link Permission}, which makes a write-then-verify apply report a spurious rolled-back; these
 * tests pin the fail-fast that replaces it.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class SecurityProviderGuardTest {
   @Mock private SecurityEngine securityEngine;
   @Mock private SecurityProvider realProvider;
   @Mock private AuthorizationProvider realAuthorization;
   @Mock private EditableAuthenticationProvider editableAuthentication;
   private MockedStatic<OrganizationManager> orgManagerStatic;

   @BeforeEach void setUp() {
      // VirtualAuthorizationProvider's constructor reads the current org.
      OrganizationManager orgManager = mock(OrganizationManager.class);
      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("host-org");
   }

   @AfterEach void tearDown() {
      orgManagerStatic.close();
   }

   // -------------------------------------------------------------------------
   // authorization
   // -------------------------------------------------------------------------

   @Test void rejectsAVirtualBackedAuthorizationProvider() {
      SecurityProvider virtual = virtualBacked();
      when(securityEngine.getSecurityProvider()).thenReturn(virtual);
      when(securityEngine.isSecurityEnabled()).thenReturn(false);

      var ex = assertThrows(SecurityProviderGuard.SecurityNotInitializedException.class,
                            () -> SecurityProviderGuard.requireWritableAuthorization(securityEngine));

      assertTrue(ex.getMessage().contains("security is not initialized"), ex.getMessage());
      assertTrue(ex.getMessage().contains("permission changes cannot be applied"), ex.getMessage());
      // The message must name the state that produced the fallback, so the operator knows which
      // branch to fix rather than guessing.
      assertTrue(ex.getMessage().contains("security.enabled=false"), ex.getMessage());
      assertTrue(ex.getMessage().contains("VirtualAuthorizationProvider"), ex.getMessage());
   }

   /**
    * The property reading {@code "true"} is not sufficient -- {@code getSecurityProvider()} takes
    * the same fallback when {@code provider} is null, which is exactly the state that made the
    * original report conclude a restart had ruled the fallback out.
    */
   @Test void rejectsTheVirtualProviderEvenWhenSecurityEnabledReadsTrue() {
      SecurityProvider virtual = virtualBacked();
      when(securityEngine.getSecurityProvider()).thenReturn(virtual);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);

      var ex = assertThrows(SecurityProviderGuard.SecurityNotInitializedException.class,
                            () -> SecurityProviderGuard.requireWritableAuthorization(securityEngine));

      assertTrue(ex.getMessage().contains("security.enabled=true"), ex.getMessage());
   }

   @Test void rejectsANullProvider() {
      when(securityEngine.getSecurityProvider()).thenReturn(null);
      when(securityEngine.isSecurityEnabled()).thenReturn(false);

      assertThrows(SecurityProviderGuard.SecurityNotInitializedException.class,
                   () -> SecurityProviderGuard.requireWritableAuthorization(securityEngine));
   }

   @Test void acceptsARealAuthorizationProvider() {
      when(securityEngine.getSecurityProvider()).thenReturn(realProvider);
      when(realProvider.getAuthorizationProvider()).thenReturn(realAuthorization);

      assertSame(realProvider, SecurityProviderGuard.requireWritableAuthorization(securityEngine));
   }

   // -------------------------------------------------------------------------
   // authentication
   // -------------------------------------------------------------------------

   /**
    * The trap this guard exists for: unlike its authorization counterpart,
    * {@code VirtualAuthenticationProvider} extends {@code AbstractEditableAuthenticationProvider},
    * so {@code SUtil.getEditableAuthenticationProvider} resolves it non-null and an
    * editability-only check would wave the fallback straight through.
    */
   @Test void rejectsAVirtualAuthenticationProviderEvenThoughItIsEditable() {
      SecurityProvider virtual = new TestSecurityProvider(
         mock(VirtualAuthenticationProvider.class), realAuthorization);
      assertInstanceOf(EditableAuthenticationProvider.class, virtual.getAuthenticationProvider(),
                       "fixture must reproduce the editable-but-virtual shape");
      when(securityEngine.getSecurityProvider()).thenReturn(virtual);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);

      var ex = assertThrows(SecurityProviderGuard.SecurityNotInitializedException.class,
                            () -> SecurityProviderGuard.requireEditableAuthentication(securityEngine));

      assertTrue(ex.getMessage().contains("identity changes cannot be applied"), ex.getMessage());
   }

   /**
    * A database- or LDAP-only chain is fully initialized and simply read-only, so it must not be
    * reported as "security is not initialized" (nor, at the controller, as a retryable 503).
    */
   @Test void reportsAReadOnlyChainDistinctlyFromAnUninitializedOne() {
      SecurityProvider readOnly = new TestSecurityProvider(
         mock(AuthenticationProvider.class), realAuthorization);
      when(securityEngine.getSecurityProvider()).thenReturn(readOnly);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);

      var ex = assertThrows(SecurityProviderGuard.ReadOnlyAuthenticationException.class,
                            () -> SecurityProviderGuard.requireEditableAuthentication(securityEngine));

      assertTrue(ex.getMessage().contains("read-only"), ex.getMessage());
      assertTrue(ex.getMessage().contains("retrying will not help"), ex.getMessage());
      assertFalse(ex.getMessage().contains("security is not initialized"), ex.getMessage());
   }

   @Test void rejectsANullProviderForIdentityChanges() {
      when(securityEngine.getSecurityProvider()).thenReturn(null);
      when(securityEngine.isSecurityEnabled()).thenReturn(false);

      assertThrows(SecurityProviderGuard.SecurityNotInitializedException.class,
                   () -> SecurityProviderGuard.requireEditableAuthentication(securityEngine));
   }

   @Test void acceptsAnEditableAuthenticationProvider() {
      SecurityProvider editable =
         new TestSecurityProvider(editableAuthentication, realAuthorization);
      when(securityEngine.getSecurityProvider()).thenReturn(editable);

      assertSame(editableAuthentication,
                 SecurityProviderGuard.requireEditableAuthentication(securityEngine));
   }

   /** The provider shape {@code getSecurityProvider()} falls back to when security is not up. */
   private static SecurityProvider virtualBacked() {
      return new TestSecurityProvider(mock(AuthenticationProvider.class),
                                      new VirtualAuthorizationProvider());
   }

   /**
    * Stands in for {@code CompositeSecurityProvider}, whose factory reaches for the Spring context
    * to build a check-permission strategy. {@code AbstractSecurityProvider} is what both the guard
    * and {@code SUtil.getEditableAuthenticationProvider} actually key off, and it delegates
    * straight through, so this is the same resolution without the container.
    */
   private static final class TestSecurityProvider extends AbstractSecurityProvider {
      TestSecurityProvider(AuthenticationProvider authentication,
                           AuthorizationProvider authorization)
      {
         super(authentication, authorization);
      }
   }
}
