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
package inetsoft.web.wiz.pairing;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

@Service
public class SheetSessionService {
   public static final long TTL_MILLIS = 30 * 60_000L;
   private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

   private final ConcurrentHashMap<String, JoinSession> sessions;
   private final SecureRandom random = new SecureRandom();
   private final LongSupplier clock;

   public SheetSessionService() { this(System::currentTimeMillis); }

   SheetSessionService(LongSupplier clock) {
      this.clock = clock;
      this.sessions = new ConcurrentHashMap<>();
   }

   /** Test constructor: shares the sessions map of an existing service with a different clock. */
   SheetSessionService(LongSupplier clock, SheetSessionService source) {
      this.clock = clock;
      this.sessions = source.sessions;
   }

   public JoinSession open(String runtimeId, String ownerIdentity, SheetType sheetType) {
      String token = newToken();
      JoinSession s = new JoinSession(token, runtimeId, ownerIdentity, sheetType,
                                      clock.getAsLong(), TTL_MILLIS, JoinSession.ConnectionMode.PAIRED);
      sessions.put(token, s);
      return s;
   }

   /** Returns the session (TTL refreshed) iff present, unexpired, and owned by agentIdentity; else null. */
   public JoinSession resolve(String token, String agentIdentity) {
      if (token == null) return null;
      JoinSession s = sessions.get(token);
      if (s == null || s.isExpired(clock.getAsLong()) || !s.ownerIdentity().equals(agentIdentity)) return null;
      JoinSession refreshed = new JoinSession(s.sessionToken(), s.runtimeId(), s.ownerIdentity(),
                                              s.sheetType(), clock.getAsLong(), s.ttlMillis(), s.connectionMode());
      sessions.put(token, refreshed);
      return refreshed;
   }

   public void close(String token) { if (token != null) sessions.remove(token); }

   private String newToken() {
      StringBuilder sb = new StringBuilder(24);
      for (int i = 0; i < 24; i++) {
         sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
      }
      return sb.toString();
   }
}
