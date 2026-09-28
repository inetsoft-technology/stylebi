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
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Insets;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * userPadding and the padding it guards used to live only on ChartVSAssemblyInfo; the hoist moves
 * both down to VSAssemblyInfo so a table can carry the same flag. The attribute name and the
 * pre-hoist XML shape are unchanged, so old assets still parse.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class UserPaddingHoistTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aTableCarriesTheFlag() {
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      assertFalse(info.isUserPadding());

      info.setUserPadding(true);

      assertTrue(info.isUserPadding());
   }

   @Test
   void theFlagRoundTripsOnATable() throws Exception {
      TableVSAssemblyInfo written = new TableVSAssemblyInfo();
      written.setUserPadding(true);

      assertTrue(reparseTable(written).isUserPadding());
   }

   @Test
   void aChartSavedBeforeTheHoistStillParses() throws Exception {
      // the attribute name is unchanged, so a pre-hoist asset must read exactly as it used to
      ChartVSAssemblyInfo read = new ChartVSAssemblyInfo();
      read.parseAttributes(parseElement(
         "<assembly class=\"ChartVSAssemblyInfo\" userPadding=\"true\"/>"));

      assertTrue(read.isUserPadding());
   }

   @Test
   void theAttributeIsWrittenExactlyOnce() throws Exception {
      // the hoist moves the write to the base class; leaving the subclass copy in place would
      // emit it twice and the second would win on parse
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setUserPadding(true);

      String xml = writeAttributes(info);
      int first = xml.indexOf("userPadding=");

      assertTrue(first >= 0, "attribute written");
      assertEquals(-1, xml.indexOf("userPadding=", first + 1), "written only once");
   }

   @Test
   void copyViewInfoCarriesTheFlag() {
      // the dialog's clone-and-merge runs through copyViewInfo; dropping the flag there would
      // make every OK on a chart or table property dialog forget that the author set the inset.
      // Note copyViewInfo (protected boolean, VSAssemblyInfo:607), NOT copyInfo, which is a
      // different method returning int
      ChartVSAssemblyInfo from = new ChartVSAssemblyInfo();
      from.setUserPadding(true);
      ChartVSAssemblyInfo to = new ChartVSAssemblyInfo();

      assertTrue(to.copyViewInfo(from, true), "copyViewInfo reports a change");
      assertTrue(to.isUserPadding());
   }

   @Test
   void resetPaddingFollowsDensityForATable() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
      TableVSAssemblyInfo info = new TableVSAssemblyInfo();
      info.setPadding(new Insets(3, 3, 3, 3));

      info.resetPadding(VizContext.of(VizMark.MODERN_LIGHT));

      assertEquals(new Insets(16, 16, 16, 16), info.getPadding());
   }

   @Test
   void resetPaddingRestoresTheLegacyInsetPerType() {
      TableVSAssemblyInfo table = new TableVSAssemblyInfo();
      ChartVSAssemblyInfo chart = new ChartVSAssemblyInfo();

      table.resetPadding(VizContext.of((VizMark) null));
      chart.resetPadding(VizContext.of((VizMark) null));

      assertEquals(new Insets(0, 0, 0, 0), table.getPadding(), "a table has never had an inset");
      assertEquals(new Insets(10, 10, 10, 10), chart.getPadding(), "the chart's creation default");
   }

   // writes just the attribute string a writeAttributes call produces, wrapped the way a real
   // element looks; the reparse helpers below feed it straight back through parseAttributes
   private static String writeAttributes(VSAssemblyInfo info) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      info.writeAttributes(pw);
      pw.flush();
      return "<assembly" + sw + "/>";
   }

   private static Element parseElement(String xml) throws Exception {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      return factory.newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes()))
         .getDocumentElement();
   }

   private static TableVSAssemblyInfo reparseTable(TableVSAssemblyInfo info) throws Exception {
      TableVSAssemblyInfo target = new TableVSAssemblyInfo();
      target.parseAttributes(parseElement(writeAttributes(info)));
      return target;
   }
}
