/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.mv.trans;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that {@link UserInfo} (marked {@code Serializable} as of 1b6ae84f) survives a Java
 * serialization round trip. {@code UserInfo} hints are collected onto
 * {@link TransformationDescriptor#getUserInfo()} during MV analysis and surfaced to the user
 * through analysis-job results that may be cached/transferred across cluster nodes -- if this
 * class were not serializable, that would fail with a {@code NotSerializableException} instead
 * of quietly succeeding.
 */
@Tag("core")
class UserInfoSerializationTest {
   @Test
   void survivesASerializationRoundTrip() throws Exception {
      UserInfo original = new UserInfo("Viewsheet1", "table1", "some hint message");

      UserInfo roundTripped = (UserInfo) deserialize(serialize(original));

      assertEquals(original, roundTripped);
      assertEquals("Viewsheet1", roundTripped.getSheetName());
      assertEquals("table1", roundTripped.getBoundTable());
      assertEquals("some hint message", roundTripped.getMessage());
   }

   private static byte[] serialize(Object o) throws IOException {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
         out.writeObject(o);
      }

      return bytes.toByteArray();
   }

   private static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
      try(ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
         return in.readObject();
      }
   }
}
