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
package inetsoft.analytic.composition.event;

import inetsoft.analytic.composition.ViewsheetEvent;
import inetsoft.mv.MVManager;
import inetsoft.report.composition.AssetCommand;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.command.MessageCommand;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.util.log.LogLevel;

/**
 * Check if MV is pending for the viewsheet.
 *
 * @version 11.1
 * @author InetSoft Technology Corp
 */
public class CheckMVEvent extends ViewsheetEvent {
   /**
    * Constructor.
    */
   public CheckMVEvent() {
      super();
   }

   /**
    * Constructor.
    */
   public CheckMVEvent(AssetEntry entry) {
      this();
      put("entry", entry);
   }

   /**
    * Check if is undoable/redoable.
    * @return <tt>true</tt> if undoable/redoable.
    */
   @Override
   public boolean isUndoable() {
      return false;
   }

   /**
    * Check if requires return.
    * @return <tt>true</tt> if requires, <tt>false</tt> otherwise.
    */
   @Override
   public boolean requiresReturn() {
      return false;
   }

   /**
    * Get the influenced assemblies.
    * @return the influenced assemblies, <tt>null</tt> means all.
    */
   @Override
   public String[] getAssemblies() {
      return null;
   }

   /**
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   @Override
   public String getName() {
      return Catalog.getCatalog().getString("CheckMV");
   }

   /**
    * Process this viewsheet event.
    * @param ws the specified runtime viewsheet as the context.
    * @param command the specified command container.
    */
   @Override
   public void process(RuntimeViewsheet rvs, AssetCommand command)
      throws Exception
   {
      AssetEntry entry = (AssetEntry) get("entry");
      MVManager mgr = MVManager.getManager();
      boolean required = "true".equals(SreeEnv.getProperty("mv.required"));
      boolean metadata = "true".equals(SreeEnv.getProperty("mv.metadata"));
      boolean background = "true".equals(get("BACKGROUND"));
      boolean waitfor = "true".equals(get("waitfor"));
      boolean bgChanged = false;
      boolean refreshDirectly = "true".equals(get("refresh.directly"));
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return;
      }

      // wait for event will be fired after background fired, we cannot make
      // sure which event will be run first, also, the wait for only fire one
      // time, here we just let it run
      /*
      // already waiting for the background mv, ignore the second event
      if(waitfor && "true".equals(vs.getBaseEntry().getProperty("mv_background"))) {
         return;
      }
      */

      VSRefreshEvent refresh = new VSRefreshEvent();
      refresh.setID(getID());
      refresh.setLinkURI(getLinkURI());
      refresh.put("initing", "false");

      if(refreshDirectly) {
         refresh.process(rvs, command);
         return;
      }

      // wait for is not background
      if(background) {
         vs.getViewsheetInfo().setMetadata(true);
         vs.getBaseEntry().setProperty("mv_background", "true");
      }
      // wait for is not confirmed
      else if(isConfirmed()) {
         ViewsheetSandbox box = rvs.getViewsheetSandbox();

         if(box == null) {
            return;
         }

         mgr.cancelMV(box.getAssetEntry(), (XPrincipal) getUser());

         if("true".equals(vs.getBaseEntry().getProperty("mv_background"))) {
            vs.getBaseEntry().setProperty("mv_background", null);
            bgChanged = true;
         }

         if(rvs.isRuntime()) {
            if(required) {
               rvs.setProperty("cancelled", "true");
               String msg = Catalog.getCatalog().getString("vs.mv.missing");
               throw new MessageException(msg, LogLevel.ERROR);
            }
            else {
               box.setMVDisabled(true);
            }
         }
         else if(metadata) {
            vs.getViewsheetInfo().setMetadata(true);
         }
         else {
            box.setMVDisabled(true);
         }
      }
      else {
         // wait until MV is done
         while(mgr.isPending(entry, (XPrincipal) getUser())) {
            // mv creation in background, the original wait should be canceled
            if(!waitfor && !rvs.isRuntime() &&
               vs.getViewsheetInfo().isMetadata())
            {
               return;
            }

            Thread.sleep(200);
         }

         // prompt user to refresh when background mv is created
         if(waitfor) {
            vs.getViewsheetInfo().setMetadata(false);
            vs.getBaseEntry().setProperty("mv_background", null);

            MessageCommand mcmd = new MessageCommand(
               Catalog.getCatalog().getString("vs.mv.complete"),
               MessageCommand.CONFIRM);
            mcmd.addEvent(refresh);
            command.addCommand(mcmd);
         }
         else {
            // cancel progress
            command.addCommand(new MessageCommand((String) null,
                                                  MessageCommand.PROGRESS));
         }
      }

      rvs.clearNotHitMVInfo();

      if(!"true".equals(rvs.getProperty("cancelled")) && !waitfor || bgChanged) {
         // force refresh since the data may have changed
         command.put("NO_SHRINK", "true");
         refresh.process(rvs, command);
      }
   }
}
