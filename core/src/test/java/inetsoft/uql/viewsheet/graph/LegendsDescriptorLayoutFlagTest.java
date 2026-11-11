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

import inetsoft.graph.internal.DimensionD;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The legend position's author flag: derived from the position and size when a file predates it,
 * written only when the derivation would read it wrong.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LegendsDescriptorLayoutFlagTest {
   @Test
   void anOldFilesRightLegendIsNoOpinion() throws Exception {
      LegendsDescriptor loaded = parse("<legendsDescriptor option=\"2\"/>");

      assertEquals(LegendsDescriptor.RIGHT, loaded.getLayout());
      assertFalse(loaded.isUserLayout());
   }

   @Test
   void anOldFilesOtherPositionIsTheAuthors() throws Exception {
      for(int option : new int[] { 0, 1, 3, 4, 5 }) {
         LegendsDescriptor loaded = parse("<legendsDescriptor option=\"" + option + "\"/>");

         assertEquals(option, loaded.getLayout());
         assertTrue(loaded.isUserLayout(), "option " + option);
      }
   }

   @Test
   void anOldFilesResizedLegendIsTheAuthors() throws Exception {
      LegendsDescriptor loaded =
         parse("<legendsDescriptor option=\"2\" preferredSize=\"0.2,1.0\"/>");

      assertTrue(loaded.isUserLayout());
   }

   @Test
   void anUneditedOldFileWritesWhatItAlwaysWrote() throws Exception {
      for(int option : new int[] { LegendsDescriptor.RIGHT, LegendsDescriptor.TOP }) {
         LegendsDescriptor d = new LegendsDescriptor();
         d.setLayout(option);
         String old = xml(d).replaceAll(" userLayout=\"[a-z]*\"", "");

         String rewritten = xml(parse(old));

         assertFalse(rewritten.contains("userLayout"), "option " + option + ": " + rewritten);
         assertEquals(old, rewritten, "option " + option);
      }
   }

   @Test
   void aSeededTopWritesItsFlag() throws Exception {
      LegendsDescriptor d = new LegendsDescriptor();
      d.setLayout(LegendsDescriptor.TOP);
      String written = xml(d);

      assertTrue(written.contains("userLayout=\"false\""), written);
      LegendsDescriptor loaded = parse(written);
      assertEquals(LegendsDescriptor.TOP, loaded.getLayout());
      assertFalse(loaded.isUserLayout());
   }

   @Test
   void authorSettersStampTheFlag() {
      LegendsDescriptor layout = new LegendsDescriptor();
      layout.setAuthorLayout(LegendsDescriptor.BOTTOM);
      assertEquals(LegendsDescriptor.BOTTOM, layout.getLayout());
      assertTrue(layout.isUserLayout());

      LegendsDescriptor size = new LegendsDescriptor();
      size.setAuthorPreferredSize(new DimensionD(0.2, 1));
      assertEquals(0.2, size.getPreferredSize().getWidth());
      assertTrue(size.isUserLayout());
   }

   @Test
   void aCopyKeepsTheFlag() {
      LegendsDescriptor d = new LegendsDescriptor();
      d.setAuthorLayout(LegendsDescriptor.LEFT);

      assertTrue(((LegendsDescriptor) d.clone()).isUserLayout());
   }

   @Test
   void equalsContentSeesTheFlag() {
      LegendsDescriptor authored = new LegendsDescriptor();
      authored.setAuthorLayout(LegendsDescriptor.TOP);
      LegendsDescriptor seeded = new LegendsDescriptor();
      seeded.setLayout(LegendsDescriptor.TOP);

      assertFalse(authored.equalsContent(seeded));
      assertTrue(authored.equalsContent((LegendsDescriptor) authored.clone()));
   }

   private static String xml(LegendsDescriptor d) {
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      d.writeXML(writer);
      writer.flush();
      return out.toString();
   }

   private static LegendsDescriptor parse(String xml) throws Exception {
      LegendsDescriptor d = new LegendsDescriptor();
      d.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      return d;
   }
}
