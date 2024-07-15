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
package inetsoft.report.io;

import inetsoft.report.DocumentInfo;
import inetsoft.report.event.ProgressEvent;
import inetsoft.report.event.ProgressListener;
import inetsoft.report.internal.paging.ReportCache;

import java.io.OutputStream;
import java.util.Vector;

/**
 * This interface is the common interface for all report export generators.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public abstract class AbstractGenerator implements Generator {
   /**
    * Set the document info to export.
    */
   @Override
   public void setDocumentInfo(DocumentInfo info) {
      this.docInfo = info;
   }

   /**
    * Set the output stream of this generator.
    */
   @Override
   public void setOutput(OutputStream output) {
      this.output = output;
   }

   /**
    * Get the output stream used by the generator.
    */
   @Override
   public OutputStream getOutput() {
      return output;
   }

   /**
    * Add a listener to be notified of export progress.
    */
   @Override
   public void addProgressListener(ProgressListener listener) {
      listeners.add(listener);
   }

   /**
    * Remove a listener.
    */
   @Override
   public void removeProgressListener(ProgressListener listener) {
      listeners.removeElement(listener);
   }

   /**
    * Set report cache;
    */
   @Override
   public void setReportCache(ReportCache repcache) {
      this.repcache = repcache;
   }

   /**
    * Get report cache;
    */
   @Override
   public ReportCache getReportCache() {
      return repcache;
   }

   /**
    * Set report ID;
    */
   @Override
   public void setReportId(Object repId) {
      this.repId = repId;
   }

   /**
    * Get report ID;
    */
   @Override
   public Object getReportId() {
      return repId;
   }

   /**
    * Cancel the generation if one if on going.
    */
   @Override
   public void cancel() {
      cancelled = true;
   }

   /**
    * Fire a progress event.
    */
   protected void fireProgressEvent(int current) {
      ProgressEvent ev = new ProgressEvent(this, current);

      for(int i = 0; i < listeners.size(); i++) {
         ((ProgressListener) listeners.get(i)).valueChanged(ev);
      }
   }

   protected DocumentInfo docInfo;
   private Vector listeners = new Vector();
   private OutputStream output;
   private ReportCache repcache;
   private Object repId;
   protected boolean cancelled = false;
}
