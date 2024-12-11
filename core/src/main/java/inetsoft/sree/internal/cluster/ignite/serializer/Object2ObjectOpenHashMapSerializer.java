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

package inetsoft.sree.internal.cluster.ignite.serializer;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.apache.ignite.binary.*;

import java.util.Map;

public class Object2ObjectOpenHashMapSerializer implements BinarySerializer {
   @Override
   public void writeBinary(Object obj, BinaryWriter writer) throws BinaryObjectException {
      Object2ObjectOpenHashMap<?, ?> map = (Object2ObjectOpenHashMap<?, ?>) obj;
      writer.writeInt("size", map.size());

      for(Map.Entry<?, ?> entry : map.entrySet()) {
         writer.writeObject("key", entry.getKey());
         writer.writeObject("value", entry.getValue());
      }
   }

   @Override
   public void readBinary(Object obj, BinaryReader reader) throws BinaryObjectException {
      Object2ObjectOpenHashMap<Object, Object> map = (Object2ObjectOpenHashMap<Object, Object>) obj;
      int size = reader.readInt("size");

      for(int i = 0; i < size; i++) {
         Object key = reader.readObject("key");
         Object value = reader.readObject("value");
         map.put(key, value);
      }
   }
}
