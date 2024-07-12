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
import { Directive, EventEmitter, Input, Output } from "@angular/core";
import { Validators } from "@angular/forms";
import { NavigationExtras, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { RepositoryEntry } from "../../../../../shared/data/repository-entry";
import { RepositoryEntryType } from "../../../../../shared/data/repository-entry-type.enum";
import { FormValidators } from "../../../../../shared/util/form-validators";
import { AssemblyAction } from "../../common/action/assembly-action";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { ComponentTool } from "../../common/util/component-tool";
import { GuiTool } from "../../common/util/gui-tool";
import { MessageCommand } from "../../common/viewsheet-client/message-command";
import { MVTreeModel } from "../../portal/data/model/mv-tree-model";
import { AnalyzeMVDialog } from "../../portal/dialog/analyze-mv/analyze-mv-dialog.component";
import { InputNameDialog } from "../dialog/input-name-dialog/input-name-dialog.component";
import { NotificationsComponent } from "../notifications/notifications.component";
import { ModelService } from "../services/model.service";
import { TreeNodeModel } from "../tree/tree-node-model";
import { AddRepositoryFolderDialog } from "./add-repository-folder-dialog.component";
import { ChangeRepositoryEntryEvent } from "./change-repository-entry-event";
import { FavoritesEntryEvent } from "./favorites-entry-event";
import { RemoveRepositoryEntryEvent } from "./remove-repository-entry-event";
import { RenameRepositoryEntryEvent } from "./rename-repository-entry-event";
import { RepositoryTreeAction } from "./repository-tree-action.enum";
import { RepositoryTreeService } from "./repository-tree.service";
import { Tool } from "../../../../../shared/util/tool";

const RENAME_ENTRY_URI = "../api/portal/tree/rename";
const REMOVE_ENTRY_URI = "../api/portal/tree/remove";
const CHANGE_ENTRY_URI = "../api/portal/tree/change";
const REMOVE_FROM_FAVORITES_URI = "../api/portal/tree/favorites/remove";
const ADD_TO_FAVORITES_URI = "../api/portal/tree/favorites/add";
const CALC_SPECIAL_CHARS_MESSAGE = "_#(js:repository.tree.SpecialChar)";
const START_WITH_CHART_DIGIT_MESSAGE = "_#(js:asset.tree.checkStart)";

@Directive()
export abstract class RepositoryBaseComponent {
   @Input() isFavoritesTree: boolean = false;
   @Input() isRepositoryList: boolean = false;
   @Input() notifications: NotificationsComponent;
   @Output() editViewsheet = new EventEmitter<RepositoryEntry>();

   loading: boolean = false;

   constructor(protected repositoryTreeService: RepositoryTreeService,
               protected modalService: NgbModal, protected modelService: ModelService,
               protected router: Router)
   {
   }

   protected createActions0(node: TreeNodeModel, selectedNodes: TreeNodeModel[]): AssemblyActionGroup[] {
      let group = new AssemblyActionGroup([]);
      let groups = [group];
      let entry: RepositoryEntry = node.data;
      let entries: RepositoryEntry[] = selectedNodes.map((treeNode) => treeNode.data);

      // no action for file folder
      if(entry.fileFolder) {
         return null;
      }

      if(entries && entries.length > 1) {
         for(let i = 0; i < entries.length; i++) {
            let rentry: RepositoryEntry = entries[i];

            if(!rentry.op.includes(RepositoryTreeAction.DELETE)) {
               return groups;
            }
         }

         let deleteEnable: boolean = entry.op.includes(RepositoryTreeAction.DELETE);

         if(deleteEnable && !this.isFavoritesTree) {
            group.actions.push(this.createDeleteEntryAction(entries, deleteEnable));
         }

         return groups;
      }

      // if root
      if(entry.path === "/" || entry.path === "My Reports") {
         let newEnable = entry.op.includes(RepositoryTreeAction.NEW_FOLDER);

         if(newEnable && !this.isFavoritesTree) {
            group.actions.push(this.createNewFolderAction(entry, newEnable));
         }
      }
      else if(entry.type === RepositoryEntryType.FOLDER) {
         let renameEnable = entry.op.includes(RepositoryTreeAction.RENAME);

         if(renameEnable && !this.isFavoritesTree) {
            group.actions.push(this.createRenameEntryAction(node, renameEnable));
         }

         let deleteEnable = entry.op.includes(RepositoryTreeAction.DELETE);

         if(deleteEnable && !this.isFavoritesTree) {
            group.actions.push(this.createDeleteEntryAction(entries, deleteEnable));
         }

         let newEnable = entry.op.includes(RepositoryTreeAction.NEW_FOLDER);

         if(newEnable && !this.isFavoritesTree) {
            group.actions.push(this.createNewFolderAction(entry, newEnable));
         }

         if(renameEnable && newEnable && !this.isFavoritesTree) {
            group.actions.push(this.createEditFolderAction(entry, renameEnable && newEnable));
         }
      }
      else if(entry.type === RepositoryEntryType.VIEWSHEET) {
         let renameEnable = entry.op.includes(RepositoryTreeAction.RENAME);

         if(renameEnable && !this.isFavoritesTree) {
            group.actions.push(this.createRenameEntryAction(node, renameEnable));
         }

         let deleteEnable = entry.op.includes(RepositoryTreeAction.DELETE);

         if(deleteEnable && !this.isFavoritesTree) {
            group.actions.push(this.createDeleteEntryAction(entries, deleteEnable));
         }

         let materializeEnable = entry.op.includes(RepositoryTreeAction.MATERIALIZE);

         if(materializeEnable && entry.type === RepositoryEntryType.VIEWSHEET) {
            group.actions.push(this.createMaterializeAction(entries, materializeEnable));
         }

         let editVSEnable = entry.op.includes(RepositoryTreeAction.EDIT);

         if(editVSEnable && !this.isRepositoryList && !GuiTool.isMobileDevice()
            && entry.type === RepositoryEntryType.VIEWSHEET)
         {
            group.actions.push(this.createEditViewsheetAction(entry, editVSEnable));
         }

         group.actions.push(this.createOpenInNewTabAction(entry));
      }

      if(entry.path != "/") {
         if(!this.isFavoritesTree) {
            group.actions.push(this.createAddFavoritesAction(entry));
         }

         group.actions.push(this.createRemoveFavoritesAction(entry));
      }

      return groups;
   }

   private createNewFolderAction(parent: RepositoryEntry, newEnable: boolean): AssemblyAction {
      return {
         id: () => "repository-tree new-folder",
         label: () => "_#(js:New Folder)",
         icon: () => "",
         enabled: () => newEnable,
         visible: () => true,
         action: () => this.addFolder(parent)
      };
   }

   private createEditFolderAction(entry: RepositoryEntry, editEnable): AssemblyAction {
      return {
         id: () => "repository-tree edit-folder",
         label: () => "_#(js:Option)",
         icon: () => "",
         enabled: () => editEnable,
         visible: () => true,
         action: () => this.editFolder(entry)
      };
   }

   private createAddFavoritesAction(entry: RepositoryEntry): AssemblyAction {
      return {
         id: () => "repository-tree add-favorites",
         label: () => "_#(js:Add to Favorites)",
         icon: () => "",
         enabled: () => true,
         visible: () => !entry.favoritesUser && !this.isRepositoryList && !entry.auditReport,
         action: () => this.addToFavorites(entry)
      };
   }

   private createRemoveFavoritesAction(entry: RepositoryEntry): AssemblyAction {
      return {
         id: () => "repository-tree remove-favorites",
         label: () => "_#(js:Remove from Favorites)",
         icon: () => "",
         enabled: () => true,
         visible: () => entry.favoritesUser && !this.isRepositoryList && !entry.auditReport,
         action: () => this.removeFromFavorites(entry)
      };
   }

   private createRenameEntryAction(node: TreeNodeModel, renameEnable: boolean): AssemblyAction {
      return {
         id: () => "repository-tree rename-entry",
         label: () => "_#(js:Rename)",
         icon: () => "",
         enabled: () => renameEnable,
         visible: () => true,
         action: () => this.showRenameDialog(node)
      };
   }

   private createDeleteEntryAction(entries: RepositoryEntry[], deleteEnable: boolean): AssemblyAction {
      return {
         id: () => "repository-tree remove-entry",
         label: () => "_#(js:Delete)",
         icon: () => "",
         enabled: () => deleteEnable,
         visible: () => true,
         action: () => this.deleteEntries(entries)
      };
   }

   private createMaterializeAction(entries: RepositoryEntry[], materializeEnable: boolean): AssemblyAction {
      return {
         id: () => "repository-tree materialize",
         label: () => "_#(js:Materialize)",
         icon: () => "",
         enabled: () => materializeEnable,
         visible: () => true,
         action: () => this.materializeEntry(entries)
      };
   }

   private createOpenInNewTabAction(entry: RepositoryEntry): AssemblyAction {
      return {
         id: () => "repository-tree open-tab",
         label: () => "_#(js:Open In New Tab)",
         icon: () => "",
         enabled: () => true,
         visible: () => true,
         action: () => this.openInNewTab(entry)
      };
   }

   private createEditViewsheetAction(entry: RepositoryEntry, editVSEnable: boolean): AssemblyAction {
      return {
         id: () => "repository-tree edit-viewsheet",
         label: () => "_#(js:Edit)",
         icon: () => "",
         enabled: () => editVSEnable,
         visible: () => true,
         action: () => this.editViewsheet.emit(entry)
      };
   }

   private addFolder(parent: RepositoryEntry) {
      let dialog = ComponentTool.showDialog(this.modalService, AddRepositoryFolderDialog,
         () => {}, {backdrop: "static"}
      );
      dialog.entry = parent;
   }

   private editFolder(entry: RepositoryEntry) {
      let dialog = ComponentTool.showDialog(this.modalService, AddRepositoryFolderDialog,
         () => {}, {backdrop: "static"}
      );
      dialog.entry = entry;
      dialog.edit = true;
   }

   private addToFavorites(entry: RepositoryEntry) {
      let event = new FavoritesEntryEvent(entry, true);

      this.modelService.putModel(ADD_TO_FAVORITES_URI + "?isArchive=" + false, event).subscribe(
         () => {
            this.refreshTree();
         });
   }

   private removeFromFavorites(entry: RepositoryEntry) {
      let event = new FavoritesEntryEvent(entry, true);

      this.modelService.putModel(REMOVE_FROM_FAVORITES_URI + "?isArchive=" + false, event)
      .subscribe(() => {
         this.refreshTree();
      });
   }

   private showRenameDialog(node: TreeNodeModel) {
      let entry: RepositoryEntry = node.data;
      let oldName = node.label;
      let suffix = "";

      let dialog = ComponentTool.showDialog(this.modalService, InputNameDialog,
         (name: string) => {
            // check if name has changed
            if(oldName != name) {
               this.dispatchRenameEntryEvent(entry, name + suffix);
            }
         },
         {backdrop: "static"}
      );
      dialog.title = "_#(js:Rename)";
      dialog.label = "_#(js:Name)";
      dialog.value = oldName;

      if(node.data.classType === "RepletFolderEntry" ||
         node.data.classType === "ViewsheetEntry") {
         dialog.validators = [
            Validators.required,
            FormValidators.assetEntryBannedCharacters,
            FormValidators.assetNameStartWithCharDigit
         ];
      }
      else if(node.data.classType === "RepletEntry") {
         dialog.validators = [
            Validators.required,
            FormValidators.isValidReportName,
            FormValidators.assetNameStartWithCharDigit
         ];
      }
      else {
         dialog.validators = [
            Validators.required,
            FormValidators.containsDashboardSpecialCharsForName,
            FormValidators.assetNameStartWithCharDigit
         ];
      }

      dialog.validatorMessages = [
         {validatorName: "required", message: "_#(js:viewer.nameValid)"},
         {validatorName: "containsSpecialCharsForName", message: CALC_SPECIAL_CHARS_MESSAGE},
         {validatorName: "assetEntryBannedCharacters" , message: CALC_SPECIAL_CHARS_MESSAGE},
         {validatorName: "assetNameStartWithCharDigit", message: START_WITH_CHART_DIGIT_MESSAGE}
      ];
   }

   private dispatchRenameEntryEvent(entry: RepositoryEntry, name: string,
                                    confirmed: boolean = false)
   {
      let event = new RenameRepositoryEntryEvent(entry, name, confirmed);

      this.loading = true;
      this.modelService.sendModel<MessageCommand>(RENAME_ENTRY_URI, event)
         .subscribe((res) => {
            this.loading = false;
            if(!!res.body) {
               const messageCommand = res.body;

               if(messageCommand.type !== "CONFIRM") {
                  ComponentTool.showMessageDialog(this.modalService, "Error",
                     messageCommand.message);
               }
               else if(!confirmed) {
                  ComponentTool.showConfirmDialog(this.modalService, "Confirm",
                     messageCommand.message).then((buttonClicked) => {
                     if(buttonClicked === "ok") {
                        this.dispatchRenameEntryEvent(entry, name, true);
                     }
                  });
               }
            }
         }, () => {
            this.loading = false;
         }, () => {
            this.loading = false;
         });
   }

   private deleteEntries(entries: RepositoryEntry[]) {
      let msg = "_#(js:em.common.items.delete) ";

      for(let i = 0; i < entries.length; i++) {
         if(i === entries.length - 1) {
            msg += entries[i].name + "?";
         }
         else {
            msg += entries[i].name + ", ";
         }
      }

      ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", msg).then(
         (buttonClicked) => {
            if(buttonClicked === "ok") {
               entries.forEach((entry) => {
                  this.dispatchRemoveRepositoryEntryEvent(entry);
               });
            }
         });
   }

   private dispatchRemoveRepositoryEntryEvent(entry: RepositoryEntry,
                                              confirmed: boolean = false)
   {
      let event = new RemoveRepositoryEntryEvent(entry, confirmed);

      this.loading = true;
      this.modelService.sendModel<MessageCommand>(REMOVE_ENTRY_URI, event)
         .subscribe((res) => {
            this.loading = false;

            if(!!res.body) {
               let messageCommand: MessageCommand = res.body;

               if(messageCommand.type !== "CONFIRM") {
                  ComponentTool.showMessageDialog(this.modalService, "Error",
                     messageCommand.message);
               }
               else if(!confirmed) {
                  ComponentTool.showConfirmDialog(this.modalService, "Confirm",
                     messageCommand.message).then((buttonClicked) => {
                     if(buttonClicked === "ok") {
                        this.dispatchRemoveRepositoryEntryEvent(entry, true);
                     }
                  });
               }
            }
         }, () => {
            this.loading = false;
         }, () => {
            this.loading = false;
         });
   }

   protected dispatchChangeEntryEvent(parent: RepositoryEntry, entry: RepositoryEntry,
                                      confirmed: boolean = false)
   {
      let event = new ChangeRepositoryEntryEvent(parent, entry, confirmed);

      this.loading = true;
      this.modelService.sendModel<MessageCommand>(CHANGE_ENTRY_URI, event)
         .subscribe((res) => {
            this.loading = false;

            if(!!res.body) {
               let messageCommand: MessageCommand = res.body;

               if(messageCommand.type !== "CONFIRM") {
                  ComponentTool.showMessageDialog(this.modalService, "Error",
                     messageCommand.message);
               }
               else if(!confirmed) {
                  ComponentTool.showConfirmDialog(this.modalService, "Confirm",
                     messageCommand.message).then((buttonClicked) => {
                     if(buttonClicked === "ok") {
                        this.dispatchChangeEntryEvent(parent, entry, true);
                     }
                  });
               }
            }
         }, () => {
            this.loading = false;
         }, () => {
            this.loading = false;
         });
   }

   private openInNewTab(entry: RepositoryEntry): void {
      GuiTool.openBrowserTab(this.repositoryTreeService.getContentSource(entry), null, "_blank", false);
   }

   private materializeEntry(entries: RepositoryEntry[]): void {
      let dialog: AnalyzeMVDialog = ComponentTool.showDialog(
         this.modalService, AnalyzeMVDialog, () => this.refreshTree(),
         {backdrop: "static", windowClass: "analyze-mv-dialog"},
         () => this.refreshTree());
      dialog.selectedNodes = entries.map(
         entry => new MVTreeModel(entry.path, entry.entry.identifier, entry.type, !!entry.owner));
   }

   public refreshTree(): void {
      // abstract
   }
}
