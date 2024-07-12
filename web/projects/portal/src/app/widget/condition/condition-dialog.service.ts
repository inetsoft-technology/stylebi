/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Injectable } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Condition } from "../../common/data/condition/condition";
import { JunctionOperatorType } from "../../common/data/condition/junction-operator-type";
import { ComponentTool } from "../../common/util/component-tool";
import { isValidCondition } from "../../common/util/condition.util";

export const CONDITION_MODIFIED_TITLE = "_#(js:Conditions Modified)";
export const CONDITION_INSERTED_TITLE = "_#(js:Conditions Inserted)";
export const CONDITION_MODIFIED_MESSAGE = "_#(js:designer.common.design.modifyChangesOnCondition)";
export const CONDITION_INSERT_MESSAGE = "_#(js:designer.common.design.insertChangesOnCondition)";
export const JUNCTION_MODIFIED_TITLE = "_#(js:Junction Condition Modified)";
export const JUNCTION_MODIFIED_MESSAGE = "_#(js:designer.common.design.modifyChangesOnJunction)";

export interface CurrentCondition {
   selectedIndex: number;
   condition: Condition;
}

export interface CurrentJunction {
   selectedIndex: number;
   junctionType: JunctionOperatorType;
}

@Injectable({
   providedIn: "root"
})
export class ConditionDialogService {
   constructor(private modalService: NgbModal)
   {
   }

   private _dirtyCondition: CurrentCondition;
   private  _dirtyJunction: CurrentJunction;

   get dirtyCondition(): CurrentCondition {
      return this._dirtyCondition;
   }

   set dirtyCondition(dirtyCondition: CurrentCondition) {
      this._dirtyCondition = dirtyCondition;
   }

   get dirtyJunction(): CurrentJunction {
      return this._dirtyJunction;
   }

   set dirtyJunction(dirtyJunction: CurrentJunction) {
      this._dirtyJunction = dirtyJunction;
   }

   public checkDirtyConditions(commitFn?: () => string, validFn?: () => boolean,
                               saveOption?: () => string, noValue: boolean = true): Promise<boolean>
      {
      if(this.hasDirtyCondition()) {
         let conditionOption: string = saveOption();
         const insert = conditionOption == "insert" && (!validFn || validFn());
         const modify = conditionOption == "modify" && (!validFn || validFn());
         let msg = insert ? CONDITION_INSERT_MESSAGE : modify ?
            CONDITION_MODIFIED_MESSAGE : CONDITION_MODIFIED_MESSAGE;

         if(!!!msg) {
            return Promise.resolve(true);
         }

         const title = insert ? CONDITION_INSERTED_TITLE : CONDITION_MODIFIED_TITLE;
         return Promise.resolve(ComponentTool.showConfirmDialog(this.modalService, title, msg,
            {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
            .then((result: string) => {
               let yes: boolean = result === "yes";

               if(!yes) {
                  return noValue;
               }

               if(yes && (insert || modify)) {
                  yes = commitFn() != null;
               }

               if(yes) {
                  this.dirtyCondition = null;
               }

               return yes;
            }));
      }
      else if(this.dirtyJunction) {
         const msg = JUNCTION_MODIFIED_MESSAGE;
         const title = JUNCTION_MODIFIED_TITLE;
         return Promise.resolve(ComponentTool.showConfirmDialog(this.modalService, title, msg,
            {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
            .then((result: string) => {
               let yes: boolean = result === "yes";

               if(!yes) {
                  return noValue;
               }
               else {
                  yes = commitFn() != null;
                  this.dirtyJunction = null;
               }

               return yes;
            }));
      }
      else {
         return Promise.resolve(true);
      }
   }

   private hasDirtyCondition(): boolean {
      if(!this.dirtyCondition) {
         return false;
      }

      let condition = this.dirtyCondition.condition;

      // the None item added in ConditionFieldComboComponent.
      if(!condition.field || condition.field.fakeNone) {
         return false;
      }

      // when value of condition is default and value is invalid, ignore it to next.
      if(this.dirtyCondition.selectedIndex == null
         && !isValidCondition(this.dirtyCondition.condition))
      {
         return false;
      }

      return true;
   }
}
