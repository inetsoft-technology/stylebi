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

import inetsoft.report.Hyperlink;
import inetsoft.report.internal.graph.ChangeChartProcessor;
import inetsoft.report.internal.graph.MapHelper;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.DateComparisonInfo;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.*;

/**
 * VSMapInfo maintains binding info of merged map.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public class VSMapInfo extends MergedVSChartInfo implements MapInfo {
   /**
    * Constructor.
    */
   public VSMapInfo() {
      super();

      rGeoRefs = new VSChartRef[0];
   }

   /**
    * Update the info to fill in runtime value.
    * @param vs the specified viewsheet.
    * @param columns the specified column selection.
    * @param sep true if the chart is separated, false otherwise.
    * @param pdim the specified dimension for period comparison.
    */
   @Override
   public synchronized void update(Viewsheet vs, ColumnSelection columns,
                                   boolean sep, VSChartDimensionRef pdim,
                                   String source, DateComparisonInfo dcInfo)
   {
      // prepare runtime geo cols for geo ref
      ColumnSelection gcols = new ColumnSelection();

      for(int i = 0; i < geoCols.getAttributeCount(); i++) {
         DataRef ref = geoCols.getAttribute(i);

         if(ref instanceof VSChartGeoRef) {
            VSChartGeoRef col = (VSChartGeoRef) ref;
            List rcols = col.update(vs, columns);

            for(int j = 0; j < rcols.size(); j++) {
               gcols.addAttribute((VSChartGeoRef) rcols.get(j));
            }
         }
      }

      VSMapInfo ominfo = (VSMapInfo) this.clone();

      // geo refs
      ArrayList list = new ArrayList();

      for(int i = 0; i < geoRefs.size(); i++) {
         VSChartGeoRef cref = geoRefs.get(i);

         try {
            List grefs = cref.update(vs, columns);

            for(int j = 0; j < grefs.size(); j++) {
               VSChartGeoRef gref = (VSChartGeoRef) grefs.get(j);
               String name = gref.getName();
               VSChartGeoRef gcol =
                  (VSChartGeoRef) getRTGeoColumns().getAttribute(name);

               if(gcol == null) {
                  gcol = (VSChartGeoRef) getGeoColumns().getAttribute(name);
               }

               if(gcol == null) {
                  continue;
               }

               GeographicOption opt = gcol.getGeographicOption();
               GeographicOption nopt = (GeographicOption) opt.clone();
               gref.setGeographicOption(nopt);
            }

            list.addAll(grefs);
         }
         catch(ColumnNotFoundException ex) {
            if(cref.isScript() || cref.isVariable()) {
               list.add(cref);

               if(cref.isScript()) {
                  throw ex;
               }
            }
         }
      }

      rGeoRefs = new VSChartGeoRef[list.size()];
      list.toArray(rGeoRefs);
      super.update(vs, columns, sep, pdim, source, dcInfo);

      // if geo ref is dynamic, layer may changes, frame should be fixed
      if(ominfo.getRTGeoFields().length > 0) {
         new ChangeChartProcessor().fixMapFrame(ominfo, this);
      }

      List list2 = new ArrayList();

      for(VSDataRef ref:getRTFields()) {
         if(ref instanceof VSChartGeoRef) {
            list2.add(ref);
         }
      }

      VSChartGeoRef[] arGeoRefs = new VSChartGeoRef[list2.size()];
      list2.toArray(arGeoRefs);
      applyHyperlink(arGeoRefs, getHyperlink());
      ChangeChartProcessor.fixMapNamedGroup(this, getRTFields());
   }

   /**
    * Get the geo field at the specified position.
    * @param idx the index of geo fields.
    * @return the geo field.
    */
   @Override
   public ChartRef getGeoFieldByName(int idx) {
      if(idx < 0 || idx >= geoRefs.size()) {
         return null;
      }

      return geoRefs.get(idx);
   }

   /**
    * Set the field at the specified index in geo fields.
    * @param idx the index of the geo fields.
    * @param field the specified field to be added to geo fields.
    */
   @Override
   public void setGeoField(int idx, ChartRef field) {
      geoRefs.set(idx, (VSChartGeoRef) field);
   }

   /**
    * Add a field to be used as geo field.
    * @param field the specified field to be added to geo field.
    */
   @Override
   public void addGeoField(ChartRef field) {
      geoRefs.add((VSChartGeoRef) field);
      ((VSDimensionRef) field).setNamedGroupInfo(null); // not supported for geo
   }

   /**
    * Add a field to be used as geo field.
    * @param field the specified field to be added to geo field.
    */
   public void addRTGeoField(ChartRef field) {
      VSChartRef[] nrefs = new VSChartRef[rGeoRefs.length + 1];
      System.arraycopy(rGeoRefs, 0, nrefs, 0, rGeoRefs.length);
      nrefs[nrefs.length - 1] = (VSChartRef) field;
      rGeoRefs = nrefs;
   }

   /**
    * Add a field to be used as geo field.
    * @param idx the index of the geo field.
    * @param field the specified field to be added to geo field.
    */
   @Override
   public void addGeoField(int idx, ChartRef field) {
      if(idx < 0 || idx > geoRefs.size() - 1) {
         geoRefs.add((VSChartGeoRef) field);
      }
      else {
         geoRefs.add(idx, (VSChartGeoRef) field);
      }
   }

   /**
    * Get all the geo field.
    * @return all the geo fields.
    */
   @Override
   public ChartRef[] getGeoFields() {
      return geoRefs.toArray(new VSChartRef[geoRefs.size()]);
   }

   /**
    * Get the runtime geo fields.
    * @return the runtime geo fields.
    */
   @Override
   public ChartRef[] getRTGeoFields() {
      return rGeoRefs;
   }

   /**
    * Get the geo fields count.
    * @return the geo fields count.
    */
   @Override
   public int getGeoFieldCount() {
      return geoRefs.size();
   }

   /**
    * Remove the geo field at the specified position.
    */
   @Override
   public void removeGeoField(int index) {
      geoRefs.remove(index);
   }

   /**
    * Remove all the geo fields.
    */
   @Override
   public void removeGeoFields() {
      geoRefs.clear();
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(geoRefs.size() > 0) {
         writer.println("<geoRefs>");

         for(int i = 0; i < geoRefs.size(); i++) {
            VSChartRef ref = geoRefs.get(i);
            ref.writeXML(writer);
         }

         writer.println("</geoRefs>");
      }

      if(rGeoRefs != null && rGeoRefs.length > 0) {
         writer.println("<rGeoRefs>");

         for(int i = 0; i < rGeoRefs.length; i++) {
            VSChartRef ref = rGeoRefs[i];
            ref.writeXML(writer);
         }

         writer.println("</rGeoRefs>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element node = Tool.getChildNodeByTagName(elem, "geoRefs");
      geoRefs = new ArrayList();

      if(node != null) {
         NodeList xnodes = Tool.getChildNodesByTagName(node, "dataRef");

         for(int i = 0; i < xnodes.getLength(); i++) {
            Element xnode = (Element) xnodes.item(i);
            VSChartRef ref = (VSChartRef) AbstractDataRef.createDataRef(xnode);
            geoRefs.add((VSChartGeoRef) ref);
         }
      }
   }

   /**
    * Remove the chart binding fields.
    */
   @Override
   public void removeFields() {
      super.removeFields();

      geoRefs.clear();
   }

   /**
    * Clone this object.
    * @return the cloned object.
    */
   @Override
   public VSChartInfo clone() {
       try {
         VSMapInfo obj = (VSMapInfo) super.clone();

         obj.geoRefs = Tool.deepCloneCollection(geoRefs);
         obj.rGeoRefs = deepCloneArray(rGeoRefs);

         return obj;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone VSmapInfo", ex);
         return null;
      }
   }

   /**
    * Check if equals another object in content.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals the object in content, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equalsContent(obj)) {
         return false;
      }

      if(!(obj instanceof VSMapInfo)) {
         return false;
      }

      VSMapInfo mapInfo = (VSMapInfo) obj;
      ArrayList geoRefs2 = mapInfo.geoRefs;

      if(geoRefs.size() != geoRefs2.size()) {
         return false;
      }

      for(int i = 0; i < geoRefs.size(); i++) {
         if(!((VSChartRef) getGeoFieldByName(i)).equalsContent(geoRefs2.get(i))) {
            return false;
         }
      }

      VSChartRef[] rGeoRefs2 = mapInfo.rGeoRefs;

      if(rGeoRefs.length != rGeoRefs2.length) {
         return false;
      }

      for(int i = 0; i < rGeoRefs.length; i++) {
         if(!Tool.equalsContent(rGeoRefs[i], rGeoRefs2[i])) {
            return false;
         }
      }

      return true;
   }

   @Override
   public VSDataRef[] getMergedRTFields() {
      // return geo field after x/y but before aesthetic, so they have higher priority
      // than aesthetic fields. (51739)
      VSChartRef[] srGeoRefs = new VSChartRef[rGeoRefs.length];

      for(int i = 0; i < rGeoRefs.length; i++) {
         srGeoRefs[i] = rGeoRefs[i];
      }

      Arrays.sort(srGeoRefs, new MapHelper.GeoRefComparator());
      return srGeoRefs;
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

      for(int i = 0; i < geoRefs.size(); i++) {
         VSChartRef ref = geoRefs.get(i);
         ref.renameDepended(oname, nname, vs);
      }
   }

   /**
    * Get the dynamic property values.
    * @return the dynamic values.
    */
   @Override
   public List getDynamicValues() {
      List list = super.getDynamicValues();

      for(int i = 0; i < geoRefs.size(); i++) {
         VSChartRef cref = geoRefs.get(i);
         list.addAll(cref.getDynamicValues());
      }

      return list;
   }

   /**
    * Check if the specifed data ref is geographic.
    */
   @Override
   public boolean isGeoRef(String name) {
      if(name == null) {
         return false;
      }

      for(int i = 0; i < rGeoRefs.length; i++) {
         if(name.equals(rGeoRefs[i].getName())) {
            return true;
         }
      }

      return false;
   }

   /**
    * Apply hyperlink to runtime VSChartAggregateRefs.
    */
   protected void applyHyperlink(VSDataRef[] refs, Hyperlink link) {
      super.applyHyperlink(refs, link);

      for(int i = 0; i < refs.length; i++) {
         if(!(refs[i] instanceof VSChartGeoRef)) {
            continue;
         }

         VSChartGeoRef geo = (VSChartGeoRef) refs[i];
         geo.setHyperlink(link);
      }
   }

   /**
    * Deep clone array.
    */
   private static VSChartRef[] deepCloneArray(VSChartRef[] refs) {
      VSChartRef[] arr = new VSChartRef[refs.length];

      for(int i = 0; i < arr.length; i++) {
         arr[i] = (VSChartRef) refs[i].clone();
      }

      return arr;
   }

   /**
    * Check if breakdown-by fields are supported.
    */
   @Override
   public boolean supportsGroupFields() {
      return true;
   }

   /**
    * Check if the size frame is per measure.
    */
   @Override
   public boolean supportsSizeFieldFrame() {
      return false;
   }

   @Override
   public String getMapType() {
      ColumnSelection cols = getGeoColumns();

      for(ChartRef ref : getGeoFields()) {
         VSChartGeoRef gref = (VSChartGeoRef) ref;
         String name = gref.getName();
         VSChartGeoRef geo = (VSChartGeoRef) cols.getAttribute(name);

         if(geo != null) {
            return geo.getGeographicOption().getMapping().getType();
         }
      }

      String type = super.getMapType();
      return StringUtils.hasText(type) ? type : "World";
   }

   @Override
   protected boolean isAggregateEnabled(ChartAggregateRef aref) {
      if(aref == null) {
         return false;
      }

      boolean isGeo = getGeoColumns() != null &&
         getGeoColumns().getAttribute(aref.getFullName()) != null;

      return isGeo || super.isAggregateEnabled(aref);
   }

   @Override
   public boolean isInvertedGraph() {
      return false;
   }

   @Override
   public boolean isFacet() {
      // facet status may not be set before isFacet -> isWebMap is called. (59034)
      return super.isFacet() || hasXYDimension();
   }

   @Override
   public boolean supportUpdateChartType() {
      return rGeoRefs != null && rGeoRefs.length > 0;
   }

   @Override
   public ChartRef getFieldByName(String name, boolean rt, boolean ignoreDataGroup) {
      ChartRef ref = super.getFieldByName(name, rt, ignoreDataGroup);
      return ref != null ? ref : getGeoFieldByName(name, rt, ignoreDataGroup);
   }
   @Override
   public ChartRef[] getBindingRefs(boolean runtime) {
      ArrayList<ChartRef> list = new ArrayList<>(Arrays.asList(super.getBindingRefs(runtime)));
      ArrayList ngeoRefs = new ArrayList(0);

      if(!runtime && geoRefs != null) {
         ngeoRefs = (ArrayList) geoRefs.clone();
      }
      else if(runtime && rGeoRefs != null) {
         ngeoRefs = new ArrayList(Arrays.asList(rGeoRefs));
      }

      Collections.sort(ngeoRefs, new MapHelper.GeoRefComparator());
      list.addAll(ngeoRefs);
      return list.toArray(new ChartRef[list.size()]);
   }

   @Override
   public VSDataRef[] getRTFields() {
      List list = new ArrayList(Arrays.asList(super.getRTFields().clone()));
      ChartRef[] geoRefs = getGeoFields();

      for(int i = 0; i < geoRefs.length; i++) {
         if(geoRefs[i] instanceof VSChartGeoRef && getHyperlink() != null) {
            VSChartGeoRef ngeoRef = ((VSChartGeoRef) geoRefs[i]).clone();
            ngeoRef.setHyperlink(getHyperlink());
            list.add(ngeoRef);
         }
         else {
            list.add(geoRefs[i]);
         }
      }

      VSDataRef[] arr = new VSDataRef[list.size()];
      list.toArray(arr);
      return arr;
   }

   private ArrayList<VSChartGeoRef> geoRefs = new ArrayList<>();
   private VSChartRef[] rGeoRefs;
   private static final Logger LOG =
      LoggerFactory.getLogger(VSMapInfo.class);
}
