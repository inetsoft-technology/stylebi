/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.util.Tool;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests covering the dual-default invariant for nodeCornerRadius:
 * new PlotDescriptors default to 0.3, but parseXML must override to 0.0
 * when the attribute is missing so saved (legacy) tree charts stay sharp.
 */
class PlotDescriptorXmlTest {
   @Test
   void nodeCornerRadius_roundTripPreservesValue() throws Exception {
      PlotDescriptor written = new PlotDescriptor();
      written.setNodeCornerRadius(0.4);

      PlotDescriptor parsed = roundTrip(written);

      assertEquals(0.4, parsed.getNodeCornerRadius(), 1e-9);
   }

   @Test
   void nodeCornerRadius_legacyXmlWithoutAttributeDefaultsToZero() throws Exception {
      Document doc = Tool.parseXML(new StringReader("<plotDescriptor/>"));
      PlotDescriptor parsed = new PlotDescriptor();
      parsed.parseXML(doc.getDocumentElement());

      assertEquals(0.0, parsed.getNodeCornerRadius(), 1e-9,
                   "Legacy charts (XML missing nodeCornerRadius) must stay sharp");
   }

   @Test
   void nodeCornerRadius_newInstanceDefaultsToRounded() {
      assertEquals(0.3, new PlotDescriptor().getNodeCornerRadius(), 1e-9,
                   "New tree charts default to rounded nodes");
   }

   @Test
   void setNodeCornerRadius_clampsBelowZero() {
      PlotDescriptor pd = new PlotDescriptor();
      pd.setNodeCornerRadius(-1.0);
      assertEquals(0.0, pd.getNodeCornerRadius(), 1e-9);
   }

   @Test
   void setNodeCornerRadius_clampsAboveHalf() {
      PlotDescriptor pd = new PlotDescriptor();
      pd.setNodeCornerRadius(2.0);
      assertEquals(0.5, pd.getNodeCornerRadius(), 1e-9);
   }

   private static PlotDescriptor roundTrip(PlotDescriptor source) throws Exception {
      StringWriter sw = new StringWriter();
      try(PrintWriter pw = new PrintWriter(sw)) {
         source.writeXML(pw);
      }
      Document doc = Tool.parseXML(new StringReader(sw.toString()));
      PlotDescriptor parsed = new PlotDescriptor();
      parsed.parseXML(doc.getDocumentElement());
      return parsed;
   }
}
