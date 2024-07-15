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
import { Component, EventEmitter, HostListener, OnInit, Output } from "@angular/core";
import { DragEvent } from "../../../common/data/drag-event";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { AssetTreeService } from "../../../widget/asset-tree/asset-tree.service";
import { DragService } from "../../../widget/services/drag.service";
import { OpenSheetEvent } from "../../data/open-sheet-event";
import { ModelService } from "../../../widget/services/model.service";
import { FormatInfoModel } from "../../../common/data/format-info-model";
import { ComposerCustomMessageModel } from "../../data/composer-custom-message-model";
import { ComposerRecentService } from "../composer-recent.service";
import { OpenLibraryAssetEvent } from "../../data/open-libraryAsset-event";
import { GuiTool } from "../../../common/util/gui-tool";

const COMPOSER_CUSTOM_MESSAGE_URI: string = "../api/composer/customMessage";

@Component({
   selector: "composer-empty-editor",
   templateUrl: "composer-empty-editor.component.html",
   styleUrls: ["composer-empty-editor.component.scss"]
})
export class ComposerEmptyEditor implements OnInit {
   @Output() onOpenSheet = new EventEmitter<OpenSheetEvent>();
   @Output() onOpenLibraryAsset = new EventEmitter<OpenLibraryAssetEvent>();
   customMessageModel: ComposerCustomMessageModel;

   constructor(private dragService: DragService, private modelService: ModelService,
               private composerRecentService: ComposerRecentService)
   {
   }

   get createVSMessage(): string {
      if(!!this.customMessageModel?.viewsheetCreateMessage &&
         this.customMessageModel.viewsheetCreateMessage.trim().length > 0)
      {
         return this.customMessageModel?.viewsheetCreateMessage;
      }

      return "_#(js:common.createViewsheet)";
   }

   get editVSMessage(): string {
      if(!!this.customMessageModel?.viewsheetEditMessage &&
         this.customMessageModel?.viewsheetEditMessage.trim().length > 0)
      {
         return this.customMessageModel?.viewsheetEditMessage;
      }

      return "_#(js:common.editViewsheet)";
   }

   get createWSMessage(): string {
      if(!!this.customMessageModel?.worksheetCreateMessage &&
         this.customMessageModel.worksheetCreateMessage.trim().length >= 0)
      {
         return this.customMessageModel?.worksheetCreateMessage;
      }

      return "_#(js:common.createWorksheet)";
   }

   get editWSMessage(): string {
      if(!!this.customMessageModel?.worksheetEditMessage &&
         this.customMessageModel?.worksheetEditMessage.trim().length > 0)
      {
         return this.customMessageModel?.worksheetEditMessage;
      }

      return "_#(js:common.editWorksheet)";
   }

   ngOnInit(): void {
      this.modelService.getModel<ComposerCustomMessageModel>(COMPOSER_CUSTOM_MESSAGE_URI).subscribe((data) => {
         this.customMessageModel = data;
      });
   }

   /**
    * Attempt to drop sheet(s) into this component.
    *
    * @param event
    */
   @HostListener("drop", ["$event"])
   public onDrop(event: DragEvent): void {
      event.preventDefault();
      const dragData = this.dragService.getDragData();
      const worksheets = dragData[AssetTreeService.getDragName(AssetType.WORKSHEET)];
      const viewsheets = dragData[AssetTreeService.getDragName(AssetType.VIEWSHEET)];
      const scripts = dragData[AssetTreeService.getDragName(AssetType.SCRIPT)];
      const tableStyle = dragData[AssetTreeService.getDragName(AssetType.TABLE_STYLE)];

      if(!!worksheets) {
         const worksheetAssets: AssetEntry[] = JSON.parse(worksheets);

         for(const worksheetAsset of worksheetAssets) {
            this.onOpenSheet.emit({
               type: "worksheet",
               assetId: worksheetAsset.identifier,
            });
            this.composerRecentService.addRecentlyViewed(worksheetAsset);
         }
      }

      if(!!viewsheets) {
         const viewsheetAssets: AssetEntry[] = JSON.parse(viewsheets);

         for(let viewsheetAsset of viewsheetAssets) {
            this.onOpenSheet.emit({type: "viewsheet", assetId: viewsheetAsset.identifier});
            this.composerRecentService.addRecentlyViewed(viewsheetAsset);
         }
      }

      if(!!scripts) {
         const scriptsAssets: AssetEntry[] = JSON.parse(scripts);

         for(let scriptAsset of scriptsAssets) {
            this.onOpenLibraryAsset.emit({type: "script", assetId: scriptAsset.identifier});
            this.composerRecentService.addRecentlyViewed(scriptAsset);
         }
      }

      if(!!tableStyle) {
         const tableAssets: AssetEntry[] = JSON.parse(tableStyle);

         for(let tableAsset of tableAssets) {
            this.onOpenLibraryAsset.emit({type: "tableStyle", assetId: tableAsset.identifier,
               styleId: tableAsset.properties["styleID"]});
            this.composerRecentService.addRecentlyViewed(tableAsset);
         }
      }

      GuiTool.clearDragImage();
   }
}
