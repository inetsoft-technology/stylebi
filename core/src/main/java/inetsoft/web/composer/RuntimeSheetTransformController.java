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
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.*;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.RenameEventModel;
import inetsoft.web.composer.model.TransformFinishedEventModel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.*;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class RuntimeSheetTransformController implements MessageListener {
   @Autowired
   public RuntimeSheetTransformController(ViewsheetService viewsheetService,
                                          SimpMessagingTemplate messagingTemplate,
                                          RuntimeSheetTransformServiceProxy runtimeSheetTransformService)
   {
      this.viewsheetService = viewsheetService;
      this.messagingTemplate = messagingTemplate;
      this.runtimeSheetTransformService = runtimeSheetTransformService;
      clusterInstance = Cluster.getInstance();
   }

   @PostConstruct
   public void addListener() {
      clusterInstance.addMessageListener(this);
   }

   @PreDestroy
   public void removeListener() {
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

      if(event.getMessage() instanceof ViewsheetBookmarkChangedEvent bkChangedEvent) {
         AssetEntry asset = bkChangedEvent.getAssetEntry();
         String id = bkChangedEvent.rvsID;
         viewsheetService.updateBookmarks(asset);

         if(bkChangedEvent.deleted) {
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
      else if(event.getMessage() instanceof RenameTransformFinishedEvent renameEvent) {
         AssetObject asset = renameEvent.getEntry();
         RenameDependencyInfo dependencyInfo = renameEvent.getDependencyInfo();
         List<RenameInfo> infos = dependencyInfo.getRenameInfo(asset);

         if(asset instanceof AssetEntry) {
            handleMessageForAssets((AssetEntry) asset, infos, renameEvent.isReload());
         }
      }
      else if(event.getMessage() instanceof RenameSheetEvent renameEvent) {
         AssetObject asset = renameEvent.getEntry();

         if(asset instanceof AssetEntry) {
            handleSheetChangeMessageForWs((AssetEntry) asset, renameEvent.getRenameInfo());
         }
      }
      else if(event.getMessage() instanceof TransformAssetFinishedEvent finishedEvent) {
         handleAssetTransformFinished(finishedEvent);
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

      for(RuntimeSheet rs : sheets) {
         if(Tool.equals(rs.getEntry(), event.getEntry())) {
            sendTransformFinsihedMessage(rs.getID(), event.getOrgID());
         }
      }
   }

   private boolean isSameOrgId(String orgId, Principal principal) {
      return Objects.equals(orgId, OrganizationManager.getInstance().getCurrentOrgID(principal));
   }

   private void sendTransformFinsihedMessage(String id, String orgId) {
      for(Principal subscription : subscriptions.values()) {
         if(isSameOrgId(orgId, subscription)) {
            messagingTemplate.convertAndSendToUser(
               SUtil.getUserDestination(subscription), "/transform-finished",
               new TransformFinishedEventModel(id));
         }
      }
   }

   private void handleMessageForAssets(AssetEntry entry, List<RenameInfo> infos, boolean reload) {
      RuntimeSheet[] sheets = null;

      if(viewsheetService instanceof ViewsheetEngine engine) {
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

   private void handleMessageForBookmarks(AssetEntry entry, String id, String bookmark,
                                          boolean reload)
   {
      RuntimeViewsheet[] sheets = null;

      if(viewsheetService instanceof ViewsheetEngine engine) {
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

            for(Principal subscription : subscriptions.values()) {
               messagingTemplate.convertAndSendToUser(
                  SUtil.getUserDestination(subscription), "/dependency-changed", model);
            }
         });
   }

   private void handleRenameBookmark(AssetEntry entry, String id, String oname, String nname,
                                     IdentityID owner)
   {
      RuntimeViewsheet[] sheets = null;

      if(viewsheetService instanceof ViewsheetEngine engine) {
         if(entry.isViewsheet()) {
            sheets = engine.getAllRuntimeViewsheets();
         }
      }

      if(sheets == null || sheets.length == 0) {
         return;
      }

      for(RuntimeViewsheet sheet : sheets) {
         if(Tool.equals(entry, sheet.getEntry()) && !Tool.equals(sheet.getID(), id)) {
            if(sheet.getOpenedBookmark() != null &&
               Tool.equals(oname, sheet.getOpenedBookmark().getName()))
            {
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

      if(viewsheetService instanceof ViewsheetEngine engine) {

         if(entry.isWorksheet()) {
            sheets = engine.getAllRuntimeWorksheetSheets();
         }
      }

      if(sheets == null || sheets.length == 0) {
         return;
      }

      List<AssetEntry> entryList = new ArrayList<>();


      for(RuntimeSheet runtimeSheet : sheets) {
         entryList.add(runtimeSheet.getEntry());
      }

      List<AssetObject> depAssets = DependencyTransformer.getDependencies(entry.toIdentifier());

      if(entry.isWorksheet() && (depAssets.isEmpty() ||
         depAssets.stream().noneMatch(entryList::contains)))
      {

         Arrays.stream(sheets).filter(sheet -> isChangeInfo(sheet, renameInfo.getOldName()))
            .forEach(sheet -> {
               ArrayList<RenameInfo> renameInfos = new ArrayList<>();
               renameInfos.add(renameInfo);
               runtimeSheetTransformService.updateRenameInfos(sheet.getID(),
                                                              sheet.getEntry(), renameInfos);
               sendMessage(sheet.getID(), sheet.getEntry(), false);
            });
      }
   }

   private boolean isChangeInfo(RuntimeSheet sheet, String oldName) {
      Assembly[] allAssemblies = sheet.getSheet().getAssemblies();

      for(Assembly assembly : allAssemblies) {
         if(!(assembly instanceof MirrorAssembly massembly)) {
            continue;
         }

         if(massembly.getEntry() != null && Tool.equals(massembly.getEntry().toIdentifier(), oldName)) {
            return true;
         }
      }

      return false;
   }

   private void sendMessage(String id, AssetEntry entry, boolean reload) {
      if(entry == null) {
         return;
      }

      RenameEventModel model = RenameEventModel.builder()
         .id(id)
         .reload(reload)
         .entry(entry)
         .build();

      for(Principal subscription : subscriptions.values()) {
         if(isSameOrgId(entry.getOrgID(), subscription)) {
            messagingTemplate.convertAndSendToUser(
               SUtil.getUserDestination(subscription), "/dependency-changed", model);
         }
      }
   }

   @SubscribeMapping("/dependency-changed")
   public void subscribeToDependency(StompHeaderAccessor header, Principal principal) {
      final MessageHeaders messageHeaders = header.getMessageHeaders();
      final String subscriptionId =
         (String) messageHeaders.get(SimpMessageHeaderAccessor.SUBSCRIPTION_ID_HEADER);
      subscriptions.put(subscriptionId, principal);
   }

   @EventListener
   public void handleUnsubscribe(SessionUnsubscribeEvent event) {
      removeSubscription(event);
   }

   @EventListener
   public void handleDisconnect(SessionDisconnectEvent event) {
      removeSubscription(event);
   }

   private void removeSubscription(AbstractSubProtocolEvent event) {
      final Message<byte[]> message = event.getMessage();
      final MessageHeaders headers = message.getHeaders();
      final String subscriptionId =
         (String) headers.get(SimpMessageHeaderAccessor.SUBSCRIPTION_ID_HEADER);

      if(subscriptionId != null) {
         subscriptions.remove(subscriptionId);
      }
   }

   private final Cluster clusterInstance;
   private final ViewsheetService viewsheetService;
   private final SimpMessagingTemplate messagingTemplate;
   private final RuntimeSheetTransformServiceProxy runtimeSheetTransformService;
   private final Map<String, Principal> subscriptions = new ConcurrentHashMap<>();
}
