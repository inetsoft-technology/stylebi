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
import { FlatTreeControl } from "@angular/cdk/tree";
import { HttpErrorResponse } from "@angular/common/http";
import { Component, Inject, OnDestroy, OnInit } from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup, Validators } from "@angular/forms";
import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { MatTreeFlatDataSource, MatTreeFlattener } from "@angular/material/tree";
import { Observable, of as observableOf, Subject, throwError } from "rxjs";
import { catchError, debounceTime, distinctUntilChanged, map, tap } from "rxjs/operators";
import {
   EmailListModel,
   EmailListService,
   GroupEmailModel,
   UserEmailModel
} from "../../schedule/task-action-pane/email-list.service";
import { Tool } from "../../../../../../shared/util/tool";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { SearchComparator } from "../../../../../../portal/src/app/widget/tree/search-comparator";

export interface EmailListDialogData {
   emails: string[];
   emailIdentities: string[];
   autocompleteEmails?: boolean;
}

export class EmailNode {
   children: EmailNode[];
   name: string;
   label: string;
   email: string;
   group: boolean;
}

export class EmailFlatNode {
   constructor(public expandable: boolean, public name: string, public label: string,
               public email: string, public level: number, public group: boolean) {
   }
}

@Component({
   selector: "em-email-list-dialog",
   templateUrl: "./email-list-dialog.component.html",
   styleUrls: ["./email-list-dialog.component.scss"]
})
export class EmailListDialogComponent implements OnInit, OnDestroy {
   autocompleteEmails = true;
   emails: string[] = [];
   emailIdentities: string[] = [];
   treeControl: FlatTreeControl<EmailFlatNode>;
   treeFlattener: MatTreeFlattener<EmailNode, EmailFlatNode>;
   dataSource: MatTreeFlatDataSource<EmailNode, EmailFlatNode>;
   originalDataSource: EmailNode[];
   form: UntypedFormGroup;
   hasChild = (_: number, nodeData: EmailFlatNode) => nodeData.expandable;
   transformer = (node: EmailNode, level: number) =>
      new EmailFlatNode(!!node.children, node.name, node.label, node.email, level, node.group);

   private emailList: EmailListModel;
   private getLevel = (node: EmailFlatNode) => node.level;
   private isExpandable = (node: EmailFlatNode) => node.expandable;
   private getChildren = (node: EmailNode): Observable<EmailNode[]> => observableOf(node.children);
   private searchTextchanges$ = new Subject<string>();

   constructor(private dialog: MatDialogRef<EmailListDialogComponent>,
               private snackBar: MatSnackBar, private listService: EmailListService,
               @Inject(MAT_DIALOG_DATA) data: EmailListDialogData,
               fb: UntypedFormBuilder) {
      this.treeFlattener = new MatTreeFlattener(
         this.transformer, this.getLevel, this.isExpandable, this.getChildren);
      this.treeControl = new FlatTreeControl<EmailFlatNode>(this.getLevel, this.isExpandable);
      this.dataSource = new MatTreeFlatDataSource(this.treeControl, this.treeFlattener);
      this.emails = data.emails;
      this.emailIdentities = data.emailIdentities;
      this.sortIdentities();
      this.autocompleteEmails = !!data.autocompleteEmails;
      this.form = fb.group({
         "email": ["", [FormValidators.emailList(",", true, false, this.emailIdentities)]]
      });

      this.form.get("email").valueChanges.subscribe((val) => {
         let strs: string[] = !!val ? val.split(",") : null;

         if(!strs || !(strs.some((str) => this.emailIdentities.indexOf(str) == -1))) {
            this.treeControl.collapseAll();
            this.dataSource.data = this.originalDataSource;
            return;
         }

         this.searchTextchanges$.next(val);
      });
   }

