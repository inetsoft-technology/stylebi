/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import {
   ChangeDetectionStrategy,
   ChangeDetectorRef,
   Component,
   ElementRef,
   EventEmitter,
   HostListener,
   Input,
   NgZone,
   OnDestroy,
   Output,
   ViewChild,
} from "@angular/core";
import { DomSanitizer, SafeHtml } from "@angular/platform-browser";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subscription } from "rxjs";
import { Tool } from "../../../../../../shared/util/tool";
import { Annotation } from "../../../common/data/annotation";
import { Rectangle, Rectangular } from "../../../common/data/rectangle";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ScaleService } from "../../../widget/services/scale/scale-service";
import { AnnotatableActions } from "../../action/annotatable-actions";
import { ContextProvider } from "../../context-provider.service";
import { RichTextService } from "../../dialog/rich-text-dialog/rich-text.service";
import { OpenAnnotationFormatDialogEvent } from "../../event/annotation/open-annotation-format-dialog-event";
import { ToggleAnnotationStatusEvent } from "../../event/annotation/toggle-annotation-status-event";
import { UpdateAnnotationEvent } from "../../event/annotation/update-annotation-event";
import { VSAnnotationModel } from "../../model/annotation/vs-annotation-model";
import { VSRectangleModel } from "../../model/vs-rectangle-model";
import { AbstractVSObject } from "../abstract-vsobject.component";
import { DataTipService } from "../data-tip/data-tip.service";

