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
import inetsoft.uql.CompositeValue;
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.web.composer.model.vs.CalcTablePropertyDialogModel;
import inetsoft.web.composer.model.vs.PaddingPaneModel;
import inetsoft.web.composer.model.vs.VSAssemblyScriptPaneModel;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.VSDialogService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Following the default density again on a freehand table's cell padding re-seeds the tier in
 * force, as the table and crosstab dialogs do.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@ExtendWith({MockitoExtension.class})
@Tag("core")
public class CalcTablePropertyDialogServiceTest {
   @BeforeEach
   public void setup() throws Exception {
      service = new CalcTablePropertyDialogService(vsObjectPropertyService, dialogService, engine);
      when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(calcAssembly);
      when(calcAssembly.getVSAssemblyInfo()).thenReturn(calcInfo);
   }

   @AfterEach
   public void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   public void followingTheDensityAgainReseedsTheCurrentTier() throws Exception {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      calcInfo.setVizMark(VizMark.MODERN_LIGHT);
      calcInfo.setCellPadding(new Insets(4, 6, 4, 6), CompositeValue.Type.DEFAULT);
      calcInfo.setCellPadding(new Insets(10, 6, 10, 6), CompositeValue.Type.USER);

      CalcTablePropertyDialogModel model = new CalcTablePropertyDialogModel();
      // the calc model carries no script pane by default and the set path reads it
      model.setVsAssemblyScriptPaneModel(
         VSAssemblyScriptPaneModel.builder().expression("").scriptEnabled(false).build());
      PaddingPaneModel pane = model.getTableViewGeneralPaneModel().getCellPaddingPaneModel();
      pane.setTop(10);
      pane.setLeft(6);
      pane.setBottom(10);
      pane.setRight(6);
      pane.setFollowsDefault(true);

      service.setCalcTablePropertyModel("Viewsheet1", "Calc1", model, "", null, commandDispatcher);

      ArgumentCaptor<VSAssemblyInfo> captor = ArgumentCaptor.forClass(VSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(
         any(RuntimeViewsheet.class), captor.capture(), eq("Calc1"), any(), any(),
         nullable(Principal.class), any(CommandDispatcher.class));
      CalcTableVSAssemblyInfo applied = (CalcTableVSAssemblyInfo) captor.getValue();

      assertFalse(applied.isUserCellPadding());
      assertEquals(new Insets(6, 8, 6, 8), applied.getCellPadding());
   }

   private CalcTablePropertyDialogService service;
   private final CalcTableVSAssemblyInfo calcInfo = new CalcTableVSAssemblyInfo();
   @Mock VSObjectPropertyService vsObjectPropertyService;
   @Mock VSDialogService dialogService;
   @Mock ViewsheetService engine;
   @Mock RuntimeViewsheet rvs;
   @Mock Viewsheet viewsheet;
   @Mock CalcTableVSAssembly calcAssembly;
   @Mock CommandDispatcher commandDispatcher;
}
