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
package inetsoft.analytic.composition.command;

import inetsoft.report.composition.AssetCommand;
import inetsoft.report.composition.command.DataCommand;
import inetsoft.util.ItemList;

import java.util.List;

public class PropertyDataCommand extends AssetCommand implements DataCommand {
   /**
    * Constructor.
    */
   public PropertyDataCommand() {
      super();
   }

   /**
    * Constructor.
    * @param table properties.
    */
   public PropertyDataCommand(List props) {
      this();
      ItemList list = new ItemList("props");
      list.addAllItems(props);

      put("props", list);
   }

   /**
    * Get the data contained in the data command.
    * @return the data contained in the data command.
    */
   @Override
   public Object getData() {
      return get("props");
   }
}