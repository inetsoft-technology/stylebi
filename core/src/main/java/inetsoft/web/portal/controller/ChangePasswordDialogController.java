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
package inetsoft.web.portal.controller;

import inetsoft.sree.ClientInfo;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.portal.model.ChangePasswordDialogModel;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.sql.Timestamp;

/**
 * Controller that provides a REST endpoint for the change password dialog
 *
 * @since 12.3
 */
@RestController
public class ChangePasswordDialogController {
   @GetMapping(value = "/api/portal/change-password-dialog-model")
   public ChangePasswordDialogModel getChangePasswordDialogModel(
      Principal principal) throws Exception
   {
      ChangePasswordDialogModel model = new ChangePasswordDialogModel();
      model.setUserName(IdentityID.getIdentityIDFromKey(principal.getName()));
      return model;
   }

   @PostMapping(value = "/api/portal/change-password-dialog-model")
   public boolean setChangePasswordDialogModel(
      @RequestBody ChangePasswordDialogModel model,
      HttpServletRequest request, Principal principal) throws Exception
   {
      Catalog catalog = Catalog.getCatalog();
      String actionName = ActionRecord.ACTION_NAME_EDIT;
      String objectType = ActionRecord.OBJECT_TYPE_PASSWORD;
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      IdentityID objectName = model.getUserName();
      String recordObjectName = objectName == null ? null : objectName.getName();
      ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal), actionName,
                                                   recordObjectName, objectType, actionTimestamp,
                                                   ActionRecord.ACTION_STATUS_FAILURE, null);
      Principal principal0 = authenticate(request, model.getUserName(),
                                          model.getOldPassword());

      if(principal0 == null) {
         String error = catalog.getString("viewer.changePassword.incorrect");
         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
         actionRecord.setActionError(error);
         throw new SRSecurityException(error);
      }

      try {
         SecurityEngine engine = SecurityEngine.getSecurity();
         engine.changePassword(principal0, model.getNewPassword());
         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
         return true;
      }
      catch(Exception e) {
         LOG.error("Failed to change the password of user: " + objectName, e);
         String error = catalog.getString("viewer.changePassword.failed", e.getMessage());
         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
         actionRecord.setActionError(error);
         return false;
      }
      finally {
         Audit.getInstance().auditAction(actionRecord, principal);
      }
   }


   /**
    * Authenticate a principal.
    */
   private Principal authenticate(HttpServletRequest req, IdentityID userName,  String password) {
      return AuthenticationService.getInstance().authenticate(
         new ClientInfo(userName, Tool.getRemoteAddr(req)),
         new DefaultTicket(userName, password));
   }

   private static final Logger LOG = LoggerFactory.getLogger(RepositoryTreeController.class);
}

