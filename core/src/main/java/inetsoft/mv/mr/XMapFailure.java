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
package inetsoft.mv.mr;

import inetsoft.mv.comm.*;

import java.io.IOException;

/**
 * XMapFailure, the map failure returned by one map task. The map task is
 * executed at one data node.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public final class XMapFailure implements XTransferable {
   /**
    * Create a map failure by providing a map task.
    */
   public static final XMapFailure create(XMapTask task) {
      XMapFailure failure = new XMapFailure();
      failure.setID(task.getID());
      failure.setXBlock(task.getXBlock());
      failure.setHost(task.getHost());
      return failure;
   }

   /**
    * Create an instance of XMapFailure.
    */
   public XMapFailure() {
      super();
   }

   /**
    * Get the job id.
    */
   public final String getID() {
      return id;
   }

   /**
    * Set the job id.
    */
   public final void setID(String id) {
      this.id = id;
   }

   /**
    * Get the id of the XBlock to be accessed.
    */
   public final String getXBlock() {
      return bid;
   }

   /**
    * Set the id of the XBlock to be accessed.
    */
   public final void setXBlock(String bid) {
      this.bid = bid;
   }

   /**
    * Get the data node to execute this map task.
    */
   public final String getHost() {
      return host;
   }

   /**
    * Set the data node to execute this map task.
    */
   public final void setHost(String host) {
      this.host = host;
   }

   /**
    * Get the reason for this failure.
    */
   public final String getReason() {
      return reason;
   }

   /**
    * Set the reason for this failure.
    */
   public final void setReason(String reason) {
      this.reason = reason;
   }

   /**
    * Read this transferable.
    */
   @Override
   public void read(XReadBuffer buf) throws IOException {
      id = buf.readString();
      bid = buf.readString();
      host = buf.readString();
      reason = buf.readString();
   }

   /**
    * Write this transferable.
    */
   @Override
   public void write(XWriteBuffer buf) throws IOException {
      buf.writeString(id);
      buf.writeString(bid);
      buf.writeString(host);
      buf.writeString(reason);
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return "MapFailure-" + bid + "<id:" + id + ",host:"+ host +
         ",reason:"+ reason + '>';
   }

   private String id;
   private String bid;
   private String host;
   private String reason;
}
