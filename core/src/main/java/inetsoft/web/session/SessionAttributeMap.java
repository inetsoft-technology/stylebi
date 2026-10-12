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

package inetsoft.web.session;

import inetsoft.sree.internal.cluster.DistributedMap;

import java.util.*;
import java.util.stream.Collectors;

/**
 * The attributes of one HTTP session, as a view over the cache that holds the attributes of all
 * sessions. Each attribute is one entry, keyed by a {@link SessionAttributeKey}, so all the
 * entries of a session are in one partition and {@link #keySet()} reads only that partition.
 * The session's existence marker is hidden from the view (Bug #78108).
 */
final class SessionAttributeMap implements DistributedMap<String, Object> {
   SessionAttributeMap(DistributedMap<SessionAttributeKey, Object> attributes, String sessionId) {
      this.attributes = attributes;
      this.sessionId = sessionId;
   }

   static SessionAttributeKey getKey(String sessionId, String key) {
      return new SessionAttributeKey(sessionId, key);
   }

   private SessionAttributeKey key(Object key) {
      return getKey(sessionId, (String) key);
   }

   @Override
   public Object get(Object key) {
      return attributes.get(key(key));
   }

   @Override
   public Object getForUpdate(String key) {
      return attributes.getForUpdate(key(key));
   }

   @Override
   public Object put(String key, Object value) {
      return attributes.put(key(key), value);
   }

   @Override
   public void set(String key, Object value) {
      attributes.set(key(key), value);
   }

   @Override
   public void putAll(Map<? extends String, ?> m) {
      Map<SessionAttributeKey, Object> entries = new HashMap<>();
      m.forEach((k, v) -> entries.put(key(k), v));
      attributes.putAll(entries);
   }

   @Override
   public Object remove(Object key) {
      return attributes.remove(key(key));
   }

   @Override
   public boolean remove(Object key, Object value) {
      return attributes.remove(key(key), value);
   }

   @Override
   public Object putIfAbsent(String key, Object value) {
      return attributes.putIfAbsent(key(key), value);
   }

   @Override
   public boolean replace(String key, Object oldValue, Object newValue) {
      return attributes.replace(key(key), oldValue, newValue);
   }

   @Override
   public Object replace(String key, Object value) {
      return attributes.replace(key(key), value);
   }

   @Override
   public boolean containsKey(Object key) {
      return attributes.containsKey(key(key));
   }

   @Override
   public Set<String> keySet() {
      return attributes.keySetByAffinityKey(sessionId).stream()
         .map(SessionAttributeKey::getKey)
         .filter(key -> !IgniteSessionRepository.SESSION_CREATED_KEY.equals(key))
         .collect(Collectors.toSet());
   }

   @Override
   public Set<Entry<String, Object>> entrySet() {
      Set<Entry<String, Object>> entries = new HashSet<>();

      for(String key : keySet()) {
         Object value = get(key);

         if(value != null) {
            entries.add(new AbstractMap.SimpleEntry<>(key, value));
         }
      }

      return entries;
   }

   @Override
   public Collection<Object> values() {
      return entrySet().stream().map(Entry::getValue).collect(Collectors.toList());
   }

   @Override
   public boolean containsValue(Object value) {
      return values().contains(value);
   }

   @Override
   public int size() {
      return keySet().size();
   }

   @Override
   public boolean isEmpty() {
      return keySet().isEmpty();
   }

   @Override
   public void removeAll(Set<? extends String> keys) {
      attributes.removeAll(keys.stream().map(this::key).collect(Collectors.toSet()));
   }

   /**
    * Removes the attributes. The session itself still exists.
    */
   @Override
   public void removeAll() {
      removeAll(keySet());
   }

   @Override
   public void clear() {
      removeAll();
   }

   private final DistributedMap<SessionAttributeKey, Object> attributes;
   private final String sessionId;
}
