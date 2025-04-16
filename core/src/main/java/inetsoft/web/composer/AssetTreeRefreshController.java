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

import inetsoft.report.LibManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.Debouncer;
import inetsoft.util.DefaultDebouncer;
import inetsoft.web.composer.model.AssetChangeEventModel;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.security.Principal;
import java.util.concurrent.TimeUnit;

@Controller
@Scope(value = "websocket", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class AssetTreeRefreshController {
   @Autowired
   public void setAssetRepository(AssetRepository assetRepository) {
      this.assetRepository = assetRepository;
   }

   @Autowired
   public void setMessagingTemplate(SimpMessagingTemplate messagingTemplate) {
      this.messagingTemplate = messagingTemplate;
   }

   @PreDestroy
   public void preDestroy() throws Exception {
      assetRepository.removeAssetChangeListener(listener);
      LibManager.getManager().removeActionListener(libraryListener);
      this.debouncer.close();
   }

   @SubscribeMapping("/asset-changed")
   public void subscribeToTopic(Principal principal) {
      destination = SUtil.getUserDestination(principal);
      assetRepository.addAssetChangeListener(listener);
      LibManager.getManager().addActionListener(libraryListener);
      AssetRepository runtimeAssetRepository = AssetUtil.getAssetRepository(false);

      if(runtimeAssetRepository != null && runtimeAssetRepository != assetRepository) {
         runtimeAssetRepository.addAssetChangeListener(listener);
      }

      DataSourceRegistry registry = DataSourceRegistry.getRegistry();

      if(principal == null) {
         return;
      }

      final String orgId = ((XPrincipal) principal).getOrgId();
      registry.addRefreshedListener(event -> {
         // Data Source Entry
         AssetEntry entry = AssetEntry.createAssetEntry("0^65605^__NULL__^/^" + orgId);
         AssetChangeEventModel eventModel = AssetChangeEventModel.builder()
            .parentEntry(entry)
            .oldIdentifier(null)
            .newIdentifier(entry.toIdentifier())
            .build();
         messagingTemplate.convertAndSendToUser(destination, "/asset-changed", eventModel);
      });
   }

   private AssetRepository assetRepository;
   private SimpMessagingTemplate messagingTemplate;
   private String destination;
   private final AssetChangeListener listener = new AssetChangeListener() {
      @Override
      public void assetChanged(AssetChangeEvent event) {
         if(isConnectionInitialized() && canSendEvent(event)) {
            AssetChangeEventModel eventModel = AssetChangeEventModel.builder()
               .parentEntry(event.getAssetEntry().getParent())
               .oldIdentifier(event.getOldName())
               .newIdentifier(event.getAssetEntry().toIdentifier())
               .build();

            debouncer.debounce("change" + (event.getAssetEntry().getParent() != null ?
               event.getAssetEntry().getParent().toIdentifier() : ""), 2, TimeUnit.SECONDS, () ->
               messagingTemplate.convertAndSendToUser(destination, "/asset-changed", eventModel)
            );
         }
      }

      private boolean canSendEvent(AssetChangeEvent event) {
         return event.isRoot() && event.getChangeType() != AssetChangeEvent.AUTO_SAVE_ADD &&
            (event.getChangeType() != AssetChangeEvent.ASSET_TO_BE_DELETED &&
               event.getAssetEntry().getParent() != null ||
               event.getChangeType() == AssetChangeEvent.ASSET_RENAMED);
      }

      private boolean isConnectionInitialized() {
         return messagingTemplate != null && destination != null;
      }
   };

   private final ActionListener libraryListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
         int changeType = event.getID();
         AssetChangeEventModel eventModel = null;

         if(changeType == LibManager.SCRIPT_ADDED || changeType == LibManager.SCRIPT_REMOVED ||
            changeType == LibManager.SCRIPT_MODIFIED)
         {
            AssetEntry scriptRootEntry = new AssetEntry(AssetRepository.COMPONENT_SCOPE,
                                                        AssetEntry.Type.SCRIPT_FOLDER, "/" + SCRIPT, null);

            eventModel = AssetChangeEventModel.builder()
               .parentEntry(scriptRootEntry)
               .oldIdentifier(null)
               .newIdentifier(scriptRootEntry.toIdentifier())
               .build();
         }

         if(changeType == LibManager.STYLE_ADDED || changeType == LibManager.STYLE_REMOVED ||
            changeType == LibManager.STYLE_MODIFIED)
         {
            AssetEntry scriptRootEntry = new AssetEntry(AssetRepository.COMPONENT_SCOPE,
                                                        AssetEntry.Type.TABLE_STYLE_FOLDER, "/" + TABLE_STYLE, null);

            eventModel = AssetChangeEventModel.builder()
               .parentEntry(scriptRootEntry)
               .oldIdentifier(null)
               .newIdentifier(scriptRootEntry.toIdentifier())
               .build();
         }

         if(eventModel != null) {
            messagingTemplate.convertAndSendToUser(destination, "/asset-changed", eventModel);
         }
      }
   };

   final private Debouncer<String> debouncer = new DefaultDebouncer<>(false);
   private static final String TABLE_STYLE = "Table Style";
   private static final String SCRIPT = "Script Function";
}
