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
package inetsoft.uql.rest.pagination;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaginationSpecTest {
   @Test
   void stopOnShortPageIsOffByDefault() {
      assertFalse(new PaginationSpec().isStopOnShortPage());
      assertFalse(PaginationSpec.builder().build().isStopOnShortPage());
   }

   @Test
   void stopOnShortPageSurvivesCloneBuilderAndXml() throws Exception {
      final PaginationSpec spec = PaginationSpec.builder()
         .type(PaginationType.OFFSET)
         .stopOnShortPage(true)
         .build();

      assertTrue(spec.isStopOnShortPage());
      assertTrue(((PaginationSpec) spec.clone()).isStopOnShortPage());
      assertTrue(PaginationSpec.builder().with(spec).build().isStopOnShortPage());

      final ByteArrayOutputStream output = new ByteArrayOutputStream();
      final PrintWriter writer = new PrintWriter(output);
      writer.format("<root>%n");
      spec.writeXML(writer);
      writer.format("</root>%n");
      writer.close();
      final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(output.toByteArray()));
      final PaginationSpec parsed = new PaginationSpec();
      parsed.parseXML(doc.getDocumentElement());

      assertTrue(parsed.isStopOnShortPage());
   }

   @Test
   void xmlWritingAndParsing() throws Exception {
      final PaginationSpec spec = new PaginationSpec();

      final ByteArrayOutputStream output = new ByteArrayOutputStream();
      final PrintWriter writer = new PrintWriter(output);
      writer.format("<root>%n");
      spec.writeXML(writer);
      writer.format("</root>%n");
      writer.close();

      final ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
      final DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      final Document doc = builder.parse(input);
      input.close();

      spec.parseXML(((Element) doc.getDocumentElement().getChildNodes().item(1)));
   }
}