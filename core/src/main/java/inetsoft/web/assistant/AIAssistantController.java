/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.web.assistant;

import inetsoft.sree.SreeEnv;
import inetsoft.web.viewsheet.service.LinkUriArgumentResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AIAssistantController {

   @GetMapping("/api/assistant/get-chat-app-server-url")
   public String getChatAppServerUrl() {
      return SreeEnv.getProperty(CHAT_APP_SERVER_URL);
   }

   /**
    * Returns the full StyleBI server URL.
    * This URL is used as the JWT issuer for SSO tokens and should be passed
    * to external applications (like chat-app) to enable them to verify tokens
    * by fetching the JWKS from ${styleBIUrl}/sso/jwks.
    */
   @GetMapping("/api/assistant/get-stylebi-url")
   public String getStyleBIUrl(HttpServletRequest request) {
      String url = LinkUriArgumentResolver.getLinkUri(request);

      // Remove trailing slash for consistency
      if(url != null && url.endsWith("/")) {
         url = url.substring(0, url.length() - 1);
      }

      return url;
   }

   public static final String CHAT_APP_SERVER_URL = "chat.app.server.url";
}
