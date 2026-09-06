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
package inetsoft.web.viewsheet.service;

/*
 * Regression for Bug #76478: setSliderPropertyDialogModel/setComboboxPropertyDialogModel/
 * setSpinnerPropertyDialogModel/setTextInputPropertyDialogModel used to copy
 * dataInputPaneModel.getTable() straight into the assembly's tableName with no check that it
 * resolved to anything. A caller writing table:"Variables" (the Composer data-input tree's own
 * non-selectable folder label, not a real leaf value -- see getInputTablesTree) instead of the
 * required "$(varName)" form was accepted silently: isVariable() came back false, refreshVariable()
 * later matched nothing, and the value never reached the VariableTable -- while
 * InputValueService.setValue() still reported {ok:true}.
 *
 * resolveInputTableBinding() now runs before the table string is stored: it passes through a
 * "$(varName)" reference that names a real variable, passes through a real worksheet
 * table/assembly name unchanged, normalizes two unambiguous natural mistakes (a bare variable
 * name, and the "Variables" folder label paired with a columnValue naming a real variable) into
 * the "$(varName)" form the runtime requires, and otherwise throws a MessageException naming the
 * bad value instead of persisting a binding that can never resolve.
 */

import inetsoft.uql.asset.DefaultVariableAssembly;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.util.MessageException;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@WizAgentTestSupport
class VSInputTableBindingResolutionTest {
   @Test
   void passesThroughACorrectlyFormattedVariableReference() throws Exception {
      Worksheet ws = worksheetWithVariable("discountRate");

      assertEquals("$(discountRate)", resolve(ws, "$(discountRate)", "discountRate"));
   }

   @Test
   void rejectsAVariableReferenceToAVariableThatDoesNotExist() {
      Worksheet ws = new Worksheet();

      MessageException ex = assertThrows(MessageException.class,
         () -> resolve(ws, "$(discountRate)", "discountRate"));
      assertTrue(ex.getMessage().contains("discountRate"), ex.getMessage());
   }

   /** The exact reported shape: the Composer tree's "Variables" folder label, not a leaf value. */
   @Test
   void normalizesTheVariablesFolderLabelPairedWithARealVariableColumnValue() throws Exception {
      Worksheet ws = worksheetWithVariable("discountRate");

      assertEquals("$(discountRate)", resolve(ws, "Variables", "discountRate"));
   }

   @Test
   void rejectsTheVariablesFolderLabelWhenTheColumnValueIsNotARealVariable() {
      Worksheet ws = worksheetWithVariable("discountRate");

      MessageException ex = assertThrows(MessageException.class,
         () -> resolve(ws, "Variables", "notAVariable"));
      assertTrue(ex.getMessage().contains("Variables"), ex.getMessage());
   }

   @Test
   void normalizesABareVariableNameIntoTheReferenceForm() throws Exception {
      Worksheet ws = worksheetWithVariable("discountRate");

      assertEquals("$(discountRate)", resolve(ws, "discountRate", null));
   }

   @Test
   void passesThroughARealWorksheetTableNameUnchanged() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new EmbeddedTableAssembly(ws, "Query1"));

      assertEquals("Query1", resolve(ws, "Query1", "DISCOUNT"));
   }

   @Test
   void rejectsATableValueThatMatchesNothing() {
      Worksheet ws = worksheetWithVariable("discountRate");

      MessageException ex = assertThrows(MessageException.class,
         () -> resolve(ws, "TotallyUnknownTable", "discountRate"));
      assertTrue(ex.getMessage().contains("TotallyUnknownTable"), ex.getMessage());
   }

   @Test
   void leavesAnUnboundInputUntouched() throws Exception {
      Worksheet ws = new Worksheet();

      assertNull(resolve(ws, null, null));
      assertEquals("", resolve(ws, "", null));
   }

   // ── harness ───────────────────────────────────────────────────────────────

   private static Worksheet worksheetWithVariable(String name) {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new DefaultVariableAssembly(ws, name));
      return ws;
   }

   private static String resolve(Worksheet ws, String table, String columnValue)
      throws Exception
   {
      Method method = VSInputService.class.getDeclaredMethod(
         "resolveInputTableBinding", Worksheet.class,
         inetsoft.uql.viewsheet.Viewsheet.class, String.class, String.class);
      method.setAccessible(true);

      try {
         return (String) method.invoke(null, ws, null, table, columnValue);
      }
      catch(InvocationTargetException e) {
         if(e.getCause() instanceof MessageException messageException) {
            throw messageException;
         }

         throw e;
      }
   }
}
