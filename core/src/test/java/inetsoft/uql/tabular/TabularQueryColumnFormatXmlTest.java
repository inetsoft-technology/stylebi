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
package inetsoft.uql.tabular;

import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.XTypeNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79776: parseContents read the saved column format extents into the column format map,
 * so after an XML round trip the extent overwrote the format and the extent was lost.
 */
@Tag("core")
class TabularQueryColumnFormatXmlTest {
   @Test
   void columnFormatAndExtentSurviveXmlRoundTrip() throws Exception {
      TestQuery query = new TestQuery();
      query.setColumnFormat("col", "yyyy-MM-dd");
      query.setColumnFormatExtent("col", "extent");

      TestQuery copy = roundTrip(query);

      assertEquals("yyyy-MM-dd", copy.getColumnFormat("col"));
      assertEquals("extent", copy.getColumnFormatExtent("col"));
   }

   @Test
   void extentOnlyColumnDoesNotGainFormat() throws Exception {
      TestQuery query = new TestQuery();
      query.setColumnFormatExtent("col", "extent");

      TestQuery copy = roundTrip(query);

      assertNull(copy.getColumnFormat("col"));
      assertEquals("extent", copy.getColumnFormatExtent("col"));
   }

   private TestQuery roundTrip(TestQuery query) throws Exception {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      query.writeXML(writer);
      writer.flush();

      Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(buffer.toString().getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
      TestQuery copy = new TestQuery();
      copy.parseXML(root);
      return copy;
   }

   public static class TestQuery extends TabularQuery {
      public TestQuery() {
         super("test");
      }

      @Override
      public XTypeNode getOutputType(Object session, boolean full) {
         return null;
      }

      @Override
      public XSelection getSelection() {
         return null;
      }
   }
}
