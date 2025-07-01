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
import { ContextProvider } from "../context-provider.service";
import { VSObjectModel } from "../model/vs-object-model";
import { ActionStateProvider } from "./action-state-provider";
import { AssemblyAction } from "../../common/action/assembly-action";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { AssemblyActions } from "./assembly-actions";
import { DataTipService } from "../objects/data-tip/data-tip.service";
import { GuiTool } from "../../common/util/gui-tool";
import { PopComponentService } from "../objects/data-tip/pop-component.service";
import { MiniToolbarService } from "../objects/mini-toolbar/mini-toolbar.service";
import { ToolbarActionsHandler } from "../toolbar-actions-handler";

/**
 * Base class for viewsheet assembly context actions.
 */
export abstract class AbstractVSActions<T extends VSObjectModel> extends AssemblyActions<T> {
   private assemblyToolbarActions: AssemblyActionGroup[];
   private assemblyMenuActions: AssemblyActionGroup[];
   private assemblyClickAction: AssemblyAction;
   private assemblyScriptAction: AssemblyAction;
   private initedActions: boolean = false;
   protected mobileDevice: boolean = GuiTool.isMobileDevice();
   showing: AssemblyActionGroup[] = [];
   more: AssemblyActionGroup[] = [];
   moreAction: AssemblyAction = null;

   /**
    * Creates a new instance of AbstractVSActions.
    *
    * @param model the assembly model.
    * @param viewer true if the assembly is in the viewer.
    * @param composer true if the assembly is in the composer.
    * @param binding true if the assembly is in the binding editor.
    * @param preview true if the assembly is in a composer preview.
    * @param securityEnabled true if security is enabled.
    * @param stateProvider the action state provider.
    */
   constructor(protected model: T,
               protected contextProvider: ContextProvider,
               protected securityEnabled: boolean,
               protected stateProvider: ActionStateProvider,
               private dataTipService: DataTipService,
               private popService: PopComponentService,
               private miniToolbarService: MiniToolbarService)
   {
      super();
   }

   protected get viewer(): boolean {
      return this.contextProvider.viewer;
   }

   protected get composer(): boolean {
      return this.contextProvider.composer;
   }

   protected get binding(): boolean {
      return this.contextProvider.binding;
   }

   protected get preview(): boolean {
      return this.contextProvider.preview;
   }

   protected get composerBinding(): boolean {
      return this.contextProvider.composerBinding;
   }

   protected get vsWizardPreview(): boolean {
      return this.contextProvider.vsWizardPreview;
   }

   protected get embed(): boolean {
      return this.contextProvider.embed;
   }

   public get toolbarActions(): AssemblyActionGroup[] {
      if(!this.assemblyToolbarActions) {
         this.assemblyToolbarActions = this.createToolbarActions([]);

         if(this.assemblyToolbarActions) {
            this.assemblyToolbarActions.forEach((group) =>
                                                this.addActionHandlers(group, this.model));
         }
      }

      return this.assemblyToolbarActions;
   }

   public get menuActions(): AssemblyActionGroup[] {
      if(this.isDataTip() || this.isPopComponent()) {
         this.assemblyMenuActions = [];
         this.initedActions = false;
      }
      else if(!this.assemblyMenuActions || !this.initedActions) {
         this.initedActions = true;
         this.assemblyMenuActions = this.createMenuActions([]);

         if(this.assemblyMenuActions) {
            this.assemblyMenuActions.forEach(g => this.addActionHandlers(g, this.model));
         }
      }

      return this.assemblyMenuActions;
   }

   allowedActionsNum(): number {
      let actionWidth: number = Math.floor(this.miniToolbarService.getActionsWidth(this.toolbarActions) /
         this.miniToolbarService.getActionCount(this.toolbarActions));

      let num: number = Math.floor(this.model.objectFormat.width / actionWidth);

      return num;
   }

