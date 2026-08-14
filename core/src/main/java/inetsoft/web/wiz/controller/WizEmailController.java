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
import inetsoft.web.viewsheet.service.LinkUri;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/**
 * Wraps the same EmailDialogService that backs the native /api/vs/email-dialog-model endpoints
 * under wiz's own JWT-authenticated, CSRF-exempt /api/wiz namespace (see
 * WizServiceAuthenticationFilter / CSRFFilter#isWizApi). The native POST endpoint requires the
 * double-submit XSRF-TOKEN cookie/header dance; going through /api/wiz sidesteps that entirely,
 * since this namespace is CSRF-exempt and JWT-authenticated instead of session-cookie-authenticated.
 * No email-sending logic is reimplemented — emailViewsheet() already returns a MessageDialogModel
 * with success/message set appropriately (including on SMTP failure), so this stays a thin proxy.
 */
@RestController
@RequestMapping("/api/wiz")
public class WizEmailController {
   public WizEmailController(EmailDialogServiceProxy emailDialogServiceProxy) {
      this.emailDialogServiceProxy = emailDialogServiceProxy;
   }

   @GetMapping("/viewsheet/email-dialog-model")
   public EmailDialogModel getEmailDialogModel(@RequestParam("runtimeId") String runtimeId,
                                               Principal principal) throws Exception
   {
      return emailDialogServiceProxy.getEmailDialogModel(runtimeId, principal);
   }

   @PostMapping("/viewsheet/email")
   public MessageDialogModel emailViewsheet(@RequestParam("runtimeId") String runtimeId,
                                            @RequestBody EmailDialogModel value,
                                            Principal principal,
                                            @LinkUri String linkUri) throws Exception
   {
      return emailDialogServiceProxy.emailViewsheet(runtimeId, value, principal, linkUri);
   }

   private final EmailDialogServiceProxy emailDialogServiceProxy;
}
