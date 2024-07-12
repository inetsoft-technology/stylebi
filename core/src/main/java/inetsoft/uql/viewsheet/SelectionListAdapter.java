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
package inetsoft.uql.viewsheet;

import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.CustomDataSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * SelectionListAdapter stores selection list values for vshtml.
 *
 * @version 11.5
 * @author InetSoft Technology Corp
 */
public class SelectionListAdapter implements CustomDataSerializable {
   /**
    * Constructor.
    */
   public SelectionListAdapter(SelectionBaseVSAssemblyInfo info) {
      this.info = info;
   }

   /**
    * Parse data from an InputStream.
    * @param input the source DataInputStream.
    * @retrun <tt>true</tt> if successfully parsed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean parseData(DataInputStream input) {
      // do nothing
      return false;
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    * @throws IOException
    */
   @Override
   public void writeData(DataOutputStream output) throws IOException {
      SelectionList list = getSelectionList();
      String search = info.getSearchString();
      list = list == null ? new SelectionList() : list;

      if(list != null) {
         list = search != null && search.length() > 0 ?
            list.findAll(search, false) : list;
         writeData0(output, list);
      }
   }

   /**
    * Write data to a DataOutputStream.
    * @param output the destination DataOutputStream.
    * @param list the specified SelectionList.
    * @throws IOException
    */
   protected void writeData0(DataOutputStream output, SelectionList list)
      throws IOException
   {
      list.writeData(output, 0, 0, 100, getMaxCount(list), false);
   }

   /**
    * Get the max displayed selection value count.
    */
   protected int getMaxCount(SelectionList list) {
      return info instanceof SelectionListVSAssemblyInfo ? 2000 :
         list.getSelectionValueCount();
   }

   /**
    * Get related selection list.
    */
   private SelectionList getSelectionList() {
      if(info instanceof SelectionListVSAssemblyInfo) {
         return ((SelectionListVSAssemblyInfo) info).getSelectionList();
      }
      else if(info instanceof SelectionTreeVSAssemblyInfo) {
         SelectionTreeVSAssemblyInfo tinfo = (SelectionTreeVSAssemblyInfo) info;
         CompositeSelectionValue sv = tinfo.getCompositeSelectionValue();
         return sv.getSelectionList();
      }

      return null;
   }

   /**
    * Get the class object for the serialized object.
    */
   @Override
   public Class getSerializedClass() {
      return SelectionList.class;
   }

   private SelectionBaseVSAssemblyInfo info;

   private static final Logger LOG =
      LoggerFactory.getLogger(SelectionList.class);
}