   get showingActions(): AssemblyActionGroup[] {
      if(!this.toolbarActions) {
         return this.showing;
      }

      if(this.model.objectFormat.width >=
         this.miniToolbarService.getActionsWidth(this.toolbarActions))
      {
         return this.toolbarActions;
      }

      const actions = ToolbarActionsHandler.getShowingActions(this.toolbarActions,
         this.allowedActionsNum());
      ToolbarActionsHandler.copyActions(actions, this.showing);

      if(this.model.objectFormat.width <
         this.miniToolbarService.getActionsWidth(this.toolbarActions))
      {
         if(this.moreAction == null) {
            this.moreAction = this.createMoreAction();
         }

         this.showing[this.showing.length - 1].actions.push(this.moreAction);
         this.addActionHandler(this.moreAction, this.model);
      }

      return this.showing;
   }

   getMoreActions(): AssemblyActionGroup[] {
      if(!this.toolbarActions) {
         return this.more;
      }

      const actions = ToolbarActionsHandler.getMoreActions(this.toolbarActions,
         this.allowedActionsNum());
      ToolbarActionsHandler.copyActions(actions, this.more);

      return this.more;
   }

   public resetAssemblyMenuActions(): void {
      this.assemblyMenuActions = null;
   }

   protected isDataTip(): boolean {
      return (this.viewer || this.preview) && this.dataTipService && this.model &&
         (this.dataTipService.isDataTip(this.model.absoluteName) ||
          this.model.container && this.dataTipService.isDataTip(this.model.container));
   }

   protected isPopComponent(): boolean {
      return this.popService && this.popService.getPopComponent() &&
         (this.popService.getPopComponent() == this.model.absoluteName ||
          this.popService.getPopComponent() == this.model.container);
   }

   public get clickAction(): AssemblyAction {
      if(!this.assemblyClickAction) {
         this.assemblyClickAction = this.createClickAction();

         if(this.assemblyClickAction) {
            this.addActionHandler(this.assemblyClickAction, this.model);
         }
      }

      return this.assemblyClickAction;
   }

   public get scriptAction(): AssemblyAction {
      if(!this.assemblyScriptAction) {
         this.assemblyScriptAction = this.createScriptAction();

         if(this.assemblyScriptAction) {
            this.addActionHandler(this.assemblyScriptAction, this.model);
         }
      }

      return this.assemblyScriptAction;
   }

   /**
    * Determines if a menu separator should be displayed before the specified action
    * group;
    *
    * @param group the group to check.
    *
    * @return <tt>true</tt> to add a separator before the group; <tt>false</tt> otherwise.
    */
   public requiresMenuSeparator(group: AssemblyActionGroup): boolean {
      let result: boolean = false;

      for(let menuGroup of this.menuActions) {
         if(menuGroup == group) {
            break;
         }

         if(menuGroup.visible) {
            result = true;
            break;
         }
      }

      return result;
   }

   public getModel(): T {
      return this.model;
   }

   /**
    * Determines if the action is visible. Actions can be individually turned on/off
    * through the script.
    *
    * @param actionNames list of action names that should be disabled
    * @param action      the name of the action to test
    */
   public static isActionVisible(actionNames: string[], action: string, oname?: string): boolean {
      return !(actionNames && (actionNames.indexOf(action) >= 0 || actionNames.indexOf(oname) >= 0));
   }

   protected getEditScriptActionId(): string {
      return null;
   }

