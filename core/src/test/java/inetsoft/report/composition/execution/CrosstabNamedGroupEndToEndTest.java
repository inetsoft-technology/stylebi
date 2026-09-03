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
import inetsoft.report.internal.binding.AssetNamedGroupInfo;
import inetsoft.report.internal.binding.ExpertNamedGroupInfo;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.IntegrationTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.JunctionOperator;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XCondition;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.DateCondition;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.util.XNamedGroupInfo;
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
import java.sql.Date;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

/**
 * The full-stack proof per this program's own "{"ok":true} is not evidence" standard: a real
 * {@link CrosstabVSAQuery} execution, against a real in-memory {@link TableLens}, with an
 * Expert or Asset-typed {@link XNamedGroupInfo} on an ordinary crosstab row dimension --
 * mirroring {@code CrosstabSortByValueEntityQualifiedNameTest}'s fixture shape. Confirms the
 * grouping this whole redesign exists to unblock actually changes the rendered data, not just
 * the model.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CrosstabNamedGroupEndToEndTest {
   /** Builds a one-row-dimension, one-aggregate crosstab grouping STATE by {@code groupInfo}. */
   private TableLens run(XNamedGroupInfo groupInfo) throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "Query1");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef("STATE")));
      cs.addAttribute(new ColumnRef(new AttributeRef("CUSTOMER_ID")));
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
      stateDim.setDataRef(new AttributeRef("STATE"));
      stateDim.setGroupColumnValue("STATE");
      stateDim.setDataType(XSchema.STRING);
      // The Part 2/Part 3 wiring this test proves: an ordinary dimension carrying a resolved
      // Expert/Asset XNamedGroupInfo, forced to SORT_SPECIFIC exactly as
      // FieldRefFactory.resolveNamedGroupInfo/ChartDimensionInfoFactory.pasteChartRef/
      // BDimensionRefModel.createDataRef do at the wiz binding-apply layer.
      stateDim.setNamedGroupInfo(groupInfo);
      stateDim.setOrder(XConstants.SORT_SPECIFIC);

      VSAggregateRef countAgg = new VSAggregateRef();
      countAgg.setDataRef(new AttributeRef("CUSTOMER_ID"));
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

      TableLens lens = new CrosstabVSAQuery(box, "Crosstab1", false).getTableLens();
      lens.moreRows(TableLens.EOT);
      return lens;
   }

   /** Builds a one-row-dimension (a date column), one-aggregate crosstab grouping ORDER_DATE by
    *  {@code groupInfo}. */
   private TableLens runDate(XNamedGroupInfo groupInfo, Object[][] rows) throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "Query1");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new AttributeRef("ORDER_DATE")));
      cs.addAttribute(new ColumnRef(new AttributeRef("CUSTOMER_ID")));
      table.setColumnSelection(cs, false);

      table.setEmbeddedData(new XEmbeddedTable(new String[]{ "date", "string" }, rows));
      ws.addAssembly(table);

      Viewsheet vs = new Viewsheet();
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, "Crosstab1");
      crosstab.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "Query1"));
      vs.addAssembly(crosstab);

      VSDimensionRef dateDim = new VSDimensionRef();
      dateDim.setDataRef(new AttributeRef("ORDER_DATE"));
      dateDim.setGroupColumnValue("ORDER_DATE");
      dateDim.setDataType(XSchema.DATE);
      dateDim.setNamedGroupInfo(groupInfo);
      dateDim.setOrder(XConstants.SORT_SPECIFIC);

      VSAggregateRef countAgg = new VSAggregateRef();
      countAgg.setDataRef(new AttributeRef("CUSTOMER_ID"));
      countAgg.setColumnValue("CUSTOMER_ID");
      countAgg.setFormulaValue(AggregateFormula.COUNT_ALL.getFormulaName());

      VSCrosstabInfo cinfo = new VSCrosstabInfo();
      cinfo.setDesignRowHeaders(new VSDimensionRef[]{ dateDim });
      cinfo.setDesignColHeaders(new VSDimensionRef[0]);
      cinfo.setDesignAggregates(new VSAggregateRef[]{ countAgg });
      crosstab.setVSCrosstabInfo(cinfo);

      Method setBaseWorksheet = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBaseWorksheet.setAccessible(true);
      setBaseWorksheet.invoke(vs, ws);

      ViewsheetSandbox box = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, null);
      AssetQuerySandbox wbox = new AssetQuerySandbox(ws, null, new VariableTable());
      Field wboxField = ViewsheetSandbox.class.getDeclaredField("wbox");
      wboxField.setAccessible(true);
      wboxField.set(box, wbox);

      cinfo.update(vs, cs, null, false, "Query1", null);

      TableLens lens = new CrosstabVSAQuery(box, "Crosstab1", false).getTableLens();
      lens.moreRows(TableLens.EOT);
      return lens;
   }

   private static Date twoYearsAgo(int month, int day) {
      return dateInYear(currentYear() - 2, month, day);
   }

   private static Date dateInYear(int year, int month, int day) {
      Calendar cal = new GregorianCalendar();
      cal.clear();
      cal.set(year, month - 1, day);
      return new Date(cal.getTimeInMillis());
   }

   private static int currentYear() {
      return new GregorianCalendar().get(Calendar.YEAR);
   }

   /** All row labels (column 0) actually present in the rendered crosstab, skipping the header. */
   private static Set<String> rowLabels(TableLens lens) {
      Set<String> labels = new HashSet<>();

      for(int r = 1; r < lens.getRowCount(); r++) {
         labels.add(String.valueOf(lens.getObject(r, 0)));
      }

      return labels;
   }

   private static int countFor(TableLens lens, String label) {
      for(int r = 1; r < lens.getRowCount(); r++) {
         if(label.equals(String.valueOf(lens.getObject(r, 0)))) {
            return ((Number) lens.getObject(r, 1)).intValue();
         }
      }

      throw new AssertionError("no row for '" + label + "' in: " + rowLabels(lens));
   }

   /** {@code STATE = 'NJ' OR STATE = 'CA'} -- two OR-joined equality items, the shape
    *  {@code add_named_group} actually produces, and the only shape
    *  {@code NamedRangeRef.getScriptExpression()} knows how to translate (it switches on
    *  {@code EQUAL_TO}/{@code LESS_THAN}/{@code GREATER_THAN} per item; a single multi-value
    *  {@code ONE_OF} condition falls through unhandled there). */
   private static ConditionList coastalCondition() {
      ConditionList conds = new ConditionList();
      Condition nj = new Condition(XSchema.STRING);
      nj.setOperation(XCondition.EQUAL_TO);
      nj.addValue("NJ");
      conds.append(new ConditionItem(new AttributeRef("STATE"), nj, 0));
      conds.append(new JunctionOperator(JunctionOperator.OR, 0));
      Condition ca = new Condition(XSchema.STRING);
      ca.setOperation(XCondition.EQUAL_TO);
      ca.addValue("CA");
      conds.append(new ConditionItem(new AttributeRef("STATE"), ca, 0));
      return conds;
   }

   @Test
   void groupsByAWorksheetLocalExpertNamedGroup() throws Exception {
      ExpertNamedGroupInfo info = new ExpertNamedGroupInfo();
      info.setGroupCondition("Coastal", coastalCondition());

      TableLens lens = run(info);

      assertEquals(Set.of("Coastal", "MA"), rowLabels(lens),
         "NJ and CA must collapse into the named group; MA is ungrouped and passes through");
      assertEquals(10, countFor(lens, "Coastal"), "NJ (6) + CA (4)");
      assertEquals(4, countFor(lens, "MA"));
      assertFalse(rowLabels(lens).contains("NJ"), "NJ must not survive ungrouped");
      assertFalse(rowLabels(lens).contains("CA"), "CA must not survive ungrouped");
   }

   /**
    * Confirms the same query-engine path is exercised identically for a repository-registered
    * ("predefined") named group -- by the time an {@link AssetNamedGroupInfo} reaches
    * {@code VSDimensionRef.createGroupRef()}, the asset lookup has already resolved (Part 1/3),
    * so a mocked {@code getGroups()}/{@code getGroupCondition()} pair is a faithful stand-in for
    * one without needing a live {@code AssetRepository} in this test.
    */
   @Test
   void groupsByARepositoryRegisteredAssetNamedGroup() throws Exception {
      AssetNamedGroupInfo info = mock(AssetNamedGroupInfo.class);
      when(info.getType()).thenReturn(XNamedGroupInfo.ASSET_NAMEDGROUP_INFO_REF);
      when(info.isEmpty()).thenReturn(false);
      when(info.getGroups()).thenReturn(new String[]{ "Coastal" });
      when(info.getGroupCondition("Coastal")).thenReturn(coastalCondition());
      // VSDimensionRef.clone() -- exercised somewhere along cinfo.update()'s runtime-ref
      // preparation -- calls groupInfo.clone(); an unstubbed Mockito mock returns null there,
      // silently discarding the named group before the query ever runs.
      when(info.clone()).thenReturn(info);

      TableLens lens = run(info);

      assertEquals(Set.of("Coastal", "MA"), rowLabels(lens));
      assertEquals(10, countFor(lens, "Coastal"), "NJ (6) + CA (4)");
      assertEquals(4, countFor(lens, "MA"));
   }

   /**
    * PC-007 (bug #76350) regression: a {@code date_in}-based named group resolves to a single
    * {@link ConditionItem} wrapping a {@link DateCondition} clone -- exactly the shape
    * {@code ConditionUtil.fromModelToConditionList()} produces for {@code add_named_group}'s
    * {@code DATE_IN} branch. {@code NamedGroupInfoModel.normalizeDateInGroupCondition} (unit
    * tested in {@code NamedGroupInfoModelTest}) expands that into the GREATER_THAN/AND/LESS_THAN
    * range built here by hand, since it's package-private -- this test's job is to confirm that
    * expanded shape actually groups correctly once it reaches the real query engine, not just
    * that the shape itself is right.
    */
   @Test
   void groupsByDateInBasedNamedGroupCondition() throws Exception {
      AttributeRef attribute = new AttributeRef("ORDER_DATE");
      Condition range = new DateCondition.YearCondition(2).toSqlCondition(false);
      Condition start = new Condition(XSchema.DATE);
      start.setOperation(XCondition.GREATER_THAN);
      start.setEqual(true);
      start.addValue(range.getValue(0));
      Condition end = new Condition(XSchema.DATE);
      end.setOperation(XCondition.LESS_THAN);
      end.setEqual(true);
      end.addValue(range.getValue(1));

      ConditionList conds = new ConditionList();
      conds.append(new ConditionItem(attribute, start, 0));
      conds.append(new JunctionOperator(JunctionOperator.AND, 0));
      conds.append(new ConditionItem(attribute, end, 0));

      ExpertNamedGroupInfo info = new ExpertNamedGroupInfo();
      info.setGroupCondition("TwoYearsAgo", conds);

      // Two identical control dates so they collapse into a single ungrouped row, matching the
      // MA-passes-through-ungrouped shape of the STATE-based tests above.
      Date controlDate = dateInYear(currentYear(), 2, 1);
      String controlLabel = String.valueOf(controlDate);
      Object[][] rows = new Object[][]{
         {"ORDER_DATE", "CUSTOMER_ID"},
         {twoYearsAgo(3, 1), "C1"}, {twoYearsAgo(6, 1), "C2"},
         {twoYearsAgo(9, 1), "C3"}, {twoYearsAgo(11, 1), "C4"},
         {controlDate, "C5"}, {controlDate, "C6"},
      };

      TableLens lens = runDate(info, rows);

      assertEquals(Set.of("TwoYearsAgo", controlLabel), rowLabels(lens),
         "the 4 dates two years ago must collapse into the named group; the control date is " +
         "ungrouped and passes through");
      assertEquals(4, countFor(lens, "TwoYearsAgo"), "the 4 rows dated two years ago");
      assertEquals(2, countFor(lens, controlLabel), "the 2 rows dated on the control date");
   }
}
