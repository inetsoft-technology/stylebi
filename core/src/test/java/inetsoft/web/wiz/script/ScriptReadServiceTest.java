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
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.script.model.ScriptInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@WizAgentTestSupport
class ScriptReadServiceTest {

   private final ScriptReadService service = new ScriptReadService();

   // -------------------------------------------------------------------------
   // list()
   // -------------------------------------------------------------------------

   @Test
   void listReturnsVsInitVsLoadAndAssemblyTargets() {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setOnInit("initScript();");
      vs.getViewsheetInfo().setOnLoad("loadScript();");

      // chart assembly — has a script, no onClick
      ChartVSAssembly chart = new ChartVSAssembly(vs, "chart1");
      chart.getVSAssemblyInfo().setScript("chartScript();");
      chart.getVSAssemblyInfo().setScriptEnabled(true);
      vs.addAssembly(chart);

      // text assembly — clickable (has onClick)
      TextVSAssembly text = new TextVSAssembly(vs, "text1");
      text.getVSAssemblyInfo().setScript("textScript();");
      ((TextVSAssemblyInfo) text.getVSAssemblyInfo()).setOnClick("onClickHandler();");
      vs.addAssembly(text);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      List<ScriptInfo> targets = service.list(rvs);

      // must contain vs-init and vs-load
      assertTrue(targets.stream().anyMatch(t -> "vs-init".equals(t.target())));
      assertTrue(targets.stream().anyMatch(t -> "vs-load".equals(t.target())));

      // chart script
      assertTrue(targets.stream().anyMatch(t -> "assembly:chart1".equals(t.target())));

      // text script + onClick
      assertTrue(targets.stream().anyMatch(t -> "assembly:text1".equals(t.target())));
      assertTrue(targets.stream().anyMatch(t -> "assembly:text1:onClick".equals(t.target())));
   }

   @Test
   void listVsInitTextMatchesViewsheetInfo() {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setOnInit("x = 1;");

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      List<ScriptInfo> targets = service.list(rvs);
      ScriptInfo init = targets.stream()
         .filter(t -> "vs-init".equals(t.target()))
         .findFirst()
         .orElseThrow();

      assertEquals("x = 1;", init.text());
      assertTrue(init.enabled());
   }

   @Test
   void listOnlyIncludesOnClickForClickableAssemblies() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "myChart");
      vs.addAssembly(chart);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      List<ScriptInfo> targets = service.list(rvs);

      // chart is not clickable — should have no onClick entry
      assertFalse(targets.stream().anyMatch(t -> "assembly:myChart:onClick".equals(t.target())));
   }

   // -------------------------------------------------------------------------
   // read()
   // -------------------------------------------------------------------------

   @Test
   void readVsInit() {
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setOnInit("initBody();");

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ScriptInfo info = service.read(rvs, ScriptTarget.parse("vs-init"));
      assertEquals("vs-init", info.target());
      assertEquals("initBody();", info.text());
   }

   @Test
   void readAssemblyScript() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "chart1");
      chart.getVSAssemblyInfo().setScript("myScript();");
      chart.getVSAssemblyInfo().setScriptEnabled(true);
      vs.addAssembly(chart);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ScriptInfo info = service.read(rvs, ScriptTarget.parse("assembly:chart1"));
      assertEquals("assembly:chart1", info.target());
      assertEquals("myScript();", info.text());
      assertTrue(info.enabled());
   }

   @Test
   void readAssemblyOnClick() {
      Viewsheet vs = new Viewsheet();
      TextVSAssembly text = new TextVSAssembly(vs, "btn1");
      ((TextVSAssemblyInfo) text.getVSAssemblyInfo()).setOnClick("doSomething();");
      vs.addAssembly(text);

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      ScriptInfo info = service.read(rvs, ScriptTarget.parse("assembly:btn1:onClick"));
      assertEquals("assembly:btn1:onClick", info.target());
      assertEquals("doSomething();", info.text());
   }

   @Test
   void readUnknownAssemblyThrows() {
      Viewsheet vs = new Viewsheet();
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      assertThrows(IllegalArgumentException.class,
         () -> service.read(rvs, ScriptTarget.parse("assembly:missing")));
   }

   // -------------------------------------------------------------------------
   // ScriptTarget.parse() round-trip
   // -------------------------------------------------------------------------

   @Test
   void parseRoundTrip() {
      assertEquals("vs-init", ScriptTarget.parse("vs-init").toString());
      assertEquals("vs-load", ScriptTarget.parse("vs-load").toString());
      assertEquals("assembly:chart1", ScriptTarget.parse("assembly:chart1").toString());
      assertEquals("assembly:text1:onClick", ScriptTarget.parse("assembly:text1:onClick").toString());
   }

   @Test
   void parseInvalidThrows() {
      assertThrows(IllegalArgumentException.class, () -> ScriptTarget.parse("unknown:foo"));
      assertThrows(IllegalArgumentException.class, () -> ScriptTarget.parse("assembly:"));
   }
}
