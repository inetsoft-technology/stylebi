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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.report.composition.RuntimeSheet;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.WSAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.composer.ws.assembly.WSAssemblyModel;
import inetsoft.web.composer.ws.assembly.WSAssemblyModelFactory;
import inetsoft.web.composer.ws.command.RefreshWorksheetCommand;
import inetsoft.web.composer.ws.command.SetAgentActiveCommand;
import inetsoft.web.composer.ws.command.SetWorksheetInfoCommand;
import inetsoft.web.viewsheet.command.RefreshVSObjectCommand;
import inetsoft.web.viewsheet.command.SaveSheetCommand;
import inetsoft.web.viewsheet.command.UpdateUndoStateCommand;
import inetsoft.web.viewsheet.model.VSObjectModel;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CommandDispatcherService;
import inetsoft.web.viewsheet.service.ComposerClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@Service
public class SheetAgentBroadcastService {
   private static final Logger LOG = LoggerFactory.getLogger(SheetAgentBroadcastService.class);

   /**
    * Where a paired browser learns that an agent joined. The client subscribes to this prefixed
    * with {@code /user}, exactly as it does for mint's {@code @SendToUser} destination.
    *
    * <p>Deliberately neither {@code CommandDispatcher.COMMANDS_TOPIC} nor
    * {@code ComposerClientService.COMMANDS_TOPIC} — see {@link #sendToComposer}'s javadoc for why
    * confusing those two is a silent failure. This is a pairing-domain destination with its own
    * client handler.
    */
   static final String PAIRING_JOINED_TOPIC = "/commands/wiz/pairing/joined";

   @Autowired
   public SheetAgentBroadcastService(CommandDispatcherService commandDispatcherService,
                                     VSObjectModelFactoryService vsObjectModelFactoryService)
   {
      this.commandDispatcherService = commandDispatcherService;
      this.vsObjectModelFactoryService = vsObjectModelFactoryService;
   }

   /**
    * Push a refresh to the browser that owns this runtime so its open composer re-renders.
    * Omits inetsoftClientId intentionally — Angular's broadcast-accept clause re-renders
    * when inetsoftClientId is absent.
    *
    * @param rs        the runtime sheet (supplies socketSessionId + socketUserName)
    * @param sheetType which refresh command to send
    * @param runtimeId the sheet runtime ID (used as the RUNTIME_ID_ATTR header value)
    * @param owner     the agent principal (used as a fallback user name if socketUserName is null)
    */
   public void broadcastRefresh(RuntimeSheet rs, SheetType sheetType, String runtimeId,
                                Principal owner)
   {
      String sessionId = rs.getSocketSessionId();

      if(sessionId == null) {
         LOG.warn("Pairing broadcast skipped — no socket session recorded (runtimeId={}, runtimeClass={})",
                  runtimeId, rs.getClass().getSimpleName());
         return;
      }

      String user = rs.getSocketUserName();

      if(user == null) {
         user = owner == null ? null : owner.getName();
      }

      if(sheetType == SheetType.VIEWSHEET) {
         broadcastViewsheetRefresh((RuntimeViewsheet) rs, runtimeId, sessionId, user);
         sendUndoState(rs, runtimeId, sessionId, user);
         return;
      }

      Object command = buildCommand(sheetType, rs, owner);
      sendCommand(user, sessionId, runtimeId, command);
      sendUndoState(rs, runtimeId, sessionId, user);

      LOG.info("Pairing broadcast refresh sent (sheetType={}, runtimeId={}, sessionId={})",
               sheetType, runtimeId, sessionId);
   }

   /**
    * Refresh every visible top-level assembly on the browser's open viewsheet.
    *
    * <p>Unlike the worksheet path (a single {@code RefreshWorksheetCommand} carrying a list
    * of assembly models), the viewsheet client expects one {@code RefreshVSObjectCommand}
    * per assembly — there is no single-shot "reload everything" command that doesn't also
    * re-run onInit/onLoad (which would double-execute a script the agent just ran live).</p>
    */
   private void broadcastViewsheetRefresh(RuntimeViewsheet rvs, String runtimeId,
                                          String sessionId, String user)
   {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return;
      }

