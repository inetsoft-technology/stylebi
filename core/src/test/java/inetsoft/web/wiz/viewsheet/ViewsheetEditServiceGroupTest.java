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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.composer.vs.VSObjectTreeService;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.composer.vs.objects.controller.*;
import inetsoft.web.viewsheet.controller.VSRefreshService;
import inetsoft.web.viewsheet.service.*;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import inetsoft.web.wiz.viewsheet.model.AssemblyNode;
import inetsoft.web.wiz.viewsheet.model.ViewsheetModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Point;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79199: edit(op:'group') must refuse a Selection Container. Real ViewsheetEditService, real
 * ComposerGroupService and a real Viewsheet; only lifecycle/tree/layout collaborators, the
 * sandbox and the session lookup are mocks.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetEditServiceGroupTest {
   private Viewsheet vs;
   private ViewsheetEditService service;

   private void setup() throws Exception {
      vs = new Viewsheet();
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(mock(ViewsheetSandbox.class)));
      add(new TableVSAssembly(vs, "OrderTable"), 300, 20);
      add(new TextVSAssembly(vs, "Text1"), 300, 300);
      add(new SelectionListVSAssembly(vs, "ListA"), 20, 20);
      add(new SelectionListVSAssembly(vs, "ListB"), 20, 220);
      CurrentSelectionVSAssembly cs = new CurrentSelectionVSAssembly(vs, "CurrentSelection1");
      add(cs, 20, 20);
      cs.setAssemblies(new String[] { "ListA", "ListB" });
      add(new CurrentSelectionVSAssembly(vs, "CurrentSelection2"), 600, 20);

      List<AssemblyNode> nodes = new ArrayList<>();

      for(Assembly a : vs.getAssemblies()) {
         nodes.add(new AssemblyNode(a.getName(), "Text", 0, 0, 10, 10, 0, null, true));
      }

      ViewsheetReadService reader = mock(ViewsheetReadService.class);
      when(reader.read(any())).thenReturn(new ViewsheetModel("vs", nodes));

      ViewsheetService vss = mock(ViewsheetService.class);
      when(vss.getViewsheet(anyString(), any())).thenReturn(rvs);
      VSObjectPropertyService props = mock(VSObjectPropertyService.class);
      when(props.getSupportedPopComponents(any(), any())).thenReturn(new String[0]);
      ComposerGroupService groups = new ComposerGroupService(
         mock(CoreLifecycleService.class), vss, mock(VSObjectTreeService.class), props,
         mock(VSCompositionService.class), mock(VSLayoutService.class));
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      doAnswer(inv -> {
         ViewsheetSessionService.Mutation m = inv.getArgument(2);
         m.run(rvs, "rt1", mock(CapturingCommandDispatcher.class));
         return List.of();
      }).when(sessions).mutate(anyString(), any(Principal.class), any());
      service = new ViewsheetEditService(sessions, mock(ComposerObjectService.class),
                                         mock(ClipboardControllerService.class),
                                         mock(VSObjectPropertyService.class), reader, groups,
                                         mock(GroupingService.class), mock(VSRefreshService.class),
                                         mock(CoreLifecycleService.class));
   }

   private void add(VSAssembly a, int x, int y) {
      a.setPixelOffset(new Point(x, y));
      vs.addAssembly(a);
   }

   private static EditRequest grp(String... names) {
      return new EditRequest("group", null, null, null, null, null, null, null, null, null,
                             List.of(names), null, null, null);
   }

   private void apply(String... names) throws Exception {
      service.apply("tok", () -> "admin", grp(names), "");
   }

   private Assembly container(String name) {
      return ((VSAssembly) vs.getAssembly(name)).getContainer();
   }

   @Test
   void selectionContainerIsRefusedNamingIt() throws Exception {
      setup();
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> apply("CurrentSelection1", "OrderTable"));
      assertTrue(e.getMessage().contains("CurrentSelection1"), e.getMessage());
      assertTrue(e.getMessage().contains("Selection Container"), e.getMessage());
      assertNull(container("CurrentSelection1"));
      assertNull(container("OrderTable"));
   }

   @Test
   void selectionContainerAmongPlainAssembliesGroupsNothing() throws Exception {
      setup();
      int count = vs.getAssemblies().length;
      assertThrows(IllegalArgumentException.class,
                   () -> apply("OrderTable", "Text1", "CurrentSelection2"));
      assertEquals(count, vs.getAssemblies().length, "no GroupContainer may be created");
      assertNull(container("OrderTable"));
      assertNull(container("Text1"));
      assertNull(container("CurrentSelection2"));
   }

   @Test
   void twoPlainAssembliesStillGroup() throws Exception {
      setup();
      apply("OrderTable", "Text1");
      assertInstanceOf(GroupContainerVSAssembly.class, container("OrderTable"));
      assertInstanceOf(GroupContainerVSAssembly.class, container("Text1"));
   }

   @Test
   void unknownNameIsARefusalNotANullPointer() throws Exception {
      setup();
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> apply("OrderTable", "Nope"));
      assertTrue(e.getMessage().contains("Nope"), e.getMessage());
      assertNull(container("OrderTable"));
   }

   @Test
   void sameNameTwiceIsRefusedInsteadOfBuildingAOneMemberGroup() throws Exception {
      setup();
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> apply("OrderTable", "OrderTable"));
      assertTrue(e.getMessage().contains("two different"), e.getMessage());
      assertNull(container("OrderTable"));
   }

   @Test
   void otherSelectionContainersAndTheirChildrenAreUntouchedByARefusal() throws Exception {
      setup();
      assertThrows(IllegalArgumentException.class, () -> apply("CurrentSelection1", "Text1"));
      assertEquals(Arrays.asList("ListA", "ListB"),
                   Arrays.asList(((CurrentSelectionVSAssembly) vs.getAssembly("CurrentSelection1"))
                                    .getAssemblies()));
      assertSame(vs.getAssembly("CurrentSelection1"), container("ListA"));
      assertNull(container("CurrentSelection2"));
   }
}
