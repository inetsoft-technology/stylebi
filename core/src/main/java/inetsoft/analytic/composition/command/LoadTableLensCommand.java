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
package inetsoft.analytic.composition.command;

import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.command.GridCommand;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.AggregateInfo;
import inetsoft.uql.asset.internal.AssemblyInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Encoder;
import inetsoft.util.script.ScriptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Map;

/**
 * Load table data command.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class LoadTableLensCommand extends ViewsheetCommand {
   /**
    * Constructor.
    */
   public LoadTableLensCommand() {
      super();
   }

   /**
    * Constructor.
    */
   public LoadTableLensCommand(String name, VSTableLens embedded, int mode,
                               int start, int num, boolean completed,
                               VSAssemblyInfo info) {
      this();
      this.embedded = embedded;
      this.start = start;
      this.num = num;
      this.info = info;

      put("name", name);
      put("mode", "" + mode);
      put("start", "" + start);
      put("num", "" + num);
      put("completed", "" + completed);

      // compact info size
      VSAssemblyInfo info0 = (VSAssemblyInfo) info.clone();
      clearCubeAggregates(info0);

      embedded.initTableGrid(info);
      put("info", info0);

      if(info instanceof TableVSAssemblyInfo) {
         ColumnSelection cs = ((TableVSAssemblyInfo) info).getColumnSelection();
         int attrCount = cs.getAttributeCount();
         boolean isAllColumnForm = true;

         for(int i = 0; i < attrCount; i++) {
            DataRef ref = cs.getAttribute(i);

            if(ref instanceof FormRef) {
               ColumnOption cp = ((FormRef) ref).getOption();

               if(!cp.isForm()) {
                  isAllColumnForm = false;

                  break;
               }
            }
         }

         TableVSAssemblyInfo tinfo = (TableVSAssemblyInfo) info;
         this.embedded.setHyperlinkEnabed(!(tinfo.isForm() && isAllColumnForm));
      }
   }

   /**
    * Clear aggregate info but not expression measure.
    */
   private void clearCubeAggregates(VSAssemblyInfo info) {
      if(!(info instanceof CrossBaseVSAssemblyInfo)) {
         return;
      }

      VSCrosstabInfo cinfo =
         ((CrossBaseVSAssemblyInfo) info).getVSCrosstabInfo();

      if(cinfo == null) {
         return;
      }

      AggregateInfo ainfo = cinfo.getAggregateInfo();

      if(!isCube(ainfo)) {
         return;
      }

      ainfo.clear();
   }

   /**
    * Check whether the binding is cube ref.
    */
   private boolean isCube(AggregateInfo ainfo) {
      return ainfo != null && ((ainfo.getGroupCount() > 0 &&
         (ainfo.getGroup(0).getRefType() & DataRef.CUBE) != 0) ||
         (ainfo.getAggregateCount() > 0 &&
         (ainfo.getAggregate(0).getRefType() & DataRef.CUBE) != 0));
   }

   /**
    * Write contents.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(embedded != null) {
         writer.println("<embedded>");
         writer.print("<![CDATA[");
         Encoder.writeAsciiHex(writer, convertObject(embedded));
         writer.println("]]>");
         writer.println("</embedded>");
      }
   }

   /**
    * Write contents.
    */
   @Override
   protected void writeContents2(DataOutputStream dos) {
      try {
         dos.writeBoolean(embedded == null);

         if(embedded != null) {
            embedded.writeData(dos, start, num);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to write load table lens command", ex);
      }
   }

   /**
    * Convert an object to a byte array.
    * @return byte array.
    */
   private byte[] convertObject(VSTableLens embedded) {
      try {
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         DataOutputStream dos = new DataOutputStream(baos);
         embedded.writeData(dos, start, num);
         return baos.toByteArray();
      }
      catch(IOException ex) {
         LOG.error("An I/O error prevented the table from being encoded", ex);
      }
      catch(ScriptException ex) {
         LOG.error("A script error prevented the table from being encoded", ex);
      }

      return new byte[0];
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, </tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof LoadTableLensCommand)) {
         return false;
      }

      // egnore num to replace wrong num command
      Map emap1 = (Map) map2.clone();
      Map emap2 = (Map) ((LoadTableLensCommand) obj).map2.clone();
      emap1.remove("num");
      emap2.remove("num");

      if(!emap1.equals(emap2)) {
         return false;
      }

      AssemblyInfo info = getAssemblyInfo(this);
      GridCommand cmd2 = (GridCommand) obj;
      AssemblyInfo info2 = getAssemblyInfo(cmd2);

      return !(info == null || info2 == null ||
         info.getAbsoluteName() == null || info2.getAbsoluteName() == null) &&
         info.getAbsoluteName().equals(info2.getAbsoluteName());
   }

   /**
    * Get the table lens.
    */
   public VSTableLens getTable() {
      return this.embedded;
   }

   private VSTableLens embedded;
   private int start = 0;
   private int num = 0;
   @SuppressWarnings({"UnusedDeclaration", "FieldCanBeLocal"})
   private VSAssemblyInfo info;

   private static final Logger LOG =
      LoggerFactory.getLogger(LoadTableLensCommand.class);
}
