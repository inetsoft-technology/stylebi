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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.internal.Common;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.*;
import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.geom.Point2D;
import java.io.*;
import java.util.*;

/**
 * SelectionListVSAssemblyInfo, the assembly info of a selection list assembly.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class SelectionListVSAssemblyInfo extends SelectionBaseVSAssemblyInfo {
   /**
    * Constructor.
    */
   public SelectionListVSAssemblyInfo() {
      super();
   }

   /**
    * Get the data ref.
    * @return the data ref.
    */
   public DataRef getDataRef() {
      return ref;
   }

   /**
    * Set the data ref.
    * @param ref the specified data ref.
    */
   public void setDataRef(DataRef ref) {
      this.ref = ref;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public DataRef[] getDataRefs() {
      final DataRef ref = getDataRef();

      if(ref == null) {
         return new DataRef[0];
      }

      return new DataRef[] {ref};
   }

   /**
    * Get the selection list.
    * @return the selection list.
    */
   public SelectionList getSelectionList() {
      return selectionList;
   }

   /**
    * Set the selection list.
    * @param list the selection list.
    */
   public void setSelectionList(SelectionList list) {
      this.selectionList = list;
   }

   /**
    * Get the number of columns to layout selection list items.
    */
   public int getColumnCount() {
      return ncol;
   }

   /**
    * Set the number of columns to layout selection list items. The number can't
    * be greater than the number of grid columns occupied by the list.
    */
   public void setColumnCount(int ncol) {
      this.ncol = Math.max(1, ncol);
   }

   /**
    * Set if the this assembly is used as adhoc filter.
    */
   @Override
   public void setAdhocFilter(boolean adFilter) {
      adhocFilter = adFilter;
   }

   /**
    * Return whether this assembly is used as adhoc filter.
    */
   @Override
   public boolean isAdhocFilter() {
      return adhocFilter;
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" ncolumn=\"" + ncol + "\"");
      writer.print(" adhocFilter=\"" + adhocFilter + "\"");
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      String text = Tool.getAttribute(elem, "ncolumn");
      setColumnCount((text == null) ? 1 : Integer.parseInt(text));
      adhocFilter = "true".equals(Tool.getAttribute(elem, "adhocFilter"));
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(ref != null) {
         ref.writeXML(writer);
      }

      if(selectionList != null) {
         String search = getSearchString();

         if(search != null && search.length() > 0) {
            selectionList.findAll(search, false).writeXML(writer, 0, 0, MAX_NODES);
         }
         else {
            selectionList.writeXML(writer, 0, 0, MAX_NODES);
         }
      }
   }

   /**
    * Write selectionList as binary.
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) this.clone();
      info.selectionList = null;
      XMLTool.writeXMLSerializableAsData(output, info);

      if(selectionList != null) {
         String search = getSearchString();

         if(search != null && search.length() > 0) {
            selectionList.findAll(search, false).writeData(output, 0, MAX_NODES);
         }
         else {
            selectionList.writeData(output, 0, MAX_NODES);
         }
      }
      else {
         (new SelectionList()).writeData(output, 0, MAX_NODES);
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element cnode = Tool.getChildNodeByTagName(elem, "dataRef");

      if(cnode != null) {
         ref = AbstractDataRef.createDataRef(cnode);
      }

      Element snode = Tool.getChildNodeByTagName(elem, "SelectionList");

      if(snode != null) {
         selectionList = new SelectionList();
         selectionList.parseXML(snode);
      }
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public SelectionListVSAssemblyInfo clone(boolean shallow) {
      try {
         SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) super.clone(shallow);

         if(!shallow) {
            if(ref != null) {
               info.ref = (DataRef) ref.clone();
            }

            if(selectionList != null) {
               info.selectionList = (SelectionList) selectionList.clone();
            }
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone SelectionListVSAssemblyInfo", ex);
      }

      return null;
   }

   /**
    * Copy the view part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      SelectionListVSAssemblyInfo sinfo = (SelectionListVSAssemblyInfo) info;
      boolean result = false;
      DataRef ref = getDataRef();
      String title = getTitle();

      // if data ref changes and user does not change default title value,
      // update title value
      if(Tool.equals(getTitleValue(), sinfo.getTitleValue()) &&
         ref != null && !ref.equals(sinfo.getDataRef()) &&
         sinfo.getDataRef() != null && ref.getName().equals(title))
      {
         setTitleValue(sinfo.getDataRef().getName());
         result = true;
      }

      result = super.copyViewInfo(info, deep) || result;

      if(ncol != sinfo.ncol) {
         this.ncol = sinfo.ncol;
         result = true;
      }

      if(!Tool.equals(selectionList, sinfo.selectionList)) {
         this.selectionList = sinfo.selectionList;
         result = true;
      }

      if(adhocFilter != sinfo.adhocFilter) {
         adhocFilter = sinfo.adhocFilter;
         result = true;
      }

      return result;
   }

   /**
    * Copy the input data part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return new hint.
    */
   @Override
   protected int copyInputDataInfo(VSAssemblyInfo info, int hint) {
      hint = super.copyInputDataInfo(info, hint);
      SelectionListVSAssemblyInfo sinfo = (SelectionListVSAssemblyInfo) info;

      if(!Tool.equals(ref, sinfo.ref)) {
         setDataRef(sinfo.ref);
         hint |= VSAssembly.INPUT_DATA_CHANGED;
         hint |= VSAssembly.BINDING_CHANGED;
      }

      if(!Tool.equals(selectionList, sinfo.selectionList)) {
         this.selectionList = sinfo.selectionList;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      return hint;
   }

   /**
    * Get the height of each row.
    * @return list of row heights.
    */
   public List<Double> getRowHeights() {
      List<Double> rowHeights = new ArrayList<>();
      SelectionList slist = getSelectionList();

      if(slist == null) {
         return rowHeights;
      }

      SelectionValue[] svalues = slist.getAllSelectionValues();
      VSCompositeFormat format = svalues[0].getFormat();
      boolean isListCellWrap = format.isWrapping();
      int nrow = svalues.length % ncol == 0 ? (svalues.length / ncol) : (svalues.length / ncol) + 1;
      double cellWidth = getPixelSize().width / ncol;
      double barSize = 0;

      if(isShowBar() && getMeasure() != null) {
         barSize = getBarSize();

         if(barSize < 0) {
            barSize = Math.ceil(cellWidth / 4);
         }

         cellWidth -= barSize;
      }

      if(isShowText() && getMeasure() != null) {
         double textWidth = getMeasureSize();

         if(textWidth < 0) {
            double measureRatio = getMeasureTextRatio();
            textWidth = Math.ceil((cellWidth - barSize) * measureRatio);
         }

         cellWidth -= barSize;
      }

      if(isListCellWrap) {
         SelectionValue svalue = null;

         for(int i = 0; i < nrow; i++) {
            double rowHeight = 0;

            for(int j = 0; j < ncol && (i * ncol + j) < svalues.length; j++) {
               svalue = svalues[i * ncol + j];
               format = svalue.getFormat();
               double cellHeight2 = Common.getWrapTextHeight(svalue.getLabel(), cellWidth,
                  format.getFont(), format.getAlignment());
               rowHeight = Math.max(cellHeight2, rowHeight);
            }

            rowHeights.add(rowHeight);
         }
      }
      else {
         int valueCount = (int) Arrays.stream(slist.getSelectionValues())
            .filter(value -> !value.isExcluded())
            .count();
         // find the total amount of rows to display for the selection values
         int rowCount = Math.min(
               (int) Math.ceil(valueCount / (double) getColumnCount()),
               Integer.MAX_VALUE);

         for(int i = 0; i < rowCount; i++) {
            rowHeights.add((double)getCellHeight());
         }
      }

      return rowHeights;
   }

   /**
    * Gets selections data for SelectionList, SelectionTree, TimeSlider.
    * @return selections.
    */
   @Override
   public DataSerializable getSelections() {
      return selectionList;
   }

   /**
    * Get the object css default type.
    */
   @Override
   public String getObjCSSType() {
      return CSSConstants.SELECTION_LIST;
   }

   /**
    * override.
    * Get size scale ratio of this assembly.
    */
   @Override
   public Point2D.Double getSizeScale(Point2D.Double scaleRatio) {
      return getShowType() == SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE ?
         new Point2D.Double(scaleRatio.getX(), 1) : scaleRatio;
   }

   private static final int MAX_NODES = 500;

   // view
   private int ncol = 1; // number of columns
   // input data
   private DataRef ref;
   // runtime data
   private SelectionList selectionList;
   private boolean adhocFilter;

   private static final Logger LOG =
      LoggerFactory.getLogger(SelectionListVSAssemblyInfo.class);
}
