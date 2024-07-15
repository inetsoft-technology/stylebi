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

public class TableTransfer extends DataTransfer {
   public void setDragType(String type) {
      this.type = type;
   }

   public String getDragType() {
      return type;
   }

   public void setDragIndex(int idx) {
      this.index = idx;
   }

   public int getDragIndex() {
      return index;
   }

   public TransferType getTransferType() {
      return transferType;
   }

   public void setTransferType(TransferType transferType) {
      this.transferType = transferType;
   }

   private String type;
   private int index;
   private TransferType transferType;

}
