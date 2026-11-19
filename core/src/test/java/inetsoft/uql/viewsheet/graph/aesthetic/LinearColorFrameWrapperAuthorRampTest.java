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
package inetsoft.uql.viewsheet.graph.aesthetic;

import inetsoft.graph.aesthetic.TealColorFrame;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class LinearColorFrameWrapperAuthorRampTest {
   @Test
   void theFlagRoundTrips() throws Exception {
      TealColorFrameWrapper wrapper = new TealColorFrameWrapper();
      wrapper.setAuthorRamp(true);

      VisualFrameWrapper parsed = VisualFrameWrapper.createVisualFrame(
         Tool.parseXML(new java.io.StringReader(xml(wrapper))).getDocumentElement());

      assertInstanceOf(TealColorFrameWrapper.class, parsed);
      assertInstanceOf(TealColorFrame.class, parsed.getVisualFrame());
      assertTrue(((TealColorFrameWrapper) parsed).isAuthorRamp());
   }

   @Test
   void anUnflaggedRampWritesNoAttribute() {
      assertFalse(xml(new TealColorFrameWrapper()).contains("authorRamp"));
   }

   @Test
   void equalsContentSeesTheFlag() {
      TealColorFrameWrapper a = new TealColorFrameWrapper();
      TealColorFrameWrapper b = new TealColorFrameWrapper();

      assertTrue(a.equalsContent(b));
      assertEquals(a, b);

      b.setAuthorRamp(true);

      assertFalse(a.equalsContent(b));
      assertNotEquals(a, b);
   }

   @Test
   void aCloneKeepsTheFlag() {
      TealColorFrameWrapper wrapper = new TealColorFrameWrapper();
      wrapper.setAuthorRamp(true);

      assertTrue(((TealColorFrameWrapper) wrapper.clone()).isAuthorRamp());
   }

   private static String xml(VisualFrameWrapper wrapper) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      wrapper.writeXML(writer);
      writer.flush();
      return buf.toString();
   }
}
