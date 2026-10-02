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
package inetsoft.sree;

import inetsoft.test.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77588: a repository entry whose path or identifier holds a control character (or, for
 * the identifier attribute, {@code "} or {@code <}) must be written as XML that parses again and
 * gives back the same value. The seed harness ({@code AssetSeedTest}) can't see attribute
 * whitespace normalization, so these tests assert the decoded field values.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RepositoryEntryControlCharTest {
   @ParameterizedTest
   @ValueSource(strings = { "\t", "\n", "\r", "\u001f", "\u0000", "\u007f", "\"", "<", "&" })
   void viewsheetIdentifierRoundTrips(String ch) throws Exception {
      String identifier = "1^128^__NULL__^Examples" + ch + "Census^host-org";
      ViewsheetEntry entry = new ViewsheetEntry("Examples/Census");
      entry.setIdentifier(identifier);

      ViewsheetEntry read = new ViewsheetEntry();
      read.parseXML(reparse(entry));

      assertEquals(identifier, read.getIdentifier());
   }

   @ParameterizedTest
   @ValueSource(strings = { "\t", "\n", "\r", "\u001f", "\u0000", "\u007f", "\"", "<", "&" })
   void worksheetIdentifierRoundTrips(String ch) throws Exception {
      String identifier = "1^2^__NULL__^Examples" + ch + "Census^host-org";
      WorksheetEntry entry = new WorksheetEntry("Examples/Census");
      entry.setIdentifier(identifier);

      // WorksheetEntry.parseAttributes(Element, boolean) calls super.parseAttributes(Element),
      // which dispatches back to it and overflows the stack (a separate, latent defect: no
      // product code parses a WorksheetEntry). Decode the attribute the way that reader does.
      org.w3c.dom.Element read = reparse(entry);

      assertEquals(identifier, Tool.byteDecode(Tool.getAttribute(read, "identifier")));
   }

   @ParameterizedTest
   @ValueSource(strings = { "\u001f", "\u0000", "\u0001", "\r", "\t", "\n" })
   void pathRoundTrips(String ch) throws Exception {
      String path = "Examples" + ch + "Census";
      ViewsheetEntry entry = new ViewsheetEntry(path);
      entry.setIdentifier("1^128^__NULL__^Examples/Census^host-org");

      ViewsheetEntry read = new ViewsheetEntry();
      read.parseXML(reparse(entry));

      assertEquals(path, read.getPath());
   }

   @Test
   void tabAndNewlineStayRawInCdata() {
      ViewsheetEntry entry = new ViewsheetEntry("Examples\tCen\nsus");
      entry.setIdentifier("1^128^__NULL__^Examples/Census^host-org");

      String xml = write(entry);

      assertTrue(xml.contains("<path><![CDATA[Examples\tCen\nsus]]></path>"), xml);
   }

   private static String write(RepositoryEntry entry) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         entry.writeXML(writer);
      }

      return buffer.toString();
   }

   private static org.w3c.dom.Element reparse(RepositoryEntry entry) throws Exception {
      String xml = write(entry);
      Document doc = Tool.parseXML(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
      return doc.getDocumentElement();
   }
}
