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
package inetsoft.uql.xmla;

import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;

/**
 * XMLAQuery2 represents a query for MDX request in XMLA, especially for
 * aggregate information.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class XMLAQuery2 extends XMLAQuery {
   /**
    * Contructor.
    */
   public XMLAQuery2() {
      super();

      aggregateInfo = new AggregateInfo();
   }

   /**
    * Get the group info.
    * @return the group info.
    */
   public AggregateInfo getAggregateInfo() {
      return aggregateInfo;
   }

   /**
    * Set the group info.
    * @param info the specified group info.
    */
   public void setAggregateInfo(AggregateInfo info) {
      this.aggregateInfo = info;
   }

   /**
    * Convert aggregate info if necessary.
    */
   public void convertAggregateInfo() {
      if(aggregateInfo == null) {
         return;
      }

      for(int i = 0; i < aggregateInfo.getGroupCount(); i++) {
         GroupRef gref = aggregateInfo.getGroup(i);
         ColumnRef column = (ColumnRef) gref.getDataRef();
         DataRef ref0 = convertDataRef(column);
         gref.setDataRef(ref0);
      }

      for(int i = 0; i < aggregateInfo.getAggregateCount(); i++) {
         AggregateRef aref = aggregateInfo.getAggregate(i);
         ColumnRef column = (ColumnRef) aref.getDataRef();
         DataRef ref0 = convertDataRef(column);
         aref.setDataRef(ref0);
      }
   }

   /**
    * Get measure index.
    * @param name measure name.
    * @return measure index.
    */
   @Override
   public int indexOfMeasure(String name) {
      int idx = super.indexOfMeasure(name);

      if(idx >= 0 || aggregateInfo == null) {
         return idx;
      }

      for(int i = 0; i < getMeasuresCount(); i++) {
         DataRef ref = getMeasureRef(i);

         if(isRight(ref, name)) {
            return i;
         }

         ref = aggregateInfo.getAggregate(ref);

         if(ref == null) {
            continue;
         }

         if(isRight(ref, name)) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Check if a data ref has the specified name.
    */
   @Override
   protected boolean isRight(DataRef ref, String name) {
      boolean right  = super.isRight(ref, name);

      if(right) {
         return true;
      }

      if(ref instanceof ColumnRef || ref instanceof AggregateRef) {
         if(Tool.equals(XMLAUtil.getCalcName(ref), name)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Convert data ref for backward compatibility.
    */
   @Override
   protected DataRef convertDataRef(DataRef ref) {
      if(!(ref instanceof ColumnRef)) {
         return super.convertDataRef(ref);
      }

      ColumnRef column = (ColumnRef) ref;
      DataRef ref0 = column.getDataRef();

      if(ref0 instanceof NamedRangeRef) {
         NamedRangeRef nref = (NamedRangeRef) ref0;
         DataRef oldAttr = nref.getDataRef();
         DataRef newAttr = super.convertDataRef(oldAttr);

         if(Tool.equals(oldAttr, newAttr)) {
            return ref;
         }

         column = (ColumnRef) column.clone();
         nref = (NamedRangeRef) nref.clone();
         nref.setDataRef(newAttr);
         column.setDataRef(nref);

         return column;
      }
      else if(ref0 instanceof AliasDataRef) {
         AliasDataRef aref = (AliasDataRef) ref0;
         DataRef oldAttr = aref.getDataRef();
         DataRef newAttr = super.convertDataRef(oldAttr);

         if(Tool.equals(oldAttr, newAttr)) {
            return ref;
         }

         column = (ColumnRef) column.clone();
         aref = (AliasDataRef) aref.clone();
         aref.setDataRef(newAttr);
         column.setDataRef(aref);

         return column;
      }

      return super.convertDataRef(ref);
   }

   /**
    * Get entity of data ref.
    */
   @Override
   protected String getEntity(DataRef ref) {
      return XMLAUtil.getEntity(ref);
   }

   /**
    * Get attribute of data ref.
    */
   @Override
   protected String getAttribute(DataRef ref) {
      return XMLAUtil.getAttribute(ref);
   }
   
   @Override
   protected void writeAggregateInfo(PrintWriter writer) {
      if(!aggregateInfo.isEmpty()) {
         aggregateInfo.writeXML(writer);
      }
   }
   
   @Override
   protected void parseAggregateInfo(Element root) {
      NodeList nlist = Tool.getChildNodesByTagName(root, "groupInfo");
      
      if(nlist != null && nlist.getLength() > 0) {
         try {
            aggregateInfo.parseXML((Element) nlist);
         }
         catch(Exception e) {
            LOG.error(
               "Failed to parse aggregateInfo: " + e.getMessage(), e);
         }
      }
   }

   private AggregateInfo aggregateInfo;

   private static final Logger LOG =
      LoggerFactory.getLogger(XMLAQuery2.class);
}