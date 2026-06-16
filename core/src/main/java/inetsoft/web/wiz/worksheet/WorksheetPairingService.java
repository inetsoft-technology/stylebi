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
package inetsoft.web.wiz.worksheet;

import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Mints and validates single-use, short-TTL pairing codes binding an agent to an open runtime. */
@Service
public class WorksheetPairingService {
   public static final long TTL_MILLIS = 5 * 60_000L;   // 5 minutes
   private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

   private final ConcurrentHashMap<String, PairingGrant> grants = new ConcurrentHashMap<>();
   private final SecureRandom random = new SecureRandom();
   private final LongSupplier clock;

   public WorksheetPairingService() { this(System::currentTimeMillis); }
   WorksheetPairingService(LongSupplier clock) { this.clock = clock; }

   public String mint(String runtimeId, String ownerIdentity, String socketSessionId) {
      String code = newCode();
      grants.put(code, new PairingGrant(code, runtimeId, ownerIdentity, socketSessionId,
                                        clock.getAsLong(), TTL_MILLIS));
      return code;
   }

   /** Non-destructive lookup. Returns null if absent or expired. */
   public PairingGrant peek(String code) {
      if(code == null) {
         return null;
      }

      PairingGrant g = grants.get(code);
      return (g == null || g.isExpired(clock.getAsLong())) ? null : g;
   }

   /** Single-use: removes and returns the grant, or null if absent/expired. */
   public PairingGrant consume(String code) {
      if(code == null) {
         return null;
      }

      PairingGrant g = grants.remove(code);
      return (g == null || g.isExpired(clock.getAsLong())) ? null : g;
   }

   private String newCode() {
      StringBuilder sb = new StringBuilder(8);
      for(int i = 0; i < 8; i++) sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
      return sb.toString();
   }
}
