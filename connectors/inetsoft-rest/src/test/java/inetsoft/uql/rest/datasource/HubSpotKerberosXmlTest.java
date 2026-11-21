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
package inetsoft.uql.rest.datasource;

import inetsoft.test.EndpointsSpringContext;
import inetsoft.test.SreeHome;
import inetsoft.uql.rest.datasource.hubspot.HubSpotDataSource;
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

@EndpointsSpringContext
@SreeHome
@Tag("core")
class HubSpotKerberosXmlTest {
   @Test
   void kerberosSettingsWithXmlSpecialCharactersSurviveSaving() throws Exception {
      HubSpotDataSource source = new HubSpotDataSource();
      source.setServicePrincipalName("HTTP/a&b<c");
      source.setImpersonatePrincipal("u&v<w");
      StringWriter text = new StringWriter();
      PrintWriter writer = new PrintWriter(text);
      source.writeXML(writer);
      writer.flush();
      Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(text.toString().getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
      HubSpotDataSource target = new HubSpotDataSource();
      target.parseXML(root);
      assertEquals("HTTP/a&b<c", target.getServicePrincipalName());
      assertEquals("u&v<w", target.getImpersonatePrincipal());
   }
}
