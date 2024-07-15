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
package inetsoft.report.composition.command;

import inetsoft.report.composition.AssetTreeModel;

/**
 * Refresh tree command with model as data.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class RefreshTreeCommand2 extends RefreshTreeCommand implements
   DataCommand
{
   /**
    * Constructor.
    */
   public RefreshTreeCommand2() {
      super();
   }

   /**
    * Constructor.
    * @param model asset tree model.
    */
   public RefreshTreeCommand2(AssetTreeModel model) {
      super(model);
   }

   /**
    * Get the data contained in the data command.
    * @return the data contained in the data command.
    */
   @Override
   public Object getData() {
      return model;
   }
}
