/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.composer;

import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeSheet;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.*;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.RenameEventModel;
import inetsoft.web.composer.model.TransformFinishedEventModel;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.*;

@Controller
@Scope(value = "websocket", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RuntimeSheetTransformController implements MessageListener {
   @Autowired
   public RuntimeSheetTransformController(AnalyticRepository repository,
                                          ViewsheetService viewsheetService,
                                          SimpMessagingTemplate messagingTemplate)
   {
      this.repository = repository;
      this.viewsheetService = viewsheetService;
      this.messagingTemplate = messagingTemplate;
      clusterInstance = Cluster.getInstance();
   }

   @PreDestroy
   public void preDestroy() {
      clusterInstance.removeMessageListener(this);
   }

   /**
    * After finished transform the asset document which caused by source rename, send message to
    * gui to popup confirm dialog for user, to support transform the editing runtime sheet and
    * reload to avoid the errors in further editing.
    * @param event the event.
    */
   public void messageReceived(MessageEvent event) {
      if(event == null) {
         return;
      }

      if(event.getMessage() instanceof ViewsheetBookmarkChangedEvent) {
         AssetEntry asset = ((ViewsheetBookmarkChangedEvent) event.getMessage()).getAssetEntry();
         String id = ((ViewsheetBookmarkChangedEvent) event.getMessage()).rvsID;
         viewsheetService.updateBookmarks(asset);

         if(((ViewsheetBookmarkChangedEvent) event.getMessage()).deleted) {
            String bookmark = ((ViewsheetBookmarkChangedEvent) event.getMessage()).bookmark;
            handleMessageForBookmarks(asset, id, bookmark, true);
         }
         else if(((ViewsheetBookmarkChangedEvent) event.getMessage()).getOldBookmark() != null) {
            String oname = ((ViewsheetBookmarkChangedEvent) event.getMessage()).getOldBookmark();
            String nname = ((ViewsheetBookmarkChangedEvent) event.getMessage()).getBookmark();
            IdentityID owner = ((ViewsheetBookmarkChangedEvent) event.getMessage()).getOwner();
            handleRenameBookmark(asset, id, oname, nname, owner);
         }
      }
      else if(event.getMessage() instanceof RenameTransformFinishedEvent) {
         RenameTransformFinishedEvent renameEvent = (RenameTransformFinishedEvent) event.getMessage();
         AssetObject asset = renameEvent.getEntry();
         RenameDependencyInfo dependencyInfo = renameEvent.getDependencyInfo();
         List<RenameInfo> infos = dependencyInfo.getRenameInfo(asset);

         if(asset instanceof AssetEntry) {
            handleMessageForAssets((AssetEntry) asset, infos, renameEvent.isReload());
         }
      }
      else if(event.getMessage() instanceof RenameSheetEvent) {
         RenameSheetEvent renameEvent = (RenameSheetEvent) event.getMessage();
         AssetObject asset = renameEvent.getEntry();

         if(asset instanceof AssetEntry) {
            handleSheetChangeMessageForWs((AssetEntry) asset, renameEvent.getRenameInfo());
         }
      }
      else if(event.getMessage() instanceof TransformAssetFinishedEvent) {
         handleAssetTransformFinished((TransformAssetFinishedEvent) event.getMessage());
      }
   }

   private void handleAssetTransformFinished(TransformAssetFinishedEvent event) {
      ViewsheetEngine engine = (ViewsheetEngine) viewsheetService;
      AssetEntry entry = event.getEntry();
      RuntimeSheet[] sheets = null;

      if(entry.isWorksheet()) {
         sheets = engine.getAllRuntimeWorksheetSheets();
      }
      else if(entry.isViewsheet()) {
         sheets = engine.getAllRuntimeViewsheets();
      }

      if(sheets == null) {
         return;
      }

      for(int i = 0; i < sheets.length; i++) {
         RuntimeSheet rs = sheets[i];

         if(Tool.equals(rs.getEntry(), event.getEntry())); {
            sendTransformFinsihedMessage(rs.getID());
         }
      }
   }

   private void sendTransformFinsihedMessage(String id) {
      messagingTemplate.convertAndSendToUser(destination, "/transform-finished",
         new TransformFinishedEventModel(id));
   }

   private void handleMessageForAssets(AssetEntry entry, List<RenameInfo> infos, boolean reload) {
      RuntimeSheet[] sheets = null;

      if(viewsheetService instanceof ViewsheetEngine) {
         ViewsheetEngine engine = (ViewsheetEngine) viewsheetService;

         if(entry.isWorksheet()) {
            sheets = engine.getAllRuntimeWorksheetSheets();
         }
         else if(entry.isViewsheet()) {
            sheets = engine.getAllRuntimeViewsheets();
         }
      }

      if(sheets == null || sheets.length == 0) {
         return;
      }

      Arrays.stream(sheets)
              .filter(sheet -> Tool.equals(entry, sheet.getEntry()))
              .forEach(sheet -> {
                 viewsheetService.updateRenameInfos(sheet.getID(), entry, infos);
                 sendMessage(sheet.getID(), entry, reload);
              });
   }

   private void handleMessageForBookmarks(AssetEntry entry, String id, String bookmark, boolean reload) {
      RuntimeViewsheet[] sheets = null;

      if(viewsheetService instanceof ViewsheetEngine) {
         ViewsheetEngine engine = (ViewsheetEngine) viewsheetService;

         if(entry.isViewsheet()) {
            sheets = engine.getAllRuntimeViewsheets();
         }
      }

      if(sheets == null || sheets.length == 0) {
         return;
      }

      Arrays.stream(sheets)
         .filter(sheet -> Tool.equals(entry, sheet.getEntry()) && !Tool.equals(sheet.getID(), id))
         .filter(sheet -> sheet.getOpenedBookmark() != null &&
            Tool.equals(sheet.getOpenedBookmark().getName(), bookmark))
         .forEach(sheet -> {
            RenameEventModel model = RenameEventModel.builder()
               .id(sheet.getID())
               .bookmark(bookmark)
               .reload(reload)
               .entry(entry)
               .build();
            messagingTemplate.convertAndSendToUser(destination, "/dependency-changed", model);
         });
   }

   private void handleRenameBookmark(AssetEntry entry, String id, String oname, String nname,
                                          IdentityID owner) {
      RuntimeViewsheet[] sheets = null;

      if(viewsheetService instanceof ViewsheetEngine) {
         ViewsheetEngine engine = (ViewsheetEngine) viewsheetService;

         if(entry.isViewsheet()) {
            sheets = engine.getAllRuntimeViewsheets();
         }
      }

      if(sheets == null || sheets.length == 0) {
         return;
      }

      for(int i = 0; i < sheets.length; i++) {
         RuntimeViewsheet sheet = sheets[i];

         if(Tool.equals(entry, sheet.getEntry()) && !Tool.equals(sheet.getID(), id)) {
            if(Tool.equals(oname, sheet.getOpenedBookmark().getName())) {
               sheet.setOpenedBookmark(sheet.getBookmarkInfo(nname, owner));
            }
         }
      }
   }

   /**
    * Update the runtime ws rename infos when the changed sheet is dependency of it
    * but the dependency relation is not be saved.
    *
    * @param entry changed sheet.
    * @param renameInfo changed sheet rename info.
    */
   private void handleSheetChangeMessageForWs(AssetEntry entry, RenameInfo renameInfo) {
      RuntimeSheet[] sheets = null;

      if(viewsheetService instanceof ViewsheetEngine) {
         ViewsheetEngine engine = (ViewsheetEngine) viewsheetService;

         if(entry.isWorksheet()) {
            sheets = engine.getAllRuntimeWorksheetSheets();
         }
      }

      if(sheets == null || sheets.length == 0) {
         return;
      }

      List<AssetEntry> entryList = new ArrayList<>();


      for(int i = 0; i < sheets.length; i++) {
         entryList.add(sheets[i].getEntry());
      }

      List<AssetObject> depAssets = DependencyTransformer.getDependencies(entry.toIdentifier());

      if(entry.isWorksheet() && (depAssets.isEmpty() ||
         !depAssets.stream().anyMatch(depAsset -> entryList.contains(depAsset)))) {

         Arrays.stream(sheets).filter(sheet -> isChangeInfo(sheet, renameInfo.getOldName()))
            .forEach(sheet -> {
               ArrayList<RenameInfo> renameInfos = new ArrayList<>();
               renameInfos.add(renameInfo);
               viewsheetService.updateRenameInfos(sheet.getID(), sheet.getEntry(), renameInfos);
               sendMessage(sheet.getID(), sheet.getEntry(), false);
            });
      }
   }

   private boolean isChangeInfo(RuntimeSheet sheet, String oldName) {
      Assembly[] allAssemblies = sheet.getSheet().getAssemblies();

      for(Assembly assembly : allAssemblies) {
         if(!(assembly instanceof MirrorAssembly)) {
            continue;
         }

         MirrorAssembly massembly = (MirrorAssembly) assembly;

         if(Tool.equals(massembly.getEntry().toIdentifier(), oldName)) {
            return true;
         }
      }

      return false;
   }

   private void sendMessage(String id, AssetEntry entry, boolean reload) {
      RenameEventModel model = RenameEventModel.builder()
         .id(id)
         .reload(reload)
         .entry(entry)
         .build();
      messagingTemplate.convertAndSendToUser(destination, "/dependency-changed", model);
   }

   @SubscribeMapping("/dependency-changed")
   public void subscribeToDependency(Principal principal) {
      destination = SUtil.getUserDestination(principal);
      clusterInstance.addMessageListener(this);
   }

   private String destination;
   private Cluster clusterInstance;
   private AnalyticRepository repository;
   private ViewsheetService viewsheetService;
   private SimpMessagingTemplate messagingTemplate;
}
