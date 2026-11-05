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
package inetsoft.web.composer.vs.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.cluster.*;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.sree.internal.SUtil;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.util.log.LogLevel;
import inetsoft.web.AutoSaveUtils;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.dialog.ModernizeDialogService;
import inetsoft.web.composer.vs.dialog.SaveViewsheetDialogService;
import inetsoft.web.composer.ws.event.SaveSheetEvent;
import inetsoft.web.viewsheet.command.*;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Saves the focused dashboard as a new asset reset to modern defaults, never writing the original.
 */
@Service
@ClusterProxy
public class ModernCopyService {
   public ModernCopyService(ViewsheetService viewsheetService,
                            CoreLifecycleService coreLifecycleService,
                            AssetRepository assetRepository,
                            ModernizeDialogService modernizeDialogService,
                            SaveViewsheetDialogService saveViewsheetDialogService,
                            ComposerViewsheetService composerViewsheetService)
   {
      this.viewsheetService = viewsheetService;
      this.coreLifecycleService = coreLifecycleService;
      this.assetRepository = assetRepository;
      this.modernizeDialogService = modernizeDialogService;
      this.saveViewsheetDialogService = saveViewsheetDialogService;
      this.composerViewsheetService = composerViewsheetService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   @ClusterWriteMethod
   public Void createModernCopy(@ClusterProxyKey String runtimeId, String name,
                                Principal principal, CommandDispatcher dispatcher, String linkUri)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      Catalog catalog = Catalog.getCatalog(principal);
      AssetEntry original = rvs.getEntry();

      if(!VSDensityDefaults.isModern() || rvs.getViewsheet() == null) {
         throw refused(catalog.getString("common.report.binding.unsupportedOperation"));
      }

      if(original == null || original.getScope() == AssetRepository.TEMPORARY_SCOPE) {
         throw refused(catalog.getString("composer.vs.modernize.reset.neverSaved"));
      }

      // the save drops control characters, so check the name it will write
      String copyName = name == null ? "" : SUtil.removeControlChars(name).trim();
      checkName(runtimeId, copyName, principal, catalog);

      Viewsheet before = rvs.getViewsheet().clone();

      // a failure could not put the sheet back
      if(before == null) {
         throw refused(catalog.getString("internal.error"));
      }

      Snapshot snapshot = new Snapshot(original, before, rvs.getSavePoint(), rvs.isEditable());
      AssetEntry parent = original.getParent();
      SaveViewsheetDialogModel model =
         saveViewsheetDialogService.getSaveViewsheetInfo(runtimeId, principal);
      model.setName(copyName);
      model.setParentId(parent == null ? null : parent.toIdentifier());
      model.setUpdateDepend(false);
      // lists the copy under its typed name
      model.getViewsheetOptionsPaneModel().setAlias(null);
      // the client learns of the copy only once it is complete
      CommandDispatcher saveDispatcher = new WithheldSaves(dispatcher);

      try {
         saveViewsheetDialogService.saveViewsheet(runtimeId, model, linkUri, principal,
                                                  saveDispatcher);
      }
      catch(Exception | Error ex) {
         AssetEntry saved = rvs.getEntry();

         // the runtime moves only after the copy is written
         if(saved != null && !saved.equals(original)) {
            restoreOriginal(rvs, snapshot, saved, principal, dispatcher, linkUri);
         }
         else {
            // Save As marks the runtime editable first
            rvs.setEditable(snapshot.editable());
         }

         throw ex;
      }

      AssetEntry copy = rvs.getEntry();

      // every later save writes the runtime's entry, which must not be the original
      if(copy == null || copy.equals(original)) {
         rvs.setEditable(snapshot.editable());
         throw new IllegalStateException("Save As left the runtime on " + original);
      }

      Viewsheet vs = rvs.getViewsheet();

      try {
         // the save copies the saver's bookmarks of the original
         assetRepository.clearVSBookmark(copy);
         VizModernizeUtil.resetToModern(vs);
         vs.getViewsheetInfo().setModernCopyOf(original.toIdentifier());
         composerViewsheetService.saveViewsheet(runtimeId, new SaveSheetEvent(), principal,
                                                saveDispatcher, linkUri);
      }
      catch(Exception | Error ex) {
         restoreOriginal(rvs, snapshot, copy, principal, dispatcher, linkUri);
         throw ex;
      }

      try {
         // the unsaved changes went into the copy
         AutoSaveUtils.deleteAutoSaveFile(original, principal);
         markSaved(rvs, dispatcher);

         ChangedAssemblyList clist =
            coreLifecycleService.createList(false, dispatcher, rvs, linkUri);
         coreLifecycleService.setViewsheetInfo(rvs, linkUri, dispatcher);
         coreLifecycleService.refreshViewsheet(rvs, rvs.getID(), linkUri, dispatcher, false,
                                               false, true, clist);
      }
      finally {
         // the runtime is on the copy even if the refresh failed
         dispatcher.sendCommand(SaveSheetCommand.builder()
                                   .savePoint(rvs.getSavePoint())
                                   .id(copy.toIdentifier())
                                   .build());
      }

      dispatcher.sendCommand(createdMessage(copy.getName(), scan(vs), catalog));
      return null;
   }

   private void checkName(String runtimeId, String name, Principal principal, Catalog catalog)
      throws Exception
   {
      if(name.isEmpty()) {
         throw refused(catalog.getString("designer.common.nameFieldCannotBeEmpty"));
      }

      ModernCopyRequest request = new ModernCopyRequest();
      request.setName(name);
      ModernCopyNameCheck check = modernizeDialogService.checkName(runtimeId, request, principal);

      if(check.getInvalidName() != null) {
         throw refused(check.getInvalidName());
      }

      if(check.getPermissionDenied() != null && !check.getPermissionDenied().isEmpty()) {
         throw refused(check.getPermissionDenied());
      }

      if(check.isExists()) {
         throw refused(catalog.getString("composer.vs.modernize.nameExists"));
      }
   }

