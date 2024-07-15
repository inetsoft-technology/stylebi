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
import { HttpParams } from "@angular/common/http";
import {
   Component,
   EventEmitter,
   Input,
   OnInit,
   Output,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { AssetEntry, createAssetEntry } from "../../../../../../shared/data/asset-entry";
import { ConditionExpression } from "../../../common/data/condition/condition-expression";
import { DataRef } from "../../../common/data/data-ref";
import { XSchema } from "../../../common/data/xschema";
import { ConditionList } from "../../../common/util/condition-list";
import { GuiTool } from "../../../common/util/gui-tool";
import { Tool } from "../../../../../../shared/util/tool";
import { AssetTreeService } from "../../../widget/asset-tree/asset-tree.service";
import { LoadAssetTreeNodesEvent } from "../../../widget/asset-tree/load-asset-tree-nodes-event";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { ModelService } from "../../../widget/services/model.service";
import { TreeDropdownComponent } from "../../../widget/tree/tree-dropdown.component";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { GroupingDialogModel } from "../../data/ws/grouping-dialog-model";
import { Worksheet } from "../../data/ws/worksheet";
import { ComponentTool } from "../../../common/util/component-tool";
import { ColumnRef } from "../../../binding/data/column-ref";
import { AssetEntryHelper } from "../../../common/data/asset-entry-helper";
import { AssetType } from "../../../../../../shared/data/asset-type";

@Component({
   selector: "grouping-dialog",
   templateUrl: "grouping-dialog.component.html",
   styleUrls: ["grouping-dialog.component.scss"]
})
export class GroupingDialog implements OnInit {
   @Input() groupingName: string;
   @Input() worksheet: Worksheet;
   @Output() onCommit: EventEmitter<any> = new EventEmitter<any>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild("groupingConditionDialog") groupingConditionDialog: TemplateRef<any>;
   @ViewChild(TreeDropdownComponent) treeDropdown: TreeDropdownComponent;
   model: GroupingDialogModel;
   private readonly treeController: string = "../api/composer/ws/grouping-assembly-tree-model";
   private readonly attributeController: string = "../api/composer/ws/asset-entry-attributes";
   private readonly RESTController: string = "../api/composer/ws/grouping-assembly-dialog-model/";
   private readonly socketController: string = "/events/ws/dialog/grouping-assembly-dialog-model";
   form: UntypedFormGroup;
   public types: {label: string, data: string}[];
   public root: TreeNodeModel;
   public attributes: DataRef[];
   public selectedConditionIndex: number;
   public selectedConditionExpr: ConditionExpression;
   currentSelectedNode: TreeNodeModel;
   outerMirror: boolean;
   formValid = () => this.model && this.form && this.form.valid && !this.outerMirror;

   constructor(private modelService: ModelService,
               private assetTreeService: AssetTreeService,
               private modalService: NgbModal)
   {
   }

   ngOnInit(): void {
      if(!this.model) {
         let params = new HttpParams();

         if(this.groupingName) {
            params = params.set("grouping", this.groupingName);
         }

         this.modelService.getModel<GroupingDialogModel>(this.RESTController + Tool.byteEncode(this.worksheet.runtimeId), params)
            .subscribe(
               (data) => {
                  this.model = data;
                  this.init();
               },
               () => {
                  console.warn("Could not fetch grouping assembly properties.");
               }
            );
      }
      else {
         this.init();
      }
   }

   private init() {
      this.initForm();
      this.initRoot();
      this.checkOuterMirror();
   }

   private initForm(): void {
      this.types = [{label: "", data: "-1"}, ...XSchema.standardDataTypeList];
      let type = this.model.type ? this.model.type : "-1";

      this.form = new UntypedFormGroup({
         newName: new UntypedFormControl(this.model.oldName, [
            Validators.required,
            FormValidators.notWhiteSpace,
            FormValidators.exists(this.worksheet.assemblyNames(this.model.oldName),
               {
                  trimSurroundingWhitespace: true,
                  ignoreCase: true,
                  originalValue: this.model.oldName
               }),
            FormValidators.variableSpecialCharacters
         ]),
         type: new UntypedFormControl(type),
         onlyFor: new UntypedFormControl(this.model.onlyFor, [
            Validators.required
         ]),
         attributeIndex: new UntypedFormControl(undefined),
         attribute: new UntypedFormControl(this.model.attribute, [
            Validators.required
         ]),
         groupAllOthers: new UntypedFormControl(this.model.groupAllOthers, [
            Validators.required
         ]),
         conditionExpressions: new UntypedFormControl(this.model.conditionExpressions, [
            Validators.required
         ]),
         variableNames: new UntypedFormControl(this.model.variableNames)
      });

      this.form.get("type").valueChanges.subscribe((value) => {
         this.form.get("onlyFor").setValue(undefined, {emitEvent: false});

         if(this.treeDropdown) {
            this.treeDropdown.reset();
         }

         Tool.setFormControlDisabled(this.form.get("onlyFor"), value !== "-1");
         this.form.get("attributeIndex").setValue(-1, {emitEvent: false});
         Tool.setFormControlDisabled(this.form.get("attribute"), value !== "-1");
         Tool.setFormControlDisabled(this.form.get("attributeIndex"), value !== "-1");
         this.form.get("conditionExpressions").setValue([]);
         this.selectedConditionIndex = undefined;
      });
      this.form.get("onlyFor").valueChanges.subscribe((value) => {
         if(value === "(all)" || !value) {
            Tool.setFormControlDisabled(this.form.get("type"), false);
            Tool.setFormControlDisabled(this.form.get("attribute"), true);
            Tool.setFormControlDisabled(this.form.get("attributeIndex"), true);
            this.form.get("attributeIndex").setValue(undefined);
         }
         else {
            Tool.setFormControlDisabled(this.form.get("type"), true);
            Tool.setFormControlDisabled(this.form.get("attribute"), false);
            Tool.setFormControlDisabled(this.form.get("attributeIndex"), false);
         }
      });
      this.form.get("attributeIndex").valueChanges.subscribe((value) => {
         if(value === undefined) {
            this.form.get("attribute").setValue(undefined);
         }
         else {
            this.form.get("attribute").setValue(this.attributes[value]);
            this.form.get("conditionExpressions").setValue([]);
            this.selectedConditionIndex = undefined;
         }
      });

      Tool.setFormControlDisabled(this.form.get("type"), this.form.get("onlyFor").value);
      Tool.setFormControlDisabled(this.form.get("onlyFor"), this.form.get("type").value !== "-1");
      Tool.setFormControlDisabled(this.form.get("attribute"), this.form.get("type").value !== "-1");
      Tool.setFormControlDisabled(this.form.get("attributeIndex"), this.form.get("type").value !== "-1");

      if(this.model.onlyFor) {
         this.modelService.sendModel<DataRef[]>(this.attributeController, this.model.onlyFor)
            .subscribe((result) => {
               this.attributes = result.body;
               let index = this.attributes.findIndex((el) => el.name === this.model.attribute.name);
               this.form.get("attributeIndex").setValue(index, {emitEvent: false});
               this.form.get("attribute").setValue(this.attributes[index]);
            });
      }
   }

   private initRoot() {
      this.assetTreeService.getAssetTreeNode(new LoadAssetTreeNodesEvent(), true, true,
         false, false, false, false, false)
         .subscribe((res) => {
            if(res.treeNodeModel.children[0]) {
               this.nodeExpanded(res.treeNodeModel.children[0]);
               this.root = res.treeNodeModel.children[0];
            }
         });

      this.currentSelectedNode = this.getCurrentSelectedNode(this.root);

      if(!this.currentSelectedNode) {
         this.currentSelectedNode = this.root;
      }
   }

   private getCurrentSelectedNode(parent: TreeNodeModel): TreeNodeModel {
      if(!parent || !this.model.onlyFor) {
         return null;
      }

      if(parent.leaf && parent.dataLabel === this.model.onlyFor?.identifier) {
         return parent;
      }

      for(let i = 0; i < parent.children?.length; i++) {
         let node: TreeNodeModel = parent.children[i];

         if(node.dataLabel === this.model.onlyFor?.identifier ||
            this.matchNode(node.dataLabel, this.model.onlyFor?.identifier))
         {
            return node;
         }
         // make sure selected node is expanded
         else if(this.model.onlyFor.path.startsWith(node.data.path + "/")) {
            if(node.children.length == 0) {
               this.nodeExpanded(node);
            }
         }

         node = this.getCurrentSelectedNode(node);

         if(node != null) {
            return node;
         }
      }

      return this.currentSelectedNode?.dataLabel === this.model.onlyFor?.identifier ?
         this.currentSelectedNode : null;
   }

   private matchNode(label: string, id: string): boolean {
      if(label == null || id == null) {
         return false;
      }

      let tentry: AssetEntry = createAssetEntry(label);
      let entry: AssetEntry = createAssetEntry(id);
      let path: string = entry.path;
      let db: string = path.substring(0, entry.path.lastIndexOf("/") + 1);
      let qname: string = path.substring(entry.path.lastIndexOf("/"));

      if(tentry.type == AssetType.QUERY && entry.type == AssetType.QUERY &&
         tentry.path.startsWith(db) && tentry.path.endsWith(qname))
      {
         return true;
      }

      return false;
   }

   private checkOuterMirror() {
      if(this.model.oldName) {
         const grouping = this.worksheet.groupings.find((g) => g.name === this.model.oldName);

         if(grouping != undefined && grouping.info.mirrorInfo &&
            grouping.info.mirrorInfo.outerMirror)
         {
            this.outerMirror = true;
            // TODO common.outerMirror
            const message = "_#(js:common.outerMirror)";

            // Schedule microtask to avoid creating a new view in a lifecycle hook.
            Promise.resolve(null).then(() => {
               ComponentTool.showMessageDialog(this.modalService, "_#(js:Information)",
                  message, {"ok": "_#(js:OK)"}, {backdrop: false })
                  .then(() => {}, () => {});
            });
         }
      }
   }

   public nodeExpanded(node: TreeNodeModel) {
      if(node.data && !node.leaf && ( !node.children || node.children.length === 0 )) {
         let entry: AssetEntry = node.data;

         this.modelService.sendModel(this.treeController, entry).subscribe((res: any) => {
            let model: TreeNodeModel = res.body;

            if(this.root === node) {
               let all: TreeNodeModel = {label: "(all)", leaf: true, data: "(all)"};
               node.children = [all, ...model.children];
            }
            else {
               node.children = model.children;
            }

            this.currentSelectedNode = this.getCurrentSelectedNode(node);

            if(!this.currentSelectedNode) {
               this.currentSelectedNode = this.root;
            }
         });
      }
   }

   public updateOnlyFor(result: TreeNodeModel) {
      this.form.get("onlyFor").patchValue(result.data);

      if(result.data === "(all)") {
         this.attributes = null;
         return;
      }

      this.modelService.sendModel<DataRef[]>(this.attributeController, result.data)
         .subscribe((response) => {
            this.attributes = response.body;
            this.form.get("attributeIndex").setValue(0);
         });
   }

   public get onlyForDisabled(): boolean {
      return this.form.get("onlyFor").disabled;
   }

   public get addDisabled(): boolean {
      return this.form.get("type").value === "-1" && this.form.get("attribute").value == null;
   }

   public get editDisabled(): boolean {
      return this.selectedConditionIndex === undefined;
   }

   public get deleteDisabled(): boolean {
      return this.selectedConditionIndex === undefined;
   }

   public get upDisabled(): boolean {
      return !this.selectedConditionIndex;
   }

   public get downDisabled(): boolean {
      let list: ConditionExpression[] = this.form.get("conditionExpressions").value;

      return !list || this.selectedConditionIndex === undefined || this.selectedConditionIndex === (list.length - 1);
   }

   public addCondition() {
      this.selectedConditionExpr = undefined;

      this.modalService.open(this.groupingConditionDialog, {backdrop: false,
                                                            windowClass: "condition-dialog"})
         .result.then(
         (resolve: {name: string, list: ConditionList}) => {
            this.form.get("conditionExpressions").value.push(resolve);
            this.form.get("conditionExpressions").updateValueAndValidity({
               onlySelf: false,
               emitEvent: false
            });
         },
         (reject) => {
         }
      );
   }

   public editCondition() {
      let list: ConditionExpression[] = this.form.get("conditionExpressions").value;
      this.selectedConditionExpr = list[this.selectedConditionIndex];

      this.modalService.open(this.groupingConditionDialog, {backdrop: false, size: "lg"})
         .result.then(
         (resolve: {name: string, conditionExpr: ConditionList}) => {
            this.form.get("conditionExpressions").value[this.selectedConditionIndex] = resolve;
            this.form.get("conditionExpressions").updateValueAndValidity({
               onlySelf: false,
               emitEvent: false
            });
         },
         (reject) => {
         }
      );
   }

   public deleteCondition() {
      this.form.get("conditionExpressions").value.splice(this.selectedConditionIndex, 1);
      this.form.get("conditionExpressions").updateValueAndValidity({
         onlySelf: false,
         emitEvent: false
      });
      this.selectedConditionIndex = undefined;
   }

   public moveConditionUp() {
      let list: ConditionExpression[] = this.form.get("conditionExpressions").value;
      let temp = list[this.selectedConditionIndex];
      list[this.selectedConditionIndex] = list[this.selectedConditionIndex - 1];
      list[this.selectedConditionIndex - 1] = temp;
      this.selectedConditionIndex--;
   }

   public moveConditionDown() {
      let list: ConditionExpression[] = this.form.get("conditionExpressions").value;
      let temp = list[this.selectedConditionIndex];
      list[this.selectedConditionIndex] = list[this.selectedConditionIndex + 1];
      list[this.selectedConditionIndex + 1] = temp;
      this.selectedConditionIndex++;
   }

   getTooltip(ref: DataRef): string {
      return ColumnRef.getTooltip(<ColumnRef> ref);
   }

   public getCSSIcon(node: TreeNodeModel): string {
      return GuiTool.getTreeNodeIconClass(node, "");
   }

   ok(): void {
      let model: GroupingDialogModel = Object.assign({}, this.model, this.form.getRawValue());
      model.newName = model.newName.trim();
      // delete vestigial properties
      Object.keys(model).forEach((e) => {
         if(!this.model.hasOwnProperty(e)) {
            delete model[e];
         }
      });

      if(model.type === "-1") {
         model.type = null;
      }

      this.onCommit.emit({model: model, controller: this.socketController});
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }
}
