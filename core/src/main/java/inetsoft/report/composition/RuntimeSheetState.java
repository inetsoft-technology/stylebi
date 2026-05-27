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

import java.util.*;

class RuntimeSheetState {
   public String getEntry() {
      return entry;
   }

   public void setEntry(String entry) {
      this.entry = entry;
   }

   public long getAccessed() {
      return accessed;
   }

   public void setAccessed(long accessed) {
      this.accessed = accessed;
   }

   public String getUser() {
      return user;
   }

   public void setUser(String user) {
      this.user = user;
   }

   public String getContextPrincipal() {
      return contextPrincipal;
   }

   public void setContextPrincipal(String contextPrincipal) {
      this.contextPrincipal = contextPrincipal;
   }

   public boolean isEditable() {
      return editable;
   }

   public void setEditable(boolean editable) {
      this.editable = editable;
   }

   public List<String> getPoints() {
      return points;
   }

   public void setPoints(List<String> points) {
      this.points = points;
   }

   public List<byte[]> getPointDeltas() {
      return pointDeltas;
   }

   public void setPointDeltas(List<byte[]> pointDeltas) {
      this.pointDeltas = pointDeltas;
   }

   public int getPoint() {
      return point;
   }

   public void setPoint(int point) {
      this.point = point;
   }

   public int getMax() {
      return max;
   }

   public void setMax(int max) {
      this.max = max;
   }

   public int getSavePoint() {
      return savePoint;
   }

   public void setSavePoint(int savePoint) {
      this.savePoint = savePoint;
   }

   public String getEid() {
      return eid;
   }

   public void setEid(String eid) {
      this.eid = eid;
   }

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public String getSocketSessionId() {
      return socketSessionId;
   }

   public void setSocketSessionId(String socketSessionId) {
      this.socketSessionId = socketSessionId;
   }

   public String getSocketUserName() {
      return socketUserName;
   }

   public void setSocketUserName(String socketUserName) {
      this.socketUserName = socketUserName;
   }

   public String getLowner() {
      return lowner;
   }

   public void setLowner(String lowner) {
      this.lowner = lowner;
   }

   public boolean isLockProcessed() {
      return isLockProcessed;
   }

   public void setLockProcessed(boolean lockProcessed) {
      isLockProcessed = lockProcessed;
   }

   public boolean isUpdate() {
      return isUpdate;
   }

   public void setUpdate(boolean update) {
      isUpdate = update;
   }

   public boolean isDisposed() {
      return disposed;
   }

   public void setDisposed(boolean disposed) {
      this.disposed = disposed;
   }

   public long getHeartbeat() {
      return heartbeat;
   }

   public void setHeartbeat(long heartbeat) {
      this.heartbeat = heartbeat;
   }

   public String getProp() {
      return prop;
   }

   public void setProp(String prop) {
      this.prop = prop;
   }

   public String getPreviousURL() {
      return previousURL;
   }

   public void setPreviousURL(String previousURL) {
      this.previousURL = previousURL;
   }

   @Override
   public boolean equals(Object o) {
      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      RuntimeSheetState that = (RuntimeSheetState) o;
      return accessed == that.accessed && editable == that.editable && point == that.point &&
         max == that.max && savePoint == that.savePoint &&
         isLockProcessed == that.isLockProcessed && disposed == that.disposed &&
         heartbeat == that.heartbeat && Objects.equals(entry, that.entry) &&
         Objects.equals(user, that.user) &&
         Objects.equals(contextPrincipal, that.contextPrincipal) &&
         Objects.equals(points, that.points) &&
         pointDeltasEqual(that.pointDeltas) &&
         Objects.equals(eid, that.eid) &&
         Objects.equals(id, that.id) && Objects.equals(socketSessionId, that.socketSessionId) &&
         Objects.equals(socketUserName, that.socketUserName) &&
         Objects.equals(lowner, that.lowner) && Objects.equals(prop, that.prop) &&
         Objects.equals(previousURL, that.previousURL);
   }

   private boolean pointDeltasEqual(List<byte[]> other) {
      if(pointDeltas == other) {
         return true;
      }

      if(pointDeltas == null || other == null || pointDeltas.size() != other.size()) {
         return pointDeltas == null && other == null;
      }

      for(int i = 0; i < pointDeltas.size(); i++) {
         if(!Arrays.equals(pointDeltas.get(i), other.get(i))) {
            return false;
         }
      }

      return true;
   }

   @Override
   public int hashCode() {
      int result = Objects.hash(
         entry, accessed, user, contextPrincipal, editable, points,
         point, max, savePoint, eid, id, socketSessionId, socketUserName, lowner,
         isLockProcessed, disposed, heartbeat, prop, previousURL);
      result = 31 * result + pointDeltasHashCode();
      return result;
   }

   private int pointDeltasHashCode() {
      if(pointDeltas == null) {
         return 0;
      }

      int hash = 1;

      for(byte[] bytes : pointDeltas) {
         hash = 31 * hash + Arrays.hashCode(bytes);
      }

      return hash;
   }

   @Override
   public String toString() {
      return "RuntimeSheetState{" +
         "entry='" + entry + '\'' +
         ", accessed=" + accessed +
         ", user='" + user + '\'' +
         ", contextPrincipal='" + contextPrincipal + '\'' +
         ", editable=" + editable +
         ", points=" + points +
         ", point=" + point +
         ", max=" + max +
         ", savePoint=" + savePoint +
         ", eid='" + eid + '\'' +
         ", id='" + id + '\'' +
         ", socketSessionId='" + socketSessionId + '\'' +
         ", socketUserName='" + socketUserName + '\'' +
         ", lowner='" + lowner + '\'' +
         ", isLockProcessed=" + isLockProcessed +
         ", disposed=" + disposed +
         ", heartbeat=" + heartbeat +
         ", prop='" + prop + '\'' +
         ", previousURL='" + previousURL + '\'' +
         '}';
   }

   private String entry;
   private long accessed;
   private String user;
   private String contextPrincipal;
   private boolean editable;
   private List<String> points;
   private List<byte[]> pointDeltas;  // VCDIFF delta-encoded checkpoints
   private int point;
   private int max;
   private int savePoint;
   private String eid;
   private String id;
   private String socketSessionId;
   private String socketUserName;
   private String lowner;
   private boolean isLockProcessed;
   private boolean disposed;
   private long heartbeat;
   private String prop;
   private String previousURL;
   private boolean isUpdate = false;
}
