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
package inetsoft.report;

import inetsoft.report.internal.table.CellHelper;
import inetsoft.report.internal.table.ValidateHelper;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.List;
import java.util.*;

/**
 * BaseLayout, the data structure to manage base table layout, include regions
 * and span.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public abstract class BaseLayout implements Serializable, Cloneable, XMLSerializable {
   /**
    * Remove all empty regions (regions with zero row).
    */
   public synchronized void removeEmptyRegions() {
      for(int i = regions.length - 1; i >= 0; i--) {
         if(regions[i].getRowCount() <= 0) {
            // already empty, won't effect span
            regions = (Region[]) MatrixOperation.removeRow(regions, i);
         }
      }
   }

   /**
    * Get row count, ignore the invisble region's row count.
    */
   public int getVisibleRowCount() {
      int r = 0;

      for(int i = 0; i < regions.length; i++) {
         if(regions[i].isVisible()) {
            r += regions[i].getRowCount();
         }
      }

      return r;
   }

   /**
    * Get row count.
    */
   public int getRowCount() {
      return getRowCount(true);
   }

   /**
    * Get row count.
    * @param all include hidden regions or not.
    */
   public int getRowCount(boolean all) {
      int r = 0;

      for(int i = 0; i < regions.length; i++) {
         if(all || regions[i].isVisible()) {
            r += regions[i].getRowCount();
         }
      }

      return r;
   }

   /**
    * Get the number of columns in the table layout.
    */
   public int getColCount() {
      return ncol;
   }

   /**
    * Set the number of columns in the table layout.
    */
   public synchronized void setColCount(int ncol) {
      this.ncol = ncol;

      for(int i = 0; i < regions.length; i++) {
         regions[i].syncSize();
      }

      spans = MatrixOperation.toSize(spans, getRowCount(), ncol);
   }

   /**
    * Insert a column before the specified column.
    * @param col column index in the layout.
    */
   public synchronized void insertColumn(int col) {
      ncol++;

      for(int i = 0; i < regions.length; i++) {
         regions[i].insertColumn0(col);
      }

      lineChanged(col, true, false);
   }

   /**
    * Remove a column.
    * @param col column index in the layout.
    */
   public synchronized void removeColumn(int col) {
      ncol--;

      for(int i = 0; i < regions.length; i++) {
         regions[i].removeColumn0(col);
      }

      lineChanged(col, false, false);
   }

   /**
    * Insert a row above the specified row.
    * @param row row index in the layout.
    */
   public void insertRow(int row) {
      Region region = locateRegion(row);
      int subRow = convertToRegionRow(row);
      insertRow(region, subRow);
   }

   /**
    * Insert a row to the specified region.
    */
   public void insertRow(Region region, int subRow) {
      // invalid
      if(region == null || subRow < 0 || subRow > region.getRowCount()) {
         return;
      }

      region.insertRow0(subRow);
      int grow = convertToGlobalRow(region, subRow);
      lineChanged(grow, true, true);
   }

   /**
    * Remove a row.
    * @param row row index in the layout.
    */
   public synchronized void removeRow(int row) {
      removeRow(row, true);
   }

   private synchronized void removeRow(int row, boolean force) {
      Region region = locateRegion(row);
      int subRow = convertToRegionRow(row);
      removeRow(region, row, force);
   }

   /**
    * Remove a row from a region.
    */
   public synchronized void removeRow(Region region, int subRow) {
      removeRow(region, subRow, true);
   }

   private void removeRow(Region region, int subRow, boolean force) {
      if(region == null || subRow < 0 || subRow >= region.getRowCount()) {
         return;
      }

      // region must have one row
      if(!force && region.getRowCount() <= 1) {
         region.setVisible(false);
         return;
      }

      int grow = convertToGlobalRow(region, subRow);
      region.removeRow0(subRow, force);
      lineChanged(grow, false, true);
      removeEmptyRegions();
   }

   /**
    * Get all the data path that have region defined.
    */
   public List<TableDataPath> getRegionDataPaths() {
      List<TableDataPath> paths = new ArrayList<>();

      for(int i = 0; i < regions.length; i++) {
         paths.add(regions[i].getPath());
      }

      return paths;
   }

   /**
    * Get the specified region's path.
    */
   public TableDataPath getRegionPath(Region region) {
      return region.getPath();
   }

   /**
    * Get all regions defined in this layout.
    */
   public Region[] getRegions() {
      return regions;
   }

   /**
    * Add a region to the layout, the region will be added to last position.
    * @param path a row data path.
    * @param region a cell region to display the row.
    */
   public synchronized void addRegion(TableDataPath path, Region region) {
      path = fixPathForRegion(path);
      region.setPath(path);
      regions = (Region[]) MatrixOperation.insertRow(regions, regions.length);
      regions[regions.length - 1] = region;
      spans = MatrixOperation.toSize(spans, getRowCount(), getColCount());
   }

   /**
    * Remove region.
    */
   public synchronized void removeRegion(Region region) {
      if(region == null) {
         return;
      }

      for(int i = region.getRowCount() - 1; i >= 0; i--) {
         removeRow(region, i, true);
      }
   }

   /**
    * Get region count.
    */
   public int getRegionCount() {
      return regions.length;
   }

   /**
    * Get the region in the specified index.
    */
   public Region getRegion(int index) {
      return index < regions.length ? regions[index] : null;
   }

   /**
    * Get the region defined for the data path.
    * @param path row data path.
    */
   public Region getRegion(TableDataPath path) {
      int idx = findRegionIndex(path);
      return idx >= 0 ? regions[idx] : null;
   }

   /**
    * Locate row by table data path.
    */
   public int locateRow(TableDataPath path) {
      Region region = locateRegion(path);

      if(region == null) {
         return -1;
      }

      int r = 0;

      for(int i = 0; i < regions.length; i++) {
         if(region == regions[i]) {
            break;
         }

         r += regions[i].getRowCount();
      }

      return r + path.getIndex();
   }

   /**
    * Locate region by table data path.
    */
   public Region locateRegion(TableDataPath path) {
      if(path != null && path.isRow()) {
         path = fixPathForRegion(path);

         for(int i = 0; i < regions.length; i++) {
            if(path.equals(regions[i].getPath())) {
               return regions[i];
            }
         }
      }

      return null;
   }

   /**
    * Locate a region by the global row index.
    * @param row the global row index in table layout.
    */
   public Region locateRegion(int row) {
      int idx = locateRegionIndex(row);
      return idx < 0 ? null : regions[idx];
   }

   /**
    * Locate region index by the global row index.
    */
   public int locateRegionIndex(int row) {
      int r = 0;

      for(int i = 0; i < regions.length; i++) {
         r = r + regions[i].getRowCount();

         if(row < r) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Convert a row in global row index in table layout to a special region's
    * sub row index.
    * @param row the global row index in table layout.
    */
   public int convertToRegionRow(int row) {
      return convertToRegionRow(row, true);
   }

   public int convertToRegionRow(int row, boolean all) {
      int r = 0;

      for(int i = 0; i < regions.length; i++) {
         if(all || regions[i].isVisible()) {
            if(r + regions[i].getRowCount() > row) {
               break;
            }

            r += regions[i].getRowCount();
         }
      }

      return row - r;
   }

   /**
    * Convert a row in region to layout row.
    */
   public int convertToGlobalRow(Region region, int subRow) {
      int r = 0;

      for(int i = 0; i < regions.length; i++) {
         if(region == regions[i]) {
            break;
         }

         r += regions[i].getRowCount();
      }

      return r + subRow;
   }

   /**
    * Get row height.
    * @param r the global row index in the table layout.
    */
   public int getRowHeight(int r) {
      Region region = locateRegion(r);
      int subRow = convertToRegionRow(r);
      return region == null ? -1 : region.getRowHeight(subRow);
   }

   /**
    * Set row height.
    */
   public void setRowHeight(int r, int h) {
      Region region = locateRegion(r);
      int subRow = convertToRegionRow(r);

      if(region != null) {
         region.setRowHeight(subRow, h);
      }
   }

   /**
    * Set cell binding.
    */
   public void setCellBinding(int r, int c, CellBinding binding) {
      Region region = locateRegion(r);
      int subRow = convertToRegionRow(r);

      if(region == null || subRow < 0) {
         return;
      }

      region.setCellBinding(subRow, c, binding);
   }

   /**
    * Get cell binding.
    */
   public CellBinding getCellBinding(int r, int c) {
      Region region = locateRegion(r);
      int subRow = convertToRegionRow(r);

      if(region == null || subRow < 0) {
         return null;
      }

      return region.getCellBinding(subRow, c);
   }

   /**
    * Get the span setting for the specified row and column.
    * @param r the global row index in table layout.
    * @param c the global column index in table layout.
    */
   public Dimension getSpan(int r, int c) {
      return spans[r][c];
   }

   /**
    * Get the span setting for the specified row and column.
    * @param r the global row index in table layout.
    * @param c the global column index in table layout.
    */
   public boolean hasCrossRegionSpan(int r, int c) {
      Region region = locateRegion(r);
      return spans[r][c] != null && region.getRowCount() < spans[r][c].height;
   }

   /**
    * Set the cell span setting for the cell.
    * @param r global row index.
    * @param c global column index.
    * @param span span.width is the number of columns, and span.height is
    * the number of rows.
    */
   public void setSpan(int r, int c, Dimension span) {
      if(span != null) {
         new CellHelper(this, new Rectangle(new Point(c, r), span)).process();
      }

      MatrixOperation.setSpan(spans, getColCount(), getRowCount(), r, c, span);
   }

   /**
    * Find the span cells that covers this cell.
    * @param row the global row index in the table layout.
    * @param col the global column index in the table layout.
    * @return if a span cell covers this cell, return the following:<p>
    * x - negative distance to the left of the span cell.<br>
    * y - negative distance to the top of the span cell.<br>
    * width - number of columns to the right of the span cell, including
    * the current column.<br>
    * height - number of rows to the bottom of the span cell, including
    * the current row.
    */
   public Rectangle findSpan(int row, int col) {
      Point p = new Point(col, row);

      for(int i = 0; i < spans.length; i++) {
         for(int j = 0; j < spans[i].length; j++) {
            if(spans[i][j] == null) {
               continue;
            }

            Dimension span = spans[i][j];
            Rectangle rect = new Rectangle(j, i, span.width, span.height);

            if(rect.contains(p)) {
               return new Rectangle(j - col, i - row, rect.width - col + j,
                                    rect.height - row + i);
            }
         }
      }

      return null;
   }

   /**
    * Clear all settings in the layout.
    */
   public synchronized void clear() {
      regions = new Region[0];
      spans = new Dimension[0][0];
   }

   public void clearFormulaBinding(Set<String> calcFieldsRefs) {
      Arrays.stream(this.regions).forEach(r -> r.clearFormulaBinding(calcFieldsRefs));
   }

   /**
    * Make a copy of the layout.
    */
   @Override
   public synchronized Object clone() {
      try {
         BaseLayout layout = (BaseLayout) super.clone();
         layout.regions = (Region[]) MatrixOperation.clone(regions, layout);
         layout.spans = (Dimension[][]) MatrixOperation.clone(spans);
         return layout;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone layout", ex);
      }

      return null;
   }

   public boolean equals(Object obj, boolean all) {
      return equals(obj, all, false);
   }

   /**
    * Check if equals another object.
    * @return ture if equals, false otherwise
    */
   public boolean equals(Object obj) {
      return equals(obj, true);
   }

   /**
    * Check if equals another object.
    * @return ture if equals, false otherwise
    */
   public boolean equals(Object obj, boolean all, boolean ignoreRowHeight) {
      if(!(obj instanceof BaseLayout)) {
         return false;
      }

      BaseLayout layout = (BaseLayout) obj;
      boolean result = ncol == layout.ncol &&
         regions.length == layout.regions.length &&
         Tool.equals(spans, layout.spans);

      if(result) {
         for(int i = 0; i < regions.length; i++) {
            if(regions[i] == null) {
               result = layout.regions[i] == null;
            }
            else {
               result = all ? regions[i].equals(layout.regions[i]) :
                  (ignoreRowHeight ?
                  regions[i].equalsBinding(layout.regions[i]) :
                  regions[i].equalsContent(layout.regions[i]));
            }

            if(!result) {
               return false;
            }
         }
      }

      return result;
   }

   /**
    * To string, print its data structure.
    */
   public String toString() {
      StringBuilder buf = new StringBuilder(super.toString() + ": row count = " +
         getRowCount() + " column count = " + getColCount() + "\n");

      for(int i = 0; i < regions.length; i++) {
         buf.append(regions[i] + "\n");
      }

      for(int i = 0; i < spans.length; i++) {
         for(int j = 0; j < spans[0].length; j++) {
            if(spans[i][j] != null) {
               buf.append("span " + i + ", " + j + " is: " +
                  spans[i][j] + "\n");
            }
         }
      }

      return buf.toString();
   }

   /**
    * Write regions.
    */
   protected void writeRegions(PrintWriter writer) {
      for(int i = 0; i < regions.length; i++) {
         Region region = regions[i];
         TableDataPath path = region.getPath();

         writer.println("<layoutRegion>");
         path.writeXML(writer);
         region.writeXML(writer);
         writer.println("</layoutRegion>");
      }
   }

   /**
    * Parse regions.
    */
   protected void parseRegions(Element tag) throws Exception {
      NodeList nlist = Tool.getChildNodesByTagName(tag, "layoutRegion");

      for(int i = 0; nlist != null && i < nlist.getLength(); i++) {
         if(nlist.item(i) instanceof Element) {
            Element tag2 = (Element) nlist.item(i);
            Element pathE = Tool.getChildNodeByTagName(tag2, "tableDataPath");
            Element regionE = Tool.getChildNodeByTagName(tag2, "region");
            TableDataPath path = new TableDataPath();
            Region region = new Region();
            path.parseXML(pathE);
            region.parseXML(regionE);

            if(findRegionIndex(path) == -1) {
               addRegion(path, region);
            }
         }
      }
   }

   /**
    * Write span.
    */
   protected void writeSpan(PrintWriter writer) {
      writer.println("<spans>");

      for(int i = 0; i < spans.length; i++) {
         for(int j = 0; j < spans[i].length; j++) {
            if(spans[i][j] == null) {
               continue;
            }

            writer.println("<span r=\"" + i + "\" c=\"" + j + "\" w=\"" +
               spans[i][j].width + "\" h=\"" + spans[i][j].height + "\"/>");
         }
      }

      writer.println("</spans>");
   }

   /**
    * Parse span.
    */
   protected void parseSpan(Element tag) {
      Element root = Tool.getChildNodeByTagName(tag, "spans");

      if(root != null) {
         NodeList nodes = Tool.getChildNodesByTagName(root, "span");

         for(int i = 0; i < nodes.getLength(); i++) {
            if(nodes.item(i) instanceof Element) {
               Element s = (Element) nodes.item(i);

               try {
                  int r = Integer.parseInt(Tool.getAttribute(s, "r"));
                  int c = Integer.parseInt(Tool.getAttribute(s, "c"));
                  int w = Integer.parseInt(Tool.getAttribute(s, "w"));
                  int h = Integer.parseInt(Tool.getAttribute(s, "h"));
                  spans[r][c] = new Dimension(w, h);
               }
               catch(Exception ex) {
                  LOG.warn("Failed to parse table span", ex);
               }
            }
         }
      }
   }

   /**
    * Sync a region size.
    */
   private void syncSize(Region region) {
      region.syncSize();
      spans = MatrixOperation.toSize(spans, getRowCount(), ncol);
   }

   /**
    * Find region index by table data path.
    */
   private int findRegionIndex(TableDataPath path) {
      path = fixPathForRegion(path);

      for(int i = regions.length - 1; i >= 0; i--) {
         if(path.equals(regions[i].getPath())) {
            return i;
         }
      }

      return -1;
   }

   /**
    * Fix the table data path for region data path.
    */
   public abstract TableDataPath fixPathForRegion(TableDataPath path);

   /**
    * Create cell binding.
    */
   protected CellBinding createCellBinding() {
      return new CellBinding();
   }

   /**
    * Insert or remove row/column to modified layout other structure.
    */
   private void lineChanged(int idx, boolean add, boolean isrow) {
      if(add) {
         if(isrow) {
            MatrixOperation.adjustSpan(spans, idx, -1, 1, 0);
            spans = (Dimension[][]) MatrixOperation.insertRow(spans, idx);
         }
         else {
            MatrixOperation.adjustSpan(spans, -1, idx, 0, 1);
            MatrixOperation.insertColumn(spans, idx);
         }
      }
      else {
         int rowCount = getRowCount();
         int colCount = getColCount();

         if(isrow) {
            MatrixOperation.adjustSpan(spans, idx, -1, -1, 0);

            if(idx >= rowCount - 1) {
               spans = (Dimension[][]) MatrixOperation.removeRow(spans, idx);
               return;
            }

            Dimension[] ospan = new Dimension[colCount];

            for(int i = 0; i < colCount; i++) {
               ospan[i] = getSpan(idx, i);

               if(ospan[i] != null && ospan[i].height <= 1) {
                  ospan[i] = null;
               }
            }

            spans = (Dimension[][]) MatrixOperation.removeRow(spans, idx);

            for(int i = 0; i < colCount; i++) {
               if(ospan[i] != null) {
                  setSpan(idx, i,
                     new Dimension(ospan[i].width, ospan[i].height - 1));
               }
            }
         }
         else {
            MatrixOperation.adjustSpan(spans, -1, idx, 0, -1);

            if(idx >= colCount - 1) {
               MatrixOperation.removeColumn(spans, idx);
               return;
            }

            // remove the span top-left line shoundn't remove the span
            Dimension[] ospan = new Dimension[rowCount];

            for(int i = 0; i < rowCount; i++) {
               ospan[i] = getSpan(i, idx);

               if(ospan[i] != null && ospan[i].width <= 1) {
                  ospan[i] = null;
               }
            }

            MatrixOperation.removeColumn(spans, idx);

            for(int i = 0; i < rowCount; i++) {
               if(ospan[i] != null) {
                  setSpan(i, idx,
                     new Dimension(ospan[i].width - 1, ospan[i].height));
               }
            }
         }
      }

      if(spans.length > 0 && spans[0].length > 0) {
         new ValidateHelper(this).process();
      }

   }

   /**
    * Defines a region of rows. Each region is used to display one row in the
    * data table lens.
    *
    * @version 10.3
    * @author InetSoft Technology Corp
    */
   public class Region implements Serializable, Cloneable, XMLSerializable {
      /**
       * Check if the cell region is virtual.
       */
      public boolean isVirtual() {
         return virtual;
      }

      /**
       * Set this region is visible.
       * @hidden
       */
      public void setVisible(boolean visible) {
         this.visible = visible;
      }

      /**
       * Check this region is visible.
       * @hidden
       */
      public boolean isVisible() {
         return visible;
      }

      /**
       * Get the region path.
       * @hidden
       */
      public TableDataPath getPath() {
         return path;
      }

      /**
       * Set the region path.
       * @hidden
       */
      public void setPath(TableDataPath path) {
         this.path = path;
      }

      /*
       * @hidden
       */
      public TableDataPath getCellDataPath(int row, int col) {
         TableDataPath path = getPath();
         String[] rpath = path.getPath();
         String[] paths = null;

         if(rpath.length == 0) {
            paths = new String[] {row + ", " + col};
         }
         else {
            // return all path seems more readable for end user
            paths = new String[rpath.length + 1];
            System.arraycopy(rpath, 0, paths, 0, rpath.length);
            paths[rpath.length] = row + ", " + col;
         }

         return new TableDataPath(path.getLevel(), path.getType(),
            XSchema.STRING, paths);
      }

      /*
       * @hidden
       */
      public TableDataPath getRowDataPath(int row) {
         TableDataPath path = getPath();
         String[] rpath = path.getPath();

         if(rpath.length == 0) {
            return new TableDataPath(path.getLevel(), path.getType(), row);
         }

         return new TableDataPath(path.getLevel(), path.getType(),
            path.getDataType(), rpath, true, false);
      }

      /**
       * Get the row height.
       * @param r row index.
       * @return -1 if the height is not set, or row heights in points.
       */
      public int getRowHeight(int r) {
         return rowHeights[r] < 0 ? -1 : rowHeights[r];
      }

      /**
       * Set the row height.
       * @param r row index.
       * @param height -1 if the height is not set, or row heights in points.
       */
      public void setRowHeight(int r, int height) {
         rowHeights[r] = height;
      }

      /**
       * Get the row in the table to map this region row to.
       */
      public int getRowBinding(int r) {
         return rowBinding[r];
      }

      /**
       * Set the row in the base table to map this row to. This is used in
       * crosstab header to allow a row to map to a header without individual
       * cell binding.
       */
      public void setRowBinding(int r, int map) {
         rowBinding[r] = map;
      }

      /**
       * Get the cell binding for the cell.
       * @param r row index within the region.
       * @param c column index within the region.
       */
      public CellBinding getCellBinding(int r, int c) {
         return c >= bindings[r].length ? null : bindings[r][c];
      }

      /**
       * Set the cell binding for the cell.
       * @param r row index within the region.
       * @param c column index within the region.
       * @param binding cell binding.
       */
      public void setCellBinding(int r, int c, CellBinding binding) {
         synchronized(BaseLayout.this) {
            bindings[r][c] = binding;
         }
      }

      /**
       * Insert a column before the specified column.
       * @param col column index.
       */
      public void insertColumn(int col) {
         BaseLayout.this.insertColumn(col);
      }

      private void insertColumn0(int col) {
         MatrixOperation.insertColumn(bindings, col);
      }

      /**
       * Remove a column.
       * @param col column index.
       */
      public void removeColumn(int col) {
         BaseLayout.this.removeColumn(col);
      }

      private void removeColumn0(int col) {
         MatrixOperation.removeColumn(bindings, col);
      }

      /**
       * Get number of rows in the region.
       */
      public int getRowCount() {
         return nrow;
      }

      /**
       * Set the number of rows in the region.
       */
      public void setRowCount(int nrow) {
         synchronized(BaseLayout.this) {
            if(this.nrow == nrow) {
               return;
            }

            if(this.nrow > nrow) {
               for(int i = this.nrow - 1; i >= nrow; i--) {
                  BaseLayout.this.removeRow(this, i);
               }
            }
            else {
               for(int i = this.nrow; i < nrow; i++) {
                  BaseLayout.this.insertRow(this, i);
               }
            }

            BaseLayout.this.syncSize(this);
         }
      }

      /**
       * Insert a row above the specified row.
       * @param row row index.
       */
      public void insertRow(int row) {
         BaseLayout.this.insertRow(this, row);
      }

      private void insertRow0(int row) {
         nrow++;
         rowHeights = MatrixOperation.insertRow(rowHeights, row);
         rowBinding = MatrixOperation.insertRow(rowBinding, row);
         bindings = (CellBinding[][]) MatrixOperation.insertRow(bindings, row);
      }

      /**
       * Remove a row.
       * @param row row index.
       */
      public void removeRow(int row) {
         BaseLayout.this.removeRow(this, row);
      }

      private void removeRow0(int row, boolean force) {
         if(!force && nrow <= 1) {
            setVisible(false);
            return;
         }

         nrow--;
         rowHeights = MatrixOperation.removeRow(rowHeights, row);
         rowBinding = MatrixOperation.removeRow(rowBinding, row);
         bindings = (CellBinding[][]) MatrixOperation.removeRow(bindings, row);
      }

      public boolean hasCrossRegionSpan(int r, int c) {
         return BaseLayout.this.hasCrossRegionSpan(convertToGlobalRow(this, r), c);
      }

      /**
       * Get the cell span setting for the cell.
       * @param r row index within the region.
       * @param c column index within the region.
       */
      public Dimension getSpan(int r, int c) {
         return BaseLayout.this.getSpan(convertToGlobalRow(this, r), c);
      }

      /**
       * Set the cell span setting for the cell.
       * @param r row index within the region.
       * @param c column index within the region.
       * @param span span.width is the number of columns, and span.height is
       * the number of rows.
       */
      public void setSpan(int r, int c, Dimension span) {
         BaseLayout.this.setSpan(convertToGlobalRow(this, r), c, span);
      }

      /**
       * Find the span cells that covers this cell.
       * @return if a span cell covers this cell, return the following:<p>
       * x - negative distance to the left of the span cell.<br>
       * y - negative distance to the top of the span cell.<br>
       * width - number of columns to the right of the span cell, including
       * the current column.<br>
       * height - number of rows to the bottom of the span cell, including
       * the current row.
       */
      public Rectangle findSpan(int r, int c) {
         return BaseLayout.this.findSpan(convertToGlobalRow(this, r), c);
      }

      /**
       * Make sure the arrays are the same size as row and column count.
       */
      private void syncSize() {
         synchronized(BaseLayout.this) {
            rowHeights = MatrixOperation.toSize(rowHeights, nrow);
            rowBinding = MatrixOperation.toSize(rowBinding, nrow);
            bindings = MatrixOperation.toSize(bindings, nrow, getColCount());
         }
      }

      /**
       * Clear all cell bindings.
       */
      public void clearBinding() {
         for(int i = 0; i < nrow; i++) {
            for(int j = 0; j < getColCount(); j++) {
              bindings[i][j] = null;
            }
         }
      }

      public void clearFormulaBinding(Set<String> calcFieldsRefs) {
         for(int i = 0; i < nrow; i++) {
            for(int j = 0; j < getColCount(); j++) {
               if(bindings[i][j] != null && bindings[i][j].getType() == CellBinding.BIND_COLUMN &&
                  calcFieldsRefs.contains(bindings[i][j].getValue()))
               {
                  bindings[i][j] = null;
               }
            }
         }
      }

      /**
       * Write to XML format.
       */
      @Override
      public void writeXML(PrintWriter writer) {
         writer.print("<region ");
         writeAttributes(writer);
         writer.println(">");
         writeContent(writer);
         writer.println("</region>");
      }

      /**
       * Write attributes.
       */
      protected void writeAttributes(PrintWriter writer) {
          writer.print(" rows=\"" + nrow + "\" visible=\"" + visible + "\"" +
            " virtual=\"" + virtual + "\"");
      }

      /**
       * Write content.
       */
      protected void writeContent(PrintWriter writer) {
         for(int i = 0; i < nrow; i++) {
            writer.println("<rowHeight row=\"" + i + "\" height=\"" +
                           rowHeights[i] + "\"/>");

            writer.println("<rowBinding row=\"" + i + "\" binding=\"" +
                           rowBinding[i] + "\"/>");

            for(int j = 0; j < getColCount(); j++) {
               if(bindings[i][j] == null) {
                  continue;
               }

               writer.println("<cell row=\"" + i + "\" column=\"" + j + "\">");
               bindings[i][j].writeXML(writer);
               writer.println("</cell>");
            }
         }
      }

      /**
       * Parse xml data into object.
       */
      @Override
      public void parseXML(Element tag) throws Exception {
         parseAttributes(tag);
         parseContent(tag);
      }

      /**
       * Parse attributes.
       */
      protected void parseAttributes(Element tag) throws Exception {
         String val = Tool.getAttribute(tag, "rows");

         if(val != null) {
            try {
               setRowCount(Integer.parseInt(val));
            }
            catch(Exception ex) {
               LOG.warn("Invalid row count value: " + val, ex);
            }
         }

         val = Tool.getAttribute(tag, "visible");
         visible = !"false".equalsIgnoreCase(val);
         val = Tool.getAttribute(tag, "virtual");
         virtual = "true".equalsIgnoreCase(val);
      }

      /**
       * Parse content.
       */
      protected void parseContent(Element tag) throws Exception {
         NodeList nlist = Tool.getChildNodesByTagName(tag, "cell");

         for(int i = 0; nlist != null && i < nlist.getLength(); i++) {
            Element tag2 = (Element) nlist.item(i);
            int row = Integer.parseInt(Tool.getAttribute(tag2, "row"));
            int col = Integer.parseInt(Tool.getAttribute(tag2, "column"));
            Element binding = Tool.getChildNodeByTagName(tag2, "cellBinding");

            if(binding != null) {
               CellBinding cb = createCellBinding();
               cb.parseXML(binding);

               setCellBinding(row, col, cb);
            }
         }

         nlist = Tool.getChildNodesByTagName(tag, "rowHeight");

         for(int i = 0; nlist != null && i < nlist.getLength(); i++) {
            Element tag2 = (Element) nlist.item(i);
            int row = Integer.parseInt(Tool.getAttribute(tag2, "row"));
            int height = Integer.parseInt(Tool.getAttribute(tag2, "height"));

            rowHeights[row] = height;
         }

         nlist = Tool.getChildNodesByTagName(tag, "rowBinding");

         for(int i = 0; nlist != null && i < nlist.getLength(); i++) {
            Element tag2 = (Element) nlist.item(i);
            int row = Integer.parseInt(Tool.getAttribute(tag2, "row"));
            int binding = Integer.parseInt(Tool.getAttribute(tag2, "binding"));

            rowBinding[i] = binding;
         }
      }

      /**
       * Make a copy of the region.
       */
      @Override
      public Object clone() {
         return clone(BaseLayout.this);
      }

      /**
       * Make a copy of the region.
       */
      public Object clone(BaseLayout layout) {
         try {
            synchronized(BaseLayout.this) {
               Region obj = layout. new Region();
               obj.nrow = nrow;
               obj.visible = visible;
               obj.rowHeights = rowHeights.clone();
               obj.rowBinding = rowBinding.clone();
               obj.bindings = (CellBinding[][]) MatrixOperation.clone(bindings);
               obj.path = path == null ? null : (TableDataPath) path.clone();
               return obj;
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to clone layout region", ex);
         }

         return null;
      }

      /**
       * Check the obj is eqauls with this object or not.
       */
      public boolean equalsContent(Object obj) {
         return equals(obj, false);
      }

      /**
       * Check if equals another object.
       * @return ture if equals, false otherwise
       */
      public boolean equals(Object obj) {
         return equals(obj, true);
      }

      /**
       * Check the obj is only binding eqauls with this object or not.
       */
      public boolean equalsBinding(Object obj) {
         return equals(obj, false, true);
      }

      /**
       * Check if equals another object.
       */
      public boolean equals(Object obj, boolean all) {
         return equals(obj, all, false);
      }

      /**
       * Check if equals another object.
       */
      public boolean equals(Object obj, boolean all, boolean ignoreRowHeight) {
         if(!(obj instanceof Region)) {
            return false;
         }

         if(obj == this) {
            return true;
         }

         Region reg = (Region) obj;
         boolean equalRowHeight = ignoreRowHeight ? true :
            rowHeightEquals(rowHeights, reg.rowHeights);
         boolean result = nrow == reg.nrow && visible == reg.visible &&
            bindings.length == reg.bindings.length &&
            equalRowHeight &&
            Arrays.equals(rowBinding, reg.rowBinding) && path.equals(reg.path);

         if(!result) {
            return false;
         }

         for(int r = 0; r < nrow; r++) {
            for(int c = 0; c < getColCount(); c++) {
               Rectangle span = findSpan(r, c);

               // for any cell in span, but not the represent position,
               // we ignore it compare
               if(all || span == null || span.x == 0 && span.y == 0) {
                  CellBinding cell1 = getCellBinding(r, c);
                  CellBinding cell2 = reg.getCellBinding(r, c);

                  if(!equalsBinding(cell1, cell2, all)) {
                     return false;
                  }
               }
            }
         }

         return true;
      }

      private boolean rowHeightEquals(int[] h1, int[] h2) {
         if(h1.length != h2.length) {
            return false;
         }

         for(int i = 0; i < h1.length; i++) {
            int rh1 = h1[i];
            int rh2 = h2[i];

            if(rh1 == rh2) {
               continue;
            }

            if(rh1 != rh2) {
               return false;
            }

            // <= 0 means not set row height
            if(rh1 <= 0 != rh2 <= 0) {
               return false;
            }
         }

         return true;
      }

      private boolean equalsBinding(CellBinding b1, CellBinding b2, boolean all)
      {
         if(b1 == null && b2 == null) {
            return true;
         }

         CellBinding non_null = b1 == null ? b2 : b1;
         CellBinding other = non_null == b1 ? b2 : b1;
         return all ? non_null.equals(other) : non_null.equalsContent(other);
      }

      /**
       * Get col count.
       */
      public int getColCount() {
         return BaseLayout.this.getColCount();
      }

      /**
       * To string.
       */
      public String toString() {
         StringBuilder buf = new StringBuilder(path + "");

         if(!isVisible()) {
            buf.append("[hidden]");
         }

         buf.append("\n");

         for(int i = 0; i < getRowCount(); i++) {
            if(i > 0) {
               buf.append("\n");
            }

            for(int j = 0; j < getColCount(); j++) {
               if(j > 0) {
                  buf.append(",");
               }

               CellBinding binding = getCellBinding(i, j);
               String val = binding == null ? null : binding.toString();
               val = val == null ? "" : val;
               buf.append(val);
            }
         }

         return buf.toString();
      }

      /*
       * @hidden
       */
      public void debug() {
         debug(calculateColMax());
      }

      /*
       * @hidden
       */
      public void debug(int[] cmax) {
         String pStr = path.toString() + ": ";
         String empty = appendBlank("", pStr.length());
         StringBuilder buf = new StringBuilder(pStr);

         for(int i = 0; i < getRowCount(); i++) {
            if(i > 0) {
               buf.append("\n");
               buf.append(empty);
            }

            for(int j = 0; j < getColCount(); j++) {
               if(j > 0) {
                  buf.append(",");
               }

               CellBinding binding = getCellBinding(i, j);
               String val = binding == null ? null : binding.toString();
               val = val == null ? "" : val;
               val = appendBlank(val, cmax[j]);
               buf.append(val);
            }
         }

         System.err.println(buf);
      }

      /*
       * @hidden
       */
      int[] calculateColMax() {
         int[] cmax = new int[getColCount()];

         for(int i = 0; i < getRowCount(); i++) {
            for(int j = 0; j < getColCount(); j++) {
               CellBinding binding = getCellBinding(i, j);

               if(binding != null) {
                  String val = binding.toString();
                  cmax[j] = Math.max(val.length(), cmax[j]);
               }
            }
         }

         return cmax;
      }

      /**
       * Append blank after prefix, to make sure the new string length is equals
       * to len.
       * @hidden
       */
      String appendBlank(String prefix, int len) {
         StringBuilder buf = new StringBuilder(prefix);

         for(int i = prefix.length(); i < len; i++) {
            buf.append(" ");
         }

         return buf.toString();
      }

      private boolean visible = true;
      private boolean virtual = false;
      private TableDataPath path;
      private int nrow = 0; // number of rows
      private int[] rowHeights = {}; // row height, not set if <= 0
      private int[] rowBinding = {}; // row height, not set if < 0
      private CellBinding[][] bindings = {}; // cell binding
   }

   /*
    * @hidden
    */
   public void debug() {
      System.err.println("layout: row count = " +
         getRowCount() + " column count = " + getColCount());
      int[] cmax = new int[getColCount()];

      for(int i = 0; i < regions.length; i++) {
         int[] rcmax = regions[i].calculateColMax();

         for(int j = 0; j < Math.min(rcmax.length, cmax.length); j++) {
            cmax[j] = Math.max(rcmax[j], cmax[j]);
         }
      }

      for(int i = 0; i < regions.length; i++) {
         regions[i].debug(cmax);
      }
   }

   protected int ncol = 0;
   protected Region[] regions = new Region[0];
   protected Dimension[][] spans = new Dimension[0][0];

   private static final Logger LOG = LoggerFactory.getLogger(BaseLayout.class);
}
