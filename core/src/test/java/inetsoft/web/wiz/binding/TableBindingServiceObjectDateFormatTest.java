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
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.binding.controller.VSBindingModelService;
import inetsoft.web.binding.event.ApplyVSAssemblyInfoEvent;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.table.BaseTableBindingModel;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.binding.model.table.TableBindingModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.composer.vs.dialog.HideColumnsDialogService;
import inetsoft.web.composer.model.vs.HideColumnsDialogModel;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79342: a binding write that would add a numeric output to a Table or Crosstab whose
 * whole-object format is a date/time format is refused, because the new output has no format of
 * its own and would render as 1970-01-01. The check runs on the planned binding model, so a
 * refused write never reaches the binding service.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TableBindingServiceObjectDateFormatTest {
   private static FieldRef field(String column, String type, String aggregate) {
      return new FieldRef(column, type, aggregate, null, null);
   }

   private static void withObjectFormat(VSAssemblyInfo info, String format) {
      VSCompositeFormat object = new VSCompositeFormat();
      object.getUserDefinedFormat().setFormatValue(format);
      info.getFormatInfo().setFormat(VSAssemblyInfo.OBJECTPATH, object);
   }

   private static void withSource(BaseTableBindingModel model) {
      model.setSource(new inetsoft.web.binding.model.SourceInfo());
      model.getSource().setSource("ORDERS");
      BindingModel.SourceTable table = new BindingModel.SourceTable();
      table.setName("ORDERS");
      table.setColumns(List.of(
         new BindingModel.SourceTableColumn("Order Date", XSchema.DATE),
         new BindingModel.SourceTableColumn("Region", XSchema.STRING),
         new BindingModel.SourceTableColumn("Total", XSchema.DOUBLE)));
      model.setTables(List.of(table));
   }

   private static TableBindingModel tableModel(String... details) {
      TableBindingModel model = new TableBindingModel();
      withSource(model);
      List<FieldRef> fields = new java.util.ArrayList<>();

      for(String detail : details) {
         fields.add(field(detail, "dimension", null));
      }

      TableBindingMutator.setShelf(model, "details", fields);
      return model;
   }

   private static TableVSAssembly table(String objectFormat) {
      TableVSAssembly assembly = mock(TableVSAssembly.class);
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();

      if(objectFormat != null) {
         withObjectFormat(info, objectFormat);
      }

      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      return assembly;
   }

   private static CrosstabVSAssembly crosstab(String objectFormat) {
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      CrosstabVSAssemblyInfo info = new CrosstabVSAssemblyInfo();

      if(objectFormat != null) {
         withObjectFormat(info, objectFormat);
      }

      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      return assembly;
   }

   private static TableBindingService service(VSAssembly assembly, BindingModel model,
                                              VSBindingModelService bindings)
   {
      return service(assembly, model, bindings, mock(HideColumnsDialogService.class));
   }

   private static TableBindingService service(VSAssembly assembly, BindingModel model,
                                              VSBindingModelService bindings,
                                              HideColumnsDialogService hideColumns)
   {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

      try {
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1",
                         mock(inetsoft.web.wiz.dispatch.CapturingCommandDispatcher.class));
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenReturn(model);
      return new TableBindingService(sessions, binding, bindings,
                                     mock(DataRefModelFactoryService.class),
                                     hideColumns, mock(VSAssemblyInfoHandler.class));
   }

   private static Principal principal() {
      return () -> "admin";
   }

   @Test
   void addingANumericTableColumnUnderAnObjectDateFormatIsRefusedAndNothingIsApplied()
      throws Exception
   {
      TableBindingModel model = tableModel("Order Date");
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service(table(XConstants.DATE_FORMAT), model, bindings)
            .addField("tok", principal(), "Table1", "details",
                      field("Total", "dimension", null), null, null));

      assertTrue(thrown.getMessage().contains("Table1"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains(XConstants.DATE_FORMAT), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("Total"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("set_format"), thrown.getMessage());
      verify(bindings, never()).setBinding(anyString(), any(ApplyVSAssemblyInfoEvent.class),
                                           any(Principal.class), any());
   }

   @Test
   void addingANumericTableColumnWithNoObjectDateFormatIsApplied() throws Exception {
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      service(table(null), tableModel("Order Date"), bindings)
         .addField("tok", principal(), "Table1", "details",
                   field("Total", "dimension", null), null, null);

      verify(bindings).setBinding(eq("rt1"), any(ApplyVSAssemblyInfoEvent.class),
                                  any(Principal.class), any());
   }

   @Test
   void aNumericColumnAlreadyUnderTheObjectDateFormatDoesNotBlockAnUnrelatedWrite()
      throws Exception
   {
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      service(table(XConstants.DATE_FORMAT), tableModel("Order Date", "Total"), bindings)
         .addField("tok", principal(), "Table1", "details",
                   field("Region", "dimension", null), null, null);

      verify(bindings).setBinding(eq("rt1"), any(ApplyVSAssemblyInfoEvent.class),
                                  any(Principal.class), any());
   }

   @Test
   void aNonDateObjectFormatDoesNotRefuseANumericColumn() throws Exception {
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      service(table(XConstants.DECIMAL_FORMAT), tableModel("Order Date"), bindings)
         .addField("tok", principal(), "Table1", "details",
                   field("Total", "dimension", null), null, null);

      verify(bindings).setBinding(eq("rt1"), any(ApplyVSAssemblyInfoEvent.class),
                                  any(Principal.class), any());
   }

   @Test
   void settingATableShelfWithANumericColumnUnderAnObjectDateFormatIsRefused() throws Exception {
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service(table(XConstants.DATE_FORMAT), tableModel("Order Date"), bindings)
            .setShelf("tok", principal(), "Table1", "details",
                      List.of(field("Order Date", "dimension", null),
                              field("Total", "dimension", null)), null));

      assertTrue(thrown.getMessage().contains("Total"), thrown.getMessage());
      verify(bindings, never()).setBinding(anyString(), any(ApplyVSAssemblyInfoEvent.class),
                                           any(Principal.class), any());
   }

   @Test
   void addingANumericCrosstabAggregateUnderAnObjectDateFormatIsRefusedAndNothingIsApplied()
      throws Exception
   {
      CrosstabBindingModel model = new CrosstabBindingModel();
      withSource(model);
      TableBindingMutator.setShelf(model, "rows", List.of(field("Region", "dimension", null)));
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service(crosstab(XConstants.DATE_FORMAT), model, bindings)
            .addField("tok", principal(), "Crosstab1", "aggregates",
                      field("Total", "measure", "Sum"), null, null));

      assertTrue(thrown.getMessage().contains("Crosstab1"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains(XConstants.DATE_FORMAT), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("Sum(Total)"), thrown.getMessage());
      verify(bindings, never()).setBinding(anyString(), any(ApplyVSAssemblyInfoEvent.class),
                                           any(Principal.class), any());
   }

   @Test
   void addingADateAggregateUnderAnObjectDateFormatIsApplied() throws Exception {
      CrosstabBindingModel model = new CrosstabBindingModel();
      withSource(model);
      TableBindingMutator.setShelf(model, "rows", List.of(field("Region", "dimension", null)));
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      service(crosstab(XConstants.DATE_FORMAT), model, bindings)
         .addField("tok", principal(), "Crosstab1", "aggregates",
                   field("Order Date", "measure", "Max"), null, null);

      verify(bindings).setBinding(eq("rt1"), any(ApplyVSAssemblyInfoEvent.class),
                                  any(Principal.class), any());
   }

   @Test
   void aNewNumericTableColumnWithItsOwnDetailFormatIsAccepted() throws Exception {
      TableVSAssembly assembly = table(XConstants.DATE_FORMAT);
      VSCompositeFormat own = new VSCompositeFormat();
      own.getUserDefinedFormat().setFormatValue(XConstants.DECIMAL_FORMAT);
      assembly.getVSAssemblyInfo().getFormatInfo().setFormat(
         new TableDataPath(-1, TableDataPath.DETAIL, XSchema.STRING, new String[]{ "Total" }),
         own);
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      service(assembly, tableModel("Order Date"), bindings)
         .addField("tok", principal(), "Table1", "details",
                   field("Total", "dimension", null), null, null);

      verify(bindings).setBinding(eq("rt1"), any(ApplyVSAssemblyInfoEvent.class),
                                  any(Principal.class), any());
   }

   private static CrosstabBindingModel crosstabWithRows(String... rows) {
      CrosstabBindingModel model = new CrosstabBindingModel();
      withSource(model);
      List<FieldRef> fields = new java.util.ArrayList<>();

      for(String row : rows) {
         fields.add(field(row, "dimension", null));
      }

      TableBindingMutator.setShelf(model, "rows", fields);
      return model;
   }

   @Test
   void movingANumericDimensionToAggregatesUnderAnObjectDateFormatIsRefused() throws Exception {
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service(crosstab(XConstants.DATE_FORMAT), crosstabWithRows("Region", "Total"),
                       bindings)
            .moveField("tok", principal(), "Crosstab1", "rows", "aggregates", "Total", null));

      assertTrue(thrown.getMessage().contains("Sum(Total)"), thrown.getMessage());
      verify(bindings, never()).setBinding(anyString(), any(ApplyVSAssemblyInfoEvent.class),
                                           any(Principal.class), any());
   }

   @Test
   void movingAStringDimensionBetweenShelvesUnderAnObjectDateFormatIsApplied() throws Exception {
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      service(crosstab(XConstants.DATE_FORMAT), crosstabWithRows("Region", "Total"), bindings)
         .moveField("tok", principal(), "Crosstab1", "rows", "cols", "Region", null);

      verify(bindings).setBinding(eq("rt1"), any(ApplyVSAssemblyInfoEvent.class),
                                  any(Principal.class), any());
   }

   @Test
   void settingACrosstabAggregateShelfUnderAnObjectDateFormatIsRefused() throws Exception {
      VSBindingModelService bindings = mock(VSBindingModelService.class);

      assertThrows(IllegalArgumentException.class,
         () -> service(crosstab(XConstants.DATE_FORMAT), crosstabWithRows("Region"), bindings)
            .setShelf("tok", principal(), "Crosstab1", "aggregates",
                      List.of(field("Total", "measure", "Sum")), null));

      verify(bindings, never()).setBinding(anyString(), any(ApplyVSAssemblyInfoEvent.class),
                                           any(Principal.class), any());
   }

   private static HideColumnsDialogService hiding(TableVSAssembly assembly)
      throws Exception
   {
      ColumnRef total = new ColumnRef(new AttributeRef(null, "Total"));
      total.setDataType(XSchema.DOUBLE);
      ((TableVSAssemblyInfo) assembly.getVSAssemblyInfo()).getHiddenColumns()
         .addAttribute(total);
      HideColumnsDialogService hideColumns = mock(HideColumnsDialogService.class);
      when(hideColumns.getColumnOptionDialogModel(anyString(), anyString(), any()))
         .thenReturn(HideColumnsDialogModel.builder()
                        .availableColumns(List.of("Order Date"))
                        .hiddenColumns(List.of("Total")).build());
      return hideColumns;
   }

   @Test
   void showingAHiddenNumericTableColumnUnderAnObjectDateFormatIsRefusedAndNothingIsWritten()
      throws Exception
   {
      TableVSAssembly assembly = table(XConstants.DATE_FORMAT);
      HideColumnsDialogService hideColumns = hiding(assembly);

      IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
         () -> service(assembly, tableModel("Order Date"), mock(VSBindingModelService.class),
                       hideColumns)
            .setFieldVisibility("tok", principal(), "Table1", "Total", true, ""));

      assertTrue(thrown.getMessage().contains("Total"), thrown.getMessage());
      verify(hideColumns, never()).setColumnOptionDialogModel(
         anyString(), anyString(), any(), any(Principal.class), any(), anyString());
   }

   @Test
   void hidingAColumnUnderAnObjectDateFormatIsApplied() throws Exception {
      TableVSAssembly assembly = table(XConstants.DATE_FORMAT);
      HideColumnsDialogService hideColumns = hiding(assembly);

      service(assembly, tableModel("Order Date"), mock(VSBindingModelService.class), hideColumns)
         .setFieldVisibility("tok", principal(), "Table1", "Order Date", false, "");

      verify(hideColumns).setColumnOptionDialogModel(
         anyString(), anyString(), any(), any(Principal.class), any(), anyString());
   }
}
