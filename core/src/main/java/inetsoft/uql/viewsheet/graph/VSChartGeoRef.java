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
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * The VSChartGeoRef class.
 *
 * @version 10.2
 * @author InetSoft Technology Corp.
 */
public class VSChartGeoRef extends VSChartDimensionRef implements GeoRef {
   /**
    * Create a VSChartGeoRef.
    */
   public VSChartGeoRef() {
      super();
   }

   /**
    * Create a VSChartGeoRef.
    */
   public VSChartGeoRef(DataRef ref) {
      super(ref);
   }

   /**
    * Create a VSChartGeoRef.
    */
   public VSChartGeoRef(VSChartDimensionRef dref) {
      this(dref.getDataRef());

      setAxisDescriptor(dref.getAxisDescriptor());
      setGroupColumnValue(dref.getGroupColumnValue());
      setRankingOptionValue(dref.getRankingNValue());
      setRankingNValue(dref.getRankingNValue());
      setRankingColValue(dref.getRankingColValue());
      setSortByColValue(dref.getSortByColValue());
      setRankingOptionValue(dref.getRankingOptionValue());
      setSubTotalVisibleValue(dref.getSubTotalVisibleValue());
      setDateLevelValue(dref.getDateLevelValue());
      setOrder(dref.getOrder());
      setDateLevel(dref.getDateLevel());
      setTimeSeries(dref.isTimeSeries());
      setManualOrderList((ArrayList) dref.getManualOrderList());
      setRankingCondition(dref.getRankingCondition());
      setDates(dref.getDates());
      setRefType(dref.getRefType());
   }

   /**
    * Get geographic option.
    * @return the geographic option.
    */
   @Override
   public GeographicOption getGeographicOption() {
      return option;
   }

   /**
    * Set the geographic option.
    * @param option the geographic option.
    */
   @Override
   public void setGeographicOption(GeographicOption option) {
      this.option = option;
   }

   /**
    * Parse contents.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);
      Element node = Tool.getChildNodeByTagName(elem, "GeographicOption");

      if(node != null) {
         option.parseXML(node);
      }
   }

   /**
    * Write contents.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(option != null) {
         option.writeXML(writer);
      }
   }

   @Override
   public VSChartGeoRef clone() {
      try {
         VSChartGeoRef ref = (VSChartGeoRef) super.clone();

         if(option != null) {
            ref.setGeographicOption((GeographicOption) option.clone());
         }

         return ref;
      }
      catch(Exception e) {
         LOG.error("Failed to clone VSChartGeoRef", e);
         return null;
      }
   }

   /**
    * Check if equqls another object by content.
    */
   public boolean equals(Object obj) {
      // for ColumnSelection equals
      return equalsContent(obj);
   }

   /**
    * Check if equqls another object by content.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equalsContent(obj)) {
         return false;
      }

      if(!(obj instanceof VSChartGeoRef)) {
         return false;
      }

      VSChartGeoRef ref = (VSChartGeoRef) obj;

      if(!Tool.equals(option, ref.option)) {
         return false;
      }

      return true;
   }

   /**
    * Rename the depended. This method should be called when an assembly or
    * other named variables are renamed. It updates of the dynamic references
    * to use the new name.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   @Override
   public void renameDepended(String oname, String nname, Viewsheet vs) {
      super.renameDepended(oname, nname, vs);
      option.renameDepended(oname, nname, vs);
   }

   /**
    * Get the dynamic property values. Dynamic properties are properties that
    * can be a variable or a script.
    * @return the dynamic values.
    */
   @Override
   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> list = super.getDynamicValues();
      list.add(option.getDynamicValue());
      return list;
   }

   /**
    * Get the hyperlink dynamic property values.
    * @return the dynamic values.
    */
   @Override
   public List<DynamicValue> getHyperlinkDynamicValues() {
      return super.getHyperlinkDynamicValues();
   }

   /**
    * Update the info to fill in runtime value.
    * @param vs the specified viewsheet.
    * @param columns the specified column selection.
    */
   @Override
   public List<DataRef> update(Viewsheet vs, ColumnSelection columns) {
      List<DataRef> refs = super.update(vs, columns);
      Object[] oarr = toArray(option.getDynamicValue().getRuntimeValue(false));

      for(int i = 0; i < refs.size(); i++) {
         if(oarr.length > 0) {
            VSChartGeoRef ref = (VSChartGeoRef) refs.get(i);
            DynamicValue dvalue = ref.getGeographicOption().getDynamicValue();
            dvalue.setRValue(oarr[i %oarr.length]);
         }
      }

      return refs;
   }

   /**
    * Get an array that contains the object or if the object is an array.
    */
   private Object[] toArray(Object robj) {
      if(robj == null) {
         return new Object[] {robj};
      }

      if(!(robj instanceof Object[])) {
         robj = new Object[] {robj};
      }

      return (Object[]) robj;
   }

   private GeographicOption option = new GeographicOption();
   private static final Logger LOG =
      LoggerFactory.getLogger(VSChartDimensionRef.class);
}
