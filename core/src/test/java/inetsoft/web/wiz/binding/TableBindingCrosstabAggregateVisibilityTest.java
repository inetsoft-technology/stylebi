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
package inetsoft.web.wiz.binding;

import inetsoft.report.TableDataDescriptor;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.FormatInfo;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo;
import inetsoft.web.binding.controller.VSBindingModelService;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.vs.dialog.HideColumnsDialogService;
import inetsoft.web.wiz.binding.model.ColumnVisibilityEntry;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #78850: a crosstab aggregate is not one rendered column. Side by side it renders once per
 * cols value plus once per grand total (separate lens columns, each with its own hidden-column
 * key); stacked, every measure shares one measure-name column. Hiding resolved the aggregate to
 * the FIRST rendered header cell only, so side-by-side hid one copy and stacked hid the shared
 * column (every measure's label), and the read-back reported what that one column said.
 *
 * <p>The lens is a mock: header cells are laid out as the real {@code CrossFilterDataDescriptor}
 * lays them out (see the diagnosis cell dump), so what is under test is the resolution and
 * bookkeeping, not the render.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableBindingCrosstabAggregateVisibilityTest {
   private static final String SUM = "Sum(T)";
   private static final String AVG = "Average(T)";

   /** One rendered fixture: the service plus the live objects a test asserts on. */
   private record Fixture(TableBindingService service, CrosstabVSAssemblyInfo info,
                          VSTableLens lens, ViewsheetSessionService sessions)
   {
   }

   private static FieldRef field(String column, String type, String aggregate) {
      return new FieldRef(column, type, aggregate, null, null);
   }

   private static TableDataPath path(String... segments) {
      return new TableDataPath(-1, TableDataPath.GROUP_HEADER, XSchema.STRING, segments);
   }

   /**
    * rows=[Region], aggregates=[Sum(T), Average(T)], cols=[Year] with grand totals, side by side:
    * <pre>
    *  col 0: Region corner   1: 2025 Sum   2: 2025 Avg   3: 2026 Sum   4: 2026 Avg
    *  col 5: Grand Total Sum 6: Grand Total Avg
    * </pre>
    */
   private static Fixture sideBySide(boolean sideBySide) throws Exception {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(field("Region", "dimension", null)));
      TableBindingMutator.setShelf(model, "aggregates", List.of(
         field("T", "measure", "Sum"), field("T", "measure", "Average")));

      VSDimensionRef liveRegion = new VSDimensionRef();
      liveRegion.setGroupColumnValue("Region");
      VSAggregateRef liveSum = mock(VSAggregateRef.class);
      when(liveSum.getFullName()).thenReturn(SUM);
      VSAggregateRef liveAvg = mock(VSAggregateRef.class);
      when(liveAvg.getFullName()).thenReturn(AVG);

      VSCrosstabInfo crossInfo = new VSCrosstabInfo();
      crossInfo.setDesignRowHeaders(new DataRef[]{ liveRegion });
      crossInfo.setDesignAggregates(new DataRef[]{ liveSum, liveAvg });
      crossInfo.setSummarySideBySide(sideBySide);

      CrosstabVSAssemblyInfo info = new CrosstabVSAssemblyInfo();
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getVSCrosstabInfo()).thenReturn(crossInfo);
      when(assembly.getCrosstabInfo()).thenReturn(info);
      when(assembly.getAbsoluteName()).thenReturn("Crosstab1");
      when(assembly.getFormatInfo()).thenReturn(new FormatInfo());

      VSTableLens lens = mock(VSTableLens.class);
      TableDataDescriptor descriptor = mock(TableDataDescriptor.class);
      when(lens.getDescriptor()).thenReturn(descriptor);
      when(lens.getHeaderRowCount()).thenReturn(1);

      if(sideBySide) {
         when(lens.getHeaderColCount()).thenReturn(1);
         when(lens.getColCount()).thenReturn(7);
         when(lens.getRowCount()).thenReturn(3);
         String[][] cols = {
            { "Cell [0,0]" }, { "Year", "2025", SUM }, { "Year", "2025", AVG },
            { "Year", "2026", SUM }, { "Year", "2026", AVG },
            { "COL_GRAND_TOTAL", SUM }, { "COL_GRAND_TOTAL", AVG }
         };

         for(int c = 0; c < cols.length; c++) {
            TableDataPath p = path(cols[c]);
            when(lens.getTableDataPath(0, c)).thenReturn(p);
            when(descriptor.getColDataPath(c)).thenReturn(p);
         }
      }
      else {
         // Stacked: one shared measure-name column (col 1) holds BOTH aggregates' header cells,
         // one per row; col 2.. are the cols values.
         when(lens.getHeaderColCount()).thenReturn(2);
         when(lens.getColCount()).thenReturn(4);
         when(lens.getRowCount()).thenReturn(3);
         TableDataPath corner = path("Cell [0,0]");
         TableDataPath shared = path("Cell [0,1]");
         TableDataPath y2025 = path("Year", "2025");
         TableDataPath y2026 = path("Year", "2026");
         when(lens.getTableDataPath(0, 0)).thenReturn(corner);
         when(lens.getTableDataPath(0, 1)).thenReturn(shared);
         when(lens.getTableDataPath(0, 2)).thenReturn(y2025);
         when(lens.getTableDataPath(0, 3)).thenReturn(y2026);
         when(lens.getTableDataPath(1, 1)).thenReturn(path("Region", SUM));
         when(lens.getTableDataPath(2, 1)).thenReturn(path("Region", AVG));
         when(descriptor.getColDataPath(0)).thenReturn(corner);
         when(descriptor.getColDataPath(1)).thenReturn(shared);
         when(descriptor.getColDataPath(2)).thenReturn(y2025);
         when(descriptor.getColDataPath(3)).thenReturn(y2026);
      }

      ViewsheetSandbox sandbox = mock(ViewsheetSandbox.class);
      when(sandbox.getVSTableLens("Crosstab1", false)).thenReturn(lens);
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(sandbox));

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(rvs, "rt1", null);
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());

      when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);

      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenReturn(model);
      TableBindingService service = new TableBindingService(
         sessions, binding, mock(VSBindingModelService.class),
         mock(DataRefModelFactoryService.class), mock(HideColumnsDialogService.class),
         mock(VSAssemblyInfoHandler.class));
      return new Fixture(service, info, lens, sessions);
   }

   private static Principal principal() {
      return () -> "admin";
   }

   @SuppressWarnings("unchecked")
   private static List<FieldRef> aggregates(Map<String, Object> read) {
      return ((Map<String, List<FieldRef>>) (Map<String, ?>) read.get("shelves"))
         .get("aggregates");
   }

   @Test
   void hidingAnAverageHidesEveryAverageColumnAndNoSumColumn() throws Exception {
      Fixture f = sideBySide(true);

      List<String> applied = f.service().setCrosstabColumnVisibility(
         "tok", principal(), "Crosstab1", null,
         List.of(new ColumnVisibilityEntry("aggregates", "T", 1)),
         false).applied();

      assertTrue(f.info().isColumnHidden(2, f.lens()), "2025 Average");
      assertTrue(f.info().isColumnHidden(4, f.lens()), "2026 Average");
      assertTrue(f.info().isColumnHidden(6, f.lens()), "Grand Total Average");
      assertFalse(f.info().isColumnHidden(1, f.lens()), "2025 Sum must stay");
      assertFalse(f.info().isColumnHidden(3, f.lens()), "2026 Sum must stay");
      assertFalse(f.info().isColumnHidden(5, f.lens()), "Grand Total Sum must stay");
      assertFalse(f.info().isColumnHidden(0, f.lens()), "Region must stay");
      assertEquals(List.of("T -> hidden (3 rendered columns)"), applied);
   }

   @Test
   void hidingTheSameAggregateTwiceIsIdempotentAndShowRestoresEveryCopy() throws Exception {
      Fixture f = sideBySide(true);
      List<ColumnVisibilityEntry> avg =
         List.of(new ColumnVisibilityEntry("aggregates", "T", 1));

      f.service().setCrosstabColumnVisibility("tok", principal(), "Crosstab1", null, avg, false);
      f.service().setCrosstabColumnVisibility("tok", principal(), "Crosstab1", null, avg, false);

      assertTrue(f.info().isColumnHidden(2, f.lens()));
      assertTrue(f.info().isColumnHidden(4, f.lens()));
      assertTrue(f.info().isColumnHidden(6, f.lens()));

      f.service().setCrosstabColumnVisibility("tok", principal(), "Crosstab1", null, null, true);

      for(int c = 0; c < 7; c++) {
         assertFalse(f.info().isColumnHidden(c, f.lens()), "col " + c + " restored");
      }
   }

   @Test
   void hidingAStackedAggregateIsRefusedAndHidesNothing() throws Exception {
      Fixture f = sideBySide(false);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> f.service().setCrosstabColumnVisibility(
            "tok", principal(), "Crosstab1", null,
            List.of(new ColumnVisibilityEntry("aggregates", "T", 1)),
            false));

      assertTrue(thrown.getMessage().contains("summarySideBySide"), thrown.getMessage());
      for(int c = 0; c < 4; c++) {
         assertFalse(f.info().isColumnHidden(c, f.lens()), "col " + c + " must be untouched");
      }
   }

   @Test
   void readReportsAnAggregateHiddenOnlyWhenEveryCopyIsHidden() throws Exception {
      Fixture f = sideBySide(true);
      f.info().addHiddenColumn(2, f.lens());
      f.info().addHiddenColumn(4, f.lens());
      f.info().addHiddenColumn(6, f.lens());

      Map<String, Object> read = f.service().read("tok", principal(), "Crosstab1");

      assertEquals(Boolean.TRUE, aggregates(read).get(0).visible(), "Sum still renders");
      assertEquals(Boolean.FALSE, aggregates(read).get(1).visible(), "Average fully hidden");
      assertNull(read.get("visibilityNote"));
   }

   @Test
   void readReportsAPartiallyHiddenAggregateAsVisibleWithANote() throws Exception {
      Fixture f = sideBySide(true);
      f.info().addHiddenColumn(2, f.lens()); // only the 2025 Average, e.g. hidden in the UI

      Map<String, Object> read = f.service().read("tok", principal(), "Crosstab1");

      assertEquals(Boolean.TRUE, aggregates(read).get(1).visible());
      assertNotNull(read.get("visibilityNote"));
      assertTrue(read.get("visibilityNote").toString().contains("1 of 3"));
   }

   @Test
   void readOmitsAggregateVisibilityWhenMeasuresAreStacked() throws Exception {
      Fixture f = sideBySide(false);
      f.info().addHiddenColumn(1, f.lens()); // the shared measure-name column, hidden in the UI

      Map<String, Object> read = f.service().read("tok", principal(), "Crosstab1");

      assertNull(aggregates(read).get(0).visible());
      assertNull(aggregates(read).get(1).visible());
   }
}
