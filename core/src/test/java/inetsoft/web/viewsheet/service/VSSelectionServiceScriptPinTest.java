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

import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import inetsoft.test.*;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79349: an explicit, initiator-driven selection apply ({@code set_selection},
 * {@code clear_selection}, "Unselect All") must clear a selection list's stale script-assigned
 * pin ({@code scriptSelectedValues}). Otherwise {@code SelectionListVSAssembly}'s own
 * {@code getSelectedObjects()}/{@code getConditionList()} -- invoked as a normal part of the very
 * same apply's selection-list refresh ({@code VSSelectionService.executeSelection()}) -- calls
 * {@code checkScriptSelectedValues()}, which unconditionally re-applies the stale pin and silently
 * reverts the selection that was just applied, within the same request.
 *
 * <p>Exercises the private {@code VSSelectionService.applySelection(SelectionVSAssembly,
 * SelectionList, boolean)} helper directly via reflection, the same pattern
 * {@link VSSelectionServiceNeighborPathTest} uses for a different private method on the same
 * class.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSSelectionServiceScriptPinTest {
   /**
    * An initiator apply (set_selection's main apply, clear_selection's empty apply, or
    * unselectAll's per-assembly clear -- all of which call this helper with
    * {@code isInitiator=true}) clears the stale script pin.
    */
   @Test
   void initiatorApplyClearsTheScriptPin() throws Exception {
      SelectionListVSAssembly assembly = createAssembly();
      assembly.setScriptSelectedValues(new Object[] { "USA West" });

      applySelection(assembly, selectionList(selectedValue("USA East")), true);

      assertNull(assembly.getScriptSelectedValues());
   }

   /**
    * A neighbour's own Association-driven refresh ({@code refreshNeighborSelections}, which calls
    * this helper with {@code isInitiator=false}) must NOT clear an unrelated neighbour's own
    * script pin.
    */
   @Test
   void neighborRefreshDoesNotClearAnotherAssemblysPin() throws Exception {
      SelectionListVSAssembly assembly = createAssembly();
      assembly.setScriptSelectedValues(new Object[] { "USA West" });

      applySelection(assembly, selectionList(selectedValue("USA East")), false);

      assertArrayEquals(new Object[] { "USA West" }, assembly.getScriptSelectedValues());
   }

   /**
    * End-to-end: once the pin is cleared by an initiator apply, the assembly's own
    * {@code getSelectedObjects()} (the exact call whose {@code checkScriptSelectedValues()} used
    * to silently revert the selection, per the bug's root cause) reflects the explicitly-applied
    * value, not the stale script value.
    */
   @Test
   void selectedObjectsReflectTheExplicitApplyNotTheStaleScriptValue() throws Exception {
      SelectionListVSAssembly assembly = createAssembly();
      assembly.setSelectionList(fullSelectionList());
      assembly.setScriptSelectedValues(new Object[] { "USA West" });

      applySelection(assembly, selectionList(selectedValue("USA East")), true);

      assertEquals(List.of("USA East"), assembly.getSelectedObjects());
   }

   private static SelectionListVSAssembly createAssembly() {
      Viewsheet vs = new Viewsheet();
      SelectionListVSAssembly assembly = new SelectionListVSAssembly(vs, "SL1");
      assembly.setDataRef(new ColumnRef(new AttributeRef(null, "STATE")));
      vs.addAssembly(assembly);
      return assembly;
   }

   private static SelectionList fullSelectionList() {
      SelectionList list = new SelectionList();
      list.setSelectionValues(new SelectionValue[] {
         new SelectionValue("USA East", "USA East"),
         new SelectionValue("USA West", "USA West")
      });
      return list;
   }

   private static SelectionList selectionList(SelectionValue... values) {
      SelectionList list = new SelectionList();
      list.setSelectionValues(values);
      return list;
   }

   private static SelectionValue selectedValue(String value) {
      SelectionValue sval = new SelectionValue(value, value);
      sval.setSelected(true);
      return sval;
   }

   private static void applySelection(SelectionVSAssembly assembly, SelectionList slist,
                                      boolean isInitiator)
      throws Exception
   {
      Method apply = VSSelectionService.class.getDeclaredMethod(
         "applySelection", SelectionVSAssembly.class, SelectionList.class, boolean.class);
      apply.setAccessible(true);
      apply.invoke(new VSSelectionService(null, null, null, null), assembly, slist, isInitiator);
   }
}
