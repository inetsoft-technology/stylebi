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
import { EventEmitter, Injectable, OnDestroy } from "@angular/core";
import { NgbModalConfig } from "@ng-bootstrap/ng-bootstrap";
import fscreen from "fscreen";
import { FixedDropdownService } from "../../widget/fixed-dropdown/fixed-dropdown.service";
import { DialogService } from "../../widget/slide-out/dialog-service.service";
import { TooltipService } from "../../widget/tooltip/tooltip.service";

declare const window;

@Injectable()
export class FullScreenService implements OnDestroy {
   private readonly listener = (event) => this.onFullScreenChange(event);
   fullScreenChange = new EventEmitter<any>();
   tooltipServiceContainer: Element;
   dropdownServiceContainer: Element;
   modalServiceContainer: string | HTMLElement;
   dialogServiceContainer: string | HTMLElement;

   get fullScreenMode(): boolean {
      return !!fscreen.fullscreenElement;
   }

   constructor(private modalConfig: NgbModalConfig,
               private dialogService: DialogService,
               private dropdownService: FixedDropdownService,
               private tooltipService: TooltipService)
   {
      fscreen.addEventListener("fullscreenchange", this.listener);
   }

   ngOnDestroy(): void {
      fscreen.removeEventListener("fullscreenchange", this.listener);
      this.fullScreenChange.complete();
   }

   enterFullScreen(): void {
      // Bug #20774, use the document element for fullscreen, see
      // https://developers.google.com/web/fundamentals/native-hardware/fullscreen/
      fscreen.requestFullscreen(window.document.documentElement);
   }

   enterFullScreenForElement(target: Element): void {
      fscreen.requestFullscreen(target);
      this.setContainersToTarget(target);
   }

   exitFullScreen(): void {
      fscreen.exitFullscreen();
      this.setContainersToTarget(null);
   }

   setContainersToTarget(target: Element) {
      if(target != null) {
         this.tooltipServiceContainer = this.tooltipService.container;
         this.dialogServiceContainer = this.dialogService.container;
         this.dropdownServiceContainer = this.dropdownService.container;
         this.modalServiceContainer = this.modalConfig.container;

         this.tooltipService.container = target;
         this.dropdownService.container = target;
         this.dialogService.container = <HTMLElement> target;
         this.modalConfig.container = <HTMLElement> target;
      }
      else {
         this.tooltipService.container = this.tooltipServiceContainer;
         this.dropdownService.container = this.dropdownServiceContainer;
         this.dialogService.container = this.dialogServiceContainer;
         this.modalConfig.container = this.modalServiceContainer;
      }
   }

   private onFullScreenChange(event: any): void {
      this.fullScreenChange.emit(event);
   }
}
