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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.TableDataPath;
import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.test.dc.DcInMemoryViewsheetFixture;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.binding.handler.SetTableHeaderAliasHandler;
import inetsoft.web.wiz.binding.TableBindingMutator;
import inetsoft.web.wiz.binding.TableBindingService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #81584: the same column bound twice on a side-by-side crosstab with no column dimension
 * renders no separate summary-header row, so both copies share one {@code GROUP_HEADER
 * [Sum(wind)]} header (the {@code ".N"} suffix exists only in the body). Labels and the
 * field-scoped header format must work there, and copies that do have headers of their own
 * (a column dimension, or stacked measures) must be unaffected.
 *
 * <p>Rendered for real from an in-memory table, like {@code DuplicateMeasureHeaderTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
                                  DcInMemoryViewsheetFixture.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SharedMeasureHeaderTest {
   private static final String SUM = "Sum(wind)";

   private final DcInMemoryViewsheetFixture fixture = new DcInMemoryViewsheetFixture();
   private ViewsheetSandbox box;
   private Viewsheet vs;
   private SourceInfo source;
   private CrosstabVSAssembly crosstab;
   private RuntimeViewsheet rvs;

   @BeforeEach
   void setUp() throws Exception {
      fixture.setUp();
      List<Object[]> rows = new ArrayList<>();

      for(int year = 0; year < 2; year++) {
         for(int month = 0; month < 3; month++) {
            rows.add(new Object[] { java.sql.Date.valueOf(LocalDate.of(2024 + year, month + 1, 1)),
                                    100 + year * 6 + month });
         }
      }

      DcInMemoryViewsheetFixture.Sheet sheet = fixture.open("D", "wind", rows);
      box = sheet.box();
      vs = sheet.vs();
      source = sheet.source();
   }

   @AfterEach
   void tearDown() {
      fixture.tearDown();
   }

   private static VSAggregateRef sum() {
      VSAggregateRef agg = new VSAggregateRef();
      agg.setColumnValue("wind");
      agg.setFormulaValue("Sum");
      return agg;
   }

   private static VSDimensionRef dateDim(int level) {
      VSDimensionRef dim = new VSDimensionRef();
      AttributeRef attr = new AttributeRef(null, "D");
      attr.setDataType(XSchema.DATE);
      ColumnRef column = new ColumnRef(attr);
      column.setDataType(XSchema.DATE);
      dim.setDataRef(column);
      dim.setGroupColumnValue("D");
      dim.setDataType(XSchema.DATE);
      dim.setDateLevelValue(level + "");
      return dim;
   }

   /** One row dimension, {@code copies} x Sum(wind), an optional year column dimension. */
   private void render(int copies, boolean sideBySide, boolean colDim) throws Exception {
      crosstab = new CrosstabVSAssembly(vs, "CT");
      crosstab.setSourceInfo((SourceInfo) source.clone());
      VSCrosstabInfo info = crosstab.getVSCrosstabInfo();
      info.setDesignRowHeaders(new DataRef[] { dateDim(XConstants.QUARTER_DATE_GROUP) });
      info.setDesignColHeaders(colDim ? new DataRef[] { dateDim(XConstants.YEAR_DATE_GROUP) }
                                  : new DataRef[0]);
      VSAggregateRef[] aggs = new VSAggregateRef[copies];

      for(int i = 0; i < copies; i++) {
         aggs[i] = sum();
      }

      info.setDesignAggregates(aggs);
      info.setSummarySideBySide(sideBySide);
      vs.addAssembly(crosstab);
      TableLens data = (TableLens) box.getData("CT");
      assertNotNull(data);
      data.moreRows(Integer.MAX_VALUE);
      VSTableLens lens = box.getVSTableLens("CT", false);
      lens.moreRows(Integer.MAX_VALUE);

      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));
   }

   private TableDataPath[] headerPaths(String field) throws Exception {
      Method m = ViewsheetFormatService.class.getDeclaredMethod(
         "computeHeaderRegionPaths", RuntimeViewsheet.class, String.class, String.class);
      m.setAccessible(true);

      try {
         return (TableDataPath[]) m.invoke(null, rvs, "CT", field);
      }
      catch(InvocationTargetException ex) {
         throw (Exception) ex.getCause();
      }
   }

   private void applyLabels(String... labels) throws Exception {
      TableBindingService svc = new TableBindingService(
         mock(ViewsheetSessionService.class), null, null, null, null, null);
      Method m = TableBindingService.class.getDeclaredMethod(
         "applyCrosstabLabels", RuntimeViewsheet.class, CrosstabVSAssembly.class, List.class);
      m.setAccessible(true);
      List<TableBindingMutator.CrosstabTarget> targets = new ArrayList<>();

      for(int i = 0; i < labels.length; i++) {
         targets.add(new TableBindingMutator.CrosstabTarget("aggregates", i, labels[i]));
      }

      try {
         m.invoke(svc, rvs, crosstab, targets);
      }
      catch(InvocationTargetException ex) {
         throw (Exception) ex.getCause();
      }
   }

   /** How many of {@code field}'s header paths carry {@code label} as a header alias. */
   private int labelled(String field, String label) throws Exception {
      FormatInfo formatInfo = crosstab.getFormatInfo();
      int n = 0;

      for(TableDataPath path : headerPaths(field)) {
         var fmt = formatInfo.getFormat(path);

         if(fmt != null && fmt.getUserDefinedFormat() != null &&
            label.equals(fmt.getUserDefinedFormat().getFormatExtentValue()))
         {
            n++;
         }
      }

      return n;
   }

   private void assertMeasureHeader(String layout) throws Exception {
      TableDataPath[] paths = headerPaths(SUM);
      assertTrue(paths.length > 0, layout + ": no header paths");

      for(TableDataPath path : paths) {
         String[] segs = path.getPath();
         assertEquals(SUM, segs[segs.length - 1], layout);
         assertTrue(path.getType() == TableDataPath.HEADER ||
                       path.getType() == TableDataPath.GROUP_HEADER, layout);
      }
   }

   /** The premise: both copies' header cells end in the bare name, only the body has ".1". */
   @Test
   void theCopiesShareOneHeaderWhenThereIsNoSummaryHeaderRow() throws Exception {
      render(2, true, false);
      List<DataRef> aggs = Arrays.asList(crosstab.getVSCrosstabInfo().getAggregates());

      assertTrue(SetTableHeaderAliasHandler.findHeaderPaths(
         box.getVSTableLens("CT", false), aggs.get(1), 1, 1).isEmpty());
      assertFalse(SetTableHeaderAliasHandler.findSharedCopyHeaderPaths(
         box.getVSTableLens("CT", false), aggs.get(1), 1).isEmpty());
   }

   @Test
   void fieldScopedHeaderFormatFindsTheSharedMeasureHeader() throws Exception {
      render(2, true, false);
      assertMeasureHeader("2 copies, side by side, no column dimension");
   }

   @Test
   void fieldScopedHeaderFormatFindsASingleMeasureHeader() throws Exception {
      render(1, true, false);
      assertMeasureHeader("1 copy, no column dimension");
   }

   /** Controls: layouts with a separate summary-header row still resolve. */
   @Test
   void fieldScopedHeaderFormatStillWorksWithAColumnDimensionAndStacked() throws Exception {
      render(2, true, true);
      assertMeasureHeader("2 copies, side by side, column dimension");
      vs.removeAssembly("CT");
      render(2, false, false);
      assertMeasureHeader("2 copies, stacked");
   }

   @Test
   void anUnmatchedHeaderFieldIsRefusedWithAFieldNamedMessage() throws Exception {
      render(2, true, false);
      Exception e = assertThrows(Exception.class, () -> headerPaths("Nope"));
      assertTrue(e.getMessage().contains("Nope"), e.getMessage());
   }

   @Test
   void theSameLabelOnEverySharedCopyIsWritten() throws Exception {
      render(2, true, false);

      applyLabels("Revenue", "Revenue");

      int paths = headerPaths(SUM).length;
      assertTrue(paths > 0);
      assertEquals(paths, labelled(SUM, "Revenue"));
   }

   @Test
   void differentLabelsOnASharedHeaderAreRefusedAndNothingIsWritten() throws Exception {
      render(2, true, false);

      Exception e = assertThrows(Exception.class, () -> applyLabels("First", "Second"));

      assertTrue(e.getMessage().contains("aggregates[1]") && e.getMessage().contains("share"),
                 e.getMessage());
      assertEquals(0, labelled(SUM, "First"));
      assertEquals(0, labelled(SUM, "Second"));
   }

   @Test
   void clearingALaterCopyOfASharedHeaderIsRefusedAndNothingIsCleared() throws Exception {
      render(2, true, false);
      applyLabels("Revenue", "Revenue");
      int written = labelled(SUM, "Revenue");
      assertTrue(written > 0);

      Exception e = assertThrows(Exception.class, () -> applyLabels("Revenue", ""));

      assertTrue(e.getMessage().contains("aggregates[1]") && e.getMessage().contains("clear"),
                 e.getMessage());
      assertEquals(written, labelled(SUM, "Revenue"));
   }

   /** Controls: copies that have a header of their own still take different labels. */
   @Test
   void copiesWithTheirOwnHeadersStillTakeDifferentLabels() throws Exception {
      render(2, true, true);
      applyLabels("First", "Second");

      assertTrue(labelled(SUM, "First") > 0);
      assertTrue(labelled(SUM + ".1", "Second") > 0);
      assertEquals(0, labelled(SUM, "Second"));
   }

   @Test
   void stackedCopiesStillTakeDifferentLabels() throws Exception {
      render(2, false, false);
      applyLabels("First", "Second");

      assertTrue(labelled(SUM + ".1", "Second") > 0);
      assertEquals(0, labelled(SUM + ".1", "First"));
   }
}
