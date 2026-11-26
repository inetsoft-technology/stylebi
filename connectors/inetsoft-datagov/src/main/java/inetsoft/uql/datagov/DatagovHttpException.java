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
package inetsoft.uql.datagov;

/**
 * A Socrata site answered a request with a non-2xx status. Carries the status itself because the
 * JDK reports 404 and 410 through the same {@link java.io.FileNotFoundException}, so the status
 * cannot be recovered from the exception type, and a retired endpoint (410) must not be reported as
 * a dataset that does not exist (404).
 */
final class DatagovHttpException extends Exception {
   DatagovHttpException(int status, String url, String body) {
      super("Datagov site answered HTTP " + status + " for " + url +
            (body == null || body.isEmpty() ? "" : ": " + body));
      this.status = status;
      this.url = url;
   }

   /**
    * @return the HTTP status code of the response.
    */
   int getStatus() {
      return status;
   }

   /**
    * @return the URL that was requested.
    */
   String getUrl() {
      return url;
   }

   private final int status;
   private final String url;
}
