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
package inetsoft.uql;

import inetsoft.report.TableDataDescriptor;

import java.io.Serializable;

/**
 * XTable provides the API for accessing table data. It is similar to
 * XTableNode but is more report oriented, and serves to provide
 * more direct access without row iteration. The interface is used in
 * the report engine.
 *
 * @version 6.1
 * @author InetSoft Technology Corp
 */
public interface XTable extends Serializable, Cloneable {
   /**
    * End of table flag for method moreRows.
    */
   int EOT = Integer.MAX_VALUE;

   /**
    * Name of the specified report/viewsheet which this table lens was created for.
    * and will be used when insert post processing record for execution_breakdown audit table.
    */
   String REPORT_NAME = "report_name";

   /**
    * Type of the specified report which this table lens was created for.
    * ExecutionBreakDownRecord.OBJECT_TYPE_REPORT or
    * ExecutionBreakDownRecord.OBJECT_TYPE_VIEWSHEET
    * and will be used when insert post processing record for execution_breakdown audit table.
    */
   String REPORT_TYPE = "report_type";

   /**
    * Check if there are more rows. The row index is the row that will be
    * accessed. This method must block until the row is available, or
    * return false if the row does not exist in the table. This method is
    * used to iterate through the table, and allow partial table to be
    * accessed in report processing.
    * @param row row number. If EOT is passed in, this method should wait
    * until the table is fully loaded.
    * @return true if the row exists, or false if no more rows.
    */
   boolean moreRows(int row);

   /**
    * Return the number of rows in the table. The number of rows includes
    * the header rows. If the table is loading in background and loading
    * is not done, return the negative number of loaded rows minus 1.
    * @return number of rows in table.
    */
   int getRowCount();

   /**
    * Return the number of columns in the table. The number of columns
    * includes the header columns.
    * @return number of columns in table.
    */
   int getColCount();

   /**
    * Return the number of rows on the top of the table to be treated
    * as header rows.
    * @return number of header rows.  Default is 1.
    */
   int getHeaderRowCount();

   /**
    * Return the number of columns on the left of the table to be
    * treated as header columns.
    */
   int getHeaderColCount();

   /**
    * Return the number of rows on the bottom of the table to be treated
    * as trailer rows.
    * @return number of header rows.
    */
   int getTrailerRowCount();

   /**
    * Return the number of columns on the right of the table to be
    * treated as trailer columns.
    */
   int getTrailerColCount();

   /**
    * Check if the value at one cell is null.
    * @param r the specified row index.
    * @param c column number.
    * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
    */
   boolean isNull(int r, int c);

   /**
    * Return the value at the specified cell.
    * @param r row number.
    * @param c column number.
    * @return the value at the location.
    */
   Object getObject(int r, int c);

   /**
    * Get the double value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the double value in the specified row.
    */
   double getDouble(int r, int c);

   /**
    * Get the float value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the float value in the specified row.
    */
   float getFloat(int r, int c);

   /**
    * Get the long value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the long value in the specified row.
    */
   long getLong(int r, int c);

   /**
    * Get the int value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the int value in the specified row.
    */
   int getInt(int r, int c);

   /**
    * Get the short value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the short value in the specified row.
    */
   short getShort(int r, int c);

   /**
    * Get the byte value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the byte value in the specified row.
    */
   byte getByte(int r, int c);

   /**
    * Get the boolean value in one row.
    * @param r the specified row index.
    * @param c column number.
    * @return the boolean value in the specified row.
    */
   boolean getBoolean(int r, int c);

   /**
    * Set the cell value. For table filters, the setObject() call should
    * be forwarded to the base table if possible. An implementation should
    * throw a runtime exception if this method is not supported. In that
    * case, data in a table can not be modified in scripts.
    * @param r row number.
    * @param c column number.
    * @param v cell value.
    */
   void setObject(int r, int c, Object v);

   /**
    * Set if need to disabled fireevent.
    */
   default void setDisableFireEvent(boolean disableFireEvent) {
   }

   /**
    * Check if fire event is disabled.
    */
   default boolean isDisableFireEvent() {
      return false;
   }

   /**
    * Get the current column content type.
    * @param col column number.
    * @return column type.
    */
   Class<?> getColType(int col);

   /**
    * Check if is primitive.
    * @return <tt>true</tt> if is primitive, <tt>false</tt> otherwise.
    */
   boolean isPrimitive(int col);

   /**
    * Dispose the table to clear up temporary resources.
    */
   void dispose();

   /**
    * Get the column identifier of a column.
    * @param col the specified column index.
    * @return the column identifier of the column. The identifier might be
    * different from the column name, for it may contain more locating
    * information than the column name.
    */
   String getColumnIdentifier(int col);

   /**
    * Set the column identifier of a column.
    * @param col the specified column index.
    * @param identifier the column identifier of the column. The identifier
    * might be different from the column name, for it may contain more
    * locating information than the column name.
    */
   void setColumnIdentifier(int col, String identifier);

   /**
    * Get internal table data descriptor which contains table structural
    * infos.
    * @return table data descriptor.
    */
   TableDataDescriptor getDescriptor();

   /**
    * Get the value of a property.
    * @param key the specified property name.
    * @return the value of the property.
    */
   default Object getProperty(String key) {
      // should override if needed.
      return null;
   }

   /**
    * Set the value a property.
    * @param key the property name.
    * @param value the property value, null to remove the property.
    */
   default void setProperty(String key, Object value) {
      // should override if needed.
   }

   /**
    * @return the report/vs name which this filter was created for,
    * and will be used when insert audit record.
    */
   default String getReportName() {
      // should override if needed.
      return null;
   }

   /**
    * @return the report type which this filter was created for:
    * ExecutionBreakDownRecord.OBJECT_TYPE_REPORT or
    * ExecutionBreakDownRecord.OBJECT_TYPE_VIEWSHEET
    */
   default String getReportType() {
      // should override if needed.
      return null;
   }
}
