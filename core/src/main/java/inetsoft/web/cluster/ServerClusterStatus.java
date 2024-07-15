/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.cluster;

import java.io.Serializable;

public class ServerClusterStatus implements Serializable {
   public ServerClusterStatus(){
   }

   public ServerClusterStatus(String address){
      this.address = address;
   }

   public Status getStatus() {
      return status;
   }

   public void setStatus(Status status) {
      this.status = status;
   }

   public boolean isPaused() {
      return paused;
   }

   public void setPaused(boolean paused) {
      this.paused = paused;
   }

   public float getLoad() {
      return load;
   }

   public void setLoad(float load) {
      this.load = load;
   }

   public String getAddress() {
      return address;
   }

   public void setAddress(String address) {
      this.address = address;
   }

   @Override
   public String toString() {
      return "ServerClusterStatus{" +
         "status=" + status +
         ", paused=" + paused +
         ", load=" + load +
         ", address='" + address + '\'' +
         '}';
   }

   private volatile Status status;
   private volatile boolean paused;
   private volatile float load;
   private volatile String address;

   private static final long serialVersionUID = 1L;

   public enum Status {
      OK, BUSY, PAUSED, DOWN
   }
}
