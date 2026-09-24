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
import inetsoft.test.*;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.composer.model.vs.PaddingPaneModel;
import inetsoft.web.composer.model.vs.TableViewPropertyDialogModel;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.VSDialogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fix-round regression coverage for the cell-padding apply guard in
 * {@link TableViewPropertyDialogService#setTablePropertyModel}. An unmarked table has no stored
 * cell padding (getCellPadding() == null), but the pane always round-trips 0/0/0/0 for an
 * untouched stepper group; the guard must treat that as "no edit" rather than pinning the USER
 * tier on every save.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@ExtendWith({MockitoExtension.class})
@Tag("core")
public class TableViewPropertyDialogServiceTest {

   @BeforeEach
   public void setup() throws Exception {
      service = new TableViewPropertyDialogService(vsObjectPropertyService, dialogService, engine);

      when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getAssembly(anyString())).thenReturn(tableAssembly);
      when(tableAssembly.getVSAssemblyInfo()).thenReturn(tableVSAssemblyInfoSpy);
   }

   // Case 1: an unmarked table (no stored cell padding), pane left at 0/0/0/0. Before the fix,
   // Insets(0,0,0,0).equals(null) is false, so the guard always saw "changed" and pinned the
   // USER tier on every save, even when the author never touched a padding field.
   @Test
   public void unmarkedTableWithUntouchedPaneStaysUnopinionated() throws Exception {
      assertNull(tableVSAssemblyInfoSpy.getCellPadding(), "precondition: unmarked table has no stored padding");

      TableViewPropertyDialogModel model = modelWithCellPadding(0, 0, 0, 0, null);
      service.setTablePropertyModel("Viewsheet1", "Table1", model, "", null, commandDispatcher);

      TableVSAssemblyInfo applied = captureAppliedInfo();
      assertFalse(applied.isUserCellPadding(), "an untouched pane must not pin the USER tier");
      assertNull(applied.getCellPadding());
   }

   // Case 2: an unmarked table, pane edited to a real value. This must still mark the USER tier -
   // the fix must not swallow genuine edits along with the false-positive zero case.
   @Test
   public void unmarkedTableWithEditedPaneMarksTheUserTier() throws Exception {
      TableViewPropertyDialogModel model = modelWithCellPadding(2, 3, 4, 5, null);
      service.setTablePropertyModel("Viewsheet1", "Table1", model, "", null, commandDispatcher);

      TableVSAssemblyInfo applied = captureAppliedInfo();
      assertTrue(applied.isUserCellPadding());
      assertEquals(new Insets(2, 3, 4, 5), applied.getCellPadding());
   }

   // Case 3, the regression this guard protects against, is covered separately in
   // inetsoft.uql.viewsheet.internal.TableCellPaddingApplyRegressionTest: seedChromeDefaults is
   // protected and only reachable from TableVSAssemblyInfo's own package, but its precondition -
   // an unmarked table left with no user opinion - is exactly what this case proves the apply
   // guard produces.

   private static TableViewPropertyDialogModel modelWithCellPadding(
      int top, int left, int bottom, int right, Boolean followsDefault)
   {
      TableViewPropertyDialogModel model = new TableViewPropertyDialogModel();
      PaddingPaneModel cellPaddingPaneModel =
         model.getTableViewGeneralPaneModel().getCellPaddingPaneModel();
      cellPaddingPaneModel.setTop(top);
      cellPaddingPaneModel.setLeft(left);
      cellPaddingPaneModel.setBottom(bottom);
      cellPaddingPaneModel.setRight(right);
      cellPaddingPaneModel.setFollowsDefault(followsDefault);
      return model;
   }

   private TableVSAssemblyInfo captureAppliedInfo() throws Exception {
      ArgumentCaptor<VSAssemblyInfo> infoCaptor = ArgumentCaptor.forClass(VSAssemblyInfo.class);
      verify(vsObjectPropertyService).editObjectProperty(
         any(RuntimeViewsheet.class), infoCaptor.capture(), eq("Table1"), any(),
         any(), nullable(Principal.class), any(CommandDispatcher.class));
      return (TableVSAssemblyInfo) infoCaptor.getValue();
   }

   @Spy TableVSAssemblyInfo tableVSAssemblyInfoSpy = new TableVSAssemblyInfo();
   @Mock VSObjectPropertyService vsObjectPropertyService;
   @Mock VSDialogService dialogService;
   @Mock ViewsheetService engine;
   @Mock RuntimeViewsheet rvs;
   @Mock Viewsheet viewsheet;
   @Mock TableVSAssembly tableAssembly;
   @Mock CommandDispatcher commandDispatcher;
   private TableViewPropertyDialogService service;
}
