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
package inetsoft.uql.viewsheet;

import inetsoft.test.*;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77810: a Text assembly in an imported viewsheet whose autoSize is bound
 * to a variable (or holds a truthy literal) must still auto-size after the
 * whole viewsheet is saved and opened again, twice.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TextAutoSizeViewsheetRoundTripTest {
   @ParameterizedTest
   @ValueSource(strings = { "$(autoVar)", "=autoVar", "yes" })
   void autoSizeSurvivesViewsheetSaves(String stored) throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"), false);

      // the imported file carries the stored value
      String xml = write(vs);
      String written = " autoSizeValue=\"false\"";
      assertEquals(xml.indexOf(written), xml.lastIndexOf(written), xml);
      assertTrue(xml.contains(written), xml);
      Viewsheet loaded = parse(xml.replace(written,
         " autoSizeValue=\"" + Tool.escape(stored) + "\""));

      for(int save = 1; save <= 2; save++) {
         TextVSAssemblyInfo info = textInfo(loaded);
         assertNotNull(binding(info, stored), "stored value after save " + (save - 1));
         evaluate(info, stored);
         assertTrue(info.isAutoSize(), "evaluated autoSize before save " + save);

         loaded = parse(write(loaded));
      }

      TextVSAssemblyInfo reloaded = textInfo(loaded);
      evaluate(reloaded, stored);
      assertTrue(reloaded.isAutoSize(), "autoSize after reopening the saved viewsheet");
   }

   private static TextVSAssemblyInfo textInfo(Viewsheet vs) {
      VSAssembly text = vs.getAssembly("Text1");
      assertNotNull(text);
      return (TextVSAssemblyInfo) text.getVSAssemblyInfo();
   }

   /**
    * Evaluate a binding with autoVar = true, as ViewsheetSandbox.executeDynamicValues
    * stores the result; a literal needs no evaluation.
    */
   private static void evaluate(TextVSAssemblyInfo info, String stored) {
      if(stored.startsWith("$") || stored.startsWith("=")) {
         binding(info, stored).setRValue(Boolean.TRUE);
      }
   }

   /**
    * The single dynamic value of the assembly that carries the stored value.
    */
   private static DynamicValue binding(TextVSAssemblyInfo info, String stored) {
      DynamicValue found = null;

      for(DynamicValue value : info.getDynamicValues()) {
         if(stored.equals(value.getDValue())) {
            assertNull(found, "only autoSize carries " + stored);
            found = value;
         }
      }

      return found;
   }

   private static String write(Viewsheet vs) {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      writer.println("<viewsheet>");
      vs.writeXML(writer);
      writer.println("</viewsheet>");
      writer.flush();
      return buffer.toString();
   }

   private static Viewsheet parse(String xml) throws Exception {
      Viewsheet result = new Viewsheet();
      result.parseXML(Tool.parseXML(new ByteArrayInputStream(
         xml.getBytes(StandardCharsets.UTF_8))).getDocumentElement(), false);
      return result;
   }
}