      int sent = 0;

      for(Assembly a : vs.getAssemblies()) {
         if(!(a instanceof VSAssembly vsAssembly) || !vsAssembly.isVisible()) {
            continue;
         }

         VSObjectModel<?> model;

         try {
            model = vsObjectModelFactoryService.createModel(vsAssembly, rvs);
         }
         catch(Exception e) {
            LOG.warn("Failed to build object model for broadcast (assembly={})", a.getName(), e);
            continue;
         }

         if(model == null) {
            continue;
         }

         RefreshVSObjectCommand command = new RefreshVSObjectCommand();
         command.setInfo(model);
         sendCommand(user, sessionId, runtimeId, command);
         sent++;
      }

      LOG.info("Pairing broadcast viewsheet refresh sent (runtimeId={}, sessionId={}, assemblies={})",
               runtimeId, sessionId, sent);
   }

   /**
    * Push a SetWorksheetInfoCommand (tab label) + SaveSheetCommand to the browser
    * so the tab title, id, and save point update — mirrors the regular save flow
    * in SaveWorksheetDialogService.
    */
   public void broadcastSave(RuntimeSheet rs, String runtimeId, Principal owner) {
      String sessionId = rs.getSocketSessionId();

      if(sessionId == null) {
         return;
      }

      String user = rs.getSocketUserName();

      if(user == null) {
         user = owner == null ? null : owner.getName();
      }

      // 1. Send SetWorksheetInfoCommand to update the tab label
      SetWorksheetInfoCommand labelCommand = SetWorksheetInfoCommand.builder()
         .label(rs.getEntry().toView())
         .build();

      sendCommand(user, sessionId, runtimeId, labelCommand);

      // 2. Send SaveSheetCommand to update savePoint, id, and newSheet flag
      SaveSheetCommand saveCommand = SaveSheetCommand.builder()
         .savePoint(rs.getSavePoint())
         .id(rs.getEntry().toIdentifier())
         .build();

      sendCommand(user, sessionId, runtimeId, saveCommand);

      // 3. Sync current/savePoint/points so the Composer's modified indicator clears,
      // even if this save is the first agent action in the session (no prior refresh).
      sendUndoState(rs, runtimeId, sessionId, user);
   }

   /**
    * Push the runtime's current undo/redo position to the browser so the Composer's
    * "modified" indicator (current !== savePoint) stays in sync with agent-driven edits,
    * mirroring what {@code VSLayoutService.makeUndoable()} does for the browser-native
    * STOMP {@code @Undoable} path.
    */
   private void sendUndoState(RuntimeSheet rs, String runtimeId, String sessionId, String user) {
      UpdateUndoStateCommand command = new UpdateUndoStateCommand();
      command.setPoints(rs.size());
      command.setCurrent(rs.getCurrent());
      command.setSavePoint(rs.getSavePoint());
      command.setId(rs.getID());
      sendCommand(user, sessionId, runtimeId, command);
   }

   private void sendCommand(String user, String sessionId, String runtimeId, Object command) {
      sendToBrowser(user, sessionId, runtimeId, command);
   }

   /**
    * Push a <b>composer-level</b> command — one addressed at the Composer application itself
    * rather than at any open sheet — to the browser holding a paired session.
    *
    * <p>Deliberately not {@link #sendToBrowser}. That publishes to
    * {@link CommandDispatcher#COMMANDS_TOPIC} ({@code "/commands"}), the per-sheet-runtime channel
    * a {@code ViewsheetClientService} listens on and filters by
    * {@link CommandDispatcher#RUNTIME_ID_ATTR}. An {@code OpenComposerAssetCommand} has no open
    * sheet to be filtered by — the runtime it names is precisely the one the browser has not
    * opened yet — and its only client handler subscribes to {@code /user/composer-client}. Sent to
    * the sheet channel it is delivered to a destination with no handler and dropped silently,
    * while every call up the stack still reports success.
    *
    * <p>The two destination constants are both named {@code COMMANDS_TOPIC}, on
    * {@link CommandDispatcher} and {@link ComposerClientService}. Reaching for the wrong one is a
    * one-word mistake that produces no error anywhere. This mirrors {@code ComposerController}'s
    * native "Edit Data" send: address the socket session, set no runtime header.
    *
    * @param socketSessionId the paired browser's STOMP session id — both the destination user and
    *                        the session header, as the native path does
    */
   public void sendToComposer(String socketSessionId, Object command) {
      SimpMessageHeaderAccessor headers =
         SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
      headers.setSessionId(socketSessionId);
      headers.setLeaveMutable(true);

      commandDispatcherService.convertAndSendToUser(
         socketSessionId, ComposerClientService.COMMANDS_TOPIC, command,
         headers.getMessageHeaders());
   }

   /**
    * Tell the browser that minted the code that an agent has now joined with it.
    *
    * <p>Addresses {@code socketUserName} — the destination user resolved at mint time, the same
    * value mint's {@code @SendToUser} resolved to — and sets the session id header as the other
    * senders here do.
    *
    * <p>Callers must treat this as best-effort. By the time it runs the agent's session is open and
    * valid, so a failure here costs the browser's indicator and nothing more.
    */
   public void sendPairingJoined(JoinSession session) {
      sendPairingNotice(session, new PairingJoinedNotice(session.runtimeId(), session.sheetType(),
                                                          session.editorContext(), false));
   }

   /**
    * Tell the browser holding this session that Follow Focus moved its target -- via
    * {@code SheetSessionService.retarget}/{@code popFocus} -- without a fresh mint-and-join.
    *
    * <p>Reuses {@link #sendPairingJoined}'s exact topic and addressing (per the design plan:
    * "reusing the same broadcast channel #4669 already wired rather than inventing a second
    * one"), distinguished only by {@link PairingJoinedNotice#focusChanged()}. This is load-bearing
    * on the client: {@code ConnectToClaudeComponent}'s toolbar instance tracks a
    * {@code focusChanged} notice for its OWN session by {@code runtimeId} alone (the target has
    * moved, so the editorContext can no longer be expected to match anything that instance itself
    * minted), while a plain {@code sendPairingJoined} notice keeps requiring an exact
    * editorContext match so an unrelated pane's fresh pairing never lights up the toolbar.
    */
   public void sendFocusChanged(JoinSession session) {
      sendPairingNotice(session, new PairingJoinedNotice(session.runtimeId(), session.sheetType(),
                                                          session.editorContext(), true));
   }

   /**
    * Tells the browser tab for this runtime that an agent session is now attached to it, so the
    * Composer tab bar can show a connected indicator. Unlike {@link #sendPairingJoined} (addressed
    * to the one toolbar/pane instance that minted the code), this rides the per-sheet
    * {@code COMMANDS_TOPIC} -- the same channel {@link #broadcastSave} uses -- because the tab bar
    * is a single composer-main-level component keyed by runtimeId, not one of the several
    * {@code ConnectToClaudeComponent} instances that can be alive for one sheet.
    *
    * <p>Best-effort: callers must not let a failure here block whatever cleanup or join success
    * triggered it, mirroring {@link #sendPairingJoined}'s contract.
    */
   public void sendAgentActive(JoinSession session) {
      String sessionId = session.socketSessionId();

      if(sessionId == null) {
         return;
      }

      SetAgentActiveCommand command = SetAgentActiveCommand.builder()
         .active(true)
         .ownerIdentity(session.ownerIdentity())
         .build();

      sendToBrowser(session.socketUserName(), sessionId, session.runtimeId(), command);
   }

   /** Tells the browser tab for this runtime that no agent session is attached to it any more. */
   public void sendAgentInactive(JoinSession session) {
      String sessionId = session.socketSessionId();

      if(sessionId == null) {
         return;
      }

      SetAgentActiveCommand command = SetAgentActiveCommand.builder()
         .active(false)
         .build();

      sendToBrowser(session.socketUserName(), sessionId, session.runtimeId(), command);
   }

   private void sendPairingNotice(JoinSession session, PairingJoinedNotice notice) {
      // Nothing to notify. The REST mint path can record a null destination user (a null
      // principal), and Spring answers that with Assert.notNull — a stack trace the caller then
      // logs as "notifying the browser failed", which is misleading: nothing failed to send, there
      // was simply nobody to send to. broadcastRefresh guards its own null user for the same reason.
      if(session.socketUserName() == null) {
         LOG.debug("Pairing notice skipped — no destination user recorded (runtimeId={})",
                   session.runtimeId());
         return;
      }

      SimpMessageHeaderAccessor headers =
         SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
      headers.setSessionId(session.socketSessionId());
      headers.setLeaveMutable(true);

      commandDispatcherService.convertAndSendToUser(
         session.socketUserName(), PAIRING_JOINED_TOPIC, notice, headers.getMessageHeaders());
   }

   /**
    * Push one command to the browser holding a paired session.
    *
    * <p>Public because opening the base worksheet has to reach the same browser as a refresh
    * does, with the same headers, but is not a refresh. The private {@code sendCommand} delegates
    * here.
    */
   public void sendToBrowser(String socketUserName, String socketSessionId, String runtimeId,
                             Object command)
   {
      SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create();
      headers.setSessionId(socketSessionId);
      headers.setLeaveMutable(true);
      headers.setNativeHeader(CommandDispatcher.RUNTIME_ID_ATTR, runtimeId);
      headers.setNativeHeader(CommandDispatcher.COMMAND_TYPE_HEADER,
                              resolveCommandType(command));

      commandDispatcherService.convertAndSendToUser(
         socketUserName, CommandDispatcher.COMMANDS_TOPIC, command, headers.getMessageHeaders());
   }

   private Object buildCommand(SheetType sheetType, RuntimeSheet rs, Principal owner) {
      return switch(sheetType) {
         case WORKSHEET -> buildWorksheetRefreshCommand((RuntimeWorksheet) rs, owner);
         // VIEWSHEET is handled directly by broadcastViewsheetRefresh (per-assembly commands),
         // not via this single-command path.
         case VIEWSHEET -> throw new IllegalStateException(
            "VIEWSHEET must be handled by broadcastViewsheetRefresh, not buildCommand");
      };
   }

   private Object buildWorksheetRefreshCommand(RuntimeWorksheet rws, Principal principal) {
      Worksheet ws = rws.getWorksheet();

      if(ws == null) {
         return RefreshWorksheetCommand.builder().build();
      }

      List<WSAssemblyModel> models = new ArrayList<>();

      for(Assembly a : ws.getAssemblies()) {
         if(!(a instanceof WSAssembly wsAssembly) || !wsAssembly.isVisible())
         {
            continue;
         }

         try {
            models.add(WSAssemblyModelFactory.createModelFrom(wsAssembly, rws, principal));
         }
         catch(Exception e) {
            LOG.warn("Failed to build assembly model for broadcast (assembly={})", a.getName(), e);
         }
      }

      return RefreshWorksheetCommand.builder().assemblies(models).build();
   }

   /**
    * Mirrors CommandDispatcher's Immutable-stripping logic so Angular gets
    * e.g. "RefreshWorksheetCommand" not "ImmutableRefreshWorksheetCommand".
    */
   private static String resolveCommandType(Object command) {
      Class<?> cls = command.getClass();
      String name = cls.getSimpleName();

      if(name.startsWith("Immutable")) {
         Class<?> base = cls.getSuperclass();
         JsonSerialize ann = base == null ? null : base.getAnnotation(JsonSerialize.class);

         if(ann != null && cls.equals(ann.as())) {
            return base.getSimpleName();
         }
      }

      return name;
   }

   private final CommandDispatcherService commandDispatcherService;
   private final VSObjectModelFactoryService vsObjectModelFactoryService;
}
