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
package inetsoft.web.admin.security.user;

/*
 * Test strategy
 *
 * Class type: pure-delegation controller — GroupController contains no in-controller logic;
 * all three methods delegate entirely to UserTreeService.
 *
 * Coverage scope:
 *   [createGroup]    delegates to service.createGroup(); returns result unchanged
 *   [editGroup]      delegates to service.editGroup() with parsed IdentityID
 *   [getGroupModel]  delegates to service.getGroupModel() with parsed IdentityID; returns result unchanged
 */

import inetsoft.sree.security.IdentityID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class GroupControllerTest {

   @Mock private UserTreeService userTreeService;
   @Mock private EditGroupPaneModel groupModel;
   @Mock private Principal principal;

   private GroupController controller;

   @BeforeEach
   void setUp() {
      controller = new GroupController(userTreeService);
   }

   // -------------------------------------------------------------------------
   // createGroup()
   // -------------------------------------------------------------------------

   @Test
   void createGroup_delegatesToService() {
      CreateEntityRequest request = CreateEntityRequest.builder().parentGroup("parent").build();
      when(userTreeService.createGroup("myProvider", "parent", principal)).thenReturn(groupModel);

      EditGroupPaneModel result = controller.createGroup("myProvider", request, principal);

      assertSame(groupModel, result);
      verify(userTreeService).createGroup("myProvider", "parent", principal);
   }

   // -------------------------------------------------------------------------
   // editGroup()
   // -------------------------------------------------------------------------

   @Test
   void editGroup_delegatesToService() throws Exception {
      IdentityID groupID = new IdentityID("admins", "host-org");
      String groupKey = groupID.convertToKey();

      controller.editGroup("myProvider", groupKey, groupModel, principal);

      verify(userTreeService).editGroup("myProvider", groupID, groupModel, principal);
   }

   // -------------------------------------------------------------------------
   // getGroupModel()
   // -------------------------------------------------------------------------

   @Test
   void getGroupModel_delegatesToService() {
      IdentityID groupID = new IdentityID("admins", "host-org");
      String groupKey = groupID.convertToKey();
      when(userTreeService.getGroupModel("myProvider", groupID, principal)).thenReturn(groupModel);

      EditGroupPaneModel result = controller.getGroupModel("myProvider", groupKey, principal);

      assertSame(groupModel, result);
      verify(userTreeService).getGroupModel("myProvider", groupID, principal);
   }
}
