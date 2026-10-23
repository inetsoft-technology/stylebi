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
package inetsoft.uql.viewsheet;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.XMLSerializable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Presenter and round corner were once written to one hint bit, so a USER tier that set only a
 * presenter reloaded with a square corner that beat the card radius seeded beneath it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSFormatRoundCornerHintTest {
   // the bit both flags were written to before they were split
   private static final int SHARED_BIT = 0x80000;
   // a legacy census selection list's object USER tier: colors, font, format, alpha, shared bit
   private static final int CENSUS_REGION_HINT = 690216;

   @Test
   void sharedBitCornerEqualToDefaultIsNotTheAuthors() throws Exception {
      VSCompositeFormat fmt = parse(composite(0, 0, CENSUS_REGION_HINT));
      fmt.getDefaultFormat().setRoundCornerValue(6);

      assertFalse(fmt.getUserDefinedFormat().isRoundCornerValueDefined());
      assertTrue(fmt.getUserDefinedFormat().isPresenterValueDefined(),
                 "the bit may still have meant a presenter");
      assertEquals(6, fmt.getRoundCorner(), "the seeded card radius shows through");
   }

   @Test
   void sharedBitCornerDifferentFromDefaultStaysTheAuthors() throws Exception {
      // a submit button squared off its 3px default
      VSCompositeFormat fmt = parse(composite(3, 0, SHARED_BIT));

      assertTrue(fmt.getUserDefinedFormat().isRoundCornerValueDefined());
      assertEquals(0, fmt.getRoundCorner());
   }

   @Test
   void sharedBitRoundedCornerStaysTheAuthors() throws Exception {
      VSCompositeFormat fmt = parse(composite(0, 12, SHARED_BIT));

      assertTrue(fmt.getUserDefinedFormat().isRoundCornerValueDefined());
      assertEquals(12, fmt.getRoundCorner());
   }

   @Test
   void presenterAloneReloadsWithoutACorner() throws Exception {
      VSCompositeFormat fmt = new VSCompositeFormat();
      fmt.getDefaultFormat().setRoundCornerValue(6);
      fmt.getUserDefinedFormat().setPresenterValue(null);

      VSCompositeFormat reloaded = parse(write(fmt));

      assertTrue(reloaded.getUserDefinedFormat().isPresenterValueDefined());
      assertFalse(reloaded.getUserDefinedFormat().isRoundCornerValueDefined());
      assertEquals(6, reloaded.getRoundCorner());
   }

   @Test
   void cornerAloneReloadsWithoutAPresenter() throws Exception {
      // an author squaring a modern card
      VSCompositeFormat fmt = new VSCompositeFormat();
      fmt.getDefaultFormat().setRoundCornerValue(6);
      fmt.getUserDefinedFormat().setRoundCornerValue(0);

      VSCompositeFormat reloaded = parse(write(fmt));

      assertTrue(reloaded.getUserDefinedFormat().isRoundCornerValueDefined());
      assertFalse(reloaded.getUserDefinedFormat().isPresenterValueDefined());
      assertEquals(0, reloaded.getRoundCorner());
   }

   @Test
   void eitherFlagStillWritesTheSharedBitForOlderBuilds() throws Exception {
      VSFormat corner = new VSFormat();
      corner.setRoundCornerValue(0);
      VSFormat presenter = new VSFormat();
      presenter.setPresenterValue(null);

      assertEquals(SHARED_BIT, hint(corner) & SHARED_BIT);
      assertEquals(SHARED_BIT, hint(presenter) & SHARED_BIT);
      assertEquals(0, hint(new VSFormat()) & SHARED_BIT);
   }

   private static String composite(int defaultCorner, int userCorner, int userHint) {
      return "<VSCompositeFormat class=\"inetsoft.uql.viewsheet.VSCompositeFormat\">" +
         "<defaultFormat><VSFormat class=\"inetsoft.uql.viewsheet.VSFormat\" roundCorner=\"" +
         defaultCorner + "\" hint=\"0\"/></defaultFormat>" +
         "<userFormat><VSFormat class=\"inetsoft.uql.viewsheet.VSFormat\" roundCorner=\"" +
         userCorner + "\" hint=\"" + userHint + "\"/></userFormat>" +
         "</VSCompositeFormat>";
   }

   private static VSCompositeFormat parse(String xml) throws Exception {
      VSCompositeFormat fmt = new VSCompositeFormat();
      fmt.parseXML(element(xml));
      return fmt;
   }

   private static int hint(VSFormat fmt) throws Exception {
      return Integer.parseInt(element(write(fmt)).getAttribute("hint"));
   }

   private static String write(XMLSerializable fmt) {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      fmt.writeXML(writer);
      writer.flush();
      return buffer.toString();
   }

   private static Element element(String xml) throws Exception {
      return DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
   }
}
