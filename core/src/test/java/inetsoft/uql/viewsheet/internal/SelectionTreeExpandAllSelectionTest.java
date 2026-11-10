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
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.viewsheet.*;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80667: toggling a selection tree's expandAll is a display-only change, so it must not be
 * reported as an input-data change (which resets the selection).
 */
@Tag("core")
@WizAgentTestSupport
class SelectionTreeExpandAllSelectionTest {
   private SelectionTreeVSAssembly tree;
   private CompositeSelectionValue selection;

   @BeforeEach
   void setUp() {
      Viewsheet vs = new Viewsheet();
      tree = new SelectionTreeVSAssembly(vs, "Tree1");
      vs.addAssembly(tree);

      selection = new CompositeSelectionValue();
      SelectionList list = new SelectionList();
      SelectionValue value = new SelectionValue("East", "East");
      value.setSelected(true);
      list.addSelectionValue(value);
      selection.setSelectionList(list);
      tree.setStateCompositeSelectionValue(selection);
      tree.setCompositeSelectionValue(selection);
   }

   private SelectionTreeVSAssemblyInfo copyInfo() {
      return (SelectionTreeVSAssemblyInfo) tree.getVSAssemblyInfo().clone();
   }

   @Test
   void expandAllToggleKeepsSelectionAndFlagsViewChange() {
      SelectionTreeVSAssemblyInfo info = copyInfo();
      info.setExpandAll(!info.isExpandAll());

      int hint = tree.setVSAssemblyInfo(info);

      assertEquals(VSAssembly.VIEW_CHANGED, hint & VSAssembly.VIEW_CHANGED);
      assertEquals(0, hint & VSAssembly.INPUT_DATA_CHANGED);
      assertEquals(info.isExpandAll(),
                   ((SelectionTreeVSAssemblyInfo) tree.getVSAssemblyInfo()).isExpandAll());
      assertNotNull(tree.getStateCompositeSelectionValue());
      assertNotNull(tree.getCompositeSelectionValue());
      assertEquals(1, tree.getCompositeSelectionValue().getSelectionList()
         .getSelectionValueCount());
   }

   @Test
   void sameExpandAllValueChangesNothing() {
      SelectionTreeVSAssemblyInfo info = copyInfo();

      int hint = tree.setVSAssemblyInfo(info);

      assertEquals(0, hint & VSAssembly.INPUT_DATA_CHANGED);
      assertNotNull(tree.getStateCompositeSelectionValue());
   }

   @Test
   void modeChangeStillResetsSelection() {
      SelectionTreeVSAssemblyInfo info = copyInfo();
      info.setMode(info.getMode() == SelectionTreeVSAssemblyInfo.ID ?
                      SelectionTreeVSAssemblyInfo.COLUMN : SelectionTreeVSAssemblyInfo.ID);

      int hint = tree.setVSAssemblyInfo(info);

      assertEquals(VSAssembly.INPUT_DATA_CHANGED, hint & VSAssembly.INPUT_DATA_CHANGED);
      assertNull(tree.getStateCompositeSelectionValue());
   }
}
