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

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Mints and validates single-use, short-TTL pairing codes binding an agent to an open runtime. */
@Service
public class SheetPairingService {
   public static final long TTL_MILLIS = 5 * 60_000L;
   public static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

   private final ConcurrentHashMap<String, PairingGrant> grants;
   private final SecureRandom random = new SecureRandom();
   private final LongSupplier clock;

   public SheetPairingService() { this(System::currentTimeMillis); }

   SheetPairingService(LongSupplier clock) {
      this.clock = clock;
      this.grants = new ConcurrentHashMap<>();
   }

   /** Test constructor: shares the grants map of an existing service with a different clock. */
   SheetPairingService(LongSupplier clock, SheetPairingService source) {
      this.clock = clock;
      this.grants = source.grants;
   }

   public String mint(String runtimeId, String ownerIdentity, String socketSessionId,
                      String socketUserName, SheetType sheetType)
   {
      String code = newCode();
      grants.put(code, new PairingGrant(code, runtimeId, ownerIdentity, socketSessionId,
                                        socketUserName, clock.getAsLong(), TTL_MILLIS, sheetType));
      return code;
   }

   /** Non-destructive lookup. Returns null if absent or expired. */
   public PairingGrant peek(String code) {
      PairingGrant g = grants.get(code);
      return (g == null || g.isExpired(clock.getAsLong())) ? null : g;
   }

   /** Single-use: removes and returns the grant, or null if absent/expired. */
   public PairingGrant consume(String code) {
      PairingGrant g = grants.remove(code);
      return (g == null || g.isExpired(clock.getAsLong())) ? null : g;
   }

   @Scheduled(fixedDelay = 10 * 60_000)
   void evictExpired() {
      long now = clock.getAsLong();
      grants.values().removeIf(g -> g.isExpired(now));
   }

   private String newCode() {
      StringBuilder sb = new StringBuilder(8);
      for (int i = 0; i < 8; i++) {
         sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
      }
      return sb.toString();
   }
}
