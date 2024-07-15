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
package inetsoft.web.composer.tablestyle.service;

import inetsoft.analytic.composition.SheetLibraryService;
import inetsoft.analytic.composition.VSCSSUtil;
import inetsoft.report.*;
import inetsoft.report.filter.*;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.style.TableStyle;
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.adhoc.model.AlignmentInfo;
import inetsoft.web.adhoc.model.FontInfo;
import inetsoft.web.composer.tablestyle.SaveLibraryDialogModelValidator;
import inetsoft.web.composer.tablestyle.css.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.security.Principal;
import java.util.Arrays;
import java.util.Enumeration;

@Service
public class TableStyleService {
   @Autowired
   public TableStyleService(SheetLibraryService sheetLibraryService,
                            AssetRepository assetRepository)
   {
      this.sheetLibraryService = sheetLibraryService;
      this.assetRepository = assetRepository;
   }

   public void checkTableStylePermission(AssetEntry entry, String name,
                                         SaveLibraryDialogModelValidator validator,
                                         Principal principal, boolean isSave)
   {
      String label = getTableStyleLabel(name);
      Catalog catalog = Catalog.getCatalog();

      try {
         assetRepository.checkAssetPermission(principal, entry, ResourceAction.WRITE);
      }
      catch(Exception e) {
         if(isSave) {
            validator.setPermissionDenied(
               catalog.getString("common.writeAuthority", label));
         }
         else {
            validator.setPermissionDenied(
               catalog.getString("security.nopermission.create", label));
         }
      }
   }

   public void checkDuplicateTableStyle(String folder, String name,
                                        SaveLibraryDialogModelValidator validator)
   {
      LibManager manager = LibManager.getManager();
      Catalog catalog = Catalog.getCatalog();
      String containsFolder = getTableStyleLabel(folder, name);

      if(contains(folder, name)) {
         validator.setAlreadyExists(Tool.buildString(
            name, " ", catalog.getString("table.style.duplicated"),  "?"));
         validator.setAllowOverwrite(true);
      }
      else if(manager.containsFolder(containsFolder)) {
         validator.setAlreadyExists(catalog.getString("em.common.folder.nameDuplicate"));
         validator.setAllowOverwrite(false);
      }
   }

   public AssetEntry getTemporaryAssetEntry(Principal user) {
      return sheetLibraryService.getTemporaryAssetEntry(user, AssetEntry.Type.TABLE_STYLE);
   }

   public TableLens getTableModel() {
      DefaultTableLens dataTbl = new DefaultTableLens();
      dataTbl.setData(group_data);
      dataTbl.setHeaderRowCount(1);
      dataTbl.setHeaderColCount(1);
      dataTbl.setTrailerRowCount(1);
      dataTbl.setTrailerColCount(1);

      SummaryFilter group = new SummaryFilter(
         new SortFilter(dataTbl, new int[] {0}),
         new int[] {1, 2, 3, 4, 5}, new SumFormula(), new SumFormula()) {
         @Override
         public int getHeaderColCount() {
            return 1;
         }

         @Override
         public int getTrailerColCount() {
            return 1;
         }

         @Override
         public int getAlignment(int r, int c) {
            if(c == 0) {
               return StyleConstants.H_LEFT;
            }

            return StyleConstants.H_RIGHT;
         }

         @Override
         public int getRowBorder(int r, int c) {
            return StyleConstants.THIN_LINE;
         }

         @Override
         public int getColBorder(int r, int c) {
            return StyleConstants.THIN_LINE;
         }
      };

      group.setAddGroupHeader(true);
      group.setShowGroupColumns(false);
      group.setGrandLabel("Total");

      return group;
   }

