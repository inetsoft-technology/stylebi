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

import org.apache.ignite.cache.affinity.AffinityKeyMapped;

import java.io.Serializable;
import java.util.Objects;

/**
 * The key of a session attribute in the cache that holds the attributes of all sessions. The
 * session ID is the affinity key, so all the attributes of a session are in one partition
 * (Bug #78108).
 *
 * <p>Not {@code org.apache.ignite.cache.affinity.AffinityKey}: its {@code equals()} and
 * {@code hashCode()} ignore the affinity key, so the same attribute of two sessions would be
 * one key in a Java collection. Not a {@code record} either, which Ignite 2.x can't marshal.
 */
public final class SessionAttributeKey implements Serializable {
   public SessionAttributeKey(String sessionId, String key) {
      this.sessionId = sessionId;
      this.key = key;
   }

   public String getSessionId() {
      return sessionId;
   }

   public String getKey() {
      return key;
   }

   @Override
   public boolean equals(Object obj) {
      return obj instanceof SessionAttributeKey other &&
         Objects.equals(sessionId, other.sessionId) && Objects.equals(key, other.key);
   }

   @Override
   public int hashCode() {
      return Objects.hash(sessionId, key);
   }

   @Override
   public String toString() {
      return "SessionAttributeKey{sessionId=" + sessionId + ", key=" + key + "}";
   }

   @AffinityKeyMapped
   private final String sessionId;
   private final String key;
}