   /**
    * Adds the reset as one undo step that is also the copy's save point.
    */
   private void markSaved(RuntimeViewsheet rvs, CommandDispatcher dispatcher) {
      rvs.addCheckpoint(rvs.getSheet().prepareCheckpoint());
      rvs.setSavePoint(rvs.getCurrent());

      UpdateUndoStateCommand command = new UpdateUndoStateCommand();
      command.setPoints(rvs.size());
      command.setCurrent(rvs.getCurrent());
      command.setSavePoint(rvs.getSavePoint());
      command.setId(rvs.getID());
      dispatcher.sendCommand(command);
   }

   /**
    * Deletes the copy and puts the runtime back on the original, each step independent.
    */
   private void restoreOriginal(RuntimeViewsheet rvs, Snapshot snapshot, AssetEntry copy,
                                Principal principal, CommandDispatcher dispatcher, String linkUri)
   {
      AssetEntry original = snapshot.entry();

      try {
         assetRepository.removeSheet(copy, principal, true);
      }
      catch(Exception ex) {
         LOG.warn("Failed to delete the modern copy {}", copy, ex);
      }

      try {
         rvs.setEntry(original);
      }
      catch(Exception ex) {
         LOG.warn("Failed to restore the entry of {}", original, ex);
      }

      try {
         rvs.setViewsheet(snapshot.sheet());
      }
      catch(Exception ex) {
         LOG.warn("Failed to restore the sheet of {}", original, ex);
      }

      rvs.setSavePoint(snapshot.savePoint());
      // an editable runtime counts as a holder of the original's lock
      rvs.setEditable(snapshot.editable());

      // the saves' info commands named the copy; no save command reached the client
      try {
         coreLifecycleService.setViewsheetInfo(rvs, linkUri, dispatcher);
      }
      catch(Exception ex) {
         LOG.warn("Failed to send the restored state of {}", original, ex);
      }
   }

   private static MessageCommand createdMessage(String copyName,
                                                ScriptFormattingScanner.Report report,
                                                Catalog catalog)
   {
      MessageCommand command = new MessageCommand();

      if(!report.components().isEmpty()) {
         String key = report.dashboardScript() ?
            "composer.vs.modernize.copyCreated.scriptsWithDashboard" :
            "composer.vs.modernize.copyCreated.scripts";
         command.setMessage(catalog.getString(key, copyName,
                                              String.join(", ", report.components())));
         command.setType(MessageCommand.Type.INFO);
      }
      else if(report.dashboardScript()) {
         command.setMessage(catalog.getString("composer.vs.modernize.copyCreated", copyName) +
                               " " +
                               catalog.getString("composer.vs.modernize.scripts.dashboardOnly"));
         command.setType(MessageCommand.Type.INFO);
      }
      else {
         command.setMessage(catalog.getString("composer.vs.modernize.copyCreated", copyName));
         command.setType(MessageCommand.Type.OK);
      }

      return command;
   }

   private static ScriptFormattingScanner.Report scan(Viewsheet vs) {
      try {
         return ScriptFormattingScanner.scan(vs);
      }
      catch(Exception ex) {
         LOG.warn("Failed to scan the dashboard scripts", ex);
         return new ScriptFormattingScanner.Report(new TreeSet<>(), false);
      }
   }

   private static MessageException refused(String message) {
      return new MessageException(message, LogLevel.DEBUG, false);
   }

   /**
    * The runtime state a failed copy puts back.
    */
   private record Snapshot(AssetEntry entry, Viewsheet sheet, int savePoint, boolean editable) {
   }

   /**
    * Passes every command to the target except {@link SaveSheetCommand}.
    */
   private static final class WithheldSaves extends CommandDispatcher {
      // the base state is unused, every method delegates
      WithheldSaves(CommandDispatcher target) {
         super(StompHeaderAccessor.create(StompCommand.MESSAGE), null, null);
         this.target = target;
      }

      @Override
      public void sendCommand(ViewsheetCommand command) {
         if(!(command instanceof SaveSheetCommand)) {
            target.sendCommand(command);
         }
      }

      @Override
      public void sendCommand(String assemblyName, ViewsheetCommand command) {
         if(!(command instanceof SaveSheetCommand)) {
            target.sendCommand(assemblyName, command);
         }
      }

      @Override
      public void flush() {
         target.flush();
      }

      @Override
      public Iterator<Command> iterator() {
         return target.iterator();
      }

      @Override
      public Stream<Command> stream() {
         return target.stream();
      }

      @Override
      public String getSharedHint() {
         return target.getSharedHint();
      }

      @Override
      public void setSharedHint(String sharedHint) {
         target.setSharedHint(sharedHint);
      }

      @Override
      public CommandDispatcher detach() {
         return new WithheldSaves(target.detach());
      }

      @Override
      public String getSessionId() {
         return target.getSessionId();
      }

      @Override
      public String getUserName() {
         return target.getUserName();
      }

      private final CommandDispatcher target;
   }

   private final ViewsheetService viewsheetService;
   private final CoreLifecycleService coreLifecycleService;
   private final AssetRepository assetRepository;
   private final ModernizeDialogService modernizeDialogService;
   private final SaveViewsheetDialogService saveViewsheetDialogService;
   private final ComposerViewsheetService composerViewsheetService;
   private static final Logger LOG = LoggerFactory.getLogger(ModernCopyService.class);
}
