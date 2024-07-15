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
package inetsoft.web.binding.handler;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.web.binding.dnd.TransferType;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class VSTableBindingHandler {
   /**
    * Add or replace data refs in a table assembly's column selection with data refs from another
    * table assembly (table columns) or selection assembly (selection list cell)
    *
    * @param tableAssembly the TableVSAssembly being dropped onto.
    * @param dragAssembly  the assembly containing the dragged region
    * @param sourceIndex   the index to get the data ref
    * @param targetIndex   the index to bind the data ref to on assembly
    * @param replace       true if replacing the column binding, else adding a new column
    * @param transferType "table" if dnd on table view, "field" if dnd on binding pane
    */
   public void addRemoveColumns(TableVSAssembly tableAssembly, VSAssembly dragAssembly,
                                int sourceIndex, int targetIndex, boolean replace,
                                String dragType, TransferType transferType)
   {
      List<DataRef> refs = getDataRefs(dragAssembly, sourceIndex, dragType,
                                       transferType == TransferType.FIELD);

      if(refs != null) {
         for(DataRef ref : refs) {
            addDataRef(tableAssembly, ref, targetIndex, replace);
         }
      }
   }

   /**
    * Add data refs created from asset entries to column selection
    *
    * @param assembly the table assembly.
    * @param entries  the entries from the binding tree to create the data refs
    * @param addIndex the index where to add the data refs in the table assembly's column selection
    * @param replace  if true, replace the data ref at the index with the new data ref, else add
    *                 it to the end of the column selection
    */
   public void addColumns(TableVSAssembly assembly,
      AssetEntry[] entries, int addIndex, boolean replace)
   {
      TableVSAssemblyInfo tableVSAssemblyInfo = (TableVSAssemblyInfo) assembly.getVSAssemblyInfo();
      //The array is traversed from the back, and then addColumns is the correct order
      for(int i = entries.length - 1; i > -1; i--) {
         DataRef ref = createDataRef(entries[i]);
         ref = tableVSAssemblyInfo.isForm() ? FormRef.toFormRef(ref) : ref;
         addDataRef(assembly, ref, addIndex, replace);
         // only replace the first field
         replace = false;
      }
   }

   /**
    * Remove data ref from column selection of table assembly
    *
    * @param assembly the vsassembly.
    * @param index    the index of the object to remove from the assembly's ColumnSelection
    */
   public void removeColumns(TableVSAssembly assembly, int index) {
      ColumnSelection cols = assembly.getColumnSelection();
      cols.removeAttribute(index);

      if(cols.getAttributeCount() == 0) {
         assembly.setSourceInfo(null);
      }
   }

   /** For vs table, only drop to detail field. Remove duplicate columns before drop.
    *  Since columnselection can't add duplicate columns, so remove column before add.
    */
   private void addDataRef(TableVSAssembly assembly, DataRef ref, int index, boolean replace) {
      ColumnSelection cols = assembly.getColumnSelection();
      SortInfo sort = assembly.getSortInfo();

      if(replace && index >= 0) {
         DataRef dropRef = cols.getAttribute(index);

         if(dropRef != null && sort != null) {
            sort.removeSort(dropRef);
         }

         cols.removeAttribute(index);
      }

      // sanity check
      if(index < 0) {
         index = cols.getAttributeCount();
      }

      int idx = cols.indexOfAttribute(ref);

      // If dropped column(s) already in table, then move them.
      if(idx >= 0) {
         if(idx != index) {
            cols.removeAttribute(idx);

            if(index < idx) {
               cols.addAttribute(index, ref);
            }
            else {
               cols.addAttribute(index - 1, ref);
            }
         }
      }
      else {
         cols.addAttribute(index, ref);
      }
   }

   /**
    * Create a dataref by assetentry.
    */
   private DataRef createDataRef(AssetEntry entry) {
      if(!entry.isColumn()) {
         return null;
      }

      String entity = entry.getProperty("entity");
      String attr = entry.getProperty("attribute");
      String refType = entry.getProperty("refType");
      String caption = entry.getProperty("caption");
      String dtype = entry.getProperty("dtype");
      AttributeRef ref = new AttributeRef(entity, attr);

      if(refType != null) {
         ref.setRefType(Integer.parseInt(refType));
      }

      ref.setCaption(caption);
      ColumnRef col = new ColumnRef(ref);
      col.setDataType(dtype);

      return col;
   }

   private List<DataRef> getDataRefs(VSAssembly assembly, int index, String dragType,
                                     boolean binding)
   {
      if(assembly instanceof TimeSliderVSAssembly) {
         return Arrays.asList(((TimeSliderVSAssemblyInfo) assembly.getInfo()).getDataRefs());
      }
      else{
         List<DataRef> dataRefs = new ArrayList<>();

         if(assembly instanceof SelectionListVSAssembly) {
            dataRefs.add(((SelectionListVSAssemblyInfo) assembly.getInfo()).getDataRef());
         }
         else if(assembly instanceof SelectionTreeVSAssembly) {
            dataRefs.add(((SelectionTreeVSAssemblyInfo) assembly.getInfo()).getDataRefs()[index]);
         }
         else if(assembly instanceof TableVSAssembly) {
            dataRefs.add(getTableDataRef((TableVSAssembly) assembly, index, binding));
         }
         else if(assembly instanceof CrosstabVSAssembly) {
            dataRefs.add(getCrosstabDataRef((CrosstabVSAssembly) assembly, index, dragType));
         }

         return dataRefs;
      }
   }

   /**
    * Get the source data ref from TableVSAssembly.
    */
   private DataRef getTableDataRef(TableVSAssembly assembly, int index, boolean binding) {
      ColumnSelection cols = assembly.getColumnSelection();

      // binding pane shows invisible columns
      if(binding) {
         return cols.getAttribute(index);
      }

      for(int i = 0, vidx = 0; i < cols.getAttributeCount(); i++) {
         DataRef col = cols.getAttribute(i);

         if(col instanceof ColumnRef && !((ColumnRef) col).isVisible()) {
            continue;
         }

         if(vidx == index) {
            return cols.getAttribute(i);
         }

         vidx++;
      }

      return null;
   }

   private DataRef getCrosstabDataRef(CrosstabVSAssembly assembly, int index, String type) {
      VSCrosstabInfo crosstabInfo = assembly.getVSCrosstabInfo();
      DataRef[] refs = this.getCrosstabDataRefs(crosstabInfo, type);

      if(refs == null || index < 0 || index >= refs.length) {
         return null;
      }

      return new ColumnRef(refs[index]);
   }

   private DataRef[] getCrosstabDataRefs(VSCrosstabInfo crosstabInfo, String type) {
      DataRef[] refs = null;

      switch(type) {
         case "rows":
            refs = crosstabInfo.getRowHeaders();
            break;
         case "cols":
            refs = crosstabInfo.getColHeaders();
            break;
         case "aggregates":
            refs = Arrays.stream(crosstabInfo.getAggregates())
               .map(ref -> ref instanceof XAggregateRef ? new AttributeRef(ref.getName()) : ref)
               .toArray(DataRef[]::new);
            break;
         default:
      }

      return refs;
   }
}
