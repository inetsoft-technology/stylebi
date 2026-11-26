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

package inetsoft.report.composition.execution;

import inetsoft.report.internal.Util;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.internal.SelectionSet;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.util.CoreTool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class RealtimeTableMetaDataTest {
   @Test
   public void testSerializeColumnsTable() throws Exception {
      RealtimeTableMetaData metaData = new RealtimeTableMetaData("test");
      metaData.process(XTableUtil.getDefaultTableLens(), new String[]{ "col1", "col2", "col3" },
                       new ArrayList<>());
      XTable originalTable = metaData.getColumnTable("test", new String[]{ "col1", "col2", "col3" });
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertNotNull(Util.getNestedTable(deserializedTable,
                                                   RealtimeTableMetaData.ColumnsTable.class));
   }

   // X, Y: a/1, a/2, b/3 and the "Sum(Y)" measure column
   private static DefaultTableLens createTable() {
      Object[][] rows = { { "a", 1 }, { "a", 2 }, { "b", 3 } };
      DefaultTableLens table = new DefaultTableLens(rows.length + 1, 3);
      table.setObject(0, 0, "X");
      table.setObject(0, 1, "Y");
      table.setObject(0, 2, SUM_Y);
      table.setHeaderRowCount(1);

      for(int r = 0; r < rows.length; r++) {
         table.setObject(r + 1, 0, rows[r][0]);
         table.setObject(r + 1, 1, rows[r][1]);
         table.setObject(r + 1, 2, rows[r][1]);
      }

      return table;
   }

   private static final String SUM_Y = new AggregateRef(
      new ColumnRef(new AttributeRef(null, "Y")), AggregateFormula.SUM).toString();

   private static AggregateRef aggr(String column) {
      return new AggregateRef(new ColumnRef(new AttributeRef(null, column)), AggregateFormula.SUM);
   }

   private static Set<Object> values(XTable table) {
      Set<Object> values = new TreeSet<>();

      for(int r = 1; table.moreRows(r); r++) {
         values.add(table.getObject(r, 0));
      }

      return values;
   }

   private static SelectionSet associated(RealtimeTableMetaData md, Map<String, Collection<Object>> sel,
                                          String column, String measure,
                                          SelectionMeasureAggregation agg)
   {
      return md.getAssociatedValues("list", sel, new DataRef[] { new AttributeRef(null, column) },
                                    measure, agg);
   }

   /**
    * A header the data does not produce (the stale binding of one selection list) must not
    * fail the meta data of the other columns, wherever it is in the header list.
    */
   @Test
   public void testMissingColumnDoesNotFailOtherColumns() throws Exception {
      String[][] headers = { { "Gone", "X", "Y" }, { "X", "Gone", "Y" }, { "X", "Y", "Gone" } };

      for(String[] header : headers) {
         RealtimeTableMetaData md = new RealtimeTableMetaData("test");
         md.process(createTable(), header, new ArrayList<>());

         Assertions.assertEquals(Set.of("a", "b"), values(md.getColumnTable("l", new String[]{ "X" })));
         Assertions.assertEquals(Set.of(1, 2, 3), values(md.getColumnTable("l", new String[]{ "Y" })));

         Map<String, Collection<Object>> sel = new HashMap<>();
         sel.put("X", new ArrayList<>(List.of("a")));
         Assertions.assertEquals(2, associated(md, sel, "Y", null, new SelectionMeasureAggregation(false)).size());

         // the selection of the missing column is ignored, the missing list has no values
         sel.put("Gone", new ArrayList<>(List.of("zzz")));
         Assertions.assertEquals(2, associated(md, sel, "Y", null, new SelectionMeasureAggregation(false)).size());
         Assertions.assertEquals(0, associated(md, sel, "Gone", null, new SelectionMeasureAggregation(false)).size());
      }
   }

   /**
    * A missing column is reported as null (so the caller runs its own query) and does not
    * queue the "Column missing from meta-data" message for the user.
    */
   @Test
   public void testMissingColumnTableIsNullWithoutUserMessage() throws Exception {
      RealtimeTableMetaData md = new RealtimeTableMetaData("test");
      md.process(createTable(), new String[]{ "X", "Gone", "Y" }, new ArrayList<>());
      CoreTool.clearUserMessage();

      Assertions.assertNull(md.getColumnTable("l", new DataRef[]{ new AttributeRef(null, "Gone") }));
      Assertions.assertNull(md.getColumnTable(
         "l", new DataRef[]{ new AttributeRef(null, "X"), new AttributeRef(null, "Gone") }));
      Assertions.assertNull(CoreTool.getUserMessage());

      Assertions.assertEquals(Set.of("a", "b"), values(md.getColumnTable(
         "l", new DataRef[]{ new AttributeRef(null, "X") })));
   }

   /**
    * A measure the data does not produce is skipped, the found measure keeps working.
    */
   @Test
   public void testMissingMeasureDoesNotFailOthers() throws Exception {
      for(boolean missingFirst : new boolean[]{ true, false }) {
         List<AggregateRef> aggrs = new ArrayList<>();

         if(missingFirst) {
            aggrs.add(aggr("Gone"));
         }

         aggrs.add(aggr("Y"));

         if(!missingFirst) {
            aggrs.add(aggr("Gone"));
         }

         RealtimeTableMetaData md = new RealtimeTableMetaData("test");
         md.process(createTable(), new String[]{ "X", "Y" }, aggrs);

         SelectionMeasureAggregation agg = new SelectionMeasureAggregation(false);
         SelectionSet set = associated(md, new HashMap<>(), "X", SUM_Y, agg);
         Assertions.assertEquals(2, set.size());
         agg.updateBounds();
         Assertions.assertEquals(3.0, ((Number) agg.getMeasures().get("a")).doubleValue());
         Assertions.assertEquals(3.0, ((Number) agg.getMeasures().get("b")).doubleValue());

         // the missing measure has no formula, the dimension values are still returned
         SelectionMeasureAggregation agg2 = new SelectionMeasureAggregation(false);
         Assertions.assertEquals(2, associated(md, new HashMap<>(), "X", aggr("Gone").toString(), agg2).size());
         Assertions.assertTrue(agg2.getFormulas().isEmpty());
      }
   }
}
