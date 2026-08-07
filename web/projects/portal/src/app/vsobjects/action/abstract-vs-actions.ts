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
import { Tool } from "../../../../../shared/util/tool";

/**
 * Base class for viewsheet assembly context actions.
 */
export abstract class AbstractVSActions<T extends VSObjectModel> extends AssemblyActions<T> {
   private assemblyToolbarActions: AssemblyActionGroup[];
   private assemblyMenuActions: AssemblyActionGroup[];
   private assemblyClickAction: AssemblyAction;
   private assemblyScriptAction: AssemblyAction;
   private initedActions: boolean = false;
   // Set by createToolbarActions() under the isVizModern gate; consumed by createMenuActions()
   // to surface the same action there instead of at toolbar index 0. See the comments at both
   // sites.
   private hideMiniToolbarAction: AssemblyAction = null;
   protected mobileDevice: boolean = GuiTool.isMobileDevice();
   showing: AssemblyActionGroup[] = [];
   more: AssemblyActionGroup[] = [];
   moreAction: AssemblyAction = null;

   // Height bands for the anchored strip. Only ACTION_FLOOR is derived: a 24px control needs 4px of
   // clearance above and below, so below 32px no control fits. ACTIONS_MIN (32 + 24) is judgement —
   // the height at which a strip stops feeling like it owns the card — and is the number in this
   // ladder most likely to be wrong. Validate against real dashboards before trusting it; a 56px card
   // is common in a KPI row.
   private static readonly ACTION_FLOOR = 32;
   private static readonly ACTIONS_MIN = 56;
   // Action buttons, kebab excluded. allowedActionsNum() returns slots, not actions — see there.
   private static readonly MAX_TOOLBAR_ACTIONS = 3;

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

   // TEMPORARY type test, like the container's isToolbarAnchored: the cap and the height bands
   // are part of the chart pilot and are deleted during the eight-assembly rollout.
   private get resident(): boolean {
      return GuiTool.isVizModern() && Tool.equalsIgnoreCase(this.model.objectType, "VSChart");
   }

   /**
    * The number of toolbar *slots*, not action buttons. ToolbarActionsHandler.getShowingActions()
    * spends one of the slots it is handed on the overflow control (it decrements before slicing, so
    * n slots yield n-1 action buttons whenever anything overflows), and the width term below is in
    * the same units — floor(width / actionWidth) is how many buttons of any kind fit. So a cap of
    * k action buttons is k + 1 slots; passing k straight through capped the strip at k-1 buttons.
    */
   allowedActionsNum(): number {
      let actionWidth: number = Math.floor(this.miniToolbarService.getActionsWidth(this.toolbarActions) /
         this.miniToolbarService.getActionCount(this.toolbarActions));

      let num: number = Math.floor(this.model.objectFormat.width / actionWidth);

      if(!this.resident) {
         return num;
      }

      const height = this.model.objectFormat.height;

      // Below the control floor nothing fits; between the floor and ACTIONS_MIN the kebab is the
      // whole strip, so no action buttons are allowed. Zero slots still leaves the kebab — it is
      // appended by showingActions outside this budget, and is always the last thing to go.
      //
      // Touch is the same case: mini-toolbar renders the action-button groups inside
      // @if (!mobileDevice), so no action button exists there and the kebab is the only control.
      // Without this the budget would claim the leading actions were on the strip and
      // getMoreActions() would skip them — leaving the kebab opening a short list, or nothing at
      // all when three or fewer are visible. Same GuiTool.isMobileDevice() read the template's
      // guard evaluates.
      if(this.mobileDevice || height < AbstractVSActions.ACTIONS_MIN) {
         return 0;
      }

      return Math.min(AbstractVSActions.MAX_TOOLBAR_ACTIONS + 1, num);
   }

