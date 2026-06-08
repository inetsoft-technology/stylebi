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

package inetsoft.report.filter;

import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.report.lens.DataSetTable;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.VSFieldValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static inetsoft.test.XTableUtil.date;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class DCMergeDatePartFilterTest {
   @Test
   public void testSerialize() throws Exception {
      DataSet dataSet = new DefaultDataSet(new Object[][]{
         { "col1", "col2", "col3" },
         { "a", date("2021-01-03"), 3 },
         { "a", date("2021-01-05"), 5 },
         { "b", date("2021-01-10"), 10 },
         { "b", date("2021-01-24"), 24 },
         { "c", date("2021-01-24"), 24 },
         });
      DataSetTable base = new DataSetTable(dataSet);
      VSDimensionRef col2Ref = new VSDimensionRef();
      col2Ref.setDataRef(new AttributeRef("col2"));
      VSDimensionRef col3Ref = new VSDimensionRef();
      col3Ref.setDataRef(new AttributeRef("col3"));
      DCMergeDatePartFilter originalTable = new DCMergeDatePartFilter(base, Collections.singletonList(col2Ref),
                                                                      col3Ref, null, null);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(DCMergeDatePartFilter.class, deserializedTable.getClass());
   }

   // Bug #75351: for a WeekOfYear date-comparison part cell whose displayed week
   // maps to a different actual week across the period's year, VSDataSet must use
   // the equivalence cell so the drill-to-detail condition targets the same week
   // the axis/data represent (matching the crosstab path).
   @Test
   public void testWeekOfYearEquivalenceFieldValue() {
      // Cover many month/week combinations so at least one part value diverges
      // from its equivalence value (week 5/6 rolling into the next month under
      // minimalDaysInFirstWeek=7).
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[]{ "WeekOfYear(date)", "date" });

      for(int year = 2019; year <= 2022; year++) {
         for(int month = 1; month <= 12; month++) {
            for(int week = 4; week <= 6; week++) {
               String mm = month < 10 ? "0" + month : Integer.toString(month);
               rows.add(new Object[]{ month * 10 + week, date(year + "-" + mm + "-15") });
            }
         }
      }

      DataSet dataSet = new DefaultDataSet(rows.toArray(new Object[0][]));
      DataSetTable base = new DataSetTable(dataSet);

      VSDimensionRef partRef = new VSDimensionRef();
      partRef.setDataRef(new AttributeRef("WeekOfYear(date)"));
      VSDimensionRef dateGroupRef = new VSDimensionRef();
      dateGroupRef.setDataRef(new AttributeRef("date"));

      List<XDimensionRef> noExtraRefs = new ArrayList<>();
      DCMergeDatePartFilter filter =
         new DCMergeDatePartFilter(base, noExtraRefs, partRef, dateGroupRef, null);

      // Locate the first row whose part cell has a diverging equivalence cell.
      int divergingRow = -1;       // VSDataSet row index (header excluded)
      String expected = null;      // field value rendered from the equivalence cell
      String original = null;      // field value rendered from the original cell

      for(int r = base.getHeaderRowCount(); r < base.getRowCount(); r++) {
         Object cell = filter.getObject(r, 0);

         if(cell instanceof DCMergeDatePartFilter.MergePartCell) {
            DCMergeDatePartFilter.MergePartCell mpc = (DCMergeDatePartFilter.MergePartCell) cell;
            DCMergeDatePartFilter.MergePartCell equivalenceCell = mpc.getEquivalenceCell();

            if(equivalenceCell != null) {
               divergingRow = r - base.getHeaderRowCount();
               // Render the field value exactly as getFieldValue() would, so the
               // comparison is independent of the cell's string formatting.
               expected = new VSFieldValue("WeekOfYear(date)", equivalenceCell, true)
                  .getFieldValue().getValue();
               original = new VSFieldValue("WeekOfYear(date)", mpc, true)
                  .getFieldValue().getValue();
               break;
            }
         }
      }

      Assertions.assertTrue(divergingRow >= 0,
                            "expected at least one WeekOfYear row whose equivalence cell diverges");
      Assertions.assertNotEquals(original, expected,
                                 "equivalence cell should differ from the original part cell");

      VSDataSet vsDataSet = new VSDataSet(filter, new VSDataRef[]{ partRef, dateGroupRef });
      VSFieldValue[][] fieldValues = vsDataSet.getFieldValues(
         divergingRow, new String[]{ "WeekOfYear(date)" }, new String[]{ "WeekOfYear(date)" },
         false, false);

      String actual = null;

      for(VSFieldValue[] tuple : fieldValues) {
         for(VSFieldValue fv : tuple) {
            if("WeekOfYear(date)".equals(fv.getFieldName())) {
               actual = fv.getFieldValue().getValue();
            }
         }
      }

      Assertions.assertEquals(expected, actual,
                              "drill field value should use the equivalence week, not the displayed part value");
   }
}
