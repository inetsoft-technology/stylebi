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
package inetsoft.web.admin.security.user;

/*
 * Test strategy
 *
 * Bug #77101: editOrganization() returns the user messages of the save. The messages are kept in
 * a thread-local list, so the controller must drop anything an earlier request left on the pooled
 * thread before the save, and consume the messages of the save even when it fails.
 *
 * Coverage scope:
 *   [stale message]        a message left on the thread before the save is not returned
 *   [save message]         a message added by the save is returned and consumed
 *   [stale + save message] only the message of the save is returned
 *   [save throws]          the exception propagates and no message is left on the thread
 */

import inetsoft.sree.security.*;
import inetsoft.util.Tool;
import inetsoft.web.admin.security.AuthenticationProviderService;
import inetsoft.web.admin.security.IdentityService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class OrganizationControllerTest {

   @Mock private UserTreeService userTreeService;
   @Mock private IdentityService identityService;
   @Mock private AuthenticationProviderService authenticationProviderService;
   @Mock private SecurityEngine securityEngine;
   @Mock private SecurityProvider securityProvider;
   @Mock private OrganizationManager organizationManager;
   @Mock private EditOrganizationPaneModel model;
   @Mock private Principal principal;

   private MockedStatic<OrganizationManager> organizationManagerStatic;
   private OrganizationController controller;

   @BeforeEach
   void setUp() {
      Tool.clearUserMessage();
      organizationManagerStatic = mockStatic(OrganizationManager.class);
      organizationManagerStatic.when(OrganizationManager::getInstance).thenReturn(organizationManager);
      when(organizationManager.getCurrentOrgID()).thenReturn("host-org");
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      when(securityProvider.getOrganization("host-org")).thenReturn(new Organization("host-org"));

      controller = new OrganizationController(
         userTreeService, identityService, authenticationProviderService, securityEngine);
   }

   @AfterEach
   void tearDown() {
      organizationManagerStatic.close();
      Tool.clearUserMessage();
   }

   @Test
   void staleMessageOnThread_notReturned() throws Exception {
      Tool.addUserMessage("stale message of an earlier request");

      String result = controller.editOrganization(null, model, "provider", principal);

      assertEquals("", result);
      assertNull(Tool.getUserMessage());
   }

   @Test
   void saveAddsMessage_returnedAndConsumed() throws Exception {
      doAnswer(inv -> {
         Tool.addUserMessage("Cannot delete yourself.");
         return null;
      }).when(userTreeService).editOrganization(any(), any(), any());

      String result = controller.editOrganization(null, model, "provider", principal);

      assertEquals("Cannot delete yourself.", result);
      assertNull(Tool.getUserMessage());
   }

   @Test
   void staleAndSaveMessage_onlySaveMessageReturned() throws Exception {
      Tool.addUserMessage("stale message of an earlier request");
      doAnswer(inv -> {
         Tool.addUserMessage("Cannot delete yourself.");
         return null;
      }).when(userTreeService).editOrganization(any(), any(), any());

      String result = controller.editOrganization(null, model, "provider", principal);

      assertEquals("Cannot delete yourself.", result);
   }

   @Test
   void saveThrows_exceptionPropagatesAndNoMessageLeftOnThread() throws Exception {
      Tool.addUserMessage("stale message of an earlier request");
      doAnswer(inv -> {
         Tool.addUserMessage("Failed to clean up the data of the removed member bob.");
         throw new IllegalStateException("save failed");
      }).when(userTreeService).editOrganization(any(), any(), any());

      IllegalStateException ex = assertThrows(
         IllegalStateException.class,
         () -> controller.editOrganization(null, model, "provider", principal));

      assertEquals("save failed", ex.getMessage());
      assertNull(Tool.getUserMessage());
      verify(organizationManager, never()).reset();
   }
}
