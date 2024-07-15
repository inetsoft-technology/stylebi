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
   AfterViewInit,
   Component,
   ElementRef,
   EventEmitter,
   HostBinding,
   HostListener,
   Input,
   OnDestroy,
   Output,
   Renderer2,
   ViewChild,
} from "@angular/core";
import { ModalDismissReasons } from "@ng-bootstrap/ng-bootstrap";
import { DropdownObserver } from "../services/dropdown-observer.service";
import { DialogService } from "./dialog-service.service";
import { SlideOutRef } from "./slide-out-ref";

@Component({
   selector: "slide-out",
   templateUrl: "slide-out.component.html",
   styleUrls: ["slide-out.component.scss"]
})
export class SlideOutComponent implements AfterViewInit, OnDestroy {
   @Input() limitResize: boolean = true;
   @Input() title: string = "";
   @Input() keyboard: boolean = true;
   @Input() size: "sm" | "lg" = null;
   @HostBinding("class") @Input() side: "left" | "right" = "left";
   @Input() windowClass: string = null;
   @Input() changedByOthers: boolean = false;
   @Output() dismiss: EventEmitter<any> = new EventEmitter<any>();
   public visible: boolean = true;
   open: boolean = true;
   @ViewChild("contentContainer") contentContainer: ElementRef;
   private resizeListener: () => void;
   private resizeEndListener: () => void;
   explicitWidth: number = null;
   resizeX: number = null;
   resizeW: number = null;
   minWidth: number = 200;

   constructor(private dialogService: DialogService, private renderer: Renderer2,
               private dropdownObserver: DropdownObserver)
   {
   }

   // z-index, under modal 1050, and top slideout 10490
   @HostBinding("style.z-index") zIndex = 10480;

   // adding the 'modal' class at the top level ensures nested modal classes work
   @HostBinding("class") modalClass = "modal";

   @HostBinding("style.width") width = "auto";

   ngAfterViewInit(): void {
      this.dropdownObserver.onDropdownOpened();
   }

   ngOnDestroy(): void {
      if(this.open) {
         this.dropdownObserver.onDropdownClosed();
      }
   }

   setOnTop(onTop: boolean) {
      this.zIndex = onTop ? 10490 : 10480;
   }

   isOnTop(): boolean {
      return this.zIndex > 10480;
   }

   isSlideoutOnTop(idx: number): boolean {
      return this.dialogService.isSlideoutOnTop(idx);
   }

   setExpanded(expanded: boolean): void {
      if(expanded !== this.open) {
         if(expanded) {
            this.dropdownObserver.onDropdownOpened();
         }
         else {
            this.dropdownObserver.onDropdownClosed();
         }
      }

      this.open = expanded;
   }

   isExpanded(): boolean {
      return this.open;
   }

   toggle(): void {
      this.open = !this.open;

      if(this.open) {
         this.dropdownObserver.onDropdownOpened();
      }
      else {
         this.dropdownObserver.onDropdownClosed();
      }
   }

   get sizeClass(): string {
      return this.size ? "modal-" + this.size : null;
   }

   @HostListener("document:keyup.esc", ["$event"])
   escKey(event): void {
      if(this.keyboard && !event.defaultPrevented && this.dialogService.openPopups == 0) {
         this.dismissComponent(ModalDismissReasons.ESC);
      }
   }

   dismissComponent(reason: any): void {
      this.dismiss.emit(reason);
   }

   getCurrentSlideouts(): SlideOutRef[] {
      return this.dialogService.getCurrentSlideOuts();
   }

   dismissAll() {
      this.dialogService.dismissAllSlideouts();
   }

   showSlideout(idx: number) {
      this.dialogService.showSlideout(idx);
   }

   startResize(event: MouseEvent) {
      this.resizeX = event.pageX;
      this.explicitWidth = this.contentContainer.nativeElement.getBoundingClientRect().width;
      this.resizeW = this.explicitWidth;

      if(!this.limitResize && !!this.contentContainer) {
         this.renderer.setStyle(this.contentContainer.nativeElement, "max-width", "100%");
      }

      this.resizeListener = this.renderer.listen("document", "mousemove", (evt: MouseEvent) => {
         this.explicitWidth = this.resizeW + evt.pageX - this.resizeX;
         evt.preventDefault();
      });
      this.resizeEndListener = this.renderer.listen("document", "mouseup", () => {
         this.resizeListener();
         this.resizeEndListener();
      });
   }

   renameTitle(oldObjectId: string, newObjectId: string): void {
      if(!!newObjectId && !!this.title) {
         this.title = this.title.replace(oldObjectId, newObjectId);
      }
   }

   get actualWidth(): number {
      return this.explicitWidth != null && this.explicitWidth < this.minWidth ? this.minWidth
         : this.explicitWidth;
   }
}
