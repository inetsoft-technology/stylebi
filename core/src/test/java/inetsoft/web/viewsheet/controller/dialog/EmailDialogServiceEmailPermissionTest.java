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
package inetsoft.web.viewsheet.controller.dialog;

import inetsoft.sree.security.*;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.model.dialog.EmailDialogModel;
import inetsoft.web.viewsheet.service.VSEmailService;
import inetsoft.web.admin.schedule.ScheduleService;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.web.viewsheet.model.dialog.MessageDialogModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #81680: the viewer Email dialog must enforce the Email toolbar action on the server.
 */
@Tag("core")
class EmailDialogServiceEmailPermissionTest {
   @Test
   void emailRefusedWithoutEmailToolbarAction() throws Exception {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      VSEmailService emailService = mock(VSEmailService.class);
      Principal principal = mock(Principal.class);
      when(principal.getName()).thenReturn("un~;~org");
      when(securityEngine.checkPermission(
         any(), eq(ResourceType.VIEWSHEET_TOOLBAR_ACTION), eq("Email"), eq(ResourceAction.READ)))
         .thenReturn(false);
      // Export granted: Email must still be judged on its own action
      when(securityEngine.checkPermission(
         any(), eq(ResourceType.VIEWSHEET_TOOLBAR_ACTION), eq("Export"), eq(ResourceAction.READ)))
         .thenReturn(true);

      EmailDialogService service = new EmailDialogService(
         viewsheetService, mock(ScheduleService.class), securityEngine, emailService);

      MessageDialogModel result =
         service.emailViewsheet("R", mock(EmailDialogModel.class), principal, "http://x/");

      assertFalse(result.success());
      assertEquals(MessageCommand.Type.ERROR, result.type());
      verifyNoInteractions(emailService);
      verifyNoInteractions(viewsheetService);
   }
}
