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
package inetsoft.uql.viewsheet.internal;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TitleInfoTest {
   @Test
   void newTitleInfoIsNotUserSet() {
      assertFalse(new TitleInfo().isUserTitleHeight());
      assertFalse(new TitleInfo("a title").isUserTitleHeight());
   }

   @Test
   void userTitleHeightSurvivesRoundTrip() throws Exception {
      TitleInfo userSet = roundTrip(28, true);
      assertTrue(userSet.isUserTitleHeight(),
                 "an author-set height must stay author-set across a save and load");
      assertEquals(28, userSet.getTitleHeightValue(), "the height itself must round-trip unchanged");

      TitleInfo notUserSet = roundTrip(28, false);
      assertFalse(notUserSet.isUserTitleHeight(),
                  "a height not marked author-set must not become author-set on load");
      assertEquals(28, notUserSet.getTitleHeightValue(), "the height itself must round-trip unchanged");
   }

   /** Write a TitleInfo to XML and parse it back. */
   private static TitleInfo roundTrip(int height, boolean userSet) throws Exception {
      TitleInfo written = new TitleInfo("a title");
      written.setTitleHeightValue(height);
      written.setUserTitleHeight(userSet);

      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      written.writeXML(writer);
      writer.flush();

      Document doc = Tool.parseXML(new StringReader("<assembly>" + buf + "</assembly>"));
      TitleInfo parsed = new TitleInfo();
      parsed.parseXML(doc.getDocumentElement());

      return parsed;
   }

   @Test
   void legacyFileDerivesFromTheDefaultHeight() throws Exception {
      TitleInfo atDefault = parseLegacy(20, AssetUtil.defh);
      assertFalse(atDefault.isUserTitleHeight(),
                  "a height equal to the type's default is not author-set");
      assertEquals(20, atDefault.getTitleHeightValue(), "the parsed height must match the XML");

      TitleInfo offDefault = parseLegacy(28, AssetUtil.defh);
      assertTrue(offDefault.isUserTitleHeight(),
                 "a height differing from the type's default is author-set");
      assertEquals(28, offDefault.getTitleHeightValue(), "the parsed height must match the XML");
   }

   @Test
   void legacyFileDerivesAgainstANonStandardDefault() throws Exception {
      TitleInfo atDefault = parseLegacy(36, 36);
      assertFalse(atDefault.isUserTitleHeight(),
                  "a calendar left at its own 36px default is not author-set");
      assertEquals(36, atDefault.getTitleHeightValue(), "the parsed height must match the XML");

      TitleInfo offDefault = parseLegacy(20, 36);
      assertTrue(offDefault.isUserTitleHeight(),
                 "a calendar moved off its 36px default is author-set");
      assertEquals(20, offDefault.getTitleHeightValue(), "the parsed height must match the XML");
   }

   @Test
   void anExplicitAttributeBeatsTheDerive() throws Exception {
      Document doc = Tool.parseXML(new StringReader(
         "<assembly><titleInfo titleHeight=\"28\" userTitleHeight=\"false\"/></assembly>"));
      TitleInfo parsed = new TitleInfo();
      parsed.parseXML(doc.getDocumentElement(), AssetUtil.defh);

      assertFalse(parsed.isUserTitleHeight(),
                  "a stored false must not be re-derived to true");
      assertEquals(28, parsed.getTitleHeightValue(), "the parsed height must match the XML");
   }

   /** Parse a titleInfo element carrying no userTitleHeight attribute. */
   private static TitleInfo parseLegacy(int height, int defaultHeight) throws Exception {
      Document doc = Tool.parseXML(new StringReader(
         "<assembly><titleInfo titleHeight=\"" + height + "\"/></assembly>"));
      TitleInfo parsed = new TitleInfo();
      parsed.parseXML(doc.getDocumentElement(), defaultHeight);

      return parsed;
   }

   @Test
   void aMissingHeightAttributeIsNeverAuthorSet() throws Exception {
      Document doc = Tool.parseXML(new StringReader("<assembly><titleInfo/></assembly>"));
      TitleInfo parsed = new TitleInfo();
      parsed.parseXML(doc.getDocumentElement(), AssetUtil.defh);

      assertFalse(parsed.isUserTitleHeight(),
                  "no recorded height at all means nothing was ever customized");
      assertEquals(AssetUtil.defh, parsed.getTitleHeightValue(),
                   "a missing height attribute must fall back to the shared default");
   }

   @Test
   void aMissingHeightAttributeIsNeverAuthorSetAgainstANonStandardDefault() throws Exception {
      Document doc = Tool.parseXML(new StringReader("<assembly><titleInfo/></assembly>"));
      TitleInfo parsed = new TitleInfo();
      parsed.parseXML(doc.getDocumentElement(), 36);

      assertFalse(parsed.isUserTitleHeight(),
                  "an ultra-legacy calendar file with no titleHeight must not be derived " +
                  "as author-set just because the generic default differs from 36");
      assertEquals(AssetUtil.defh, parsed.getTitleHeightValue(),
                   "a missing height attribute must fall back to the shared default, " +
                   "not the type's own default");
   }

   @Test
   void theForBcBranchDerivesFromTheDefaultHeight() throws Exception {
      Document withHeight = Tool.parseXML(new StringReader(
         "<assembly titleHeight=\"28\"><titleValue>x</titleValue></assembly>"));
      TitleInfo parsedWithHeight = new TitleInfo();
      parsedWithHeight.parseXML(withHeight.getDocumentElement(), AssetUtil.defh);

      assertTrue(parsedWithHeight.isUserTitleHeight(),
                 "a recorded height differing from the default is author-set");
      assertEquals(28, parsedWithHeight.getTitleHeightValue(), "the parsed height must match the XML");

      Document withoutHeight = Tool.parseXML(new StringReader(
         "<assembly><titleValue>x</titleValue></assembly>"));
      TitleInfo parsedWithoutHeight = new TitleInfo();
      parsedWithoutHeight.parseXML(withoutHeight.getDocumentElement(), AssetUtil.defh);

      assertFalse(parsedWithoutHeight.isUserTitleHeight(),
                  "the for-bc branch must not derive author-set with no recorded height");
      assertEquals(AssetUtil.defh, parsedWithoutHeight.getTitleHeightValue(),
                   "a missing height attribute must fall back to the shared default");
   }

   @Test
   void presenceIsDetectedThroughTheDesignTimeAttribute() throws Exception {
      Document doc = Tool.parseXML(new StringReader(
         "<assembly><titleInfo titleHeightValue=\"28\"/></assembly>"));
      TitleInfo parsed = new TitleInfo();
      parsed.parseXML(doc.getDocumentElement(), AssetUtil.defh);

      assertTrue(parsed.isUserTitleHeight(),
                 "a titleHeightValue attribute alone must still count as a recorded height");
      assertEquals(28, parsed.getTitleHeightValue(), "the height must be read from titleHeightValue");
   }

   @Test
   void eachTypeDeclaresItsOwnDefaultTitleHeight() {
      assertEquals(36, new CalendarVSAssemblyInfo().getDefaultTitleHeight(),
                   "calendar seeds 36 rather than the shared default");
      assertEquals(AssetUtil.defh, new ChartVSAssemblyInfo().getDefaultTitleHeight(),
                   "every other titled type uses the shared default");
   }

   @Test
   void copyingATitleHeightCarriesItsProvenance() {
      TitleInfo authorSet = new TitleInfo();
      authorSet.setTitleHeightValue(28);
      authorSet.setUserTitleHeight(true);

      TitleInfo target = new TitleInfo();
      target.setTitleHeightValue(authorSet.getTitleHeightValue());
      target.setUserTitleHeight(authorSet.isUserTitleHeight());

      assertTrue(target.isUserTitleHeight(),
                 "a copied author-set height stays author-set");

      TitleInfo untouched = new TitleInfo();
      TitleInfo copy = new TitleInfo();
      copy.setTitleHeightValue(untouched.getTitleHeightValue());
      copy.setUserTitleHeight(untouched.isUserTitleHeight());

      assertFalse(copy.isUserTitleHeight(),
                  "copying a default height must not manufacture author intent");
   }
}
