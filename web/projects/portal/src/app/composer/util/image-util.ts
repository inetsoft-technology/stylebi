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
export class ImageType {
   public static SERVER: string = "^SERVER^";
   public static UPLOAD: string = "^UPLOADED^";
   public static SKIN: string = "^SKIN^";
}

export function getImageName(path: string): string {
   if(!path) {
      return null;
   }
   if(path.indexOf(ImageType.SERVER) == 0) {
      return path.substr(ImageType.SERVER.length);
   }
   else if(path.indexOf(ImageType.UPLOAD) == 0) {
      return path.substr(ImageType.UPLOAD.length);
   }
   else if(path.indexOf(ImageType.SKIN) == 0) {
      return path.substr(ImageType.SKIN.length);
   }
   else {
      return path;
   }
}

export function getImageType(path: string): string {
   if(path && path.indexOf(ImageType.SERVER) == 0) {
      return ImageType.SERVER;
   }
   else if(path && path.indexOf(ImageType.UPLOAD) == 0) {
      return ImageType.UPLOAD;
   }
   else if(path && path.indexOf(ImageType.SKIN) == 0) {
      return ImageType.SKIN;
   }
   else {
      return "";
   }
}