/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.report.composition;

import java.io.Serializable;

/**
 * The runtime sheet that holds the edit lock of an asset: the editable sheet of the asset that was
 * opened first.
 * <p>
 * "First" is decided by the number at the end of the runtime id ({@code <path>-<number>}). The
 * number comes from a counter shared by the whole cluster (see {@code WorksheetEngine.getNextID}),
 * so every node orders the sheets of all nodes the same way and agrees on a single holder.
 */
final class SheetLockHolder implements Serializable {
   /**
    * @param user    the name of the user of the sheet, {@code null} if the sheet has no user.
    * @param sheetId the runtime id of the sheet.
    */
   SheetLockHolder(String user, String sheetId) {
      this.user = user;
      this.sheetId = sheetId;
      this.sequence = sequenceOf(sheetId);
   }

   /**
    * Get the name of the user that holds the lock.
    */
   String getUser() {
      return user;
   }

   /**
    * Get the runtime id of the sheet that holds the lock.
    */
   String getSheetId() {
      return sheetId;
   }

   /**
    * Get the holder that opened its sheet first.
    *
    * @return the earlier of the two, or the other one if one is {@code null}.
    */
   static SheetLockHolder earlier(SheetLockHolder holder1, SheetLockHolder holder2) {
      if(holder1 == null) {
         return holder2;
      }

      if(holder2 == null) {
         return holder1;
      }

      return holder2.isBefore(holder1) ? holder2 : holder1;
   }

   /**
    * Get the opening order of a runtime id, the number after its last '-'.
    *
    * @return the number, or {@link Long#MAX_VALUE} if the id does not end in a number, so a sheet
    *         of an unknown order never takes the lock from a sheet of a known order.
    */
   static long sequenceOf(String id) {
      int dash = id == null ? -1 : id.lastIndexOf('-');

      if(dash >= 0 && dash < id.length() - 1) {
         try {
            return Long.parseLong(id.substring(dash + 1));
         }
         catch(NumberFormatException ignore) {
            // not a counter
         }
      }

      return Long.MAX_VALUE;
   }

   private boolean isBefore(SheetLockHolder other) {
      if(sequence != other.sequence) {
         return sequence < other.sequence;
      }

      return sheetId != null && (other.sheetId == null || sheetId.compareTo(other.sheetId) < 0);
   }

   @Override
   public String toString() {
      return "SheetLockHolder{user='" + user + "', sheetId='" + sheetId + "'}";
   }

   private final String user;
   private final String sheetId;
   private final long sequence;
   private static final long serialVersionUID = 1L;
}