@Component({
   selector: "vs-annotation",
   templateUrl: "./vs-annotation.component.html",
   styleUrls: ["./vs-annotation.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class VSAnnotation extends AbstractVSObject<VSAnnotationModel> implements OnDestroy {
   @Input() selected: boolean = false;
   @Input()
   set model(model: VSAnnotationModel) {
      if(model.contentModel) {
         this.content = this.domSanitizer.bypassSecurityTrustHtml(model.contentModel.content);
      }

      if(model.annotationRectangleModel != null) {
         this.contentPadding = this.getContentPadding(model.annotationRectangleModel);
      }

      this._model = model;
   }

   get model(): VSAnnotationModel {
      return this._model;
   }

   @Input()
   set actions(actions: AnnotatableActions) {
      if(actions == null) {
         return;
      }

      // first remove any existing subscriptions
      this.actionSubscription.unsubscribe();
      this.actionSubscription = actions.onAssemblyActionEvent.subscribe((event) => {
         if("annotation edit" + this.getAssemblyName() === event.id) {
            this.richTextService.showAnnotationDialog((content) => {
               this.updateAnnotation(new Rectangle(0, 0, 0, 0), content);
            }, this.model?.annotationRectangleModel?.objectFormat?.background)
               .subscribe(dialog => {
                  if(this.model.contentModel) {
                     dialog.initialContent = this.model.contentModel.content;
                  }
               });
         }
         else if("annotation remove" + this.getAssemblyName() === event.id) {
            this.remove.emit(this.model);
         }
         else if("annotation format" + this.getAssemblyName() === event.id) {
            this.openFormatDialog();
         }
      });
   }

   // restrict the line endpoint to this area. for data annotations hide the annotations
   // when the point moves outside of this element
   @Input() restrictTo: Element | Rectangular;

   // sometimes the available DOM structure isn't sufficient to define the full bounds of the
   // annotation visibility restriction and we need to define it as the intersection of 2 bounds
   @Input() additionalRestriction: Element | Rectangular;

   // when using restrictTo, calculate the position of the line endpoint using the offset from this
   // element's bounding client rectangle
   @Input() tetherTo: Element | Rectangular;

   // adjusted position (most likely due to scroll)
   @Input() offsetX: number = 0;
   @Input() offsetY: number = 0;
   @Input() restrictXAdjust: number = 0;
   @Input() overflowXTetherHidden: boolean = false;

   @Output() remove = new EventEmitter<VSAnnotationModel>();
   @Output() mouseSelect = new EventEmitter<[VSAnnotationModel, MouseEvent]>();

   @HostListener("click", ["$event"])
   @HostListener("contextmenu", ["$event"])
   mouseSelectAnnotation(event: MouseEvent): void {
      this.mouseSelect.emit([this.model, event]);
   }

   // used to determine if the tooltip should show
   @ViewChild("annotationContent") public contentContainer: ElementRef;
   public content: SafeHtml;
   public isOverflowing: boolean = false;
   public contentPadding: number = 3;
   private scaleSubscription = Subscription.EMPTY;
   private actionSubscription = Subscription.EMPTY;
   private scale: number;

   constructor(private domSanitizer: DomSanitizer,
               protected viewsheetClient: ViewsheetClientService,
               private modalService: NgbModal,
               private scaleService: ScaleService,
               zone: NgZone,
               private changeRef: ChangeDetectorRef,
               protected context: ContextProvider,
               protected dataTipService: DataTipService,
               private richTextService: RichTextService)
   {
      super(viewsheetClient, zone, context, dataTipService);
      this.scaleSubscription = this.scaleService.getScale().subscribe((scale) => this.scale = scale);
   }

   ngOnDestroy(): void {
      super.ngOnDestroy();
      this.scaleSubscription.unsubscribe();
      this.actionSubscription.unsubscribe();
   }

   checkOverflow(): void {
      const contentContainer = this.contentContainer.nativeElement;
      const isOverflowing = contentContainer.innerText && contentContainer.innerText.trim() &&
         this.model.annotationRectangleModel.objectFormat.height <
         contentContainer.getBoundingClientRect().height;

      if(isOverflowing != this.isOverflowing) {
         this.isOverflowing = isOverflowing;
         this.changeRef.detectChanges();
      }
   }

   /**
    * Check if the line endpoint is in the given restriction container. Very performance heavy so
    * this should be called as few times as possible. Currently called in a change detection cycle
    * which isn't too bad since the annotations is OnPush.
    *
    * @return {boolean} whether the line is in the container
    */
   isLineInContainer() {
      const lineModel = this.model.annotationLineModel;

      if(lineModel && this.tetherTo && this.restrictTo) {
         // the element to which the annotation is positioned in the DOM
         const offsetParentBounds = this.getContainerBounds(this.tetherTo);

         // scaled endpoint (relative to offset parent) + scaled scroll + parent bounds
         const lineLeft = this.scale *
            (lineModel.objectFormat.left + this.offsetX) + offsetParentBounds.x;
         const lineTop = this.scale *
            (lineModel.objectFormat.top + this.offsetY) + offsetParentBounds.y;

         // hide when the line endpoint goes outside of this element
         const containerBounds = this.getContainerBounds(this.restrictTo);
         containerBounds.x += this.restrictXAdjust;

         if(this.overflowXTetherHidden && containerBounds.x + containerBounds.width >
            offsetParentBounds.x + offsetParentBounds.width)
         {
            containerBounds.width = offsetParentBounds.x + offsetParentBounds.width -
               containerBounds.x;
         }

         let contained = containerBounds.contains(lineLeft, lineTop);

         if(this.additionalRestriction != null) {
            const additionalBounds = this.getContainerBounds(this.additionalRestriction);
            contained = contained && additionalBounds.contains(lineLeft, lineTop);
         }

         return contained;
      }

      // if these properties aren't defined just assume that the annotation will always be shown
      return true;
   }

   /**
    * Transform an element or Rectangular into a Rectangle object for intersection logic
    */
   private getContainerBounds(container: Element | Rectangular): Rectangle {
      if(container instanceof Element) {
         return Rectangle.fromClientRect(container.getBoundingClientRect());
      }
      else {
         let x = container.x * this.scale;
         let y = container.y * this.scale;
         let width = container.width * this.scale;
         let height = container.height * this.scale;
         return new Rectangle(x, y, width, height);
      }
   }

   /**
    * Update the position of the annotation on the server
    *
    * @param deltaRect a rectangle with values equal to the change in position and size
    *                  of the annotation
    * @param content   new content for the annotation
    */
   public updateAnnotation(deltaRect: Rectangle, content?: string): void {
      const format = this.model.annotationRectangleModel.objectFormat;
      const newRect = new Rectangle(format.left, format.top,
                                    Math.max(10, format.width),
                                    Math.max(10, format.height));
      const event = new UpdateAnnotationEvent(this.model.absoluteName, newRect, content);
      this.viewsheetClient.sendEvent("/events/annotation/update-annotation", event);
   }

   /**
    * When the hidden annotation icon is clicked we should show all annotations
    */
   public toggleAnnotationStatus(): void {
      const event = new ToggleAnnotationStatusEvent(true);
      this.viewsheetClient.sendEvent("/events/annotation/toggle-status", event);
   }

   public onRectangleDragMove(event: any): void {
      const rectFormat = this.model.annotationRectangleModel.objectFormat;
      rectFormat.top += event.dy / this.scale;
      rectFormat.left += event.dx / this.scale;
   }

   public onRectangleResizeMove(event: any): void {
      const rectFormat = this.model.annotationRectangleModel.objectFormat;
      rectFormat.height += event.deltaRect.height / this.scale;
      rectFormat.width += event.deltaRect.width / this.scale;

      // translate when resizing from top or left edges
      if(event.edges.top || event.edges.left) {
         rectFormat.top += event.deltaRect.top / this.scale;
         rectFormat.left += event.deltaRect.left / this.scale;
      }
   }

   /**
    * Update the line position by the amount changed on each mouse move
    *
    * @param {Object} event the interact event
    * @param {number} event.dx the change in x
    * @param {number} event.dy the change in y
    */
   public onLineEndDragMove(event: any): void {
      this.model.annotationLineModel.endLeft += event.dx / this.scale;
      this.model.annotationLineModel.endTop += event.dy / this.scale;
   }

   /**
    * On releasing the line end update the position on the server
    *
    * @param {Rectangle} deltaRect the total change amount from the initial position
    */
   public onLineEndDragEnd(deltaRect: Rectangle): void {
      const annotationLineModel = this.model.annotationLineModel;
      const x = annotationLineModel.endLeft + annotationLineModel.objectFormat.left;
      const y = annotationLineModel.endTop + annotationLineModel.objectFormat.top;

      // we don't care about width/height when moving the line endpoint
      const scaledRect = new Rectangle(x, y, 0, 0);
      const event = new UpdateAnnotationEvent(this.getAssemblyName(), scaledRect);
      this.viewsheetClient.sendEvent("/events/annotation/update-annotation-endpoint", event);
   }

   public getSrc(): string {
      return this.vsInfo.linkUri + "getAssemblyImage" +
         "/" + Tool.byteEncode(this.viewsheetClient.runtimeId) +
         "/" + Tool.byteEncode(this.model.annotationRectangleModel.absoluteName) +
         "/" + this.model.objectFormat.width +
         "/" + this.model.objectFormat.height +
         "?" + this.model.genTime;
   }

   public isViewsheetAnnotation(): boolean {
      return Annotation.isViewsheetAnnotation(this.model.annotationType);
   }

   public isAssemblyAnnotation(): boolean {
      return Annotation.isAssemblyAnnotation(this.model.annotationType);
   }

   /**
    * Make sure our text is within the possibly rounded edges. Use 3px default padding and add the
    * coordinates of the point on the quarter ellipse at 45 degrees due to border-radius.
    */
   private getContentPadding(rectangleModel: VSRectangleModel): number {
      const borderRadius = rectangleModel.roundCornerValue / 2;
      return borderRadius * (1 - Math.sin(Math.PI / 4)) + 3;
   }

   private openFormatDialog(): void {
      const event = new OpenAnnotationFormatDialogEvent(this.model.absoluteName);
      this.viewsheetClient.sendEvent("/events/annotation/open-format-dialog", event);
   }
}
