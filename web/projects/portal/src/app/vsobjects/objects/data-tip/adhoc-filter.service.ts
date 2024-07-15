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

      const listener = this.renderer.listen("document", "click", (event: MouseEvent) => {
         if(!elementRef.nativeElement.contains(event.target) &&
            !GuiTool.parentContainsClass(<Element> event.target, "mini-toolbar") &&
            !GuiTool.parentContainsClass(<Element> event.target, "mobile-toolbar") &&
            (!acceptClose || acceptClose()))
         {
            const vsevent: VSObjectEvent = new VSObjectEvent(absoluteName);

            // delay so change is applied before temp assembly is removed
            setTimeout(() => {
               this.viewsheetClient.sendEvent("/events/composer/viewsheet/moveAdhocFilter",
                                              vsevent);
            }, 500);

            // remove the listener
            listener();
            closed();
            this._adhocFilterShowing = false;
         }
      });

      this.changeRef.detectChanges();
      return listener;
   }
}
