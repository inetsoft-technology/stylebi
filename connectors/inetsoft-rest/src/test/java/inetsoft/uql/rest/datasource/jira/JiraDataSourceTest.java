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
package inetsoft.uql.rest.datasource.jira;

import inetsoft.test.EndpointsSpringContext;
import inetsoft.test.SreeHome;
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
 * Jira Cloud authenticates with the account e-mail and an API token (Atlassian REST API basic
 * auth); a local installation uses the user name and password.
 */
@EndpointsSpringContext
@SreeHome
@Tag("core")
class JiraDataSourceTest {
   private static JiraDataSource source() {
      JiraDataSource ds = new JiraDataSource();
      ds.setName("jira-" + System.nanoTime());
      ds.setUser("me@example.com");
      ds.setApiToken("api-token");
      ds.setLocalPassword("local-pw");
      return ds;
   }

   private static JiraDataSource roundTrip(JiraDataSource source) throws Exception {
      StringWriter text = new StringWriter();
      PrintWriter writer = new PrintWriter(text);
      source.writeXML(writer);
      writer.flush();
      Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(text.toString().getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
      JiraDataSource target = new JiraDataSource();
      target.parseXML(root);
      return target;
   }

   @Test
   void cloudServerUsesTheDomainAndTheApiTokenAsPassword() {
      JiraDataSource ds = source();
      assertTrue(ds.isCloudServer());
      assertEquals("https://[domain].atlassian.net", ds.getURL());
      ds.setCloudDomain("acme");
      assertEquals("https://acme.atlassian.net", ds.getURL());
      assertEquals("api-token", ds.getPassword());
   }

   @Test
   void localServerUsesItsUrlAndThePassword() {
      JiraDataSource ds = source();
      ds.setServerType("local");
      ds.setLocalUrl("https://jira.example.com");
      assertTrue(ds.isLocalServer());
      assertEquals("https://jira.example.com", ds.getURL());
      assertEquals("local-pw", ds.getPassword());
   }

   @Test
   void serverSettingsSurviveSaveAndLoad() throws Exception {
      JiraDataSource ds = source();
      ds.setServerType("local");
      ds.setLocalUrl("https://jira.example.com/jira");
      ds.setCloudDomain("acme");
      JiraDataSource copy = roundTrip(ds);
      assertEquals("local", copy.getServerType());
      assertEquals("https://jira.example.com/jira", copy.getLocalUrl());
      assertEquals("acme", copy.getCloudDomain());
      assertEquals("local-pw", copy.getPassword());
      assertEquals("api-token", copy.getApiToken());
   }
}
