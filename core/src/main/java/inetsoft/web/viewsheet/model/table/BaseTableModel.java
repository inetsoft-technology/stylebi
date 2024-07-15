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
package inetsoft.web.viewsheet.model.table;

import inetsoft.report.TableDataPath;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.viewsheet.model.VSFormatModel;
import inetsoft.web.viewsheet.model.VSObjectModel;

import java.awt.*;
import java.util.ArrayList;
import java.util.Enumeration;

public abstract class BaseTableModel<T extends TableDataVSAssembly> extends VSObjectModel<T> {
   BaseTableModel(T assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);

      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      TableDataVSAssemblyInfo tinfo = (TableDataVSAssemblyInfo) info;
      TableDataPath titlePath = new TableDataPath(-1, TableDataPath.TITLE);
      VSCompositeFormat compositeFormat = info.getFormatInfo().getFormat(titlePath, false);
      titleFormat = new VSFormatModel(compositeFormat, info);
      title = tinfo.getTitle();
      empty = tinfo.getTableName() == null;
      final Dimension maxSize = tinfo.getMaxSize();

      if(maxSize != null) {
         final VSFormatModel objectFormat = getObjectFormat();
         setMaxModeOriginalWidth(objectFormat.getWidth());
         objectFormat.setPositions(new Point(0, 0), maxSize);
         objectFormat.setzIndex(tinfo.getMaxModeZIndex());
         this.maxMode = true;
      }

      TableHighlightAttr highlightAttr = tinfo.getHighlightAttr();

      // highlightedCells maintains a list of data paths of cells that have highlight in this table
      // It's used to determine if copy highlight option is available for a selected table cell
      if(highlightAttr != null) {
         Enumeration dataPaths = highlightAttr.getAllDataPaths();
         ArrayList<TableDataPath> array = new ArrayList<>();

         while(dataPaths.hasMoreElements()) {
            TableDataPath dataPath = (TableDataPath) dataPaths.nextElement();
            array.add(dataPath);
         }

         this.highlightedCells = array.toArray(new TableDataPath[0]);
      }
      else {
         this.highlightedCells = new TableDataPath[0];
      }

      String[] flyovers = VSUtil.getValidFlyovers(tinfo.getFlyoverViews(), assembly.getViewsheet());
      hasFlyover = flyovers != null && flyovers.length > 0;
      isFlyOnClick = tinfo.isFlyOnClick();

      int titleHeight = tinfo.getTitleHeight();
      titleFormat.setPositions(new Point(0, 0),
         new Dimension((int) this.getObjectFormat().getWidth(), titleHeight));
      //visibleRows will not include headers
      titleVisible = tinfo.isTitleVisible();

      shrink = tinfo.isShrink();
      explicitTableWidth = tinfo.isExplicitTableWidth();
      enableAdhoc = tinfo.isEnableAdhoc();
      enableAdvancedFeatures =
         "true".equals(SreeEnv.getProperty("viewsheet.viewer.advancedFeatures"));

      Viewsheet vs = rvs.getViewsheet();
      metadata = vs.getViewsheetInfo().isMetadata();

      // disable editing on data tip
      if(vs.getDataTips() != null && vs.getDataTips().contains(info.getAbsoluteName()) ||
         vs.getPopComponents().contains(info.getAbsoluteName()))
      {
         enableAdhoc = false;
      }
   }

   public String getTitle() {
      return title;
   }

   public VSFormatModel getTitleFormat() {
      return titleFormat;
   }

   public boolean isTitleVisible() {
      return titleVisible;
   }

   public boolean isHasFlyover() {
      return hasFlyover;
   }

   public boolean getIsFlyOnClick() {
      return isFlyOnClick;
   }

   public boolean isShrink() {
      return shrink;
   }

   public boolean isExplicitTableWidth() {
      return explicitTableWidth;
   }

   public boolean isEnableAdhoc() {
      return enableAdhoc;
   }

   public boolean isEnableAdvancedFeatures() {
      return enableAdvancedFeatures;
   }

   public void setHighlightedCells (TableDataPath[] paths) {
      this.highlightedCells = paths;
   }

   public TableDataPath[] getHighlightedCells() {
      return this.highlightedCells;
   }

   public double[] getColWidths() {
      return colWidths;
   }

   public void setColWidths(double[] colWidths) {
      this.colWidths = colWidths;
   }

   public int getRowCount() {
      return rowCount;
   }

   public void setRowCount(int rowCount) {
      this.rowCount = rowCount;
   }

   public int getColCount() {
      return colCount;
   }

   public int getDataRowCount() {
      return dataRowCount;
   }

   public int getDataColCount() {
      return dataColCount;
   }

   public int getHeaderRowCount() {
      return headerRowCount;
   }

   public void setHeaderRowCount(int headerRowCount) {
      this.headerRowCount = headerRowCount;
   }

   public int getHeaderColCount() {
      return headerColCount;
   }

   public int[] getHeaderRowHeights() {
      return headerRowHeights;
   }

   public int getDataRowHeight() {
      return dataRowHeight;
   }

   public void setDataRowHeight(int dataRowHeight) {
      this.dataRowHeight = dataRowHeight;
   }

   public int[] getHeaderRowPositions() {
      return headerRowPositions;
   }

   public int[] getDataRowPositions() {
      return dataRowPositions;
   }

   public int getScrollHeight() {
      return scrollHeight;
   }

   public boolean isWrapped() {
      return wrapped;
   }

   public boolean isEmpty() {
      return empty;
   }

   public boolean isMaxMode() {
      return maxMode;
   }

   public void setMaxMode(boolean maxMode) {
      this.maxMode = maxMode;
   }

   public double getMaxModeOriginalWidth() {
      return maxModeOriginalWidth;
   }

   public void setMaxModeOriginalWidth(double maxModeOriginalWidth) {
      this.maxModeOriginalWidth = maxModeOriginalWidth;
   }

   private String title;
   private VSFormatModel titleFormat;
   private boolean titleVisible;
   private boolean hasFlyover;
   private boolean isFlyOnClick;
   private boolean shrink;
   private boolean explicitTableWidth;
   private boolean enableAdhoc;
   private boolean enableAdvancedFeatures;
   private TableDataPath[] highlightedCells;
   private boolean maxMode = false;
   private double maxModeOriginalWidth = 0;
   protected boolean empty = false;

   // these properties will be setted when first load table model in
   // VSBindingMoelController to make sure the table can be fully expanded
   // in vs binding pane.
   private double[] colWidths = new double[0];
   private int rowCount = 0;
   private int headerRowCount = 0;
   private int dataRowHeight = 0;

   // these properties are sent to the client in LoadTableDataCommand, they are just here to
   // initialize the client data structure
   private final int colCount = 0;
   private final int dataRowCount = 0;
   private final int dataColCount = 0;
   private final int headerColCount = 0;
   private final int[] headerRowHeights = new int[0];
   private final int[] headerRowPositions = new int[0];
   private final int[] dataRowPositions = new int[0];
   private final int scrollHeight = 0;
   private final boolean wrapped = false;
   private final boolean metadata;
}
