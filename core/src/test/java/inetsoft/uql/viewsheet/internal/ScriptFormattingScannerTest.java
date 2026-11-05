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
package inetsoft.uql.viewsheet.internal;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScriptFormattingScannerTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   private Viewsheet sheet(String... names) {
      Viewsheet vs = new Viewsheet();

      for(String name : names) {
         VSAssembly assembly = name.startsWith("Chart") ? new ChartVSAssembly(vs, name) :
            name.startsWith("Table") ? new TableVSAssembly(vs, name) :
            new TextVSAssembly(vs, name);
         vs.addAssembly(assembly);
      }

      return vs;
   }

   private void script(Viewsheet vs, String name, String script) {
      VSAssemblyInfo info = ((VSAssembly) vs.getAssembly(name)).getVSAssemblyInfo();
      info.setScript(script);
      info.setScriptEnabled(true);
   }

   @Test
   void anotherComponentsScriptCanTargetIt() {
      Viewsheet vs = sheet("Text1", "Text2");
      script(vs, "Text2", "Text1.foreground = [255,0,0];");
      assertEquals(Set.of("Text1"), ScriptFormattingScanner.scan(vs).components());
   }

   @Test
   void anImplicitTargetIsTheComponentItself() {
      Viewsheet vs = sheet("Text1");
      script(vs, "Text1", "foreground = 0xff0000;");
      assertEquals(Set.of("Text1"), ScriptFormattingScanner.scan(vs).components());
   }

   @Test
   void aSetterCallCounts() {
      Viewsheet vs = sheet("Text1", "Text2");
      script(vs, "Text2", "Text1.setForeground(0xff0000);");
      assertEquals(Set.of("Text1"), ScriptFormattingScanner.scan(vs).components());
   }

   @Test
   void readsAndComparisonsDoNotCount() {
      Viewsheet vs = sheet("Text1");
      script(vs, "Text1", "var c = Text1.foreground; if(Text1.font == null) {}");
      assertTrue(ScriptFormattingScanner.scan(vs).isEmpty());
   }

   @Test
   void commentsAndStringsDoNotCount() {
      Viewsheet vs = sheet("Text1");
      script(vs, "Text1", "// Text1.foreground = 1;\nvar s = 'Text1.font = 2'; /* Text1.alpha = 3 */");
      assertTrue(ScriptFormattingScanner.scan(vs).isEmpty());
   }

   @Test
   void chartAxisColorsCount() {
      Viewsheet vs = sheet("Chart1");
      script(vs, "Chart1", "Chart1.axis.y.labelColor = 0xff0000;");
      assertEquals(Set.of("Chart1"), ScriptFormattingScanner.scan(vs).components());
   }

   @Test
   void aDisabledScriptDoesNotCount() {
      Viewsheet vs = sheet("Text1");
      script(vs, "Text1", "foreground = 0xff0000;");
      ((VSAssembly) vs.getAssembly("Text1")).getVSAssemblyInfo().setScriptEnabled(false);
      assertTrue(ScriptFormattingScanner.scan(vs).isEmpty());
   }

   @Test
   void aDashboardScriptIsAttributedByName() {
      Viewsheet vs = sheet("Table1");
      vs.getViewsheetInfo().setOnInit("Table1.background = 0xeeeeee;");
      ScriptFormattingScanner.Report report = ScriptFormattingScanner.scan(vs);
      assertEquals(Set.of("Table1"), report.components());
      assertFalse(report.dashboardScript());
   }

   @Test
   void anUnresolvedDashboardTargetSetsTheFlag() {
      Viewsheet vs = sheet("Table1");
      vs.getViewsheetInfo().setOnLoad("var t = x; t.foreground = 1;");
      ScriptFormattingScanner.Report report = ScriptFormattingScanner.scan(vs);
      assertTrue(report.dashboardScript());
      assertTrue(report.components().isEmpty());
      assertFalse(report.isEmpty());
   }

   @Test
   void anExpressionBoundFormatCounts() {
      Viewsheet vs = sheet("Text1");
      VSAssemblyInfo info = ((VSAssembly) vs.getAssembly("Text1")).getVSAssemblyInfo();
      VSCompositeFormat fmt = new VSCompositeFormat();
      info.getFormatInfo().setFormat(VSAssemblyInfo.OBJECTPATH, fmt);
      fmt.getUserDefinedFormat().setForegroundValue("=if(true,'red','blue')");
      assertEquals(Set.of("Text1"), ScriptFormattingScanner.scan(vs).components());
   }
}
