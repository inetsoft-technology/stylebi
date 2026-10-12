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

package inetsoft.report.internal;

import inetsoft.report.internal.table.PresenterRef;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77456: a malformed Format persisted as a presenter parameter must not abort parsing
 * the whole viewsheet.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CommonGetFormatTest {
   @ParameterizedTest(name = "{0} \"{1}\"")
   @CsvSource(delimiter = '|', value = {
      "MessageFMT|{0,choice,}",
      "DecimalFMT|#,##0.0.0",
      "SimpleDateFMT|yyyy-qq",
      // Bug #77817: specs without a limit separator parse to a ChoiceFormat with no limits
      "ChoiceFMT|abc",
      "ChoiceFormat|abc",
      "ChoiceFMT|1",
      "ChoiceFMT|''"
   })
   void malformedSpecReturnsNull(String type, String spec) {
      assertNull(Common.getFormat(type, spec));
   }

   @Test
   void validSpecIsUnaffected() {
      assertEquals("1,234.50", Common.getFormat("DecimalFMT", "#,##0.00").format(1234.5));
      assertEquals("5 ok", Common.getFormat("MessageFMT", "{0} ok")
         .format(new Object[] { 5 }));
      assertNull(Common.getFormat("MessageFMT", null));
      assertEquals("one", Common.getFormat("ChoiceFMT", "1#one").format(5));
      assertEquals("zero", Common.getFormat("ChoiceFMT", "0#zero|1#one").format(0));
      assertEquals("one", Common.getFormat("ChoiceFormat", "0#zero|1#one").format(1));
      assertInstanceOf(NumberFormat.class, Common.getFormat("ChoiceFMT", null));
   }

   // Bug #77817: RulerPresenter formats with no guard, so a limitless ChoiceFormat parameter
   // made paint() throw ArrayIndexOutOfBoundsException
   @Test
   void limitlessChoiceFormatIsDroppedFromPresenter() throws Exception {
      String xml = "<presenter name=\"inetsoft.report.painter.RulerPresenter\">" +
         "<presenterParameter Name=\"format\" Type=\"Format\" FormatType=\"ChoiceFMT\" " +
         "Format=\"abc\"/></presenter>";
      PresenterRef ref = new PresenterRef();
      ref.parseXML(Tool.parseXML(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement());

      assertEquals("inetsoft.report.painter.RulerPresenter", ref.getName());
      assertNull(ref.getParameter("format"), "the limitless format is dropped");

      BufferedImage img = new BufferedImage(200, 30, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();

      try {
         assertDoesNotThrow(() -> ref.createPresenter().paint(g, 5, 0, 0, 200, 30));
      }
      finally {
         g.dispose();
      }
   }

   @Test
   void badPresenterFormatDropsOnlyThatParameter() throws Exception {
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(createText("Text1", "{0,choice,}"));
      vs.addAssembly(createText("Text2", "{0} ok"));

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
      vs.writeXML(writer);
      writer.close();
      String xml = out.toString(StandardCharsets.UTF_8);
      assertTrue(xml.contains("Format=\"{0,choice,}\""), "the bad format is persisted");

      Element elem = Tool.parseXML(new ByteArrayInputStream(out.toByteArray()))
         .getDocumentElement();
      Viewsheet parsed = new Viewsheet();
      parsed.parseXML(elem);

      assertEquals(2, parsed.getAssemblies().length, "the rest of the viewsheet still loads");
      PresenterRef bad = getPresenter(parsed, "Text1");
      PresenterRef good = getPresenter(parsed, "Text2");
      assertNotNull(bad, "the presenter itself is kept");
      assertEquals(PRESENTER, bad.getName());
      assertNull(bad.getParameter("format"), "the malformed parameter is dropped");
      assertEquals("{0} ok", ((inetsoft.util.MessageFormat) good.getParameter("format")).toPattern());
   }

   private static TextVSAssembly createText(String name, String spec) {
      TextVSAssembly text = new TextVSAssembly();
      text.getVSAssemblyInfo().setName(name);
      PresenterRef ref = new PresenterRef(PRESENTER);
      // the JDK MessageFormat accepts specs that inetsoft.util.MessageFormat rejects (#77415)
      ref.setParameter("format", new MessageFormat(spec));
      text.getVSAssemblyInfo().getFormat().getUserDefinedFormat().setPresenterValue(ref);
      return text;
   }

   private static PresenterRef getPresenter(Viewsheet vs, String name) {
      return ((TextVSAssembly) vs.getAssembly(name)).getVSAssemblyInfo().getFormat()
         .getUserDefinedFormat().getPresenterValue();
   }

   private static final String PRESENTER = "inetsoft.report.painter.BulletGraphPresenter";
}
