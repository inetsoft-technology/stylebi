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
package inetsoft.web.admin.content.repository;

import inetsoft.util.Tool;
import inetsoft.web.admin.content.repository.model.DeleteTreeNodesRequest;
import inetsoft.web.admin.content.repository.model.TreeNodeInfo;
import inetsoft.web.admin.security.ConnectionStatus;
import org.junit.jupiter.api.*;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77939: the EM repository tree delete reports the user messages left by the delete, e.g. a
 * permission it may not have removed, as a "warning:" status. The client shows that status, any
 * other status asks to confirm the delete and sends it again with force.
 */
@Tag("core")
class RepositoryObjectControllerDeleteWarningTest {
   @BeforeEach
   void setUp() {
      Tool.clearUserMessage();
      service = mock(RepositoryObjectService.class);
      controller = new RepositoryObjectController(service, null, null);
   }

   @AfterEach
   void tearDown() {
      Tool.clearUserMessage();
   }

   @Test
   void messageLeftByDelete_returnedAsWarningAndDrained() throws Exception {
      when(service.deleteNodes(any(), any(), anyBoolean(), anyBoolean())).thenAnswer(inv -> {
         Tool.addUserWarning("left at its path");
         return null;
      });

      ConnectionStatus status = controller.deleteRepositoryEntry(request(), mock(Principal.class));

      assertNotNull(status);
      assertEquals("warning:left at its path", status.getStatus());
      assertNull(Tool.getUserMessage(), "the message was not drained");
   }

   @Test
   void noMessage_returnsNothing() throws Exception {
      // left by an earlier request on this pooled thread, not returned
      Tool.addUserMessage("stale");

      ConnectionStatus status = controller.deleteRepositoryEntry(request(), mock(Principal.class));

      assertNull(status);
   }

   // a confirmation prompt is returned as before, the confirmed delete reports the message
   @Test
   void otherStatus_returnedUnchanged() throws Exception {
      ConnectionStatus confirm = new ConnectionStatus("The item has dependencies.");
      when(service.deleteNodes(any(), any(), anyBoolean(), anyBoolean())).thenAnswer(inv -> {
         Tool.addUserWarning("left at its path");
         return confirm;
      });

      ConnectionStatus status = controller.deleteRepositoryEntry(request(), mock(Principal.class));

      assertSame(confirm, status);
   }

   private static DeleteTreeNodesRequest request() {
      return DeleteTreeNodesRequest.builder()
         .nodes(new TreeNodeInfo[] {
            TreeNodeInfo.builder().label("vs").path("vs").type(1).build() })
         .force(false)
         .permanent(false)
         .build();
   }

   private RepositoryObjectService service;
   private RepositoryObjectController controller;
}
