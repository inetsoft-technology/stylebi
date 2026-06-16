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
package inetsoft.web.wiz.script;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@WizAgentTestSupport
class ScriptWriteServiceTest {

   private final ScriptWriteService service = new ScriptWriteService();

   @Test
   void writeVsInit() {
      Viewsheet vs = new Viewsheet();
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      service.write(rvs, ScriptTarget.parse("vs-init"), "x = 1;");
      assertEquals("x = 1;", vs.getViewsheetInfo().getOnInit());
   }

   @Test
   void writeVsLoad() {
      Viewsheet vs = new Viewsheet();
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      service.write(rvs, ScriptTarget.parse("vs-load"), "loadIt();");
      assertEquals("loadIt();", vs.getViewsheetInfo().getOnLoad());
   }

   @Test
   void writeAssemblyScript() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "chart1");
      vs.addAssembly(chart);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      service.write(rvs, ScriptTarget.parse("assembly:chart1"), "myScript();");
      assertEquals("myScript();", chart.getVSAssemblyInfo().getScript());
   }

   @Test
   void writeAssemblyOnClick() {
      Viewsheet vs = new Viewsheet();
      TextVSAssembly text = new TextVSAssembly(vs, "btn1");
      vs.addAssembly(text);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      service.write(rvs, ScriptTarget.parse("assembly:btn1:onClick"), "doSomething();");
      assertEquals("doSomething();", ((TextVSAssemblyInfo) text.getVSAssemblyInfo()).getOnClick());
   }

   @Test
   void setEnabledTogglesScriptEnabled() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "chart1");
      chart.getVSAssemblyInfo().setScriptEnabled(true);
      vs.addAssembly(chart);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      service.setEnabled(rvs, ScriptTarget.parse("assembly:chart1"), false);
      assertFalse(chart.getVSAssemblyInfo().isScriptEnabled());

      service.setEnabled(rvs, ScriptTarget.parse("assembly:chart1"), true);
      assertTrue(chart.getVSAssemblyInfo().isScriptEnabled());
   }

   @Test
   void setEnabledOnVsInitThrows() {
      Viewsheet vs = new Viewsheet();
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      assertThrows(IllegalArgumentException.class,
         () -> service.setEnabled(rvs, ScriptTarget.parse("vs-init"), true));
   }

   @Test
   void writeNullClearsScript() {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setOnInit("existing();");
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      service.write(rvs, ScriptTarget.parse("vs-init"), null);
      assertEquals("", vs.getViewsheetInfo().getOnInit());
   }

   @Test
   void writeOnClickForNonClickableThrows() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "chart1");
      vs.addAssembly(chart);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      assertThrows(IllegalArgumentException.class,
         () -> service.write(rvs, ScriptTarget.parse("assembly:chart1:onClick"), "fn();"));
   }
}
