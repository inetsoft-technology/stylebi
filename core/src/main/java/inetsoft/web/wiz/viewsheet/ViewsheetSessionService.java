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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import inetsoft.web.wiz.pairing.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;

/**
 * Resolves a pairing session to a live viewsheet runtime and runs mutations against it.
 *
 * <p>Mirrors {@code ScriptEditService.resolve}, including the socket-session plumbing that
 * makes broadcasts reach the browser.
 *
 * <p>Mutations run under a {@link CapturingCommandDispatcher} rather than a dummy one:
 * composer services report failure by dispatching a {@code MessageCommand} of
 * {@code Type.ERROR} and returning normally, so a dispatcher that drops commands turns every
 * such failure into a silent success.
 */
@Service
public class ViewsheetSessionService {
   @Autowired
   public ViewsheetSessionService(SheetSessionService sessions,
                                  SheetRuntimeAccess runtimeAccess,
                                  SheetAgentBroadcastService broadcast)
   {
      this.sessions = sessions;
      this.runtimeAccess = runtimeAccess;
      this.broadcast = broadcast;
   }

   public JoinSession requireSession(String sessionToken, Principal agent) throws PairingException {
      JoinSession session = sessions.resolve(sessionToken, agentKey(agent));

      if(session == null) {
         throw new PairingException(
            PairingException.Kind.SESSION_EXPIRED,
            "Invalid or expired viewsheet session: " + sessionToken +
            ". Ask the user for a fresh pairing code and run connect_viewsheet again.");
      }

      return session;
   }

   public RuntimeViewsheet resolve(String sessionToken, Principal agent) throws PairingException {
      JoinSession session = requireSession(sessionToken, agent);
      RuntimeViewsheet rvs = (RuntimeViewsheet) runtimeAccess.getSheetForPairing(
         SheetType.VIEWSHEET, session.runtimeId(), agent);
      applySocketSession(rvs, session);
      return rvs;
   }

   public String runtimeId(String sessionToken, Principal agent) throws PairingException {
      return requireSession(sessionToken, agent).runtimeId();
   }

   /**
    * Resolve, run the mutation under a capturing dispatcher, checkpoint, broadcast.
    *
    * <p>One checkpoint per call, matching what a single Composer dialog OK does, so the
    * human's Ctrl+Z steps back through agent edits one at a time.
    */
   public void mutate(String sessionToken, Principal agent, Mutation mutation) throws Exception {
      JoinSession session = requireSession(sessionToken, agent);
      RuntimeViewsheet rvs = (RuntimeViewsheet) runtimeAccess.getSheetForPairing(
         SheetType.VIEWSHEET, session.runtimeId(), agent);
      applySocketSession(rvs, session);

      CapturingCommandDispatcher.withCapturingDispatcher(agent, dispatcher -> {
         mutation.run(rvs, session.runtimeId(), dispatcher);
         return null;
      });

      Viewsheet vs = rvs.getViewsheet();

      if(vs != null) {
         rvs.addCheckpoint(vs.prepareCheckpoint());
      }

      broadcast.broadcastRefresh(rvs, SheetType.VIEWSHEET, session.runtimeId(), agent);
   }

   /**
    * Resolve and run a <b>read</b> under a capturing dispatcher — no checkpoint, no broadcast.
    *
    * <p>Some composer services return their result by <em>dispatching a command</em> rather than
    * returning a value: {@code VSTableLayoutService.getCellScript} and {@code getNamedGroup} both
    * do. Reading those needs the capturing dispatcher, which {@link #resolve} does not supply.
    *
    * <p>{@link #mutate} would supply one, but it also opens a checkpoint and broadcasts — so
    * using it for a read would put a stray Ctrl+Z step in the user's history for having
    * <em>looked</em> at something, and tell their Composer to refresh for a change that never
    * happened. Hence a third entry point rather than a flag on the second.
    */
   public <T> T read(String sessionToken, Principal agent, Read<T> read) throws Exception {
      JoinSession session = requireSession(sessionToken, agent);
      RuntimeViewsheet rvs = (RuntimeViewsheet) runtimeAccess.getSheetForPairing(
         SheetType.VIEWSHEET, session.runtimeId(), agent);
      applySocketSession(rvs, session);

      return CapturingCommandDispatcher.withCapturingDispatcher(
         agent, dispatcher -> read.run(rvs, session.runtimeId(), dispatcher));
   }

   @FunctionalInterface
   public interface Mutation {
      void run(RuntimeViewsheet rvs, String runtimeId, CapturingCommandDispatcher dispatcher)
         throws Exception;
   }

   /** A read that needs the capturing dispatcher to see a command-delivered result. */
   @FunctionalInterface
   public interface Read<T> {
      T run(RuntimeViewsheet rvs, String runtimeId, CapturingCommandDispatcher dispatcher)
         throws Exception;
   }

   /**
    * Carry the browser's socket session onto the runtime. Without this
    * {@code SheetAgentBroadcastService} finds no socket session and skips the broadcast, so
    * the human's Composer never reflects the agent's edits.
    */
   private void applySocketSession(RuntimeViewsheet rvs, JoinSession session) {
      if(session.socketSessionId() != null && rvs.getSocketSessionId() == null) {
         rvs.setSocketSessionId(session.socketSessionId());
      }

      if(rvs.getSocketUserName() == null && session.socketUserName() != null) {
         rvs.setSocketUserName(session.socketUserName());
      }
   }

   private String agentKey(Principal agent) {
      if(agent instanceof XPrincipal p) {
         IdentityID id = IdentityID.getIdentityIDFromKey(p.getName());
         return id != null ? id.convertToKey() : p.getName();
      }

      return agent != null ? agent.getName() : null;
   }

   private final SheetSessionService sessions;
   private final SheetRuntimeAccess runtimeAccess;
   private final SheetAgentBroadcastService broadcast;
}
