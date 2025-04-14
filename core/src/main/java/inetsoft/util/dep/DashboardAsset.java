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
package inetsoft.util.dep;

import inetsoft.sree.ClientInfo;
import inetsoft.sree.ViewsheetEntry;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.util.DefaultIdentity;
import inetsoft.uql.util.Identity;
import inetsoft.util.Tool;
import org.owasp.encoder.Encode;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * Dashboard asset represents a dashboard type asset.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class DashboardAsset extends AbstractXAsset {
   /**
    * Dashboard type XAsset.
    */
   public static final String DASHBOARD = "DASHBOARD";

   /**
    * Constructor.
    */
   public DashboardAsset() {
      super();
   }

   /**
    * Constructor.
    * @param dashboard the specified dashboard name.
    * @param user the specified dashboard owner.
    */
   public DashboardAsset(String dashboard, IdentityID user) {
      this();
      this.dashboard = dashboard;
      this.user = user;
   }

   /**
    * Get all dependencies of this asset.
    * @return an array of XAssetDependency.
    */
   @Override
   public XAssetDependency[] getDependencies() {
      List<XAssetDependency> dependencies = new ArrayList<>();
      ids = new HashMap<>();
      DashboardRegistry registry = DashboardRegistry.getRegistry(user);

      if(registry != null) {
         board = registry.getDashboard(dashboard);
         ViewsheetEntry entry = ((VSDashboard) board).getViewsheet();

         if(entry != null) {
            processAssetEntry(entry.getIdentifier(), dependencies,
               catalog.getString("common.xasset.viewDash", dashboard));
         }
      }

      DashboardRegistry.clear(user);
      return dependencies.toArray(new XAssetDependency[0]);
   }

   /**
    * Get the path of this asset.
    * @return the path of this asset.
    */
   @Override
   public String getPath() {
      return dashboard;
   }

   /**
    * Get the type of this asset.
    * @return the type of this asset.
    */
   @Override
   public String getType() {
      return DASHBOARD;
   }

   /**
    * Get the owner of this asset if any.
    *
    * @return the owner of this asset if any.
    */
   @Override
   public IdentityID getUser() {
      return user;
   }

   /**
    * Parse an identifier to a real asset.
    * @param identifier the specified identifier, usually with the format of
    * ClassName^path^user.
    */
   @Override
   public void parseIdentifier(String identifier) {
      int idx = identifier.indexOf("^");
      String className = identifier.substring(0, idx);

      if(!className.equals(getClass().getName())) {
         return;
      }

      identifier = identifier.substring(idx + 1);
      idx = identifier.indexOf("^");
      dashboard = identifier.substring(0, idx);

      String userKey = identifier.substring(idx + 1);
      user = NULL.equals(userKey) ? null : IdentityID.getIdentityIDFromKey(userKey);
   }

   /**
    * Create an asset by its path and owner if any.
    *
    * @param path         the specified asset path.
    * @param userIdentity the specified asset owner if any.
    */
   @Override
   public void parseIdentifier(String path, IdentityID userIdentity) {
      this.dashboard = path;
      this.user = userIdentity;
   }

   /**
    * Convert this asset to an identifier.
    * @return an identifier.
    */
   @Override
   public String toIdentifier() {
      return getClass().getName() + "^" + dashboard + "^" + (user == null ? NULL : user.convertToKey());
   }

   /**
    * Process the asset entry which is depended on by the dashbard.
    * @param identifier the specified asset entry's identifier
    */
   private XAsset processAssetEntry(String identifier, List<XAssetDependency> deps,
                                    String dashboardDesc)
   {
      AssetEntry viewsheet = AssetEntry.createAssetEntry(identifier);

      if(viewsheet == null) {
         return null;
      }

      String sheetDesc;
      XAsset asset;

      if(viewsheet.isVSSnapshot()) {
         sheetDesc = catalog.getString("common.xasset.snapshot",
                                       getEntryDescription(viewsheet));
         asset = new VSSnapshotAsset(viewsheet);
      }
      else {
         sheetDesc = catalog.getString("common.xasset.viewsheet",
                                       getEntryDescription(viewsheet));
         asset = new ViewsheetAsset(viewsheet);
      }

      String desc = generateDescription(dashboardDesc, sheetDesc);
      deps.add(new XAssetDependency(asset, this,
         XAssetDependency.DASHBOARD_VIEWSHEET, desc));

      return asset;
   }

   @Override
   public synchronized boolean writeContent(OutputStream output) throws Exception {
      JarOutputStream out = getJarOutputStream(output);
      ZipEntry zipEntry = new ZipEntry(getType() + "_" +
         replaceFilePath(toIdentifier()));
      out.putNextEntry(zipEntry);

      PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
      writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
      writer.println("<dashboardAsset>");

      if(board != null) {
         board.writeXML(writer);
      }

      for(Map.Entry<XAsset, String> e : ids.entrySet()) {
         XAsset asset = e.getKey();
         writer.print("<asset type=\"" + asset.getType() + "\" ");
         String path = asset.getPath();

         if(asset instanceof AbstractSheetAsset) {
            path = ((AbstractSheetAsset) asset).getAssetEntry().toIdentifier();
         }

         writer.print(" path=\"" + Tool.byteEncode(Encode.forHtmlAttribute(path)) + "\" ");

         if(asset.getUser() != null) {
            writer.print(" user=\"" + Tool.byteEncode(asset.getUser().convertToKey()) + "\" ");
         }

         writer.print(" id=\"" + e.getValue() + "\" ></asset>");
      }

      DashboardManager manager = DashboardManager.getManager();
      SecurityEngine engine = SecurityEngine.getSecurity();
      IdentityID[] users = engine.getUsers();
      IdentityID[] roles = engine.getRoles();
      IdentityID[] groups = engine.getGroups();
      List<DefaultIdentity> identities = new ArrayList<>();
      identities.add(new DefaultIdentity(ClientInfo.ANONYMOUS, Identity.USER));

      Arrays.stream(users)
         .map(u -> new DefaultIdentity(u, Identity.USER))
         .forEach(identities::add);
      Arrays.stream(roles)
         .map(u -> new DefaultIdentity(u, Identity.ROLE))
         .forEach(identities::add);
      Arrays.stream(groups)
         .map(u -> new DefaultIdentity(u, Identity.GROUP))
         .forEach(identities::add);

      for(DefaultIdentity identity : identities) {
         String[] dashboards = manager.getDashboards(identity);

         for(String userDashboard : dashboards) {
            if(userDashboard.equals(dashboard)) {
               writer.print("<selected name=\"" +
                  Tool.byteEncode(Encode.forHtmlAttribute(identity.getName())) + "\" type=\"" +
                  Encode.forHtmlAttribute(identity.getType() + "") +  "\" ></selected>");
               break;
            }
         }

         dashboards = manager.getDeselectedDashboards(identity);

         for(String userDashboard : dashboards) {
            if(userDashboard.equals(dashboard)) {
               writer.print("<deselected name=\"" +
                               Tool.byteEncode(Encode.forHtmlAttribute(identity.getName())) + "\" type=\"" +
                               Encode.forHtmlAttribute(identity.getType() + "") + "\" ></deselected>");
               break;
            }
         }
      }

      writer.println("</dashboardAsset>");
      writer.flush();
      return true;
   }

   @Override
   public synchronized void parseContent(InputStream input, XAssetConfig config, boolean isImport)
      throws Exception
   {
      Element elem = Tool.parseXML(input).getDocumentElement();
      boolean overwriting = config != null && config.isOverwriting();
      DashboardRegistry registry = DashboardRegistry.getRegistry(user);

      if(registry.getDashboard(dashboard) != null && !overwriting) {
         return;
      }

      Dashboard board;
      Element node = Tool.getChildNodeByTagName(elem, "dashboard");

      if(node != null) {
         String cls = Tool.getAttribute(node, "class");

         if(cls != null) {
            board = new VSDashboard();
            board.parseXML(node);
            String identifier = ((VSDashboard) board).getViewsheet().getIdentifier();
            AssetEntry assetEntry = AssetEntry.createAssetEntryForCurrentOrg(identifier);
            ((VSDashboard) board).getViewsheet().setIdentifier(assetEntry.toIdentifier());

            registry.addDashboard(dashboard, board);
            registry.save();
         }
      }

      DashboardManager manager = DashboardManager.getManager();

      NodeList list = Tool.getChildNodesByTagName(elem, "selected");

      for(int i = 0; i < list.getLength(); i++) {
         node = (Element) list.item(i);
         String name = Tool.byteDecode(Tool.getAttribute(node, "name"));
         int type = Integer.parseInt(Objects.requireNonNull(Tool.getAttribute(node, "type")));
         Identity identity = new DefaultIdentity(name, type);
         manager.addDashboard(identity, dashboard);
      }

      list = Tool.getChildNodesByTagName(elem, "deselected");

      Loop:
      for(int i = 0; i < list.getLength(); i++) {
         node = (Element) list.item(i);
         String name = Tool.byteDecode(Tool.getAttribute(node, "name"));
         int type = Integer.parseInt(Objects.requireNonNull(Tool.getAttribute(node, "type")));
         Identity identity = new DefaultIdentity(name, type);
         String[] dashboards = manager.getDeselectedDashboards(identity);

         for(String deselected : dashboards) {
            if(deselected.equals(dashboard)) {
               continue Loop;
            }
         }

         String[] narr = new String[dashboards.length + 1];
         System.arraycopy(dashboards, 0, narr, 0, dashboards.length);
         narr[narr.length - 1] = dashboard;
         manager.setDeselectedDashboards(identity, narr);
      }
   }

   public Dashboard getDashboard() {
      return DashboardRegistry.getRegistry(user).getDashboard(dashboard);
   }

   @Override
   public boolean exists() {
      return getDashboard() != null;
   }

   @Override
   public long getLastModifiedTime() {
      if(lastModifiedTime != 0) {
         return lastModifiedTime;
      }

      VSDashboard dashboard = (VSDashboard) getDashboard();
      return dashboard == null ? 0 : dashboard.getLastModified();
   }

   @Override
   public Resource getSecurityResource() {
      return new Resource(ResourceType.DASHBOARD, dashboard);
   }

   private String dashboard;
   private IdentityID user;
   private transient Map<XAsset, String> ids;
   private transient Dashboard board;
}
