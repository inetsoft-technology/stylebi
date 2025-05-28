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
   Component,
   EventEmitter,
   Input,
   OnInit,
   Output,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { NgbDropdown, NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { RepositoryEntry } from "../../../../../../shared/data/repository-entry";
import { RepositoryEntryType } from "../../../../../../shared/data/repository-entry-type.enum";
import { Tool } from "../../../../../../shared/util/tool";
import { TrapInfo } from "../../../common/data/trap-info";
import { XSchema } from "../../../common/data/xschema";
import { GuiTool } from "../../../common/util/gui-tool";
import { RepositoryTreeService } from "../../../widget/repository-tree/repository-tree.service";
import { ModelService } from "../../../widget/services/model.service";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { HyperlinkDialogModel } from "../../model/hyperlink-dialog-model";
import { InputParameterDialogModel } from "../../model/input-parameter-dialog-model";
import { VSTrapService } from "../../util/vs-trap.service";
import { ExpressionValue } from "../../../common/data/condition/expression-value";
import { ExpressionType } from "../../../common/data/condition/expression-type";
enum LinkType {
   WEB_LINK = 1,
   VIEWSHEET_LINK = 8,
   NONE = 9,
   MESSAGE_LINK = 16
}

enum ValueType {
   VALUE,
   FIELD,
   EXPRESSION,
}

const CHECK_TRAP_REST_URI: string = "../api/composer/viewsheet/check-hyperlink-dialog-trap/";
const GET_REPOSITORY_TREE_URI: string = "../api/composer/vs/hyperlink-dialog-model/tree";

@Component({
   selector: "hyperlink-dialog",
   templateUrl: "hyperlink-dialog.component.html",
   styleUrls: ["hyperlink-dialog.component.scss"]
})
export class HyperlinkDialog implements OnInit {
   @Input() objectName: string;
   @Input() runtimeId: string;
   @Input() isVSContext = true;
   @Output() onCommit = new EventEmitter<HyperlinkDialogModel>();
   @Output() onCancel = new EventEmitter<string>();
   @Output() onApply = new EventEmitter<{collapse: boolean, result: any}>();
   @ViewChild("parameterDialog") parameterDialog: TemplateRef<any>;
   formValid = () => this.isValid();
   selector: number = RepositoryEntryType.VIEWSHEET | RepositoryEntryType.FOLDER;

   _model: HyperlinkDialogModel;
   editing: boolean = false;
   LinkType = LinkType;
   valueType: ValueType = ValueType.VALUE;
   ValueType = ValueType;
   selectedParam: number = -1;
   selectedNodePath: string = "";
   rootNode: TreeNodeModel = null;
   selectedNode: TreeNodeModel = null;
   _bookmarks: string[] = [];
   viewsheetParameters: string[] = [];
   expressionTypes: ExpressionType[] = [ExpressionType.JS];
   _expressionValue: ExpressionValue = <ExpressionValue>{
      expression: "=",
      type: ExpressionType.JS
   };

   @Input() set model(model: HyperlinkDialogModel) {
      this._model = model;

      if(model.webLink) {
         if(model.webLink.startsWith("hyperlink:")) {
            this.valueType = ValueType.FIELD;
         }
         else if(model.webLink.startsWith("=")) {
            this.expressionValue.expression = model.webLink;
            this.valueType = ValueType.EXPRESSION;
         }
      }
   }

   get model(): HyperlinkDialogModel {
      return this._model;
   }

   paramListElementToString: (x: InputParameterDialogModel) => string =
      (x: InputParameterDialogModel) => {
         if(x.value && x.value.name) {
            return x.name + ":[" + x.value.name + "]";
         }
         else if(x.valueSource == "constant") {
            if(x.type === XSchema.DATE) {
               return x.name + ":{d '" + x.value + "'}";
            }
            else if(x.type === XSchema.TIME) {
               return x.name + ":{t '" + x.value + "'}";
            }
            else if(x.type === XSchema.TIME_INSTANT) {
               return x.name + ":{ts '" + x.value + "'}";
            }
            else {
               return x.name + ":" + x.value;
            }
         }
         else {
            return x.name + ":[" + x.value + "]";
         }
      };

   constructor(private trapService: VSTrapService,
               private modalService: NgbModal,
               private modelService: ModelService,
               private http: HttpClient,
               private repositoryTreeService: RepositoryTreeService)
   {
   }

   ngOnInit(): void {
      this.getBookmarks();

      if(this.model.linkType != LinkType.WEB_LINK && this.model.linkType != this.LinkType.NONE) {
         this.loadRepositoryTree();
         this.updateParameters();
      }

      if(this.model.linkType == LinkType.WEB_LINK) {
         if(this.model.webLink.startsWith("hyperlink:")) {
            this.valueType = ValueType.FIELD;
         }

         if(this.model.webLink.startsWith("=") || this.model.webLink.startsWith("message:")) {
            this.expressionValue.expression = this.model.webLink;
         }
      }
   }

   expressionChange(value: ExpressionValue) {
      if(!!value?.expression && !value.expression.startsWith("=")) {
         value.expression = `=${value.expression}`;
      }

      this.model.webLink = value.expression;

      if(value.expression.startsWith(`="message:`)) {
         this.model.linkType = LinkType.MESSAGE_LINK;
      }
      else {
         this.model.linkType = LinkType.WEB_LINK;
      }
   }

   get paramSelected(): boolean {
      return this.selectedParam !== -1;
   }

   get parameterModel(): InputParameterDialogModel {
      return this.paramSelected ? this.model.paramList[this.selectedParam] : null;
   }

   get bookmarks(): string[] {
      return this.isAssetLink() ? this._bookmarks : [];
   }

   get expressionValue(): ExpressionValue {
      return this._expressionValue;
   }

   set expressionValue(value: ExpressionValue) {
      this._expressionValue = value;
   }

   chooseLink(value: LinkType): void {
      this.model.linkType = value;
      this.loadRepositoryTree();
   }

   getLinkType(fileType: RepositoryEntryType): number {
      if(fileType === RepositoryEntryType.VIEWSHEET) {
         return LinkType.VIEWSHEET_LINK;
      }
      else {
         return 0;
      }
   }

   select(index: number): void {
      this.selectedParam = index;
   }

   selectWebLink(field: string): void {
      // String "hyperlink:" comes from Hyperlink.java (Hyperlink.Ref.setLinkData)
      this.model.webLink = field;
      this.model.linkType = LinkType.WEB_LINK;
   }

   resetTargetFrame(): void {
      this.model.targetFrame = "";
   }

   isAssetLink(): boolean {
      return !(this.model.linkType & (1 | 16));
   }

   getBookmarks(): void {
      // Only get bookmarks for viewsheets
      if(this.model.linkType === 8) {
         this.http.get("../api/composer/vs/hyperlink-dialog-model/bookmarks/"
                       + Tool.encodeURIPath(this.model.assetLinkId))
            .subscribe(
            (data) => {
               this._bookmarks = <string[]>data;
            },
            (error) => {
               console.error("Failed to get bookmarks: ", error);
            }
         );
      }
   }

   bookmarkSelected(bookmark: string): boolean {
      let userNameIndex = bookmark.lastIndexOf("(");

      if(userNameIndex > 0) {
         let bookmarkName = bookmark.substring(0, userNameIndex);
         return bookmarkName == this.model.bookmark;
      }
      else {
         return bookmark == this.model.bookmark;
      }
   }

   add(): void {
      this.editing = false;
      this.modalService.open(this.parameterDialog, { backdrop: "static" }).result.then(
         (result: InputParameterDialogModel) => {
            if(this.model.paramList) {
               this.model.paramList.push(result);
            }
            else {
               this.model.paramList = [result];
            }
         },
         (reject) => {
         }
      );
   }

   edit(): void {
      this.editing = true;
      this.modalService.open(this.parameterDialog).result.then(
         (result: InputParameterDialogModel) => {
            this.model.paramList[this.selectedParam] = result;
         },
         (reject) => {
         }
      );
   }

   remove(): void {
      this.model.paramList.splice(this.selectedParam, 1);
      this.selectedParam = -1;
   }

   close(): void {
      this.cancel();
   }

   ok() {
      this.submit(true);
   }

   private submit(commit: boolean, collapse: boolean = false) {
      if(this.model.self == true && this.model.linkType !== LinkType.NONE) {
         this.model.targetFrame = "SELF";
      }

      if(this.model.linkType === LinkType.WEB_LINK) {
         if(this.model.webLink.startsWith("message:")) {
            this.model.linkType = LinkType.MESSAGE_LINK;
         }
      }

      const { fields, ...modelClone } = this.model;
      const model: HyperlinkDialogModel = { ...modelClone, fields: [] };

      // Check trap
      const trapInfo = new TrapInfo(CHECK_TRAP_REST_URI, this.objectName, this.runtimeId,
                                    this.model);

      this.trapService.checkTrap(trapInfo, () => {
         if(commit) {
            this.onCommit.emit(model);
         }
         else {
            this.onApply.emit({collapse: collapse, result: model});
         }
      }, () => {
      }, () => {
         if(commit) {
            this.onCommit.emit(model);
         }
         else {
            this.onApply.emit({collapse: collapse, result: model});
         }
      });
   }

   apply(event: boolean): void {
      this.submit(false, event);
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }

   isValid(): boolean {
      return !((this.model.linkType == LinkType.WEB_LINK ||
                this.model.linkType == LinkType.MESSAGE_LINK)
               && !this.model.webLink || this.isAssetLink() && !this.model.assetLinkPath);
   }

   assetNodeSelected(node: TreeNodeModel, dropdown: NgbDropdown) {
      this.nodeSelected(node);

      if(node.leaf) {
         dropdown.close();
      }
   }

   nodeSelected(node: TreeNodeModel) {
      const entry: RepositoryEntry = node.data;
      this.model.assetLinkId = entry.entry ? entry.entry.identifier : entry.path;
      this.model.assetLinkPath = entry.type !== RepositoryEntryType.FOLDER ? entry.path : null;
      this.selectedNodePath =
         this.repositoryTreeService.getAliasedPath(entry.path, this.rootNode);
      this.model.linkType = this.getLinkType(entry.type);

      if(entry.entry) {
         this.getBookmarks();
      }
      else {
         this._bookmarks = [];
      }

      if(entry.type !== RepositoryEntryType.FOLDER) {
         this.updateParameters();
      }
   }

   private updateParameters() {
      if(this.model.linkType == LinkType.VIEWSHEET_LINK && this.model.assetLinkId) {
         const params: HttpParams = new HttpParams()
            .set("assetId", this.model.assetLinkId);
         this.modelService.getModel<string[]>("../api/composer/vs/hyperlink-parameters", params)
            .subscribe((vars) => {
               this.viewsheetParameters = vars;
         });
      }
   }

   private loadAllNodes(node: TreeNodeModel) {
      for(let child of node.children) {
         if(!child.leaf) {
            this.repositoryTreeService.getFolder(child.data.path, null,
               this.selector, null, null, null,
               true, true, false, false, true)
               .subscribe(
                  (data) => {
                     child.children = data.children;

                     if(!this.selectedNode ||
                        this.selectedNode.data.path != this.model.assetLinkPath)
                     {
                        this.selectedNode = GuiTool.getNodeByPath(this.model.assetLinkPath,
                           this.rootNode);
                     }

                     this.loadAllNodes(child);
                  }, () => {}, () => {
                     this.selectedNodePath =
                        this.repositoryTreeService
                           .getAliasedPath(this.model.assetLinkPath, this.rootNode);
                  });
         }
      }
   }

   private loadRepositoryTree() {
      const params = new HttpParams().set("path", "/");
      this.modelService.getModel(GET_REPOSITORY_TREE_URI, params)
         .subscribe((rootNode: TreeNodeModel) => {
            this.rootNode = rootNode;
            this.rootNode.expanded = true;
            this.loadAllNodes(this.rootNode);
         });
   }

   getBookMarkValue(bookmark: string): string {
      let userIndex = bookmark.lastIndexOf("(");

      if(userIndex > 0) {
         let bookmarkValue = bookmark.substring(0, userIndex);
         return bookmarkValue;
      }

      return bookmark;
   }

   selectType(type: ValueType): void {
      this.valueType = type;
      this.model.webLink = "";
   }

   getBookmark(): string {
      if(!!this.model && this._bookmarks.length > 0) {
         const index = this._bookmarks.map(bk => this.getBookMarkValue(bk)).indexOf(this.model.bookmark);

         if(index > -1) {
            return this.model.bookmark;
         }
         else {
            const defBookmark = this.getBookMarkValue(this._bookmarks[0]);
            this.model.bookmark = defBookmark;

            return defBookmark;
         }
      }

      return null;
   }

   changeBookmark(bookmark: string): void {
      this.model.bookmark = bookmark;
   }
}
