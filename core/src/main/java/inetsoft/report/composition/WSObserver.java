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
package inetsoft.report.composition;

/**
 * WSObserver gets notified by engine.
 *
 * @version 8.0, 07/28/2005
 * @author InetSoft Technology Corp
 */
public interface WSObserver {
   /**
    * Level message.
    */
   public static final String LEVEL_MESSAGE = "__level__";
   /**
    * Data message.
    */
   public static final String DATA_MESSAGE = "__data__";
   /**
    * Result message.
    */
   public static final String RESULT_MESSAGE = "__result__";
   /**
    * Over message.
    */
   public static final String OVER_MESSAGE = "__OVER__";

   /**
    * Notify the worksheet observer.
    * @param name the specified message name.
    * @param obj the specified message body.
    */
   public void notify(String name, Object body);
}
