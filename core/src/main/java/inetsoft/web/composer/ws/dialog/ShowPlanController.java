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

import inetsoft.report.composition.QueryTreeModel;
import inetsoft.util.Tool;
import inetsoft.web.composer.ws.WorksheetController;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
public class ShowPlanController extends WorksheetController {

   public ShowPlanController(ShowPlanServiceProxy showPlanServiceProxy) {
      this.showPlanServiceProxy = showPlanServiceProxy;
   }

   @RequestMapping(value = "api/composer/ws/dialog/show-plan/{runtimeid}", method = RequestMethod.GET)
   public QueryTreeModel.QueryNode showPlan(
      @PathVariable("runtimeid") String runtimeId,
      @RequestParam("table") String tname,
      Principal principal) throws Exception
   {
      return showPlanServiceProxy.showPlan(Tool.byteDecode(runtimeId), tname, principal);

   }

   private ShowPlanServiceProxy showPlanServiceProxy;
}
