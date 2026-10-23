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
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.VSFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.controller.VSBindingModelService;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.vs.dialog.HideColumnsDialogService;
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
 * Bug #79249: {@code set_column_labels} on a crosstab measure must write the label at EVERY path
 * under which the measure's header renders (detail, row subtotal and row grand total share one
 * lens column when measures are stacked), not just the first one found. Exercises the
 * {@code TableBindingService.applyCrosstabLabels} fan-out through the service entry point.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TableBindingCrosstabLabelFanOutTest {
   private static final String SUM = "Sum(REVENUE)";

   private static final TableDataPath DETAIL = path("Year", "Quarter", SUM);
   private static final TableDataPath SUBTOTAL = path("Year", SUM);
   private static final TableDataPath GRAND = path("ROW_GRAND_TOTAL", SUM);
   private static final TableDataPath CORNER = path("Cell [0,0]");

   private static TableDataPath path(String... segments) {
      return new TableDataPath(-1, TableDataPath.HEADER, XSchema.STRING, segments);
   }

   private static TableDataPath twin(TableDataPath p) {
      TableDataPath twin = (TableDataPath) p.clone();
      twin.setType(TableDataPath.GROUP_HEADER);
      return twin;
   }

   /**
    * Stacked measures, rows=[Year, Quarter], one aggregate; header column 2 holds the
    * measure-name cell of the detail rows, the Year subtotal row and the grand-total row.
    */
   private static TableBindingService stacked(FormatInfo formatInfo) throws Exception {
      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(model, "rows", List.of(
         new FieldRef("Year", "dimension", null, null, null),
         new FieldRef("Quarter", "dimension", null, null, null)));
      TableBindingMutator.setShelf(model, "aggregates", List.of(
         new FieldRef("REVENUE", "measure", "Sum", null, null)));

      VSDimensionRef year = new VSDimensionRef();
      year.setGroupColumnValue("Year");
      VSDimensionRef quarter = new VSDimensionRef();
      quarter.setGroupColumnValue("Quarter");
      VSAggregateRef liveSum = mock(VSAggregateRef.class);
      when(liveSum.getFullName()).thenReturn(SUM);

      VSCrosstabInfo crossInfo = new VSCrosstabInfo();
      crossInfo.setDesignRowHeaders(new DataRef[]{ year, quarter });
      crossInfo.setDesignAggregates(new DataRef[]{ liveSum });
      crossInfo.setSummarySideBySide(false);

      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getVSCrosstabInfo()).thenReturn(crossInfo);
      when(assembly.getAbsoluteName()).thenReturn("Crosstab1");
      when(assembly.getFormatInfo()).thenReturn(formatInfo);

      VSTableLens lens = mock(VSTableLens.class);
      when(lens.getDescriptor()).thenReturn(mock(TableDataDescriptor.class));
      when(lens.getHeaderRowCount()).thenReturn(1);
      when(lens.getHeaderColCount()).thenReturn(3);
      when(lens.getColCount()).thenReturn(4);
      when(lens.getRowCount()).thenReturn(6);
      when(lens.getTableDataPath(0, 0)).thenReturn(CORNER);
      when(lens.getTableDataPath(1, 2)).thenReturn(DETAIL);
      when(lens.getTableDataPath(2, 2)).thenReturn(DETAIL);
      when(lens.getTableDataPath(3, 2)).thenReturn(SUBTOTAL);
      when(lens.getTableDataPath(4, 2)).thenReturn(DETAIL);
      when(lens.getTableDataPath(5, 2)).thenReturn(GRAND);

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

      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenReturn(model);
      return new TableBindingService(
         sessions, binding, mock(VSBindingModelService.class),
         mock(DataRefModelFactoryService.class), mock(HideColumnsDialogService.class),
         mock(VSAssemblyInfoHandler.class));
   }

   private static Principal principal() {
      return () -> "admin";
   }

   private static boolean isLabelled(FormatInfo formatInfo, TableDataPath p) {
      VSCompositeFormat format = formatInfo.getFormat(p);
      return format != null && format.getUserDefinedFormat() != null &&
         java.util.Objects.equals(VSFormat.MESSAGE_FORMAT, format.getUserDefinedFormat().getFormatValue());
   }

   private static void assertLabelled(FormatInfo formatInfo, TableDataPath p, String label) {
      assertTrue(isLabelled(formatInfo, p), "no MESSAGE_FORMAT written at " + p);
      assertEquals(label, formatInfo.getFormat(p).getUserDefinedFormat().getFormatExtentValue(),
                   "at " + p);
   }

   @Test
   void measureLabelIsWrittenAtDetailSubtotalAndGrandTotalPathsAndTheirTwins() throws Exception {
      FormatInfo formatInfo = new FormatInfo();

      stacked(formatInfo).setColumnLabels(
         "tok", principal(), "Crosstab1", Map.of("REVENUE", "Revenue"), null);

      for(TableDataPath p : List.of(DETAIL, SUBTOTAL, GRAND)) {
         assertLabelled(formatInfo, p, "Revenue");
         assertLabelled(formatInfo, twin(p), "Revenue");
      }
   }

   @Test
   void rowsShelfLabelWritesOnlyItsOneCornerPath() throws Exception {
      FormatInfo formatInfo = new FormatInfo();

      stacked(formatInfo).setColumnLabels(
         "tok", principal(), "Crosstab1", Map.of("Year", "Fiscal Year"), null);

      assertLabelled(formatInfo, CORNER, "Fiscal Year");
      assertFalse(isLabelled(formatInfo, DETAIL));
      assertFalse(isLabelled(formatInfo, SUBTOTAL));
      assertFalse(isLabelled(formatInfo, GRAND));
   }

   @Test
   void emptyMeasureLabelClearsEveryPathItWasWrittenAt() throws Exception {
      FormatInfo formatInfo = new FormatInfo();
      TableBindingService service = stacked(formatInfo);
      service.setColumnLabels("tok", principal(), "Crosstab1", Map.of("REVENUE", "Revenue"), null);
      assertTrue(isLabelled(formatInfo, GRAND));

      service.setColumnLabels("tok", principal(), "Crosstab1", Map.of("REVENUE", ""), null);

      for(TableDataPath p : List.of(DETAIL, SUBTOTAL, GRAND)) {
         assertFalse(isLabelled(formatInfo, p), "still labelled at " + p);
         assertFalse(isLabelled(formatInfo, twin(p)), "still labelled at twin of " + p);
      }
   }
}
