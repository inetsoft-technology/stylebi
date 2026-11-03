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
import inetsoft.uql.viewsheet.GaugeVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.ViewsheetVSAssemblyInfo;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Point;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80150 Part B: {@link ViewsheetObjectPropertyDialogService#setViewsheetPropertyModel} over
 * a real embedded {@code Viewsheet} and the real {@code VSObjectPropertyService}. The embedded
 * dashboard's info is cloned before it is edited (as Tab/Submit do), so a rename that
 * {@code editObjectProperty} refuses must not leave the other fields of the same dialog
 * written.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class ViewsheetObjectPropertyDialogServiceTest {
   private static final String EMBED = "Embedded1";

   @BeforeEach
   void setup() throws Exception {
      coreLifecycle = mock(CoreLifecycleService.class);
      VSObjectPropertyService propertyService = new VSObjectPropertyService(
         coreLifecycle, null, null, null, null, null, null, null,
         mock(QueryManagerService.class));
      ViewsheetService engine = mock(ViewsheetService.class);
      rvs = mock(RuntimeViewsheet.class);
      service = new ViewsheetObjectPropertyDialogService(
         engine, propertyService, new VSDialogService(null, null));

      viewsheet = new Viewsheet();
      embedded = new Viewsheet().createVSAssembly(EMBED);
      viewsheet.addAssembly(embedded);
      viewsheet.addAssembly(new GaugeVSAssembly(viewsheet, "Taken"));

      when(engine.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getWriteRevision()).thenReturn(0);
      info().setPixelOffset(new Point(10, 20));
   }

   private ViewsheetVSAssemblyInfo info() {
      return (ViewsheetVSAssemblyInfo) embedded.getVSAssemblyInfo();
   }

   private ViewsheetObjectPropertyDialogModel read() throws Exception {
      return service.getViewsheetPropertyModel("rt", EMBED, null);
   }

   private void write(ViewsheetObjectPropertyDialogModel model) throws Exception {
      service.setViewsheetPropertyModel("rt", EMBED, model, null, null,
                                        mock(CommandDispatcher.class));
   }

   /** The model as the dialog would post it: the read model with the given fields changed. */
   private ViewsheetObjectPropertyDialogModel edited(String name, String visible, boolean primary,
                                                    int left, int top, String script)
      throws Exception
   {
      ViewsheetObjectPropertyDialogModel model = read();
      BasicGeneralPaneModel basic = model.generalPropPaneModel().getBasicGeneralPaneModel();
      basic.setName(name);
      basic.setVisible(visible);
      basic.setPrimary(primary);
      model.sizePositionPaneModel().setLeft(left);
      model.sizePositionPaneModel().setTop(top);

      return ViewsheetObjectPropertyDialogModel.builder().from(model)
         .vsAssemblyScriptPaneModel(VSAssemblyScriptPaneModel.builder()
            .scriptEnabled(script != null).expression(script == null ? "" : script).build())
         .build();
   }

   @Test
   void refusedDuplicateRenameLeavesTheRestOfThePatchUnwritten() throws Exception {
      String visibleBefore = info().getVisibleValue();
      boolean primaryBefore = info().isPrimary();
      Point offsetBefore = new Point(info().getPixelOffset());

      write(edited("Taken", "false", !primaryBefore, 300, 400, null));

      verify(coreLifecycle).sendMessage(contains("already exists"),
                                        eq(MessageCommand.Type.ERROR), any());
      assertEquals(EMBED, info().getAbsoluteName());
      assertEquals(visibleBefore, info().getVisibleValue());
      assertEquals(primaryBefore, info().isPrimary());
      assertEquals(offsetBefore, info().getPixelOffset());
   }

   @Test
   void refusedInvalidNameLeavesTheRestOfThePatchUnwritten() throws Exception {
      String visibleBefore = info().getVisibleValue();

      write(edited("1bad", "false", true, 300, 400, null));

      verify(coreLifecycle).sendMessage(contains("not a valid assembly name"),
                                        eq(MessageCommand.Type.ERROR), any());
      assertEquals(visibleBefore, info().getVisibleValue());
      assertEquals(new Point(10, 20), info().getPixelOffset());
   }

   @Test
   void writeOnAScriptLessEmbedAppliesVisiblePrimaryLeftAndTop() throws Exception {
      write(edited(EMBED, "false", true, 300, 400, null));

      verify(coreLifecycle, never()).sendMessage(anyString(), any(), any());
      assertEquals("false", info().getVisibleValue());
      assertTrue(info().isPrimary());
      assertEquals(new Point(300, 400), info().getPixelOffset());
      assertEquals(EMBED, info().getAbsoluteName());
   }

   @Test
   void scriptChangeClearsHiddenActionsLikeEveryOtherPropertyDialog() throws Exception {
      info().getActionNames().add("hiddenAction");

      write(edited(EMBED, "true", false, 10, 20, "var x = 1;"));

      assertEquals("var x = 1;", info().getScript());
      assertTrue(info().isScriptEnabled());
      assertTrue(info().getActionNames().isEmpty(), info().getActionNames().toString());
   }

   private CoreLifecycleService coreLifecycle;
   private RuntimeViewsheet rvs;
   private ViewsheetObjectPropertyDialogService service;
   private Viewsheet viewsheet;
   private Viewsheet embedded;
}
