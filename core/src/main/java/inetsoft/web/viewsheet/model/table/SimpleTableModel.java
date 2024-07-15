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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.EmbeddedTableVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;

import java.util.*;

public abstract class SimpleTableModel<T extends TableVSAssembly> extends BaseTableModel<T> {
   SimpleTableModel(T assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) assembly.getInfo();
      ColumnSelection cols = info.getVisibleColumns();
      int columnSize = cols.getAttributeCount();
      headersSortType = (columnSize > 0) ? new int[columnSize] : null;
      sortPositions = (columnSize > 0) ? new int[columnSize] : null;
      columnEditorEnabled = new boolean[columnSize];
      rowHeights = info.getRowHeights();
      this.editedByWizard = info.isEditedByWizard();
      SortInfo sInfo = assembly.getSortInfo();
      List<SortRef> sorts = sInfo != null ? Arrays.asList(sInfo.getSorts()) : null;

      for(int i = 0; i < columnSize; i++) {
         DataRef ref = cols.getAttribute(i);

         if(ref instanceof ColumnRef) {
            DataRef ref2 = ((ColumnRef) ref).getDataRef();
            String name = ref2.getName();
            colNames.add(name);
            // find and add sort type to the headerSortType if there exist any
            SortRef sortRef = sInfo == null ? null : sInfo.getSort(ref2);
            headersSortType[i] = (sInfo != null && sortRef != null) ? sortRef.getOrder()
               : XConstants.SORT_NONE;
            sortPositions[i] = sorts != null && sortRef != null ?
               sorts.indexOf(sortRef) : -1;
         }

         if(ref instanceof FormRef) {
            FormRef formRef = (FormRef) ref;
            columnEditorEnabled[i] = formRef.getOption().isForm();
         }
      }

      insert = info.isInsert();
      del = info.isDel();
      edit = info.isEdit();
      form = info.isForm();
      writeBack = info.isWriteBack();
      embedded = assembly instanceof EmbeddedTableVSAssembly;
      summary = assembly.isSummaryTable();
      submitOnChange = embedded && ((EmbeddedTableVSAssemblyInfo) info).isSubmitOnChange();

      Viewsheet vs = rvs.getViewsheet();

      // disable editing on data tip and pop
      if(vs.getDataTips().contains(info.getAbsoluteName()) ||
         vs.getFlyoverViews().contains(info.getAbsoluteName()) ||
         vs.getPopComponents().contains(info.getAbsoluteName()))
      {
         form = false;
      }

      formVisible = LicenseManager.isComponentAvailable(LicenseManager.LicenseComponent.FORM);
   }

   public List<String> getColNames() {
      return Collections.unmodifiableList(colNames);
   }

   public int[] getHeadersSortType() {
      return headersSortType;
   }

   public int[] getSortPositions() {
      return sortPositions;
   }

   public boolean isInsert() {
      return insert;
   }

   public boolean isDel() {
      return del;
   }

   public boolean isEdit() {
      return edit;
   }

   public boolean isForm() {
      return form;
   }

   public boolean isEmbedded() {
      return embedded;
   }

   public boolean isSummary() {
      return summary;
   }

   public boolean isWriteBack() {
      return writeBack;
   }

   public void setWriteBack(boolean writeBack) {
      this.writeBack = writeBack;
   }

   public boolean isSubmitOnChange() {
      return submitOnChange;
   }

   public Map<Integer, Double> getRowHeights() {
      return rowHeights;
   }

   private final List<String> colNames = new ArrayList<>();

   public boolean[] getColumnEditorEnabled() {
      return columnEditorEnabled;
   }

   public boolean isEditedByWizard() {
      return editedByWizard;
   }

   public void setEditedByWizard(boolean editedByWizard) {
      this.editedByWizard = editedByWizard;
   }

   public boolean isFormVisible() {
      return formVisible;
   }

   public void setFormVisible(boolean formVisible) {
      this.formVisible = formVisible;
   }

   /*an integer array of sort types for headers*/
   private int[] headersSortType;
   /*an integer array of sort position*/
   private int[] sortPositions;
   private boolean insert;
   private boolean del;
   private boolean edit;
   private boolean form;
   private boolean embedded;
   private boolean summary;
   private boolean writeBack;
   private boolean submitOnChange;
   private boolean[] columnEditorEnabled;
   private Map<Integer, Double> rowHeights;
   private boolean editedByWizard = true; //Not edited by binding
   private boolean formVisible;
}
