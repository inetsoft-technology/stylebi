/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

import { Component, Input, OnDestroy, OnInit } from "@angular/core";
import { Subscription } from "rxjs";
import { ToolbarAction } from "../../../../widget/toolbar/toolbar-action";
import { ToolbarActionGroup } from "../../../../widget/toolbar/toolbar-action-group";
import { WizDashboard } from "../../../data/vs/wizDashboard";
import { CloseSheetEvent } from "../../vs/event/close-sheet-event";
import { WizService } from "../services/wiz.service";

@Component({
   selector: "wiz-editor-pane",
   templateUrl: "./wiz-editor-pane.component.html",
   styleUrls: [
      "./wiz-editor-pane.component.scss",
      "../../toolbar/composer-toolbar.component.scss"
   ]
})
export class WizEditorPane implements OnInit, OnDestroy {
   @Input() currentVisualization: WizDashboard;

   private subscriptions = new Subscription();

   constructor(private wizService: WizService) {
   }

   ngOnInit(): void {
      this.subscriptions.add(
         this.wizService.exitVisualization.subscribe(() => {
            this.closeVisualizationOnServer(this.currentVisualization);
         })
      );
   }

   ngOnDestroy(): void {
      this.subscriptions.unsubscribe();
   }

   exit(): void {
      this.wizService.onExitVisualization();
   }

   accept(): void {
      if(this.currentVisualization) {
         this.wizService.onSaveVisualization(this.currentVisualization);
      }
   }

   get actionGroup(): ToolbarActionGroup {
      return <ToolbarActionGroup>{
         label: "",
         iconClass: "",
         buttonClass: "",
         enabled: () => true,
         visible: () => true,
         action: () => {},
         actions: this.actions
      };
   }

   actions: ToolbarAction[] = [
      {
         label: "_#(js:Accept)",
         iconClass: "submit-icon",
         buttonClass: "finish-button",
         tooltip: () => "<b>_#(js:Finish Editing)</b>",
         enabled: () => true,
         visible: () => true,
         action: () => this.accept()
      },
      {
         label: "_#(js:Exit)",
         iconClass: "close-icon",
         buttonClass: "close-button",
         tooltip: () => "<b>_#(js:Cancel)</b>",
         enabled: () => true,
         visible: () => true,
         action: () => this.exit()
      }
   ];

   private closeVisualizationOnServer(vs: WizDashboard): void {
      if(vs?.runtimeId && vs?.socketConnection) {
         vs.socketConnection.sendEvent("/events/composer/viewsheet/close", new CloseSheetEvent(true));
      }
   }
}