   get showingActions(): AssemblyActionGroup[] {
      if(!this.toolbarActions) {
         return this.showing;
      }

      const modern = this.resident;

      // No chrome at all below the control floor — a 24px control with 4px clearance does not fit,
      // and right-click becomes the only route. This is the one rung that removes the kebab.
      if(modern && this.model.objectFormat.height < AbstractVSActions.ACTION_FLOOR) {
         ToolbarActionsHandler.copyActions([], this.showing);
         return this.showing;
      }

      if(!modern && this.model.objectFormat.width >=
         this.miniToolbarService.getActionsWidth(this.toolbarActions))
      {
         return this.toolbarActions;
      }

      const actions = ToolbarActionsHandler.getShowingActions(this.toolbarActions,
         this.allowedActionsNum());
      ToolbarActionsHandler.copyActions(actions, this.showing);

      // Under the gate the kebab is resident, not an overflow control: it is the permanent "this
      // object has actions" signal, the only touch route, and the only resting keyboard target.
      // The two entry points do not show the same list. The kebab opens getMoreActions() — the
      // overflowed toolbar actions, which include the trailing "menu actions" wrapper once that
      // itself overflows — while right-click opens menuActions directly. What makes the lower rungs
      // safe is that the wrapper is the last toolbar group and its childAction() is menuActions, so
      // the full menu is always one click away: either the wrapper is still on the strip, or it has
      // overflowed into the kebab. It is not a guarantee that the kebab is non-empty — when nothing
      // overflows, getMoreActions() is empty and the wrapper on the strip carries the menu instead.
      const needsKebab = modern || this.model.objectFormat.width <
         this.miniToolbarService.getActionsWidth(this.toolbarActions);

      if(needsKebab) {
         // getShowingActions(groups, 0) can leave this.showing empty (e.g. every toolbar action
         // suppressed via actionNames) — guard the trailing-group access rather than assume it.
         if(this.showing.length === 0) {
            this.showing.push(new AssemblyActionGroup());
         }

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

   public updateModel(model: T): void {
      this.model = model;
      this.assemblyToolbarActions = null;
      this.assemblyMenuActions = null;
      this.assemblyClickAction = null;
      this.assemblyScriptAction = null;
      this.initedActions = false;
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
      this.hideMiniToolbarAction = null;

      if(groups && groups.length > 0 && !GuiTool.isMobileDevice() && this.model.containerType != "VSSelectionContainer")
      {
         let othersGroups = [...groups];

         const hideMiniToolbar: AssemblyAction = {
            id: () => "vs-assembly hide-mini-toolbar",
            label: () => "_#(js:Hide MiniToolbar)",
            icon: () => "close-icon",
            enabled: () => true,
            visible: () => this.isActionVisible("Hide MiniToolbar") && othersGroups &&
               othersGroups.length > 0 &&
               othersGroups.some(group => group.actions.some(action => action.visible())),
            action: () => this.hideMiniToolbar(),
         };

         if(GuiTool.isVizModern()) {
            // The dismissal is something done to the strip, not to the assembly, and at toolbar
            // index 0 it ate a slot ahead of show-data under a cap on toolbar length. It moves to
            // the menu instead of splicing at index 0 here; createMenuActions() below reads this
            // field to surface it there. Gated, so gate-off orgs keep index 0 and the other seven
            // assembly types with a mini-toolbar are unaffected until the rollout.
            this.hideMiniToolbarAction = hideMiniToolbar;
         }
         else {
            groups.splice(0, 0, new AssemblyActionGroup([hideMiniToolbar]));
         }
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
                  // On touch the action-button groups are not rendered, so this wrapper is the only
                  // thing that can carry menuActions into the resident kebab. TEMPORARY relaxation
                  // of the pre-existing mobile exclusion, reusing the resident type test the cap
                  // uses so it yields only for an anchored chart under the gate; deleted with the
                  // rest of the pilot's type tests during the eight-assembly rollout. It restores
                  // the route only — menu entries carrying their own !mobileDevice stay hidden.
                  visible: () => !this.vsWizardPreview && (!this.mobileDevice || this.resident)
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
      // Counterpart to the gated branch in createToolbarActions(): reading this.toolbarActions
      // forces that method to run (it is a cached getter, so this is a no-op if it already has),
      // which populates hideMiniToolbarAction under the same gate before we look for it here.
      if(GuiTool.isVizModern() && !GuiTool.isMobileDevice() &&
         this.model.containerType != "VSSelectionContainer" &&
         this.toolbarActions && this.toolbarActions.length > 0)
      {
         if(this.hideMiniToolbarAction) {
            groups.push(new AssemblyActionGroup([this.hideMiniToolbarAction]));
         }
      }

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
