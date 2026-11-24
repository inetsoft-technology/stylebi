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
 * A single-use authorization binding an agent pairing code to an open sheet runtime.
 *
 * @param editorContext the script/formula location this session is scoped to, or {@code null}
 *                      for a whole-sheet ("Connect to Claude" toolbar) grant
 * @param runtimeKind   what the minting node saw the runtime to be (Redmine #80547), derived
 *                      server-side at mint and never from a client field; {@code null} when the
 *                      runtime could not be resolved there. Defence in depth: lets the join gate a
 *                      session whose runtime it cannot resolve itself.
 */
public record PairingGrant(String code, String runtimeId, String ownerIdentity,
                           String socketSessionId, String socketUserName,
                           long createdAt, long ttlMillis,
                           SheetType sheetType, EditorContext editorContext,
                           RuntimeKind runtimeKind) {
   /** What the runtime was when the code was minted. */
   public enum RuntimeKind { VIEWER, DESIGN }

   /** A grant that recorded nothing about its runtime (runtimeKind null). */
   public PairingGrant(String code, String runtimeId, String ownerIdentity,
                       String socketSessionId, String socketUserName,
                       long createdAt, long ttlMillis,
                       SheetType sheetType, EditorContext editorContext)
   {
      this(code, runtimeId, ownerIdentity, socketSessionId, socketUserName, createdAt, ttlMillis,
           sheetType, editorContext, null);
   }

   public boolean isExpired(long now) { return now - createdAt > ttlMillis; }
}
