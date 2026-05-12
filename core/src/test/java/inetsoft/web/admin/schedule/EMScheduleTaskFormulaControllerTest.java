/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

/*
 * Test strategy
 *
 * EMScheduleTaskFormulaController is a pure-delegation controller: all four methods
 * pass through to ScheduleTaskFormulaService with no additional logic.
 *
 * Coverage scope:
 *   [getScriptDefinition]              delegates to formulaService.getScriptDefinition()
 *   [testScheduleParameterExpression]  delegates to formulaService.testScheduleParameterExpression()
 *   [getFunctions]                     delegates to formulaService.getFunctions()
 *   [getOperationTree]                 delegates to formulaService.getOperationTree()
 */

import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.web.admin.schedule.model.TestTaskParameterExpressionRequest;
import inetsoft.web.composer.model.TreeNodeModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class EMScheduleTaskFormulaControllerTest {

   @Mock private ScheduleTaskFormulaService formulaService;
   @Mock private ObjectNode scriptDefinition;
   @Mock private TreeNodeModel treeNode;

   private EMScheduleTaskFormulaController controller;

   @BeforeEach
   void setUp() {
      controller = new EMScheduleTaskFormulaController(formulaService);
   }

   // [delegation] delegates to formulaService.getScriptDefinition() and returns result unchanged
   @Test
   void getScriptDefinition_delegatesToService() throws Exception {
      when(formulaService.getScriptDefinition()).thenReturn(scriptDefinition);

      ObjectNode result = controller.getScriptDefinition();

      assertSame(scriptDefinition, result);
   }

   // [delegation] delegates to formulaService.testScheduleParameterExpression()
   @Test
   void testScheduleParameterExpression_delegatesToService() {
      TestTaskParameterExpressionRequest req = mock(TestTaskParameterExpressionRequest.class);
      when(req.expression()).thenReturn("1 + 1");
      when(formulaService.testScheduleParameterExpression("1 + 1")).thenReturn("2");

      String result = controller.testScheduleParameterExpression(req);

      assertEquals("2", result);
   }

   // [delegation] delegates to formulaService.getFunctions() and returns result unchanged
   @Test
   void getFunctions_delegatesToService() {
      when(formulaService.getFunctions()).thenReturn(treeNode);

      TreeNodeModel result = controller.getFunctions();

      assertSame(treeNode, result);
   }

   // [delegation] delegates to formulaService.getOperationTree() and returns result unchanged
   @Test
   void getOperationTree_delegatesToService() {
      when(formulaService.getOperationTree()).thenReturn(treeNode);

      TreeNodeModel result = controller.getOperationTree();

      assertSame(treeNode, result);
   }
}
