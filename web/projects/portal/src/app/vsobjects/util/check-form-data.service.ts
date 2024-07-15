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
import { HttpClient, HttpParams } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subject } from "rxjs";
import { Tool } from "../../../../../shared/util/tool";
import { VSObjectModel } from "../model/vs-object-model";
import { VSTableModel } from "../model/vs-table-model";
import { ComponentTool } from "../../common/util/component-tool";

/**
 * Service created for checking whether an action is interfering with an edited
 * form table cell.
 */
@Injectable()
export class CheckFormDataService {
   private formCount: number = 0;

   constructor(private http: HttpClient, private modalService: NgbModal) {
   }

   public replaceObject(old: VSObjectModel, current: VSObjectModel): void {
      const oldIsForm: boolean = this.isForm(old);
      const currentIsForm: boolean = this.isForm(current);

      if(oldIsForm && !currentIsForm) {
         this.formCount--;
      }
      else if(!oldIsForm && currentIsForm) {
         this.formCount++;
      }
   }

   public addObject(obj: VSObjectModel): void {
      if(this.isForm(obj)) {
         this.formCount++;
      }
   }

   public removeObject(obj: VSObjectModel): void {
      if(this.isForm(obj)) {
         this.formCount--;
      }
   }

   public recalculateForms(objs: VSObjectModel[]): void {
      this.formCount = 0;

      objs.forEach((obj: VSObjectModel) => {
         if(this.isForm(obj)) {
            this.formCount++;
         }
      });
   }

   public resetCount(): void {
      this.formCount = 0;
   }

   private get hasFormTable(): boolean {
      return this.formCount > 0;
   }

   public checkTableFormData(runtimeId: string, name: string,
                             selection: Map<number, number[]>,
                             callback: Function): void
   {
      const selectionString: string = this.convertTableSelection(selection);
      this.checkFormData(runtimeId, name, selectionString, callback);
   }

   public checkFormData(runtimeId: string, name: string, selection: string,
                        confirmedCallback: Function, canceledCallback?: Function): void
   {
      this.checkFormData0(runtimeId, name, selection, confirmedCallback, canceledCallback,
         true);
   }

   public checkFormData0(runtimeId: string, name: string, selection: string,
                        confirmedCallback: Function, canceledCallback: Function,
                        checkCondition: boolean = true): void
   {
      if(this.hasFormTable) {
         const params = new HttpParams()
            .set("runtimeId", Tool.byteEncode(runtimeId))
            .set("checkCondition", checkCondition + "");
         const body = {
            name: name,
            selection: selection
         };

         this.http.post<boolean>("../api/formDataCheck", body, {params})
            .subscribe((changed: boolean) => {
                  if(changed) {
                     this.showFormDataAlert().then((option: string) => {
                        if(option == "yes") {
                           confirmedCallback();
                        }
                        else if(option === "no" && !!canceledCallback) {
                           canceledCallback();
                        }
                     });
                  }
                  else {
                     confirmedCallback();
                  }
               },
               (error) => {
                  console.log("Unable to check form tables: " + error);
               });
      }
      else {
         confirmedCallback();
      }
   }

   public isFormTableChanged(runtimeId: string, name: string): Promise<boolean | void> {
      if(this.hasFormTable) {
         const params = new HttpParams().set("runtimeId", Tool.byteEncode(runtimeId));
         const body = {
            name: name,
         };

         // Return promise containing the result of if the form table has been changed and if it
         // has, whether the user would like to continue or not
         const subject = new Subject<boolean>();
         this.http.post<boolean>("../api/formTableModified", body, {params})
            .subscribe((changed) => {
               if(changed) {
                  this.showFormDataAlert().then((option: string) => {
                     subject.next(option === "yes");
                     subject.complete();
                  });
               }
               else {
                  subject.next(true);
                  subject.complete();
               }
            });
         return subject.toPromise();
      }
      else {
         return Promise.resolve(true);
      }
   }

   private showFormDataAlert(): Promise<any> {
      let buttonOptions = {"yes": "_#(js:Yes)", "no": "_#(js:No)"};
      let message: string = "_#(js:viewer.viewsheet.formDataChanged)";

      return ComponentTool.showMessageDialog(this.modalService, "_#(js:Confirm)", message, buttonOptions);
   }

   private convertTableSelection(selection: Map<number, number[]>): string {
      let selected: string = "";

      selection.forEach((cols: number[], row: number) => {
         cols.forEach((col: number) => selected += row + "X" + col + ";");
      });

     return selected ? selected.substring(0, selected.length - 1) : "";
   }

   private isForm(obj: VSObjectModel): boolean {
      return obj.objectType === "VSTable" && (obj as VSTableModel).form;
   }
}
