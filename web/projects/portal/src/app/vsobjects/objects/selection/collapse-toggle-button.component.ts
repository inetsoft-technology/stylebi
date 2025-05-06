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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { GuiTool } from "../../../common/util/gui-tool";
import { AbstractVSActions } from "../../action/abstract-vs-actions";
import { ContextProvider } from "../../context-provider.service";

@Component({
   selector: "collapse-toggle-button",
   templateUrl: "collapse-toggle-button.component.html",
   styleUrls: ["collapse-toggle-button.component.scss"]
})
export class CollapseToggleButton {
   @Input() public collapsed = false;

   @Input()
   public set actionNames(actionNames: string[]) {
      // check the action visiblity in the viewer
      if(this.viewerOrPreview) {
         this.hideButtonVisible = AbstractVSActions.isActionVisible(actionNames, "Hide");
         this.showButtonVisible = AbstractVSActions.isActionVisible(actionNames, "Show");
      }
   }

   @Output() public hideClicked = new EventEmitter<void>();
   @Output() public showClicked = new EventEmitter<void>();

   // true by default
   public hideButtonVisible: boolean = true;
   public showButtonVisible: boolean = true;
   private viewerOrPreview: boolean;
   mobileDevice: boolean = GuiTool.isMobileDevice();

   constructor(private contextProvider: ContextProvider) {
      this.viewerOrPreview = contextProvider.viewer || contextProvider.preview;
   }

   clickShow(event: MouseEvent) {
      event.stopPropagation();
      this.showClicked.emit();
   }

   clickHide(event: MouseEvent) {
      event.stopPropagation();
      this.hideClicked.emit();
   }
}
