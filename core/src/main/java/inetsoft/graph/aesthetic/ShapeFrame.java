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
package inetsoft.graph.aesthetic;

import inetsoft.graph.data.DataSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * This class defines the common API for all shape frames.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public abstract class ShapeFrame extends VisualFrame {
   /**
    * Get the shape for the specified cell.
    * @param data the specified dataset.
    * @param col the name of the specified column.
    * @param row the specified row index.
    */
   public abstract GShape getShape(DataSet data, String col, int row);

   /**
    * Get the shape for the specified value.
    */
   public abstract GShape getShape(Object val);

   @Override
   boolean isMultiItem(Method getter) throws Exception {
      try {
         Class[] params = {Object.class};
         return super.isMultiItem(getClass().getMethod("getShape", params));
      }
      catch(Exception ex) {
         LOG.warn("Failed to determine if frame is a multi-item shape frame", ex);
      }
      
      return true;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(ShapeFrame.class);
}
