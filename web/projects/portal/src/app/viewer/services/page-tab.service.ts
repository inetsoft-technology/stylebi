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
import { Injectable } from "@angular/core";
import { Subject } from "rxjs";
import {
   FeatureFlagsService,
   FeatureFlagValue
} from "../../../../../shared/feature-flags/feature-flags.service";

@Injectable()
export class PageTabService {
   private _tabs: TabInfoModel[];
   private _refreshPage = new Subject<TabInfoModel>();
   private _onTabAddedRemoved = new Subject<boolean>();
   currentTab: TabInfoModel;

   constructor(private featureFlagsService: FeatureFlagsService) {
      this.clearTabs();
   }

   get tabs(): TabInfoModel[] {
      return this._tabs;
   }

   addTab(tab: TabInfoModel, changeCurrentTab = true): number {
      const index = this._tabs.push(tab);
      tab.tabIndex = index;

      if(changeCurrentTab) {
         this.changeCurrentTab(tab);
      }

      this._onTabAddedRemoved.next(true);
      return index;
   }

   public updateTabLabel(id: string, label: string) {
      let labelChanged = false;

      for(let tab of this._tabs) {
         if(tab.id == id && tab.label != label) {
            tab.label = label;
            labelChanged = true;
         }
      }

      if(labelChanged) {
         this._onTabAddedRemoved.next(true);
      }
   }

   /**
    * General change current tab method.
    * @param {TabInfoModel} tab: new current tab
    */
   public changeCurrentTab(tab: TabInfoModel): void {
      this.changeCurrentVsTab(tab);
   }

   public changeCurrentVsTab(tab: TabInfoModel): void {
      this.changeTab(tab);
      this.refreshVSPage(tab);
   }

   private changeTab(currentTab: TabInfoModel): void {
      this._tabs.forEach((tab) => tab.isFocused = false);
      currentTab.isFocused = true;
      this.currentTab = currentTab;
   }

   /**
    * General close tab method.
    * @param {TabInfoModel} tab
    */
   public closeTab(tab: TabInfoModel): void {
      this.closeVsTab(tab);
      this._onTabAddedRemoved.next(false);
   }

   public closeVsTab(_tab: TabInfoModel): void {
      let newTabs: TabInfoModel[] = [];
      let newIndex = 0;
      this._tabs.filter((tab) => tab != _tab)
                  .map((tab) => {
                     tab.tabIndex = newIndex++;
                     return tab;
                  })
                 .forEach((tab) => newTabs.push(tab));

      this._tabs = newTabs;

      if(this._tabs.length == 0) { // close last finally tab
         this.refreshVSPage(null);
         return;
      }

      if(_tab == this.currentTab) { // close current tab
         let newCurrentTabIndex = this.getNewCurrentTabIndex(_tab);
         this.changeCurrentVsTab(this._tabs[newCurrentTabIndex]);
      }
   }

   private getNewCurrentTabIndex(tab: TabInfoModel): number {
      while(!!tab?.parentTab) {
         if(this._tabs[tab.parentTab.tabIndex]) {
            return tab.parentTab.tabIndex;
         }

         tab = tab.parentTab;
      }

      return 0;
   }

   public clearTabs(): void {
      this._tabs = [];
      this.currentTab = null;
   }

   public getVSTabLabel(assetId: string): string {
      return assetId ? assetId.substring(assetId.lastIndexOf("^") + 1) : "";
   }

   public get onRefreshPage(): Subject<TabInfoModel> {
      return this._refreshPage;
   }

   public refreshVSPage: (tab: TabInfoModel) => any = (tab: TabInfoModel) => this._refreshPage.next(tab);

   public get onTabAddedRemoved(): Subject<boolean> {
      return this._onTabAddedRemoved;
   }
}

export interface TabInfoModel {
   id: string;
   label: string;
   tooltip?: string;
   isFocused?: boolean;
   runtimeId?: string;
   tabIndex?: number;
   parentTab?: TabInfoModel;
   queryParameters?: Map<string, string[]>;
}
