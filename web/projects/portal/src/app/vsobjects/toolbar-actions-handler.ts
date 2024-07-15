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
import { Injectable } from "@angular/core";
import { AssemblyActionGroup } from "../common/action/assembly-action-group";

export interface ActionMarkPoint {
   index: number;
   count: number;
}

export class ToolbarActionsHandler {
   public static getShowingActions(actionGroups: AssemblyActionGroup[], allowedActionsNum: number): AssemblyActionGroup[] {
      allowedActionsNum = Math.max(0, allowedActionsNum);
      const visibleActions = ToolbarActionsHandler.getVisibleToolbarActions(actionGroups);
      const markPoint = ToolbarActionsHandler.getMarkPoint(actionGroups, allowedActionsNum);
      const index = markPoint.index;
      const count = markPoint.count;
      let actions: AssemblyActionGroup[] = [];

      for(let i = 0; i < visibleActions.length; i++) {
         actions.push(new AssemblyActionGroup());
      }

      if(index < visibleActions.length) {
         let groups = visibleActions.slice(0, index);

         for(let i = 0; i < groups.length; i++) {
            actions[i].actions = groups[i].actions;
         }

         const middleActions = visibleActions[index];
         allowedActionsNum--;

         if(count > allowedActionsNum) {
            actions[index].actions = middleActions.actions.slice(0, allowedActionsNum - count);
         }
      }
      else {
         actions = visibleActions;
      }

      return actions;
   }

   public static getMoreActions(actionGroups: AssemblyActionGroup[], allowedActionsNum: number): AssemblyActionGroup[] {
      allowedActionsNum = Math.max(0, allowedActionsNum);
      const visibleActions = ToolbarActionsHandler.getVisibleToolbarActions(actionGroups);
      const markPoint = ToolbarActionsHandler.getMarkPoint(actionGroups, allowedActionsNum);
      const index = markPoint.index;
      const count = markPoint.count;
      let actions: AssemblyActionGroup[] = [];

      for(let i = index; i < visibleActions.length; i++) {
         actions.push(new AssemblyActionGroup());
      }

      if(index < visibleActions.length) {
         const middleActions = visibleActions[index];
         allowedActionsNum--;

         if(count > allowedActionsNum) {
            actions[0].actions = middleActions.actions.slice(allowedActionsNum - count);
         }

         if(index < visibleActions.length - 1) {
            const groups = visibleActions.slice(index);

            for(let i = 1; i < actions.length; i++) {
               actions[i].actions = groups[i].actions;
            }
         }

         return actions;
      }

      return null;
   }

   public static getVisibleToolbarActions(actionGroups: AssemblyActionGroup[]): AssemblyActionGroup[] {
      if(!actionGroups) {
         return [];
      }

      const visibleActions = [];

      for(let i = 0; i < actionGroups.length; i++) {
         visibleActions.push(new AssemblyActionGroup());
      }

      for(let i = 0; i < actionGroups.length; i++) {
         if(!visibleActions[i].actions) {
            visibleActions[i].actions = [];
         }

         visibleActions[i].actions = actionGroups[i].actions.filter(a => a.visible());
      }

      return visibleActions;
   }

   public static copyActions(source: AssemblyActionGroup[], target: AssemblyActionGroup[]): void {
      if(!source) {
         target.forEach(group => group.actions = []);
         return;
      }

      if(target.length == 0) {
         for(let i = 0; i < source.length; i++) {
            target.push(new AssemblyActionGroup());
         }
      }

      if(target.length == source.length) {
         for(let i = 0; i < source.length; i++) {
            target[i].actions = source[i].actions;
         }
      }
      else {
         target.splice(0, target.length);

         for(let i = 0; i < source.length; i++) {
            target[i] = new AssemblyActionGroup();
            target[i].actions = source[i].actions;
         }
      }
   }

   private static getMarkPoint(actionGroups: AssemblyActionGroup[], allowedActionsNum: number): ActionMarkPoint {
      const visibleActions = ToolbarActionsHandler.getVisibleToolbarActions(actionGroups);
      let index = 0;
      let count = 0;

      for(; index < visibleActions.length; index++) {
         count += visibleActions[index].actions.length;

         if(count >= allowedActionsNum) {
            break;
         }
      }

      return {index: index, count: count};
   }

   public static get MOBILE_BUTTON_WIDTH(): number {
      return 46;
   }

   public static get MOBILE_BUTTON_HEIGHT(): number {
      return 66;
   }
}