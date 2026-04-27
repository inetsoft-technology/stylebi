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
package inetsoft.web.admin.schedule;

import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.web.admin.schedule.model.TestTaskParameterExpressionRequest;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.springframework.web.bind.annotation.*;

@RestController
public class EMScheduleTaskFormulaController {
   public EMScheduleTaskFormulaController(ScheduleTaskFormulaService scheduleTaskFormulaService) {
      this.scheduleTaskFormulaService = scheduleTaskFormulaService;
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/tasks",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/schedule/parameters/formula/scriptDefinition")
   public ObjectNode getScriptDefinition() throws Exception {
      return scheduleTaskFormulaService.getScriptDefinition();
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/tasks",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping(
      value = "/api/em/schedule/parameters/formula/test-script")
   public String testScheduleParameterExpression(@RequestBody TestTaskParameterExpressionRequest script) {
      return scheduleTaskFormulaService.testScheduleParameterExpression(script.expression());
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/tasks",
         actions = ResourceAction.ACCESS
      )
   )
   @RequestMapping(value = "/api/em/schedule/parameters/formula/function", method=RequestMethod.GET)
   public TreeNodeModel getFunctions() {
      return scheduleTaskFormulaService.getFunctions();
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/tasks",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/schedule/parameters/formula/operationTree")
   public TreeNodeModel getOperationTree() {
      return this.scheduleTaskFormulaService.getOperationTree();
   }

   private final ScheduleTaskFormulaService scheduleTaskFormulaService;
}
