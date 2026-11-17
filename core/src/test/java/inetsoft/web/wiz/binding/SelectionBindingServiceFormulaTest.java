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


import inetsoft.test.*;
import inetsoft.uql.viewsheet.SelectionListVSAssembly;
import inetsoft.uql.viewsheet.TimeSliderVSAssembly;
import inetsoft.web.composer.model.vs.OutputColumnRefModel;
import inetsoft.web.composer.model.vs.SelectionListPropertyDialogModel;
import inetsoft.web.composer.vs.dialog.DataOutputService;
import inetsoft.web.composer.vs.dialog.RangeSliderPropertyDialogService;
import inetsoft.web.composer.vs.dialog.SelectionListPropertyDialogService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static inetsoft.web.wiz.binding.SelectionBindingServiceTest.harness;
import static inetsoft.web.wiz.binding.SelectionBindingServiceTest.listService;
import static inetsoft.web.wiz.binding.SelectionBindingServiceTest.principal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * #81535: set_selection_source's aggregate rides the binding's own write (one undo checkpoint).
 * Separate from SelectionBindingServiceTest because resolving the aggregate initializes
 * AggregateFormula, which reads SreeEnv and needs the Spring context and a SREE home.
 */@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class SelectionBindingServiceFormulaTest {

   private static DataOutputService typedMeasure(String name, String type) throws Exception {
      OutputColumnRefModel column = new OutputColumnRefModel();
      column.setName(name);
      column.setDataType(type);
      DataOutputService dataOutput = mock(DataOutputService.class);
      when(dataOutput.getOutputSelectionColumns(eq("rt1"), any(), any()))
         .thenReturn(new OutputColumnRefModel[] { new OutputColumnRefModel(), column });
      return dataOutput;
   }
   @Test
   void writesTheFormulaAndTitleIntoTheSameListApplyAsTheBinding() throws Exception {
      SelectionListPropertyDialogModel model = new SelectionListPropertyDialogModel();
      SelectionListPropertyDialogService listService = listService(model);

      harness(mock(SelectionListVSAssembly.class), listService, null, null, null,
              typedMeasure("AMOUNT", "double"))
         .setSource("tok", principal(), "List1", "ORDERS", List.of("STATE"), null, "AMOUNT", null,
                    null, null, false, "", "sum", "State");

      assertEquals("Sum", model.getSelectionListPaneModel().getSelectionMeasurePaneModel().getFormula());
      assertEquals("State", model.getSelectionGeneralPaneModel().getTitlePropPaneModel().getTitle());
      verify(listService, times(1)).setSelectionListPropertyModel(
         any(), any(), any(), any(), any(), any());
   }

   @Test
   void refusesASumFormulaOverANonNumericMeasureBeforeApplying() throws Exception {
      SelectionListPropertyDialogService listService = listService(new SelectionListPropertyDialogModel());

      Exception thrown = assertThrows(IllegalArgumentException.class, () ->
         harness(mock(SelectionListVSAssembly.class), listService, null, null, null,
                 typedMeasure("NAME", "string"))
            .setSource("tok", principal(), "List1", "ORDERS", List.of("STATE"), null, "NAME", null,
                       null, null, false, "", "Sum", null));

      assertTrue(thrown.getMessage().contains("numeric"), thrown.getMessage());
      verify(listService, never()).setSelectionListPropertyModel(
         any(), any(), any(), any(), any(), any());
   }

   @Test
   void refusesAnUnknownFormulaAndAFormulaWithNoMeasure() throws Exception {
      SelectionListPropertyDialogService listService = listService(new SelectionListPropertyDialogModel());

      Exception unknown = assertThrows(IllegalArgumentException.class, () ->
         harness(mock(SelectionListVSAssembly.class), listService, null, null, null,
                 typedMeasure("AMOUNT", "double"))
            .setSource("tok", principal(), "List1", "ORDERS", List.of("STATE"), null, "AMOUNT",
                       null, null, null, false, "", "Average", null));
      assertTrue(unknown.getMessage().contains("Average"), unknown.getMessage());

      Exception noMeasure = assertThrows(IllegalArgumentException.class, () ->
         harness(mock(SelectionListVSAssembly.class), listService, null, null, null)
            .setSource("tok", principal(), "List1", "ORDERS", List.of("STATE"), null, null, null,
                       null, null, false, "", "Sum", null));
      assertTrue(noMeasure.getMessage().contains("'measure'"), noMeasure.getMessage());
      verify(listService, never()).setSelectionListPropertyModel(
         any(), any(), any(), any(), any(), any());
   }

   @Test
   void refusesAFormulaForARangeSlider() throws Exception {
      Exception thrown = assertThrows(IllegalArgumentException.class, () ->
         harness(mock(TimeSliderVSAssembly.class), null, null,
                 mock(RangeSliderPropertyDialogService.class), null)
            .setSource("tok", principal(), "Slider1", "ORDERS", List.of("ORDER_DATE"), null, null,
                       null, null, null, false, "", "Sum", null));

      assertTrue(thrown.getMessage().contains("'formula'"), thrown.getMessage());
   }
}
