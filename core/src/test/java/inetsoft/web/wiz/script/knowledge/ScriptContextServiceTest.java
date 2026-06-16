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
package inetsoft.web.wiz.script.knowledge;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.script.model.ScriptContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@WizAgentTestSupport
class ScriptContextServiceTest {

   private final ScriptContextService service = new ScriptContextService();

   @Test
   void contextListsAssemblies() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "chart1");
      vs.addAssembly(chart);
      TextVSAssembly text = new TextVSAssembly(vs, "text1");
      vs.addAssembly(text);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ScriptContext ctx = service.context(rvs);

      assertTrue(ctx.assemblies().stream().anyMatch(a -> "chart1".equals(a.name())));
      assertTrue(ctx.assemblies().stream().anyMatch(a -> "text1".equals(a.name())));
   }

   @Test
   void contextAssemblyTypeIsHumanReadable() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "myChart");
      vs.addAssembly(chart);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ScriptContext ctx = service.context(rvs);
      ScriptContext.AssemblyEntry chartEntry = ctx.assemblies().stream()
         .filter(a -> "myChart".equals(a.name()))
         .findFirst().orElseThrow();

      assertEquals("chart", chartEntry.type());
   }

   @Test
   void contextIncludesKnownGlobals() {
      Viewsheet vs = new Viewsheet();
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ScriptContext ctx = service.context(rvs);

      assertTrue(ctx.globals().contains("runQuery"));
      assertTrue(ctx.globals().contains("setCellValue"));
      assertTrue(ctx.globals().contains("saveWorksheet"));
      assertTrue(ctx.globals().contains("refreshData"));
   }

   @Test
   void contextIncludesStandardContextVars() {
      Viewsheet vs = new Viewsheet();
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ScriptContext ctx = service.context(rvs);

      assertTrue(ctx.contextVars().contains("thisViewsheet"));
      assertTrue(ctx.contextVars().contains("parameter"));
      assertTrue(ctx.contextVars().contains("_USER_"));
      assertTrue(ctx.contextVars().contains("_ROLES_"));
      assertTrue(ctx.contextVars().contains("event"));
   }

   @Test
   void emptyViewsheetHasNoAssemblies() {
      Viewsheet vs = new Viewsheet();
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ScriptContext ctx = service.context(rvs);
      assertTrue(ctx.assemblies().isEmpty());
   }
}
