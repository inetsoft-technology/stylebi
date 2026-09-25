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
package inetsoft.util.script.graal.pool;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;

import java.util.ArrayList;

/**
 * A host copy of a worksheet script's array passed to a Java method (bug #76960, spec §14.12
 * A3). It is an ordinary List for Java, and reads back into a script as an array.
 */
public final class CopyList extends ArrayList<Object> implements ProxyArray {
   @Override
   public Object get(long index) {
      return get((int) index);
   }

   @Override
   public void set(long index, Value value) {
      int i = (int) index;

      while(size() <= i) {
         add(null);
      }

      set(i, WsValueCopier.interopElement(value));
   }

   @Override
   public long getSize() {
      return size();
   }

   @Override
   public boolean remove(long index) {
      remove((int) index);
      return true;
   }
}
