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

import inetsoft.report.internal.AllLegendDescriptor;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The author flag on a legend title's visibility: derived for old files, written only where the
 * derivation would misread it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LegendTitleFlagTest {
   @Test
   void anOldFilesHiddenTitleIsTheAuthors() throws Exception {
      LegendDescriptor desc = new LegendDescriptor();
      desc.setTitleVisible(false);

      assertTrue(oldFile(desc).isUserTitleVisible());
   }

   @Test
   void anOldFilesTypedTitleIsTheAuthors() throws Exception {
      LegendDescriptor desc = new LegendDescriptor();
      desc.setTitleValue("Brands");

      assertTrue(oldFile(desc).isUserTitleVisible());
   }

   @Test
   void anOldFilesPlainTitleIsNoOpinion() throws Exception {
      assertFalse(oldFile(new LegendDescriptor()).isUserTitleVisible());
   }

   @Test
   void aClassicLegendTitleWritesNoFlag() throws Exception {
      for(boolean visible : new boolean[] { true, false }) {
         for(String text : new String[] { null, "Brands" }) {
            LegendDescriptor desc = new LegendDescriptor();
            desc.setTitleVisible(visible);

            if(text != null) {
               desc.setTitleValue(text);
            }

            assertFalse(write(oldFile(desc)).contains("userTitleVisible"),
                        "visible=" + visible + " text=" + text);
         }
      }
   }

   @Test
   void aSeededHiddenTitleWritesItsFlag() throws Exception {
      LegendDescriptor desc = new LegendDescriptor();
      desc.setTitleVisible(false);
      String xml = write(desc);

      assertTrue(xml.contains("userTitleVisible=\"false\""));
      assertFalse(parse(xml).isUserTitleVisible());
      assertFalse(parse(xml).isTitleVisible());
   }

   @Test
   void typingATitleKeepsAnAuthorsHiddenTitleHidden() {
      LegendDescriptor desc = new LegendDescriptor();
      desc.setAuthorTitleVisible(false);
      desc.setAuthorTitleValue("Brands");

      assertFalse(desc.isTitleVisible());
      assertTrue(desc.isUserTitleVisible());

      LegendDescriptor rt = new LegendDescriptor();
      rt.setAuthorTitleVisible(false);
      rt.setAuthorTitle("Brands");

      assertFalse(rt.isTitleVisible());
      assertTrue(rt.isUserTitleVisible());
   }

   @Test
   void typingATitleShowsItAndStampsTheFlag() {
      LegendDescriptor desc = new LegendDescriptor();
      desc.setTitleVisible(false);
      desc.setAuthorTitleValue("Brands");

      assertTrue(desc.isTitleVisible());
      assertTrue(desc.isUserTitleVisible());
      assertEquals("Brands", desc.getTitleValue());

      LegendDescriptor rt = new LegendDescriptor();
      rt.setTitleVisible(false);
      rt.setAuthorTitle("Brands");

      assertTrue(rt.isTitleVisible());
      assertTrue(rt.isUserTitleVisible());
   }

   @Test
   void clearingTheTextStampsNothing() {
      LegendDescriptor desc = new LegendDescriptor();
      desc.setTitleVisible(false);
      desc.setAuthorTitleValue("");

      assertFalse(desc.isTitleVisible());
      assertFalse(desc.isUserTitleVisible());
      assertFalse(desc.hasTitleText());
   }

   @Test
   void anAuthorsVisibilityStampsTheFlag() {
      LegendDescriptor desc = new LegendDescriptor();
      desc.setAuthorTitleVisible(false);

      assertFalse(desc.isTitleVisible());
      assertTrue(desc.isUserTitleVisible());
   }

   @Test
   void allLegendDescriptorFansOut() {
      LegendDescriptor a = new LegendDescriptor();
      LegendDescriptor b = new LegendDescriptor();
      a.setTitleVisible(false);
      b.setTitleVisible(false);
      AllLegendDescriptor all = new AllLegendDescriptor(List.of(a, b));

      all.setAuthorTitleVisible(true);
      assertTrue(a.isUserTitleVisible());
      assertTrue(b.isUserTitleVisible());
      assertTrue(b.isTitleVisible());

      a.setUserTitleVisible(false);
      b.setUserTitleVisible(false);
      a.setTitleVisible(false);
      b.setTitleVisible(false);
      all.setAuthorTitle("Brands");

      assertTrue(a.isUserTitleVisible() && b.isUserTitleVisible());
      assertTrue(a.isTitleVisible() && b.isTitleVisible());
   }

   // the XML a build without the flag wrote, read by this one
   private static LegendDescriptor oldFile(LegendDescriptor desc) throws Exception {
      return parse(write(desc).replaceAll(" userTitleVisible=\"[a-z]*\"", ""));
   }

   private static String write(LegendDescriptor desc) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      desc.writeXML(writer);
      writer.flush();
      return buf.toString();
   }

   private static LegendDescriptor parse(String xml) throws Exception {
      LegendDescriptor desc = new LegendDescriptor();
      desc.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      return desc;
   }
}
