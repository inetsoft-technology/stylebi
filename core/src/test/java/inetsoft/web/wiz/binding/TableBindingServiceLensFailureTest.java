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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.FormatInfo;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo;
import inetsoft.util.script.ScriptException;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #78406 (round 2): a Crosstab that did not render is refused by name where the write needs
 * its lens, and is not refused where it does not (clearing a label needs no lens).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TableBindingServiceLensFailureTest {
   @Test
   void hidingACrosstabColumnOnAFailedCrosstabIsANamedRefusalForBothOutcomes() throws Exception {
      for(Failure failure : Failure.values()) {
         TableBindingService service = service(failure);

         IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> service.setCrosstabColumnVisibility(
               "tok", principal(), "Crosstab1", List.of("Region"), null, false));

         assertTrue(thrown.getMessage().contains("'Crosstab1' did not render"),
                    thrown.getMessage());
         assertEquals(failure == Failure.THROWS, thrown.getMessage().contains("script blew up"),
                      thrown.getMessage());
      }
   }

   @Test
   void showingEveryColumnNeedsNoLensSoAFailedCrosstabIsNotRefused() throws Exception {
      TableBindingService.TableWrite write = service(Failure.THROWS)
         .setCrosstabColumnVisibility("tok", principal(), "Crosstab1", null, null, true);

      assertEquals(List.of("shown every hidden column"), write.applied());
   }

   @Test
   void relabellingACrosstabColumnOnAFailedCrosstabIsANamedRefusalForBothOutcomes()
      throws Exception
   {
      for(Failure failure : Failure.values()) {
         TableBindingService service = service(failure);

         IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> service.setColumnLabels(
               "tok", principal(), "Crosstab1", Map.of("Region", "Area"), null));

         assertTrue(thrown.getMessage().contains("'Crosstab1' did not render"),
                    thrown.getMessage());
      }
   }

   /** Clearing a label writes no header path, so it must not be refused for want of a lens. */
   @Test
   void clearingACrosstabLabelNeedsNoLensSoAFailedCrosstabIsNotRefused() throws Exception {
      for(Failure failure : Failure.values()) {
         TableBindingService.TableWrite write = service(failure).setColumnLabels(
            "tok", principal(), "Crosstab1", Map.of("Region", ""), null);

         assertEquals(List.of("rows[0] -> "), write.applied());
      }
   }

   @Test
   void aMissingSandboxIsANamedRefusalNotAnIllegalState() throws Exception {
      RuntimeViewsheet rvs = rvs(null);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.empty());
      TableBindingService service = service(rvs);

      assertThrows(IllegalArgumentException.class, () -> service.setCrosstabColumnVisibility(
         "tok", principal(), "Crosstab1", List.of("Region"), null, false));
      assertThrows(IllegalArgumentException.class, () -> service.setColumnLabels(
         "tok", principal(), "Crosstab1", Map.of("Region", "Area"), null));
   }

   /** How the sandbox reports a table that did not render. */
   private enum Failure { THROWS, NULL }

   private static RuntimeViewsheet rvs(Failure failure) throws Exception {
      VSDimensionRef liveRegion = new VSDimensionRef();
      liveRegion.setGroupColumnValue("Region");
      VSCrosstabInfo crossInfo = new VSCrosstabInfo();
      crossInfo.setDesignRowHeaders(new inetsoft.uql.erm.DataRef[]{ liveRegion });

      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getVSCrosstabInfo()).thenReturn(crossInfo);
      when(assembly.getCrosstabInfo()).thenReturn(new CrosstabVSAssemblyInfo());
      when(assembly.getAbsoluteName()).thenReturn("Crosstab1");
      when(assembly.getFormatInfo()).thenReturn(new FormatInfo());

      ViewsheetSandbox sandbox = mock(ViewsheetSandbox.class);

      if(failure == Failure.THROWS) {
         when(sandbox.getVSTableLens(anyString(), anyBoolean()))
            .thenThrow(new ScriptException("script blew up"));
      }
      else if(failure == Failure.NULL) {
         when(sandbox.getVSTableLens(anyString(), anyBoolean())).thenReturn(null);
      }

      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(sandbox));
      return rvs;
   }

   private static TableBindingService service(Failure failure) throws Exception {
      return service(rvs(failure));
   }

   private static TableBindingService service(RuntimeViewsheet rvs) throws Exception {
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.mutate(anyString(), any(Principal.class), any())).thenAnswer(inv -> {
         ViewsheetSessionService.Mutation mutation = inv.getArgument(2);
         mutation.run(rvs, "rt1", null);
         return null;
      });

      CrosstabBindingModel model = new CrosstabBindingModel();
      TableBindingMutator.setShelf(
         model, "rows", List.of(new FieldRef("Region", "dimension", null, null, null)));
      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenReturn(model);

      return new TableBindingService(
         sessions, binding, mock(VSBindingModelService.class),
         mock(DataRefModelFactoryService.class), mock(HideColumnsDialogService.class),
         mock(VSAssemblyInfoHandler.class));
   }

   private static Principal principal() {
      Principal principal = mock(Principal.class);
      when(principal.getName()).thenReturn("tester");
      return principal;
   }
}
