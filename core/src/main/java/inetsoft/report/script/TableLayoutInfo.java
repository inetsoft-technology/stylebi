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
package inetsoft.report.script;

import inetsoft.report.TableCellBinding;
import inetsoft.report.TableLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TableLayoutInfo, scriptable to operate table layout info.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public abstract class TableLayoutInfo extends PropertyScriptable {
   public TableLayoutInfo() {
      try {
         Class[] osparams = {int.class, int.class, Object.class, String.class};
         addFunctionProperty(getClass(), "setCellBinding", osparams);

         Class[] oparams = {int.class, int.class, Object.class};
         addFunctionProperty(getClass(), "setExpansion", oparams);

         Class[] sparams = {int.class, int.class, String.class};
         addFunctionProperty(getClass(), "setRowGroup", sparams);

         addFunctionProperty(getClass(), "setColGroup", sparams);
         addFunctionProperty(getClass(), "setCellName", sparams);

         Class[] bparams = {int.class, int.class, boolean.class};
         addFunctionProperty(getClass(), "setMergeCells", bparams);
         addFunctionProperty(getClass(), "setMergeRowGroup", sparams);
         addFunctionProperty(getClass(), "setMergeColGroup", sparams);

         Class[] iparams = {int.class, int.class, int.class, int.class};
         addFunctionProperty(getClass(), "setSpan", iparams);
      }
      catch(Exception e) {
         LOG.error("Failed to register table layout properties and functions", e);
      }
   }

   /**
    * Set cell binding.
    */
   public void setCellBinding(int r, int c, Object type, String value) {
      int itype = parseType(type);
      TableCellBinding binding = getCellBinding(r, c);

      if(binding != null) {
         binding.setType(itype);
         binding.setValue(value);
         resetCalc();
      }
   }

   /**
    * Set expansion.
    */
   public void setExpansion(int r, int c, Object direct) {
      if(!getTableLayout(true).isCalc()) {
         LOG.warn("setExpansion() is only applicable to formula tables: " + getID());
      }

      int idirect = parseDirect(direct);
      TableCellBinding binding = getCellBinding(r, c);

      if(binding != null) {
         binding.setExpansion(idirect);
         resetCalc();
      }
   }

   /**
    * Set row group.
    */
   public void setRowGroup(int r, int c, String rgrp) {
      if(!getTableLayout(true).isCalc()) {
         LOG.warn("setRowGroup() is only applicable to formula tables: " + getID());
      }

      TableCellBinding binding = getCellBinding(r, c);

      if(binding != null) {
         binding.setRowGroup(rgrp);
         resetCalc();
      }
   }

   /**
    * Set column group.
    */
   public void setColGroup(int r, int c, String cgrp) {
      if(!getTableLayout(true).isCalc()) {
         LOG.info("setColGroup() is only applicable to formula tables: " + getID());
      }

      TableCellBinding binding = getCellBinding(r, c);

      if(binding != null) {
         binding.setColGroup(cgrp);
         resetCalc();
      }
   }

   /**
    * Set cell name.
    */
   public void setCellName(int r, int c, String cname) {
      if(!getTableLayout(true).isCalc()) {
         LOG.info("setCellName() is only applicable to formula tables: " + getID());
      }

      TableCellBinding binding = getCellBinding(r, c);

      if(binding != null) {
         binding.setCellName(cname);
         resetCalc();
      }
   }

   /**
    * Set merge cells.
    */
   public void setMergeCells(int r, int c, boolean merge) {
      TableCellBinding binding = getCellBinding(r, c);

      if(binding != null) {
         binding.setMergeCells(merge);
         resetCalc();
      }
   }

   /**
    * Set merge row group.
    */
   public void setMergeRowGroup(int r, int c, String mrgp) {
      if(!getTableLayout(true).isCalc()) {
         LOG.info("setMergeRowGroup() is only applicable to formula tables: " + getID());
      }

      TableCellBinding binding = getCellBinding(r, c);

      if(binding != null) {
         binding.setMergeRowGroup(mrgp);
         resetCalc();
      }
   }

   /**
    * Set merge column group.
    */
   public void setMergeColGroup(int r, int c, String mrgp) {
      if(!getTableLayout(true).isCalc()) {
         LOG.info("setMergeColGroup() is only applicable to formula tables: " + getID());
      }

      TableCellBinding binding = getCellBinding(r, c);

      if(binding != null) {
         binding.setMergeColGroup(mrgp);
         resetCalc();
      }
   }

   /**
    * Set span.
    */
   public void setSpan(int row, int col, int spanw, int spanh) {
      if(spanw <= 0 || spanh <= 0) {
         return;
      }

      TableLayout layout = getTableLayout(false);

      if(layout != null) {
         setCellSpan(row, col, spanw, spanh);
         resetCalc();
      }
   }

   /**
    * Get cell binding in the specified row and column.
    */
   private TableCellBinding getCellBinding(int r, int c) {
      TableLayout layout = getTableLayout(false);

      if(layout == null) {
         return null;
      }

      if(r < 0 || c < 0 || r >= layout.getRowCount() || c >= layout.getColCount()) {
         LOG.warn("Invalid row or column found: (r = " + r +
                  ", " + "c = " + c + "), operation will be ignored.");
         return null;
      }

      TableLayout.RegionIndex ridx = layout.getRegionIndex(r);

      if(ridx != null) {
         TableCellBinding binding = (TableCellBinding)
            ridx.getRegion().getCellBinding(ridx.getRow(), c);

         if(binding == null) {
            binding = new TableCellBinding();
            ridx.getRegion().setCellBinding(ridx.getRow(), c, binding);
         }

         return binding;
      }

      return null;
   }

   /**
    * Reset calc runtime layout.
    */
   protected abstract void resetCalc();

   protected abstract String getID();

   protected abstract TableLayout getTableLayout(boolean createIfNone);

   protected abstract void setCellSpan(int row, int col, int spanw, int spanh);

   /**
    * Parse binding type.
    */
   private int parseType(Object type) {
      int itype = -1;

      if(type instanceof Number) {
         double dval = ((Number) type).doubleValue();
         itype = (int) dval;
      }
      else {
         String stype = type == null ? null : type.toString();

         try {
            double dval = Double.parseDouble(stype);
            itype = (int) dval;
         }
         catch(Exception ex) {
         }

         if(itype < 0) {
            if("text".equalsIgnoreCase(stype) ||
               "txt".equalsIgnoreCase(stype) || "t".equalsIgnoreCase(stype))
            {
               itype = TableCellBinding.BIND_TEXT;
            }
            else if("column".equalsIgnoreCase(stype) ||
               "col".equalsIgnoreCase(stype) || "c".equalsIgnoreCase(stype))
            {
               itype = TableCellBinding.BIND_COLUMN;
            }
            else if("formula".equalsIgnoreCase(stype) ||
               "f".equalsIgnoreCase(stype))
            {
               itype = TableCellBinding.BIND_FORMULA;
            }
         }
      }

      switch(itype) {
      case TableCellBinding.BIND_TEXT:
      case TableCellBinding.BIND_COLUMN:
      case TableCellBinding.BIND_FORMULA:
         break;
      default:
         itype = TableCellBinding.BIND_TEXT;
         LOG.warn(
            "Given type \"" + type + "\" is not a valid, " +
            " Use text binding instead.");
         break;
      }

      return itype;
   }

   /**
    * Parse direction.
    */
   private int parseDirect(Object direct) {
      int expansion = -1;

      if(direct instanceof Number) {
         double dval = ((Number) direct).doubleValue();
         expansion = (int) dval;
      }
      else if(direct != null) {
         String sdirect = direct.toString();

         try {
            double dval = Double.parseDouble(sdirect);
            expansion = (int) dval;
         }
         catch(Exception ex) {
         }

         if(expansion < 0) {
            if("V".equalsIgnoreCase(sdirect)) {
               expansion = TableCellBinding.EXPAND_V;
            }
            else if("H".equalsIgnoreCase(sdirect)) {
               expansion = TableCellBinding.EXPAND_H;
            }
            else {
               expansion = TableCellBinding.EXPAND_NONE;
            }
         }
      }

      switch(expansion) {
      case TableCellBinding.EXPAND_V:
      case TableCellBinding.EXPAND_H:
      case TableCellBinding.EXPAND_NONE:
         break;
      default:
         expansion = TableCellBinding.EXPAND_V;
         LOG.warn("Given expansion value \"" + direct +
                  "\" is not valid, use vertical expansion instead.");
         break;
      }

      return expansion;
   }

   @Override
   public String getClassName() {
      return "TableLayoutInfo";
   }

   @Override
   public Object getDefaultValue(Class aClass) {
      return "Table Layout Info";
   }

   private static final Logger LOG = LoggerFactory.getLogger(TableLayoutInfo.class);
}
