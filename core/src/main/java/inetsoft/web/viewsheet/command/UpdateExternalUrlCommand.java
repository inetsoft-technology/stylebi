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
package inetsoft.web.viewsheet.command;

/**
 * Command used to notify embedded HTML that an assembly has changed in case it is consuming its
 * data.
 *
 * @since 13.4
 */
public class UpdateExternalUrlCommand implements ViewsheetCommand {
   /**
    * Gets the absolute name of the updated assembly.
    *
    * @return the assembly name.
    */
   public String getName() {
      return name;
   }

   /**
    * Sets the absolute name of the updated assembly.
    *
    * @param name the assembly name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Gets the URL used to get the data of the updated assembly.
    *
    * @return the data URL.
    */
   public String getUrl() {
      return url;
   }

   /**
    * Sets the URL used to get the data of the updated assembly.
    *
    * @param url the data URL.
    */
   public void setUrl(String url) {
      this.url = url;
   }

   private String name;
   private String url;
}
