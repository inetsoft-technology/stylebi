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
import java.util.List;
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

   public ScriptEditService(SheetSessionService sessions,
                            SheetRuntimeAccess runtimeAccess,
                            SheetAgentBroadcastService broadcast)
   {
      this(sessions, runtimeAccess, broadcast, new CalcFieldService());
   }

   @Autowired
   public ScriptEditService(SheetSessionService sessions,
                            SheetRuntimeAccess runtimeAccess,
                            SheetAgentBroadcastService broadcast,
                            CalcFieldService calcFields)
   {
      this.sessions = sessions;
      this.runtimeAccess = runtimeAccess;
      this.broadcast = broadcast;
      this.calcFields = calcFields;
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
    * mutation was actually attempted against the live runtime (the predicate must not be derived
    * from a "changed" list: a partially-applied mutation can leave that list empty).
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

      Viewsheet vs = rvs.getViewsheet();

      if(vs != null) {
         rvs.addCheckpoint(vs.prepareCheckpoint());
      }

      // Write coordination: this is a direct live write, not routed through any
      // XxxPropertyDialogService, so it must bump the shared counter itself -- otherwise a
      // property dialog with an embedded script pane (Gauge, Chart, ... -- 15 of the 18
      // registered types) would read this write's revision as unchanged and silently clobber
      // it on its own later commit. See 2026-08-17-write-coordination-design.md /
      // -implementation.md.
      rvs.bumpWriteRevision();

      broadcast.broadcastRefresh(rvs, SheetType.VIEWSHEET, session.runtimeId(), agent);
   }

   /** Sets the raw script text at {@code target}. */
   public void write(RuntimeViewsheet rvs, Principal agent, ScriptTarget target, String text)
      throws PairingException
   {
      Viewsheet vs = requireViewsheet(rvs);

      switch(target.location()) {
         case VS_INIT -> vs.getViewsheetInfo().setOnInit(text);
         case VS_LOAD -> vs.getViewsheetInfo().setOnLoad(text);
         case ASSEMBLY -> ScriptReadService.requireAssemblyInfo(vs, target.assemblyName()).setScript(text);
         case ASSEMBLY_ONCLICK -> {
            VSAssemblyInfo info = ScriptReadService.requireAssemblyInfo(vs, target.assemblyName());
            ScriptReadService.setOnClick(info, target.assemblyName(), text);
         }
         case CALC_FIELD -> calcFields.write(rvs, target.assemblyName(), target.name(), text);
         // This is a switch STATEMENT, not an expression -- with no `default`, a Location this
         // switch does not name would silently fall through and do NOTHING (the dangerous half
         // of the trap this package has hit before: a no-op that reports success). A worksheet
         // expression/condition column has no RuntimeViewsheet representation to write to at
         // all -- it is written via WorksheetScriptService, routed onto
         // WorksheetAgentController's edit_expression/edit_condition ops -- so this must fail
         // loud if ever misrouted here, not do nothing.
         case WORKSHEET_EXPRESSION, WORKSHEET_CONDITION, WORKSHEET_CONDITION_VALUE -> throw new PairingException(
            "'" + target.kind().wireName() + "' is a worksheet-level target; it cannot be " +
            "written through the viewsheet script API. Use WorksheetScriptService (worksheet-chat's " +
            "edit_expression/edit_condition) instead.");
      }

      // Bug #80447 symptom 1: the canvas-repaint broadcast apply() already issues (per-assembly
      // RefreshVSObjectCommand + undo state) never reaches an already-open ViewsheetScriptPane/
      // FormulaEditorDialog -- neither component subscribes to any command, so a script an agent
      // just wrote stayed invisible in a dialog the human already had open until they closed and
      // reopened it. This is the only place every write this method performs funnels through, so
      // it is the one place that can push the new text to that specific open dialog.
      broadcast.broadcastScriptRefresh(rvs, rvs.getID(), agent, target.kind().wireName(),
                                       target.assemblyName(), target.name(), text);
   }

   /**
    * A target whose enabled state is governed by the same flag as the one just written.
    * Derived from the written location alone, never from a grant-scoped listing, so a pane
    * session (which cannot list the sibling) can still be told it flipped.
    */
   public record SharedFlagTarget(String id, String kind, String assembly, boolean enabled) {}

   /**
    * The other targets that share {@code target}'s enable flag: onInit and onLoad share the
    * viewsheet flag; an assembly's main and onClick scripts share that assembly's flag (onClick
    * only where the assembly supports it).
    */
   public List<SharedFlagTarget> flagSiblings(RuntimeViewsheet rvs, ScriptTarget target, boolean enabled)
      throws PairingException
   {
      ScriptTarget.Kind sibling;

      switch(target.location()) {
         case VS_INIT -> sibling = ScriptTarget.Kind.VIEWSHEET_ON_LOAD;
         case VS_LOAD -> sibling = ScriptTarget.Kind.VIEWSHEET_ON_INIT;
         case ASSEMBLY -> {
            VSAssemblyInfo info = ScriptReadService.requireAssemblyInfo(
               requireViewsheet(rvs), target.assemblyName());

            if(!ScriptReadService.supportsOnClick(info)) {
               return List.of();
            }

            sibling = ScriptTarget.Kind.ASSEMBLY_ON_CLICK;
         }
         case ASSEMBLY_ONCLICK -> sibling = ScriptTarget.Kind.ASSEMBLY_MAIN;
         default -> {
            return List.of();
         }
      }

      boolean viewsheetLevel = sibling == ScriptTarget.Kind.VIEWSHEET_ON_LOAD ||
         sibling == ScriptTarget.Kind.VIEWSHEET_ON_INIT;
      ScriptTarget other = ScriptTarget.of(sibling, viewsheetLevel ? null : target.assemblyName());
      return List.of(new SharedFlagTarget(
         other.id(), sibling.wireName(), viewsheetLevel ? null : target.assemblyName(), enabled));
   }

   /** Toggles whether the script at {@code target} is enabled. */
   public void setEnabled(RuntimeViewsheet rvs, ScriptTarget target, boolean enabled) throws PairingException {
      Viewsheet vs = requireViewsheet(rvs);

      switch(target.location()) {
         case VS_INIT, VS_LOAD -> vs.getViewsheetInfo().setScriptEnabled(enabled);
         case ASSEMBLY ->
            ScriptReadService.requireAssemblyInfo(vs, target.assemblyName()).setScriptEnabled(enabled);
         case ASSEMBLY_ONCLICK -> {
            VSAssemblyInfo info = ScriptReadService.requireAssemblyInfo(vs, target.assemblyName());
            ScriptReadService.requireOnClickSupport(info, target.assemblyName());
            info.setScriptEnabled(enabled);
         }
         // Permanent, unlike write's refusal above: a calculated field has no per-field enable
         // flag at all, so there is nothing for Task 3 (or anyone) to wire in here later.
         case CALC_FIELD -> throw new PairingException(
            "A calculated field has no enable flag; only scripts can be enabled or disabled.");
         // Same silent-no-op hazard as write() above -- this is a switch STATEMENT with no
         // default. A worksheet expression/condition column has no enable flag either, and has
         // no RuntimeViewsheet representation regardless -- fail loud rather than doing nothing.
         case WORKSHEET_EXPRESSION, WORKSHEET_CONDITION, WORKSHEET_CONDITION_VALUE -> throw new PairingException(
            "'" + target.kind().wireName() + "' is a worksheet-level target with no enable flag; " +
            "it cannot be toggled through the viewsheet script API.");
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

   /**
    * Fills a null socket session only -- deliberately not unconditional. See
    * {@code ViewsheetSessionService.applySocketSession}'s doc comment: {@code SheetJoinService
    * .join} is the one place that may apply a grant's socket values unconditionally, exactly
    * once per pairing, because that is the one moment they are provably fresh. Reapplying this
    * session's own frozen value on every later call would silently undo a human's own later
    * recovery (a manual Refresh unconditionally re-stamps the runtime from a live dispatcher).
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
   private final CalcFieldService calcFields;
}
