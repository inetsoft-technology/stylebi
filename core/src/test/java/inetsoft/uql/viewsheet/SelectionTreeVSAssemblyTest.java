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
package inetsoft.uql.viewsheet;

import inetsoft.test.SreeHome;
import inetsoft.uql.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SreeHome
class SelectionTreeVSAssemblyTest {
   @Test
   void topLevelNonCompositeSelectionValue() {
      final Viewsheet vs = new Viewsheet();
      final SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(vs, "SelectionTree");
      final ColumnRef ref1 = new ColumnRef(new AttributeRef("Query", "col1"));
      final ColumnRef ref2 = new ColumnRef(new AttributeRef("Query", "col2"));
      tree.setDataRefs(new DataRef[] {ref1, ref2});

      final SelectionList list = new SelectionList();
      final SelectionList stateList = new SelectionList();

      final CompositeSelectionValue cval = new CompositeSelectionValue();
      cval.getSelectionList().addSelectionValue(new SelectionValue("child", "child"));
      list.addSelectionValue(cval);

      final String topLevelLabel = "Top-level non-composite selection";
      final SelectionValue val = new SelectionValue(topLevelLabel, topLevelLabel);
      val.setSelected(true);
      list.addSelectionValue(val);
      stateList.addSelectionValue(val);

      tree.setSelectionList(list);
      tree.setStateSelectionList(stateList);

      final Condition cond = new Condition(ref1.getDataType());
      cond.setOperation(Condition.EQUAL_TO);
      cond.addValue(topLevelLabel);
      final ConditionList expected = new ConditionList();
      expected.append(new ConditionItem(ref1, cond, 0));

      final ConditionList actual = tree.getConditionList();

      assertEquals(expected, actual);
   }
}
