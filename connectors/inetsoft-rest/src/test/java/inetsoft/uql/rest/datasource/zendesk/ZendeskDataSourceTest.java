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
package inetsoft.uql.rest.datasource.zendesk;

import inetsoft.test.EndpointsSpringContext;
import inetsoft.test.SreeHome;
import inetsoft.test.TestHttpResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Zendesk: the API token login name and the Test Data Source check. Zendesk documents the user
 * name of API token authentication as {email}/token, and users/me.json returns user.verified.
 */
@EndpointsSpringContext
@SreeHome
@Tag("core")
class ZendeskDataSourceTest {
   private static ZendeskDataSource source() {
      ZendeskDataSource ds = new ZendeskDataSource();
      ds.setName("zd-" + System.nanoTime());
      return ds;
   }

   @Test
   void userNameGetsTheTokenSuffixOnce() {
      ZendeskDataSource ds = source();
      ds.setUser("a@example.com");
      assertEquals("a@example.com/token", ds.getUser());
      ds.setUser("a@example.com/token");
      assertEquals("a@example.com/token", ds.getUser());
   }

   @Test
   void urlIsBuiltFromTheDomain() {
      ZendeskDataSource ds = source();
      assertEquals("https://[Domain].zendesk.com", ds.getURL());
      ds.setDomain("acme");
      assertEquals("https://acme.zendesk.com", ds.getURL());
   }

   @Test
   void verifiedUserPassesTheTest() {
      assertDoesNotThrow(() -> source().validateTestResponse(
         new TestHttpResponse("{\"user\":{\"id\":1,\"verified\":true}}")));
   }

   @Test
   void unverifiedUserFailsTheTest() {
      IllegalStateException e = assertThrows(IllegalStateException.class, () ->
         source().validateTestResponse(new TestHttpResponse("{\"user\":{\"verified\":false}}")));
      assertTrue(e.getMessage().contains("Unverified"), e.getMessage());
   }

   @Test
   void responseWithoutVerifiedFieldFailsTheTest() {
      assertThrows(IllegalStateException.class, () ->
         source().validateTestResponse(new TestHttpResponse("{\"user\":{\"id\":1}}")));
   }
}
