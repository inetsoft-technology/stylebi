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
package inetsoft.web.composer.vs.dialog;

import inetsoft.util.Tool;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Controller that provides the REST endpoints for the text property dialog.
 *
 * @since 13.4
 */
@Controller
public class PropertyDialogController {
   @Autowired
   public PropertyDialogController(RuntimeViewsheetRef runtimeViewsheetRef,
                                   PropertyDialogServiceProxy propertyDialogServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.propertyDialogServiceProxy = propertyDialogServiceProxy;
   }

   @RequestMapping(
      value = "api/composer/vs/check-script/**",
      method = RequestMethod.POST)
   @ResponseBody
   public String checkScript(
      @RequestBody String[] scripts, @RemainingPath() String runtimeId,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return propertyDialogServiceProxy.checkScript(runtimeId, scripts, principal);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final PropertyDialogServiceProxy propertyDialogServiceProxy;
}
