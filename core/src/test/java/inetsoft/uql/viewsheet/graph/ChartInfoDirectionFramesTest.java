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
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.aesthetic.StaticColorFrameWrapper;
import inetsoft.util.Tool;
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
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartInfoDirectionFramesTest {
   @Test
   void theFramesRoundTrip() throws Exception {
      DefaultVSChartInfo source = new DefaultVSChartInfo();
      StaticColorFrameWrapper up = new StaticColorFrameWrapper();
      up.setDefaultColor(new Color(0x1C857B));
      up.setUserColor(new Color(0x00AA00));
      StaticColorFrameWrapper down = new StaticColorFrameWrapper();
      down.setDefaultColor(new Color(0xAD6119));
      source.setUpColorFrameWrapper(up);
      source.setDownColorFrameWrapper(down);

      DefaultVSChartInfo parsed = new DefaultVSChartInfo();
      parsed.parseXML(Tool.parseXML(new StringReader(write(source))).getDocumentElement());

      assertNotNull(parsed.getUpColorFrameWrapper());
      assertNotNull(parsed.getDownColorFrameWrapper());
      assertEquals(new Color(0x1C857B), parsed.getUpColorFrameWrapper().getDefaultColor());
      assertEquals(new Color(0x00AA00), parsed.getUpColorFrameWrapper().getUserColor());
      assertEquals(new Color(0xAD6119), parsed.getDownColorFrameWrapper().getDefaultColor());
      assertNull(parsed.getDownColorFrameWrapper().getUserColor());
   }

   @Test
   void aChartWithoutThemWritesNothingNew() {
      String xml = write(new DefaultVSChartInfo());

      assertFalse(xml.contains("upColorVisualFrame"));
      assertFalse(xml.contains("downColorVisualFrame"));
   }

   @Test
   void aCloneDoesNotShareThem() {
      DefaultVSChartInfo source = new DefaultVSChartInfo();
      source.setUpColorFrameWrapper(frame(0x1C857B));
      source.setDownColorFrameWrapper(frame(0xAD6119));

      DefaultVSChartInfo copy = (DefaultVSChartInfo) source.clone();

      assertNotSame(source.getUpColorFrameWrapper(), copy.getUpColorFrameWrapper());
      assertNotSame(source.getDownColorFrameWrapper(), copy.getDownColorFrameWrapper());
      copy.getUpColorFrameWrapper().setUserColor(Color.RED);
      assertNull(source.getUpColorFrameWrapper().getUserColor());
   }

   @Test
   void equalsContentSeesThem() {
      DefaultVSChartInfo a = new DefaultVSChartInfo();
      DefaultVSChartInfo b = new DefaultVSChartInfo();
      assertTrue(a.equalsContent(b));

      a.setUpColorFrameWrapper(frame(0x1C857B));
      assertFalse(a.equalsContent(b));
      b.setUpColorFrameWrapper(frame(0x1C857B));
      assertTrue(a.equalsContent(b));

      a.setDownColorFrameWrapper(frame(0xAD6119));
      assertFalse(a.equalsContent(b));
      b.setDownColorFrameWrapper(frame(0x111111));
      assertFalse(a.equalsContent(b));
   }

   private static StaticColorFrameWrapper frame(int rgb) {
      StaticColorFrameWrapper wrapper = new StaticColorFrameWrapper();
      wrapper.setDefaultColor(new Color(rgb));
      return wrapper;
   }

   private static String write(DefaultVSChartInfo info) {
      StringWriter sw = new StringWriter();

      try(PrintWriter pw = new PrintWriter(sw)) {
         info.writeXML(pw);
      }

      return sw.toString();
   }
}
