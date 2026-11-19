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
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.RuntimeSheet;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.asset.AbstractTableAssembly;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.WSAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.viewsheet.CurrentSelectionVSAssembly;
import inetsoft.uql.viewsheet.TableDataVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.util.Catalog;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.command.RefreshBindingTreeCommand;
import inetsoft.web.binding.service.VSBindingTreeService;
import inetsoft.web.composer.command.FocusSheetCommand;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.vs.controller.VSLayoutControllerServiceProxy;
import inetsoft.web.composer.ws.assembly.WSAssemblyModel;
import inetsoft.web.composer.ws.assembly.WSAssemblyModelFactory;
import inetsoft.web.composer.ws.command.RefreshWorksheetCommand;
import inetsoft.web.composer.ws.command.SetAgentActiveCommand;
import inetsoft.web.composer.ws.command.SetWorksheetInfoCommand;
import inetsoft.web.viewsheet.command.AddVSObjectCommand;
import inetsoft.web.viewsheet.command.LoadTableDataCommand;
import inetsoft.web.viewsheet.command.RefreshAgentScriptCommand;
import inetsoft.web.viewsheet.command.RefreshEmbeddedVSCommand;
import inetsoft.web.viewsheet.command.RefreshVSObjectCommand;
import inetsoft.web.viewsheet.command.RemoveVSObjectCommand;
import inetsoft.web.viewsheet.command.SaveSheetCommand;
import inetsoft.web.viewsheet.command.SetViewsheetInfoCommand;
import inetsoft.web.viewsheet.command.UpdateLayoutUndoStateCommand;
import inetsoft.web.viewsheet.command.UpdateUndoStateCommand;
import inetsoft.web.viewsheet.model.VSObjectModel;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CommandDispatcherService;
import inetsoft.web.viewsheet.service.ComposerClientService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

   /**
    * What {@link #broadcastRefresh} reports when the browser's socket session no longer exists:
    * the edit is on the server, the open page was not told.
    */
   public static final String SESSION_GONE_NOTICE =
      "The edit is saved on the server, but the open page is no longer connected, so it was " +
      "not refreshed. Reload the page or re-pair to see the change.";

   @Autowired
   public SheetAgentBroadcastService(CommandDispatcherService commandDispatcherService,
                                     VSObjectModelFactoryService vsObjectModelFactoryService,
                                     VSBindingTreeService vsBindingTreeService,
                                     CoreLifecycleService coreLifecycleService,
                                     VSLayoutControllerServiceProxy vsLayoutControllerService)
   {
      this.commandDispatcherService = commandDispatcherService;
      this.vsObjectModelFactoryService = vsObjectModelFactoryService;
      this.vsBindingTreeService = vsBindingTreeService;
      this.coreLifecycleService = coreLifecycleService;
      this.vsLayoutControllerService = vsLayoutControllerService;
   }

   /**
    * Convenience constructor for callers that never push a layout-designer refresh;
    * {@link #broadcastLayoutRefresh} is then a no-op.
    */
   public SheetAgentBroadcastService(CommandDispatcherService commandDispatcherService,
                                     VSObjectModelFactoryService vsObjectModelFactoryService,
                                     VSBindingTreeService vsBindingTreeService,
                                     CoreLifecycleService coreLifecycleService)
   {
      this(commandDispatcherService, vsObjectModelFactoryService, vsBindingTreeService,
           coreLifecycleService, null);
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
   public String broadcastRefresh(RuntimeSheet rs, SheetType sheetType, String runtimeId,
                                  Principal owner)
   {
      return broadcastRefresh(rs, sheetType, runtimeId, owner, List.of());
   }

   /**
    * {@link #broadcastRefresh(RuntimeSheet, SheetType, String, Principal)} that also forwards the
    * table data the mutation itself loaded.
    *
    * <p>A wiz write runs under a capturing dispatcher, so the {@code LoadTableDataCommand} a
    * native table service dispatches (e.g. {@code VSBindingModelService.setBinding}) never reaches
    * the browser, and crosstab/table cells are not part of the object model the refresh carries.
    * Without them a freshly bound table paints empty until the user hits Refresh (Bug #80736).
    * Only commands the mutation already produced are forwarded: no extra query runs, and a chart
    * mutation produces none.
    *
    * @param capturedCommands commands the mutation dispatched; only {@code LoadTableDataCommand}s
    *                         for visible tables (calc tables included) are used. May be null.
    * @return {@link #SESSION_GONE_NOTICE} when the browser session the runtime addresses no longer
    *         exists, so nothing was pushed; the caller relays it to the agent next to the other
    *         warnings of the call. {@code null} otherwise, including when no browser session was
    *         ever recorded (nothing is open to refresh). A session that is gone but not yet
    *         reaped (a half-open connection) still reads as live and is not reported.
    */
   public String broadcastRefresh(RuntimeSheet rs, SheetType sheetType, String runtimeId,
                                  Principal owner, List<CommandDispatcher.Command> capturedCommands)
   {
      String sessionId = rs.getSocketSessionId();

      if(sessionId == null) {
         LOG.warn("Pairing broadcast skipped — no socket session recorded (runtimeId={}, runtimeClass={})",
                  runtimeId, rs.getClass().getSimpleName());
         return null;
      }

      if(!commandDispatcherService.isSessionLive(sessionId)) {
         LOG.warn("Pairing broadcast not delivered — the browser's socket session is gone " +
                  "(runtimeId={}, sessionId={})", runtimeId, sessionId);
         return SESSION_GONE_NOTICE;
      }

      String user = rs.getSocketUserName();

      if(user == null) {
         user = owner == null ? null : owner.getName();
      }

      if(sheetType == SheetType.VIEWSHEET) {
         broadcastCapturedRemovals(runtimeId, sessionId, user, capturedCommands);
         broadcastViewsheetRefresh((RuntimeViewsheet) rs, runtimeId, sessionId, user);
         broadcastCapturedEmbeddedContent((RuntimeViewsheet) rs, runtimeId, sessionId, user,
                                          capturedCommands);
         broadcastCapturedTableData((RuntimeViewsheet) rs, runtimeId, sessionId, user,
                                    capturedCommands);
         sendUndoState(rs, runtimeId, sessionId, user);
         return null;
      }

      Object command = buildCommand(sheetType, rs, owner);
      sendCommand(user, sessionId, runtimeId, command);
      sendUndoState(rs, runtimeId, sessionId, user);

      LOG.info("Pairing broadcast refresh sent (sheetType={}, runtimeId={}, sessionId={})",
               sheetType, runtimeId, sessionId);
      return null;
   }

   /**
    * Tells the browser to drop every assembly that an undo/redo removed or re-parented.
    *
    * <p>The refresh broadcast only walks the assemblies that still exist, and the Composer client
    * removes an object from its canvas solely on {@code RemoveVSObjectCommand}, so without this an
    * undone add (or a redone remove, or either side of a rename) leaves a ghost. The diff is the
    * human undo path's own {@link CoreLifecycleService#checkAndRemoveAssemblies}, run against a
    * capturing dispatcher and forwarded, so container and embedded-viewsheet handling match it.
    * Call before {@link #broadcastRefresh} so removals precede the refreshes.
    *
    * <p>Failures are logged and swallowed: the undo already happened and must not be reported as
    * failed because the browser could not be told.
    *
    * @param before a clone of the viewsheet taken before the undo/redo was applied
    */
   public void broadcastRemovedAssemblies(RuntimeViewsheet rvs, String runtimeId,
                                          Principal owner, Viewsheet before)
   {
      String sessionId = rvs.getSocketSessionId();
      Viewsheet after = rvs.getViewsheet();

      if(sessionId == null || before == null || after == null) {
         return;
      }

      String user = rvs.getSocketUserName();

      if(user == null) {
         user = owner == null ? null : owner.getName();
      }

      try {
         List<CommandDispatcher.Command> removals =
            CapturingCommandDispatcher.withCapturingDispatcher(owner, dispatcher -> {
               coreLifecycleService.checkAndRemoveAssemblies(before, after, dispatcher);
               return new ArrayList<>(dispatcher.getCapturedCommands());
            });

         for(CommandDispatcher.Command removal : removals) {
            String assembly = removal.getAssembly();
            sendToBrowser(user, sessionId, runtimeId,
                          assembly == null || assembly.isEmpty() ? null : assembly,
                          removal.getCommand());
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to send removed-assembly commands (runtimeId={})", runtimeId, e);
      }
   }

   /**
    * The absolute names of the assemblies a viewer of {@code rvs} draws right now, by the same
    * rule the native viewer uses ({@link VSEventUtil#isVisible}). Take it before a mutation and
    * hand it to {@link #broadcastVisibilityChanges} afterwards.
    *
    * @return the names, or {@code null} when they cannot be determined (the caller then skips the
    *         diff; nothing is lost beyond the add of a re-shown assembly)
    */
   public Set<String> drawnAssemblyNames(RuntimeViewsheet rvs) {
      try {
         Viewsheet vs = rvs.getViewsheet();

         if(vs == null) {
            return null;
         }

         Set<String> names = new HashSet<>();

         for(Assembly a : vs.getAssemblies(true, false)) {
            if(a instanceof VSAssembly vsAssembly && VSEventUtil.isVisible(rvs, vsAssembly)) {
               names.add(vsAssembly.getAbsoluteName());
            }
         }

         return names;
      }
      catch(Exception e) {
         LOG.warn("Failed to determine the drawn assemblies (runtimeId={})", rvs.getID(), e);
         return null;
      }
   }

   /**
    * Tells the browser about every assembly whose visibility the mutation changed: the ones drawn
    * now that were not before (a re-shown assembly, an undone hide, the children of a re-shown
    * group container) are added, the ones drawn before that are not any more (a hide, an undone
    * show, a redone hide) are removed.
    *
    * <p>The refresh broadcast only updates objects the viewer already holds: the Preview
    * ({@code viewer-app}) ignores a refresh for an object it no longer has, and keeps drawing
    * one it was not told to remove, so a visibility change that reaches the server only through
    * an undo/redo or a show shows in the browser only after a reload (Bug #81569). The native
    * service sends an {@code AddVSObjectCommand} or {@code RemoveVSObjectCommand}; this runs that
    * same {@link CoreLifecycleService#addDeleteVSObject} (and the table load of
    * {@link CoreLifecycleService#initTable} for an added table) under a capturing dispatcher and
    * forwards the commands with the address the native service gave them (selection-container
    * and embedded viewsheet children). Call before {@link #broadcastRefresh}, which then updates
    * the added objects like any other.
    *
    * <p>Best effort: a failure is logged and swallowed, the mutation already happened.
    *
    * @param drawnBefore     {@link #drawnAssemblyNames} taken before the mutation; null skips
    * @param alreadyCaptured commands the mutation produced; a table whose data is among them is
    *                        not loaded again, and an assembly whose removal is among them is not
    *                        removed again
    * @return the {@code LoadTableDataCommand}s of the added tables, for the caller to pass to
    *         {@link #broadcastRefresh} so they follow the refresh that carries the table model
    */
   public List<CommandDispatcher.Command> broadcastVisibilityChanges(
      RuntimeViewsheet rvs, String runtimeId, Principal owner, Set<String> drawnBefore,
      List<CommandDispatcher.Command> alreadyCaptured)
   {
      String sessionId = rvs.getSocketSessionId();
      Viewsheet vs = rvs.getViewsheet();
      List<CommandDispatcher.Command> tableData = new ArrayList<>();

      if(sessionId == null || vs == null || drawnBefore == null) {
         return tableData;
      }

      String user = rvs.getSocketUserName();

      if(user == null) {
         user = owner == null ? null : owner.getName();
      }

      Set<String> loaded = new HashSet<>();
      Set<String> removed = new HashSet<>();

      if(alreadyCaptured != null) {
         for(CommandDispatcher.Command c : alreadyCaptured) {
            if(c == null) {
               continue;
            }

            if(c.getCommand() instanceof LoadTableDataCommand && c.getAssembly() != null) {
               loaded.add(c.getAssembly());
            }
            else if(c.getCommand() instanceof RemoveVSObjectCommand remove) {
               removed.add(remove.getName());
            }
         }
      }

      List<VSAssembly> added = changedVisibility(rvs, vs, drawnBefore, true);
      List<VSAssembly> hidden = changedVisibility(rvs, vs, drawnBefore, false);
      hidden.removeIf(a -> removed.contains(a.getAbsoluteName()));

      if(added.isEmpty() && hidden.isEmpty()) {
         return tableData;
      }

      List<CommandDispatcher.Command> captured = new ArrayList<>();

      try {
         CapturingCommandDispatcher.withCapturingDispatcher(owner, dispatcher -> {
            try {
               for(VSAssembly assembly : hidden) {
                  coreLifecycleService.addDeleteVSObject(rvs, assembly, dispatcher, true);
               }

               for(VSAssembly assembly : added) {
                  coreLifecycleService.addDeleteVSObject(rvs, assembly, dispatcher, true);

                  if(!loaded.contains(assembly.getAbsoluteName())) {
                     coreLifecycleService.initTable(rvs, dispatcher, "", assembly);
                  }
               }
            }
            finally {
               captured.addAll(dispatcher.getCapturedCommands());
            }

            return null;
         });
      }
      catch(Exception e) {
         LOG.warn("Failed to send the visibility changes to the paired browser (runtimeId={})",
                  runtimeId, e);
      }

      for(CommandDispatcher.Command c : captured) {
         if(c == null || c.getCommand() == null) {
            continue;
         }

         Object command = c.getCommand();

         try {
            if(command instanceof LoadTableDataCommand) {
               tableData.add(c);
            }
            else if(command instanceof AddVSObjectCommand ||
                    command instanceof RemoveVSObjectCommand ||
                    command instanceof RefreshEmbeddedVSCommand)
            {
               String assembly = c.getAssembly();
               sendToBrowser(user, sessionId, runtimeId,
                             assembly == null || assembly.isEmpty() ? null : assembly, command);
            }
         }
         catch(Exception e) {
            LOG.warn("Failed to forward a visibility change to the paired browser (runtimeId={})",
                     runtimeId, e);
         }
      }

      LOG.info("Pairing broadcast sent visibility changes (runtimeId={}, sessionId={}, " +
               "added={}, removed={})", runtimeId, sessionId, added.size(), hidden.size());
      return tableData;
   }

   /**
    * The assemblies still on the sheet that are drawn now and were not in {@code drawnBefore}
    * ({@code drawnNow} true), or were and are not ({@code drawnNow} false), without those inside
    * an embedded viewsheet that is itself among them: the embedded viewsheet's own add or remove
    * covers its children. A deleted assembly is not on the sheet; that is the removal diff's job.
    */
   private List<VSAssembly> changedVisibility(RuntimeViewsheet rvs, Viewsheet vs,
                                              Set<String> drawnBefore, boolean drawnNow)
   {
      List<VSAssembly> changed = new ArrayList<>();
      Set<String> changedViewsheets = new HashSet<>();

      try {
         for(Assembly a : vs.getAssemblies(true, false)) {
            if(a instanceof VSAssembly vsAssembly &&
               drawnBefore.contains(vsAssembly.getAbsoluteName()) != drawnNow &&
               VSEventUtil.isVisible(rvs, vsAssembly) == drawnNow)
            {
               changed.add(vsAssembly);

               if(vsAssembly instanceof Viewsheet) {
                  changedViewsheets.add(vsAssembly.getAbsoluteName());
               }
            }
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to determine the assemblies whose visibility changed (runtimeId={})",
                  rvs.getID(), e);
         return new ArrayList<>();
      }

      changed.removeIf(a -> {
         for(Viewsheet parent = a.getViewsheet(); parent != null && parent != vs;
             parent = parent.getViewsheet())
         {
            if(changedViewsheets.contains(parent.getAbsoluteName())) {
               return true;
            }
         }

         return false;
      });

      return changed;
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
            // an output assembly's runtime state (a text's display text) is only computed by the
            // native refresh, so a model built without it draws an untouched text empty
            rvs.getViewsheetSandbox().ifPresent(
               box -> coreLifecycleService.prepareOutputAssemblyForRefresh(vsAssembly, rvs, box));
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

         // Bug #81648: the native refresh (CoreLifecycleService.refreshVSObject) addresses a
         // selection-container child to its container. The client updates the child inside the
         // container only on an addressed command; an unaddressed one draws the child again at
         // its stored position, leaving a ghost copy until the user hits Refresh.
         if(vsAssembly.getContainer() instanceof CurrentSelectionVSAssembly container) {
            sendToBrowser(user, sessionId, runtimeId, container.getAbsoluteName(), command);
         }
         else {
            sendCommand(user, sessionId, runtimeId, command);
         }

         sent++;
      }

      LOG.info("Pairing broadcast viewsheet refresh sent (runtimeId={}, sessionId={}, assemblies={})",
               runtimeId, sessionId, sent);
   }

   /**
    * Forwards the {@code RemoveVSObjectCommand}s a mutation dispatched (and the capturing
    * dispatcher swallowed) to the paired browser, ahead of the per-assembly refresh. The refresh
    * only walks the assemblies that still exist and the Composer client removes an object from its
    * canvas solely on {@code RemoveVSObjectCommand}, so without this a removed, cut, ungrouped or
    * hidden assembly (Bug #81304), and every child of a removed embedded viewsheet, stays drawn.
    * Each command keeps the address the native service gave it (selection-container children).
    * Best effort: a failure is logged and swallowed.
    */
   private void broadcastCapturedRemovals(String runtimeId, String sessionId, String user,
                                          List<CommandDispatcher.Command> captured)
   {
      if(captured == null || captured.isEmpty()) {
         return;
      }

      for(CommandDispatcher.Command c : captured) {
         if(c == null || !(c.getCommand() instanceof RemoveVSObjectCommand)) {
            continue;
         }

         try {
            String assembly = c.getAssembly();
            sendToBrowser(user, sessionId, runtimeId,
                          assembly == null || assembly.isEmpty() ? null : assembly,
                          c.getCommand());
         }
         catch(Exception ex) {
            LOG.warn("Failed to forward a removal to the paired browser (runtimeId={})",
                     runtimeId, ex);
         }
      }
   }

   /**
    * Forwards the commands that put an embedded viewsheet's content on the canvas to the paired
    * browser, after the per-assembly refresh has created the embed itself (Bug #81304). The
    * embed's children live inside the embed's own client component, which creates a child only
    * from an {@code AddVSObjectCommand} (a refresh updates an existing child and ignores a
    * missing one), so the capturing dispatcher swallowing them left an added embed empty until the
    * user hit Refresh. Only commands addressed to an embedded viewsheet that still exists are
    * forwarded, in the order the native service dispatched them ({@code RefreshEmbeddedVSCommand}
    * before the children's adds). Best effort: a failure is logged and swallowed.
    */
   private void broadcastCapturedEmbeddedContent(RuntimeViewsheet rvs, String runtimeId,
                                                 String sessionId, String user,
                                                 List<CommandDispatcher.Command> captured)
   {
      if(captured == null || captured.isEmpty()) {
         return;
      }

      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return;
      }

      for(CommandDispatcher.Command c : captured) {
         if(c == null || c.getAssembly() == null || c.getAssembly().isEmpty() ||
            !(c.getCommand() instanceof AddVSObjectCommand ||
              c.getCommand() instanceof RefreshEmbeddedVSCommand))
         {
            continue;
         }

         try {
            if(vs.getAssembly(c.getAssembly()) instanceof Viewsheet) {
               sendToBrowser(user, sessionId, runtimeId, c.getAssembly(), c.getCommand());
            }
         }
         catch(Exception ex) {
            LOG.warn("Failed to forward embedded viewsheet content to the paired browser " +
                     "(assembly={})", c.getAssembly(), ex);
         }
      }
   }

   /**
    * Forwards the {@code LoadTableDataCommand}s a mutation dispatched (and the capturing
    * dispatcher swallowed) to the paired browser, after the per-assembly refresh so the table's
    * model is already in place. The last command per assembly wins. Table and calc/freehand
    * table cells are never part of the object model (the client assigns them only from
    * {@code LoadTableDataCommand}), and charts have no table data, so only visible
    * {@code TableDataVSAssembly} instances are sent. Best effort: a failure is logged and swallowed.
    */
   private void broadcastCapturedTableData(RuntimeViewsheet rvs, String runtimeId,
                                           String sessionId, String user,
                                           List<CommandDispatcher.Command> captured)
   {
      if(captured == null || captured.isEmpty()) {
         return;
      }

      try {
         Viewsheet vs = rvs.getViewsheet();

         if(vs == null) {
            return;
         }

         Map<String, LoadTableDataCommand> latest = new LinkedHashMap<>();

         for(CommandDispatcher.Command c : captured) {
            if(c != null && c.getCommand() instanceof LoadTableDataCommand load &&
               c.getAssembly() != null)
            {
               latest.remove(c.getAssembly());
               latest.put(c.getAssembly(), load);
            }
         }

         for(Map.Entry<String, LoadTableDataCommand> e : latest.entrySet()) {
            try {
               Assembly a = vs.getAssembly(e.getKey());

               if(!(a instanceof TableDataVSAssembly) || !((VSAssembly) a).isVisible())
               {
                  continue;
               }

               sendToBrowser(user, sessionId, runtimeId, e.getKey(), e.getValue());
            }
            catch(Exception ex) {
               LOG.warn("Failed to forward table data to the paired browser (assembly={})",
                        e.getKey(), ex);
            }
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to forward captured table data (runtimeId={})", runtimeId, ex);
      }
   }

   /**
    * Redraw the paired browser's layout designer after a wiz layout write (Bug #81189).
    *
    * <p>The layout tools edit the master's {@code LayoutInfo} under a capturing dispatcher and
    * discard everything it captures, and the master-assembly refresh does not reach the designer,
    * which is bound to a separate temporary layout runtime. This re-runs the native
    * {@code changeViewsheetLayout} resend for {@code layoutName} (current layout == target, so the
    * layout undo stack is left alone) and forwards the captured commands, addressed with the
    * MASTER runtime id as the native path does, then the layout undo/redo button state.
    *
    * <p>The push always targets {@code layoutName}: the server does not know which layout the
    * browser's designer currently shows, so a browser on Master or on another layout is switched
    * to {@code layoutName}. Best effort: any failure is logged and swallowed so it never fails
    * the tool call whose mutation already succeeded.
    */
   public void broadcastLayoutRefresh(RuntimeViewsheet master, String layoutName,
                                      Principal agent)
   {
      if(layoutName == null) {
         return;
      }

      pushLayoutChange(master, layoutName, layoutName, agent);
   }

   /**
    * Switches the paired browser back to the Master view after the layout it may be showing was
    * deleted or cleared through a wiz tool (Bug #81732).
    *
    * <p>The layout designer is bound to a separate temporary layout runtime, so without this the
    * Composer stays in layout mode on a layout that no longer exists, with no layouts to switch
    * to. This re-runs the native {@code changeViewsheetLayout} for Master, which sends
    * {@code ChangeCurrentLayoutCommand(null)} and redraws the master. The server does not know
    * which layout the browser currently shows, so a browser on another layout is also returned
    * to Master. Best effort: any failure is logged and swallowed so it never fails the tool call
    * whose mutation already succeeded.
    *
    * @param removedLayoutName the layout that was removed (the browser's presumed current layout)
    */
   public void broadcastSwitchToMaster(RuntimeViewsheet master, String removedLayoutName,
                                       Principal agent)
   {
      pushLayoutChange(master, removedLayoutName,
                       Catalog.getCatalog(agent).getString("Master"), agent);
   }

   private void pushLayoutChange(RuntimeViewsheet master, String currentLayout,
                                 String targetLayout, Principal agent)
   {
      if(master == null || vsLayoutControllerService == null) {
         return;
      }

      String sessionId = master.getSocketSessionId();

      if(sessionId == null) {
         LOG.debug("Layout refresh broadcast skipped - no socket session recorded (runtimeId={})",
                   master.getID());
         return;
      }

      String user = master.getSocketUserName();

      if(user == null) {
         user = agent == null ? null : agent.getName();
      }

      try {
         String runtimeId = master.getID();
         List<CommandDispatcher.Command> captured = new ArrayList<>();

         CapturingCommandDispatcher.withCapturingDispatcher(agent, dispatcher -> {
            vsLayoutControllerService.changeViewsheetLayout(
               runtimeId, currentLayout, targetLayout, agent, null, dispatcher);
            captured.addAll(dispatcher.getCapturedCommands());
            return null;
         });

         for(CommandDispatcher.Command c : captured) {
            if(c != null && c.getCommand() != null) {
               sendToBrowser(user, sessionId, runtimeId, c.getAssembly(), c.getCommand());
            }
         }

         UpdateLayoutUndoStateCommand undoState = new UpdateLayoutUndoStateCommand();
         undoState.setLayoutPoint(master.getLayoutPoint());
         undoState.setLayoutPoints(master.getLayoutPointsSize());
         undoState.setId(runtimeId);
         sendCommand(user, sessionId, runtimeId, undoState);

         LOG.info("Pairing layout refresh sent (runtimeId={}, layout={}, commands={})",
                  runtimeId, targetLayout, captured.size());
      }
      catch(Exception e) {
         LOG.warn("Failed to push the layout designer refresh (runtimeId={}, layout={})",
                  master.getID(), targetLayout, e);
      }
   }

   /**
    * Push a live refresh of the persistent Data panel/asset tree (the Toolbox's
    * {@code ComposerBindingTree}) to the browser holding this runtime's paired session.
    *
    * <p>{@link #broadcastViewsheetRefresh} only repaints visible top-level assembly canvases — it
    * has no path to the binding/asset tree, which the browser otherwise only repopulates on a
    * client-initiated pull ({@code RefreshBindingTreeEvent}). This mirrors that pull's own tree
    * construction ({@code VSBindingTreeControllerService.getBinding}, with no assembly selected —
    * the same no-assembly-context {@code ComposerBindingTree} itself uses) and pushes the result
    * as a {@code RefreshBindingTreeCommand} via the same unsolicited, runtime-id-keyed addressing
    * {@link #broadcastViewsheetRefresh} already uses. Reaches
    * {@code VSPane.processRefreshBindingTreeCommand} on the client, which only applies it while
    * that viewsheet's tab is focused — a caller-accepted limitation, not a defect of this method.
    *
    * <p>Callers must not let a failure here fail the mutation that already succeeded — a stale
    * tree is a paper cut, not lost data — so build/dispatch errors are logged and swallowed.
    *
    * @param principal the principal to resolve the runtime's binding tree with (must own/be
    *                  authorized for {@code runtimeId} — ordinarily the same one the caller just
    *                  used to make the mutation being broadcast)
    */
   public void broadcastBindingTreeRefresh(RuntimeSheet rs, String runtimeId, Principal principal) {
      String sessionId = rs.getSocketSessionId();

      if(sessionId == null) {
         return;
      }

      String user = rs.getSocketUserName();

      if(user == null) {
         user = principal == null ? null : principal.getName();
      }

      try {
         TreeNodeModel tree = vsBindingTreeService.getBinding(runtimeId, null, false, principal);

         if(tree == null) {
            return;
         }

         RefreshBindingTreeCommand command = new RefreshBindingTreeCommand(tree);
         sendCommand(user, sessionId, runtimeId, command);
      }
      catch(Exception e) {
         LOG.warn("Failed to build/send binding tree refresh (runtimeId={})", runtimeId, e);
      }
   }

   /**
    * Push a fully-populated {@code SetViewsheetInfoCommand} (the same field set
    * {@link CoreLifecycleService#setViewsheetInfo} sends for human-driven flows) to the browser
    * holding this runtime's paired session.
    *
    * <p>This is the only command that writes {@code VSPane}'s {@code this.vs.baseEntry}
    * client-side, which drives the Composer's bottom status-bar worksheet-path chip. Neither
    * {@link #broadcastRefresh} (per-assembly canvas repaint) nor {@link #broadcastBindingTreeRefresh}
    * (Data panel/asset tree) ever touches it, so a caller that changes a viewsheet's base entry
    * (e.g. {@code attach_base_worksheet}) needs this separate push too, or the chip stays stale
    * until the user manually reopens/refreshes the viewsheet.
    *
    * <p>Callers must not let a failure here fail the mutation that already succeeded -- a stale
    * status bar is a paper cut, not lost data -- so build/dispatch errors are logged and swallowed,
    * mirroring {@link #broadcastBindingTreeRefresh}'s contract.
    */
   public void broadcastViewsheetInfoRefresh(RuntimeViewsheet rvs, String runtimeId,
                                             Principal principal)
   {
      String sessionId = rvs.getSocketSessionId();

      if(sessionId == null) {
         return;
      }

      String user = rvs.getSocketUserName();

      if(user == null) {
         user = principal == null ? null : principal.getName();
      }

      try {
         SetViewsheetInfoCommand command = coreLifecycleService.buildViewsheetInfoCommand(rvs, null);
         sendCommand(user, sessionId, runtimeId, command);
      }
      catch(Exception e) {
         LOG.warn("Failed to build/send viewsheet info refresh (runtimeId={})", runtimeId, e);
      }
   }

   /**
    * Push the just-written text of a script/calc-field expression to the browser holding this
    * runtime's paired session, so an already-open {@code ViewsheetScriptPane}/
    * {@code FormulaEditorDialog} editing that EXACT location can refresh its own display --
    * see {@link RefreshAgentScriptCommand}'s own javadoc for the client-side clobber guard this
    * depends on.
    *
    * <p>Extends the same unsolicited, runtime-id-keyed addressing {@link #broadcastBindingTreeRefresh}/
    * {@link #broadcastViewsheetInfoRefresh} already use, rather than inventing a new push
    * architecture -- but unlike those two (read-only tree/status chip, safe to apply
    * unconditionally), this targets EDITABLE dialog state, which is exactly why it is the CLIENT
    * that decides whether applying it would clobber an unsaved human edit, not this method.
    *
    * <p>Callers must not let a failure here fail the mutation that already succeeded -- same
    * contract as {@link #broadcastBindingTreeRefresh}/{@link #broadcastViewsheetInfoRefresh}.
    *
    * @param kind     the script location's wire kind (e.g. {@code "viewsheetOnInit"},
    *                 {@code "calcField"} -- mirrors {@code ScriptTarget.Kind.wireName()})
    * @param assembly the assembly name, or -- for {@code calcField} -- the table the field
    *                 belongs to; {@code null} for a kind with neither
    * @param name     the calc field's own name; {@code null} for a kind with no third component
    * @param text     the new script/expression text, as just written
    */
   public void broadcastScriptRefresh(RuntimeSheet rs, String runtimeId, Principal owner,
                                      String kind, String assembly, String name, String text)
   {
      pushScriptRefresh(rs, runtimeId, owner, kind, assembly, name, text);
   }

   /**
    * Same push as {@link #broadcastScriptRefresh}, but reports whether it was sent. For a caller
    * whose ONLY effect is the push (a pending Create Expression draft), a silently dropped push
    * would be a silent no-op, so it must be able to fail loud.
    *
    * @return {@code true} when the command was handed to the messaging layer; {@code false} when
    *         the runtime has no paired browser socket or the send failed. There is no browser
    *         acknowledgement, so {@code true} means "sent", not "applied"
    */
   public boolean pushScriptRefresh(RuntimeSheet rs, String runtimeId, Principal owner,
                                    String kind, String assembly, String name, String text)
   {
      return pushScriptDraft(rs, runtimeId, owner, kind, assembly, name, text, null, null);
   }

   /**
    * Same push as {@link #pushScriptRefresh}, additionally carrying the dialog's SQL/Script
    * switch and return data type as a draft (applied by the user's OK, like the text).
    *
    * @param text       the new text, or {@code null} to leave the dialog's text alone
    * @param sql        {@code true} for SQL, {@code false} for Script, {@code null} to leave it
    * @param returnType the return data type, or {@code null} to leave it
    */
   public boolean pushScriptDraft(RuntimeSheet rs, String runtimeId, Principal owner,
                                  String kind, String assembly, String name, String text,
                                  Boolean sql, String returnType)
   {
      String sessionId = rs.getSocketSessionId();

      if(sessionId == null) {
         return false;
      }

      String user = rs.getSocketUserName();

      if(user == null) {
         user = owner == null ? null : owner.getName();
      }

      try {
         RefreshAgentScriptCommand command = new RefreshAgentScriptCommand();
         command.setKind(kind);
         command.setAssembly(assembly);
         command.setName(name);
         command.setText(text);
         command.setSql(sql);
         command.setReturnType(returnType);
         sendCommand(user, sessionId, runtimeId, command);
         return true;
      }
      catch(Exception e) {
         LOG.warn("Failed to build/send agent script refresh (runtimeId={}, kind={})",
                  runtimeId, kind, e);
         return false;
      }
   }

   /**
    * Push a tab-label command (SetViewsheetInfoCommand or SetWorksheetInfoCommand, matching the
    * runtime's own type) + SaveSheetCommand to the browser so the tab title, id, and save point
    * update — mirrors the regular save flow in SaveWorksheetDialogService.
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

      // 1. Send a tab-label command matching the runtime's own type -- the viewsheet tab
      // (viewsheet-pane.component.ts) only listens for SetViewsheetInfoCommand.assemblyInfo.name,
      // never SetWorksheetInfoCommand.label; sending the worksheet command here left every
      // viewsheet save silently unable to update its own tab title.
      //
      // Bug #76637: this used to send a SetViewsheetInfoCommand with only assemblyInfo populated,
      // leaving info/baseEntry at their Java null defaults. processSetViewsheetInfoCommand
      // unconditionally assigns command.baseEntry into this.vs.baseEntry with no null guard, so
      // that partial command silently re-nulled the bottom-bar worksheet-path chip on every save --
      // including right after attach_base_worksheet populated it. Use the same fully-populated
      // builder CoreLifecycleService.setViewsheetInfo uses instead of hand-rolling a subset.
      if(rs instanceof RuntimeViewsheet rvs) {
         SetViewsheetInfoCommand infoCommand = coreLifecycleService.buildViewsheetInfoCommand(rvs, null);
         sendCommand(user, sessionId, runtimeId, infoCommand);
      }
      else {
         SetWorksheetInfoCommand labelCommand = SetWorksheetInfoCommand.builder()
            .label(rs.getEntry().toView())
            .build();

         sendCommand(user, sessionId, runtimeId, labelCommand);
      }

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
    * Hints the Composer that owns {@code rs} that an agent just edited it, so a Composer with
    * "Follow Focus" ticked can switch to that sheet's tab (Bug #80379).
    *
    * <p>Not part of {@link #broadcastRefresh}, which several non-edit callers use (the
    * viewsheet reload that follows a worksheet save, undo/redo, a save acknowledgement) and
    * which also runs after a failed mutation. A caller invokes this once, after an agent edit
    * it knows succeeded, so only an edit of the paired sheet itself can move the user's tab.
    *
    * <p>Sent on the composer channel (see {@link #sendToComposer}), to the owning socket session
    * only. Best effort and silent: nothing is sent when no browser session is recorded or the
    * session is gone, and a failure never reaches the caller, because the edit already
    * succeeded. Whether to act is the browser's decision.
    */
   public void focusSheet(RuntimeSheet rs, String runtimeId) {
      String sessionId = rs == null ? null : rs.getSocketSessionId();

      if(sessionId == null || runtimeId == null) {
         return;
      }

      try {
         if(!commandDispatcherService.isSessionLive(sessionId)) {
            return;
         }

         sendToComposer(sessionId, FocusSheetCommand.builder().runtimeId(runtimeId).build());
      }
      catch(Exception e) {
         LOG.warn("Follow Focus tab hint not sent (runtimeId={}, sessionId={})", runtimeId,
                  sessionId, e);
      }
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
         .ownerLabel(PairingUtil.label(session.ownerIdentity()))
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
      sendToBrowser(socketUserName, socketSessionId, runtimeId, null, command);
   }

   /**
    * {@link #sendToBrowser(String, String, String, Object)} for a command the client routes by
    * assembly (e.g. {@code LoadTableDataCommand}, which carries no assembly name itself).
    */
   public void sendToBrowser(String socketUserName, String socketSessionId, String runtimeId,
                             String assemblyName, Object command)
   {
      SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create();
      headers.setSessionId(socketSessionId);
      headers.setLeaveMutable(true);
      headers.setNativeHeader(CommandDispatcher.RUNTIME_ID_ATTR, runtimeId);

      if(assemblyName != null) {
         headers.setNativeHeader(CommandDispatcher.ASSEMBLY_NAME_HEADER, assemblyName);
      }

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
      long deadline = System.currentTimeMillis() + BROADCAST_BUDGET_MS;

      for(Assembly a : ws.getAssemblies()) {
         if(!(a instanceof WSAssembly wsAssembly) || !wsAssembly.isVisible())
         {
            continue;
         }

         try {
            models.add(buildModel(wsAssembly, rws, principal, deadline));
         }
         catch(Exception e) {
            LOG.warn("Failed to build assembly model for broadcast (assembly={})", a.getName(), e);
         }
      }

      return RefreshWorksheetCommand.builder().assemblies(models).build();
   }

   /**
    * Bug #81415: builds one assembly's model for the refresh, never holding the caller (the HTTP
    * request thread of the edit) longer than the broadcast budget.
    *
    * <p>A table's model is built by executing the table, and a table downstream of an assembly
    * that needs script evaluation (a mirror, join, ... of a table with a script expression
    * column) runs that script right there. So a table gets {@link #TABLE_BUDGET_MS} of the shared
    * {@link #BROADCAST_BUDGET_MS} to build live. A table that does not finish in time, is still
    * building from an earlier edit, or finds the shared budget spent, is still sent: its model is
    * built from the definition alone, without row count or column infos, which the next read of
    * the table fills in. Healthy tables keep both; only a slow one degrades. Other assemblies
    * never execute anything.</p>
    */
   private WSAssemblyModel buildModel(WSAssembly assembly, RuntimeWorksheet rws,
                                      Principal principal, long deadline)
      throws Exception
   {
      if(!(assembly instanceof AbstractTableAssembly)) {
         return modelFactory.create(assembly, rws, principal, true);
      }

      long budget = Math.min(TABLE_BUDGET_MS, deadline - System.currentTimeMillis());

      if(budget > 0) {
         Optional<WSAssemblyModel> live = InFlightModelBuild.run(
            rws, assembly.getName(), () -> modelFactory.create(assembly, rws, principal, true),
            budget);

         if(live.isPresent()) {
            return live.get();
         }
      }

      LOG.warn("Table '{}' did not finish within the refresh budget; sending it without row " +
               "count and column infos", assembly.getName());
      return modelFactory.create(assembly, rws, principal, false);
   }

   /**
    * Builds a worksheet assembly's model; {@code liveMetadata} false keeps a table from being
    * executed. Replaceable only so a test can control how long a build takes.
    */
   @FunctionalInterface
   interface ModelFactory {
      WSAssemblyModel create(WSAssembly assembly, RuntimeWorksheet rws, Principal principal,
                             boolean liveMetadata)
         throws Exception;
   }

   void setModelFactory(ModelFactory modelFactory) {
      this.modelFactory = modelFactory;
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

   /**
    * How long one refresh broadcast may spend building table models live. Well under the 120 s the
    * Composer plugin waits for an edit, and well above what an ordinary worksheet needs. Non-final solely so a unit test can
    * shrink it.
    */
   static long BROADCAST_BUDGET_MS = 30_000L;

   /**
    * How long one table may take of {@link #BROADCAST_BUDGET_MS}, so a single slow table cannot
    * starve the tables after it. Non-final solely so a unit test can shrink it.
    */
   static long TABLE_BUDGET_MS = 15_000L;

   private volatile ModelFactory modelFactory = WSAssemblyModelFactory::createModelFrom;
   private final CommandDispatcherService commandDispatcherService;
   private final VSObjectModelFactoryService vsObjectModelFactoryService;
   private final VSBindingTreeService vsBindingTreeService;
   private final CoreLifecycleService coreLifecycleService;
   private final VSLayoutControllerServiceProxy vsLayoutControllerService;
}
