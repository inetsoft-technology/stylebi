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
package inetsoft.web.admin.monitoring;

import inetsoft.sree.SreeEnv;

import java.io.Serializable;
import java.util.*;

/**
 * Status container contains status.
 */
public class StatusContainer implements Serializable {
   /**
    * Create a StatusConainer.
    */
   public StatusContainer() {
      String mscount = SreeEnv.getProperty("monitor.dataset.size");
      maxStatusCount = Integer.parseInt(mscount);
   }

   /**
    * Add a status to this container.
    */
   public synchronized void add(Object stus) {
      list.add(stus);
      shrink();
   }

   /**
    * Add a collection of status to this container.
    */
   public synchronized void addAll(Collection<?> coll) {
      list.addAll(coll);
      shrink();
   }

   /**
    * Returns an array containing all of the elements in this list in
    * the correct order.
    */
   public synchronized Object[] toArray() {
      return list.toArray();
   }

   /**
    * Clear the container.
    */
   public synchronized void clear() {
      list.clear();
   }

   /**
    * Get the size of the container.
    */
   public synchronized int size() {
      return list.size();
   }

   /**
    * Update the max size of the container.
    */
   public synchronized void updateMaxSize() {
      int mscount =
         Integer.parseInt(SreeEnv.getProperty("monitor.dataset.size"));

      if(mscount == maxStatusCount) {
         return;
      }

      maxStatusCount = mscount;
   }

   /**
    * Remove the first element.
    */
   private void removeFirst() {
      list.removeFirst();
   }

   /**
    * Shrink the size of the container to the max count.
    */
   private void shrink() {
      updateMaxSize();
      
      if(size() <= maxStatusCount) {
         return;
      }

      for(int i = size(); i > maxStatusCount; i--) {
         removeFirst();
      }
   }

   private Deque<Object> list = new ArrayDeque<>();
   private int maxStatusCount = 300; // default
}