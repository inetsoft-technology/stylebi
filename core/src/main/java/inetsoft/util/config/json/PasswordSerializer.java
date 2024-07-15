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
package inetsoft.util.config.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import inetsoft.util.PasswordEncryption;

import java.io.IOException;

/**
 * {@code PasswordSerializer} handles serializing passwords.
 */
public class PasswordSerializer extends StdSerializer<String> {
   public PasswordSerializer() {
      super(String.class);
   }

   @Override
   public void serialize(String value, JsonGenerator gen, SerializerProvider provider)
      throws IOException
   {
      gen.writeString(encryption.get().encryptMasterPassword(value));
   }

   static ThreadLocal<PasswordEncryption> encryption = new ThreadLocal<>();
}
