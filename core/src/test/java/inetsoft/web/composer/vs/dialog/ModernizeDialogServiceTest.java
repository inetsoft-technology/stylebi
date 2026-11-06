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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.ScriptFormattingScanner;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.util.Catalog;
import inetsoft.web.composer.model.vs.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("core")
class ModernizeDialogServiceTest {
   @BeforeEach
   void setup() throws Exception {
      service = new ModernizeDialogService(viewsheetService, saveService);
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setVizMark(null);
      entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "Sales",
                             null);

      when(viewsheetService.getViewsheet(anyString(), nullable(Principal.class)))
         .thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getEntry()).thenReturn(entry);
      when(saveService.validateSaveViewSheet(anyString(), any(), nullable(Principal.class)))
         .thenReturn(SaveViewsheetDialogModelValidator.builder().build());
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   private ScriptFormattingScanner.Report report(boolean dashboard, String... names) {
      return new ScriptFormattingScanner.Report(new TreeSet<>(java.util.List.of(names)),
                                                dashboard);
   }

   @Test
   void scriptWarningIsNullWhenNothingCounts() {
      assertNull(ModernizeDialogService.scriptWarning(report(false), Catalog.getCatalog()));
   }

   @Test
   void scriptWarningUsesSingularAndDashboardForms() {
      Catalog catalog = Catalog.getCatalog();
      assertEquals("Scripts set formatting on 1 component. The reset does not change scripts, " +
                      "so a script that sets formatting can still override the new defaults.",
                   ModernizeDialogService.scriptWarning(report(false, "A"), catalog));
      assertEquals("Scripts set formatting on 2 components and in the dashboard script. " +
                      "The reset does not change scripts, so a script that sets formatting " +
                      "can still override the new defaults.",
                   ModernizeDialogService.scriptWarning(report(true, "A", "B"), catalog));
      assertEquals("The dashboard script sets formatting. The reset does not change scripts, " +
                      "so a script that sets formatting can still override the new defaults.",
                   ModernizeDialogService.scriptWarning(report(true), catalog));
   }

   @Test
   void modelReflectsTheSheet() throws Exception {
      TextVSAssembly text = new TextVSAssembly(viewsheet, "Text1");
      viewsheet.addAssembly(text);
      text.getVSAssemblyInfo().setVizMark(null);

      ModernizeDialogModel model = service.getModel("rid", principal);
      assertTrue(model.isKeepAvailable());
      assertFalse(model.isNeverSaved());
      assertFalse(model.isEmbedsDashboards());
      assertEquals("Sales (modern)", model.getSuggestedName());
      assertNotNull(model.getParentId());

      text.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);
      viewsheet.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);
      assertFalse(service.getModel("rid", principal).isKeepAvailable());

      Viewsheet embedded = new Viewsheet();
      embedded.createVSAssembly("Embedded1");
      viewsheet.addAssembly(embedded);
      assertTrue(service.getModel("rid", principal).isEmbedsDashboards());

      entry = new AssetEntry(AssetRepository.TEMPORARY_SCOPE, AssetEntry.Type.VIEWSHEET,
                             "Sales", null);
      when(rvs.getEntry()).thenReturn(entry);
      assertTrue(service.getModel("rid", principal).isNeverSaved());
   }

   @Test
   void suggestedNameSkipsNamesTheSaveValidatorReportsTaken() throws Exception {
      when(saveService.validateSaveViewSheet(anyString(), argThat(
         m -> m != null && "Sales (modern)".equals(m.getName())), nullable(Principal.class)))
         .thenReturn(SaveViewsheetDialogModelValidator.builder().alreadyExists("x").build());

      assertEquals("Sales (modern 2)", service.getModel("rid", principal).getSuggestedName());
   }

   @Test
   void suggestedNameFallsBackToTheBaseNameWhenTheValidatorFails() throws Exception {
      when(saveService.validateSaveViewSheet(anyString(), any(), nullable(Principal.class)))
         .thenThrow(new IllegalStateException("repository down"));

      assertEquals("Sales (modern)", service.getModel("rid", principal).getSuggestedName());
      verify(saveService, times(1)).validateSaveViewSheet(anyString(), any(),
                                                          nullable(Principal.class));
   }

   @Test
   void checkNameMapsTheSaveValidator() throws Exception {
      when(saveService.validateSaveViewSheet(anyString(), any(), nullable(Principal.class)))
         .thenReturn(SaveViewsheetDialogModelValidator.builder()
                        .alreadyExists("exists").permissionDenied("denied").build());
      ModernCopyRequest request = new ModernCopyRequest();
      request.setName("  Sales (modern)  ");

      ModernCopyNameCheck check = service.checkName("rid", request, principal);

      assertTrue(check.isExists());
      assertEquals("denied", check.getPermissionDenied());
      assertNull(check.getInvalidName());
      ArgumentCaptor<SaveViewsheetDialogModel> captor =
         ArgumentCaptor.forClass(SaveViewsheetDialogModel.class);
      verify(saveService).validateSaveViewSheet(eq("rid"), captor.capture(),
                                                nullable(Principal.class));
      assertEquals("Sales (modern)", captor.getValue().getName());
   }

   @Test
   void checkNameRefusesInvalidNamesWithoutValidating() throws Exception {
      ModernCopyRequest request = new ModernCopyRequest();
      request.setName("Sales/1");
      assertNotNull(service.checkName("rid", request, principal).getInvalidName());

      request.setName("  _Sales");
      assertNotNull(service.checkName("rid", request, principal).getInvalidName());

      verify(saveService, never()).validateSaveViewSheet(anyString(), any(),
                                                         nullable(Principal.class));
   }

   @Test
   void checkNamePassesAValidFreeName() throws Exception {
      ModernCopyRequest request = new ModernCopyRequest();
      request.setName("Sales 2");
      ModernCopyNameCheck check = service.checkName("rid", request, principal);
      assertFalse(check.isExists());
      assertNull(check.getInvalidName());
   }

   private ModernizeDialogService service;
   private Viewsheet viewsheet;
   private AssetEntry entry;
   private final Principal principal = mock(Principal.class);
   @Mock private ViewsheetService viewsheetService;
   @Mock private SaveViewsheetDialogService saveService;
   @Mock private RuntimeViewsheet rvs;
}
