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
package inetsoft.uql.rest.datasource.toggl;

import inetsoft.test.EndpointsSpringContext;
import inetsoft.test.SreeHome;
import inetsoft.uql.rest.HttpResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@EndpointsSpringContext
@SreeHome
@Tag("core")
class TogglTestResponseHintTest {
   @Test
   void hintAddedForToggl2KeyOnFailure() {
      Exception e = assertThrows(Exception.class,
         () -> validate("toggl_sk_0123456789abcdef", 403, "Forbidden"));
      assertTrue(e.getMessage().startsWith("Test request returned 403: Forbidden"));
      assertTrue(e.getMessage().contains(TogglDataSource.TOGGL_2_KEY_HINT));
      assertFalse(e.getMessage().contains("0123456789abcdef"));
   }

   @Test
   void noHintForClassicToken() {
      Exception e = assertThrows(Exception.class,
         () -> validate("1971800d4d82861d8f2c1651fea4d212", 403, "Forbidden"));
      assertEquals("Test request returned 403: Forbidden", e.getMessage());
   }

   @Test
   void noHintForNullOrEmptyToken() {
      assertEquals("Test request returned 401: Unauthorized",
         assertThrows(Exception.class, () -> validate(null, 401, "Unauthorized")).getMessage());
      assertEquals("Test request returned 401: Unauthorized",
         assertThrows(Exception.class, () -> validate("", 401, "Unauthorized")).getMessage());
   }

   @Test
   void successfulResponseNotRejectedEvenWithToggl2Prefix() {
      assertDoesNotThrow(() -> validate("toggl_sk_0123456789abcdef", 200, "OK"));
   }

   private void validate(String token, int status, String text) throws Exception {
      TogglDataSource ds = new TogglDataSource();
      ds.setApiToken(token);
      ds.validateTestResponse(new StubResponse(status, text));
   }

   private static final class StubResponse implements HttpResponse {
      StubResponse(int status, String text) {
         this.status = status;
         this.text = text;
      }

      @Override public InputStream getResponseBodyAsStream() { return null; }
      @Override public String getResponseHeaderValue(String headerName) { return null; }
      @Override public Map<String, String> getResponseHeaders() { return Map.of(); }
      @Override public String getURI() { return null; }
      @Override public int getResponseStatusCode() { return status; }
      @Override public String getResponseStatusText() { return text; }
      @Override public long getContentLength() { return 0; }
      @Override public void close() { }

      private final int status;
      private final String text;
   }
}
