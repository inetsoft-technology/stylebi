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
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.IntegrationTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test for a "value_desc"/"value_asc" crosstab sort silently falling back to a
 * label sort when the aggregate's underlying column carries an entity/table qualifier -- the
 * normal case for any real (non-embedded) datasource table, as opposed to this test's own
 * in-memory fixture.
 *
 * <p>{@code AbstractCrosstabVSAQuery#findCol} resolved a dimension's {@code sortByCol} by
 * comparing it against each candidate aggregate's {@link inetsoft.uql.erm.DataRef#getName()},
 * which is entity-qualified (e.g. {@code "CUSTOMERS.CUSTOMER_ID"}) whenever the aggregate's
 * column has an entity set. Every sibling resolution path in this area -- {@code
 * VSCrosstabInfo#fixCol} (which normalizes a caller-supplied full aggregate name like {@code
 * "Count(CUSTOMER_ID)"} down to the aggregate's entity-free VS name before storing it as the
 * runtime {@code sortByCol}), {@code ColumnSelection#getAttribute} (used to resolve every other
 * bare column reference in this API, including ranking's measure), and the aggregate's own
 * {@code getFullName()}/{@code getVSName()} -- all use the entity-free form. So a {@code
 * sortByCol} that legitimately named the right column, in the same bare form every sibling API
 * accepts, silently failed to match, {@code sortBy} stayed {@code -1}, and the crosstab rendered
 * sorted by label instead -- indistinguishable from a successful sort until compared against the
 * actual values.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CrosstabSortByValueEntityQualifiedNameTest {
   private static final String ENTITY = "CUSTOMERS";

   /** Builds a one-row-dimension, one-aggregate crosstab whose columns carry entity {@link #ENTITY}. */
   private TableLens run(String sortByField) throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "Query1");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(ENTITY, "STATE")));
      cs.addAttribute(new ColumnRef(new AttributeRef(ENTITY, "CUSTOMER_ID")));
      table.setColumnSelection(cs, false);

      Object[][] rows = new Object[][]{
         {"STATE", "CUSTOMER_ID"},
         {"NJ", "C1"}, {"NJ", "C2"}, {"NJ", "C3"}, {"NJ", "C4"}, {"NJ", "C5"}, {"NJ", "C6"},
         {"CA", "C7"}, {"CA", "C8"}, {"CA", "C9"}, {"CA", "C10"},
         {"MA", "C11"}, {"MA", "C12"}, {"MA", "C13"}, {"MA", "C14"},
      };
      table.setEmbeddedData(new XEmbeddedTable(new String[]{ "string", "string" }, rows));
      ws.addAssembly(table);

      Viewsheet vs = new Viewsheet();
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, "Crosstab1");
      crosstab.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "Query1"));
      vs.addAssembly(crosstab);

      VSDimensionRef stateDim = new VSDimensionRef();
      stateDim.setDataRef(new AttributeRef(ENTITY, "STATE"));
      stateDim.setGroupColumnValue("STATE");
      stateDim.setOrder(XConstants.SORT_VALUE_DESC);
      stateDim.setSortByColValue(sortByField);

      VSAggregateRef countAgg = new VSAggregateRef();
      countAgg.setDataRef(new AttributeRef(ENTITY, "CUSTOMER_ID"));
      countAgg.setColumnValue("CUSTOMER_ID");
      countAgg.setFormulaValue(AggregateFormula.COUNT_ALL.getFormulaName());

      VSCrosstabInfo cinfo = new VSCrosstabInfo();
      cinfo.setDesignRowHeaders(new VSDimensionRef[]{ stateDim });
      cinfo.setDesignColHeaders(new VSDimensionRef[0]);
      cinfo.setDesignAggregates(new VSAggregateRef[]{ countAgg });
      crosstab.setVSCrosstabInfo(cinfo);

      // No AssetEntry/asset-repository is available in a from-scratch unit test, so the base
      // worksheet and query sandbox are wired in directly rather than through the normal
      // asset-repository-backed constructors.
      Method setBaseWorksheet = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBaseWorksheet.setAccessible(true);
      setBaseWorksheet.invoke(vs, ws);

      ViewsheetSandbox box = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, null);
      AssetQuerySandbox wbox = new AssetQuerySandbox(ws, null, new VariableTable());
      Field wboxField = ViewsheetSandbox.class.getDeclaredField("wbox");
      wboxField.setAccessible(true);
      wboxField.set(box, wbox);

      cinfo.update(vs, cs, null, false, "Query1", null);

      return new CrosstabVSAQuery(box, "Crosstab1", false).getTableLens();
   }

   private static void assertSortedByCountDescending(TableLens table) {
      table.moreRows(TableLens.EOT);

      assertEquals("NJ", table.getObject(1, 0), "highest count (6) must sort first");
      assertEquals("CA", table.getObject(2, 0), "tied count (4) -- either tied row may come next");
      assertEquals("MA", table.getObject(3, 0), "tied count (4)");
   }

   @Test
   void sortsByValueWhenSortByFieldIsTheBareColumnName() throws Exception {
      assertSortedByCountDescending(run("CUSTOMER_ID"));
   }

   @Test
   void sortsByValueWhenSortByFieldIsTheFullyQualifiedAggregateName() throws Exception {
      assertSortedByCountDescending(run("Count(CUSTOMER_ID)"));
   }
}