   /**
    * Creates the toolbar actions for this type of assembly.
    */
   protected createToolbarActions(groups: AssemblyActionGroup[], addMenuActions?: boolean,
                                  label?: string): AssemblyActionGroup[]
   {
      if(groups && groups.length > 0 && !GuiTool.isMobileDevice() && this.model.containerType != "VSSelectionContainer")
      {
         let othersGroups = [...groups];

         groups.splice(0, 0, new AssemblyActionGroup([
            {
               id: () => "vs-assembly hide-mini-toolbar",
               label: () => "_#(js:Hide MiniToolbar)",
               icon: () => "close-icon",
               enabled: () => true,
               visible: () => this.isActionVisible("Hide MiniToolbar") && othersGroups &&
                  othersGroups.length > 0 &&
                  othersGroups.some(group => group.actions.some(action => action.visible())),
               action: () => this.hideMiniToolbar(),
            }
         ]));
      }

      if(groups && addMenuActions) {
         if(this.model.containerType == "VSSelectionContainer") {
            this.menuActions.forEach(m => groups.push(m));
         }
         else {
            groups.push(new AssemblyActionGroup([
               {
                  id: () => "menu actions",
                  label: () => label ? label : "_#(js:More)",
                  icon: () => "menu-horizontal-icon",
                  enabled: () => true,
                  visible: () => !this.vsWizardPreview && !this.mobileDevice
                     && this.isActionVisibleInViewer("Menu Actions")
                     && this.menuActions.some((g) => g.actions.some((action) => action.visible())),
                  childAction: () => this.menuActions
               }
            ]));
         }
      }

      return groups;
   }

   /**
    * Creates the menuActions for this type of assembly.
    */
   protected createMenuActions(groups: AssemblyActionGroup[]): AssemblyActionGroup[] {
      return groups;
   }

   private createMoreAction(): AssemblyAction {
      return {
         id: () => "more actions",
         label: () => "_#(js:More)...",
         icon: () => "menu-vertical-icon",
         enabled: () => true,
         visible: () => true
      };
   }

   protected createScriptAction(): AssemblyAction {
      const id = this.getEditScriptActionId();

      if(id) {
         return {
            id: () => id,
            label: () => "_#(js:Edit Script)",
            icon: () => "script-icon",
            enabled: () => true,
            visible: () => this.composer && (!!this.model.script || (<any>this.model).hasOnClick)
         };
      }

      return null;
   }

   protected createDefaultEditMenuActions(
      isCopyEnabled: () => boolean = () => true,
      isCopyVisible: () => boolean = () => this.composer,
      isCutEnabled: () => boolean = () => true,
      isCutVisible: () => boolean = () => this.composer,
      isRemoveEnabled: () => boolean = () => true,
      isRemoveVisible: () => boolean = () => this.composer,
      isGroupEnabled: () => boolean = () => this.isStateEnabled("vs-object group"),
      isGroupVisible: () => boolean = () => this.composer,
      isUngroupEnabled: () => boolean = () => this.isStateEnabled("vs-object ungroup"),
      isUngroupVisible: () => boolean = () => this.composer): AssemblyActionGroup
   {
      return new AssemblyActionGroup([
         {
            id: () => "vs-object copy",
            label: () => "_#(js:Copy)",
            icon: () => "place-holder-icon",
            enabled: isCopyEnabled,
            visible: isCopyVisible
         },
         {
            id: () => "vs-object cut",
            label: () => "_#(js:Cut)",
            icon: () => "place-holder-icon",
            enabled: isCutEnabled,
            visible: isCutVisible
         },
         {
            id: () => "vs-object remove",
            label: () => "_#(js:Remove)",
            icon: () => "place-holder-icon",
            enabled: isRemoveEnabled,
            visible: isRemoveVisible
         },
         {
            id: () => "vs-object group",
            label: () => "_#(js:Group)",
            icon: () => "place-holder-icon",
            enabled: isGroupEnabled,
            visible: isGroupVisible
         },
         {
            id: () => "vs-object ungroup",
            label: () => "_#(js:Ungroup)",
            icon: () => "place-holder-icon",
            enabled: isUngroupEnabled,
            visible: isUngroupVisible
         }]);
   }

