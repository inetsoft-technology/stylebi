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
import inetsoft.uql.viewsheet.GroupContainerVSAssembly;
import inetsoft.uql.viewsheet.RectangleVSAssembly;
import inetsoft.uql.viewsheet.TabVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.composer.vs.objects.controller.ClipboardControllerService;
import inetsoft.web.composer.vs.objects.controller.ComposerGroupService;
import inetsoft.web.composer.vs.objects.controller.ComposerObjectService;
import inetsoft.web.composer.vs.objects.controller.GroupingService;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.composer.vs.objects.event.ResizeVSObjectEvent;
import inetsoft.web.viewsheet.controller.VSRefreshService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.wiz.viewsheet.model.AssemblyNode;
import inetsoft.web.wiz.viewsheet.model.ViewsheetModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78901: the {@code resize} op refuses the assemblies whose size is re-derived by layout
 * (a group container, an embedded viewsheet) instead of reporting a resize that is silently
 * thrown away, and still resizes the ones that own their size. Uses real assembly classes, whose
 * constructors read {@code SreeEnv}, hence the Spring context.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetEditServiceResizeTest {
   @Test
   void resizeOnAGroupContainerFailsLoudRatherThanSilentlyReverting() {
      ComposerObjectService objects = mock(ComposerObjectService.class);
      ViewsheetEditService service = serviceWith("GroupContainer1", new GroupContainerVSAssembly(),
         new AssemblyNode("GroupContainer1", "GroupContainer", 20, 380, 300, 220, 0, null, true),
         objects);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.apply("tok", principal(), sized("GroupContainer1", 600, 400), ""));
      assertTrue(thrown.getMessage().contains("GroupContainer1"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("group container"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("member"), thrown.getMessage());
      verifyNoInteractions(objects);
   }

   @Test
   void resizeOnAnEmbeddedViewsheetFailsLoudRatherThanSilentlyReverting() {
      ComposerObjectService objects = mock(ComposerObjectService.class);
      ViewsheetEditService service = serviceWith("Viewsheet1", new Viewsheet(),
         new AssemblyNode("Viewsheet1", "Viewsheet", 40, 60, 100, 50, 0, null, true), objects);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.apply("tok", principal(), sized("Viewsheet1", 400, 300), ""));
      assertTrue(thrown.getMessage().contains("Viewsheet1"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("embedded viewsheet"), thrown.getMessage());
      verifyNoInteractions(objects);
   }

   /** A rectangle, a tab and a selection container own their size and still resize. */
   @ParameterizedTest
   @ValueSource(strings = { "Rectangle", "Tab", "CurrentSelection" })
   void resizeStillAppliesToAssembliesThatOwnTheirSize(String type) throws Exception {
      VSAssembly assembly = switch(type) {
         case "Rectangle" -> new RectangleVSAssembly();
         case "Tab" -> new TabVSAssembly();
         default -> new CurrentSelectionVSAssembly();
      };
      ComposerObjectService objects = mock(ComposerObjectService.class);
      ViewsheetEditService service = serviceWith("Obj1", assembly,
         new AssemblyNode("Obj1", type, 10, 10, 150, 30, 0, null, true), objects);

      service.apply("tok", principal(), sized("Obj1", 250, 40), "");

      ArgumentCaptor<ResizeVSObjectEvent> captor =
         ArgumentCaptor.forClass(ResizeVSObjectEvent.class);
      verify(objects).resizeObject(eq("rt1"), captor.capture(), any(Principal.class), any(),
                                   anyString());
      assertEquals("Obj1", captor.getValue().getName());
      assertEquals(250, captor.getValue().getWidth());
      assertEquals(40, captor.getValue().getHeight());
   }

   private static ViewsheetEditService serviceWith(String name, VSAssembly assembly,
                                                   AssemblyNode node,
                                                   ComposerObjectService objects)
   {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      Viewsheet vs = mock(Viewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(vs.getAssembly(name)).thenReturn(assembly);

      ViewsheetReadService reader = mock(ViewsheetReadService.class);
      when(reader.read(any())).thenReturn(new ViewsheetModel("vs", List.of(node)));

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

      try {
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return new ViewsheetEditService(sessions, objects, mock(ClipboardControllerService.class),
                                      mock(VSObjectPropertyService.class), reader,
                                      mock(ComposerGroupService.class),
                                      mock(GroupingService.class),
                                      mock(VSRefreshService.class),
                                      mock(CoreLifecycleService.class));
   }

   private static EditRequest sized(String assembly, Integer width, Integer height) {
      return new EditRequest("resize", assembly, null, null, width, height, null, null, null,
                             null, null, null, null, null);
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
