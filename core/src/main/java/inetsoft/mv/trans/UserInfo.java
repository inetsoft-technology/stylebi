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
package inetsoft.mv.trans;

import inetsoft.util.Tool;

import java.io.Serializable;

/**
 * This class is used to record the transformation information that are shown
 * for user.
 *
 * @author InetSoft Technology Corp
 * @version 12.0
 */
public final class UserInfo implements Serializable {
   public UserInfo(String sheetName, String boundTable, String msg) {
      this.sheetName = sheetName;
      this.boundTable = boundTable;
      this.msg = msg;
   }

   public String getSheetName() {
      return sheetName;
   }

   public String getBoundTable() {
      return boundTable;
   }

   public String getMessage() {
      return msg;
   }

   public boolean equals(Object obj) {
      if(!(obj instanceof UserInfo)) {
         return false;
      }

      UserInfo info = (UserInfo) obj;
      return Tool.equals(sheetName, info.sheetName) && Tool.equals(boundTable, info.boundTable)
         && Tool.equals(msg, info.msg);
   }

   private String sheetName;
   private String boundTable;
   private String msg;
}
