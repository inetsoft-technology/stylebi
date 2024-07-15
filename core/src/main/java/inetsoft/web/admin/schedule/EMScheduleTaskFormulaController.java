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
package inetsoft.web.admin.schedule;

import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.web.admin.schedule.model.TestTaskParameterExpressionRequest;
import inetsoft.web.composer.model.TreeNodeModel;
import org.springframework.web.bind.annotation.*;

@RestController
public class EMScheduleTaskFormulaController {
   public EMScheduleTaskFormulaController(ScheduleTaskFormulaService scheduleTaskFormulaService) {
      this.scheduleTaskFormulaService = scheduleTaskFormulaService;
   }

   @GetMapping("/api/em/schedule/parameters/formula/scriptDefinition")
   public ObjectNode getScriptDefinition() throws Exception {
      return scheduleTaskFormulaService.getScriptDefinition();
   }

   @PostMapping(
      value = "/api/em/schedule/parameters/formula/test-script")
   public String testScheduleParameterExpression(@RequestBody TestTaskParameterExpressionRequest script) {
      return scheduleTaskFormulaService.testScheduleParameterExpression(script.expression());
   }

   @RequestMapping(value = "/api/em/schedule/parameters/formula/function", method=RequestMethod.GET)
   public TreeNodeModel getFunctions() {
      return scheduleTaskFormulaService.getFunctions();
   }

   @GetMapping("/api/em/schedule/parameters/formula/operationTree")
   public TreeNodeModel getOperationTree() {
      return this.scheduleTaskFormulaService.getOperationTree();
   }

   private final ScheduleTaskFormulaService scheduleTaskFormulaService;
}
