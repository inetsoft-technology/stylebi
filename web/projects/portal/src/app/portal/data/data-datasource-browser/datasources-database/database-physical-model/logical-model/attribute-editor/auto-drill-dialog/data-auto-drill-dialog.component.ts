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
   OnInit,
   Input,
   Output,
   EventEmitter,
   ViewChild,
   ElementRef,
   AfterViewInit
} from "@angular/core";
import {
   UntypedFormGroup, UntypedFormControl, Validators, AbstractControl,
   ValidationErrors
} from "@angular/forms";
import { XSchema } from "../../../../../../../../common/data/xschema";
import { AutoDrillInfoModel } from "../../../../../../model/datasources/database/physical-model/logical-model/auto-drill-info-model";
import { Tool } from "../../../../../../../../../../../shared/util/tool";
import { TreeNodeModel } from "../../../../../../../../widget/tree/tree-node-model";
import { HttpClient, HttpParams } from "@angular/common/http";
import { NgbDropdown, NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { AutoDrillPathModel } from "../../../../../../model/datasources/database/physical-model/logical-model/auto-drill-path-model";
import {
   CheckCycleDependenciesEvent
} from "../../../../../../model/datasources/database/physical-model/logical-model/check-cycle-dependencies-event";
import { DrillParameterModel } from "../../../../../../model/datasources/database/physical-model/logical-model/drill-parameter-model";
import { ComponentTool } from "../../../../../../../../common/util/component-tool";
import { ParameterDialog } from "./parameter-dialog/parameter-dialog.component";
import { ValidatorMessageInfo } from "../../../../../../../../widget/dialog/input-name-dialog/input-name-dialog.component";
import { RepositoryTreeComponent } from "../../../../../../../../widget/repository-tree/repository-tree.component";
import { RepositoryEntryType } from "../../../../../../../../../../../shared/data/repository-entry-type.enum";
import { EntityModel } from "../../../../../../model/datasources/database/physical-model/logical-model/entity-model";
import { DrillSubQueryModel } from "../../../../../../model/datasources/database/physical-model/logical-model/drill-sub-query-model";
import { AssetConstants } from "../../../../../../../../common/data/asset-constants";
import { SelectWorksheetDialog } from "./select-worksheet-dialog.component";
import { createAssetEntry } from "../../../../../../../../../../../shared/data/asset-entry";
import {
   QueryFieldModel
} from "../../../../../../model/datasources/database/query/query-field-model";

enum LinkType {
   WEB_LINK = 1,
   VIEWSHEET_LINK = 8
}

const GET_REPOSITORY_TREE_URI: string = "../api/composer/vs/hyperlink-dialog-model/tree";
const GET_VIEWSHEET_AUTO_DRILL_PARAMETERS: string = "../api/data/logicalModel/vs/autoDrill-parameters";

@Component({
   selector: "data-auto-drill-dialog",
   templateUrl: "data-auto-drill-dialog.component.html",
   styleUrls: ["data-auto-drill-dialog.component.scss"]
})
export class AutoDrillDialog implements OnInit, AfterViewInit {
   @Input() entities: EntityModel[];
   @Input() fields: QueryFieldModel[];
   @Input() portal: boolean = true;
   @Input() logicalModelName: string;
   @Output() onCommit: EventEmitter<any> = new EventEmitter<any>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild("repositoryTree") repositoryTree: RepositoryTreeComponent;
   @ViewChild("btnFocus") btnFocus: ElementRef;
   private _autoDrillModel: AutoDrillInfoModel;
   queryFields: string[] = ["this.column"];
   LinkType = LinkType;
   form: UntypedFormGroup;
   paramIndex: number = -1;
   validatorMessages: ValidatorMessageInfo[] = [
      {validatorName: "required", message: "_#(js:data.logicalmodel.drillNameRequired)"},
      {validatorName: "duplicate", message: "_#(js:data.logicalmodel.drillNameDuplicate)"}
   ];
   assetTreeRoot: TreeNodeModel = {
      label: "Asset Tree",
      leaf: false,
      expanded: true,
      data: {
         path: "/"
      },
      children: []
   };
   targetSelf: boolean = true;
   variables: string[] = [];
   _editIndex: number = -1;
   _selectedDrills: number[] = [];
   drillPathLabel: string;

   @Input() set autoDrillModel(value: AutoDrillInfoModel) {
      this._autoDrillModel = Tool.clone(value);
   }

   get autoDrillModel(): AutoDrillInfoModel {
      return this._autoDrillModel;
   }

   get selectedDrills(): number[] {
      return this._selectedDrills;
   }

   get editIndex(): number {
      return this._editIndex;
   }

   set editIndex(selectedIndex: number) {
      this._editIndex = selectedIndex;

      if(this._editIndex == -1) {
         this.clearForm();
      }
   }

   constructor(private http: HttpClient,
               private modalService: NgbModal) {}

   loadRepositoryTreeAndselectDrill(): void {
      // now the end of identifier is organization id
      let pathWithoutOrgId = this.editDrill.link.substring(0, this.editDrill.link.lastIndexOf("^"));
      let path: string = pathWithoutOrgId.substring(pathWithoutOrgId.lastIndexOf("^") + 1);
      let arr: string[] = this.editDrill.link.split("^");

      if(arr.length > 0 && arr[0] == AssetConstants.USER_SCOPE + "" && !path.startsWith(Tool.MY_DASHBOARDS)) {
         path = Tool.MY_DASHBOARDS + "/" + path;
      }

      this.selectAndExpandToPath(path);
   }

   ngOnInit(): void {
      if(this.autoDrillModel && this.autoDrillModel.paths && this.autoDrillModel.paths.length > 0) {
         this.editIndex = 0;
         this.selectedDrills.push(0);
         this.targetSelf = this.editDrill.targetFrame == "SELF" ||
            this.editDrill.targetFrame == "";
      }

      this.initFormControl();
      this.loadRepositoryTree(() => {
         if(!!this.editDrill && this.editDrill.linkType !== LinkType.WEB_LINK) {
            if(this.editDrill.link) {
               this.loadRepositoryTreeAndselectDrill();
            }
         }
      });

      this.updateVariable();
      this.getWorksheetFields();
   }

   ngAfterViewInit(): void {
      this.btnFocus.nativeElement.focus();
   }

   /**
    * Get selected asset path to display.
    */
   get drillLabel(): string {
      if(!this.editDrill) {
         return "";
      }

      if(this.editDrill.linkType !== LinkType.WEB_LINK) {
         if(!this.editDrill.link) {
            return "";
         }

         if(!this.drillPathLabel) {
            let entry = createAssetEntry(this.editDrill.link);
            this.drillPathLabel = entry?.path;
         }

         if(!!this.selectedAssetNode) {
            let pathLabel = this.getDrillPathLabel(this.selectedAssetNode, "");

            if(pathLabel != this.drillPathLabel) {
               this.drillPathLabel = pathLabel;
            }
         }

         return this.drillPathLabel;
      }
      else {
         return !this.editDrill.link ? "" : this.editDrill.link;
      }
   }

   getDrillPathLabel(node: TreeNodeModel, childPath: string): string {
      if(!node || !node.data || node.data.path == "/") {
         return childPath;
      }

      let alias = node.data["alias"];
      alias = !alias ? node.data["name"] : alias;
      childPath = childPath == "" ? alias : alias + "/" + childPath;

      return this.getDrillPathLabel(this.repositoryTree.getParentNode(node), childPath);
   }

   /**
    * Get selected asset node.
    */
   get selectedAssetNode(): TreeNodeModel {
      if(this.repositoryTree) {
         return this.repositoryTree.selectedNode;
      }

      return null;
   }

   /**
    * The current drill being edited.
    * @returns {AutoDrillPathModel}
    */
   get editDrill(): AutoDrillPathModel {
      return this.autoDrillModel.paths[this.editIndex];
   }

   getDrillWorksheet(): string {
      let entry = this.editDrill?.query?.entry;

      if(!entry) {
         return "_#(js:None)";
      }

      let path = entry.path;
      let alias = entry.alias;

      if(!alias) {
         return path;
      }

      let pathArray = path.split("/");
      pathArray[pathArray.length - 1] = alias;
      return pathArray.join("/");
   }

   private loadRepositoryTree(callback?: () => void) {
      const params = new HttpParams()
                        .set("path", "/")
                        .set("isOnPortal", this.portal);
      this.http.get(GET_REPOSITORY_TREE_URI, {params: params})
         .subscribe((rootNode: TreeNodeModel) => {
            this.assetTreeRoot = rootNode;
            this.assetTreeRoot.expanded = true;

            if(!!callback) {
               callback();
            }
         });
   }

   /**
    * Select drill to edit.
    * @param index
    */
   selectDrill(evt: MouseEvent, index: number): void {
      if(this.editIndex >= 0 && !!this.editDrill) {
         this.editDrill.name = this.nameControl.value;
         this.editDrill.link = this.editDrill.linkType == LinkType.WEB_LINK ?
            this.linkControl.value : this.editDrill.link;
      }

      if(index >= 0 && index < this.autoDrillModel.paths.length) {
         this.editIndex = index;

         if(evt && (evt.shiftKey || evt.ctrlKey)) {
            this._selectedDrills.push(index);
         }
         else {
            this._selectedDrills = [index];
         }

         this.initFormControl();
         this.targetSelf = this.editDrill.targetFrame == "SELF" ||
            this.editDrill.targetFrame == "";
      }

      this.clearSelectedNode();

      if(!!this.editDrill && this.editDrill.linkType !== LinkType.WEB_LINK) {
         if(this.editDrill.link) {
            this.updateVariable();
            setTimeout(() => {
               this.loadRepositoryTreeAndselectDrill();
            }, 0);
         }
      }

      this.getWorksheetFields();
   }

   /**
    * Add a new drill.
    */
   addDrill(): void {
      const newDrill: AutoDrillPathModel = {
         name: this.getNewDrillName(),
         link: "",
         targetFrame: "",
         tip: "",
         params: [],
         passParams: true,
         disablePrompting: false,
         linkType: LinkType.VIEWSHEET_LINK,
         query: null,
         queryFields: []
      };

      this.autoDrillModel.paths.push(newDrill);
      this.selectDrill(null, this.autoDrillModel.paths.length - 1);
   }

   /**
    * Get the new drill name. for example: New Drill1.
    */
   getNewDrillName(): string {
      let existPaths = !!this._autoDrillModel.paths ? this._autoDrillModel.paths : [];
      let existIndexs = [];
      const newNamePre = "New Drill";

      if(!existPaths || existPaths.length == 0) {
         return newNamePre;
      }

      for(let path of existPaths) {
         let name = path.name;

         if(name == null) {
            continue;
         }
         else if(name === newNamePre) {
            existIndexs[0] = true;
            continue;
         }

         let index = name.indexOf(newNamePre);

         if(index == 0) {
            let number = parseInt(name.substring(newNamePre.length), 10);

            if(!Number.isNaN(number)) {
               existIndexs[number] = true;
            }
         }
      }

      for(let index = 0; index < existIndexs.length; index++) {
         if(existIndexs[index] !== true) {
            return index == 0 ? newNamePre : newNamePre + index;
         }
      }

      return newNamePre + existIndexs.length;
   }

   /**
    * Move current drill down in the list.
    */
   moveDrillDown(index: number): void {
      if(index != -1 && index < this.autoDrillModel.paths.length - 1) {
         //fix bug#43875 clone to avoid error when move up
         const path: AutoDrillPathModel = Tool.clone(this.autoDrillModel.paths[index]);
         this.autoDrillModel.paths[index] = this.autoDrillModel.paths[index + 1];
         this.autoDrillModel.paths[index + 1] = path;

         if(index == this.editIndex) {
            let findIndex = this._selectedDrills.findIndex((idx) => this.editIndex == idx);
            this.editIndex++;
            this._selectedDrills[findIndex] = this.editIndex;
         }

         this.nameControl.setValue(this.editDrill.name);
      }
   }

   /**
    * Move current drill up the list.
    */
   moveDrillUp(index: number): void {
      if(index != -1 && index > 0) {
         //fix bug#43875 clone to avoid error when move up
         const path: AutoDrillPathModel = Tool.clone(this.autoDrillModel.paths[index]);
         this.autoDrillModel.paths[index] = this.autoDrillModel.paths[index - 1];
         this.autoDrillModel.paths[index - 1] = path;

         if(index == this.editIndex) {
            let findIndex = this._selectedDrills.findIndex((idx) => this.editIndex == idx);
            this.editIndex--;
            this._selectedDrills[findIndex] = this.editIndex;
         }

         this.nameControl.setValue(this.editDrill.name);
      }
   }

   /**
    * Delete currently selected drill.
    */
   deleteDrill(all: boolean = false): void {
      const title: string = "_#(js:data.logicalmodel.removeDrill)";
      const message: string = all ? "_#(js:data.logicalmodel.confirmRemoveAllDrills)" :
         "_#(js:data.logicalmodel.confirmRemoveDrill)";

      ComponentTool.showConfirmDialog(this.modalService, title, message)
         .then((result: string) => {
            if(all) {
               if(result == "ok") {
                  this.autoDrillModel.paths = [];
                  this.editIndex = -1;
               }
            }
            else {
               const index: number = this.editIndex;

               if(index != -1 && result == "ok") {
                  this.selectedDrills.sort();

                  for(let i = this.selectedDrills.length - 1; i > -1; i--) {
                     this.autoDrillModel.paths.splice(this.selectedDrills[i], 1);
                  }

                  this.initFormControl();
                  let nindex = index;

                  if(this.autoDrillModel.paths.length == 0) {
                     nindex = -1;
                  }
                  else {
                     nindex = index < this.autoDrillModel.paths.length ? index : index - 1;
                     nindex = Math.max(nindex, 0);
                  }

                  if(nindex < 0) {
                     this.editIndex = nindex;
                  }

                  this.selectDrill(null, nindex);
               }
            }
         },
         () => {});
   }

   openSelectWorksheetDialog() {
      let oquery: DrillSubQueryModel = Tool.clone(this.editDrill.query);
      let dialog = ComponentTool.showDialog(this.modalService, SelectWorksheetDialog,
         (model: DrillSubQueryModel) => {
            this.editDrill.query = model;

            if(model == null) {
               this.editDrill.queryFields = ["this.column"];
            }
            else if(oquery == null || oquery.entry != model.entry) {
               this.getWorksheetFields();
            }

            if(this.editDrill.params) {
               this.editDrill.params = this.editDrill.params.filter(param => !!param.type);
            }
         });
      dialog.selectedSubQuery = Tool.clone(this.editDrill.query);
      dialog.entities = this.entities;
      dialog.fields = this.fields;
   }

   getWorksheetFields() {
      let wsIdentifier = this.editDrill?.query?.entry?.identifier;

      if(!wsIdentifier) {
         return;
      }

      let params = new HttpParams().set("wsIdentifier", wsIdentifier);
      this.http.get("../api/portal/data/autodrill/worksheet/fields", {params}).subscribe((fields: string[]) => {
         this.editDrill.queryFields = fields;
      });
   }

   /**
    * Select asset tree node.
    * @param node
    */
   selectNode(node: TreeNodeModel, dropdown: NgbDropdown): void {
      let entry = node.data;

      if(!entry) {
         return;
      }

      let type = -1;

      if(entry.type == RepositoryEntryType.VIEWSHEET) {
         type = LinkType.VIEWSHEET_LINK;
      }
      else {
         // invalid
         return;
      }

      let identifier = this.getAssetNodeIdentifier(node);
      this.editDrill.link = identifier;
      this.editDrill.linkType = type;
      this.updateVariable();
      setTimeout(() => {
         this.form.get("link").setValue(node.label);
      }, 0);

      if(node.leaf) {
         dropdown.close();
      }

      this.drillPathLabel = null;
   }

   /**
    * Get selected asset variable.
    */
   private updateVariable(): void {
      if(!this.editDrill || !this.editDrill.link) {
         return;
      }

      if(this.editDrill.linkType == LinkType.VIEWSHEET_LINK) {
         const params: HttpParams = new HttpParams()
            .set("assetId", this.editDrill.link);
         this.http.get<string[]>(GET_VIEWSHEET_AUTO_DRILL_PARAMETERS, {params: params})
            .subscribe((vars) => {
               this.variables = vars;
            });
      }
   }

   /**
    * get asset identifier by asset tree node.
    * @param node
    */
   private getAssetNodeIdentifier(node: TreeNodeModel): string {
      if(!node) {
         return null;
      }

      let entry = node.data;

      return entry.entry ? entry.entry.identifier : entry.path;
   }

   /**
    * Add new parameter.
    */
   addParameter(): void {
      this.paramIndex = -1;
      this.openParameterDialog();
   }

   /**
    * Edit selected parameter.
    * @param index
    */
   editParameter(index: number): void {
      this.paramIndex = index;
      this.openParameterDialog();
   }

   /**
    * Open the parameter dialog.
    */
   private openParameterDialog(): void {
      let dialog = ComponentTool.showDialog(this.modalService, ParameterDialog,
         (data: DrillParameterModel[]) => {
            this.editDrill.params = data;
      });

      dialog.index = this.paramIndex;
      dialog.parameters = this.editDrill.params;
      dialog.variables = this.variables;
      dialog.fields = this.editDrill.queryFields == null || this.editDrill.queryFields.length == 0 ?
         ["this.column"] : this.editDrill.queryFields;
   }

   /**
    * Remove specified parameter.
    * @param index
    */
   removeParameter(index: number): void {
      this.editDrill.params.splice(index, 1);
   }

   /**
    * Remove all parameters.
    */
   removeAllParameters(): void {
      const title: string = "_#(js:data.logicalmodel.removeParams)";
      const message: string = "_#(js:data.logicalmodel.confirmRemoveParams)";

      ComponentTool.showConfirmDialog(this.modalService, title, message)
         .then((result: string) => {
            if(result == "ok") {
               this.editDrill.params = [];
            }
         },
         () => {});
   }

   /**
    * When there is more than one error message, onle get the first one.
    * @param control
    * @returns {string}
    */
   getFirstErrorMessage(control: AbstractControl): string {
      const info: ValidatorMessageInfo = this.validatorMessages.find(
         (messageInfo: ValidatorMessageInfo) => control.getError(messageInfo.validatorName));

      return info ? info.message : null;
   }

   /**
    * Get the name form control
    * @returns {AbstractControl|null} the form control
    */
   get nameControl(): AbstractControl {
      return this.form.get("name");
   }

   /**
    * Get the link form control
    * @returns {AbstractControl|null} the form control
    */
   get linkControl(): AbstractControl {
      return this.form.get("link");
   }

   /**
    * Initialize the form group.
    */
   private initFormControl() {
      this.form = new UntypedFormGroup({
         name: new UntypedFormControl(this.editDrill ? this.editDrill.name : null, [
            Validators.required,
            this.uniqueName
         ]),
         link: new UntypedFormControl(this.editDrill ? this.editDrill.link : null, [
            Validators.required
         ])
      });

      this.nameControl.valueChanges.subscribe((value) => {
         this.editDrill.name = value;
      });

      this.form.get("link").valueChanges.subscribe((value => {
         if(!this.editDrill) {
            return;
         }

         if(this.editDrill.linkType != LinkType.WEB_LINK && this.selectedAssetNode) {
            this.editDrill.link = this.getAssetNodeIdentifier(this.selectedAssetNode);
         }
         else {
            this.editDrill.link = value;
         }
      }));
   }

   /**
    * Clear form controls.
    */
   private clearForm(): void {
      if(this.form) {
         this.form = new UntypedFormGroup({});
      }
   }

   /**
    * Make sure that the drill name is unique.
    * @param control
    * @returns {{duplicate: boolean}}
    */
   private uniqueName = (control: UntypedFormControl): ValidationErrors => {
      for(let i = 0; i < this.autoDrillModel.paths.length; i++) {
         if(control.value == this.autoDrillModel.paths[i].name && this.editIndex != i) {
            return { duplicate: true };
         }
      }

      return null;
   };

   getDisplayParam(param: DrillParameterModel) {
      let paramField: string = param.field;

      if(param.type == XSchema.TIME_INSTANT) {
         paramField = paramField.replace("T", " ");
      }

      return param.name + ":[" + paramField + "]";
   }

   changeLinkType(): void {
      this.editDrill.link = "";
      this.linkControl.setValue("");
      this.clearSelectedNode();
      this.initFormControl();

      if(this.editDrill.linkType != LinkType.WEB_LINK) {
         this.loadRepositoryTree();
      }
   }

   changeLinkTarget(val: boolean): void {
      if(val) {
         this.editDrill.targetFrame = "";
      }
   }

   private selectAndExpandToPath(path: string, node: TreeNodeModel = this.assetTreeRoot) {
      if(this.repositoryTree) {
         this.repositoryTree.selectAndExpandToPath(path, node);
      }
   }

   private clearSelectedNode(): void {
      if(this.repositoryTree) {
         this.repositoryTree.selectedNode = null;
         this.drillPathLabel = null;
      }
   }

   /**
    * Submit auto drill modifications.
    */
   ok(): void {
      let invalidPath: AutoDrillPathModel[] = this.autoDrillModel.paths.filter(
         (path: AutoDrillPathModel) => !path.link && "" == path.link.trim());

      if(invalidPath.length != 0) {
         let drillNames: string = "";

         invalidPath.forEach((path: AutoDrillPathModel) => {
            drillNames += path.name + " ";
         });

         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
            "_#(js:data.logicalmodel.drillMissingLink)" + drillNames);
         return;
      }

      let names: string[] = this.autoDrillModel.paths.map((path) => path.name);

      if(Array.from(new Set(names)).length < names.length) {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
            "_#(js:Duplicate Name): " + names);

         return;
      }

      let vsLinks: string[] = [];

      for(let path of this.autoDrillModel.paths) {
         if(path.linkType == LinkType.WEB_LINK) {
            continue;
         }

         vsLinks.push(path.link);
      }

      if(vsLinks.length != 0) {
         let event: CheckCycleDependenciesEvent = new CheckCycleDependenciesEvent();
         event.logicalModelName = this.logicalModelName;
         event.links = vsLinks;

         this.http.post("../api/data/logicalmodel/autoDrill/checkCycleDependencies", event).subscribe((cycle) => {
            if(cycle) {
               ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                  "_#(js:data.logical.model.cycleDependOnWarning)");
               return;
            }
            else {
               this.selectDrill(null, -1);
               this.onCommit.emit(this.autoDrillModel);
            }
         });
      }
      else {
         this.selectDrill(null, -1);
         this.onCommit.emit(this.autoDrillModel);
      }
   }

   /**
    * Cancel changes.
    */
   cancel(): void {
      this.onCancel.emit("cancel");
   }
}
