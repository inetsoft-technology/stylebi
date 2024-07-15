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
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.sree.security.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.graphics.SVGSupport;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.security.Principal;
import java.util.List;
import java.util.*;

@Service
public class VSObjectService {
   @Autowired
   public VSObjectService(PlaceholderService placeholderService,
                          ViewsheetService viewsheetService,
                          SecurityEngine securityEngine)
   {
      this.placeholderService = placeholderService;
      this.viewsheetService = viewsheetService;
      this.securityEngine = securityEngine;
   }

   public boolean isSecurityEnabled() {
      SecurityProvider securityProvider = securityEngine.getSecurityProvider();

      return securityProvider != null && !securityProvider.isVirtual();
   }

   /**
    * Return the runtime viewsheet associated with the current runtimeID and user
    *
    * @param runtimeId the runtime identifier of the viewsheet.
    * @param principal the current user
    *
    * @return the runtime viewsheet instance
    */
   public RuntimeViewsheet getRuntimeViewsheet(String runtimeId, Principal principal)
      throws Exception
   {
      return viewsheetService.getViewsheet(runtimeId, principal);
   }

   /**
    * Add or delete a VSObject
    *
    * @param rvs        the runtime viewsheet
    * @param assembly   the assembly
    * @param dispatcher the command dispatcher
    */
   public void addDeleteVSObject(final RuntimeViewsheet rvs,
                                 final VSAssembly assembly,
                                 final CommandDispatcher dispatcher) throws Exception
   {
      placeholderService.addDeleteVSObject(rvs, assembly, dispatcher);
   }

   /**
    * Execute the runtime viewsheet with a given hint
    *
    * @param rvs        the runtime viewsheet to execute
    * @param name       the assembly to process the change on
    * @param hint       the hint for what changed
    * @param dispatcher the command dispatcher
    */
   public void execute(final RuntimeViewsheet rvs,
                       final String name,
                       final String linkUri,
                       final int hint,
                       final CommandDispatcher dispatcher) throws Exception
   {
      placeholderService.execute(rvs, name, linkUri, hint, dispatcher);
   }


   /**
    * Execute the runtime viewsheet with a given changed assembly list
    *
    * @param rvs        the runtime viewsheet to execute
    * @param name       the assembly that triggered the execute
    * @param clist      the changed assembly list
    * @param dispatcher the command dispatcher
    * @param included   whether to recursively execute other assemblies
    */
   public void execute(final RuntimeViewsheet rvs,
                       final String name,
                       final String linkUri,
                       final ChangedAssemblyList clist,
                       final CommandDispatcher dispatcher,
                       final boolean included) throws Exception
   {
      placeholderService.execute(rvs, name, linkUri, clist, dispatcher, included);
   }

   /**
    * Re-layout viewsheet objects
    *
    * @param rvs        the runtime viewsheet
    * @param linkUri    the link URI
    * @param dispatcher the command dispatcher
    */
   public void layoutViewsheet(final RuntimeViewsheet rvs,
                               final String linkUri,
                               final CommandDispatcher dispatcher) throws Exception
   {
      placeholderService.layoutViewsheet(rvs, rvs.getID(), linkUri, dispatcher);
   }

   /**
    * Refresh the viewsheet
    *
    * @param rvs        the runtime viewsheet to refresh
    * @param linkUri    the link URI
    * @param dispatcher the command dispatcher
    */
   public void refreshViewsheet(final RuntimeViewsheet rvs, int width, int height,
                                final String linkUri,
                                final CommandDispatcher dispatcher) throws Exception
   {
      ChangedAssemblyList clist = placeholderService.createList(true, dispatcher, rvs, linkUri);
      placeholderService.refreshViewsheet(
         rvs, rvs.getID(), linkUri, width, height, false, null, dispatcher, false, true, true,
         clist);
   }

   /**
    * Refresh the viewsheet
    *
    * @param rvs        the runtime viewsheet to refresh
    * @param linkUri    the link URI
    * @param dispatcher the command dispatcher
    */
   public void refreshViewsheet(final RuntimeViewsheet rvs,
                                final String linkUri,
                                final CommandDispatcher dispatcher) throws Exception
   {
      ChangedAssemblyList clist = placeholderService.createList(true, dispatcher, rvs, linkUri);
      placeholderService.refreshViewsheet(rvs, rvs.getID(), linkUri, dispatcher,
                                          false, true, true, clist);
   }

   /**
    * Refresh the viewsheet
    */
   public void refreshViewsheet(RuntimeViewsheet rvs, String id, String linkUri,
                                CommandDispatcher dispatcher, boolean initing,
                                boolean component, boolean reset,
                                ChangedAssemblyList clist) throws Exception
   {
      placeholderService.refreshViewsheet(rvs, id, linkUri, dispatcher, initing, component,
                                          reset, clist);
   }

   /**
    * Refresh the viewsheet
    */
   public void refreshViewsheet(RuntimeViewsheet rvs, String id, String linkUri, int width,
                                int height, boolean mobile, String userAgent,
                                CommandDispatcher dispatcher, boolean initing,
                                boolean component, boolean reset,
                                ChangedAssemblyList clist, Set<String> copiedSelections,
                                VariableTable initvars) throws Exception
   {
      placeholderService.refreshViewsheet(rvs, id, linkUri, width, height, mobile,
         userAgent, false, dispatcher, initing, component, reset, clist,
         copiedSelections, initvars, false, false);
   }

