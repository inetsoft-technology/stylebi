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
package inetsoft.sree.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public interface JsonConfigurableProvider {
   /**
    * Reads the configuration for this provider from JSON.
    *
    * @param configuration the JSON representation of the configuration.
    */
   default void readConfiguration(JsonNode configuration) {
   }

   /**
    * Writes the configuration for this provider as JSON.
    *
    * @param mapper the {@code ObjectMapper} used to create JSON nodes.
    *
    * @return the JSON representation of the configuration.
    */
   default JsonNode writeConfiguration(ObjectMapper mapper) {
      return mapper.createObjectNode();
   }

   /**
    * Gets a name that identifies this provider instance.
    *
    * @return the provider name.
    */
   String getProviderName();

   /**
    * Sets the name that identifies this provider instance.
    *
    * @param providerName the provider name.
    */
   void setProviderName(String providerName);
}
