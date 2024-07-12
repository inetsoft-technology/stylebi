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
package inetsoft.graph.aesthetic;

import com.inetsoft.build.tern.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.Scale;
import inetsoft.util.CoreTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This class defines a line frame for categorical values. Different values
 * are represented by lines with different dash size.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=CategoricalLineFrame")
public class CategoricalLineFrame extends LineFrame implements CategoricalFrame {
   /**
    * Create a line frame for categorical values.
    */
   public CategoricalLineFrame() {
      lines = new GLine[] {GLine.THIN_LINE, GLine.DOT_LINE, GLine.DASH_LINE,
         GLine.MEDIUM_DASH, GLine.LARGE_DASH, GLine.THIN_LINE, GLine.DOT_LINE,
         GLine.DASH_LINE, GLine.MEDIUM_DASH, GLine.LARGE_DASH, GLine.LARGE_DASH,
         GLine.THIN_LINE, GLine.DOT_LINE, GLine.DASH_LINE, GLine.MEDIUM_DASH,
         GLine.LARGE_DASH,GLine.THIN_LINE, GLine.DOT_LINE, GLine.DASH_LINE,
         GLine.MEDIUM_DASH, GLine.LARGE_DASH, GLine.THIN_LINE, GLine.DOT_LINE,
         GLine.DASH_LINE, GLine.MEDIUM_DASH, GLine.LARGE_DASH, GLine.THIN_LINE,
         GLine.DOT_LINE, GLine.DASH_LINE, GLine.MEDIUM_DASH, GLine.LARGE_DASH};
   }

   /**
    * Create a line frame.
    * @param field field to get value to map to line styles.
    */
   @TernConstructor
   public CategoricalLineFrame(String field) {
      this();
      setField(field);
   }

   /**
    * Initialize the categorical line frame with categorical values.
    */
   public void init(Object... vals) {
      CategoricalScale scale = new CategoricalScale();

      scale.init(vals);
      setScale(scale);
   }

   /**
    * Initialize the categorical line frame with categorical values and lines.
    * The value and line arrays must have identical length. Each value
    * is assigned the line style from the line array at the same position.
    */
   public void init(Object[] vals, GLine[] lines) {
      this.lines = lines;
      CategoricalScale scale = new CategoricalScale();
      scale.init(vals);
      setScale(scale);
   }

   /**
    * Initialize the categorical line frame with categorical values from the
    * dimension column of the dataset.
    */
   @Override
   public void init(DataSet data) {
      if(getField() == null) {
         init(getAllHeaders(data), lines);
         return;
      }

      createScale(data);
   }

   /**
    * Get the line for the specified cell.
    * @param data the specified dataset.
    * @param col the name of the specified column.
    * @param row the specified row index.
    */
   @Override
   public GLine getLine(DataSet data, String col, int row) {
      Object val = (getField() != null) ? data.getData(getField(), row) : col;
      return getLine(val);
   }

   /**
    * Get a line for the specified value.
    */
   @Override
   @TernMethod
   public GLine getLine(Object val) {
      if(cmap.size() > 0) {
         GLine line = cmap.get(GTool.toString(val));

         if(line != null) {
            return line;
         }
      }

      Scale scale = getScale();

      if(scale == null) {
         return defaultLine;
      }

      double idx = scale.map(val);

      if(Double.isNaN(idx)) {
         idx = scale.map(GTool.toString(val));
      }

      return Double.isNaN(idx) ? defaultLine : lines[(int) idx % lines.length];
   }

   /**
    * Set the line to be used if the value is not found in the categorical values.
    */
   @TernMethod
   public void setDefaultLine(GLine line) {
      this.defaultLine = line;
   }

   /**
    * Get the line to be used if the value is not found in the categorical values.
    */
   @TernMethod
   public GLine getDefaultLine() {
      return defaultLine;
   }

   /**
    * Set the line for the specified value.
    */
   @TernMethod
   public void setLine(Object val, GLine line) {
      if(line != null) {
         cmap.put(GTool.toString(val), line);
      }
      else {
         cmap.remove(GTool.toString(val));
      }
   }

   /**
    * Check if the value is assigned a static aesthetic value.
    */
   @Override
   @TernMethod
   public boolean isStatic(Object val) {
      return cmap.get(GTool.toString(val)) != null;
   }

   @Override
   @TernMethod
   public Set<Object> getStaticValues() {
      return cmap.keySet();
   }

   @Override
   @TernMethod
   public void clearStatic() {
      cmap.clear();
   }

   /**
    * Get the line at the specified index.
    */
   @TernMethod
   public GLine getLine(int index) {
      return lines[index % lines.length];
   }

   /**
    * Set the line at the specified index.
    */
   @TernMethod
   public void setLine(int index, GLine line) {
      if(lines != null) {
         lines[index % lines.length] = line;
      }
   }

   /**
    * Get the number of lines in the frame.
    */
   @TernMethod
   public int getLineCount() {
      return lines.length;
   }

   @Override
   @TernMethod
   public String getUniqueId() {
      return super.getUniqueId() + new TreeMap(cmap);
   }

   /**
    * Check if equals another object.
    */
   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      GLine[] lines2 = ((CategoricalLineFrame) obj).lines;

      if(lines.length != lines2.length) {
         return false;
      }

      for(int i = 0; i < lines.length; i++) {
         if(!CoreTool.equals(lines[i], lines2[i])) {
            return false;
         }
      }

      return true;
   }

   /**
    * Create a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         CategoricalLineFrame frame = (CategoricalLineFrame) super.clone();
         frame.lines = lines.clone();
         frame.cmap = new HashMap<>(cmap);
         return frame;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone line frame", ex);
         return null;
      }
   }

   private GLine[] lines;
   private Map<Object, GLine> cmap = new HashMap<>();
   private GLine defaultLine = null;

   private static final long serialVersionUID = 1L;
   private static final Logger LOG = LoggerFactory.getLogger(CategoricalLineFrame.class);
}
