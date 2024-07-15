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
package inetsoft.web.admin.authz;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class ComponentAuthorizationService {

   @PostConstruct
   public void loadComponents() throws IOException {
      try(InputStream input = getClass().getResourceAsStream("view-components.json")) {
         componentTree = new ObjectMapper().readValue(input, ViewComponent.class);
      }
   }

   public ViewComponent getComponentTree() {
      return getComponent(null);
   }

   public ViewComponent getComponent(String path) {
      if(path == null || path.isEmpty() || "/".equals(path)) {
         return componentTree;
      }

      ViewComponent component = componentTree;

      for(String name : path.split("/")) {
         component = component.children().get(name);

         if(component == null) {
            break;
         }
      }

      return component;
   }

   private ViewComponent componentTree;
}
