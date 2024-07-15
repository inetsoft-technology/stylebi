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
package inetsoft.util;

import java.util.LinkedList;
import java.util.List;

/**
 * Implementation of the queue data structure. In a queue, elements are removed
 * in the same order in which they where added (FIFO).
 */
public class Queue<E> extends LinkedList<E> {
   public Queue() {
   }

   public Queue(List<E> olist) {
      super(olist);
   }

   /**
    * Get the first element in the queue and remove it from the queue.
    * @return the first element in the queue, or <code>null</code> if the
    * queue is empty.
    */
   public E dequeue() {
      if(size() > 0) {
         return remove(0);
      }

      return null;
   }

   /**
    * Add an element to the end of the queue.
    * @param obj the element to add.
    */
   public void enqueue(E obj) {
      add(obj);
   }

   /**
    * Get the first element in the queue without removing it.
    * @return the first element in the queue, or <code>null</code> if the
    * queue is empty.
    */
   public E peek() {
      if(size() > 0) {
         return get(0);
      }

      return null;
   }
}
