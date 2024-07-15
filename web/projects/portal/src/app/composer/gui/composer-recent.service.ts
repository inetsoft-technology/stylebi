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
import { AssetEntry } from "../../../../../shared/data/asset-entry";
import { LocalStorage } from "../../common/util/local-storage.util";
import { CurrentUser } from "../../portal/current-user";
import { BehaviorSubject, Observable, Subject } from "rxjs";
import { RecentAssetsCheckModel } from "./asset-pane/recent-assets-check-model";
import { HttpClient } from "@angular/common/http";
import { Tool } from "../../../../../shared/util/tool";

const CHECK_RECENT_ASSETS_URI = "../api/composer/asset_tree/check-recent-assets";
const MAX_RECENTLY_VIEWED: number = 10;

@Injectable()
export class ComposerRecentService {
  private _recentlyViewed: AssetEntry[];
  private _currentUser: string;
  private recentChange: Subject<AssetEntry[]> = new BehaviorSubject<AssetEntry[]>([]);

  set currentUser(currentUser: string) {
    this._currentUser = currentUser;
    this.updateRecentlyViewed();
  }

  get recentlyViewed(): AssetEntry[] {
    return this._recentlyViewed;
  }

  constructor(private httpClient: HttpClient) {
  }

  updateRecentlyViewed() {
    if(this._currentUser) {
      this._recentlyViewed =
         JSON.parse(LocalStorage.getItem("composer_recently_viewed_" +
            this._currentUser)) || [];
      this.recentChange.next(this.recentlyViewed);
    }
  }

  recentlyViewedChange(): Observable<AssetEntry[]> {
    return this.recentChange.asObservable();
  }

  removeRecentlyViewed(entries: AssetEntry[]) {
    for(let i = 0; i < entries.length; i++) {
      for(let j = 0; j < this.recentlyViewed.length; j++) {
        if(entries[i].path == this.recentlyViewed[j].path) {
          this.recentlyViewed.splice(j, 1);
          continue;
        }
      }
    }

    this.storeItems();
  }

  addRecentlyViewed(entry: AssetEntry): void {
    for(let i = 0; i < this.recentlyViewed.length; i++) {
      if(entry.path === this.recentlyViewed[i].path) {
        this.recentlyViewed.splice(i, 1);
        break;
      }
    }

    this.recentlyViewed.unshift(entry);

    if(this.recentlyViewed.length > MAX_RECENTLY_VIEWED) {
      this.recentlyViewed.splice(MAX_RECENTLY_VIEWED,
         this.recentlyViewed.length - MAX_RECENTLY_VIEWED);
    }

    this.storeItems();
  }

  removeNonExistItems(): void {
    let model = <RecentAssetsCheckModel> {
      assets: this.recentlyViewed
    };

    this.httpClient.post<RecentAssetsCheckModel>(CHECK_RECENT_ASSETS_URI, model).subscribe(result => {
      this._recentlyViewed = result.assets;
      this.storeItems();
    });
  }

  private storeItems(): void {
    LocalStorage.setItem("composer_recently_viewed_" + this._currentUser,
       JSON.stringify(this.recentlyViewed));

    this.recentChange.next(this.recentlyViewed);

  }
}

