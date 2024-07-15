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
   Component,
   ElementRef,
   EventEmitter,
   Input,
   OnDestroy,
   OnInit,
   AfterViewInit,
   Output,
   ViewChild
} from "@angular/core";
import { VirtualScrollService } from "../../../widget/tree/virtual-scroll.service";
import { Subscription } from "rxjs";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { map } from "rxjs/operators";

export enum SidebarTab {
   BINDING_TREE,
   FORMAT_PANE
}

@Component({
   selector: "data-editor-tab-pane",
   templateUrl: "data-editor-tab-pane.component.html",
   styleUrls: ["data-editor-tab-pane.component.scss", "../../../composer/gui/tab-selector/tab-selector-shared.scss"]
})
export class DataEditorTabPane  {
   SidebarTab = SidebarTab;
   @Input() formatPaneDisabled: boolean;
   @Output() onSwitchTab: EventEmitter<SidebarTab> = new EventEmitter<SidebarTab>();
   @Input() selectedTab: SidebarTab = SidebarTab.BINDING_TREE;

   onTabClick(tab: SidebarTab): void {
      this.onSwitchTab.emit(this.selectedTab = tab);
   }
}
