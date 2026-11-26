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
package inetsoft.uql.rest.datasource.influxdb;

import inetsoft.test.EndpointsSpringContext;
import inetsoft.test.SreeHome;
import inetsoft.uql.rest.auth.AuthType;
import inetsoft.uql.tabular.HttpParameter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InfluxDB 2 accepts "Authorization: Token <token>" (InfluxDB API reference) or Basic
 * authentication; the data source offers both as an authentication scheme.
 */
@EndpointsSpringContext
@SreeHome
@Tag("core")
class InfluxDBAuthTest {
   private static InfluxDBDataSource roundTrip(InfluxDBDataSource source) throws Exception {
      StringWriter text = new StringWriter();
      PrintWriter writer = new PrintWriter(text);
      source.writeXML(writer);
      writer.flush();
      Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(text.toString().getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
      InfluxDBDataSource target = new InfluxDBDataSource();
      target.parseXML(root);
      return target;
   }

   @Test
   void tokenSchemeSendsTheTokenHeaderAndBasicSchemeDoesNot() {
      InfluxDBDataSource ds = new InfluxDBDataSource();
      ds.setName("influx-" + System.nanoTime());
      ds.setApiToken("tok123");
      ds.setAuthScheme("TOKEN");
      assertEquals(AuthType.NONE, ds.getAuthType());
      HttpParameter[] params = ds.getQueryHttpParameters();
      assertEquals(1, params.length);
      assertEquals("Authorization", params[0].getName());
      assertEquals("Token tok123", params[0].getValue());

      ds.setAuthScheme("BASIC");
      assertEquals(AuthType.BASIC, ds.getAuthType());

      for(HttpParameter p : ds.getQueryHttpParameters()) {
         assertFalse(p.getValue() != null && p.getValue().contains("tok123"), p.getName());
      }
   }

   @Test
   void tokenSchemeSurvivesSaveAndLoad() throws Exception {
      InfluxDBDataSource ds = new InfluxDBDataSource();
      ds.setName("influx-" + System.nanoTime());
      ds.setAuthScheme("TOKEN");
      ds.setApiToken("tok123");
      InfluxDBDataSource copy = roundTrip(ds);
      assertEquals("TOKEN", copy.getAuthScheme());
      assertTrue(copy.isTokenAuth());
      assertEquals("tok123", copy.getApiToken());
      assertEquals("Token tok123", copy.getQueryHttpParameters()[0].getValue());
   }
}
