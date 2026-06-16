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
package inetsoft.web.wiz.worksheet;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.web.wiz.pairing.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;

/**
 * Session-resolved edit service for worksheets.
 *
 * <p>Resolves a {@link JoinSession} from a session token, fetches the corresponding
 * {@link RuntimeWorksheet}, applies a caller-supplied mutation via an {@link Editor},
 * then broadcasts a refresh to the owning browser session.</p>
 */
@Service
public class WorksheetEditService {

   @Autowired
   public WorksheetEditService(SheetSessionService sessions,
                               SheetRuntimeAccess runtimeAccess,
                               SheetAgentBroadcastService broadcast)
   {
      this.sessions = sessions;
      this.runtimeAccess = runtimeAccess;
      this.broadcast = broadcast;
   }

   /**
    * Resolve a session, fetch the runtime worksheet, apply the mutation, and broadcast a refresh.
    *
    * @param sessionToken the session token obtained at join time
    * @param agent        the agent's principal
    * @param mutation     the mutation to apply via the {@link Editor}
    * @throws PairingException if the session is invalid/expired or the runtime is not found
    */
   public void apply(String sessionToken, Principal agent,
                     ThrowingConsumer<Editor> mutation)
      throws PairingException
   {
      String agentKey = agentKey(agent);
      JoinSession session = sessions.resolve(sessionToken, agentKey);

      if(session == null) {
         throw new PairingException("Invalid or expired session: " + sessionToken);
      }

      RuntimeWorksheet rws = (RuntimeWorksheet) runtimeAccess.getSheetForPairing(
         SheetType.WORKSHEET, session.runtimeId(), agent);

      Editor editor = new Editor(rws.getWorksheet());
      mutation.accept(editor);

      broadcast.broadcastRefresh(rws, SheetType.WORKSHEET, session.runtimeId(), agent);
   }

   /**
    * Resolve a session and fetch the runtime worksheet without applying any mutation.
    * Useful for read operations that need a live runtime.
    *
    * @param sessionToken the session token obtained at join time
    * @param agent        the agent's principal
    * @return the live {@link RuntimeWorksheet}
    * @throws PairingException if the session is invalid/expired or the runtime is not found
    */
   public RuntimeWorksheet resolve(String sessionToken, Principal agent) throws PairingException {
      String agentKey = agentKey(agent);
      JoinSession session = sessions.resolve(sessionToken, agentKey);

      if(session == null) {
         throw new PairingException("Invalid or expired session: " + sessionToken);
      }

      return (RuntimeWorksheet) runtimeAccess.getSheetForPairing(
         SheetType.WORKSHEET, session.runtimeId(), agent);
   }

   // -------------------------------------------------------------------------
   // Identity key helpers
   // -------------------------------------------------------------------------

   private String agentKey(Principal agent) {
      if(agent instanceof XPrincipal p) {
         IdentityID id = IdentityID.getIdentityIDFromKey(p.getName());
         return id != null ? id.convertToKey() : p.getName();
      }

      return agent.getName();
   }

   // -------------------------------------------------------------------------
   // Dependencies
   // -------------------------------------------------------------------------

   private final SheetSessionService sessions;
   private final SheetRuntimeAccess runtimeAccess;
   private final SheetAgentBroadcastService broadcast;

   // =========================================================================
   // Inner class: Editor
   // =========================================================================

   /**
    * Applies column mutations to an in-memory {@link Worksheet}.
    *
    * <p>An {@code Editor} instance is created per {@link #apply} call and
    * operates on the live worksheet object held by the {@link RuntimeWorksheet}.</p>
    */
   public static final class Editor {

      Editor(Worksheet ws) {
         this.ws = ws;
      }

      /**
       * Removes the named column from the table's public {@link ColumnSelection}.
       * No-ops if the column does not exist.
       *
       * @param table the assembly name
       * @param col   the column attribute name to remove
       * @throws PairingException if no {@link TableAssembly} with {@code table} exists
       */
      public void removeColumn(String table, String col) throws PairingException {
         TableAssembly t = requireTable(table);
         ColumnSelection cs = t.getColumnSelection(false);
         DataRef toRemove = cs.getAttribute(col);

         if(toRemove != null) {
            cs.removeAttribute(toRemove);
            t.setColumnSelection(cs, false);
         }
      }

      /**
       * Adds a new column to the table's public {@link ColumnSelection}.
       *
       * @param table the assembly name
       * @param name  the new column's attribute name
       * @param type  the data type string (e.g. {@code "string"}, {@code "integer"}), or {@code null}
       * @throws PairingException if no {@link TableAssembly} with {@code table} exists
       */
      public void addColumn(String table, String name, String type) throws PairingException {
         TableAssembly t = requireTable(table);
         ColumnSelection cs = t.getColumnSelection(false);
         AttributeRef attr = new AttributeRef(null, name);
         ColumnRef ref = new ColumnRef(attr);

         if(type != null) {
            ref.setDataType(type);
         }

         cs.addAttribute(ref);
         t.setColumnSelection(cs, false);
      }

      /**
       * Sets the alias of an existing column, effectively renaming it in the output.
       * No-ops if the column does not exist or is not a {@link ColumnRef}.
       *
       * @param table   the assembly name
       * @param col     the column attribute name to rename
       * @param newName the new alias
       * @throws PairingException if no {@link TableAssembly} with {@code table} exists
       */
      public void renameColumn(String table, String col, String newName) throws PairingException {
         TableAssembly t = requireTable(table);
         ColumnSelection cs = t.getColumnSelection(false);
         DataRef existing = cs.getAttribute(col);

         if(existing instanceof ColumnRef cr) {
            cr.setAlias(newName);
         }
      }

      // -----------------------------------------------------------------------
      // Helper
      // -----------------------------------------------------------------------

      private TableAssembly requireTable(String name) throws PairingException {
         Assembly a = ws.getAssembly(name);

         if(!(a instanceof TableAssembly t)) {
            throw new PairingException("Table not found in worksheet: " + name);
         }

         return t;
      }

      private final Worksheet ws;
   }
}
