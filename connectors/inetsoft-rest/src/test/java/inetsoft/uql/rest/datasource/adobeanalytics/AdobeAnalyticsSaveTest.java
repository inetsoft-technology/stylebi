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
package inetsoft.uql.rest.datasource.adobeanalytics;

import inetsoft.test.EndpointsSpringContext;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@EndpointsSpringContext
@SreeHome
@Tag("core")
class AdobeAnalyticsSaveTest {
   private static AdobeAnalyticsDataSource roundTrip(AdobeAnalyticsDataSource source)
      throws Exception
   {
      StringWriter text = new StringWriter();
      PrintWriter writer = new PrintWriter(text);
      source.writeXML(writer);
      writer.flush();
      Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(text.toString().getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
      AdobeAnalyticsDataSource target = new AdobeAnalyticsDataSource();
      target.parseXML(root);
      return target;
   }

   @Test
   @Disabled("Bug #81965: a missing global company id is saved as the text 'null'; enable when it is fixed")
   void missingGlobalCompanyIdStaysMissingAfterSaving() throws Exception {
      assertNull(roundTrip(new AdobeAnalyticsDataSource()).getGlobalCompanyId());
   }

   @Test
   @Disabled("Bug #81965: the global company id is written to XML without escaping; enable when it is fixed")
   void globalCompanyIdWithXmlSpecialCharactersSurvivesSaving() throws Exception {
      AdobeAnalyticsDataSource source = new AdobeAnalyticsDataSource();
      source.setGlobalCompanyId("a&b<c");
      assertEquals("a&b<c", roundTrip(source).getGlobalCompanyId());
   }
}
