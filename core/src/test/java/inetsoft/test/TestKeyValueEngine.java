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

package inetsoft.test;

import inetsoft.storage.KeyValueEngine;
import inetsoft.storage.KeyValuePair;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

public class TestKeyValueEngine implements KeyValueEngine {
   @Override
   public boolean contains(String id, String key) {
      return storage.computeIfAbsent(id, k -> new ConcurrentHashMap<>()).containsKey(key);
   }

   @SuppressWarnings("unchecked")
   @Override
   public <T> T get(String id, String key) {
      return (T) storage.computeIfAbsent(id, k -> new ConcurrentHashMap<>()).get(key);
   }

   @SuppressWarnings("unchecked")
   @Override
   public <T> T put(String id, String key, T value) {
      return (T) storage.computeIfAbsent(id, k -> new ConcurrentHashMap<>()).put(key, value);
   }

   @SuppressWarnings("unchecked")
   @Override
   public <T> T remove(String id, String key) {
      return (T) storage.computeIfAbsent(id, k -> new ConcurrentHashMap<>()).remove(key);
   }

   @Override
   public void deleteStorage(String id) {
      storage.remove(id);
   }

   @SuppressWarnings("unchecked")
   @Override
   public <T> Stream<KeyValuePair<T>> stream(String id) {
      return storage.computeIfAbsent(id, k -> new ConcurrentHashMap<>()).entrySet().stream()
         .map(e -> new KeyValuePair<>(e.getKey(), (T) e.getValue()));
   }

   @Override
   public Stream<String> idStream() {
      return storage.keySet().stream();
   }

   @Override
   public <T> void putAll(String id, Map<String, T> keyValueMap) {
      storage.computeIfAbsent(id, k -> new ConcurrentHashMap<>()).putAll(keyValueMap);
   }

   @Override
   public void close() throws Exception {
      storage.clear();
   }

   private final ConcurrentMap<String, ConcurrentMap<String, Object>> storage =
      new ConcurrentHashMap<>();
}
