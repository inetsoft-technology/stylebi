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
package inetsoft.uql.odata;

import inetsoft.util.Tool;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80961: a query typed by {@code $metadata} is saved with its worksheet, so the flag has to
 * survive a write/parse round trip; if it did not, a reloaded worksheet would silently go back to
 * text dates.
 */
class ODataQueryTypedByTest {
   private static ODataQuery roundTrip(ODataQuery query) throws Exception {
      StringWriter xml = new StringWriter();
      PrintWriter writer = new PrintWriter(xml);
      query.writeXML(writer);
      writer.flush();

      Document doc = Tool.parseXML(new StringReader(xml.toString()));
      ODataQuery copy = new ODataQuery();
      copy.parseXML(doc.getDocumentElement());
      return copy;
   }

   @Test
   void typedByMetadataSurvivesSaveAndReload() throws Exception {
      ODataQuery query = new ODataQuery();
      query.setEntity("Products");
      query.setTypedBy(ODataQuery.TYPED_BY_METADATA);
      assertTrue(query.isTypedByMetadata());

      ODataQuery copy = roundTrip(query);

      assertEquals("Products", copy.getEntity());
      assertEquals(ODataQuery.TYPED_BY_METADATA, copy.getTypedBy());
      assertTrue(copy.isTypedByMetadata());
   }

   @Test
   void queryWithoutTheFlagStaysUntypedAfterReload() throws Exception {
      ODataQuery query = new ODataQuery();
      query.setEntity("Products");
      assertFalse(query.isTypedByMetadata());

      ODataQuery copy = roundTrip(query);

      assertNull(copy.getTypedBy());
      assertFalse(copy.isTypedByMetadata());
   }

   @Test
   void otherTypedByValuesAreNotMetadataTyping() {
      ODataQuery query = new ODataQuery();
      query.setTypedBy("something else");
      assertFalse(query.isTypedByMetadata());
   }
}
