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
package inetsoft.web.composer.vs.dialog;

import inetsoft.util.Tool;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.factory.RemainingPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Controller
public class ModernizeDialogController {
   @Autowired
   public ModernizeDialogController(ModernizeDialogServiceProxy modernizeDialogServiceProxy) {
      this.modernizeDialogServiceProxy = modernizeDialogServiceProxy;
   }

   @RequestMapping(
      value = "/api/composer/vs/modernize-dialog-model/**",
      method = RequestMethod.GET
   )
   @ResponseBody
   public ModernizeDialogModel getModel(@RemainingPath String runtimeId, Principal principal)
      throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return modernizeDialogServiceProxy.getModel(runtimeId, principal);
   }

   @RequestMapping(
      value = "/api/composer/vs/modernize-dialog-model/check-name/{runtimeId}",
      method = RequestMethod.POST
   )
   @ResponseBody
   public ModernCopyNameCheck checkName(
      @PathVariable("runtimeId") String runtimeId, @RequestBody ModernCopyRequest request,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return modernizeDialogServiceProxy.checkName(runtimeId, request, principal);
   }

   private final ModernizeDialogServiceProxy modernizeDialogServiceProxy;
}
