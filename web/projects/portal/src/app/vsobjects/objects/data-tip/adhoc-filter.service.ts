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
import { Injectable, ElementRef, Renderer2, ChangeDetectorRef } from "@angular/core";
import { VSObjectEvent } from "../../event/vs-object-event";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { GuiTool } from "../../../common/util/gui-tool";

/**
 * Manage the adhoc filter components.
 */
@Injectable()
export class AdhocFilterService {
   private _adhocFilterShowing: boolean = false;
   private listener = null;
   private filterName: string = null;


   constructor(private renderer: Renderer2,
               private changeRef: ChangeDetectorRef,
               private viewsheetClient: ViewsheetClientService) {
   }

   get adhocFilterShowing(): boolean {
      return this._adhocFilterShowing;
   }

   showFilter(elementRef: ElementRef, absoluteName: string, closed: () => any,
              acceptClose: () => boolean): () => any
   {
      this._adhocFilterShowing = true;
      this.filterName = absoluteName;

      this.listener = this.renderer.listen("document", "click", (event: MouseEvent) => {
         if(!elementRef.nativeElement.contains(event.target) &&
            !GuiTool.parentContainsClass(<Element> event.target, "mini-toolbar") &&
            !GuiTool.parentContainsClass(<Element> event.target, "mobile-toolbar") &&
            (!acceptClose || acceptClose()))
         {
            this.hideAdhocFilter();
            closed();
         }
      });

      this.changeRef.detectChanges();
      return this.listener;
   }

   hideAdhocFilter() {
      if(this.filterName == null) {
         return;
      }

      const vsevent: VSObjectEvent = new VSObjectEvent(this.filterName);

      // delay so change is applied before temp assembly is removed
      setTimeout(() => {
         this.viewsheetClient.sendEvent("/events/composer/viewsheet/moveAdhocFilter",
            vsevent);
      }, 500);

      // remove the listener
      this.listener();
      this._adhocFilterShowing = false;
      this.filterName = null;
   }
}
