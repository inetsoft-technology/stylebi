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

import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.FormatInfo;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableDataVSAssemblyInfo;
import inetsoft.web.binding.controller.VSBindingModelService;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.binding.model.table.TableBindingModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.vs.dialog.HideColumnsDialogService;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78831: the multi-target Crosstab writes (labels, column visibility, widths) validate every
 * target before writing any, so a call that throws leaves the assembly unchanged.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TableBindingServiceAtomicWritesTest {
   private static final TableDataPath REGION_HEADER = new TableDataPath(
      -1, TableDataPath.HEADER, XSchema.STRING, new String[]{ "Cell [0,0]" });

   @Test
   void labelsWithAnUnresolvableTargetLeaveTheValidTargetUnchanged() throws Exception {
      Fixture f = new Fixture();
      Map<String, String> labels = new LinkedHashMap<>();
      labels.put("Region", "Sales Region");
      labels.put("Product", "Item");

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> f.service().setColumnLabels("tok", principal(), "Crosstab1", labels, null));

      assertTrue(thrown.getMessage().contains("rows[1]"), thrown.getMessage());
      assertNull(f.formatInfo.getFormat(REGION_HEADER),
                 "the valid target must not be written when another target fails");
   }

   @Test
   void labelsWithAllValidTargetsAreApplied() throws Exception {
      Fixture f = new Fixture();

      List<String> applied = f.service().setColumnLabels(
         "tok", principal(), "Crosstab1", Map.of("Region", "Sales Region"), null).applied();

      assertEquals(List.of("rows[0] -> Sales Region"), applied);
      assertNotNull(f.formatInfo.getFormat(REGION_HEADER));
   }

   @Test
   void visibilityWithAnUnresolvableTargetHidesNothing() throws Exception {
      Fixture f = new Fixture();

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> f.service().setCrosstabColumnVisibility(
            "tok", principal(), "Crosstab1", List.of("Region", "Product"), null, false));

      assertTrue(thrown.getMessage().contains("rows[1]"), thrown.getMessage());
      verify(f.tableInfo, never()).addHiddenColumn(anyInt(), any());
   }

   @Test
   void visibilityWithAllValidTargetsHidesThem() throws Exception {
      Fixture f = new Fixture();

      List<String> applied = f.service().setCrosstabColumnVisibility(
         "tok", principal(), "Crosstab1", List.of("Region"), null, false).applied();

      assertEquals(List.of("Region -> hidden"), applied);
      verify(f.tableInfo).addHiddenColumn(0, f.lens);
   }

   @Test
   void widthsWithAnUnknownColumnWriteNothing() throws Exception {
      WidthFixture f = new WidthFixture();
      Map<String, Double> widths = new LinkedHashMap<>();
      widths.put("Region", 120.0);
      widths.put("Nope", 100.0);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> f.service().setColumnWidths("tok", principal(), "Table1", widths));

      assertTrue(thrown.getMessage().contains("'Nope'"), thrown.getMessage());
      verify(f.info, never()).setColumnWidthValue2(anyInt(), anyDouble(), any());
      verify(f.info, never()).setColumnWidthValue(anyInt(), anyDouble());
      verify(f.info, never()).setExplicitTableWidthValue(anyBoolean());
   }

   @Test
   void widthsWithABadWidthAfterAValidOneWriteNothingAndReportEveryProblem() throws Exception {
      WidthFixture f = new WidthFixture();
      Map<String, Double> widths = new LinkedHashMap<>();
      widths.put("Region", 120.0);
      widths.put("Total", -1.0);
      widths.put("Nope", 100.0);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> f.service().setColumnWidths("tok", principal(), "Table1", widths));

      assertTrue(thrown.getMessage().contains("'Total' width must be"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("'Nope'"), thrown.getMessage());
      verify(f.info, never()).setColumnWidthValue2(anyInt(), anyDouble(), any());
   }

   @Test
   void widthsWithAllValidEntriesAreApplied() throws Exception {
      WidthFixture f = new WidthFixture();
      Map<String, Double> widths = new LinkedHashMap<>();
      widths.put("Region", 120.0);
      widths.put("Total", 80.0);

      List<String> applied = f.service().setColumnWidths("tok", principal(), "Table1", widths)
         .applied();

      assertEquals(List.of("Region -> 120px", "Total -> 80px"), applied);
      verify(f.info).setColumnWidthValue2(0, 120.0, f.lens);
      verify(f.info).setColumnWidthValue2(1, 80.0, f.lens);
   }

   /** A Crosstab with rows[Region] live and rendered, but the model also claiming rows[Product]. */
   private static class Fixture {
      final FormatInfo formatInfo = new FormatInfo();
      final CrosstabVSAssemblyInfo tableInfo = mock(CrosstabVSAssemblyInfo.class);
      final VSTableLens lens = mock(VSTableLens.class);
      final RuntimeViewsheet rvs;

      Fixture() throws Exception {
         VSDimensionRef liveRegion = new VSDimensionRef();
         liveRegion.setGroupColumnValue("Region");
         VSCrosstabInfo crossInfo = new VSCrosstabInfo();
         crossInfo.setDesignRowHeaders(new DataRef[]{ liveRegion });

         CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
         when(assembly.getVSCrosstabInfo()).thenReturn(crossInfo);
         when(assembly.getCrosstabInfo()).thenReturn(tableInfo);
         when(assembly.getAbsoluteName()).thenReturn("Crosstab1");
         when(assembly.getFormatInfo()).thenReturn(formatInfo);

         when(lens.getHeaderRowCount()).thenReturn(1);
         when(lens.getHeaderColCount()).thenReturn(1);
         when(lens.getColCount()).thenReturn(1);
         when(lens.getRowCount()).thenReturn(1);
         when(lens.getTableDataPath(0, 0)).thenReturn(REGION_HEADER);

         ViewsheetSandbox sandbox = mock(ViewsheetSandbox.class);
         when(sandbox.getVSTableLens("Crosstab1", false)).thenReturn(lens);

         Viewsheet vs = mock(Viewsheet.class);
         when(vs.getAssembly(anyString())).thenReturn(assembly);
         rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheet()).thenReturn(vs);
         when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(sandbox));
      }

      TableBindingService service() throws Exception {
         CrosstabBindingModel model = new CrosstabBindingModel();
         TableBindingMutator.setShelf(model, "rows", List.of(
            new FieldRef("Region", "dimension", null, null, null),
            new FieldRef("Product", "dimension", null, null, null)));
         return newService(rvs, model);
      }
   }

   /** A Table rendering two columns, Region and Total. */
   private static class WidthFixture {
      final TableDataVSAssemblyInfo info = mock(TableDataVSAssemblyInfo.class);
      final VSTableLens lens = mock(VSTableLens.class);
      final RuntimeViewsheet rvs;

      WidthFixture() throws Exception {
         TableVSAssembly assembly = mock(TableVSAssembly.class);
         when(assembly.getAbsoluteName()).thenReturn("Table1");
         when(assembly.getInfo()).thenReturn(info);

         when(lens.getHeaderRowCount()).thenReturn(1);
         when(lens.getRowCount()).thenReturn(1);
         when(lens.getColCount()).thenReturn(2);
         when(lens.getObject(0, 0)).thenReturn("Region");
         when(lens.getObject(0, 1)).thenReturn("Total");

         ViewsheetSandbox sandbox = mock(ViewsheetSandbox.class);
         when(sandbox.getVSTableLens("Table1", false)).thenReturn(lens);

         Viewsheet vs = mock(Viewsheet.class);
         when(vs.getAssembly(anyString())).thenReturn(assembly);
         rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheet()).thenReturn(vs);
         when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(sandbox));
      }

      TableBindingService service() throws Exception {
         return newService(rvs, new TableBindingModel());
      }
   }

   private static TableBindingService newService(RuntimeViewsheet rvs, BindingModel model)
      throws Exception
   {
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.mutate(anyString(), any(Principal.class), any())).thenAnswer(inv -> {
         ViewsheetSessionService.Mutation mutation = inv.getArgument(2);
         mutation.run(rvs, "rt1", null);
         return null;
      });

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
}
