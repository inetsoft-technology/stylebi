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
import { ViewsheetEvent } from "../../common/viewsheet-client/index";
import { Dimension } from "../../common/data/dimension";

/**
 * Event used to open a viewsheet instance on the server.
 */
export class OpenViewsheetEvent implements ViewsheetEvent {
   /**
    * The asset entry identifier of the viewsheet.
    */
   public entryId: string;

   /**
    * The viewport width of the browser.
    */
   public width: number;

   /**
    * The viewport height of the browser.
    */
   public height: number;

   /**
    * The flag that indicates if the client is a mobile device.
    */
   public mobile: boolean;

   /**
    * The user agent string of the client browser.
    */
   public userAgent: string;

   /**
    * The runtime identifier of the viewsheet from which this viewsheet was linked.
    */
   public drillFrom: string = null;

   /**
    * The flag that indicates if this request is to synchronize a viewsheet instance
    * with another.
    */
   public sync: boolean = false;

   /**
    * The runtime identifier of the viewsheet that is to be rendered in a full screen
    * view.
    */
   public fullScreenId: string = null;

   /**
    * The runtime identifier of the viewsheet instance to be opened.
    */
   public runtimeViewsheetId: string = null;

   /**
    * The runtime identifier of the embedded viewsheet.
    */
   public embeddedViewsheetId: string = null;

   /**
    * The flag that indicates if the viewsheet will be opened in the viewer.
    */
   public viewer: boolean = true;

   /**
    * The flag that indicates if the auto-saved version of the viewsheet should be opened.
    */
   public openAutoSaved: boolean = false;

   /**
    * The flag that indicates if the user has confirmed using the auto-saved file.
    */
   public confirmed: boolean = false;

   /**
    * The URL of the page that was loaded in the browser before the viewer page.
    */
   public previousUrl: string = null;

   /**
    * The name of the bookmark to open.
    */
   public bookmarkName: string = null;

   /**
    * The name of the user that owns the bookmark.
    */
   public bookmarkUser: string = null;

   /**
    * The flag that indicates if the "Enter Parameters" prompt (variable-input-dialog) should open
    */
   public disableParameterSheet: boolean = false;

   /**
    * The viewsheet parameters.
    */
   public parameters: {[name: string]: string[]} = {};

   /**
    * The scale.
    */
   public scale: number = 1;

   /**
    * The flag that indicates a manual refresh
    */
   public manualRefresh: boolean = false;

   /**
    * The source id that opened this vs from hyperlink.
    */
   public hyperlinkSourceId: string = null;

   /**
    * The viewsheet open by meta data.
    */
   public meta: boolean = false;

   /**
    * The viewsheet create by wizard.
    */
   public newSheet: boolean = false;

   /**
    * viewsheet layout name.
    */
   public layoutName: string = null;

   /**
    * Standalone/embed assembly name
    */
   public embedAssemblyName: string = null;

   /**
    * Standalone/embed assembly size
    */
   public embedAssemblySize: Dimension = null;

   /**
    * Creates a new instance of <tt>OpenViewsheetEvent</tt>.
    *
    * @param entryId   the asset entry identifier of the viewsheet.
    * @param width     the viewport width of the browser.
    * @param height    the viewport height of the browser.
    * @param mobile    the flag that indicates if the client is a mobile device.
    * @param userAgent the user agent string of the client browser.
    */
   constructor(entryId: string, width: number, height: number, mobile: boolean, userAgent: string, meta?: boolean, newSheet?: boolean) {
      this.entryId = entryId;
      this.width = width;
      this.height = height;
      this.mobile = mobile;
      this.userAgent = userAgent;
      this.meta = meta;
      this.newSheet = newSheet;
   }

   /**
    * Loads data from a JSON representation of an event into this instance.
    *
    * @param data the JSON data to load.
    */
   public loadFromJson(data: any): void {
      for(let property in data) {
         if(data.hasOwnProperty(property) && this.hasOwnProperty(property)) {
            this[property] = data[property];
         }
      }
   }

   public checkQueryParameters(value: string[], key: any) {
      switch(key) {
      case "runtimeid":
         this.runtimeViewsheetId = value[0];
         break;
      case "__bookmarkName__":
         this.bookmarkName = value[0];
         break;
      case "__bookmarkUser__":
         this.bookmarkUser = value[0];
         break;
      case "disableParameterSheet":
         this.disableParameterSheet = value[0] == "true";
         break;
      case "hyperlinkSourceId":
         this.hyperlinkSourceId = value[0];
         break;
      default:
         this.parameters[key] = value;
         break;
      }
   }
}
