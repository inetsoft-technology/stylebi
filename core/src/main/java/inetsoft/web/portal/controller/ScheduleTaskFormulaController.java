/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.portal.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.web.admin.schedule.ScheduleTaskFormulaService;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.portal.model.database.StringWrapper;
import org.springframework.web.bind.annotation.*;

@RestController
public class ScheduleTaskFormulaController {
   public ScheduleTaskFormulaController(ScheduleTaskFormulaService scheduleTaskFormulaService) {
      this.scheduleTaskFormulaService = scheduleTaskFormulaService;
   }

   @GetMapping("/api/portal/schedule/parameters/formula/scriptDefinition")
   public ObjectNode getScriptDefinition() throws Exception {
      return scheduleTaskFormulaService.getScriptDefinition();
   }

   @PostMapping(
      value = "/api/portal/schedule/parameters/formula/test-script")
   public StringWrapper testScheduleParameterExpression(@RequestBody StringWrapper script) {
      StringWrapper result = new StringWrapper();
      String testError = scheduleTaskFormulaService.testScheduleParameterExpression(script.getBody());
      result.setBody(testError);

      return result;
   }

   @RequestMapping(value = "/api/portal/schedule/parameters/formula/function", method=RequestMethod.GET)
   public TreeNodeModel getFunctions() {
      return scheduleTaskFormulaService.getFunctions();
   }

   @GetMapping("/api/portal/schedule/parameters/formula/operationTree")
   public TreeNodeModel getOperationTree() {
      return this.scheduleTaskFormulaService.getOperationTree();
   }

   private final ScheduleTaskFormulaService scheduleTaskFormulaService;
}
