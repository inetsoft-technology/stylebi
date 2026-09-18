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
package inetsoft.web.admin.sheet;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

import java.util.*;

/**
 * {@code SheetList} contains the list of viewsheets or worksheets.
 */
@Validated
@Schema(description = "A list of viewsheets or worksheets.")
public class SheetList {
   /**
    * Gets the list of sheets.
    *
    * @return the sheet list.
    */
   @NotNull
   @Schema(description = "The list of sheets.")
   public List<Sheet> getSheets() {
      if(sheets == null) {
         sheets = new ArrayList<>();
      }
      else {
         Iterator<Sheet> iterator = sheets.iterator();

         while(iterator.hasNext()) {
            Sheet sheet = iterator.next();

            if(sheet == null) {
               iterator.remove();
            }
         }
      }

      return sheets;
   }

   /**
    * Sets the list of sheets.
    *
    * @param sheets the sheet list.
    */
   public void setSheets(List<Sheet> sheets) {
      this.sheets = sheets;
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      SheetList sheetList = (SheetList) o;
      return Objects.equals(sheets, sheetList.sheets);
   }

   @Override
   public int hashCode() {
      return Objects.hash(sheets);
   }

   @Override
   public String toString() {
      return "SheetList{" +
         "sheets=" + sheets +
         '}';
   }

   private List<Sheet> sheets;
}
