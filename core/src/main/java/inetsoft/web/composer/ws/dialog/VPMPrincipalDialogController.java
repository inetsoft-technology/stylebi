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
package inetsoft.web.composer.ws.dialog;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.ws.VPMPrincipalDialogModel;
import inetsoft.web.composer.ws.WorksheetController;
import inetsoft.web.composer.ws.assembly.WorksheetEventUtil;
import inetsoft.web.composer.ws.command.SetVPMPrincipalCommand;
import inetsoft.web.viewsheet.*;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Controller
public class VPMPrincipalDialogController extends WorksheetController {
   @Autowired
   public VPMPrincipalDialogController(SecurityEngine engine) {
      this.engine = engine;
   }

   @GetMapping("api/composer/ws/dialog/vpm-principal-dialog/{runtimeId}")
   @ResponseBody
   public VPMPrincipalDialogModel getVPMPrincipalModel(
      @PathVariable("runtimeId") String runtimeId, Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      final RuntimeWorksheet rws = getWorksheetEngine().getWorksheet(runtimeId, principal);
      final boolean vpmSelectable = isVpmSelectable();
      final VPMPrincipalDialogModel.Builder builder = VPMPrincipalDialogModel.builder()
         .vpmSelectable(vpmSelectable);
      final XPrincipal vpmUser = rws.getAssetQuerySandbox().getVPMUser();

      if(vpmSelectable) {
         String sessionType = "user";
         String sessionId = null;

         if(vpmUser != null) {
            final IdentityID id = vpmUser.getIdentityID();

            if("".equals(id.name)) {
               final IdentityID[] roles = vpmUser.getRoles();
               sessionType = "role";
               sessionId = roles.length > 0 ? vpmUser.getRoles()[0].convertToKey() : null;
            }
            else {
               sessionType = "user";
               sessionId = id.convertToKey();
            }
         }

         builder.sessionType(sessionType)
            .sessionId(sessionId)
            .users(engine.getUsers())
            .roles(engine.getRoles());
      }

      builder.vpmEnabled(vpmUser != null);
      return builder.build();
   }

   @LoadingMask
   @Undoable
   @InitWSExecution
   @MessageMapping("/composer/ws/dialog/vpm-principal-dialog")
   public void setVPMPrincipalModel(
      @Payload VPMPrincipalDialogModel model, CommandDispatcher commandDispatcher,
      Principal principal) throws Exception
   {
      if(!isVpmSelectable()) {
         return;
      }

      final RuntimeWorksheet rws = getRuntimeWorksheet(principal);
      final AssetQuerySandbox box = rws.getAssetQuerySandbox();
      SRPrincipal vpmPrincipal = null;

      if(model.vpmEnabled()) {
         IdentityID user = null;
         IdentityID[] roles = null;
         String[] groups = null;
         String orgID = null;

         if("user".equals(model.sessionType())) {
            user = IdentityID.getIdentityIDFromKey(model.sessionId());
            roles = XUtil.getUserRoles(new XPrincipal(user), true);
            groups = XUtil.getUserGroups(new XPrincipal(user), true);
            Principal user1 = new XPrincipal(user);
            orgID = OrganizationManager.getInstance().getUserOrgId(user1);
         }
         else if("role".equals(model.sessionType())) {
            roles = new IdentityID[] { IdentityID.getIdentityIDFromKey(model.sessionId()) };
            groups = new String[0];
         }

         vpmPrincipal = new SRPrincipal(user, roles, groups, orgID, 0L);
      }

      box.setVPMUser(vpmPrincipal);

      commandDispatcher.sendCommand(SetVPMPrincipalCommand.builder()
                                       .hasVPMPrincipal(vpmPrincipal != null)
                                       .build());
      WorksheetEventUtil.refreshWorksheet(
         rws, getWorksheetEngine(), false, true, commandDispatcher, principal);
   }

   private boolean isVpmSelectable() {
      return false;
   }

   private final SecurityEngine engine;
}
