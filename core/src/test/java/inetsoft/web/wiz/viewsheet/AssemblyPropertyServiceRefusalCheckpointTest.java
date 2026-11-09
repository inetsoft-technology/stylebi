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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.CurrentSelectionVSAssembly;
import inetsoft.uql.viewsheet.SelectionListVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.composer.model.vs.SelectionContainerPropertyDialogModel;
import inetsoft.web.composer.model.vs.SelectionListPropertyDialogModel;
import inetsoft.web.composer.vs.dialog.*;
import inetsoft.web.viewsheet.service.VSInputService;
import inetsoft.web.viewsheet.service.VSOutputService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80802: a patch {@code set} refuses must be refused before {@code sessions.mutate}, whose
 * {@code finally} records an undo checkpoint even when the mutation throws. A refusal inside it
 * left an empty checkpoint, so the user's next undo reverted nothing.
 *
 * <p>Unlike the {@code AssemblyPropertyServiceTest} harness, {@code sessions.read} here runs its
 * lambda, and each dialog read returns a fresh model, as the real dialog services do. The guards'
 * static initializers read SreeEnv, hence the Spring context.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class AssemblyPropertyServiceRefusalCheckpointTest {
   @Test
   void refusesAnUnknownKeyWithoutMutating() throws Exception {
      Harness harness = new Harness(mock(SelectionListVSAssembly.class));

      IllegalArgumentException thrown = assertThrows(
         IllegalArgumentException.class,
         () -> harness.service.set("tok", principal(), "CategoryList",
                                   Map.of("bogusProp", 1), ""));

      assertTrue(thrown.getMessage().contains("bogusProp"), thrown.getMessage());
      harness.assertRefusedBeforeMutate();
   }

   @Test
   void refusesASelectionLayoutKeyOnAContainerWithoutMutating() throws Exception {
      Harness harness = new Harness(mock(CurrentSelectionVSAssembly.class));

      IllegalArgumentException thrown = assertThrows(
         IllegalArgumentException.class,
         () -> harness.service.set("tok", principal(), "SelectionContainer1",
                                   Map.of("titleRatio", 0.25), ""));

      assertTrue(thrown.getMessage().contains("set_selection_layout"), thrown.getMessage());
      harness.assertRefusedBeforeMutate();
   }

   /** A value refusal raised after the keys resolve, while the patch is applied to the model. */
   @Test
   void refusesABadValueWithoutMutating() throws Exception {
      Harness harness = new Harness(mock(SelectionListVSAssembly.class));

      IllegalArgumentException thrown = assertThrows(
         IllegalArgumentException.class,
         () -> harness.service.set("tok", principal(), "CategoryList",
                                   Map.of("width", 0), ""));

      assertTrue(thrown.getMessage().contains("positive number"), thrown.getMessage());
      harness.assertRefusedBeforeMutate();
   }

   @Test
   void stillWritesAnAcceptedPatchInOneMutate() throws Exception {
      Harness harness = new Harness(mock(CurrentSelectionVSAssembly.class));

      harness.service.set("tok", principal(), "SelectionContainer1",
                          Map.of("adhocEnabled", true), "");

      verify(harness.sessions).read(anyString(), any(Principal.class), any());
      verify(harness.sessions, times(1)).mutate(anyString(), any(Principal.class), any());
      ArgumentCaptor<SelectionContainerPropertyDialogModel> written =
         ArgumentCaptor.forClass(SelectionContainerPropertyDialogModel.class);
      verify(harness.container).setSelectionContainerPropertyModel(
         eq("rt1"), eq("SelectionContainer1"), written.capture(), anyString(),
         any(Principal.class), any());
      assertTrue(written.getValue().getSelectionContainerGeneralPaneModel().isAdhocEnabled());
   }

   private static Principal principal() {
      return () -> "admin";
   }

   private static final class Harness {
      Harness(VSAssembly assembly) throws Exception {
         Viewsheet vs = mock(Viewsheet.class);
         when(vs.getAssembly(anyString())).thenReturn(assembly);
         when(vs.getAllVariables()).thenReturn(new inetsoft.uql.schema.UserVariable[0]);
         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheet()).thenReturn(vs);
         when(rvs.getID()).thenReturn("rt1");

         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
         when(sessions.read(anyString(), any(Principal.class), any())).thenAnswer(invocation -> {
            ViewsheetSessionService.Read<?> read = invocation.getArgument(2);
            return read.run(rvs, "rt1", null);
         });
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());

         when(list.getSelectionListPropertyModel(anyString(), anyString(), any(Principal.class)))
            .thenAnswer(invocation -> new SelectionListPropertyDialogModel());
         when(container.getSelectionContainerPropertyModel(anyString(), anyString(),
                                                           any(Principal.class)))
            .thenAnswer(invocation -> new SelectionContainerPropertyDialogModel());

         service = new AssemblyPropertyService(
            sessions, mock(GaugePropertyDialogService.class),
            mock(ImagePropertyDialogService.class), mock(TextPropertyDialogService.class),
            mock(ChartPropertyDialogService.class), mock(TableViewPropertyDialogService.class),
            mock(CrosstabPropertyDialogService.class), list,
            mock(SelectionTreePropertyDialogService.class), mock(VSInputService.class),
            mock(RangeSliderPropertyDialogService.class),
            mock(CalendarPropertyDialogService.class), mock(TabPropertyDialogService.class),
            mock(CalcTablePropertyDialogService.class),
            mock(GroupContainerPropertyDialogService.class),
            mock(LinePropertyDialogService.class), mock(OvalPropertyDialogService.class),
            mock(RectanglePropertyDialogService.class), container,
            mock(SubmitPropertyDialogService.class), mock(VSOutputService.class),
            mock(VSColumnHandler.class), mock(DataOutputService.class),
            mock(ViewsheetObjectPropertyDialogService.class));
      }

      void assertRefusedBeforeMutate() throws Exception {
         verify(sessions).read(anyString(), any(Principal.class), any());
         verify(sessions, never()).mutate(anyString(), any(Principal.class), any());
      }

      final ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      final SelectionListPropertyDialogService list =
         mock(SelectionListPropertyDialogService.class);
      final SelectionContainerPropertyDialogService container =
         mock(SelectionContainerPropertyDialogService.class);
      final AssemblyPropertyService service;
   }
}
