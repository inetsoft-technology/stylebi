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
import { BreakpointObserver } from "@angular/cdk/layout";
import { Component, OnInit, ViewChild } from "@angular/core";
import { ContextHelp } from "../../../../context-help";
import { PageHeaderService } from "../../../../page-header/page-header.service";
import { Searchable } from "../../../../searchable";
import { Secured } from "../../../../secured";
import { TopScrollService } from "../../../../top-scroll/top-scroll.service";
import { ActionTreeNode } from "../action-tree-node";
import { MessageDialog, MessageDialogType } from "../../../../common/util/message-dialog";
import { MatDialog } from "@angular/material/dialog";
import { SecurityActionsTreeComponent } from "../security-actions-tree/security-actions-tree.component";

const SMALL_WIDTH_BREAKPOINT = 720;

@Secured({
   route: "/settings/security/actions",
   label: "Actions"
})
@Searchable({
   route: "/settings/security/actions",
   title: "Security Settings Actions",
   keywords: ["em.security.actions"]
})
@ContextHelp({
   route: "/settings/security/actions",
   link: "EMSettingsSecurityActions"
})
@Component({
   selector: "em-security-actions-page",
   templateUrl: "./security-actions-page.component.html",
   styleUrls: ["./security-actions-page.component.scss"]
})
export class SecurityActionsPageComponent implements OnInit {
   @ViewChild(SecurityActionsTreeComponent, { static: true }) tree: SecurityActionsTreeComponent;
   selectedAction: ActionTreeNode;
   unsavedChanges = false;
   //For small device use only
   private _editing = false;

   get resourceSelected(): boolean {
      return !!this.selectedAction;
   }

   get editing(): boolean {
      return this._editing;
   }

   set editing(value: boolean) {
      if(value !== this._editing) {
         this._editing = value;
         this.scrollService.scroll("up");
      }
   }

   constructor(private pageTitle: PageHeaderService, private dialog: MatDialog,
               private breakpointObserver: BreakpointObserver,
               private scrollService: TopScrollService)
   {
   }

   ngOnInit() {
      this.pageTitle.title = "_#(js:Security Settings Actions)";
   }

   onActionSelected(node: ActionTreeNode): void {
      if(this.unsavedChanges && this.selectedAction != node) {
         const ref = this.dialog.open(MessageDialog, {
            data: {
               title: "_#(js:em.settings.actionSettingsChanged)",
               content: "_#(js:em.settings.actionSettings.confirm)",
               type: MessageDialogType.CONFIRMATION
            }
         });

         ref.afterClosed().subscribe(val => {
            if(val) {
               this.unsavedChanges = false;
               this.selectedAction = node;
            }
            else {
               this.tree.selectNode(this.selectedAction);
            }
         });
      }
      else {
         this.selectedAction = node;
      }
   }

   isScreenSmall(): boolean {
      return this.breakpointObserver.isMatched(`(max-width: ${SMALL_WIDTH_BREAKPOINT}px)`);
   }
}
