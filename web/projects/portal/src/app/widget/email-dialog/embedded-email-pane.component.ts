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
import { Component, Input, ViewChild, OnInit, EventEmitter, Output, OnDestroy } from "@angular/core";
import { EmailAddrDialogModel } from "./email-addr-dialog-model";
import { IdentityTreeComponent } from "../identity-tree/identity-tree.component";
import { TreeNodeModel } from "../tree/tree-node-model";
import { IdentityType } from "../../../../../shared/data/identity-type";
import { Tool } from "../../../../../shared/util/tool";
import { UntypedFormControl, UntypedFormGroup } from "@angular/forms";
import { debounceTime, distinctUntilChanged } from "rxjs/operators";
import { IdentityModel } from "../../../../../em/src/app/settings/security/security-table-view/identity-model";
import { SearchComparator } from "../tree/search-comparator";
import { FormValidators } from "../../../../../shared/util/form-validators";
import { Subject } from "rxjs";
import { GuiTool } from "../../common/util/gui-tool";
import { ModelService } from "../services/model.service";
import { HttpParams } from "@angular/common/http";
import { equalsIdentity, IdentityId } from "../../../../../em/src/app/settings/security/users/identity-id";

const EXPAND_IDENTITY_NODE_URI = "../api/vs/expand-identity-node";

@Component({
   selector: "embedded-email-pane",
   templateUrl: "embedded-email-pane.component.html",
   styleUrls: ["embedded-email-pane.component.scss"]
})
export class EmbeddedEmailPane implements OnInit, OnDestroy {
   @Input() embeddedOnly: boolean = true;
   @Input() showRoot: boolean = true;
   @Input() model: EmailAddrDialogModel;
   @Input() emailForm: UntypedFormGroup;
   @Output() addressesChange: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild("searchIdentityTree") searchIdentityTree: IdentityTreeComponent;
   @ViewChild("identityTree") identityTree: IdentityTreeComponent;
   public IdentityType = IdentityType;
   otherEmail: string = "";
   addedIdentities: IdentityModel[] = [];
   addedEmails: string[] = [];
   selectedAddedIdentities: IdentityModel[] = [];
   selectedNodes: TreeNodeModel[] = [];
   searchMode: boolean = false;
   searchIdentityMode = false;
   searchText: string = "";
   searchIdentityText: string = "";
   searchTree: TreeNodeModel = <TreeNodeModel> {
      label: "_#(js:Root)",
      data: "",
      type: String(IdentityType.ROOT),
      leaf: false,
      expanded: true,
      children: []
   };
   initialAddresses: string = null;
   _addresses: string;
   usersNode: TreeNodeModel[] = [];
   private searchTextchanges$ = new Subject<string>();
   private searchIdentitychanges$ = new Subject<string>();
   mobile: boolean;
   currOrg: string;

   @Input() set addresses(value: string) {
      if(this.initialAddresses == null) {
         this.initialAddresses = value ? Tool.clone(value) : "";
      }

      this._addresses = value;
   }

   get addresses(): string {
      return this._addresses;
   }

   constructor(private modelService: ModelService) {
      let params = new HttpParams()
         .set("name", "_#(js:Users)")
         .set("type", String(IdentityType.USERS));

      this.modelService.getCurrentOrganization().subscribe((org)=>{this.currOrg=org;});

      this.modelService.getModel(EXPAND_IDENTITY_NODE_URI, params)
         .subscribe(
            (data: TreeNodeModel[]) => {
               this.usersNode = data;
               this.reset();
            },
            (err) => {
               console.error("Failed to expand identity node: ", err);
            }
         );
   }

   ngOnInit(): void {
      this.reset();
      this.initForm();
      this.mobile = GuiTool.isMobileDevice();

      this.emailForm.controls["otherEmail"].valueChanges
         .pipe(debounceTime(300), distinctUntilChanged())
         .subscribe((newValue: string) => {
            this.updateOtherEmail(newValue);
         });
   }

   initForm() {
      this.emailForm = !!this.emailForm ? this.emailForm : new UntypedFormGroup({});
      this.emailForm.addControl("otherEmail", new UntypedFormControl(this.otherEmail, [
         FormValidators.emailList(",;", true, false, this.getEmailUsers()),
         FormValidators.duplicateTokens()
      ]));

      this.searchTextchanges$
         .pipe(
            debounceTime(300),
            distinctUntilChanged()
         )
         .subscribe((val: string) => this.searchUsers(val));

      this.searchIdentitychanges$
         .pipe(
            debounceTime(300),
            distinctUntilChanged()
         )
         .subscribe((val: string) => this.onSearchIdentity(val));
   }

