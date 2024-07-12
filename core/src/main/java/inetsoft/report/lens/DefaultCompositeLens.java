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
package inetsoft.report.lens;

import inetsoft.report.ReportSheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * It takes a ReportSheet as constructor argument, and returns elements
 * in the report from nextElement().
 *
 * Use it to add another report to the middle of a report.
 */
public class DefaultCompositeLens implements Enumeration {
   /**
    * constructor, will return elements of the default type.
    *
    * @param report a report which is used to insert into another report..
    */
   public DefaultCompositeLens(ReportSheet report) {
      this(report, -1);
   }

   /**
    * constructor, will return elements of the specified type.
    *
    * @param  report  used to insert into another report..
    * @param  type the specified type.
    */
   public DefaultCompositeLens(ReportSheet report, int type) {
      if(report == null) {
         LOG.error("Inserted report can't be null!");
         return;
      }

      elements = report.getElements(type);
      currentPosition = -1;
   }

   /*
    * Tests if this enumeration contains more elements.
    *
    * @Returns: true if and only if this enumeration object
    *           contains at least one more element to provide;
    *           false otherwise.
    */
   @Override
   public boolean hasMoreElements() {
      return ((currentPosition + 1) < elements.size());
   }

   /*
    * Returns the next element of the report.
    *
    * @return: the next element of this enumeration.
    * @exception  NoSuchElementException  if no more elements exist.
    */
   @Override
   public Object nextElement() {
      currentPosition++;

      if(currentPosition >= elements.size()) {
         throw new NoSuchElementException();
      }

      return elements.elementAt(currentPosition);
   }

   private int currentPosition = -1;
   private Vector elements = new Vector();

   private static final Logger LOG =
      LoggerFactory.getLogger(DefaultCompositeLens.class);
}
