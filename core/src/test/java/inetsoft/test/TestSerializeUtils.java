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

package inetsoft.test;

import inetsoft.uql.XTable;

import java.io.*;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TestSerializeUtils {
   public static XTable serializeAndDeserialize(XTable originalTable) throws Exception {
      originalTable.moreRows(XTable.EOT);
      byte[] serializedData = serializeTable(originalTable);
      XTable deserializedTable = deserializeTable(serializedData);

      // Ensure the deserialized object has the expected data
      XTableUtil.assertEquals(originalTable, deserializedTable);

      return deserializedTable;
   }

   public static byte[] serializeTable(XTable table) throws Exception {
      // Serialize to a byte array
      byte[] serializedData;

      try(ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
          ObjectOutputStream out = new ObjectOutputStream(byteOut))
      {
         out.writeObject(table);
         serializedData = byteOut.toByteArray();
      }

      assertNotNull(serializedData);
      assertTrue(serializedData.length > 0);
      return serializedData;
   }

   public static XTable deserializeTable(byte[] serializedData) throws Exception {
      // Deserialize from byte array
      XTable deserializedTable;

      try(ByteArrayInputStream byteIn = new ByteArrayInputStream(serializedData);
          ObjectInputStream in = new ObjectInputStream(byteIn))
      {

         deserializedTable = (XTable) in.readObject();
      }

      assertNotNull(deserializedTable);
      return deserializedTable;
   }
}