   ngOnInit() {
      if(this.autocompleteEmails) {
         this.listService.getEmailList()
            .pipe(
               tap(list => this.emailList = list),
               catchError(error => this.handleError(error)),
               map(list => this.createEmailTree(list, ""))
            )
            .subscribe(data => {
               this.originalDataSource = Tool.clone(data);
               this.dataSource.data = data;
            });
      }

      this.searchTextchanges$.pipe(
            debounceTime(300),
            distinctUntilChanged()
         )
         .subscribe((val: string) => {
            let splits: string[] = val.split(",");
            let searchStr = splits[splits.length - 1];
            this.dataSource.data = this.searchEmailTree(Tool.clone(this.originalDataSource), searchStr);
            this.treeControl.expandAll();
         });
   }

   ngOnDestroy() {
      this.searchTextchanges$.unsubscribe();
   }

   close() {
      this.dialog.close();
   }

   addEmail(): void {
      const newEmails: string[] = this.form.get("email").value.split(",");

      for(const newEmail of newEmails) {
         if(newEmail.endsWith(Tool.GROUP_SUFFIX)) {
            this.addGroup(newEmail);
         }
         //add email or user
         else if(!this.emails.includes(newEmail)) {
            this.emails.push(newEmail);
         }
         else {
            const message = newEmail.endsWith(Tool.USER_SUFFIX) ?
                "_#(js:em.schedule.listUserAdded)" : "_#(js:em.schedule.listEmailsDuplicate)";
            this.snackBar.open(message, null, {duration: Tool.SNACKBAR_DURATION});
         }
      }

      this.sortIdentities();
      this.form.get("email").setValue(null);
   }

   addGroup(newEmail: string) {
      let selectedGroup: GroupEmailModel = this.findGroup(newEmail);
      let include: boolean = false;

      if(selectedGroup != null) {
         let memberOf: string[] = selectedGroup.memberOf;
         let added: string = null;

         //remove group child
         for(let group of this.emailList.groups) {
            if(group.memberOf.indexOf(selectedGroup.name) != -1 &&
               this.emails.includes(group.name + Tool.GROUP_SUFFIX))
            {
               this.removeEmail(group.name + Tool.GROUP_SUFFIX);
            }
         }

         for(let m of memberOf) {
            if(this.emails.includes(m + Tool.GROUP_SUFFIX)) {
               added = m;
               include = true;
            }
         }
      }

      if(!include && !this.emails.includes(newEmail) && this.emailIdentities.includes(newEmail)) {
         this.emails.push(newEmail);
      }
      else {
         this.snackBar.open("_#(js:em.schedule.listParentGroupAdded)",
            null, {duration: Tool.SNACKBAR_DURATION});
      }
   }

   removeEmail(email: string): void {
      this.emails = this.emails.filter(e => e !== email);
   }

   sortIdentities(): void {
      if(this.emails && this.emails.length > 0) {
         this.emails = this.emails.sort((a, b) =>
            this.getIdentityType(a) == this.getIdentityType(b) ? a.toLowerCase() < b.toLowerCase() ?
               -1 : 1 : this.getIdentityType(a) - this.getIdentityType(b));
      }
   }

   private findGroup(email: string): GroupEmailModel {
      let selectedGroup: GroupEmailModel = null;
      let index = this.emailList.groups.findIndex(g =>
         g.name == email.substring(0, email.lastIndexOf(Tool.GROUP_SUFFIX)));

      if(index != -1) {
         selectedGroup = this.emailList.groups[index];
      }

      return selectedGroup;
   }

   private getIdentityType(email: string) {
      return email.endsWith(Tool.GROUP_SUFFIX) ? 1 : email.endsWith(Tool.USER_SUFFIX) ? 0 : 2;
   }

   getIdentityIcon(email: string) {
      return email.endsWith(Tool.GROUP_SUFFIX) ?
         "user-group-icon" : email.endsWith(Tool.USER_SUFFIX) ? "account-icon" : "";
   }

