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

/**
 * A reusable session opened after a successful pairing join.
 * Edits reuse this; the code stays single-use.
 */
public record JoinSession(String sessionToken, String runtimeId, String ownerIdentity,
                          SheetType sheetType, long lastAccess, long ttlMillis,
                          ConnectionMode connectionMode, String socketSessionId) {
   public boolean isExpired(long now) { return now - lastAccess > ttlMillis; }

   /** Forward-compat slot: PAIRED = browser owns + agent joins; AGENT_OWNED reserved for future viz. */
   public enum ConnectionMode { PAIRED }
}
