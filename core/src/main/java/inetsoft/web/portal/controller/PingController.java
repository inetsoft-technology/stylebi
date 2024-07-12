/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.portal.controller;

import inetsoft.web.cluster.ServerClusterClient;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Controller for pinging. Used to bypass authentication to see if server is running.
 *
 * @since 12.3
 */
@RestController
public class PingController {
   @PostConstruct
   public void createClient() {
      this.client = new ServerClusterClient();
   }

   @GetMapping("/ping")
   public void ping(HttpServletResponse response) throws IOException {
      response.setContentType("text/plain");

      try(PrintWriter writer = response.getWriter()) {
         writer.println(client.getStatus().getStatus().name().toLowerCase());
      }
   }

   private ServerClusterClient client;
}
