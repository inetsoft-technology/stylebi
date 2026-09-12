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

import inetsoft.test.*;
import inetsoft.uql.viewsheet.ShapeShadow;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The shadow settings persist as a <shadowInfo> child element of the shape
 * assembly, so the round trip is what guarantees a dashboard reopens looking
 * the way it was saved -- and that an asset written before the settings
 * existed still parses.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ShapeVSAssemblyInfoShadowTest {
   @Test
   void shadowSettingsSurviveAnXmlRoundTrip() throws Exception {
      RectangleVSAssemblyInfo info = new RectangleVSAssemblyInfo();
      info.setShadowValue(true);
      ShapeShadow shadow = new ShapeShadow();
      shadow.setColor("#336699");
      shadow.setAlpha(65);
      shadow.setDirection(ShapeShadow.NORTH_WEST);
      shadow.setDistance(12);
      shadow.setBlur(4);
      info.setShadowInfo(shadow);

      RectangleVSAssemblyInfo parsed = roundTrip(info);

      assertTrue(parsed.getShadowValue());
      assertEquals(shadow, parsed.getShadowInfo());
   }

   @Test
   void anAssetSavedBeforeTheSettingsExistedParsesBackToTheDefaults()
      throws Exception
   {
      // the <shadowInfo> element simply is not there in an older asset
      RectangleVSAssemblyInfo info = new RectangleVSAssemblyInfo();
      info.setShadowValue(true);

      String xml = write(info).replaceAll("<shadowInfo[^>]*/>", "");
      assertFalse(xml.contains("shadowInfo"), xml);

      RectangleVSAssemblyInfo parsed = new RectangleVSAssemblyInfo();
      parsed.parseXML(parse(xml));

      assertTrue(parsed.getShadowValue());
      assertEquals(new ShapeShadow(), parsed.getShadowInfo(),
                   "an absent element must fall back to the defaults that " +
                   "approximate the previously hardcoded shadow");
   }

   /**
    * The direction is not restricted to DIRECTIONS by its setter (an unknown
    * value simply casts no offset), and the script API exposes shadowInfo as a
    * writable bean property, so a quote reaching the attribute must not be
    * able to break the assembly's own xml.
    */
   @Test
   void aDirectionContainingXmlSyntaxDoesNotCorruptTheAssembly()
      throws Exception
   {
      RectangleVSAssemblyInfo info = new RectangleVSAssemblyInfo();
      info.setShadowValue(true);
      ShapeShadow shadow = new ShapeShadow();
      shadow.setDirection("\"><evil attr=\"");
      info.setShadowInfo(shadow);

      // would throw on the malformed document if the attribute were raw
      RectangleVSAssemblyInfo parsed = roundTrip(info);

      assertEquals("\"><evil attr=\"", parsed.getShadowInfo().getDirection());
      assertEquals(0, parsed.getShadowInfo().getOffsetX());
      assertEquals(0, parsed.getShadowInfo().getOffsetY());
   }

   @Test
   void anOutOfRangeDistanceOrBlurIsClampedRatherThanPersisted()
      throws Exception
   {
      // these size the exported shadow image, so a value bypassing the
      // dialog's own clamp must not reach the rasterizer
      RectangleVSAssemblyInfo info = new RectangleVSAssemblyInfo();
      info.setShadowValue(true);
      ShapeShadow shadow = new ShapeShadow();
      shadow.setDistance(100000);
      shadow.setBlur(100000);
      info.setShadowInfo(shadow);

      assertEquals(ShapeShadow.MAX_LENGTH, shadow.getDistance());
      assertEquals(ShapeShadow.MAX_LENGTH, shadow.getBlur());

      RectangleVSAssemblyInfo parsed = roundTrip(info);

      assertEquals(ShapeShadow.MAX_LENGTH, parsed.getShadowInfo().getDistance());
      assertEquals(ShapeShadow.MAX_LENGTH, parsed.getShadowInfo().getBlur());
   }

   @Test
   void anOutOfRangeDistanceOrBlurInAnEditedAssetIsClampedOnParse()
      throws Exception
   {
      RectangleVSAssemblyInfo info = new RectangleVSAssemblyInfo();
      info.setShadowValue(true);

      String xml = write(info)
         .replaceAll("distance=\"[0-9]+\"", "distance=\"100000\"")
         .replaceAll("blur=\"[0-9]+\"", "blur=\"100000\"");

      RectangleVSAssemblyInfo parsed = new RectangleVSAssemblyInfo();
      parsed.parseXML(parse(xml));

      assertEquals(ShapeShadow.MAX_LENGTH, parsed.getShadowInfo().getDistance());
      assertEquals(ShapeShadow.MAX_LENGTH, parsed.getShadowInfo().getBlur());
   }

   private static RectangleVSAssemblyInfo roundTrip(RectangleVSAssemblyInfo info)
      throws Exception
   {
      RectangleVSAssemblyInfo parsed = new RectangleVSAssemblyInfo();
      parsed.parseXML(parse(write(info)));

      return parsed;
   }

   private static String write(RectangleVSAssemblyInfo info) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         info.writeXML(writer);
      }

      return buffer.toString();
   }

   private static Element parse(String xml) throws Exception {
      Document doc = Tool.parseXML(new StringReader(xml));

      return doc.getDocumentElement();
   }
}
