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
import {
   ChangeDetectorRef,
   Component,
   EventEmitter,
   Input,
   NgZone,
   OnChanges,
   OnDestroy,
   OnInit,
   Output,
   SimpleChange,
   SimpleChanges
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { AssemblyActionEvent } from "../../../../common/action/assembly-action-event";
import { Dimension } from "../../../../common/data/dimension";
import { HyperlinkModel } from "../../../../common/data/hyperlink-model";
import { Tool } from "../../../../../../../shared/util/tool";
import { ViewsheetClientService } from "../../../../common/viewsheet-client";
import { FixedDropdownService } from "../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DebounceService } from "../../../../widget/services/debounce.service";
import { ContextProvider } from "../../../context-provider.service";
import { RichTextService } from "../../../dialog/rich-text-dialog/rich-text.service";
import { VSImageModel } from "../../../model/output/vs-image-model";
import { ShowHyperlinkService } from "../../../show-hyperlink.service";
import { DataTipService } from "../../data-tip/data-tip.service";
import { PopComponentService } from "../../data-tip/pop-component.service";
import { AbstractImageComponent } from "../abstract-image";

@Component({
   selector: "vs-image",
   templateUrl: "vs-image.component.html",
   styleUrls: ["vs-image.component.scss"]
})
export class VSImage extends AbstractImageComponent<VSImageModel>
   implements OnInit, OnChanges, OnDestroy
{
   @Input() layoutRegion: string = "CONTENT";
   @Input() layoutName: string;
   @Output() onOpenFormatPane = new EventEmitter<VSImageModel>();
   imageSize = new Dimension(null, null);
   tooltip: string = "";
   opacity: any;
   src: string = "";
   oldSrc: string = "";

   constructor(protected viewsheetClient: ViewsheetClientService,
               private popComponentService: PopComponentService,
               modalService: NgbModal,
               dropdownService: FixedDropdownService,
               zone: NgZone,
               contextProvider: ContextProvider,
               protected dataTipService: DataTipService,
               private debounceService: DebounceService,
               private changeRef: ChangeDetectorRef,
               protected hyperlinkService: ShowHyperlinkService,
               richTextService: RichTextService)
   {
      super(viewsheetClient, modalService, dropdownService, zone,
            contextProvider, hyperlinkService, dataTipService, richTextService);
   }

   ngOnInit() {
      this.modelChanged();
      this.opacity = this.getOpacity();
      this.src = this.getSrc();

      if(this.model.scaleInfo.tiled && this.loading) {
         this.finishLoad();
      }
   }

   ngOnChanges(simpleChanges: SimpleChanges) {
      this.modelChanged(simpleChanges.model);
   }

   ngOnDestroy() {
      super.ngOnDestroy();
      this.debounceService.cancel(this.createImageResizeDebounceKey(this.model.absoluteName));
   }

   private modelChanged(simpleChange: SimpleChange = null) {
      if(simpleChange && !simpleChange.isFirstChange()) {
         const prevModel = simpleChange.previousValue as VSImageModel;
         const currModel = simpleChange.currentValue as VSImageModel;

         if(prevModel.absoluteName !== currModel.absoluteName) {
            this.debounceService.cancel(this.createImageResizeDebounceKey(prevModel.absoluteName));
         }
      }

      this.debounceService.debounce(this.createImageResizeDebounceKey(this.model.absoluteName),
         () => {
            this.opacity = this.getOpacity();
            this.src = this.getSrc();
            this.changeRef.detectChanges();

            if(this.model.scaleInfo.tiled) {
               this.finishLoad();
            }
         }, 200, []);

      this.tooltip = "";

      if(this.model.tooltipVisible) {
         if(!!this.model.customTooltipString) {
            this.tooltip = this.model.customTooltipString;
         }
         else if(this.model.hyperlinks && this.model.hyperlinks[0] &&
                 this.model.hyperlinks[0].tooltip)
         {
            this.tooltip = this.model.hyperlinks[0].tooltip;
         }
         else if(!!this.model.defaultAnnotationContent) {
            this.tooltip = this.model.defaultAnnotationContent;
         }
      }
   }

   public getSrc(): string {
      let name = this.model?.absoluteName;
      let isPrintRegionImage = name != null &&
         name.toLowerCase().startsWith(this.layoutRegion.toLowerCase() + "_");
      let newSrc = null;

      if(this.viewsheetClient.isLayoutFocused && this.layoutName && isPrintRegionImage) {
         newSrc = this.vsInfo.linkUri + "getLayoutImage" +
            "/" + Tool.byteEncodeURLComponent(this.layoutName) +
            "/" + Tool.byteEncodeURLComponent(this.layoutRegion) +
            "/" + Tool.byteEncodeURLComponent(this.viewsheetClient.runtimeId) +
            "/" + Tool.byteEncodeURLComponent(this.model.absoluteName) +
            "/" + this.model.objectFormat.width +
            "/" + this.model.objectFormat.height +
            "?" + this.model.genTime;
      }
      else {
         newSrc = this.vsInfo.linkUri + "getAssemblyImage" +
            "/" + Tool.byteEncodeURLComponent(this.viewsheetClient.runtimeId) +
            "/" + Tool.byteEncodeURLComponent(this.model.absoluteName) +
            "/" + this.model.objectFormat.width +
            "/" + this.model.objectFormat.height +
            "?" + this.model.genTime;
      }

      if(this.oldSrc != newSrc && !this.model.noImageFlag && this.vsInfo?.linkUri) {
         this.loading = true;
      }

      this.oldSrc = newSrc;

      return newSrc;
   }

   protected getHyperlinks(): HyperlinkModel[] {
      return this.model.hyperlinks;
   }

   protected onAssemblyActionEvent(event: AssemblyActionEvent<VSImageModel>): void {
      switch(event.id) {
      case "image annotate":
         this.showAnnotationDialog(event.event, false);
         break;
      case "image show-hyperlink":
         this.hyperlinkService.showHyperlinks(event.event,
                               this.getHyperlinks(), this.dropdownService,
                               this.viewsheetClient.runtimeId, this.vsInfo.linkUri,
                               this.isForceTab());
         break;
      case "image show-format-pane":
         this.onOpenFormatPane.emit(this.model);
         break;
      }
   }

   clicked(event: MouseEvent) {
      this.popComponentService.setPopLocation(this.model.popLocation);
      if(this.viewer && this.model.popComponent && !this.dataTipService.isDataTip(this.model.absoluteName)) {
         this.popComponentService.toggle(this.model.popComponent, event.clientX, event.clientY,
                                         this.model.popAlpha, this.model.absoluteName);
      }

      this.clickHyperlink(event);

      if(this.viewer) {
         let target = "/events/onclick/" + this.model.absoluteName + "/" + event.offsetX +
            "/" + event.offsetY;
         this.viewsheetClient.sendEvent(target);
      }
   }

   getOpacity(): any {
      // apply data tip alpha on image itself if it's a data tip
      return (this.dataTipService.isDataTip(this.model.absoluteName) ||
              this.popComponentService.isPopComponent(this.model.absoluteName)) &&
      (this.contextProvider.preview || this.contextProvider.viewer)
         ? this.model.objectFormat.alpha : this.model.alpha;
   }

   isForceTab(): boolean {
      return this.contextProvider.composer;
   }

   onImageLoad(event: UIEvent) {
      this.imageSize = new Dimension(null, null);
      this.changeRef.detectChanges();

      if(this.model.scaleInfo.scaleImage || this.model.noImageFlag) {
         let size = new Dimension(this.model.objectFormat.width, this.model.objectFormat.height);

         if(this.model.scaleInfo.preserveAspectRatio) {
            const image = event.target as HTMLImageElement;
            size = size.getScaledDimension(image.width / image.height);
         }

         size.width -= Tool.getMarginSize(this.model.objectFormat.border.left);
         size.width -= Tool.getMarginSize(this.model.objectFormat.border.right);
         size.height -= Tool.getMarginSize(this.model.objectFormat.border.top);
         size.height -= Tool.getMarginSize(this.model.objectFormat.border.bottom);
         this.imageSize = size;
      }

      this.finishLoad();
   }

   finishLoad() {
      this.loading = false;
      this.changeRef.detectChanges();
   }

   private createImageResizeDebounceKey(name: string): string {
      return "image-resize-" + name;
   }

   protected isPopupOrDataTipSource(): boolean {
      return this.popComponentService.isPopSource(this.model.absoluteName);
   }
}