   ngOnDestroy(): void {
      this.searchTextchanges$.unsubscribe();
      this.searchIdentitychanges$.unsubscribe();
   }

   updateSearchText(str: string) {
      this.searchText = str;
      this.searchTextchanges$.next(str);
   }

   updateSearchIdentityText(str: string) {
      this.searchIdentityText = str;
      this.searchIdentitychanges$.next(str);
   }

   nodeSelected(nodes: TreeNodeModel[]): void {
      this.selectedNodes = [];

      if(nodes) {
         for(let node of nodes) {
            if(Number(node.type) == IdentityType.USER || Number(node.type) == IdentityType.GROUP ||
               Number(node.type) == IdentityType.USERS || Number(node.type) == IdentityType.GROUPS)
            {
               this.selectedNodes.push(node);
            }
         }
      }
   }

   select(identity: IdentityModel , event: MouseEvent): void {
      if(event.ctrlKey || event.metaKey) {
         let index: number = this.findIdentityIndex(this.selectedAddedIdentities, identity);

         if(index == -1) {
            this.selectedAddedIdentities.push(identity);
         }
         else {
            this.selectedAddedIdentities.splice(index, 1);
         }
      }
      else if(event.shiftKey && this.selectedAddedIdentities.length == 0) {
         this.selectedAddedIdentities.push(identity);
      }
      else if(event.shiftKey) {
         let lastSelectedItem: IdentityModel = this.selectedAddedIdentities[this.selectedAddedIdentities.length - 1];
         let last = this.addedIdentities.findIndex((item) =>
            item.type == lastSelectedItem.type && item.identityID == lastSelectedItem.identityID);

         let lastIndex = this.addedIdentities.findIndex((item) =>
            item.type == identity.type && item.identityID == identity.identityID);

         if(last <= lastIndex) {
            for(let i = last; i <= lastIndex; i++) {
               this.selectedAddedIdentities.push(this.addedIdentities[i]);
            }
         }
         else {
            this.selectedAddedIdentities.push(identity);
         }
      }
      else {
         this.selectedAddedIdentities = [identity];
      }
   }

   sortIdentities(): void {
      if(this.addedIdentities && this.addedIdentities.length > 0) {
         this.addedIdentities = this.addedIdentities.sort((a, b) =>
            a.type - b.type == 0 ? a.identityID.name.toLowerCase() < b.identityID.name.toLowerCase() ? -1 : 1 : a.type - b.type);
      }
   }

   addIdentities(): void {
      for(let node of this.selectedNodes) {
         if(node.type == IdentityType.USERS + "" || node.type == IdentityType.GROUPS + "") {
            if(!node.children) {
               continue;
            }

            for(let item of node.children) {
               this.addIdentity(item);
            }
         }
         else {
            this.addIdentity(node);
         }
      }

      const sibling = this.findNextNode(this.selectedNodes[this.selectedNodes.length - 1]);

      if(sibling) {
         this.selectedNodes = [sibling];
      }

      this.addressChange();
   }

   addIdentity(node: TreeNodeModel) {
      let identity: IdentityModel = {identityID: {name:node.label, organization: node.organization}, type: parseInt(node.type, 10), alias: node.alias};

      if(node.type == IdentityType.USER + "" && this.findIdentityIndex(this.addedIdentities, identity) === -1) {
         this.addedIdentities.push(identity);
         this.addedEmails.push(node.data);
      }
      else if(node.type == IdentityType.GROUP + "") {
         identity.parentNode = node.data.parentNode;
         let path: string = node.data.parentNode == "/" ? node.label : node.data.parentNode + "/" + node.label;
         let strs: string[] = path.split("/");

         for(let str of strs) {
            if(this.findIdentityIndex(this.addedIdentities, {identityID: {name: str, organization: node.organization}, type: identity.type}) != -1) {
               return;
            }

            this.addedIdentities = this.addedIdentities.filter((i) =>
               i.type == IdentityType.USER || i.parentNode == "/" ||
               !(this.getGroupPath(i).startsWith(this.getGroupPath(identity))));
         }

         this.addedIdentities.push(identity);
      }
   }

