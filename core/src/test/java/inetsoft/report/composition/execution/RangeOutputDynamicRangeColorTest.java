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
package inetsoft.report.composition.execution;

import inetsoft.test.*;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.GaugeVSAssemblyInfo;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.awt.Color;
import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77585: a gauge range color design value that is a variable ({@code $(var)}) must
 * survive save and reload and still be evaluated by the sandbox into the
 * runtime range color. Before the fix the save threw NumberFormatException.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RangeOutputDynamicRangeColorTest {
   @ParameterizedTest
   @ValueSource(strings = { "$(c)" })
   void dynamicRangeColorEvaluatesAfterSaveAndReload(String dvalue) throws Exception {
      Viewsheet vs = new Viewsheet();
      TextInputVSAssembly input = new TextInputVSAssembly(vs, "c");
      input.setSelectedObject("16711680");
      vs.addAssembly(input);

      GaugeVSAssemblyInfo info = parse(
         "<assemblyInfo class=\"" + GaugeVSAssemblyInfo.class.getName() +
         "\" style=\"1\"><name><![CDATA[Gauge1]]></name><rangeColorsValue><rangeColor>" +
         "<![CDATA[" + dvalue + "]]></rangeColor></rangeColorsValue></assemblyInfo>");
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      info.writeXML(pw);
      pw.flush();
      GaugeVSAssemblyInfo reloaded = parse(sw.toString());

      GaugeVSAssembly gauge = new GaugeVSAssembly(vs, "Gauge1");
      gauge.setVSAssemblyInfo(reloaded);
      vs.addAssembly(gauge);

      DynamicValue rangeColor = reloaded.getViewDynamicValues(true).stream()
         .filter(v -> dvalue.equals(v.getDValue())).findFirst().orElse(null);
      assertNotNull(rangeColor, "range color design value lost on save and reload");

      ViewsheetSandbox box =
         new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, null);
      box.executeDynamicValue(rangeColor, null, "Gauge1", null);

      assertEquals(Color.red, reloaded.getRangeColors()[0]);
   }

   private static GaugeVSAssemblyInfo parse(String xml) throws Exception {
      Element elem = Tool.getFirstElement(Tool.parseXML(
         new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "UTF-8"));
      GaugeVSAssemblyInfo info = new GaugeVSAssemblyInfo();
      info.parseXML(elem);
      return info;
   }
}
