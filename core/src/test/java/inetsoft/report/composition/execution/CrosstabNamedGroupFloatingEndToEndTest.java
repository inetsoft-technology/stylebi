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
package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.report.internal.binding.ExpertNamedGroupInfo;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.IntegrationTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.JunctionOperator;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XCondition;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.Viewsheet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #79129: a real {@link CrosstabVSAQuery} over a FLOAT/DOUBLE column with LESS_THAN /
 * GREATER_THAN / range named groups. The legacy script branch used to compare every operator
 * as "equal to the bound", so LESS_THAN 10 grouped the row equal to 10 and a
 * {@code >= a AND < b} range grouped nothing.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
   IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CrosstabNamedGroupFloatingEndToEndTest {
   private static final String[] VALUES = {
      "-15.5", "-5.25", "0", "5.5", "10", "15.75", "19.99", "20", "25.5", "999.99" };
   private static final String[] TYPES = { XSchema.DOUBLE, XSchema.FLOAT };

   private Map<String, Integer> run(String type, ConditionList conds) throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "Query1");
      ColumnSelection cs = new ColumnSelection();
      ColumnRef priceCol = new ColumnRef(new AttributeRef("PRICE"));
      priceCol.setDataType(type);
      cs.addAttribute(priceCol);
      cs.addAttribute(new ColumnRef(new AttributeRef("ID")));
      table.setColumnSelection(cs, false);

      Object[][] rows = new Object[VALUES.length + 1][];
      rows[0] = new Object[]{ "PRICE", "ID" };

      for(int i = 0; i < VALUES.length; i++) {
         Object price = XSchema.FLOAT.equals(type) ?
            (Object) Float.valueOf(VALUES[i]) : (Object) Double.valueOf(VALUES[i]);
         rows[i + 1] = new Object[]{ price, "C" + i };
      }

      table.setEmbeddedData(new XEmbeddedTable(new String[]{ type, "string" }, rows));
      ws.addAssembly(table);

      Viewsheet vs = new Viewsheet();
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, "Crosstab1");
      crosstab.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "Query1"));
      vs.addAssembly(crosstab);

      ExpertNamedGroupInfo info = new ExpertNamedGroupInfo();
      info.setGroupCondition("G", conds);

      VSDimensionRef dim = new VSDimensionRef();
      dim.setDataRef(new AttributeRef("PRICE"));
      dim.setGroupColumnValue("PRICE");
      dim.setDataType(type);
      dim.setNamedGroupInfo(info);
      dim.setOrder(XConstants.SORT_SPECIFIC);

      VSAggregateRef countAgg = new VSAggregateRef();
      countAgg.setDataRef(new AttributeRef("ID"));
      countAgg.setColumnValue("ID");
      countAgg.setFormulaValue(AggregateFormula.COUNT_ALL.getFormulaName());

      VSCrosstabInfo cinfo = new VSCrosstabInfo();
      cinfo.setDesignRowHeaders(new VSDimensionRef[]{ dim });
      cinfo.setDesignColHeaders(new VSDimensionRef[0]);
      cinfo.setDesignAggregates(new VSAggregateRef[]{ countAgg });
      crosstab.setVSCrosstabInfo(cinfo);

      Method setBaseWorksheet =
         Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBaseWorksheet.setAccessible(true);
      setBaseWorksheet.invoke(vs, ws);

      ViewsheetSandbox box =
         new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, null);
      AssetQuerySandbox wbox = new AssetQuerySandbox(ws, null, new VariableTable());
      Field wboxField = ViewsheetSandbox.class.getDeclaredField("wbox");
      wboxField.setAccessible(true);
      wboxField.set(box, wbox);

      cinfo.update(vs, cs, null, false, "Query1", null);

      TableLens lens = new CrosstabVSAQuery(box, "Crosstab1", false).getTableLens();
      lens.moreRows(TableLens.EOT);
      Map<String, Integer> result = new LinkedHashMap<>();

      for(int r = 1; r < lens.getRowCount(); r++) {
         result.put(String.valueOf(lens.getObject(r, 0)),
                    ((Number) lens.getObject(r, 1)).intValue());
      }

      return result;
   }

   private static Condition cond(String type, int op, boolean equal, String value) {
      Condition c = new Condition(type);
      c.setOperation(op);
      c.setEqual(equal);
      c.addValue(value);
      return c;
   }

   private static ConditionList single(Condition c) {
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new AttributeRef("PRICE"), c, 0));
      return list;
   }

   private static ConditionList range(String type, String from, String to) {
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new AttributeRef("PRICE"),
         cond(type, XCondition.GREATER_THAN, true, from), 0));
      list.append(new JunctionOperator(JunctionOperator.AND, 0));
      list.append(new ConditionItem(new AttributeRef("PRICE"),
         cond(type, XCondition.LESS_THAN, false, to), 0));
      return list;
   }

   private void check(String type, ConditionList conds, int expectedInGroup) throws Exception {
      Map<String, Integer> result = run(type, conds);
      assertEquals(expectedInGroup, result.getOrDefault("G", 0).intValue(), type + " " + result);
      int total = result.values().stream().mapToInt(Integer::intValue).sum();
      assertEquals(VALUES.length, total, type + " " + result);
   }

   @Test
   void lessThanAndLessOrEqual() throws Exception {
      for(String t : TYPES) {
         check(t, single(cond(t, XCondition.LESS_THAN, false, "10")), 4);
         check(t, single(cond(t, XCondition.LESS_THAN, true, "10")), 5);
      }
   }

   @Test
   void greaterThanAndGreaterOrEqual() throws Exception {
      for(String t : TYPES) {
         check(t, single(cond(t, XCondition.GREATER_THAN, false, "20")), 2);
         check(t, single(cond(t, XCondition.GREATER_THAN, true, "20")), 3);
      }
   }

   @Test
   void rangePairsIncludingNegativeSpan() throws Exception {
      for(String t : TYPES) {
         check(t, range(t, "10", "20"), 3);
         check(t, range(t, "-10", "0"), 1);
         check(t, range(t, "19.99", "20"), 1);
      }
   }

   @Test
   void equalToIncludingLargeFloat() throws Exception {
      for(String t : TYPES) {
         check(t, single(cond(t, XCondition.EQUAL_TO, false, "10")), 1);
         check(t, single(cond(t, XCondition.EQUAL_TO, false, "999.99")), 1);
      }
   }
}
