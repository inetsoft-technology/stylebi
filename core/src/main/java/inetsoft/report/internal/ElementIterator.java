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
package inetsoft.report.internal;

import inetsoft.report.*;

import java.util.*;

/**
 * This class enumerations through all elements inside a section or a bean.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ElementIterator {
   /**
    * Get an enumeration of all elements in a report.
    */
   public static Enumeration elements(ReportSheet report) {
      ElementIterator iter = new ElementIterator();

      if(report != null) {
         iter.processReport(report.getAllElements());

         iter.processReport(report.getAllHeaderElements());
         iter.processReport(report.getAllFooterElements());
      }

      if(iter.elems.size() == 0) {
         return Collections.emptyEnumeration();
      }

      return iter.elems.elements();
   }

   /**
    * Get an enumeration of elements. If the element is not a section or
    * a bean, it returns null.
    */
   public static Enumeration elements(ReportElement elem) {
      ElementIterator iter = new ElementIterator();
      iter.process(elem, false);

      if(iter.elems.size() == 0) {
         return Collections.emptyEnumeration();
      }

      return iter.elems.elements();
   }

   /**
    * @param elemv elements of vector of vectors or vector of FixedContainer.
    */
   private void processReport(Vector elemv) {
      for(int i = 0; i < elemv.size(); i++) {
         Object val = elemv.elementAt(i);

         if(val instanceof Vector) {
            processReport(((Vector) val).elements());
         }
         else if(val instanceof FixedContainer) {
            process((FixedContainer) val);
         }
         else if(val instanceof ReportElement) {
            process((ReportElement) val, true);
         }
      }
   }

   /**
    * @param elems element vector.
    */
   private void processReport(Enumeration elems) {
      while(elems.hasMoreElements()) {
         process((ReportElement) elems.nextElement(), true);
      }
   }

   /**
    * Process elements.
    */
   protected void process(ReportElement elem, boolean add) {
      if(elem instanceof SectionElement) {
         process(((SectionElement) elem).getSection());
      }

      if(add) {
         elems.addElement(elem);
      }
   }

   /**
    * Process header text in a section.
    */
   protected void process(SectionLens lens) {
      if(lens == null) {
         return;
      }

      process(lens.getSectionHeader());

      SectionBand[] content = lens.getSectionContent();

      process(content);
      process(lens.getSectionFooter());
   }

   /**
    * Process header text in a section band.
    */
   protected void process(SectionBand[] bands) {
      for(int i = 0; i < bands.length; i++) {
         process(bands[i]);
      }
   }

   /**
    * Process header text in a section band.
    */
   protected void process(FixedContainer band) {
      for(int i = 0; i < band.getElementCount(); i++) {
         process(band.getElement(i), true);
      }
   }

   /**
    * Process all elements in a bean.
    */
   protected void process(ReportSheet bean) {
      if(bean == null) {
         return;
      }

      processReport(bean.getAllElements());
      processReport(bean.getAllHeaderElements());
      processReport(bean.getAllFooterElements());
   }

   private Vector elems = new Vector();
}

