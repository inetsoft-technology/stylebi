/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.graph.treeviz.util;

/**
 * Processes Runnable objects sequentially on a processor thread.
 * The order in which the runnable objects are processed is
 * the same in which they were added to the dispatcher.
 * <p>
 * Design pattern used: Acceptor
 * Role in design pattern: EventCollector and EventProcessor
 *
 * @author Werner Randelshofef
 * @version 1.0 2002-05-18 Created
 */
public class SequentialDispatcher extends EventLoop {
   /**
    * Creates new SequentialDispatcher which processes Runnable objects
    * at java.lang.Thread.NORM_PRIORITY.
    */
   public SequentialDispatcher() {
   }

   /**
    * Creates a new SequentialDispatcher which processes Runnable Objects
    * at the desired thread priority.
    *
    * @param priority The Thread priority of the event processor.
    */
   public SequentialDispatcher(int priority) {
      super(priority);
   }

   /**
    * This method processes an event on the event processor thread.
    *
    * @param event An event from the queue.
    */
   protected void processEvent(Object event) {
      Runnable r = (Runnable) event;
      r.run();
   }

   /**
    * Queues the Runnable object for later execution on the
    * processor thread.
    */
   public void dispatch(Runnable r) {
      collectEvent(r);
   }
}
