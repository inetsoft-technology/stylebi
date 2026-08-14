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
package inetsoft.web.wiz.controller;

import inetsoft.web.viewsheet.controller.dialog.EmailDialogServiceProxy;
import inetsoft.web.viewsheet.model.dialog.EmailDialogModel;
import inetsoft.web.viewsheet.model.dialog.MessageDialogModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("core")
class WizEmailControllerTest {

   @Test
   void getEmailDialogModelDelegatesToProxy() throws Exception {
      EmailDialogServiceProxy proxy = mock(EmailDialogServiceProxy.class);
      Principal principal = mock(Principal.class);
      EmailDialogModel model = mock(EmailDialogModel.class);
      when(proxy.getEmailDialogModel("rt1", principal)).thenReturn(model);

      WizEmailController ctrl = new WizEmailController(proxy);

      assertSame(model, ctrl.getEmailDialogModel("rt1", principal));
   }

   @Test
   void emailViewsheetDelegatesToProxyWithRuntimeIdBodyPrincipalAndLinkUri() throws Exception {
      EmailDialogServiceProxy proxy = mock(EmailDialogServiceProxy.class);
      Principal principal = mock(Principal.class);
      EmailDialogModel body = mock(EmailDialogModel.class);
      MessageDialogModel expected = mock(MessageDialogModel.class);
      when(proxy.emailViewsheet("rt1", body, principal, "http://host/link")).thenReturn(expected);

      WizEmailController ctrl = new WizEmailController(proxy);

      assertSame(expected, ctrl.emailViewsheet("rt1", body, principal, "http://host/link"));
   }

   @Test
   void emailViewsheetReturnsTheProxysFailureResultUnchangedOnSmtpFailure() throws Exception {
      // EmailDialogService.emailViewsheet() already catches SMTP/send failures internally and
      // returns a MessageDialogModel with success=false rather than throwing -- this controller
      // must not swallow or reinterpret that, just pass it straight through.
      EmailDialogServiceProxy proxy = mock(EmailDialogServiceProxy.class);
      Principal principal = mock(Principal.class);
      EmailDialogModel body = mock(EmailDialogModel.class);
      MessageDialogModel failure = mock(MessageDialogModel.class);
      when(failure.success()).thenReturn(false);
      when(proxy.emailViewsheet("rt1", body, principal, "http://host/link")).thenReturn(failure);

      WizEmailController ctrl = new WizEmailController(proxy);

      MessageDialogModel result = ctrl.emailViewsheet("rt1", body, principal, "http://host/link");
      assertSame(failure, result);
      assertFalse(result.success());
   }
}