   removeIdentities(): void {
      for(let identity of this.selectedAddedIdentities) {
         if(identity.type == IdentityType.USER) {
            let index = this.findIdentityIndex(this.addedIdentities, identity);
            this.addedIdentities.splice(index, 1);
            this.addedEmails.splice(index, 1);
         }
         else if(identity.type == IdentityType.GROUP) {
            let index = this.findIdentityIndex(this.addedIdentities, identity);
            this.addedIdentities.splice(index, 1);
         }
         else {
            let index = this.findIdentityIndex(this.addedIdentities, identity);
            this.addedIdentities.splice(index, 1);
         }
      }

      this.selectedAddedIdentities = this.addedIdentities.length > 0 ? [this.addedIdentities[0]] : [];
      this.addressChange();
   }

   addDisable() {
      return !!!this.selectedNodes || this.selectedNodes.length == 0 ||
         this.selectedNodes.some((node) => (node.type == IdentityType.USERS + "" ||
            node.type == IdentityType.GROUPS + "") && node.children.length == 0);
   }

   findIdentityIndex(identities: IdentityModel[], identity: IdentityModel): number {
      return identities.findIndex(i => i.type == identity.type && equalsIdentity(i.identityID, identity.identityID));
   }

   isSelectedIdentity(identity: IdentityModel) {
      return this.findIdentityIndex(this.selectedAddedIdentities, identity) != -1;
   }

   getIdentityIcon(type: number) {
      return type == IdentityType.USER ? "account-icon" :  "user-group-icon";
   }

   onSearchIdentity(val: string) {
      this.searchIdentityMode = !!val;
   }

   searchAllIdentities() {
      this.sortIdentities();

      if(!this.searchIdentityMode) {
         return this.addedIdentities;
      }

      return this.addedIdentities
         .filter((identity) => identity.identityID.name.toLowerCase().includes(this.searchIdentityText.toLowerCase()))
         .sort((a, b) =>
            new SearchComparator(this.searchText).searchSortStr(a.identityID.name, b.identityID.name));
   }

   searchUsers(str: string): void {
      this.searchText = str;

      if(this.searchText && this.searchText.length > 0) {
         this.selectedNodes = [];
         this.searchTree.children = [];
         this.searchTree.expanded = true;
         this.searchText = this.searchText.trim();
         this.searchIdentityTree.searchMode = true;
         this.searchIdentityTree.nodeExpanded(this.searchTree);
         this.searchMode = true;
      }
      else if(this.searchMode) {
         this.selectedNodes = [];
         this.identityTree.tree.selectedNodes = [];
         this.searchIdentityTree.searchMode = false;
         this.searchMode = false;
      }
   }

   updateOtherEmail(email: string): void {
      this.otherEmail = email;
      this.addressChange();
   }

   changeEmail(index: number, email: string): void {
      this.addedEmails[index] = !!email ? email : "";
      this.addressChange();
   }

   addressChange(): void {
      let result: string;

      if(this.embeddedOnly) {
         result = this.addedIdentities
            .map((identity) => identity.type == IdentityType.USER ?
               identity.identityID.name + Tool.USER_SUFFIX : identity.identityID.name + Tool.GROUP_SUFFIX)
            .join(",") + (this.otherEmail ?
            (this.addedIdentities.length > 0 ? "," + this.otherEmail : this.otherEmail) : "");
      }
      else {
         result = "";

         for(let i = 0; i < this.addedIdentities.length; i++) {
            let userEmail: string = !!this.addedEmails[i] ? this.addedEmails[i] : "";
            const email: string = this.addedIdentities[i].identityID + ":" + userEmail;
            result += email + (i == this.addedIdentities.length - 1 ? "" : ",");
         }
      }

      this.addressesChange.emit(result);
   }

