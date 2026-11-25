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
package inetsoft.uql.rest.datasource.lighthouse;

import inetsoft.test.EndpointsSpringContext;
import inetsoft.test.SreeHome;
import inetsoft.uql.tabular.HttpParameter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lighthouse API Basics: requests go to https://&lt;account&gt;.lighthouseapp.com and the API token is
 * sent in the X-LighthouseToken header.
 */
@EndpointsSpringContext
@SreeHome
@Tag("core")
class LighthouseDataSourceTest {
   private static LighthouseDataSource source() {
      LighthouseDataSource ds = new LighthouseDataSource();
      ds.setName("lh-" + System.nanoTime());
      ds.setDomain("acme");
      ds.setApiToken("lh-token");
      return ds;
   }

   @Test
   void tokenIsSentInTheLighthouseTokenHeader() {
      Map<String, String> headers = new LinkedHashMap<>();

      for(HttpParameter p : source().getQueryHttpParameters()) {
         headers.put(p.getName(), p.getValue());
      }

      assertEquals("lh-token", headers.get("X-LighthouseToken"));
      assertEquals("application/json", headers.get("Content-Type"));
   }

   @Test
   void urlNamesTheAccountDomain() {
      assertTrue(source().getURL().endsWith("acme.lighthouseapp.com"), source().getURL());
   }

   @Test
   void apiTokenTravelsOverTls() {
      assertEquals("https://acme.lighthouseapp.com", source().getURL());
   }
}
