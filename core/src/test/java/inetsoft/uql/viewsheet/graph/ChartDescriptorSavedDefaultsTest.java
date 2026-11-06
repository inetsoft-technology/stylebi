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
package inetsoft.uql.viewsheet.graph;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.CompositeValue;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartDescriptorSavedDefaultsTest {
   @Test
   void aSwitchedOnDescriptorWritesItsRegisteredDefaultTiers() throws Exception {
      ChartDescriptor desc = new ChartDescriptor();
      desc.getPlotDescriptor().setYGridColor(new Color(0x4A484F), CompositeValue.Type.DEFAULT);
      desc.setSaveDefaults(true);

      // a fresh descriptor reads as an older build does: its own switch is off
      ChartDescriptor read = parse(new ChartDescriptor(), xml(desc));

      assertEquals(new Color(0x4A484F), read.getPlotDescriptor().getYGridColor());

      for(int i = 0; i < desc.savedDefaults().size(); i++) {
         assertEquals(desc.savedDefaults().get(i).get(CompositeValue.Type.DEFAULT),
                      read.savedDefaults().get(i).get(CompositeValue.Type.DEFAULT), "field " + i);
         assertFalse(read.savedDefaults().get(i).hasUserValue(), "field " + i);
      }
   }

   @Test
   void aSwitchedOffDescriptorWritesWhatItAlwaysWrote() {
      ChartDescriptor desc = new ChartDescriptor();
      String before = xml(desc);
      desc.setSaveDefaults(true);
      desc.setSaveDefaults(false);

      assertEquals(before, xml(desc));
      assertFalse(before.contains("^^DEF^^"));
   }

   @Test
   void aBareValueFromAnOldFileStaysTheAuthors() throws Exception {
      ChartDescriptor old = new ChartDescriptor();
      old.savedDefaults().forEach(ChartDescriptorSavedDefaultsTest::promoteDefaultToUser);
      String oldXml = xml(old);
      assertFalse(oldXml.contains("^^DEF^^"), "precondition: bare values, as every older file has");

      ChartDescriptor read = parse(new ChartDescriptor(), oldXml);

      read.savedDefaults().forEach(v -> assertTrue(v.hasUserValue()));
   }

   @Test
   void theSwitchDoesNotChangeHowABareValueParses() {
      CompositeValue<Color> value = new CompositeValue<>(Color.class, Color.BLACK);
      value.setSaveDefault(true);

      value.parse(String.valueOf(Color.RED.getRGB()));

      assertTrue(value.hasUserValue());
      assertEquals(Color.BLACK, value.get(CompositeValue.Type.DEFAULT));
   }

   @Test
   void aCopyKeepsTheSwitchAndOwnsIt() {
      ChartDescriptor original = new ChartDescriptor();
      original.setSaveDefaults(true);

      ChartDescriptor copy = (ChartDescriptor) original.clone();
      assertTrue(xml(copy).contains("^^DEF^^"));

      copy.setSaveDefaults(false);
      assertTrue(xml(original).contains("^^DEF^^"));
      assertFalse(xml(copy).contains("^^DEF^^"));
   }

   private static <T> void promoteDefaultToUser(CompositeValue<T> value) {
      value.setValue(value.get(CompositeValue.Type.DEFAULT), CompositeValue.Type.USER);
   }

   private static String xml(XMLSerializable obj) {
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      obj.writeXML(writer);
      writer.flush();
      return out.toString();
   }

   private static <T extends XMLSerializable> T parse(T target, String xml) throws Exception {
      target.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      return target;
   }
}
