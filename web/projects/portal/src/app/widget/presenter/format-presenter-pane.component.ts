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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { ModelService } from "../services/model.service";
import { TreeNodeModel } from "../tree/tree-node-model";

@Component({
   selector: "format-presenter-pane",
   templateUrl: "format-presenter-pane.component.html",
   styleUrls: ["format-presenter-pane.component.scss"]
})

export class FormatPresenterPane implements OnInit {
   @Input() presenterPath: string = "";
   @Input() presenterLabel: string = "";
   @Input() hasDescriptors: boolean = false;
   @Input() runtimeId: string;
   @Input() layout: boolean = false;
   @Input() tableSelected = false;
   @Input() textSelected = false;
   @Output() onOpenPresenterPropertyDialog = new EventEmitter<any>();
   @Output() onPresenterChange = new EventEmitter<any>();
   public root: TreeNodeModel;

   constructor(private modelService: ModelService) {}

   ngOnInit() {
      this.modelService.getModel("../composer/vs/presenter")
         .subscribe((data: TreeNodeModel) => {
            this.root = data;
         });
   }

   public getIcon(node: TreeNodeModel): string {
      let css: string = "crosshair-icon";

      if(node.leaf) {
         return css;
      }

      return null;
   }

   public selectPresenter(node: TreeNodeModel): void {
      this.onPresenterChange.emit({
         label: node.label,
         presenter: node.data.class,
         hasDescriptors: node.data.hasDescriptors
      });
   }

   public isPresenterDialogEnabled(): boolean {
      return this.hasDescriptors && (this.tableSelected || this.textSelected);
   }
}