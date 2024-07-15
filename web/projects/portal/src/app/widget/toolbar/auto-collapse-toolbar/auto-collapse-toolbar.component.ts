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
import { Component, ElementRef, Input, OnInit, ViewChild } from "@angular/core";
import { GuiTool } from "../../../common/util/gui-tool";
import { ToolbarAction } from "../toolbar-action";

@Component({
  selector: "auto-collapse-toolbar",
  templateUrl: "./auto-collapse-toolbar.component.html",
  styleUrls: ["./auto-collapse-toolbar.component.scss"]
})
export class AutoCollapseToolbarComponent implements OnInit {
  @Input() actions: ToolbarAction[] = [];
  @Input() flowRight: boolean = true;
  @Input() minEmpty: number = 0;
  @ViewChild("toolbarContainer") toolbarContainer: ElementRef;
  @ViewChild("buttonsContainer") buttonsContainer: ElementRef;
  collapse: boolean = false;
  buttonWidth: number = -1;

  constructor() { }

  ngOnInit(): void {
  }

  resize(event: any) {
    if(!this.toolbarContainer) {
      return;
    }

    let toolbarRec =
       GuiTool.getElementRect(this.toolbarContainer.nativeElement);
    let buttonsRec =
       GuiTool.getElementRect(this.buttonsContainer.nativeElement);

    if(!toolbarRec || !buttonsRec) {
      return;
    }

    if(!this.collapse) {
      this.collapse = buttonsRec.width + this.minEmpty > toolbarRec.width;

      if(this.collapse && this.buttonWidth == -1) {
        let actionCount = this.visibleActionCount;

        this.buttonWidth = actionCount == 0 ? -1 :
           buttonsRec.width / this.visibleActionCount;
      }
    }
    else {
      this.collapse = this.buttonWidth * this.visibleActionCount + this.minEmpty > toolbarRec.width;
    }
  }

  get visibleActionCount(): number {
    return this.actions.filter(aciton => aciton && aciton.visible()).length;
  }

  clickAction(action: ToolbarAction): void {
     if(action && action.action) {
        action.action();
     }
  }
}
