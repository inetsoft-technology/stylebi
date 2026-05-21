/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

package inetsoft.web.wiz.model;

/**
 * Response body for POST /api/wiz/visualization/save.
 */
public class WizVisualizationSaveResult {
   public String getSavedViewsheetIdentifier() {
      return savedViewsheetIdentifier;
   }

   public void setSavedViewsheetIdentifier(String savedViewsheetIdentifier) {
      this.savedViewsheetIdentifier = savedViewsheetIdentifier;
   }

   public String getThumbnail() {
      return thumbnail;
   }

   public void setThumbnail(String thumbnail) {
      this.thumbnail = thumbnail;
   }

   private String savedViewsheetIdentifier;
   /** Base64-encoded PNG data URI ({@code data:image/png;base64,...}) for chart assemblies,
    *  or raw SVG markup for vector-rendered assemblies. */
   private String thumbnail;
}
