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
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.EmbeddedTableVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.web.composer.vs.objects.controller.ClipboardControllerService;
import inetsoft.web.composer.vs.objects.controller.ComposerGroupService;
import inetsoft.web.composer.vs.objects.controller.ComposerObjectService;
import inetsoft.web.composer.vs.objects.controller.GroupingService;
import inetsoft.web.composer.vs.objects.controller.VSObjectPropertyService;
import inetsoft.web.viewsheet.controller.VSRefreshService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80479: the add op flags a created Embedded Table so its worksheet table is not mirrored;
 * a plain Table or any other type stays unflagged. Real viewsheet (needs the Spring context),
 * mocked object factory that "creates" the named assembly.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome()
@Tag("core")
class ViewsheetEditServiceEmbeddedTableFlagTest {
   @Test
   void addFlagsACreatedEmbeddedTableButNotOtherTypes() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new EmbeddedTableVSAssembly(vs, "EmbeddedTable1"));
      vs.addAssembly(new TableVSAssembly(vs, "Table1"));
      vs.addAssembly(new ChartVSAssembly(vs, "Chart1"));
      ComposerObjectService objects = mock(ComposerObjectService.class);
      when(objects.addNewObject(eq("rt1"), any(), any(Principal.class), any(), anyString()))
         .thenReturn("EmbeddedTable1", "Table1", "Chart1");
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(rvs, "rt1", null);
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());
      ViewsheetEditService service = new ViewsheetEditService(
         sessions, objects, mock(ClipboardControllerService.class),
         mock(VSObjectPropertyService.class), mock(ViewsheetReadService.class),
         mock(ComposerGroupService.class), mock(GroupingService.class),
         mock(VSRefreshService.class), mock(CoreLifecycleService.class));

      for(int type : new int[] { 1000, 1000, 1000 }) {
         service.apply("tok", () -> "admin", new EditRequest(
            "add", null, 40, 60, null, null, null, null, null, null, null, null, type, null), "");
      }

      assertTrue(((TableVSAssemblyInfo) vs.getAssembly("EmbeddedTable1").getVSAssemblyInfo())
                    .isEmbeddedTable());
      assertFalse(((TableVSAssemblyInfo) vs.getAssembly("Table1").getVSAssemblyInfo())
                     .isEmbeddedTable());
   }
}
