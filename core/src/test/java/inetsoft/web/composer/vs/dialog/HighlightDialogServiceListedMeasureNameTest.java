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
package inetsoft.web.composer.vs.dialog;

import inetsoft.report.TableDataPath;
import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.*;
import inetsoft.report.composition.graph.calc.ChangeCalc;
import inetsoft.report.composition.graph.calc.PercentCalc;
import inetsoft.report.filter.CrossTabFilter;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.Calculator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81483: {@code set_highlight} with {@code colName} naming a crosstab measure whose lens name
 * differs from its runtime aggregate ref's own name (a measure with a calculator, or a second
 * binding of the same column) was refused although {@code list_highlights} advertises that name.
 *
 * <p>The fixtures are real lenses ({@code CrosstabVSAQuery}), so the names are the live ones, not
 * names typed into a mock.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  IntegrationTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class HighlightDialogServiceListedMeasureNameTest {
   private static final String ENTITY = "SALES";
   private static final String SUM = "Sum(DISCOUNT)";

   /** A real crosstab lens with its runtime aggregates. */
   private static final class Fixture {
      CrossTabFilter lens;
      DataRef[] aggregates;
      boolean sideBySide;
      String[] listed;

      int headerRows() {
         return lens.getHeaderRowCount();
      }

      int headerCols() {
         return lens.getHeaderColCount();
      }

      java.util.function.Supplier<String[]> listedNames() {
         return () -> listed;
      }

      int[] resolve(String colName) {
         return HighlightDialogService.resolveStackedMeasureCell(
            aggregates, sideBySide, headerRows(), headerCols(), colName, false, listedNames());
      }
   }

   private static VSAggregateRef sum(Calculator calc) {
      VSAggregateRef agg = new VSAggregateRef();
      agg.setDataRef(new AttributeRef(ENTITY, "DISCOUNT"));
      agg.setColumnValue("DISCOUNT");
      agg.setFormulaValue(AggregateFormula.SUM.getFormulaName());

      if(calc != null) {
         agg.setCalculator(calc);
      }

      return agg;
   }

   private static Fixture build(boolean sideBySide, VSAggregateRef... aggs) throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "Query1");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef(ENTITY, "STATE")));
      cs.addAttribute(new ColumnRef(new AttributeRef(ENTITY, "REGION")));
      cs.addAttribute(new ColumnRef(new AttributeRef(ENTITY, "DISCOUNT")));
      table.setColumnSelection(cs, false);

      Object[][] rows = new Object[][]{
         {"STATE", "REGION", "DISCOUNT"},
         {"NJ", "E", 1.5}, {"NJ", "W", 2.5}, {"NJ", "E", 3.0},
         {"CA", "W", 4.0}, {"CA", "E", 5.0}, {"MA", "E", 6.0},
      };
      table.setEmbeddedData(new XEmbeddedTable(new String[]{ "string", "string", "double" }, rows));
      ws.addAssembly(table);

      Viewsheet vs = new Viewsheet();
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, "Crosstab1");
      crosstab.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "Query1"));
      vs.addAssembly(crosstab);

      VSDimensionRef stateDim = new VSDimensionRef();
      stateDim.setDataRef(new AttributeRef(ENTITY, "STATE"));
      stateDim.setGroupColumnValue("STATE");
      VSDimensionRef regionDim = new VSDimensionRef();
      regionDim.setDataRef(new AttributeRef(ENTITY, "REGION"));
      regionDim.setGroupColumnValue("REGION");

      VSCrosstabInfo cinfo = new VSCrosstabInfo();
      cinfo.setDesignRowHeaders(new VSDimensionRef[]{ stateDim });
      cinfo.setDesignColHeaders(new VSDimensionRef[]{ regionDim });
      cinfo.setDesignAggregates(aggs);
      cinfo.setSummarySideBySide(sideBySide);
      crosstab.setVSCrosstabInfo(cinfo);

      Method setBaseWorksheet =
         Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBaseWorksheet.setAccessible(true);
      setBaseWorksheet.invoke(vs, ws);

      ViewsheetSandbox box =
         new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, null);
      AssetQuerySandbox wbox = new AssetQuerySandbox(ws, null, new VariableTable());
      java.lang.reflect.Field wboxField = ViewsheetSandbox.class.getDeclaredField("wbox");
      wboxField.setAccessible(true);
      wboxField.set(box, wbox);

      cinfo.update(vs, cs, null, false, "Query1", null);

      TableLens lens = new CrosstabVSAQuery(box, "Crosstab1", false).getTableLens(false);
      CrossTabFilter filter = (CrossTabFilter) inetsoft.report.internal.Util.getCrossFilter(lens);
      assertNotNull(filter, "expected a crosstab filter, got " + lens.getClass());
      filter.moreRows(TableLens.EOT);

      Fixture fixture = new Fixture();
      fixture.lens = filter;
      fixture.aggregates = cinfo.getRuntimeAggregates();
      fixture.sideBySide = sideBySide;
      TableDataPath path = lens.getDescriptor().getCellDataPath(fixture.headerRows(),
                                                                  fixture.headerCols());
      fixture.listed = HighlightDialogService.listedMeasureNames(
         lens, path, fixture.aggregates.length);
      return fixture;
   }

   private static PercentCalc percent() {
      PercentCalc calc = new PercentCalc();
      calc.setLevel(PercentCalc.GRAND_TOTAL);
      return calc;
   }

   private static ChangeCalc change() {
      ChangeCalc calc = new ChangeCalc();
      calc.setColumnName("REGION");
      calc.setFrom(ChangeCalc.PREVIOUS);
      return calc;
   }

   // Live names of a real lens (captured from the fixture below): the lens lists the second measure as
   // "<calculator prefix>DISCOUNT.1", its runtime ref's full name is "<calculator prefix>Sum(DISCOUNT)".
   private static final String PERCENT_LISTED = "% of total: DISCOUNT.1";
   private static final String CHANGE_LISTED = "Change from previous REGION: DISCOUNT.1";

   private static int[] cell(Fixture f, int index) {
      return f.sideBySide
         ? new int[]{ f.headerRows(), f.headerCols() + index }
         : new int[]{ f.headerRows() + index, f.headerCols() };
   }

   @Test
   void lensListsTheMeasuresInRuntimeAggregateOrder() throws Exception {
      Fixture f = build(false, sum(null), sum(percent()));

      assertArrayEquals(new String[]{ "DISCOUNT", PERCENT_LISTED }, f.listed);
      assertEquals("% of total: Sum(DISCOUNT)", ((VSAggregateRef) f.aggregates[1]).getFullName());
   }

   @Test
   void percentMeasureResolvesByItsListedNameStackedAndSideBySide() throws Exception {
      for(boolean sideBySide : new boolean[]{ false, true }) {
         Fixture f = build(sideBySide, sum(null), sum(percent()));

         assertArrayEquals(cell(f, 1), f.resolve(PERCENT_LISTED), "sideBySide=" + sideBySide);
         // today's names keep resolving to the same cells
         assertArrayEquals(cell(f, 1), f.resolve("% of total: Sum(DISCOUNT)"));
         assertArrayEquals(cell(f, 0), f.resolve("Sum(DISCOUNT)"));
      }
   }

   @Test
   void changeMeasureResolvesByItsListedNameStackedAndSideBySide() throws Exception {
      for(boolean sideBySide : new boolean[]{ false, true }) {
         Fixture f = build(sideBySide, sum(null), sum(change()));

         assertArrayEquals(new String[]{ "DISCOUNT", CHANGE_LISTED }, f.listed);
         assertArrayEquals(cell(f, 1), f.resolve(CHANGE_LISTED), "sideBySide=" + sideBySide);
      }
   }

   @Test
   void secondBindingOfTheSameColumnIsReachableByItsListedName() throws Exception {
      for(boolean sideBySide : new boolean[]{ false, true }) {
         Fixture f = build(sideBySide, sum(null), sum(null));

         assertArrayEquals(new String[]{ "DISCOUNT", "DISCOUNT.1" }, f.listed);
         assertArrayEquals(cell(f, 1), f.resolve("DISCOUNT.1"), "sideBySide=" + sideBySide);
         // the plain name still picks the first measure, exactly as before
         assertArrayEquals(cell(f, 0), f.resolve("Sum(DISCOUNT)"));
      }
   }

   @Test
   void unknownNameStillResolvesToNothing() throws Exception {
      Fixture f = build(false, sum(null), sum(percent()));

      assertNull(f.resolve("no such measure"));
   }

   @Test
   void aRuntimeNameMatchWinsOverAListedNameMatch() throws Exception {
      Fixture f = build(false, sum(null), sum(percent()));
      f.listed = new String[]{ "other", "Sum(DISCOUNT)" };

      assertArrayEquals(cell(f, 0), f.resolve("Sum(DISCOUNT)"));
   }

   @Test
   void aNameListedForTwoMeasuresIsRefusedAsAmbiguous() throws Exception {
      Fixture f = build(false, sum(null), sum(percent()));
      f.listed = new String[]{ "same", "same" };

      Exception e = assertThrows(IllegalArgumentException.class, () -> f.resolve("same"));
      assertTrue(e.getMessage().contains("'same'"), e.getMessage());
   }

   @Test
   void listedNamesThatDoNotPairWithTheAggregatesByPositionAreNotUsed() throws Exception {
      Fixture f = build(false, sum(null), sum(percent()));
      f.listed = new String[]{ "a", PERCENT_LISTED, "c" };

      assertNull(f.resolve(PERCENT_LISTED));

      TableDataPath path = f.lens.getDescriptor().getCellDataPath(f.headerRows(), f.headerCols());
      assertNull(HighlightDialogService.listedMeasureNames(f.lens, path, 3));
   }

   @Test
   void refusalListsTheNamesThatResolve() throws Exception {
      Fixture f = build(false, sum(null), sum(percent()));
      List<String> names =
         HighlightDialogService.resolvableMeasureNames(f.aggregates, f.listedNames());

      assertTrue(names.contains("'Sum(DISCOUNT)'"), names.toString());
      assertTrue(names.contains("'" + PERCENT_LISTED + "'"), names.toString());
      assertTrue(names.contains("'% of total: Sum(DISCOUNT)'"), names.toString());
   }
}
