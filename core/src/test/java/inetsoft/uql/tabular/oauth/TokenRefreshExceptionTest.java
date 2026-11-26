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
package inetsoft.uql.tabular.oauth;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Bug #82260: the message of a failed token refresh. */
@Tag("core")
class TokenRefreshExceptionTest {
   @Test
   void messageNamesDataSourceCauseAndAuthorize() {
      Exception cause = new Exception("Failed to refresh token [invalid_grant]: expired");
      TokenRefreshException ex = new TokenRefreshException("ds1", cause);

      assertEquals("Failed to refresh the access token of data source 'ds1': " +
                   "Failed to refresh token [invalid_grant]: expired. " +
                   "Please authorize the data source again.", ex.getMessage());
      assertSame(cause, ex.getCause());
   }

   @Test
   void emptyCauseMessageIsOmitted() {
      assertEquals("Failed to refresh the access token of data source 'ds1'. " +
                   "Please authorize the data source again.",
                   new TokenRefreshException("ds1", new NullPointerException()).getMessage());
      assertEquals("Failed to refresh the access token of data source 'ds1'. " +
                   "Please authorize the data source again.",
                   new TokenRefreshException("ds1", new Exception("  ")).getMessage());
   }

   @Test
   void trailingPeriodOfCauseIsNotDoubled() {
      String message = new TokenRefreshException("ds1", new Exception("expired...")).getMessage();

      assertTrue(message.contains("expired. Please"), message);
      assertFalse(message.contains(".."), message);
   }
}
