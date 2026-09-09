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

import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.DefaultVariableAssembly;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
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

   /**
    * Review round 1 finding: a real table can share its name with a UserVariable that
    * Worksheet.getAllVariables() pulls in purely because some *other*, unrelated table's own
    * condition references "$(Query1)" -- no assembly named "Query1" is ever a VariableAssembly.
    * The real "Query1" table must still win.
    */
   @Test
   void aRealTableOutranksAnUnrelatedConditionVariableOfTheSameName() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new EmbeddedTableAssembly(ws, "Query1"));

      EmbeddedTableAssembly other = new EmbeddedTableAssembly(ws, "OtherTable");
      ColumnRef ref = new ColumnRef(new AttributeRef("col1"));
      Condition cond = new Condition();
      cond.addValue("$(Query1)");
      ConditionList conditionList = new ConditionList();
      conditionList.append(new ConditionItem(ref, cond, 0));
      other.setPreConditionList(conditionList);
      ws.addAssembly(other);

      // sanity check that the collision is actually set up: "Query1" really does show up as
      // a merged UserVariable name, purely via the unrelated table's condition.
      assertTrue(java.util.Arrays.stream(ws.getAllVariables())
                    .anyMatch(v -> "Query1".equals(v.getName())));

      assertEquals("Query1", resolve(ws, "Query1", null));
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

   /**
    * Bug #76555: the "{@code table} left unset, {@code columnValue} alone carries the
    * {@code "$(varName)"} reference" normalization (added for bug #76530) has been relocated out
    * of this method into {@code AssemblyPropertyService.normalizeVariableTableBinding} (the
    * AI-only wiz layer), since this class is also shared with the interactive Composer UI's own
    * property-dialog save path. {@code resolveInputTableBinding} on its own must therefore leave
    * an unset {@code table} genuinely unset/unchanged now, even when {@code columnValue} looks
    * exactly like a variable reference.
    */
   @Test
   void leavesTableUnsetWhenOnlyColumnValueLooksLikeAVariableReference() throws Exception {
      Worksheet ws = worksheetWithVariable("StartDate");

      assertNull(resolve(ws, null, "$(StartDate)"));
      assertEquals("", resolve(ws, "", "$(StartDate)"));
   }

   /**
    * Same relocation: since this method no longer normalizes the columnValue-only shape at all,
    * it also no longer validates the variable name referenced that way -- {@code table} comes
    * back unchanged regardless of whether the variable actually exists (that existence check now
    * only ever runs, downstream, once a real setter is invoked with a {@code table} that is
    * itself {@code "$(...)"}-shaped).
    */
   @Test
   void leavesTableUnsetEvenWhenTheColumnValueReferencesAVariableThatDoesNotExist()
      throws Exception
   {
      Worksheet ws = new Worksheet();

      assertNull(resolve(ws, null, "$(StartDate)"));
   }

   /**
    * The new shape only fires when {@code table} is unset -- an explicit {@code table} value
    * (even a plain, non-variable table binding) must not be silently overridden by a {@code
    * columnValue} that happens to look like a variable reference.
    */
   @Test
   void doesNotOverrideAnExplicitTableWithAColumnValueThatLooksLikeAVariable() throws Exception {
      Worksheet ws = worksheetWithVariable("StartDate");
      ws.addAssembly(new EmbeddedTableAssembly(ws, "Query1"));

      assertEquals("Query1", resolve(ws, "Query1", "$(StartDate)"));
   }

   /**
    * {@link VSInputService#resolvesToVariableBinding} is the public entry point {@code
    * AssemblyPropertyService} uses (bug #76530 part b) to decide whether an explicit {@code
    * dataInputPaneModel.variable} write is achievable. It must agree with {@code
    * resolveInputTableBinding} on a raw {@code table} value, whether literal or already
    * {@code "$(...)"}-shaped. The columnValue-only shape (bug #76555) is no longer this method's
    * concern -- callers (i.e. {@code AssemblyPropertyService}) normalize {@code table} from
    * {@code columnValue} themselves, before ever reaching this helper.
    */
   @Test
   void resolvesToVariableBindingAgreesWithTheUnderlyingResolution() {
      Worksheet ws = worksheetWithVariable("StartDate");
      ws.addAssembly(new EmbeddedTableAssembly(ws, "Query1"));

      assertFalse(VSInputService.resolvesToVariableBinding(ws, null, null, "$(StartDate)"));
      assertTrue(VSInputService.resolvesToVariableBinding(ws, null, "$(StartDate)", null));
      assertFalse(VSInputService.resolvesToVariableBinding(ws, null, "Query1", null));
      assertFalse(VSInputService.resolvesToVariableBinding(ws, null, null, null));
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
