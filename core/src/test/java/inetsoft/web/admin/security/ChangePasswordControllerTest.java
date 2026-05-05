/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.web.admin.security;

/*
 * Test strategy
 *
 * Class type: behavioral-orchestration controller — ChangePasswordController has real
 * in-controller logic in both changePassword() and verifyOldPassword():
 *   - changePassword(): validates password strength (static), resolves editable
 *     authentication provider, casts user to FSUser (throws if not), sets password,
 *     and audits the action.
 *   - verifyOldPassword(): resolves authentication provider, delegates to
 *     authc.authenticate().
 *
 * Coverage scope (4 cases in 2 groups):
 *
 * --- changePassword() ---
 *
 *  [weak password]             IdentityService.validatePasswordStrength() throws
 *                              → controller propagates the exception unchanged
 *  [non-editable provider]     no editable provider found in chain
 *                              → IllegalStateException propagated
 *  [FSUser success]            editable provider returns FSUser → SUtil.setPassword() called;
 *                              Audit.auditAction() called
 *
 * --- verifyOldPassword() ---
 *
 *  [delegates to authenticate] authc.authenticate() is called with a DefaultTicket built
 *                              from the principal name and the request password
 *
 * Static singletons (IdentityService, SUtil, Audit) are intercepted with
 * Mockito.mockStatic() using lenient() where possible.
 */

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class ChangePasswordControllerTest {

   @Mock private SecurityProvider securityProvider;
   @Mock private AuthenticationChain authenticationChain;
   @Mock private EditableAuthenticationProvider editableProvider;
   @Mock private FSUser fsUser;
   @Mock private ActionRecord actionRecord;
   @Mock private Audit audit;
   @Mock private Principal principal;

   private ChangePasswordController controller;

   private MockedStatic<IdentityService> identityServiceStatic;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<Audit> auditStatic;

   @BeforeEach
   void setUp() {
      controller = new ChangePasswordController(securityProvider);

      identityServiceStatic = mockStatic(IdentityService.class, withSettings().lenient());
      sUtilStatic = mockStatic(SUtil.class, withSettings().lenient());
      auditStatic = mockStatic(Audit.class, withSettings().lenient());

      auditStatic.when(Audit::getInstance).thenReturn(audit);
      lenient().when(principal.getName()).thenReturn("alice~;~host-org");
   }

   @AfterEach
   void tearDown() {
      identityServiceStatic.close();
      sUtilStatic.close();
      auditStatic.close();
   }

   // -------------------------------------------------------------------------
   // changePassword()
   // -------------------------------------------------------------------------

   // [weak password] validatePasswordStrength() throws → exception propagates
   @Test
   void changePassword_weakPassword_propagatesException() {
      identityServiceStatic.when(() -> IdentityService.validatePasswordStrength(anyString()))
         .thenThrow(new IllegalArgumentException("Password too weak"));
      ChangePasswordRequest request = ChangePasswordRequest.builder().password("weak").build();

      assertThrows(IllegalArgumentException.class,
         () -> controller.changePassword(request, principal));
   }

   // [non-editable provider] no editable provider found → IllegalStateException
   @Test
   void changePassword_nonEditableProvider_throwsIllegalState() {
      when(securityProvider.getAuthenticationProvider()).thenReturn(authenticationChain);
      // chain streams zero editable providers with this user
      when(authenticationChain.stream()).thenReturn(Stream.empty());
      ChangePasswordRequest request = ChangePasswordRequest.builder().password("Admin1234!").build();

      assertThrows(IllegalStateException.class,
         () -> controller.changePassword(request, principal));
   }

   // [FSUser success] editable provider returns FSUser → setPassword and audit called
   @Test
   void changePassword_fsUser_setsPasswordAndAudits() {
      IdentityID pId = IdentityID.getIdentityIDFromKey("alice~;~host-org");

      when(securityProvider.getAuthenticationProvider()).thenReturn(authenticationChain);
      when(authenticationChain.stream()).thenReturn(Stream.of(editableProvider));
      when(editableProvider.getUser(pId)).thenReturn(fsUser);
      lenient().when(fsUser.getName()).thenReturn("alice");
      sUtilStatic.when(() -> SUtil.getActionRecord(any(Principal.class), anyString(), anyString(), anyString()))
         .thenReturn(actionRecord);

      ChangePasswordRequest request = ChangePasswordRequest.builder().password("Admin1234!").build();
      controller.changePassword(request, principal);

      sUtilStatic.verify(() -> SUtil.setPassword(eq(fsUser), eq("Admin1234!")));
      verify(editableProvider).addUser(fsUser);
      verify(audit).auditAction(eq(actionRecord), eq(principal));
   }

   // -------------------------------------------------------------------------
   // verifyOldPassword()
   // -------------------------------------------------------------------------

   // [delegates to authenticate] authenticate() is called with the correct ticket
   @Test
   void verifyOldPassword_delegatesToAuthenticate() {
      IdentityID pId = IdentityID.getIdentityIDFromKey("alice~;~host-org");
      when(securityProvider.getAuthenticationProvider()).thenReturn(editableProvider);
      when(editableProvider.authenticate(eq(pId), any(DefaultTicket.class))).thenReturn(true);

      ChangePasswordRequest request = ChangePasswordRequest.builder().password("Admin1234!").build();
      boolean result = controller.verifyOldPassword(request, principal);

      assertTrue(result);
      verify(editableProvider).authenticate(eq(pId), any(DefaultTicket.class));
   }
}
