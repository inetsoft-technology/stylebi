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
package inetsoft.graph.treeviz;

import javax.swing.*;

/**
 * ProgressObserver.
 *
 * @author Werner Randelshofer
 * @version 1.1 2007-11-19 Added method setWarning and setError.
 * <br>1.0 September 18, 2006 Created.
 */
public interface ProgressObserver {

   BoundedRangeModel getModel();

   void setModel(BoundedRangeModel brm);

   /**
    * Set cancelable to false if the operation can not be canceled.
    */
   void setCancelable(boolean b);

   /**
    * The specified Runnable is executed when the user presses
    * the cancel button.
    */
   void setDoCancel(Runnable doCancel);

   /**
    * Returns the progress of the operation being monitored.
    */
   int getProgress();

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
   void setProgress(int nv);

   /**
    * Returns the minimum value -- the lower end of the progress value.
    *
    * @return an int representing the minimum value
    *
    * @see #setMinimum
    */
   int getMinimum();


   /**
    * Specifies the minimum value.
    *
    * @param m an int specifying the minimum value
    *
    * @see #getMinimum
    */
   void setMinimum(int m);


   /**
    * Returns the maximum value -- the higher end of the progress value.
    *
    * @return an int representing the maximum value
    *
    * @see #setMaximum
    */
   int getMaximum();


   /**
    * Specifies the maximum value.
    *
    * @param m an int specifying the maximum value
    *
    * @see #getMaximum
    */
   void setMaximum(int m);

   /**
    * Returns true if the progress observer is set to indeterminate.
    */
   boolean isIndeterminate();

   /**
    * Sets the progress observer to indeterminate.
    */
   void setIndeterminate(boolean newValue);

   /**
    * Indicate that the operation is complete.  This happens automatically
    * when the value set by setProgress is >= max, but it may be called
    * earlier if the operation ends early.
    */
   void complete();

   /**
    * Returns true if the operation is completed.
    */
   boolean isCompleted();


   /**
    * Cancels the operation.
    * This method must be invoked from the user event dispatch thread.
    */
   void cancel();

   /**
    * Returns true if the user has hit the Cancel button in the progress dialog.
    */
   boolean isCanceled();

   /**
    * Closes the progress observer.
    */
   void close();

   /**
    * Returns true if the progress observer is closed.
    */
   boolean isClosed();

   /**
    * Specifies the additional note that is displayed along with the
    * progress message.
    *
    * @return a String specifying the note to display
    *
    * @see #setNote
    */
   String getNote();

   /**
    * Specifies the additional note that is displayed along with the
    * progress message. Used, for example, to show which file
    * is currently being copied during a multiple-file copy.
    *
    * @param note a String specifying the note to display
    *
    * @see #getNote
    */
   void setNote(String note);

   /**
    * Specifies the warning message that is displayed along with the
    * progress message.
    *
    * @return a String specifying the message to display, or null if
    * there is no warning.
    */
   String getWarning();

   /**
    * Specifies the additional warning message that is displayed along with the
    * progress message. Used, for example, to show which files couldn't
    * be copied during a multiple-file copy..
    *
    * @param message a String specifying the message to display, or null
    *                if there is no warning.
    *
    * @see #getWarning
    */
   void setWarning(String message);

   /**
    * Specifies the error message that is displayed along with the
    * progress message.
    *
    * @return a String specifying the message to display, or null if
    * there is no error.
    */
   String getError();

   /**
    * Specifies the additional error message that is displayed along with the
    * progress message. Used, for example, to show which files couldn't
    * be copied during a multiple-file copy..
    *
    * @param message a String specifying the message to display, or null
    *                if there is no error.
    *
    * @see #getWarning
    */
   void setError(String message);
}
