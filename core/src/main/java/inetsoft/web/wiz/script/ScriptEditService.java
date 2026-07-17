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
package inetsoft.web.wiz.script;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.wiz.pairing.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.function.Predicate;

/**
 * Session-resolved edit service for viewsheet scripts.
 *
 * <p>Resolves a {@link JoinSession} from a session token, fetches the corresponding
 * {@link RuntimeViewsheet}, applies a caller-supplied mutation, then broadcasts a refresh
 * to the owning browser session — mirrors {@code WorksheetEditService}'s shape.</p>
 */
@Service
public class ScriptEditService {

   @Autowired
   public ScriptEditService(SheetSessionService sessions,
                            SheetRuntimeAccess runtimeAccess,
                            SheetAgentBroadcastService broadcast)
   {
      this.sessions = sessions;
      this.runtimeAccess = runtimeAccess;
      this.broadcast = broadcast;
   }

   /**
    * Resolve a session and fetch the runtime viewsheet without applying any mutation.
    * Used for read operations that need a live runtime.
    */
   public RuntimeViewsheet resolve(String sessionToken, Principal agent) throws PairingException {
      JoinSession session = requireSession(sessionToken, agent);
      RuntimeViewsheet rvs = (RuntimeViewsheet) runtimeAccess.getSheetForPairing(
         SheetType.VIEWSHEET, session.runtimeId(), agent);
      applySocketSession(rvs, session);
      return rvs;
   }

   @FunctionalInterface
   public interface ThrowingConsumer<T> {
      void accept(T t) throws Exception;
   }

   @FunctionalInterface
   public interface ThrowingFunction<A, R> {
      R apply(A a) throws Exception;
   }

   /**
    * Like {@link #apply} but returns the mutation's result (e.g. an execution result) instead
    * of {@code void}. Broadcasts unconditionally after the mutation runs, mirroring
    * {@code WorksheetEditService#applyOnRuntime}.
    */
   public <T> T applyOnRuntime(String sessionToken, Principal agent,
                               ThrowingFunction<RuntimeViewsheet, T> mutation)
      throws Exception
   {
      JoinSession session = requireSession(sessionToken, agent);
      RuntimeViewsheet rvs = (RuntimeViewsheet) runtimeAccess.getSheetForPairing(
         SheetType.VIEWSHEET, session.runtimeId(), agent);
      applySocketSession(rvs, session);

      T result = mutation.apply(rvs);

      broadcast.broadcastRefresh(rvs, SheetType.VIEWSHEET, session.runtimeId(), agent);
      return result;
   }

   /**
    * Like {@link #applyOnRuntime} but only broadcasts when {@code shouldBroadcast} says the
    * mutation actually changed something.
    *
    * <p>Exists because {@code execute} (dry-run) has no isolated clone to run against (see
    * {@link ScriptExecuteService}'s class javadoc) — a script that isn't caught by the
    * destructive-globals check mutates the SAME live runtime {@code execute-live} does. Without
    * this, that mutation would take effect with no broadcast at all, silently diverging the
    * live runtime from what the owning browser session displays.</p>
    */
   public <T> T applyOnRuntimeIfChanged(String sessionToken, Principal agent,
                                        ThrowingFunction<RuntimeViewsheet, T> mutation,
                                        Predicate<T> shouldBroadcast)
      throws Exception
   {
      JoinSession session = requireSession(sessionToken, agent);
      RuntimeViewsheet rvs = (RuntimeViewsheet) runtimeAccess.getSheetForPairing(
         SheetType.VIEWSHEET, session.runtimeId(), agent);
      applySocketSession(rvs, session);

      T result = mutation.apply(rvs);

      if(shouldBroadcast.test(result)) {
         broadcast.broadcastRefresh(rvs, SheetType.VIEWSHEET, session.runtimeId(), agent);
      }

      return result;
   }

   /**
    * Resolve a session, fetch the runtime viewsheet, apply the mutation, then broadcast
    * a refresh to the owning browser session.
    */
   public void apply(String sessionToken, Principal agent, ThrowingConsumer<RuntimeViewsheet> mutation)
      throws Exception
   {
      JoinSession session = requireSession(sessionToken, agent);
      RuntimeViewsheet rvs = (RuntimeViewsheet) runtimeAccess.getSheetForPairing(
         SheetType.VIEWSHEET, session.runtimeId(), agent);
      applySocketSession(rvs, session);

      mutation.accept(rvs);

      broadcast.broadcastRefresh(rvs, SheetType.VIEWSHEET, session.runtimeId(), agent);
   }

   /** Sets the raw script text at {@code target}. */
   public void write(RuntimeViewsheet rvs, ScriptTarget target, String text) throws PairingException {
      Viewsheet vs = requireViewsheet(rvs);

      switch(target.location()) {
         case VS_INIT -> vs.getViewsheetInfo().setOnInit(text);
         case VS_LOAD -> vs.getViewsheetInfo().setOnLoad(text);
         case ASSEMBLY -> ScriptReadService.requireAssemblyInfo(vs, target.assemblyName()).setScript(text);
         case ASSEMBLY_ONCLICK -> {
            VSAssemblyInfo info = ScriptReadService.requireAssemblyInfo(vs, target.assemblyName());
            ScriptReadService.setOnClick(info, text);
         }
      }
   }

   /** Toggles whether the script at {@code target} is enabled. */
   public void setEnabled(RuntimeViewsheet rvs, ScriptTarget target, boolean enabled) throws PairingException {
      Viewsheet vs = requireViewsheet(rvs);

      switch(target.location()) {
         case VS_INIT, VS_LOAD -> vs.getViewsheetInfo().setScriptEnabled(enabled);
         case ASSEMBLY, ASSEMBLY_ONCLICK ->
            ScriptReadService.requireAssemblyInfo(vs, target.assemblyName()).setScriptEnabled(enabled);
      }
   }

   private JoinSession requireSession(String sessionToken, Principal agent) throws PairingException {
      String agentKey = agentKey(agent);
      JoinSession session = sessions.resolve(sessionToken, agentKey);

      if(session == null) {
         throw new PairingException(
            PairingException.Kind.SESSION_EXPIRED, "Invalid or expired session: " + sessionToken);
      }

      return session;
   }

   private Viewsheet requireViewsheet(RuntimeViewsheet rvs) throws PairingException {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         throw new PairingException("Viewsheet not found in runtime");
      }

      return vs;
   }

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
