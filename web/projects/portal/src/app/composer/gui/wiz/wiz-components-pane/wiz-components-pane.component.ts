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
import { HttpClient, HttpParams } from "@angular/common/http";
import { Component, HostBinding, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from "@angular/core";
import { Subscription } from "rxjs";
import { AssetType } from "../../../../../../../shared/data/asset-type";
import { AssemblyActionGroup } from "../../../../common/action/assembly-action-group";
import { ActionsContextmenuComponent } from "../../../../widget/fixed-dropdown/actions-contextmenu.component";
import { DropdownOptions } from "../../../../widget/fixed-dropdown/dropdown-options";
import { FixedDropdownService } from "../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { TreeNodeModel } from "../../../../widget/tree/tree-node-model";
import { WizService } from "../services/wiz.service";

@Component({
   selector: "wiz-components-pane",
   templateUrl: "./wiz-components-pane.component.html",
   styleUrls: ["./wiz-components-pane.component.scss"]
})
export class WizComponentsPane implements OnInit, OnChanges, OnDestroy {
   @HostBinding("hidden")
   @Input() inactive: boolean;
   @Input() runtimeId: string;

   root: TreeNodeModel;
   private visualizations: TreeNodeModel = {
      label: "_#(js:Visualizations)",
      icon: "folder-toolbox-icon",
      data: {
         visualizationRoot: true
      },
      children: []
   };
   private components: TreeNodeModel = {
      label: "_#(js:Components)",
      icon: "folder-toolbox-icon",
      data: { componentsRoot: true },
      children: []
   };
   private filter: TreeNodeModel = {
      label: "_#(js:Filter)",
      icon: "condition-icon",
      children: []
   };
   private output: TreeNodeModel;
   private shape: TreeNodeModel;
   private subscriptions = new Subscription();

   constructor(private http: HttpClient, private wizService: WizService,
               private dropdownService: FixedDropdownService)
   {
   }

   ngOnInit(): void {
      this.initStaticNodes();
      this.loadVisualizations();
      this.loadComponents();
      this.loadFilters();
      this.buildRoot();
      this.subscriptions.add(this.wizService.refreshFilters.subscribe(() => this.loadFilters()));
      this.subscriptions.add(this.wizService.refreshTree.subscribe(() => {
         this.loadVisualizations();
         this.loadComponents();
         this.loadFilters();
      }));
   }

   ngOnDestroy(): void {
      this.subscriptions.unsubscribe();
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes["runtimeId"] && !changes["runtimeId"].firstChange) {
         this.loadVisualizations();
         this.loadComponents();
         this.loadFilters();
      }
   }

   private loadVisualizations(): void {
      if(!this.runtimeId) {
         this.visualizations.children = [];
         return;
      }

      const params = new HttpParams().set("runtimeId", this.runtimeId);

      this.http.get<TreeNodeModel>("../api/composer/wiz/visualizations", { params })
         .subscribe({
            next: (result) => {
               this.visualizations.children = result.children || [];
            },
            error: () => {
               this.visualizations.children = [];
            }
         });
   }

   private loadComponents(): void {
      if(!this.runtimeId) {
         this.components.children = [];
         return;
      }

      const params = new HttpParams().set("runtimeId", this.runtimeId);

      this.http.get<TreeNodeModel>("../api/composer/wiz/components", { params })
         .subscribe({
            next: (result) => {
               this.components.children = result.children || [];
            },
            error: () => {
               this.components.children = [];
            }
         });
   }

   newVisualization(): void {
      this.wizService.onOpenVisualization();
   }

   private loadFilters(): void {
      if(!this.runtimeId) {
         this.filter.children = [];
         return;
      }

      const params = new HttpParams().set("runtimeId", this.runtimeId);

      this.http.get<TreeNodeModel>("../api/composer/wiz/filters", { params })
         .subscribe({
            next: (result) => {
               this.filter.children = result.children || [];
            },
            error: () => {
               this.filter.children = [];
            }
         });
   }

   private buildRoot(): void {
      this.root = {
         children: [
            this.visualizations,
            this.components,
            this.filter,
            this.output,
            this.shape
         ].filter(n => !!n)
      };
   }

   private initStaticNodes(): void {

      this.output = {
         label: "_#(js:Output)",
         icon: "folder-toolbox-icon",
         children: [
            {
               label: "_#(js:Text)",
               icon: "text-box-icon",
               leaf: true,
               dragName: "dragtext"
            },
            {
               label: "_#(js:Image)",
               icon: "image-icon",
               leaf: true,
               dragName: "dragimage"
            }
         ]
      };

      this.shape = {
         label: "_#(js:Shape)",
         icon: "folder-toolbox-icon",
         children: [
            {
               label: "_#(js:Line)",
               icon: "line-icon",
               leaf: true,
               dragName: "dragline"
            },
            {
               label: "_#(js:Rectangle)",
               icon: "rectangle-icon",
               leaf: true,
               dragName: "dragrectangle"
            },
            {
               label: "_#(js:Oval)",
               icon: "oval-icon",
               leaf: true,
               dragName: "dragoval"
            }
         ]
      };
   }

   openVisualization(node: TreeNodeModel) {
      if(!node?.data?.properties || node.data.type !== AssetType.VIEWSHEET) {
         return;
      }

      const standaloneVisualization = node.data?.properties?.visualizationScope !== "private";
      this.wizService.onOpenVisualization(node.data.identifier, standaloneVisualization);
   }

   removeVisualization(node: TreeNodeModel) {
      if(!node?.data || node.data.type !== AssetType.VIEWSHEET) {
         return;
      }

      const event = {
         entry: node.data,
         confirmed: true
      };

      this.http.post("../api/composer/asset-tree/remove-asset", event)
         .subscribe({
            next: () => {
               this.loadVisualizations();
               this.loadComponents();
            },
            error: (err) => {
               console.error("Failed to remove visualization", err);
            }
         });
   }

   hasMenuFunction(): any {
      return (node) => this.hasMenu(node);
   }

   hasMenu(node: TreeNodeModel): boolean {
      const actions = this.createActions([null, node, [node]]);
      return actions.some(group => group.visible);
   }

   openContextmenu(event: [MouseEvent, TreeNodeModel, TreeNodeModel[]]) {
      let options: DropdownOptions = {
         position: {x: event[0].clientX, y: event[0].clientY},
         contextmenu: true,
      };

      let contextmenu: ActionsContextmenuComponent =
         this.dropdownService.open(ActionsContextmenuComponent, options).componentInstance;
      contextmenu.sourceEvent = event[0];
      contextmenu.actions = this.createActions(event);
   }

   private createActions(event: [MouseEvent, TreeNodeModel, TreeNodeModel[]]): AssemblyActionGroup[] {
      let group = new AssemblyActionGroup([]);
      let groups = [group];
      let node = event[1];

      if(node?.data?.visualizationRoot || node?.data?.componentsRoot) {
         group.actions.push({
            id: () => "new-wiz-visualization",
            label: () => "_#(js:New Visualization)",
            icon: () => "",
            enabled: () => true,
            visible: () => true,
            action: () => this.wizService.onOpenVisualization(undefined, node?.data?.visualizationRoot)
         });
      }
      else if(node?.data?.type === AssetType.VIEWSHEET) {
         group.actions.push({
            id: () => "open-wiz-visualization",
            label: () => "_#(js:Open)",
            icon: () => "",
            enabled: () => true,
            visible: () => true,
            action: () => this.openVisualization(node)
         });
         group.actions.push({
            id: () => "remove-wiz-visualization",
            label: () => "_#(js:Remove)",
            icon: () => "",
            enabled: () => true,
            visible: () => true,
            action: () => this.removeVisualization(node)
         });
      }

      return groups;
   }
}