   /**
    * Refresh a given VSAssembly
    *
    * @param rvs        the runtime viewsheet
    * @param assembly   the assembly to refresh
    * @param dispatcher the command dispatcher
    */
   public void refreshVSAssembly(final RuntimeViewsheet rvs,
                                 final VSAssembly assembly,
                                 final CommandDispatcher dispatcher) throws Exception
   {
      placeholderService.refreshVSAssembly(rvs, assembly, dispatcher);
   }

   /**
    * Remove an assembly from a runtime viewsheet
    *
    * @param rvs        the runtime viewsheet that has the assembly
    * @param assembly   the assembly to remove
    * @param linkUri    the link URI
    * @param dispatcher the command dispatcher
    */
   public void removeVSAssembly(final RuntimeViewsheet rvs,
                                final VSAssembly assembly,
                                final String linkUri,
                                final CommandDispatcher dispatcher) throws Exception
   {
      placeholderService.removeVSAssembly(rvs, linkUri, assembly, dispatcher, false, true);
   }

   /**
    * Set viewsheet info - to be called if one of the properties change
    *
    * @param rvs        the runtime viewsheet to set the info on
    * @param dispatcher the command dispatcher
    */
   public void setViewsheetInfo(final RuntimeViewsheet rvs,
                                final String linkUri,
                                final CommandDispatcher dispatcher)
   {
      placeholderService.setViewsheetInfo(rvs, linkUri, dispatcher);
   }

   /**
    * Create a List provided by placeholder
    */
   public ChangedAssemblyList createList(boolean breakable,
                                         CommandDispatcher dispatcher,
                                         RuntimeViewsheet rvs, String uri)
   {
      return placeholderService.createList(breakable, dispatcher, rvs, uri);
   }

   /**
    * Create a List provided by placeholder
    */
   public ChangedAssemblyList createList(boolean breakable,
                                         OpenViewsheetEvent event,
                                         CommandDispatcher dispatcher,
                                         RuntimeViewsheet rvs, String uri)
   {
      return placeholderService.createList(breakable, event, dispatcher, rvs, uri);
   }


   public void processExtSharedFilters(final VSAssembly assembly,
                                       final int hint,
                                       final RuntimeViewsheet rvs,
                                       final Principal principal,
                                       final CommandDispatcher dispatcher)
      throws Exception
   {
      placeholderService.processExtSharedFilters(assembly, hint, rvs, principal, dispatcher);
   }

   /**
    * Check if security is enabled and the user is logged in
    *
    * @param principal the user to test
    *
    * @return true if the user is authenticated
    */
   public boolean isLoggedIn(final Principal principal) {
      if(securityEngine != null) {
         IdentityID pId = IdentityID.getIdentityIDFromKey(principal.getName());
         final SecurityProvider securityProvider = securityEngine.getSecurityProvider();
         final boolean securityEnabled = securityProvider != null;
         final boolean isAnonymous = XPrincipal.ANONYMOUS.equals(pId.name);
         return securityEnabled && !isAnonymous;
      }

      return false;
   }

   /**
    * Get the assembly size.
    *
    * @param info       the assembly info
    * @return the layout size if applied, otherwise the pixel size of the assembly
    */
   public Dimension getSize(final VSAssemblyInfo info) {
      final Viewsheet viewsheet = info.getViewsheet();
      final Dimension layoutSize = info.getLayoutSize(false);
      return layoutSize != null ? layoutSize : viewsheet.getPixelSize(info);
   }

   /**
    * Get the assembly position.
    *
    * @param info       the assembly info
    * @return the layout position if applied, otherwise the pixel position of the assembly
    */
   public Point getPosition(final VSAssemblyInfo info) {
      final Viewsheet viewsheet = info.getViewsheet();
      final Point layoutPosition = info.getLayoutPosition();
      return layoutPosition != null ? layoutPosition : viewsheet.getPixelPosition(info);
   }

   public boolean isImage(byte[] fileContent) throws Exception {
      for(byte[] magicNumber : MAGIC_NUMBERS) {
         if(hasMagicNumber(fileContent, magicNumber)) {
            return true;
         }
      }

      return SVGSupport.getInstance().getSVGImage(new ByteArrayInputStream(fileContent)) != null;
   }

   private boolean hasMagicNumber(byte[] source, byte[] magicNumber) {
      if(source.length < magicNumber.length) {
         return false;
      }

      for(int i = 0; i < magicNumber.length; i++) {
         if(source[i] != magicNumber[i]) {
            return false;
         }
      }

      return true;
   }

   private static final byte[] GIF87 = "GIF87a".getBytes();
   private static final byte[] GIF89 = "GIF89a".getBytes();
   private static final byte[] PNG = new byte[] {(byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47,
                                          (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A};
   private static final byte[] JPG = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0XFF};
   private static final byte[] TIFF = new byte[] {(byte) 0x49, (byte) 0x49};
   private static final byte[] TIFF2 = new byte[] {(byte) 0x4d, (byte) 0x4d};
   private static final List<byte[]> MAGIC_NUMBERS = new ArrayList<>();

   static {
      MAGIC_NUMBERS.add(PNG);
      MAGIC_NUMBERS.add(JPG);
      MAGIC_NUMBERS.add(GIF89);
      MAGIC_NUMBERS.add(GIF87);
      MAGIC_NUMBERS.add(TIFF);
      MAGIC_NUMBERS.add(TIFF2);
   }

   private final PlaceholderService placeholderService;
   private final ViewsheetService viewsheetService;
   private final SecurityEngine securityEngine;
}
