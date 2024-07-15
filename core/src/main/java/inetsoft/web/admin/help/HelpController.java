/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.help;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.util.Tool;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import jakarta.annotation.PostConstruct;
import org.owasp.encoder.Encode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;

@RestController
public class HelpController {
   @Autowired
   public HelpController(ObjectMapper objectMapper) {
      this.objectMapper = objectMapper;
   }

   @PostConstruct
   public void loadLinks() throws IOException {
      try(InputStream input = getClass().getResourceAsStream("help-links.json")) {
         String prefix = Tool.getHelpURL(true) + "/#cshid=";
         HelpLinks.Builder builder = HelpLinks.builder();
         // prefix with help base URL
         objectMapper.readValue(input, HelpLinks.class).links().stream()
            .map(l -> HelpLink.builder().from(l).link(prefix + l.link()).build())
            .forEach(builder::addLinks);
         // add default help link
         builder.addLinks(HelpLink.builder().route("").link(prefix + "EM").build());
         helpLinks = builder.build();
      }
   }

   @GetMapping("/api/em/help-links")
   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM, resource = "*", actions = ResourceAction.ACCESS
      )
   )
   public HelpLinks getHelpLinks(Principal princpal) {
      return helpLinks;
   }

   @GetMapping("/api/em/help-url")
   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM, resource = "*", actions = ResourceAction.ACCESS
      )
   )
   public String getHelpUrL(Principal princpal) {
      return Encode.forUri(Tool.getHelpURL(true));
   }

   private HelpLinks helpLinks;
   private final ObjectMapper objectMapper;
}
