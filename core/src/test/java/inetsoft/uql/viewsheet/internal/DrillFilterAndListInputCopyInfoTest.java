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

import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81892: comparing an assembly info with its own clone must report no change, otherwise the
 * viewsheet onLoad diff treats every assembly named in the script as changed on every event.
 * Covers DrillFilterInfo equality and the list input sortType/edataDown comparison.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DrillFilterAndListInputCopyInfoTest {
   private static ConditionList cond(String col, String value) {
      ConditionList list = new ConditionList();
      ColumnRef ref = new ColumnRef(new AttributeRef(null, col));
      ref.setDataType(XSchema.STRING);
      Condition condition = new Condition(XSchema.STRING);
      condition.setOperation(XCondition.EQUAL_TO);
      condition.addValue(value);
      list.append(new ConditionItem(ref, condition, 0));
      return list;
   }

   private static DrillFilterInfo drill(String field, String value) {
      DrillFilterInfo info = new DrillFilterInfo();
      info.setDrillFilterConditionList(field, cond("state", value));
      return info;
   }

   @Test
   void drillFilterCloneEqualsOriginal() {
      DrillFilterInfo info = drill("city", "NJ");
      DrillFilterInfo clone = info.clone();

      assertNotSame(info, clone);
      assertEquals(info, clone);
      assertEquals(info.hashCode(), clone.hashCode());
      assertTrue(Tool.equals(info, clone));
   }

   @Test
   void emptyDrillFiltersAreEqual() {
      assertEquals(new DrillFilterInfo(), new DrillFilterInfo().clone());
      assertEquals(new DrillFilterInfo().hashCode(), new DrillFilterInfo().hashCode());
   }

   @Test
   void differentDrillConditionsDiffer() {
      assertNotEquals(drill("city", "NJ"), drill("city", "NY"));
      assertNotEquals(drill("city", "NJ"), drill("zip", "NJ"));
      assertNotEquals(drill("city", "NJ"), new DrillFilterInfo());
   }

   @Test
   void chartAndCrosstabSelfCopyReportsNoChange() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      ((ChartVSAssemblyInfo) chart.getVSAssemblyInfo()).setDrillFilterInfo(drill("city", "NJ"));
      ChartVSAssembly copy = (ChartVSAssembly) chart.clone();
      assertEquals(0, copy.setVSAssemblyInfo(((VSAssembly) chart.clone()).getVSAssemblyInfo()));

      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, "Crosstab1");
      ((CrosstabVSAssemblyInfo) crosstab.getVSAssemblyInfo())
         .setDrillFilterInfo(drill("city", "NJ"));
      CrosstabVSAssembly copy2 = (CrosstabVSAssembly) crosstab.clone();
      assertEquals(0, copy2.setVSAssemblyInfo(((VSAssembly) crosstab.clone()).getVSAssemblyInfo()));
   }

   @Test
   void realDrillFilterChangeStillReportsBindingChanged() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      ((ChartVSAssemblyInfo) chart.getVSAssemblyInfo()).setDrillFilterInfo(drill("city", "NJ"));
      ChartVSAssembly other = (ChartVSAssembly) chart.clone();
      ((ChartVSAssemblyInfo) other.getVSAssemblyInfo()).setDrillFilterInfo(drill("city", "NY"));

      int hint = ((VSAssembly) chart.clone()).setVSAssemblyInfo(other.getVSAssemblyInfo());
      assertNotEquals(0, hint & VSAssembly.BINDING_CHANGED);
   }

   private static void assertListInput(ListInputVSAssembly assembly) {
      ListInputVSAssembly self = (ListInputVSAssembly) assembly.clone();
      assertEquals(0, self.setVSAssemblyInfo(((VSAssembly) assembly.clone()).getVSAssemblyInfo()),
                   assembly.getClass().getSimpleName() + " self-clone must report no change");

      ListInputVSAssembly changed = (ListInputVSAssembly) assembly.clone();
      ((ListInputVSAssemblyInfo) changed.getVSAssemblyInfo()).setSortTypeValue(XConstants.SORT_DESC);
      int hint = ((VSAssembly) assembly.clone()).setVSAssemblyInfo(changed.getVSAssemblyInfo());
      assertNotEquals(0, hint & VSAssembly.INPUT_DATA_CHANGED,
                      "a real sortType change must still be reported");
   }

   @Test
   void radioButtonSelfCopyReportsNoChange() {
      assertListInput(new RadioButtonVSAssembly(new Viewsheet(), "Radio1"));
   }

   @Test
   void comboBoxSelfCopyReportsNoChange() {
      assertListInput(new ComboBoxVSAssembly(new Viewsheet(), "Combo1"));
   }

   @Test
   void checkBoxSelfCopyReportsNoChange() {
      assertListInput(new CheckBoxVSAssembly(new Viewsheet(), "Check1"));
   }
}
