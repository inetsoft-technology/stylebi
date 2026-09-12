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
package inetsoft.web.binding.event;

import java.awt.*;
import java.io.Serializable;

/**
 * Class that encapsulates the parameters for applying a selection.
 *
 * @since 12.3
 */
public class ModifyTableLayoutEvent implements Serializable {
   /**
    * Get the assembly name.
    * @return assembly name.
    */
   public String getName() {
      return name;
   }

   /**
    * Set the assembly name.
    * @param name assembly name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the operation.
    * @return the operation.
    */
   public String getOp() {
      return op;
   }

   /**
    * Set the operation.
    * @param op the operation.
    */
   public void setOp(String op) {
      this.op = op;
   }

   /**
    * Get operation number.
    * @return the operation number.
    */
   public int getNum() {
      return num;
   }

   /**
    * Set the operation number.
    * @param num the operation number.
    */
   public void setNum(int num) {
      this.num = num;
   }

   /**
    * Get the selection.
    * @return selection.
    */
   public Rectangle getSelection() {
      return selection;
   }

   /**
    * Set the selection.
    * @param selection the selections.
    */
   public void setSelection(Rectangle selection) {
      this.selection = selection;
   }

   private String name;
   private String op;
   private int num;
   private Rectangle selection;
}
