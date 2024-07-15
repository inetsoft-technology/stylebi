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
package inetsoft.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.internal.cluster.SingletonCallableTask;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * {@code GetKeyValueTask} is a cluster singleton task that gets a key-value pair from a store. The
 * pair is returned as a GZIPed JSON document.
 */
public class GetKeyValueTask implements SingletonCallableTask<byte[]> {
   /**
    * Creates a new instance of {@code GetKeyValueTask}.
    *
    * @param id  the unique identifier of the key-value store.
    * @param key the entry key.
    */
   public GetKeyValueTask(String id, String key) {
      this.id = id;
      this.key = key;
   }

   @Override
   public byte[] call() throws Exception {
      KeyValuePair<Object> pair = KeyValueEngine.getInstance().get(id, key);
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();

      try(GZIPOutputStream output = new GZIPOutputStream(buffer)) {
         ObjectMapper mapper = KeyValueEngine.createObjectMapper();
         mapper.writeValue(output, pair);
      }

      return buffer.toByteArray();
   }

   @SuppressWarnings("unchecked")
   public static KeyValuePair<Object> parse(byte[] data, ObjectMapper objectMapper)
      throws IOException
   {
      ByteArrayInputStream buffer = new ByteArrayInputStream(data);
      GZIPInputStream input = new GZIPInputStream(buffer);
      return objectMapper.readValue(input, KeyValuePair.class);
   }

   private final String id;
   private final String key;
}