   private createEmailTree(list: EmailListModel, searchStr: string): EmailNode[] {
      const groups = new Map<string, [GroupEmailModel, EmailNode]>();
      const users = new Map<string, [UserEmailModel, EmailNode]>();

      list.groups.filter(group => !!group.name)
         .forEach(group => {
            const node = new EmailNode();
            node.name = group.name;
            node.label = group.label;
            node.group = true;
            groups.set(group.name, [group, node]);
      });

      groups.forEach(value => {
         value[0].memberOf.forEach(parent => {
            const node = groups.get(parent)[1];

            if(!node.children) {
               node.children = [];
            }

            node.children.push(value[1]);
         });
      });

      list.users.filter(user => this.emailIdentities.includes(user.userID.name + "(User)"))
         .forEach(user => {
            if(user.email) {
               const node = new EmailNode();
               node.name = user.userID.name;
               node.label = user.userID.name;
               node.email = user.email;
               node.group = false;
               users.set(user.userID.name, [user, node]);
            }
      });

      users.forEach(value => {
         value[0].memberOf.filter(parent => !!parent)
            .forEach(parent => {
               const node = groups.get(parent)[1];

               if(!node.children) {
                  node.children = [];
               }

               node.children.push(value[1]);
         });
      });

      let found = false;

      do {
         const removed: string[] = [];

         groups.forEach(value => {
            if(!value[1].children || value[1].children.length === 0) {
               removed.push(value[0].name);

               value[0].memberOf.forEach(parent => {
                  const node = groups.get(parent)[1];
                  node.children = node.children.filter(n => n.name !== value[0].name);
               });
            }
         });

         found = removed.length > 0;
         removed.forEach(group => groups.delete(group));
      }
      while(found);

      let roots: EmailNode[] = [];

      groups.forEach(value => {
         if(value[0].memberOf.length === 0) {
            roots.push(value[1]);
         }
      });

      users.forEach(value => {
         if(value[0].memberOf.length === 0 || value[0].memberOf.length == 1 && Tool.isEmpty(value[0].memberOf[0])) {
            roots.push(value[1]);
         }
      });

      roots = this.searchEmailTree(roots, searchStr);
      roots.forEach(node => this.setParentEmail(node));

      return roots;
   }

   private searchEmailTree(roots: EmailNode[], searchStr: string): EmailNode[] {
      if(!searchStr) {
         return roots;
      }

      return roots.filter((node) => this.searchEmailNode(node, searchStr))
         .sort((a, b) => new SearchComparator(searchStr).searchSortStr(a.name, b.name));
   }

   searchEmailNode(node: EmailNode, searchStr: string): boolean {
      if(!node.group) {
         return node.name.toLowerCase().includes(searchStr.toLowerCase());
      }
      //parent match
      else if(node.children.length > 0 && node.name.toLowerCase().includes(searchStr.toLowerCase())) {
         return true;
      }
      else if(node.children.length > 0) {
         node.children = node.children.filter((child) => this.searchEmailNode(child, searchStr));

         return node.children.length > 0;
      }
      else {
         return false;
      }
   }

   private setParentEmail(node: EmailNode): string {
      if(!!node.children && node.children.length != 0) {
         for(let index = 0; index < node.children.length; index ++) {
            this.setParentEmail(node.children[index]);
         }

         node.email = node.children
            .map(child => this.setParentEmail(child)).join(",");
      }

      return node.email;
   }

   private handleError(error: HttpErrorResponse): Observable<EmailListModel> {
      this.snackBar.open(error.message, null, {
         duration: Tool.SNACKBAR_DURATION,
      });
      console.error("Failed to list email addresses: ", error);
      return throwError(error);
   }

   public onSelection(event: any) {
      const newEmail = event.option.value;

      if(newEmail.endsWith(Tool.GROUP_SUFFIX)) {
         this.addGroup(newEmail);
      }
      else if(!this.emails.includes(newEmail)) {
         this.emails.push(newEmail);
      }

      this.sortIdentities();
   }
}
