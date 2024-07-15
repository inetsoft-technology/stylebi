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
import { Component, EventEmitter, NgZone, OnChanges, Output, SimpleChanges } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Tool } from "../../../../../../../shared/util/tool";
import { AssemblyActionEvent } from "../../../../common/action/assembly-action-event";
import { HyperlinkModel } from "../../../../common/data/hyperlink-model";
import { ViewsheetClientService } from "../../../../common/viewsheet-client";
import { FixedDropdownService } from "../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { ContextProvider } from "../../../context-provider.service";
import { RichTextService } from "../../../dialog/rich-text-dialog/rich-text.service";
import { VSGaugeModel } from "../../../model/output/vs-gauge-model";
import { ShowHyperlinkService } from "../../../show-hyperlink.service";
import { DataTipService } from "../../data-tip/data-tip.service";
import { PopComponentService } from "../../data-tip/pop-component.service";
import { AbstractImageComponent } from "../abstract-image";

@Component({
   selector: "vs-gauge",
   templateUrl: "vs-gauge.component.html",
   styleUrls: ["vs-gauge.component.scss"]
})
export class VSGauge extends AbstractImageComponent<VSGaugeModel> implements OnChanges {
   @Output() onOpenFormatPane = new EventEmitter<VSGaugeModel>();
   src: string = null;

   constructor(viewsheetClient: ViewsheetClientService,
               modalService: NgbModal,
               dropdownService: FixedDropdownService,
               contextProvider: ContextProvider,
               zone: NgZone,
               protected dataTipService: DataTipService,
               private popComponentService: PopComponentService,
               protected hyperlinkService: ShowHyperlinkService,
               richTextService: RichTextService)
   {
      super(viewsheetClient, modalService, dropdownService, zone, contextProvider,
            hyperlinkService, dataTipService, richTextService);
   }

   ngOnChanges(changes: SimpleChanges): void {
      if((changes.hasOwnProperty("vsInfo") || changes.hasOwnProperty("model")) &&
         this.vsInfo != null)
      {
         const src = this.getSrc();

         if(this.src !== src) {
            this.loading = true;
         }

         this.src = src;
      }
   }

   getWidth(includeMargin: boolean = true): number {
      let width: number = this.model.objectFormat.width;

      if(!includeMargin) {
         width -= Tool.getMarginSize(this.model.objectFormat.border.left);
         width -= Tool.getMarginSize(this.model.objectFormat.border.right);
         width -= this.model.paddingLeft + this.model.paddingRight;
      }

      return width;
   }

   getHeight(includeMargin: boolean = true): number {
      let height = this.model.objectFormat.height;

      if(!includeMargin) {
         height -= Tool.getMarginSize(this.model.objectFormat.border.top);
         height -= Tool.getMarginSize(this.model.objectFormat.border.bottom);
         height -= this.model.paddingTop + this.model.paddingBottom;
      }

      return height;
   }

   getSrc(): string {
      return this.vsInfo.linkUri + "getAssemblyImage" +
         "/" + Tool.byteEncode(this.viewsheetClient.runtimeId) +
         "/" + Tool.byteEncode(this.model.absoluteName) +
         "/" + this.getWidth(false) +
         "/" + this.getHeight(false) +
         "?" + this.model.genTime;
   }

   protected getHyperlinks(): HyperlinkModel[] {
      return this.model.hyperlinks;
   }

   getTooltip(): string {
      let tooltip = "";

      if(this.model.tooltipVisible) {
         if(!!this.model.customTooltipString) {
            tooltip = this.model.customTooltipString;
         }
         else if(!!this.model.hyperlinks && this.model.hyperlinks.length > 0) {
            tooltip = this.model.hyperlinks[0].tooltip ? this.model.hyperlinks[0].tooltip : "";
         }
         else if(!!this.model.defaultAnnotationContent) {
            tooltip = this.model.defaultAnnotationContent;
         }
      }

      return tooltip;
   }

   protected onAssemblyActionEvent(event: AssemblyActionEvent<VSGaugeModel>): void {
      switch(event.id) {
      case "gauge annotate":
         this.showAnnotationDialog(event.event);
         break;
      case "gauge show-hyperlink":
         this.hyperlinkService.showHyperlinks(event.event,
                             this.getHyperlinks(), this.dropdownService,
                             this.viewsheetClient.runtimeId, this.vsInfo.linkUri,
                             this.isForceTab());
         break;
      case "gauge show-format-pane":
         this.onOpenFormatPane.emit(this.model);
         break;
      }
   }

   getOpacity(): number {
      // apply data tip alpha on image itself if it's a data tip
      return (this.dataTipService.isDataTip(this.model.absoluteName) ||
              this.popComponentService.isPopComponent(this.model.absoluteName)) &&
         (this.contextProvider.preview || this.contextProvider.viewer)
         ? this.model.objectFormat.alpha : 1;
   }

   isForceTab(): boolean {
      return this.contextProvider.composer;
   }
}