   public CSSTableStyleModel getCSSTableStyleModel(XTableStyle style) {
      style.getTable().moreRows(XTable.EOT);
      TableStyleRow[] rows = new TableStyleRow[style.getRowCount()];

      for(int i = 0; i < style.getRowCount(); i++) {
         TableStyleCell[] cells = new TableStyleCell[style.getColCount()];

         for(int j = 0; j < style.getColCount(); j++) {
            Font font = style.getFont(i, j);
            FontInfo finfo = new FontInfo(font);
            AlignmentInfo alignInfo = CSSUtil.getAlignment(style.getAlignment(i, j));
            TableStyleFormat tableStyleFormat = new TableStyleFormat();
            tableStyleFormat.setBackground(CSSUtil.getColorString(style.getBackground(i, j)));
            tableStyleFormat.setFont(VSCSSUtil.getFont(font));
            tableStyleFormat.setForeground(CSSUtil.getColorString(style.getForeground(i, j)));
            tableStyleFormat.setvAlign(alignInfo.getValign());
            tableStyleFormat.sethAlign(alignInfo.getHalign());
            tableStyleFormat.setFontUnderline(
               Tool.equals(finfo.getFontUnderline(), "underline") ? "underline " : "");
            tableStyleFormat.setFontStrikethrough(
               Tool.equals(finfo.getFontStrikethrough(), "strikethrough") ? "line-through" : "");
            tableStyleFormat.setRightBorder((CSSUtil.getBorderStyle(style.getColBorder(i, j),
               style.getColBorderColor(i, j))));
            tableStyleFormat.setBottomBorder(CSSUtil.getBorderStyle(style.getRowBorder(i, j),
               style.getRowBorderColor(i, j)));

            // only paint top border for header row.
            if(i == 0) {
               tableStyleFormat.setTopBorder(CSSUtil.getBorderStyle(style.getRowBorder(-1, j),
                  style.getRowBorderColor(-1, j)));
            }

            // only paint left border for header column
            if(j == 0) {
               tableStyleFormat.setLeftBorder(CSSUtil.getBorderStyle(style.getColBorder(i, -1),
                  style.getColBorderColor(i, -1)));
            }

            cells[j] = new TableStyleCell(i, j, style.getObject(i, j),
               regionString(i, j, style), tableStyleFormat);
         }

         rows[i] = new TableStyleRow(cells);
      }

      return new CSSTableStyleModel(rows);
   }

   private String regionString(int i, int j, XTableStyle style) {
      if(i == 0) {
         return "Header Row";
      }
      else if(i == style.getRowCount() - 1) {
         return "Trailer Row";
      }
      else if(i > 0 && i < style.getRowCount() - 1 && j == 0) {
         return "Header Column";
      }
      else if(i > 0 && i < style.getRowCount() - 1 && j == style.getColCount() - 1) {
         return "Trailer Column";
      }
      else {
         return "Body";
      }
   }

   /**
    * Check if contains a table style.
    * @name the specified table style name.
    * @return <tt>true</tt> if contains, <tt>false</tt> otherwise.
    */
   public boolean containsTableStyle(String name) {
      return getTableStyleID(name) != null;
   }

   public void initTableStyle(XTableStyle style) {
      style.setApplyAlignment(true);
      style.setFormatFirstRow(true);
      style.setFormatFirstCol(true);
      style.setFormatLastRow(true);
      style.setFormatLastCol(true);
   }

   /**
    * Get existing table style id.
    * @param name the full name of the specified table style.
    * @return table style id if any.
    */
   private static String getTableStyleID(String name) {
      LibManager mgr = LibManager.getManager();
      Enumeration<String> styles = mgr.getTableStyles();

      while(styles.hasMoreElements()) {
         XTableStyle tstyle = (XTableStyle) get(styles.nextElement());

         if(name.equals(tstyle.getName())) {
            return tstyle.getID();
         }
      }

      return null;
   }

   /**
    * Get a table style class. The name of the style is either one of the
    * default style class name (without the package name), or the style
    * name of an user defined class.
    * @param name name of the style.
    * @return table style.
    */
   private static TableStyle get(String name) {
      return LibManager.getManager().getTableStyle(name);
   }

   public boolean contains(String folder, String name) {
      LibManager manager = LibManager.getManager();
      XTableStyle[] tableStyles = manager.getTableStyles(folder);

      return Arrays.stream(tableStyles)
         .anyMatch(xTableStyle -> Tool.equals(xTableStyle.getName(),
            getTableStyleLabel(folder, name)));
   }

   public String getTableStyleLabel(String path) {
      int index = path.lastIndexOf(LibManager.SEPARATOR);
      String name;
      Catalog catalog = Catalog.getCatalog();

      if(index < 0) {
         name = path;
      }
      else {
         name = path.substring(index + 1);
      }

      return catalog.getString(name);
   }

   public String getTableStyleLabel(String folder, String name) {
      if(Tool.isEmptyString(folder)) {
         return name;
      }
      else {
         return folder + LibManager.SEPARATOR + name;
      }
   }

   public String getObjectName(String name) {
      int index = name.lastIndexOf(LibManager.SEPARATOR);

      if(index < 0) {
         return "TableStyle/" + name;
      }
      else {
         return "TableStyle/" + name.replace(LibManager.SEPARATOR, "/");
      }
   }

   private Object[][] group_data = {
      {"Region", "1st Qtr", "2nd Qtr", "3rd Qtr", "4th Qtr", "Total"},
      {"East", 200, 300, 100, 120, 720},
      {"West", 70, 80, 50, 60, 260},
      {"North", 80, 40, 20, 20, 160},
      {"South", 100, 200, 80, 60, 440}
   };

   private final SheetLibraryService sheetLibraryService;
   private final AssetRepository assetRepository;
}