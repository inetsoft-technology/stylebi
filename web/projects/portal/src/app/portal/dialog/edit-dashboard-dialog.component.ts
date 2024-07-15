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
import {
   ChangeDetectorRef,
   Component,
   EventEmitter,
   Input,
   OnInit,
   Output,
   ViewChild
} from "@angular/core";
import { UntypedFormControl, Validators } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { DashboardModel } from "../../common/data/dashboard-model";
import { GuiTool } from "../../common/util/gui-tool";
import { Tool } from "../../../../../shared/util/tool";
import { RepositoryEntry } from "../../../../../shared/data/repository-entry";
import { RepositoryEntryType } from "../../../../../shared/data/repository-entry-type.enum";
import { RepositoryTreeComponent } from "../../widget/repository-tree/repository-tree.component";
import { RepositoryTreeService } from "../../widget/repository-tree/repository-tree.service";
import { FormValidators } from "../../../../../shared/util/form-validators";
import { TreeNodeModel } from "../../widget/tree/tree-node-model";
import { ComponentTool } from "../../common/util/component-tool";

const NEW_DASHBOARD_URI: string = "../api/portal/dashboard/new";
const EDIT_DASHBOARD_URI: string = "../api/portal/dashboard/edit/";
const DASHBOARD_DUPLICATE_URI: string = "../api/portal/dashboard/duplicate/";

@Component({
   selector: "edit-dashboard-dialog",
   templateUrl: "edit-dashboard-dialog.component.html",
   styleUrls: ["edit-dashboard-dialog.component.scss"]
})
export class EditDashboardDialog implements OnInit {
   @Input() dashboard: DashboardModel;
   @Input() composerEnabled: boolean;
   @Output() onCommit: EventEmitter<DashboardModel> = new EventEmitter<DashboardModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild(RepositoryTreeComponent) tree: RepositoryTreeComponent;
   rootNode: TreeNodeModel;
   selector: number = RepositoryEntryType.FOLDER | RepositoryEntryType.VIEWSHEET;
   name: string;
   oldName: string;
   global: boolean;
   isNew: boolean;
   compose: boolean;
   nameControl: UntypedFormControl;
   mobile: boolean;

   constructor(private repositoryTreeService: RepositoryTreeService,
               private http: HttpClient, private modalService: NgbModal,
               private changeRef: ChangeDetectorRef)
   {
   }

   ngOnInit(): void {
      this.isNew = !this.dashboard;
      this.mobile = GuiTool.isMobileDevice();

      if(this.isNew) {
         this.dashboard = <DashboardModel> {};
      }
      else {
         this.oldName = this.dashboard.name;
         let globalRegex = /(?:__GLOBAL)$/;

         if(globalRegex.test(this.dashboard.name)) {
            this.name = this.dashboard.name.substring(0,
               globalRegex.exec(this.dashboard.name).index);
            this.global = true;
         }
         else {
            this.name = this.dashboard.name;
            this.global = false;
         }
      }

      this.repositoryTreeService.getRootFolder(null, this.selector, null, false, true, false, this.global)
         .subscribe((data) => {
            this.rootNode = data;

            if(this.dashboard.path) {
               this.changeRef.detectChanges();

               Promise.resolve(null).then(() => {
                  this.tree.selectAndExpandToPath(this.dashboard.path);
               });
            }
         });

      this.nameControl = new UntypedFormControl(this.name, [Validators.required,
         FormValidators.containsDashboardSpecialCharsForName]);
   }

   closeDialog(): void {
      this.onCancel.emit("cancel");
   }

   okClicked(): void {
      let newDashboard = Tool.clone(this.dashboard);
      newDashboard.name = this.name;

      // new dashboard
      if(this.isNew) {
         if(this.compose) {
            newDashboard.path = newDashboard.identifier = null;
         }

         this.http.get<boolean>(DASHBOARD_DUPLICATE_URI + encodeURIComponent(newDashboard.name))
            .subscribe((duplicate) => {
               if(duplicate) {
                  ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                     "_#(js:viewer.nameValid)");
               }
               else {
                  this.http.post<DashboardModel>(NEW_DASHBOARD_URI, newDashboard)
                     .subscribe(
                        (dashboard: DashboardModel) => {
                           if(this.compose) {
                              const composerUrl = "composer";
                              GuiTool.openBrowserTab(composerUrl,
                                 new HttpParams()
                                    .set("vsId", dashboard.identifier)
                                    .set("deployed", "true")
                              );
                           }

                           this.onCommit.emit(dashboard);
                        },
                        (err: any) => {
                        }
                     );
               }
            });
      }
      // edit existing dashboard
      else {
         if(this.global) {
            newDashboard.name += "__GLOBAL";
         }

         if(this.oldName !== newDashboard.name) {
            this.http.get<boolean>(DASHBOARD_DUPLICATE_URI + encodeURIComponent(newDashboard.name))
               .subscribe((duplicate) => {
                  if(duplicate) {
                     ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                        "_#(js:viewer.nameValid)");
                  }
                  else {
                     this.editDashboard(newDashboard);
                  }
               });
         }
         else {
            this.editDashboard(newDashboard);
         }
      }
   }

   editDashboard(dashboard: DashboardModel) {
      this.http.post(EDIT_DASHBOARD_URI + this.oldName, dashboard)
         .subscribe(
            (data: any) => {
               if(this.compose) {
                  const composerUrl = "composer";
                  GuiTool.openBrowserTab(composerUrl,
                     new HttpParams()
                        .set("vsId", dashboard.identifier)
                        .set("deployed", "true")
                  );
               }

               this.onCommit.emit(dashboard);
            },
            (err: any) => {
               console.error("Dashboard edit was unsuccessful.");
            }
         );
   }

   nodeSelected(node: TreeNodeModel) {
      const entry: RepositoryEntry = node.data;

      if(entry.type !== RepositoryEntryType.FOLDER) {
         this.dashboard.path = entry.path;
         this.dashboard.identifier = entry.entry.identifier;
      }
      else {
         this.dashboard.path = this.dashboard.identifier = null;
      }
   }

   isValid(): boolean {
      return this.nameControl && this.nameControl.valid &&
         (!!this.dashboard.path || this.compose);
   }
}
