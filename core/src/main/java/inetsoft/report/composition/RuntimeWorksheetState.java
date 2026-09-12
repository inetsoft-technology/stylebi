/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import java.util.Arrays;
import java.util.Objects;

class RuntimeWorksheetState extends RuntimeSheetState {
   public String getWs() {
      return ws;
   }

   public void setWs(String ws) {
      this.ws = ws;
   }

   public byte[] getWsDelta() {
      return wsDelta;
   }

   public void setWsDelta(byte[] wsDelta) {
      this.wsDelta = wsDelta;
   }

   public String getVars() {
      return vars;
   }

   public void setVars(String vars) {
      this.vars = vars;
   }

   public boolean isPreview() {
      return preview;
   }

   public void setPreview(boolean preview) {
      this.preview = preview;
   }

   public boolean isGettingStarted() {
      return gettingStarted;
   }

   public void setGettingStarted(boolean gettingStarted) {
      this.gettingStarted = gettingStarted;
   }

   public String getPid() {
      return pid;
   }

   public void setPid(String pid) {
      this.pid = pid;
   }

   public boolean isSyncData() {
      return syncData;
   }

   public void setSyncData(boolean syncData) {
      this.syncData = syncData;
   }

   public RuntimeWorksheetState getJoinWS() {
      return joinWS;
   }

   public void setJoinWS(RuntimeWorksheetState joinWS) {
      this.joinWS = joinWS;
   }

   @Override
   public boolean equals(Object o) {
      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      if(!super.equals(o)) {
         return false;
      }

      RuntimeWorksheetState that = (RuntimeWorksheetState) o;
      return preview == that.preview && gettingStarted == that.gettingStarted &&
         syncData == that.syncData && Objects.equals(ws, that.ws) &&
         Arrays.equals(wsDelta, that.wsDelta) &&
         Objects.equals(vars, that.vars) && Objects.equals(pid, that.pid) &&
         Objects.equals(joinWS, that.joinWS);
   }

   @Override
   public int hashCode() {
      int result = Objects.hash(
         super.hashCode(), ws, vars, preview, gettingStarted, pid, syncData, joinWS);
      result = 31 * result + Arrays.hashCode(wsDelta);
      return result;
   }

   @Override
   public String toString() {
      return "RuntimeWorksheetState{" +
         "ws='" + ws + '\'' +
         ", wsDelta=" + (wsDelta != null ? wsDelta.length + " bytes" : "null") +
         ", vars=" + vars +
         ", preview=" + preview +
         ", gettingStarted=" + gettingStarted +
         ", pid='" + pid + '\'' +
         ", syncData=" + syncData +
         ", joinWS=" + joinWS +
         '}';
   }

   private String ws;
   private byte[] wsDelta;  // VCDIFF delta-encoded ws for joinWS (relative to parent ws)
   private String vars;
   private boolean preview;
   private boolean gettingStarted;
   private String pid;
   private boolean syncData;
   private RuntimeWorksheetState joinWS;
}
