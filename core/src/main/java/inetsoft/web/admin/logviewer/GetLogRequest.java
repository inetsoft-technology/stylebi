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
package inetsoft.web.admin.logviewer;

import java.io.Serializable;

public class GetLogRequest implements Serializable {
   public GetLogRequest() {
   }

   public GetLogRequest(String logFileName, int offset, int length) {
      this.logFileName = logFileName;
      this.offset = offset;
      this.length = length;
   }

   public String getLogFileName() {
      return logFileName;
   }

   public void setLogFileName(String logFileName) {
      this.logFileName = logFileName;
   }

   public int getOffset() {
      return offset;
   }

   public void setOffset(int offset) {
      this.offset = offset;
   }

   public int getLength() {
      return length;
   }

   public void setLength(int length) {
      this.length = length;
   }

   @Override
   public String toString() {
      return "GetLogRequest{" +
         "logFileName='" + logFileName + '\'' +
         ", offset=" + offset +
         ", length=" + length +
         '}';
   }

   private String logFileName;
   private int offset;
   private int length;
}
