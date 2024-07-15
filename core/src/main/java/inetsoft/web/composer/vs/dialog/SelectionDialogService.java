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
package inetsoft.web.composer.vs.dialog;

import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.vs.OutputColumnRefModel;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SelectionDialogService {
   /**
    * Attempt to find the column ref model corresponding to the given table name and column ref.
    *
    * @param node      the root tree node to search.
    * @param tableName the matching table name.
    * @param columnRef the column ref the model should correspond to.
    * @return an Optional containing a matching ref model if one is found, otherwise empty.
    */
   public Optional<OutputColumnRefModel> findSelectedOutputColumnRefModel(
      TreeNodeModel node,
      String tableName,
      ColumnRef columnRef)
   {
      for(final TreeNodeModel child : node.children()) {
         final Object data = child.data();

         if(data instanceof OutputColumnRefModel) {
            final OutputColumnRefModel refModel = (OutputColumnRefModel) data;
            boolean cube = tableName.startsWith(Assembly.CUBE_VS);

            if(refModel.getTable().equals(tableName) &&
               (!cube && Tool.equals(columnRef.getAttribute(), refModel.getAttribute()) ||
               cube && Tool.equals(columnRef.getEntity() + "." + columnRef.getAttribute(),
               refModel.getEntity() + "." + refModel.getAttribute())))
            {
               return Optional.of(refModel);
            }
         }

         if(child.children().size() > 0) {
            final Optional<OutputColumnRefModel> model =
               findSelectedOutputColumnRefModel(child, tableName, columnRef);

            if(model.isPresent()) {
               return model;
            }
         }
      }

      return Optional.empty();
   }
}
