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
package inetsoft.web.binding.command;

import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.viewsheet.command.ViewsheetCommand;

/**
 * Command that instructs the client to check trap.
 *
 * @since 12.3
 */
public class SetGrayedOutFieldsCommand implements ViewsheetCommand {
   /**
    * Construstor.
    */
   public SetGrayedOutFieldsCommand(DataRefModel[] fields) {
      this.fields = fields;
   }

   /**
    * Get the grayed out fields.
    * @return the grayed out fields.
    */
   public DataRefModel[] getFields() {
      return fields;
   }

   /**
    * Set the grayed out fields.
    * @param fields the grayed out fields.
    */
   public void setFields(DataRefModel[] fields) {
      this.fields = fields;
   }

   private DataRefModel[] fields;
}
