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

import com.fasterxml.jackson.annotation.*;

import java.util.Objects;

/**
 * {@code KeyValuePair} contains the key and value for an entry in a key-value store.
 *
 * @param <T> the type of value.
 */
public final class KeyValuePair<T> {
   /**
    * Creates a new instance of {@code KeyValuePair}.
    *
    * @param key   the key for the entry.
    * @param value the value of the entry.
    */
   @JsonCreator
   public KeyValuePair(@JsonProperty("key") String key, @JsonProperty("value") T value) {
      Objects.requireNonNull(key, "The key must not be null");
      Objects.requireNonNull(value, "The value must not be null");
      this.key = key;
      this.value = value;
   }

   /**
    * Gets the key for the entry.
    *
    * @return the key.
    */
   public String getKey() {
      return key;
   }

   /**
    * Gets the value of the entry.
    *
    * @return the value.
    */
   @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
   public T getValue() {
      return value;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      KeyValuePair<?> that = (KeyValuePair<?>) o;
      return key.equals(that.key) && value.equals(that.value);
   }

   @Override
   public int hashCode() {
      return Objects.hash(key, value);
   }

   @Override
   public String toString() {
      return "KeyValuePair{" +
         "key='" + key + '\'' +
         ", value=" + value +
         '}';
   }

   private final String key;
   private final T value;
}
