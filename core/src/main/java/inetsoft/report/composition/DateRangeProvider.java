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
package inetsoft.report.composition;

import inetsoft.uql.AbstractCondition;
import inetsoft.uql.asset.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Date range provider provides available date ranges.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class DateRangeProvider implements AssetObject {
   /**
    * Constructor.
    */
   public DateRangeProvider() {
      super();
   }

   /**
    * Constructor.
    */
   public DateRangeProvider(DateCondition[] dateConditions,
                            DateRangeAssembly[] dateRangeAssemblies) {
      this.dateConditions = dateConditions;
      this.dateRangeAssemblies = dateRangeAssemblies;
   }

   /**
    * Get built-in date conditions.
    */
   public DateCondition[] getBuiltinDateConditions() {
      return dateConditions;
   }

   /**
    * Set built-in date conditions.
    */
   public void setBuiltinDateConditions(DateCondition[] dateConditions) {
      this.dateConditions = dateConditions;
   }

   /**
    * Get user-defined date range assemblies.
    */
   public DateRangeAssembly[] getDateRangeAssemblies() {
      return dateRangeAssemblies;
   }

   /**
    * Set user-defined date range assemblies.
    */
   public void setDateRangeAssemblies(DateRangeAssembly[] dateRangeAssemblies) {
      this.dateRangeAssemblies = dateRangeAssemblies;
   }

   /**
    * Add listener.
    */
   public void addActionListener(ActionListener listener) {
      listeners.add(new WeakReference(listener));
   }

   /**
    * Fire action event.
    */
   public void fireActionEvent(ActionEvent event) {
      try {
         for(int i = listeners.size() - 1; i >= 0; i--) {
            WeakReference ref = (WeakReference) listeners.get(i);
            ActionListener listener = (ActionListener) ref.get();

            if(listener == null) {
               listeners.remove(i);
            }
            else {
               listener.actionPerformed(event);
            }
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to process action event", ex);
      }
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<dateRangeProvider class=\"" +
         getClass().getName() + "\">");

      if(dateConditions != null) {
         writer.println("<builtin>");

         for(int i = 0; i < dateConditions.length; i++) {
            dateConditions[i].writeXML(writer);
         }

         writer.println("</builtin>");
      }

      if(dateRangeAssemblies != null) {
         writer.println("<range>");

         for(int i = 0; i < dateRangeAssemblies.length; i++) {
            dateRangeAssemblies[i].writeXML(writer);
         }

         writer.println("</range>");
      }

      writer.println("</dateRangeProvider>");
   }

   /**
    * Method to parse an xml segment.
    * @param tag the specified xml element.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      Element builtinElement = Tool.getChildNodeByTagName(tag, "builtin");

      if(builtinElement != null) {
         NodeList list = builtinElement.getChildNodes();
         ArrayList array = new ArrayList();

         for(int i = 0; i < list.getLength(); i++) {
            Element condition = (Element) list.item(i);
            array.add(AbstractCondition.createXCondition(condition));
         }

         dateConditions = new DateCondition[array.size()];
         array.toArray(dateConditions);
      }

      Element rangeNode = Tool.getChildNodeByTagName(tag, "range");

      if(rangeNode != null) {
         NodeList list = rangeNode.getChildNodes();
         ArrayList array = new ArrayList();

         for(int i = 0; i < list.getLength(); i++) {
            Element rangenode = (Element) list.item(i);
            array.add(AbstractCondition.createXCondition(rangenode));
         }

         dateRangeAssemblies = new DateRangeAssembly[array.size()];
         array.toArray(dateRangeAssemblies);
      }
   }

   /**
    * Clone this object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         // ignore it
      }

      return null;
   }

   private DateCondition[] dateConditions = new DateCondition[0];
   private DateRangeAssembly[] dateRangeAssemblies = new DateRangeAssembly[0];
   private List listeners = new ArrayList();

   private static final Logger LOG =
      LoggerFactory.getLogger(DateRangeProvider.class);
}
