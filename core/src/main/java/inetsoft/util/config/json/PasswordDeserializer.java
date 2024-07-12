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
package inetsoft.util.config.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import inetsoft.util.PasswordEncryption;

import java.io.IOException;

/**
 * {@code PasswordDeserializer} handles deserializing passwords.
 */
public class PasswordDeserializer extends StdDeserializer<String> {
   public PasswordDeserializer() {
      super(String.class);
   }

   @Override
   public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      String value = p.getValueAsString();

      if(value == null) {
         return null;
      }

      return encryption.get().decryptMasterPassword(value);
   }

   static ThreadLocal<PasswordEncryption> encryption = new ThreadLocal<>();
}
