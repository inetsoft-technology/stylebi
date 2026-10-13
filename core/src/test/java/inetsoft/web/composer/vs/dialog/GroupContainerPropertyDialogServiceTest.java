/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.GroupContainerVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.viewsheet.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome()
@ExtendWith(MockitoExtension.class)
@Tag("core")
class GroupContainerPropertyDialogServiceTest {
   @BeforeEach
   void setUp() throws Exception {
      // the real property service; with no sandbox it returns right after merging the dialog
      // info into the live assembly (setVSAssemblyInfo -> copyInfo)
      VSObjectPropertyService propertyService = new VSObjectPropertyService(
         mock(CoreLifecycleService.class), null, null, null, null, null, null, null,
         mock(QueryManagerService.class));
      service = new GroupContainerPropertyDialogService(
         propertyService, viewsheetService, mock(ImagePreviewPaneService.class),
         coreLifecycleService, new VSDialogService(null, null));

      viewsheet = new Viewsheet();
      group = new HintRecordingGroup(viewsheet, "Group1");
      text1 = new TextVSAssembly(viewsheet, "Text1");
      text2 = new TextVSAssembly(viewsheet, "Text2");
      text1.getVSAssemblyInfo().setPixelOffset(new Point(10, 10));
      text1.getVSAssemblyInfo().setPixelSize(new Dimension(100, 20));
      text2.getVSAssemblyInfo().setPixelOffset(new Point(10, 30));
      text2.getVSAssemblyInfo().setPixelSize(new Dimension(100, 30));
      group.setAssemblies(new String[] { "Text1", "Text2" });
      group.getVSAssemblyInfo().setPixelOffset(new Point(10, 10));
      group.getVSAssemblyInfo().setPixelSize(new Dimension(100, 50));
      viewsheet.addAssembly(text1);
      viewsheet.addAssembly(text2);
      viewsheet.addAssembly(group);

      when(viewsheetService.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      // the model part of the real layoutViewsheet()
      lenient().doAnswer(inv -> {
         viewsheet.layout(new String[] { inv.getArgument(4) });
         return false;
      }).when(coreLifecycleService).layoutViewsheet(any(), any(), any(), any(),
                                                     anyString(), any());
   }

   /**
    * Bug #78302: a script edit in the group container dialog must reach editObjectProperty as a
    * change, so the runtime values set by the old script are reset.
    */
   @Test
   void scriptEditResetsRuntimeValuesOfOldScript() throws Exception {
      GroupContainerVSAssemblyInfo live = (GroupContainerVSAssemblyInfo) group.getVSAssemblyInfo();
      live.setScript("Text1.visible = false;");
      live.setScriptEnabled(true);
      // what the old script left at runtime
      text1.getVSAssemblyInfo().setVisible("false");
      live.setVisible("false");

      GroupContainerPropertyDialogModel model = getModel();
      model.setVsAssemblyScriptPane(VSAssemblyScriptPaneModel.builder()
                                       .scriptEnabled(true)
                                       .expression("")
                                       .build());
      save(model);

      assertSame(live, group.getVSAssemblyInfo());
      assertEquals("", live.getScript());
      assertNotEquals(VSAssembly.NONE_CHANGED, group.lastHint,
                      "the script edit must be detected as a change");
      assertTrue(text1.getVSAssemblyInfo().isVisible(),
                 "runtime visibility set by the old script must be reset");
      assertTrue(live.isVisible(), "container runtime values must be reset");
   }

   /**
    * Bug #78302: the layout during the dialog save fits the live container to its moved
    * children, the edited copy must not restore the stale size it was opened with.
    */
   @Test
   void moveKeepsContainerFittedToChildren() throws Exception {
      // stored size out of sync with the children's bounds (10,10)-(110,60)
      group.getVSAssemblyInfo().setPixelSize(new Dimension(100, 60));

      GroupContainerPropertyDialogModel model = getModel();
      SizePositionPaneModel position =
         model.getGroupContainerGeneralPane().getSizePositionPaneModel();
      position.setLeft(50);
      position.setTop(50);
      save(model);

      VSAssemblyInfo live = group.getVSAssemblyInfo();
      assertEquals(new Point(50, 50), text1.getVSAssemblyInfo().getPixelOffset());
      assertEquals(new Point(50, 70), text2.getVSAssemblyInfo().getPixelOffset());
      assertEquals(new Point(50, 50), live.getPixelOffset());
      assertEquals(new Dimension(100, 50), live.getPixelSize());
   }

   private GroupContainerPropertyDialogModel getModel() throws Exception {
      return service.getGroupContainerPropertyDialogModel("Viewsheet1", "Group1", null);
   }

   private void save(GroupContainerPropertyDialogModel model) throws Exception {
      service.setGroupContainerPropertyDialogModel("Viewsheet1", "Group1", model, "", null,
                                                   commandDispatcher);
   }

   private static class HintRecordingGroup extends GroupContainerVSAssembly {
      HintRecordingGroup(Viewsheet vs, String name) {
         super(vs, name);
      }

      @Override
      public int setVSAssemblyInfo(VSAssemblyInfo info) {
         lastHint = super.setVSAssemblyInfo(info);
         return lastHint;
      }

      int lastHint = -1;
   }

   @Mock ViewsheetService viewsheetService;
   @Mock CoreLifecycleService coreLifecycleService;
   @Mock RuntimeViewsheet rvs;
   @Mock CommandDispatcher commandDispatcher;
   private Viewsheet viewsheet;
   private HintRecordingGroup group;
   private TextVSAssembly text1;
   private TextVSAssembly text2;
   private GroupContainerPropertyDialogService service;
}
