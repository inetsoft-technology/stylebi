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
import { OpenViewsheetEvent } from "./open-viewsheet-event";

/**
 * Event used to open a preview viewsheet.
 */
export class OpenPreviewViewsheetEvent extends OpenViewsheetEvent {
   /**
    * The name of the current layout if available.
    */
   public layoutName: string;

   /**
    * Creates a new instance of <tt>OpenPreviewViewsheetEvent</tt>.
    *
    * @param entryId   the asset entry identifier of the viewsheet.
    * @param width     the viewport width of the browser.
    * @param height    the viewport height of the browser.
    * @param mobile    the flag that indicates if the client is a mobile device.
    * @param userAgent the user agent string of the client browser.
    */
   constructor(entryId: string, width: number, height: number, mobile: boolean,
               userAgent: string)
   {
      super(entryId, width, height, mobile, userAgent);
   }
}