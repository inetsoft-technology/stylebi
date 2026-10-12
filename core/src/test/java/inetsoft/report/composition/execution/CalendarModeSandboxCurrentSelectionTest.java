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

import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.gui.viewsheet.FlexTheme;
import inetsoft.report.gui.viewsheet.VSCalendar;
import inetsoft.sree.security.Organization;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.*;

import java.awt.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77809: a viewsheet whose calendar view mode is not an integer literal must open
 * through the real sandbox reset without losing the Current Selection rows, and the
 * calendar must still paint for export. Covers a calendar inside the selection container
 * and calendars shown as out selections, next to a valid-mode ("2") control calendar.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CalendarModeSandboxCurrentSelectionTest {
   private static final String[] DATES = { "y2025", "y2026" };

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
   }

   @ParameterizedTest(name = "mode=[{0}]")
   @ValueSource(strings = { "$(m)", "abc", "2" })
   void sandboxResetKeepsCurrentSelectionRows(String mode) throws Exception {
      Map<String, String> modes = new LinkedHashMap<>();
      modes.put("CalIn", mode);     // inside the selection container
      modes.put("CalOut", mode);    // shown as an out selection
      modes.put("CalCtl", "2");     // control, valid double-calendar mode
      Viewsheet vs = load(modes);

      CurrentSelectionVSAssembly cs = (CurrentSelectionVSAssembly) vs.getAssembly("CS1");
      assertSame(cs, vs.getAssembly("CalIn").getContainer(), "CalIn is a child of CS1");

      for(String name : modes.keySet()) {
         calendarInfo(vs, name).setDates(DATES);
      }

      // the real open/refresh path: CoreLifecycleService resets runtime values then the box
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, new Worksheet());
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.VIEWSHEET, "test/Bug77809Verify", null,
         Organization.getDefaultOrganizationID());
      vs.setEntry(entry);
      ViewsheetSandbox box = new ViewsheetSandbox(
         vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);

      try {
         VSUtil.resetRuntimeValues(vs, false);
         box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);
      }
      finally {
         box.dispose();
      }

      // export picture path, for every calendar (AbstractVSExporter.getImage wiring)
      for(String name : modes.keySet()) {
         CalendarVSAssemblyInfo info = calendarInfo(vs, name);
         VSCalendar vc = new VSCalendar(vs);
         vc.setViewsheet(vs);
         vc.setTheme(new FlexTheme("granite"));
         vc.setAssemblyInfo(info);
         assertNotNull(assertDoesNotThrow(() -> vc.getImage(true), name), name);
         assertDoesNotThrow(() -> info.getDisplayValue(), name);
      }

      CurrentSelectionVSAssemblyInfo csInfo =
         (CurrentSelectionVSAssemblyInfo) cs.getVSAssemblyInfo();
      List<String> names = Arrays.asList(csInfo.getOutSelectionNames());
      List<String> values = Arrays.asList(csInfo.getOutSelectionValues());
      assertEquals(2, names.size(), "out selection rows after reset: " + names + " " + values);
      assertTrue(names.contains("CalOut"), names.toString());
      assertTrue(names.contains("CalCtl"), names.toString());
      assertFalse(names.contains("CalIn"), "child calendar is not an out selection");

      int expectedOut = "2".equals(mode) ? 2 : 1;
      String outValue = values.get(names.indexOf("CalOut"));
      String ctlValue = values.get(names.indexOf("CalCtl"));
      assertEquals(calendarInfo(vs, "CalOut").getDisplayValue(true), outValue);
      assertEquals(expectedOut == 2, outValue.contains(CalendarUtil.rangeSpliter.trim()),
                   outValue);
      assertTrue(ctlValue.contains(CalendarUtil.rangeSpliter.trim()), ctlValue);
   }

   private static CalendarVSAssemblyInfo calendarInfo(Viewsheet vs, String name) {
      return (CalendarVSAssemblyInfo) vs.getAssembly(name).getVSAssemblyInfo();
   }

   /**
    * Writes a viewsheet with CS1 containing CalIn and showing the other calendars as out
    * selections, sets each calendar's modeValue attribute in the XML and parses it back.
    */
   private static Viewsheet load(Map<String, String> modes) throws Exception {
      Viewsheet vs = new Viewsheet();
      int x = 10;

      for(String name : modes.keySet()) {
         CalendarVSAssembly cal = new CalendarVSAssembly(vs, name);
         cal.getVSAssemblyInfo().setPixelOffset(new Point(x, 300));
         cal.getVSAssemblyInfo().setPixelSize(new Dimension(300, 200));
         vs.addAssembly(cal);
         x += 320;
      }

      CurrentSelectionVSAssembly cs = new CurrentSelectionVSAssembly(vs, "CS1");
      cs.getVSAssemblyInfo().setPixelOffset(new Point(10, 10));
      ((CurrentSelectionVSAssemblyInfo) cs.getVSAssemblyInfo())
         .setShowCurrentSelectionValue(true);
      vs.addAssembly(cs);
      cs.setAssemblies(new String[] { "CalIn" });

      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      writer.println("<viewsheet>");
      vs.writeXML(writer);
      writer.println("</viewsheet>");
      writer.flush();

      Document doc = Tool.parseXML(new ByteArrayInputStream(
         buffer.toString().getBytes(StandardCharsets.UTF_8)));
      NodeList infos = doc.getElementsByTagName("assemblyInfo");
      Set<String> done = new HashSet<>();

      for(int i = 0; i < infos.getLength(); i++) {
         Element info = (Element) infos.item(i);

         if(!info.hasAttribute("modeValue")) {
            continue;
         }

         String name = Tool.getValue(Tool.getChildNodeByTagName(info, "absoluteName"));
         String mode = modes.get(name);
         assertNotNull(mode, "calendar " + name);
         info.setAttribute("modeValue", mode);
         done.add(name);

         // keep <state_view> as the product writes it (the lenient getViewModeValue()),
         // otherwise parseStateContent overwrites the mode under test
         Element assembly = (Element) info.getParentNode();
         NodeList views = assembly.getElementsByTagName("state_view");

         for(int j = 0; j < views.getLength(); j++) {
            ((Element) views.item(j)).setAttribute("mode", "2".equals(mode) ? "2" : "1");
         }
      }

      assertEquals(modes.keySet(), done, "every calendar mode was replaced");
      Viewsheet result = new Viewsheet();
      result.parseXML(doc.getDocumentElement(), false);
      return result;
   }
}
