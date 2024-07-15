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
package inetsoft.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/**
 * Class with array functionality with a much smaller memory and serialization footprint when the entries are sparse
 * compared with the array length. Iterators use index natural ordering.
 */
@JsonSerialize(using = SparseArray.Serializer.class)
public class SparseArray<T> implements Iterable<T> {
   /**
    * @param index non-negative integer array index
    * @param elem  non-null element to insert
    */
   public void set(int index, T elem) {
      if(index < 0) {
         throw new ArrayIndexOutOfBoundsException(index);
      }

      if(elem == null) {
         return;
      }

      map.put(index, elem);
   }

   /**
    * @param index the non-negative index location at which to retrieve the element.
    * @return the associated element if one exists, null otherwise.
    */
   public T get(int index) {
      if(index < 0) {
         throw new ArrayIndexOutOfBoundsException(index);
      }

      return map.get(index);
   }

   @Override
   public Iterator<T> iterator() {
      return map.values().iterator();
   }

   @Override
   public void forEach(Consumer<? super T> action) {
      map.values().forEach(action);
   }

   @Override
   public Spliterator<T> spliterator() {
      return map.values().spliterator();
   }

   private final TreeMap<Integer, T> map = new TreeMap<>();

   /**
    * Serializes the SparseArray as a map with integer strings as the keys.
    */
   static final class Serializer extends JsonSerializer<SparseArray<?>> {
      @Override
      public void serialize(SparseArray value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
         gen.writeObject(value.map);
      }
   }
}
