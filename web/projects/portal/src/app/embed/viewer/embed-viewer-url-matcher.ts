/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import { UrlMatchResult, UrlSegment } from "@angular/router";

export function EMBED_VIEWER_URL_MATCHER(url: UrlSegment[]): UrlMatchResult {
   let result: UrlMatchResult = null;

   if(url && url.length > 0) {
      const params: { [name: string]: UrlSegment } = {};
      result = {
         consumed: url,
         posParams: params
      };

      if(url.length > 0) {
         let assetScope: UrlSegment = null;
         let assetOwner: UrlSegment = null;
         let assetPath: string = null;
         let assetId: string = null;

         if(url[0].path === "global") {
            assetScope = url[0];

            if(url.length > 1) {
               assetPath = url.slice(1).map((s) => s.path).join("/");
               assetId = `1^128^__NULL__^${assetPath}`;
            }
         }
         else if(url[0].path === "user") {
            assetScope = url[0];

            if(url.length > 1) {
               assetOwner = url[1];

               if(url.length > 2) {
                  assetPath = url.slice(2).map((s) => s.path).join("/");
                  assetId = `4^128^${assetOwner.path}^${assetPath}`;
               }
            }
         }
         else {
            assetId = url.map((s) => s.path).join("/");
            const match = /^([14])+\^128\^([^^]+)\^(.+)$/.exec(assetId);

            if(match) {
               if(match[1] == "1") {
                  assetScope = new UrlSegment("global", {});
               }
               else {
                  assetScope = new UrlSegment("user", {});
                  assetOwner = new UrlSegment(match[2], {});
               }

               assetPath = match[3];
            }
         }

         if(assetScope) {
            params.assetScope = assetScope;
         }

         if(assetOwner) {
            params.assetOwner = assetOwner;
         }

         if(assetPath) {
            params.assetPath = new UrlSegment(assetPath, {});
         }

         if(assetId) {
            params.assetId = new UrlSegment(assetId, {});
         }
      }
   }

   return result;
}