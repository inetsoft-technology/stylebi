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
package inetsoft.uql.rest;

import java.io.IOException;
import java.net.*;
import java.time.Duration;

class ConnectionTester {
   /**
    * Tests whether the url's host is reachable via port 80.
    *
    * @param urlString the url to test
    *
    * @return true if the url is reachable, false if it is not reachable within five seconds.
    */
   boolean isReachable(String urlString) {
      try {
         final URL url = new URL(urlString);
         final int port = inferPort(url);
         final Duration timeout = Duration.ofSeconds(5);
         final InetSocketAddress address = new InetSocketAddress(url.getHost(), port);

         try(Socket socket = new Socket()) {
            socket.connect(address, (int) timeout.toMillis());
         }

         return true;
      }
      catch(IOException ex) {
         return false;
      }
   }

   private int inferPort(URL url) throws MalformedURLException {
      final int port;

      if(url.getPort() != -1) {
         port = url.getPort();
      }
      else if(url.getDefaultPort() != -1) {
         port = url.getDefaultPort();
      }
      else {
         throw new MalformedURLException("URL has no associated port: " + url);
      }

      return port;
   }
}
