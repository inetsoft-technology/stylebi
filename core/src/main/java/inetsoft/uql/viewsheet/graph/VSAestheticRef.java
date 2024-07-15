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
package inetsoft.uql.viewsheet.graph;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.DataRefWrapper;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.ContentObject;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * VSAestheticRef, it stores aesthetic attributes and binding information.
 *
 * @version 10.0
 * @author InetSoft Technology Corp.
 */
public class VSAestheticRef extends AbstractAestheticRef implements ContentObject {
   /**
    * Get the runtime data ref.
    */
   @Override
   public VSDataRef getRTDataRef() {
      return rdataRef;
   }

   @Override
   public boolean isRuntime() {
      return runtime;
   }

   public void setRuntime(boolean runtime) {
      this.runtime = runtime;
   }

   /**
    * Set the runtime data ref.
    */
   public void setRTDataRef(VSDataRef rdataRef) {
      this.rdataRef = rdataRef;
   }

   /**
    * Set the dataRef.
    * @param ref the dataRef.
    */
   public void setALLDataRef(VSDataRef ref) {
      setDataRef(ref);
      setRTDataRef(ref);
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<VSAestheticRef>");
      writeContents(writer);
      writer.println("</VSAestheticRef>");
   }

   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(rdataRef != null) {
         writer.println("<rdataRef>");
         rdataRef.writeXML(writer);
         writer.println("</rdataRef>");
      }
   }

   /**
    * Rename the depended. This method should be called when an assembly or
    * other named variables are renamed. It updates of the dynamic references
    * to use the new name.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   public void renameDepended(String oname, String nname, Viewsheet vs) {
      DataRef dataRef = getDataRef();

      if(dataRef instanceof VSAggregateRef) {
         ((VSAggregateRef) dataRef).renameDepended(oname, nname, vs);
      }
      else if(dataRef instanceof VSDimensionRef) {
         ((VSDimensionRef) dataRef).renameDepended(oname, nname, vs);
      }
   }

   /**
    * Get the dynamic property values.
    * @return the dynamic values.
    */
   @Override
   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> list = super.getDynamicValues();

      DataRef dataRef = getDataRef();

      if(dataRef instanceof VSAggregateRef) {
         list.addAll(((VSAggregateRef) dataRef).getDynamicValues());
      }
      else if(dataRef instanceof VSDimensionRef) {
         list.addAll(((VSDimensionRef) dataRef).getDynamicValues());
      }

      return list;
   }

   /**
    * Update the info to fill in runtime value.
    * @param vs the specified viewsheet.
    * @param columns the specified column selection.
    */
   public void update(Viewsheet vs, ColumnSelection columns) {
      List list = new ArrayList();
      DataRef dataRef = getDataRef();

      if(dataRef instanceof VSAggregateRef) {
         list = ((VSAggregateRef) dataRef).update(vs, columns);
      }
      else if(dataRef instanceof VSDimensionRef) {
         list = ((VSDimensionRef) dataRef).update(vs, columns);
      }

      rdataRef = null;

      // use last ref, see bug1385629719209
      if(list != null) {
         for(int i = list.size() - 1; i >= 0; i--) {
            VSDataRef d = (VSDataRef) list.get(i);

            if(d != null) {
               rdataRef = d;
               break;
            }
         }
      }

      // make sure the data ref of aggregateRef is set so the type is
      // correct. Otherwise the aesthetic frame may be wrong.
      if(rdataRef != null && ((DataRefWrapper) dataRef).getDataRef() == null) {
         ((DataRefWrapper) dataRef).setDataRef(((DataRefWrapper) rdataRef).getDataRef());
      }
   }

   /**
    * Check if equals another object by content.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!(obj instanceof VSAestheticRef)) {
         return false;
      }

      return super.equalsContent(obj) && Tool.equalsContent(getDataRef(),
              ((VSAestheticRef) obj).getDataRef());
   }

   @Override
   public String toString() {
      return "VSAestheticRef" + System.identityHashCode(this) + " " + super.toString();
   }

   private VSDataRef rdataRef; // runtime dataref
   private boolean runtime;

   private static final Logger LOG =
      LoggerFactory.getLogger(VSAestheticRef.class);
}
