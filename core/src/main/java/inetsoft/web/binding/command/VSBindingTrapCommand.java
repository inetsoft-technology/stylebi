/*
 * inetsoft-core - StyleBI is a business intelligence web application.
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
package inetsoft.web.binding.command;

import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.viewsheet.command.MessageCommand;

/**
 * Command that instructs the client to check trap.
 *
 * @since 12.3
 */
public class VSBindingTrapCommand extends MessageCommand {
   /**
    * Construstor.
    */
   public VSBindingTrapCommand(BindingModel model, DataRefModel[] fields) {
      this.model = model;
      this.fields = fields;
   }

   /**
    * Get the model.
    * @return the special model.
    */
   public BindingModel getModel() {
      return model;
   }

   /**
    * Set the model.
    * @param model the special model.
    */
   public void setModel(BindingModel model) {
      this.model = model;
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

   private BindingModel model;
   private DataRefModel[] fields;
}