   protected createDefaultOrderMenuActions(
      isBringToFrontEnabled: () => boolean = () => this.isStateEnabled("vs-object bring-to-front"),
      isBringToFrontVisible: () => boolean = () => this.composer,
      isSendToBackEnabled: () => boolean = () => this.isStateEnabled("vs-object send-to-back"),
      isSendToBackVisible: () => boolean = () => this.composer): AssemblyActionGroup
   {
      return new AssemblyActionGroup([
         {
            id: () => "vs-object bring-forward",
            label: () => "_#(js:Bring Forward)",
            icon: () => "place-holder-icon",
            enabled: isBringToFrontEnabled,
            visible: isBringToFrontVisible
         },
         {
            id: () => "vs-object bring-to-front",
            label: () => "_#(js:Bring to Front)",
            icon: () => "place-holder-icon",
            enabled: isBringToFrontEnabled,
            visible: isBringToFrontVisible
         },
         {
            id: () => "vs-object send-backward",
            label: () => "_#(js:Send Backward)",
            icon: () => "place-holder-icon",
            enabled: isSendToBackEnabled,
            visible: isSendToBackVisible
         },
         {
            id: () => "vs-object send-to-back",
            label: () => "_#(js:Send to Back)",
            icon: () => "place-holder-icon",
            enabled: isSendToBackEnabled,
            visible: isSendToBackVisible
         }]);
   }

   protected createClickAction(): AssemblyAction {
      return null;
   }

   /**
    * Determines if the action is visible. Actions can be individually turned on/off
    * through the script.
    *
    * @param name name of the action
    */
   protected isActionVisible(name: string, oname: string = null): boolean {
      return AbstractVSActions.isActionVisible(this.model.actionNames, name, oname);
   }

   protected isActionVisibleInViewer(name: string, oname: string = null): boolean {
      return !(this.viewer || this.preview) || this.isActionVisible(name, oname);
   }

   protected isStateEnabled(id: string, defaultValue: boolean = true): boolean {
      if(this.stateProvider) {
         return this.stateProvider.isActionEnabled(id, this.model);
      }

      return defaultValue;
   }

   protected isStateVisible(id: string, defaultValue: boolean = true): boolean {
      if(this.stateProvider) {
         return this.stateProvider.isActionVisible(id, this.model);
      }

      return defaultValue;
   }

   protected createDefaultAnnotationMenuActions(): AssemblyActionGroup {
      if(this.embed) {
         return new AssemblyActionGroup([]);
      }

      return new AssemblyActionGroup([
         {
            id: () => "annotation edit" + this.selectedAnnotationName,
            label: () => "_#(js:Edit)",
            icon: () => "edit-icon",
            enabled: () => true,
            visible: () => !this.mobileDevice && this.annotationsSelected
         },
         {
            id: () => "annotation format" + this.selectedAnnotationName,
            label: () => "_#(js:Format)",
            icon: () => "format-painter-icon",
            enabled: () => true,
            visible: () => !this.mobileDevice && this.annotationsSelected
         },
         {
            id: () => "annotation remove" + this.selectedAnnotationName,
            label: () => "_#(js:Remove)",
            icon: () => "trash-icon",
            enabled: () => true,
            visible: () => !this.mobileDevice && this.annotationsSelected
         }
      ]);
   }

   protected get annotationsSelected(): boolean {
      return this.model.selectedAnnotations != null && this.model.selectedAnnotations.length > 0;
   }

   private get selectedAnnotationName(): string {
      return this.model.selectedAnnotations &&
         this.model.selectedAnnotations.find((name) => name != null) || "";
   }

   protected get inSelectionContainer(): boolean {
      return this.model.containerType == "VSSelectionContainer";
   }

   protected get inContainer(): boolean {
      return this.model.containerType === "VSSelectionContainer" ||
         this.model.containerType === "VSTab";
   }

   protected get menuActionHelperTextVisible(): boolean {
      return !this.embed || this.embed && !this.annotationsSelected;
   }

   private hideMiniToolbar() {
      this.miniToolbarService?.hideMiniToolbar(this.model.absoluteName);
   }
}
