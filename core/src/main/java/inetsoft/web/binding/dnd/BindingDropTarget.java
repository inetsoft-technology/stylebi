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
package inetsoft.web.binding.dnd;

public class BindingDropTarget extends DropTarget {
   public void setDropType(String type) {
      this.type = type;
   }

   public String getDropType() {
      return type;
   }

   public void setDropIndex(int index) {
      this.index = index;
   }

   public int getDropIndex() {
      return index;
   }

   public void setReplace(boolean replace) {
      this.replace = replace;
   }

   public boolean getReplace() {
      return replace;
   }

   public TransferType getTransferType() {
      return transferType;
   }

   public void setTransferType(TransferType transferType) {
      this.transferType = transferType;
   }

   private String type;
   private int index;
   private boolean replace;
   private TransferType transferType;
}
