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
package inetsoft.web.composer.ws.joins;

import inetsoft.util.Tool;
import inetsoft.web.composer.ws.WorksheetController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.ArrayList;

@Controller
public class JoinCompatibilityController extends WorksheetController {

   public JoinCompatibilityController(JoinCompatibilityServiceProxy joinCompatibilityServiceProxy)
   {
      this.joinCompatibilityServiceProxy = joinCompatibilityServiceProxy;
   }

   @RequestMapping(
      value = "api/composer/worksheet/join/compatible-insertion-tables/{runtimeId}",
      method = RequestMethod.GET)
   @ResponseBody
   public ArrayList<String> getCompatibleInsertionTables(
      @PathVariable("runtimeId") String runtimeId, @RequestParam("joinTable") String joinTableName,
      Principal principal) throws Exception
   {
      runtimeId = Tool.byteDecode(runtimeId);
      return new ArrayList<>(joinCompatibilityServiceProxy.getCompatibleInsertionTables(runtimeId, joinTableName, principal));
   }

   private final JoinCompatibilityServiceProxy joinCompatibilityServiceProxy;
}