   reset(): void {
      if(this.embeddedOnly) {
         this.otherEmail = "";
         this.addedIdentities = [];
         let addrs: string[] = this.initialAddresses ? this.initialAddresses.split(",") : [];

         for(let i = 0; i < addrs.length; i++) {
            if(!addrs[i]) {
               continue;
            }

            if(addrs[i].endsWith(Tool.USER_SUFFIX)) {
               let name = addrs[i].substring(0, addrs[i].lastIndexOf(Tool.USER_SUFFIX));
               this.addedIdentities.push({
                  identityID: {name: name, organization: this.currOrg},
                  type: IdentityType.USER,
                  alias: this.getUserAlias(name)
               });
            }
            else if(addrs[i].endsWith(Tool.GROUP_SUFFIX)) {
               this.addedIdentities.push({identityID: {name: addrs[i].substring(0, addrs[i].lastIndexOf(Tool.GROUP_SUFFIX)), organization: this.currOrg},
                  type: IdentityType.GROUP});
            }
            else if(Tool.isValidEmail(addrs[i])) {
               this.otherEmail += this.otherEmail.length > 0 ? "," + addrs[i] : addrs[i];
            }
         }

         this.emailForm?.controls["otherEmail"]?.setValue(this.otherEmail);
      }
      else {
         this.addedIdentities = [];
         this.addedEmails = [];
         const addrs: string[] = this.addresses ? this.addresses.split(":") : [];

         if(addrs && addrs.length > 0 &&  addrs[0].substring(0, 7) === "query: ") {
            return;
         }

         if(this.addresses) {
            if(this.addresses.endsWith(":")) {
               addrs.pop();
            }

            this.addedIdentities.push({
               identityID: {name: addrs[0], organization: this.currOrg},
               type: IdentityType.USER,
               alias: this.getUserAlias(addrs[0])
            });
         }

         for(let i = 1; i < addrs.length; i++) {
            const addr = addrs[i];

            if(i == addrs.length - 1 && !this.addresses.endsWith(":")) {
               this.addedEmails.push(addr);
            }
            else {
               const email = addr.substring(0, addr.lastIndexOf(","));
               const nextUser = addr.substring(addr.lastIndexOf(",") + 1);
               this.addedEmails.push(email);
               this.addedIdentities.push({
                  identityID: {name: nextUser, organization: this.currOrg},
                  type: IdentityType.USER,
                  alias: this.getUserAlias(nextUser)
               });
            }
         }
      }

      this.addressChange();
   }

   // find the sibling of node
   private findNextNode(node: TreeNodeModel) {
      const parent = GuiTool.findNode(this.model.rootTree, n => this.isChild(n, node));

      if(parent) {
         for(let i = 0; i < parent.children.length; i++) {
            if(JSON.stringify(parent.children[i]) == JSON.stringify(node)) {
               if(i < parent.children.length - 1) {
                  return parent.children[i + 1];
               }
            }
         }
      }

      return null;
   }

   // check if node is a child of p
   private isChild(p: TreeNodeModel, node: TreeNodeModel): boolean {
      return p.children &&
         p.children.some(n => JSON.stringify(n.data) == JSON.stringify(node.data));
   }

   private getEmailUsers(): string[] {
      let identities: string[] = [];

      if(this.addedIdentities) {
         identities = this.addedIdentities.map(identity =>  identity.type == IdentityType.USER ?
            identity.identityID + Tool.USER_SUFFIX : identity.identityID + Tool.GROUP_SUFFIX);
      }

      return identities;
   }

   private getGroupPath(identity: IdentityModel) {
      return identity.parentNode == "/" ? identity.identityID.name : identity.parentNode + "/" + identity.identityID.name;
   }

   trackByIndex(index: number, value: number) {
      return index;
   }

   /**
    * Focus on the first item in list (if exists), when pressing down key.
    * @param {KeyboardEvent} event
    */
   focus(event: KeyboardEvent): void {
      const key: number = event.keyCode;
      const target: any = event.currentTarget;

      if(key == 40 && target.firstElementChild) {
         target.firstElementChild.focus();
      }
   }

   /**
    * Move focus up or down the list.
    * @param {KeyboardEvent} event
    */
   moveFocus(event: KeyboardEvent): void {
      const key: number = event.keyCode;
      const target: any = event.currentTarget;

      if(key == 40 && target.nextElementSibling) { // down
         target.nextElementSibling.focus();
      }
      else if(key == 38 && target.previousElementSibling) { // up
         target.previousElementSibling.focus();
      }
      else if(key == 8) {
         this.removeIdentities();
      }

      event.stopPropagation();
   }

   getUserAlias(name: string): string {
      if(this.usersNode && this.usersNode.length > 0) {
         return this.usersNode.find(user => user.label == name)?.alias;
      }

      return null;
   }
}
