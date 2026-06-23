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
package inetsoft.util.swap;

import com.esotericsoftware.kryo.kryo5.Kryo;
import com.esotericsoftware.kryo.kryo5.io.Input;
import com.esotericsoftware.kryo.kryo5.io.Output;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class XSwapUtilTest {
   /**
    * A Kryo instance that is still checked out of the pool must not be handed
    * to a re-entrant caller, otherwise nested swap (de)serialization on the
    * same thread corrupts the shared class-resolver/reference state. (Bug 75496)
    */
   @Test
   void reentrantGetKryoReturnsDistinctInstances() {
      Kryo outer = XSwapUtil.getKryo();

      try {
         Kryo nested = XSwapUtil.getKryo();

         try {
            assertNotSame(outer, nested,
                          "re-entrant getKryo() must not return the in-use instance");
         }
         finally {
            XSwapUtil.releaseKryo(nested);
         }
      }
      finally {
         XSwapUtil.releaseKryo(outer);
      }
   }

   /**
    * A re-entrant read happening in the middle of an outer write (both via the
    * pool) must not corrupt the outer stream. Before the fix this produced a
    * stream with dangling class references that failed on read-back with
    * "Encountered unregistered class ID". (Bug 75496)
    */
   @Test
   void nestedSerializationDoesNotCorruptOuterStream() throws Exception {
      // data for a nested (inner) swap operation
      byte[] nested = write(new ArrayList<>(Arrays.asList(new Date(1), new Date(2), new Date(3))));

      Kryo outerKryo = XSwapUtil.getKryo();
      byte[] outer;

      try {
         ByteArrayOutputStream bout = new ByteArrayOutputStream();

         try(Output out = new Output(bout)) {
            outerKryo.writeClassAndObject(out, "header");

            // re-enter the pool mid-write, as a nested column load would
            Kryo innerKryo = XSwapUtil.getKryo();

            try(Input in = new Input(new ByteArrayInputStream(nested))) {
               innerKryo.readClassAndObject(in);
            }
            finally {
               XSwapUtil.releaseKryo(innerKryo);
            }

            outerKryo.writeClassAndObject(out, new ArrayList<>(Arrays.asList(new Date(4), new Date(5))));
            outerKryo.writeClassAndObject(out, Integer.valueOf(42));
         }

         outer = bout.toByteArray();
      }
      finally {
         XSwapUtil.releaseKryo(outerKryo);
      }

      // the outer stream must read back cleanly with a fresh Kryo
      Kryo reader = XSwapUtil.getKryo();

      try(Input in = new Input(new ByteArrayInputStream(outer))) {
         assertEquals("header", reader.readClassAndObject(in));
         assertEquals(Arrays.asList(new Date(4), new Date(5)), reader.readClassAndObject(in));
         assertEquals(Integer.valueOf(42), reader.readClassAndObject(in));
      }
      finally {
         XSwapUtil.releaseKryo(reader);
      }
   }

   private static byte[] write(Object obj) {
      Kryo kryo = XSwapUtil.getKryo();

      try {
         ByteArrayOutputStream bout = new ByteArrayOutputStream();

         try(Output out = new Output(bout)) {
            kryo.writeClassAndObject(out, obj);
         }

         return bout.toByteArray();
      }
      finally {
         XSwapUtil.releaseKryo(kryo);
      }
   }
}
