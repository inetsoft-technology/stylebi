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
import { VSObjectEvent } from "./vs-object-event";

/**
 * Event used to change the fileName of a vs upload object.
 */
export class UploadCompleteEvent extends VSObjectEvent {
   /**
    * The new file name.
    */
   public fileName: string;

   /**
    * Creates a new instance of <tt>UploadCompleteEvent</tt>.
    *
    * @param objectName the name of the object.
    * @param fileName       the new file name.
    */
   constructor(objectName: string, fileName: string) {
      super(objectName);
      this.fileName = fileName;
   }
}
