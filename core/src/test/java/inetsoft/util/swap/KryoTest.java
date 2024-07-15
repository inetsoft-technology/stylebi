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

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class KryoTest {
   @Test
   public void kryoSerialization() {
      final Kryo kryo = new Kryo();
      final Output output = new Output(512);
      final String t1 = "test string 1";
      final String t2 = "test string 2";
      final String t3 = "test string 3";
      kryo.writeObject(output, t1);
      kryo.writeObject(output, t2);
      kryo.writeObject(output, t3);
      output.flush();
      final Input input = new Input(output.toBytes());
      final String e1 = kryo.readObject(input, String.class);
      final String e2 = kryo.readObject(input, String.class);
      final String e3 = kryo.readObject(input, String.class);
      input.close();

      Assertions.assertEquals(t1, e1);
      Assertions.assertEquals(t2, e2);
      Assertions.assertEquals(t3, e3);
   }
}
