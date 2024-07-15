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
package inetsoft.report.composition.region;

import inetsoft.util.DataSerializable;

import java.awt.geom.Rectangle2D;
import java.io.*;

/**
 * SplitContainer is a container to process all ui area's index area.
 * When those ui's image is too large, to split them as index, and the
 * SplitContainer will process all index.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class SplitContainer implements DataSerializable {
   /**
    * Default Constructor, to create a default IndexArea.
    */
   public SplitContainer() {
      super();
   }

   /**
    * Constructor, to create a IndexArea with the params.
    * @param rect the bounds of the container.
    */
   public SplitContainer(Rectangle2D rect) {
      process(rect);
   }

   /**
    * Get the index's row count.
    * @return the row count.
    */
   public int getRows() {
      return rows;
   }

   /**
    * Set the index's row count.
    * @param rows the row count.
    */
   public void setRows(int rows) {
      this.rows = rows;
   }

   /**
    * Get the index's column count.
    * @return the column count.
    */
   public int getCols() {
      return cols;
   }

   /**
    * Set the index's col count.
    * @param cols the col count.
    */
   public void setCols(int cols) {
      this.cols = cols;
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    * @throws IOException
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      output.writeInt(rows);
      output.writeInt(cols);
   }

   /**
    * Parse data from an InputStream.
    * @param input the source DataInputStream.
    * @retrun <tt>true</tt> if successfully parsed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean parseData(DataInputStream input) {
      return true;
   }

   /**
    * Caculate the rows and columns of the container, and to create IndexArea.
    * @param rect the bounds of the container.
    */
   private void process(Rectangle2D rect) {
      this.rows =
         (int) Math.ceil(rect.getHeight() / ChartArea.MAX_IMAGE_SIZE);
      this.cols =
         (int) Math.ceil(rect.getWidth() / ChartArea.MAX_IMAGE_SIZE);
   }

   private int rows = 1;
   private int cols = 1;
}
