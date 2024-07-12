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
package inetsoft.report;

import inetsoft.report.internal.binding.Field;
import inetsoft.uql.VariableTable;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.awt.*;
import java.io.PrintWriter;
import java.util.List;
import java.util.*;

/**
 * TableLayout, the data structure to manage design time table's layout, when
 * execute table lens, table layout engine will convert the design time table
 * layout to runtime table layout which contains normal layout, calc layout
 * and null layout.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class TableLayout extends BaseLayout implements GroupableLayout {
   /**
    * Normal table mode.
    */
   public static final int NORMAL = 1;
   /**
    * Plain table mode.
    */
   public static final int PLAIN = 2;
   /**
    * Crosstab table mode.
    */
   public static final int CROSSTAB = 4;
   /**
    * Calc table mode.
    */
   public static final int CALC = 8;

   /**
    * Regular calc table edit mode.
    */
   public static final int DEFAULT_MODE = 0;
   /**
    * Calc table mode that shows cell names and groups.
    */
   public static final int NAME_MODE = 1;
   /**
    * Calc table mode that shows full formula string.
    */
   public static final int FORMULA_MODE = 2;

   /**
    * @hidden
    */
   public int getBackLayoutType() {
      return backLayoutType;
   }

   /**
    * Get all bands infomations.
    */
   @Override
   public List<GroupableBandInfo> getBandInfos() {
      List<GroupableBandInfo> binfos = new ArrayList<>();

      for(int i = 0; i < regions.length; i++) {
         binfos.add(new RegionInfo(regions[i]));
      }

      return binfos;
   }

   /**
    * Get cell binding infos.
    * @param all false only return cells which on top-left of
    *  span or no span cells.
    */
   @Override
   public List<CellBindingInfo> getCellInfos(boolean all) {
      List<CellBindingInfo> cinfos = new ArrayList<>();
      List<GroupableBandInfo> binfos = getBandInfos();

      for(int i = 0; i < binfos.size(); i++) {
         cinfos.addAll(binfos.get(i).getCellInfos(all));
      }

      return cinfos;
   }

   /**
    * Get a cell binding infomation.
    * @param gr the row in the layout.
    * @param gc the column int the layout.
    */
   public CellBindingInfo getCellInfo(int gr, int gc) {
      RegionIndex ridx = getRegionIndex(gr);

      if(ridx != null) {
         return new TableCellBindingInfo(ridx.getRegion(), ridx.getRow(), gc);
      }

      return null;
   }

   /**
    * Swap two regions.
    */
   public void swapRegion(Region region1, Region region2) {
      // only normal layout support swap region
      if(!isNormal()) {
         LOG.warn(
            "Current table mode " + mode +
            " does not support swapping regions.");
         return;
      }

      if(region1 == null || region2 == null) {
         return;
      }

      int idx1 = -1;
      int idx2 = -1;

      for(int i = 0; i < regions.length; i++) {
         if(regions[i] == region1) {
            idx1 = i;
         }
         else if(regions[i] == region2) {
            idx2 = i;
         }

         if(idx1 >= 0 && idx2 >= 0) {
            break;
         }
      }

      if(idx1 < 0) {
         LOG.warn("Cannot swap regions, first region not found: " + region1);
         return;
      }

      if(idx2 < 0) {
         LOG.warn(
            "Cannot swap regions, second region not found: " + region2);
         return;
      }

      // fix span
      int gr1 = convertToGlobalRow(region1, 0);
      int gr2 = convertToGlobalRow(region2, 0);

      Region lregion = gr1 < gr2 ? region1 : region2;
      int lr = gr1 < gr2 ? gr1 : gr2;
      Region hregion = gr1 < gr2 ? region2 : region1;
      int hr = gr1 < gr2 ? gr2 : gr1;
      ArrayList<Dimension[]> lspan = new ArrayList<>();

      for(int r = lr; r < lr + lregion.getRowCount(); r++) {
         lspan.add(spans[r]);
      }

      ArrayList<Dimension[]> hspan = new ArrayList<>();

      for(int r = hr; r < hr + hregion.getRowCount(); r++) {
         hspan.add(spans[r]);
      }

      ArrayList<Dimension[]> cspan = new ArrayList<>();

      for(int r = lr + lregion.getRowCount(); r < hr; r++) {
         cspan.add(spans[r]);
      }

      for(int i = 0; i < hspan.size(); i++) {
         spans[lr + i] = hspan.get(i);
      }

      for(int i = 0; i < cspan.size(); i++) {
         spans[lr + hspan.size()] = cspan.get(i);
      }

      for(int i = 0; i < lspan.size(); i++) {
         spans[lr + hspan.size() + cspan.size()] = lspan.get(i);
      }

      TableDataPath path1 = region1.getPath();
      TableDataPath path2 = region2.getPath();
      region1.setPath(path2);
      region2.setPath(path1);
      regions[idx1] = region2;
      regions[idx2] = region1;
   }

   /**
    * Get specified type of regions, such as header, detail and so on.
    */
   public Region[] getRegions(int type) {
      return getRegions(type, regions);
   }

   /**
    * Get specified level's group header/footer region.
    */
   public Region getGroupRegion(int type, int level) {
      for(int i = 0; i < regions.length; i++) {
         TableDataPath path = regions[i].getPath();

         if(path.getType() == type && path.getLevel() == level) {
            return regions[i];
         }
      }

      return null;
   }

   /**
    * Get all cell names.
    * @param runtime true to include runtime cell names (names generated from
    * cell binding if a group is defined).
    */
   public String[] getCellNames(boolean runtime) {
      Set<String> names = getCellNames0(runtime);

      return names.toArray(new String[0]);
   }

   public Set<String> getCellNames0(boolean runtime) {
      Region[] regions = getRegions();
      Set<String> names = new HashSet<>();

      for(Region region : regions) {
         for(int r = 0; r < region.getRowCount(); r++) {
            for(int c = 0; c < region.getColCount(); c++) {
               TableCellBinding binding = (TableCellBinding) region.getCellBinding(r, c);
               Rectangle span = region.findSpan(r, c);

               if(binding == null) {
                  continue;
               }

               String name = runtime ? getRuntimeCellName(binding) : binding.getCellName();

               if(name != null && !"".equals(name) &&
                  (span == null || span.x == 0 && span.y == 0))
               {
                  names.add(name);
               }
            }
         }
      }

      return names;
   }

   /**
    * Get the runtime cell name of a cell.
    * @hidden
    */
   public String getRuntimeCellName(TableCellBinding binding) {
      if(binding.getCellName() != null && !"".equals(binding.getCellName())) {
         return binding.getCellName();
      }

      String rname = binding.getRuntimeCellName();

      if(rname == null) {
         return rname;
      }

      String identifier = System.identityHashCode(binding) + "";
      List<String> sames = new ArrayList<>();
      Set cellNames = new HashSet();

      for(int i = 0; i < getRegionCount(); i++) {
         Region region = getRegion(i);

         for(int r = 0; r < region.getRowCount(); r++) {
            for(int c = 0; c < region.getColCount(); c++) {
               Rectangle span = region.findSpan(r, c);

               // ignore invisible cell
               if(span == null || span.x == 0 && span.y == 0) {
                  TableCellBinding binding2 = (TableCellBinding) region.getCellBinding(r, c);
                  String rname2 = binding2 == null ? null : binding2.getRuntimeCellName();

                  if(binding2 != null && binding2.getCellName() != null) {
                     cellNames.add(binding2.getCellName());
                  }

                  if(rname2 != null && rname.equals(rname2)) {
                     sames.add(System.identityHashCode(binding2) + "");
                  }
               }
            }
         }
      }

      int idx = sames.indexOf(identifier);

      if(idx <= 0 || sames.size() <= 1) {
         return rname;
      }

      while(cellNames.contains(rname + "_" + idx)) {
         idx++;
      }

      return rname + "_" + idx;
   }

   /**
    * Get all vregions.
    */
   public Region[] getVRegions() {
      return vregions;
   }

   /**
    * Get vregions.
    */
   public Region[] getVRegions(int type) {
      return getRegions(type, vregions);
   }

   /**
    * Get vertical region count.
    */
   public int getVRegionCount() {
      return vregions.length;
   }

   /**
    * Get specified index of vertical region.
    */
   public Region getVRegion(int idx) {
      return idx < 0 || idx >= vregions.length ? null : vregions[idx];
   }

   /**
    * Add vertical region.
    */
   public void addVRegion(TableDataPath path, Region vregion) {
      path = fixPathForRegion(path);
      vregion.setPath(path);
      vregions = (Region[]) MatrixOperation.insertRow(vregions, vregions.length);
      vregions[vregions.length - 1] = vregion;
   }

   /**
    * Set runtime to design time table data path mapping.
    * @param r2dpath runtime to designtime path mapping.
    * @param d2rpath designtime to runtime path mapping.
    */
   public void setPathMapping(Map<TableDataPath, TableDataPath> r2dpath,
                              Map<TableDataPath, TableDataPath> d2rpath)
   {
      this.r2dpath = r2dpath;
      this.d2rpath = d2rpath;
   }

   /**
    * Get runtime path by design path.
    * @param dpath the designtime path.
    */
   public TableDataPath getRuntimePath(TableDataPath dpath) {
      return dpath == null ? null : d2rpath.get(dpath);
   }

   /**
    * Get designtime path by runtime path.
    * @param rpath the runtime path.
    */
   public TableDataPath getDesigntimePath(TableDataPath rpath) {
      return rpath == null ? null : r2dpath.get(rpath);
   }

   /**
    * Set mode.
    */
   public void setMode(int mode) {
      this.mode = mode;
   }

   /**
    * Get mode.
    */
   public int getMode() {
      return mode;
   }

   /**
    * Check if the layout is an identical layout.
    */
   public boolean isCalc() {
      return mode == TableLayout.CALC;
   }

   /**
    * Check if layout is crosstab layout.
    */
   public boolean isCrosstab() {
      return mode == TableLayout.CROSSTAB;
   }

   /**
    * Check if layout is normal layout.
    */
   public boolean isNormal() {
      return !isCalc() && !isCrosstab();
   }

   /**
    * Check if layout is normal layout.
    */
   public boolean isPlain() {
      return mode == TableLayout.PLAIN;
   }

   /**
    * Get the calc edit mode.
    */
   public int getCalcEditMode() {
      return calcEditMode;
   }

   /**
    * Set the calc edit mode.
    */
   public void setCalcEditMode(int calcEditMode) {
      this.calcEditMode = calcEditMode;
   }

   /**
    * Get column width.
    */
   public int getColWidth(int c) {
      return c < 0 || c >= cwidths.length ? -1 : cwidths[c];
   }

   /**
    * Set column width.
    */
   public void setColWidth(int c, int w) {
      if(c >= 0 && c < cwidths.length) {
         cwidths[c] = w;
      }
   }

   /**
    * Reset Column Widths.
    */
   public void resetColumnWidths() {
      for(int i = 0; i < cwidths.length; i++) {
         cwidths[i] = -1;
      }
   }

   /**
    * Locate col by table data path.
    */
   public int locateCol(TableDataPath path) {
      Region region = locateVRegion(path);

      if(region != null) {
         for(int i = 0; i < vregions.length; i++) {
            if(region == vregions[i]) {
               return i;
            }
         }
      }

      return -1;
   }

   /**
    * Locate vregion by table data path.
    */
   public Region locateVRegion(TableDataPath path) {
      if(path != null && path.isCol()) {
         path = fixPathForRegion(path);

         for(int i = 0; i < vregions.length; i++) {
            if(path.equals(vregions[i].getPath())) {
               return vregions[i];
            }
         }
      }

      return null;
   }

   /**
    * Get region index by table data path.
    */
   public RegionIndex getRegionIndex(TableDataPath path) {
      int r = locateRow(path);
      return r < 0 ? null : getRegionIndex(r);
   }

   /**
    * Get region index.
    * @param row the global row index in table layout.
    */
   public RegionIndex getRegionIndex(int row) {
      Region region = locateRegion(row);
      int subRow = convertToRegionRow(row);
      return region == null ? null : new RegionIndex(region, subRow);
   }

   /**
    * Get row data path for a region.
    * @param row the global row index in table layout.
    */
   public TableDataPath getRowDataPath(int row) {
      Region region = locateRegion(row);
      int subRow = convertToRegionRow(row);
      return getRowDataPath(region, subRow);
   }

   /**
    * Get row data path for a specified region and the row in the region.
    */
   public TableDataPath getRowDataPath(Region region, int row) {
      return region == null ? null : region.getRowDataPath(row);
   }

   /**
    * Get column data path for a region.
    * @param col the global column index in table layout.
    */
   public TableDataPath getColDataPath(int col) {
      return new TableDataPath("Column [" + col + "]");
   }

   /**
    * Get cell data path for a region.
    * @param row the global row index in table layout.
    * @param col the global column index in table layout.
    */
   public TableDataPath getCellDataPath(int row, int col) {
      Region region = locateRegion(row);
      int subRow = convertToRegionRow(row);
      return getCellDataPath(region, subRow, col);
   }

   /**
    * Get cell data path for a specified region by the region and the row in the
    * region.
    */
   public TableDataPath getCellDataPath(Region region, int row, int col) {
      return region == null ? null : region.getCellDataPath(row, col);
   }

   /**
    * Set the number of columns in the table layout.
    */
   @Override
   public synchronized void setColCount(int ncol) {
      super.setColCount(ncol);
      cwidths = MatrixOperation.toSize(cwidths, ncol);
   }

   /**
    * Insert a column before the specified column.
    * @param col column index in the layout.
    */
   @Override
   public synchronized void insertColumn(int col) {
      super.insertColumn(col);
      cwidths = MatrixOperation.insertColumn(cwidths, col);
   }

   /**
    * Remove a column.
    * @param col column index in the layout.
    */
   @Override
   public synchronized void removeColumn(int col) {
      super.removeColumn(col);
      cwidths = MatrixOperation.removeColumn(cwidths, col);
   }

   /**
    * Write layout to xml format.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<tableLayout columns=\"" + ncol + "\" mode=\"" + mode +
                     "\"");

      if(backLayoutType != -1) {
         writer.print(" backLayoutType=\"" + backLayoutType + "\"");
      }

      if(isCalc() && calcEditMode != DEFAULT_MODE) {
         writer.print(" editMode=\"" + calcEditMode + "\"");
      }

      writer.println(">");

      writer.println("<hregions>");
      writeRegions(writer);
      writer.println("</hregions>");

      writer.println("<vregions>");

      for(int i = 0; i < vregions.length; i++) {
         writer.println("<layoutRegion>");
         TableDataPath path = vregions[i].getPath();
         path.writeXML(writer);
         vregions[i].writeXML(writer);
         writer.println("</layoutRegion>");
      }

      writer.println("</vregions>");

      writer.println("<cwidths>");

      for(int i = 0; i < ncol && i < cwidths.length; i++) {
         if(cwidths[i] >= 0) {
            writer.println("<cwidth c=\"" + i + "\" w=\"" +
               cwidths[i] + "\"/>");
         }
      }

      writer.println("</cwidths>");
      writeSpan(writer);
      writer.println("</tableLayout>");
   }

   /**
    * Parse xml data into object.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      String val;

      if(tag == null) {
         return;
      }

      if((val = Tool.getAttribute(tag, "columns")) != null) {
         setColCount(Integer.parseInt(val));
      }

      if((val = Tool.getAttribute(tag, "backLayoutType")) != null) {
         backLayoutType = Integer.parseInt(val);
      }

      if((val = Tool.getAttribute(tag, "mode")) != null) {
         mode = Integer.parseInt(val);
      }

      if((val = Tool.getAttribute(tag, "editMode")) != null) {
         calcEditMode = Integer.parseInt(val);
      }

      Element hregionsNode = Tool.getChildNodeByTagName(tag, "hregions");

      if(hregionsNode != null) {
         parseRegions(hregionsNode);
      }

      Element vregionsNode = Tool.getChildNodeByTagName(tag, "vregions");

      if(vregionsNode != null) {
         NodeList vregionsList = Tool.getChildNodesByTagName(vregionsNode,
                                                             "layoutRegion");

         for(int i = 0; i < vregionsList.getLength(); i++) {
            if(vregionsList.item(i) instanceof Element) {
               Element tag2 = (Element) vregionsList.item(i);
               Element pE = Tool.getChildNodeByTagName(tag2, "tableDataPath");
               Element rE = Tool.getChildNodeByTagName(tag2, "region");
               TableDataPath path = new TableDataPath();
               Region region = new VRegion();
               path.parseXML(pE);
               region.parseXML(rE);
               addVRegion(path, region);
            }
         }
      }

      Element wnode = Tool.getChildNodeByTagName(tag, "cwidths");

      if(wnode != null) {
         NodeList wlist = Tool.getChildNodesByTagName(wnode, "cwidth");

         for(int i = 0; i < wlist.getLength(); i++) {
            Node node = wlist.item(i);

            if(node instanceof Element) {
               int c = Integer.parseInt(Tool.getAttribute((Element) node, "c"));
               int w = Integer.parseInt(Tool.getAttribute((Element) node, "w"));
               setColWidth(c, w);
            }
         }
      }

      spans = new Dimension[getRowCount()][getColCount()];
      parseSpan(tag);
   }

   /**
    * Make a copy of the layout.
    */
   @Override
   public synchronized Object clone() {
      try {
         TableLayout layout = (TableLayout) super.clone();
         layout.vregions = (Region[]) MatrixOperation.clone(vregions);
         layout.cwidths = cwidths.clone();
         return layout;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone table layout", ex);
      }

      return null;
   }

   /**
    * Check if equals another object.
    * @return ture if equals, false otherwise
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof TableLayout)) {
         return false;
      }

      TableLayout layout = (TableLayout) obj;
      return super.equals(obj) && mode == layout.mode &&
         Tool.equals(vregions, layout.vregions) &&
         Arrays.equals(cwidths, layout.cwidths);
   }

   /**
    * Fix the table data path for region data path.
    */
   @Override
   public TableDataPath fixPathForRegion(TableDataPath path) {
      return new TableDataPath(path.getLevel(), path.getType(),
         path.getDataType(), path.getPath(), path.isRow(), path.isCol());
   }

   /**
    * Replace variable.
    */
   public boolean replaceVariables(VariableTable vars) {
      if(vars == null) {
         return false;
      }

      this.vars = vars;
      boolean replaced = false;

      if(isCalc() && vars != null) {
         for(Region region : getRegions()) {
            for(int r = 0; r < region.getRowCount(); r++) {
               for(int c = 0; c < region.getColCount(); c++) {
                  TableCellBinding cell =
                     (TableCellBinding) region.getCellBinding(r, c);

                  if(cell != null) {
                     replaced = cell.replaceVariables(vars) || replaced;
                  }
               }
            }
         }
      }

      return replaced;
   }

   /**
    * Get all variables.
    */
   public Enumeration getAllVariables() {
      Vector vec = new Vector();

      if(isCalc()) {
         for(Region region : getRegions()) {
            for(int r = 0; r < region.getRowCount(); r++) {
               for(int c = 0; c < region.getColCount(); c++) {
                  TableCellBinding cell =
                     (TableCellBinding) region.getCellBinding(r, c);

                  if(cell != null) {
                     vec.addAll(cell.getAllVariables());
                  }
               }
            }
         }
      }

      return vec.elements();
   }

   /**
    * Create cell binding.
    */
   @Override
   protected CellBinding createCellBinding() {
      return new TableCellBinding();
   }

   /**
    * Get specified type of regions from the region array.
    */
   private Region[] getRegions(int type, Region[] fregions) {
      ArrayList<Region> rregions = new ArrayList<>();

      for(int i = 0; i < fregions.length; i++) {
         TableDataPath path = fregions[i].getPath();

         if(path != null && path.getType() == type) {
            rregions.add(fregions[i]);
         }
      }

      return rregions.toArray(new Region[0]);
   }

   /**
    * Defines a region of cols. Each region is used to display one col in the
    * data table lens, now only is empty, all cell store in HRegion.
    */
   public class VRegion extends Region {
      /**
       * Get number of rows in the region.
       */
      @Override
      public int getRowCount() {
         return 0;
      }

      /**
       * Set the number of rows in the region.
       */
      @Override
      public void setRowCount(int nrow) {
         // do nothing
      }

      /**
       * Get the row height.
       * @param r row index.
       * @return -1 if the height is not set, or row heights in points.
       */
      @Override
      public int getRowHeight(int r) {
         return r < 0 || r >= rheights.length ? -1 : rheights[r];
      }

      /**
       * Set the row height.
       * @param r row index.
       * @param height -1 if the height is not set, or row heights in points.
       */
      @Override
      public void setRowHeight(int r, int height) {
         if(r >= 0 && r < rheights.length) {
            rheights[r] = height;
         }
      }

      /**
       * Get the cell binding for the cell.
       * @param r row index within the region.
       * @param c column index within the region.
       */
      @Override
      public CellBinding getCellBinding(int r, int c) {
         return null;
      }

      /**
       * Set the cell binding for the cell.
       * @param r row index within the region.
       * @param c column index within the region.
       * @param binding cell binding.
       */
      @Override
      public void setCellBinding(int r, int c, CellBinding binding) {
         // do nothing
      }

      /**
       * Get the row in the table to map this region row to.
       */
      @Override
      public int getRowBinding(int r) {
         return -1;
      }

      /**
       * Set the row in the base table to map this row to. This is used in
       * crosstab header to allow a row to map to a header without individual
       * cell binding.
       */
      @Override
      public void setRowBinding(int r, int map) {
         // do nothing
      }

      /**
       * Insert a row above the specified row.
       * @param row row index.
       */
      @Override
      public void insertRow(int row) {
         // do nothing
      }

      /**
       * Remove a row.
       * @param row row index.
       */
      @Override
      public void removeRow(int row) {
         // do nothing
      }
   }

   /**
    * Data structure to hold a region and index within the region.
    */
   public static class RegionIndex {
      public RegionIndex(Region region, int row) {
         this.region = region;
         this.row = row;
      }

      public Region getRegion() {
         return region;
      }

      public int getRow() {
         return row;
      }

      public String toString() {
         return "row: " + row + ", " + region;
      }

      private Region region;
      private int row; // row index within region
   }

   /**
    * Region info.
    */
   private static class RegionInfo implements GroupableBandInfo {
      public RegionInfo(Region region) {
         this.region = region;
      }

      /**
       * Check if the band is visible.
       */
      @Override
      public boolean isVisible() {
         return region.isVisible();
      }

      /**
       * Get band type.
       */
      @Override
      public int getType() {
         return region.getPath().getType();
      }

      /**
       * Get band level.
       */
      @Override
      public int getLevel() {
         return region.getPath().getLevel();
      }

      /**
       * Get cell binding infos.
       */
      public List<CellBindingInfo> getCellInfos() {
         return getCellInfos(false);
      }

      /**
       * Get cell binding infos.
       * @param all, false only return cells which on top-left of
       *  span or no span cells.
       */
      @Override
      public List<CellBindingInfo> getCellInfos(boolean all) {
         List<CellBindingInfo> cinfos = new ArrayList<>();

         for(int r = 0; r < region.getRowCount(); r++) {
            for(int c = 0; c < region.getColCount(); c++) {
               boolean include = all;

               if(!include) {
                  Rectangle span = region.findSpan(r, c);
                  include = span == null || (span.x == 0 && span.y == 0);
               }

               if(include) {
                  cinfos.add(new TableCellBindingInfo(region, r, c));
               }
            }
         }

         return cinfos;
      }

      /**
       * Get band.
       */
      @Override
      public Object getBand() {
         return region;
      }

      private Region region;
   }

   /**
    * CellBinding info.
    */
   public static class TableCellBindingInfo implements CellBindingInfo {
      public TableCellBindingInfo(Region region, int r, int c) {
         this.region = region;
         this.r = r;
         this.c = c;
         binding = (TableCellBinding) region.getCellBinding(r, c);
      }

      /**
       * Check if the cell binding is virtual.
       */
      @Override
      public boolean isVirtual() {
         return region.isVirtual();
      }

      /**
       * Get the value for the cell binding.
       */
      @Override
      public String getValue() {
         return binding == null ? null : binding.getValue();
      }

      /**
       * Set the value for the cell binding.
       */
      @Override
      public void setValue(String value) {
         if(binding != null) {
            binding.setValue(value);
         }
      }

      /**
       * Get the binding type for the cell.
       */
      @Override
      public int getType() {
         return binding == null ? -1 : binding.getType();
      }

      /**
       * Get the group type for the cell, group, summary or detail.
       */
      @Override
      public int getBType() {
         return binding == null ? TableCellBinding.DETAIL : binding.getBType();
      }

      /**
       * Set the group type for the cell, group, summary or detail.
       */
      @Override
      public void setBType(int btype) {
         if(binding != null) {
            binding.setBType(btype);
         }
      }

      /**
       * Get group option.
       */
      public int getDateOption() {
         return binding == null ? -1 : binding.getDateOption();
      }

      /**
       * Set date option.
       */
      public void setDateOption(int dateOpt) {
         if(binding != null) {
            binding.setDateOption(dateOpt);
         }
      }

      /**
       * Get aggregate formula.
       */
      public String getFormula() {
         return binding == null ? null : binding.getFormula();
      }

      /**
       * Set formula.
       */
      public void setFormula(String formula) {
         if(binding != null) {
            binding.setFormula(formula);
         }
      }

      /**
       * Get the cell binding field.
       */
      @Override
      public Field getField() {
         return binding == null ? null : binding.getField();
      }

      /**
       * Set the cell binding field.
       */
      @Override
      public void setField(Field cfield) {
         if(binding != null) {
            binding.setField(cfield);
         }
      }

      /**
       * Get expansion.
       */
      @Override
      public int getExpansion() {
         return binding == null ? 0 : binding.getExpansion();
      }

      /**
       * Set expansion.
       */
      @Override
      public void setExpansion(int expansion) {
         if(binding != null) {
            binding.setExpansion(expansion);
         }
      }

      /**
       * Get the band type.
       */
      @Override
      public int getBandType() {
         return region.getPath().getType();
      }

      /**
       * Get the group band level for the cell.
       */
      @Override
      public int getBandLevel() {
         return region.getPath().getLevel();
      }

      /**
       * Get the position for the cell in the band.
       */
      @Override
      public Point getPosition() {
         return new Point(c, r);
      }

      /**
       * Set value as group.
       */
      @Override
      public void setAsGroup(boolean asGroup) {
         if(binding != null) {
            binding.setAsGroup(asGroup);
         }
      }

      /**
       * Set date period interval and option.
       * @param d date period interval.
       * @param opt date period option.
       */
      public void setInterval(double d, int opt) {
         binding.getOrderInfo(true).setInterval(d, opt);
      }

      public String toString() {
         return region.getPath() + " : binding = " + getType() +
            ", btype = " + getType() + ", gtype = " + getBType() +
            ", glvl = " + getBandLevel() + ", pos = " + getPosition() + ")";
      }

      /**
       * Get cell binding.
       */
      public TableCellBinding getCellBinding() {
         return binding;
      }

      private Region region;
      private TableCellBinding binding;
      private int r;
      private int c;
   }

   // @by davyc, for BC, we should support freehand layout type
   private int backLayoutType = -1;
   // vertical regions, for crosstab
   private Region[] vregions = {};
   // each column widths
   private int[] cwidths = {};
   private int[] rheights = {};
   private int mode = -1;
   private int calcEditMode = DEFAULT_MODE;

   // runtime table data path -> design time table data path
   private Map<TableDataPath, TableDataPath> r2dpath = new HashMap<>();
   // design time table data path -> runtime time table data path
   private Map<TableDataPath, TableDataPath> d2rpath = new HashMap<>();

   private transient VariableTable vars;
   private static final Logger LOG = LoggerFactory.getLogger(TableLayout.class);
}
