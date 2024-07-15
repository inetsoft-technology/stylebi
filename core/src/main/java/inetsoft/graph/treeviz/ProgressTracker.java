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
package inetsoft.graph.treeviz;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * ProgressTracker implements ProgressObserver without providing visual
 * components for the user.
 *
 * @author Werner Randelshofer
 * @version 2.0 2009-03-23 Updated to new ProgressObserver interface.
 * <br>1.0 2008-07-05 Created.
 */
public class ProgressTracker implements ProgressObserver {
   private String message, note, warning, error;
   private boolean isCanceled, isClosed, isCancelable = true, isIndeterminate, isCompleted;
   private javax.swing.BoundedRangeModel model;
   private Runnable doCancel;
   private ChangeListener changeHandler = new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
         if(model != null && model.getValue() >= model.getMaximum()) {
            close();
         }
      }
   };

   public ProgressTracker() {
   }

   /**
    * Creates new form ProgressTracker
    */
   public ProgressTracker(String message, String note, int min, int max) {
      setModel(new javax.swing.DefaultBoundedRangeModel(min, 0, min, max));
      this.message = message;
      this.note = note;
   }

   /**
    * Creates new form ProgressTracker
    */
   public ProgressTracker(String message, String note) {
      setModel(new javax.swing.DefaultBoundedRangeModel(0, 0, 0, 1));
      this.message = message;
      this.note = note;
      this.isIndeterminate = true;
   }

   public BoundedRangeModel getModel() {
      return model;
   }

   public void setModel(BoundedRangeModel brm) {
      if(model != null) {
         model.removeChangeListener(changeHandler);
      }
      model = brm;
      if(model != null) {
         model.addChangeListener(changeHandler);
      }
   }

   /**
    * Set cancelable to false if the operation can not be canceled.
    */
   public void setCancelable(final boolean b) {
      isCancelable = b;
   }

   /**
    * The specified Runnable is executed when the user presses
    * the cancel button.
    */
   public void setDoCancel(Runnable doCancel) {
      this.doCancel = doCancel;
   }

   /**
    * Returns the progress of the operation being monitored.
    */
   public int getProgress() {
      return model != null ? model.getValue() : 0;
   }

   /**
    * Indicate the progress of the operation being monitored.
    * If the specified value is >= the maximum, the progress
    * monitor is closed.
    *
    * @param nv an int specifying the current value, between the
    *           maximum and minimum specified for this component
    *
    * @see #setMinimum
    * @see #setMaximum
    * @see #close
    */
   public void setProgress(int nv) {
      if(model != null) {
         model.setValue(nv);
      }
   }

   /**
    * Indicate that the operation is complete.  This happens automatically
    * when the value set by setProgress is >= max, but it may be called
    * earlier if the operation ends early.
    */
   public void close() {
      if(!isClosed) {
         isClosed = true;
         if(model != null) {
            model.removeChangeListener(changeHandler);
         }
      }
   }

   /**
    * Returns the minimum value -- the lower end of the progress value.
    *
    * @return an int representing the minimum value
    *
    * @see #setMinimum
    */
   public int getMinimum() {
      return model != null ? model.getMinimum() : 0;
   }

   /**
    * Specifies the minimum value.
    *
    * @param m an int specifying the minimum value
    *
    * @see #getMinimum
    */
   public void setMinimum(int m) {
      if(model != null) {
         model.setMinimum(m);
      }
   }

   /**
    * Returns the maximum value -- the higher end of the progress value.
    *
    * @return an int representing the maximum value
    *
    * @see #setMaximum
    */
   public int getMaximum() {
      return model != null ? model.getMaximum() : 1;
   }

   /**
    * Specifies the maximum value.
    *
    * @param m an int specifying the maximum value
    *
    * @see #getMaximum
    */
   public void setMaximum(int m) {
      if(model != null) {
         model.setMaximum(m);
      }
   }

   /**
    * Returns true if the user has hit the Cancel button in the progress dialog.
    */
   public boolean isCanceled() {
      return isCanceled;
   }

   /**
    * Returns true if the ProgressTracker is closed.
    */
   public boolean isClosed() {
      return isClosed;
   }

   /**
    * Cancels the operation.
    * This method must be invoked from the user event dispatch thread.
    */
   public void cancel() {
      if(isCancelable) {
         isCanceled = true;
         note = "Canceling...";
         if(doCancel != null) {
            doCancel.run();
         }
      }
      else {
         note = "This process can not be canceled!";
      }
   }

   /**
    * Specifies the additional note that is displayed along with the
    * progress message.
    *
    * @return a String specifying the note to display
    *
    * @see #setNote
    */
   public String getNote() {
      return note;
   }

   /**
    * Specifies the additional note that is displayed along with the
    * progress message. Used, for example, to show which file the
    * is currently being copied during a multiple-file copy.
    *
    * @param note a String specifying the note to display
    *
    * @see #getNote
    */
   public void setNote(String note) {
      if(!isCanceled) {
         this.note = note;
      }
   }

   public boolean isIndeterminate() {
      return isIndeterminate;
   }

   public void setIndeterminate(boolean newValue) {
      this.isIndeterminate = newValue;

   }

   public void complete() {
      isCompleted = true;
   }

   public boolean isCompleted() {
      return isCompleted;
   }

   public String getWarning() {
      return warning;
   }

   public void setWarning(String message) {
      warning = message;
   }

   public String getError() {
      return error;
   }

   public void setError(String message) {
      error = message;
   }

}
