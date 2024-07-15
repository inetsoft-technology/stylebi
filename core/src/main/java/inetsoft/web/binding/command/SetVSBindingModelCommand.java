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

import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.viewsheet.command.ViewsheetCommand;

/**
 * Command that instructs the client to refresh an assembly object.
 *
 * @since 12.3
 */
public class SetVSBindingModelCommand implements ViewsheetCommand {
   /**
    * Construstor.
    */
   public SetVSBindingModelCommand(BindingModel binding) {
      this.binding = binding;
   }

   /**
    * Get the binding model.
    * @return the binding model.
    */
   public BindingModel getBinding() {
      return binding;
   }

   /**
    * Set binding model.
    * @param binding the binding model.
    */
   public void setBinding(BindingModel binding) {
      this.binding = binding;
   }

   private BindingModel binding;
}
