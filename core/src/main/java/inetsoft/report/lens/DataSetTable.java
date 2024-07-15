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
package inetsoft.report.lens;

import inetsoft.graph.data.*;
import inetsoft.report.*;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.uql.XMetaInfo;

import java.util.List;

/**
 * The DataSetTable class can be used to create a table corresponding to
 * a data set. It extracts data and header information from the data set.
 * <p>
 * The header can be displayed as plain text.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class DataSetTable extends AttributeTableLens {
   /**
    * Create a chart table from a chart.
    * @param data base chart.
    */
   public DataSetTable(DataSet data) {
      this.data = data;
      setTable(new Table());
   }

   /**
    * Set the embedded dataset.
    */
   public void setDataSet(DataSet data) {
      this.data = data;
   }

   /**
    * Get the embedded dataset.
    */
   public DataSet getDataSet() {
      return data;
   }

   class Table extends AbstractTableLens {
      /**
       * Return the number of rows in the table. The number of rows includes
       * the header rows.
       * @return number of rows in table.
       */
      @Override
      public int getRowCount() {
         if(data == null) {
            return 0;
         }

         return data.getRowCount() + 1;
      }

      /**
       * Return the number of columns in the table. The number of columns
       * includes the header columns.
       * @return number of columns in table.
       */
      @Override
      public int getColCount() {
         if(data == null) {
            return 0;
         }

         return data.getColCount();
      }

      /**
       * Return the number of rows on the top of the table to be treated
       * as header rows.
       * @return number of header rows.
       */
      @Override
      public int getHeaderRowCount() {
         return 1;
      }

      /**
       * Return the number of columns on the left of the table to be
       * treated as header columns.
       */
      @Override
      public int getHeaderColCount() {
         return 0;
      }

      /**
       * Return the value at the specified cell.
       * @param r row number.
       * @param c column number.
       * @return the value at the location.
       */
      @Override
      public Object getObject(int r, int c) {
         if(data == null) {
            return null;
         }

         return r == 0 ? data.getHeader(c) : data.getData(c, r - 1);
      }

      @Override
      public String getColumnIdentifier(int col) {
         return (String) getObject(0, col);
      }

      /**
       * Set the cell value.
       * @param r row number.
       * @param c column number.
       * @param v cell value.
       */
      @Override
      public void setObject(int r, int c, Object v) {
         throw new RuntimeException("Not implemented method: setObject");
      }

      /**
       * Get internal table data descriptor which contains table structural
       * infos.
       * @return table data descriptor.
       */
      @Override
      public TableDataDescriptor getDescriptor() {
         if(descriptor == null) {
            descriptor = new DefaultTableDataDescriptor(this) {
               /**
                * Check if contains drill.
                * @return <tt>true</tt> if contains drill,
                * <tt>false</tt> otherwise
                */
               @Override
               public boolean containsDrill() {
                  TableLens table = getVSDataSetTableLens();
                  return table != null && table.getDescriptor().containsDrill();
               }

               @Override
               public boolean containsFormat() {
                  TableLens table = getVSDataSetTableLens();
                  return table != null && table.getDescriptor().containsFormat();
               }

               @Override
               public TableDataPath getCellDataPath(int row, int col) {
                  TableLens table = getVSDataSetTableLens();

                  if(table == null) {
                     return null;
                  }

                  // since the base VSDataSet is used for descriptor (see getVSDataSetTableLens),
                  // the number of rows may be less than the FullProjectedDataSet rows. in case
                  // it's exceeded, just use the last row. (52149)
                  if(!table.moreRows(row)) {
                     row = table.getRowCount() - 1;
                  }

                  if(table.getColCount() <= col && col  < data.getColCount()) {
                     return super.getCellDataPath(row, col);
                  }

                  return table.getDescriptor().getCellDataPath(row, col);
               }

               /**
                * Get meta info of a specified table data path.
                * @param path the specified table data path
                * @return meta info of the table data path
                */
               @Override
               public XMetaInfo getXMetaInfo(TableDataPath path) {
                  TableLens table = getVSDataSetTableLens();

                  if(table == null) {
                     return null;
                  }

                  return table.getDescriptor().getXMetaInfo(path);
               }

               private TableLens getVSDataSetTableLens() {
                  if(vset != null) {
                     return vset.getTable();
                  }

                  if(data instanceof FullProjectedDataSet) {
                     for(DataSet subDataSet : ((FullProjectedDataSet) data).getSubDataSets()) {
                        vset = findVSDataSet(subDataSet);

                        if(vset != null) {
                           break;
                        }
                     }
                  }
                  else {
                     vset = findVSDataSet(data);
                  }

                  if(vset == null) {
                     return null;
                  }

                  return vset.getTable();
               }

               private VSDataSet findVSDataSet(DataSet data) {
                  DataSet currDataSet = data;

                  while(true) {
                     if(currDataSet instanceof VSDataSet) {
                        return (VSDataSet) currDataSet;
                     }
                     else if(currDataSet instanceof DataSetFilter) {
                        currDataSet = ((DataSetFilter) currDataSet).getDataSet();
                     }
                     else if(currDataSet instanceof FullProjectedDataSet) {
                        for(DataSet subDataSet : ((FullProjectedDataSet) currDataSet).getSubDataSets()) {
                           currDataSet = findVSDataSet(subDataSet);

                           if(currDataSet != null) {
                              break;
                           }
                        }
                     }
                     else {
                        break;
                     }
                  }

                  return null;
               }

               @Override
               public List<TableDataPath> getXMetaInfoPaths() {
                  if(vset == null) {
                     return null;
                  }

                  TableLens table = vset.getTable();
                  return table.getDescriptor().getXMetaInfoPaths();
               }

               VSDataSet vset = null;
            };
         }

         return descriptor;
      }
   }

   private DataSet data;
}
