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
import inetsoft.uql.asset.internal.AssetUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class OptionRowTest {
   @BeforeEach
   void density() {
      SreeEnv.setProperty("viewsheet.density", "comfortable");
   }

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aComboBoxNeverResolvesARow() {
      ComboBoxVSAssemblyInfo info = new ComboBoxVSAssemblyInfo();
      info.setVizMark(VizMark.MODERN_LIGHT);

      assertEquals(AssetUtil.defh, info.getCellHeight());
   }

   @Test
   void aSetFlagRoundTripsAndAnUnsetOneWritesNothing() throws Exception {
      ComboBoxVSAssemblyInfo clean = new ComboBoxVSAssemblyInfo();
      assertFalse(xml(clean).contains("userCellHeight"), "an unedited row keeps its saved form");

      ComboBoxVSAssemblyInfo saved = new ComboBoxVSAssemblyInfo();
      saved.setUserCellHeight(true);
      saved.setCellHeight(33);
      assertTrue(xml(saved).contains("userCellHeight=\"true\""));

      ComboBoxVSAssemblyInfo loaded = new ComboBoxVSAssemblyInfo();
      loaded.parseXML(element(saved));
      assertTrue(loaded.isUserCellHeight());
      assertEquals(33, loaded.getCellHeightValue());
   }

   @Test
   void copyCarriesTheFlag() {
      ComboBoxVSAssemblyInfo from = new ComboBoxVSAssemblyInfo();
      from.setUserCellHeight(true);
      ComboBoxVSAssemblyInfo to = new ComboBoxVSAssemblyInfo();

      assertTrue(to.copyViewInfo(from, false), "a changed flag reports a change");
      assertTrue(to.isUserCellHeight());
   }

   private static String xml(VSAssemblyInfo info) {
      StringWriter sw = new StringWriter();
      PrintWriter writer = new PrintWriter(sw);
      info.writeXML(writer);
      writer.flush();
      return sw.toString();
   }

   private static Element element(VSAssemblyInfo info) throws Exception {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      Document doc = dbf.newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml(info).getBytes(StandardCharsets.UTF_8)));
      return doc.getDocumentElement();
   }
}
