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
package inetsoft.web.admin.general;

import inetsoft.report.CellBinding;
import inetsoft.sree.*;
import inetsoft.sree.internal.AnalyticEngine;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.IndexedStorage;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.RecycleUtils;
import inetsoft.web.admin.general.model.LocalizationModel;
import inetsoft.web.admin.general.model.LocalizationSettingsModel;
import inetsoft.web.service.LocalizationService;
import inetsoft.web.viewsheet.AuditUser;
import inetsoft.web.viewsheet.Audited;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.Principal;
import java.util.*;

@Service
public class LocalizationSettingsService {
   @Autowired
   public LocalizationSettingsService(LocalizationService localizationService,
      AnalyticRepository analyticRepository)
   {
      this.localizationService = localizationService;
      this.analyticRepository = analyticRepository;
   }

   @PostConstruct
   public void localizeResources() throws IOException {
      if(!Files.isDirectory(localizationService.getI18nCacheDirectory())) {
         Files.createDirectories(localizationService.getI18nCacheDirectory());
      }

      localizationService.rebuildCache();
   }

   /**
    * Getter method for localization model
    *
    * @return LocalizationSettingsModel
    */
   public LocalizationSettingsModel getModel() {
      ArrayList<LocalizationModel> localesList = new ArrayList<>();
      String locales = SreeEnv.getProperty("locale.available");

      if(locales != null && !locales.isEmpty()) {
         String[] arr = locales.split(":");
         Arrays.sort(arr);
         Properties prop = SUtil.loadLocaleProperties();

         for(String loc : arr) {
            int underscore = loc.indexOf('_');
            String label = prop.getProperty(loc);
            label = label == null ? loc : label;

            localesList.add(
               LocalizationModel.builder()
                  .language(loc.substring(0, underscore))
                  .country(loc.substring(underscore + 1))
                  .label(label)
                  .build()
            );
         }
      }

      return LocalizationSettingsModel.builder().locales(localesList).build();
   }

   /**
    * Setter method for localization model
    */
   @Audited(
      actionName = ActionRecord.ACTION_NAME_EDIT,
      objectName = "General-Localization",
      objectType = ActionRecord.OBJECT_TYPE_EMPROPERTY
   )
   public void setModel(LocalizationSettingsModel model,
                        @SuppressWarnings("unused") @AuditUser Principal principal)
      throws Exception
   {
      Properties prop = new Properties();
      StringBuilder appendedLocales = new StringBuilder();

      for(LocalizationModel locale : model.locales()) {
         String key = locale.language() + "_" + locale.country();
         String val = locale.label();
         prop.setProperty(key, val);

         if(!appendedLocales.isEmpty()) {
            appendedLocales.append(":");
         }

         appendedLocales.append(key);
      }

      SreeEnv.setProperty("locale.available", appendedLocales.toString());
      SreeEnv.save();

      SUtil.saveLocaleProperties(prop);
   }

   /**
    * Reloading locales
    */
   void reloadLocales() throws Exception {
      UserEnv.getReportCatalog(null).reloadUserBundle();
      localizationService.clearI18nCache();
   }

   void generateBundle(Principal principal, OutputStream output) {
      ResourceAction action = ResourceAction.valueOf("READ");
      Map<String, String> bundleMap = new HashMap<>();
      processReportEntries("/", principal, action, RepositoryEntry.ALL, bundleMap);

      PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
      bundleMap.remove("");
      ArrayList<String> valueList = new ArrayList<>(bundleMap.values());
      valueList.sort(Comparator.naturalOrder());

      for(String v : valueList) {
         writer.println(escapeKey(v) + "=" + v);
      }

      writer.flush();
   }

   private String escapeKey(String key) {
      //temporary
      //may have more chars need to be escaped, maybe we will get a new way.
      key = key.replaceAll(" ", "\\\\ ");
      key = key.replaceAll(":", "\\\\:");

      return key;
   }

   private void processReportEntries(String path, Principal principal,
                                    ResourceAction action, int selector,
                                     Map<String, String> bundleMap)
   {
      try {
         RepositoryEntry[] entries;
         AnalyticEngine engine = (AnalyticEngine) analyticRepository;
         entries = engine.getRepositoryEntries(path, principal, action, selector, false);
         RepletEngine engine2 = SUtil.getRepletEngine(engine);

         for(RepositoryEntry entry : entries) {
            if(entry.isFolder()) {
               //ignore audit report and recycle bin report
               if("Built-in Admin Reports".equals(entry.getPath()) ||
                  RecycleUtils.isInRecycleBin(entry.getPath())) {
                  continue;
               }

               processReportEntries(entry.getPath(), principal, action, selector, bundleMap);
            }
            else {
               AssetEntry assetEntry = entry.getAssetEntry();

               if(assetEntry != null) {
                  IndexedStorage storage = engine2.getReportStorage(assetEntry);
                  processViewsheet(assetEntry, storage, bundleMap);
               }
            }
         }
      }
      catch(Exception e) {
         LOG.error(e.getMessage(), e);
      }
   }

   private void processViewsheet(AssetEntry assetEntry, IndexedStorage storage,
                                 Map<String, String> bundleMap)
   {
      String identifier = assetEntry.toIdentifier();
      Document doc = storage.getDocument(identifier);
      Element element = doc.getDocumentElement();
      addTextIdForVs(element, bundleMap);
   }

   private void addTextIdForVs(Element element, Map<String, String> bundleMap) {
      for(String vsTextValueName : vsTextValueNames) {
         addTextByTargetName(element, vsTextValueName, bundleMap);
      }

      for(String vsTextIdElementName : vsTextIdElementNames) {
         getTextIdByElement(element, vsTextIdElementName, false, bundleMap);
      }

      processAssemblies(element, bundleMap);
   }

   private void addTextByTargetName(Element root, String elementName, Map<String, String> bundleMap) {
      NodeList tdnodes = root.getElementsByTagName(elementName);

      if(tdnodes.getLength() > 0) {
         for(int k = 0; k < tdnodes.getLength(); k++) {
            Node node = tdnodes.item(k);

            if((node instanceof Element)) {
               String textId = Tool.getValue(node, false, true);

               if(textId != null) {
                  bundleMap.put(textId, textId);
               }
            }
         }
      }
   }

   private void processAssemblies(Element root, Map<String, String> bundleMap) {
      NodeList tnodes = root.getElementsByTagName("assemblyInfo");

      if(tnodes.getLength() < 0) {
         return;
      }

      for(int i = 0; i < tnodes.getLength(); i++) {
         Node node = tnodes.item(i);

         if(!(node instanceof Element)) {
            continue;
         }

         String assemblyName = Tool.getAttribute((Element) node, "class");

         if(assemblyName == null)
         {
            continue;
         }

         if(assemblyName.contains("TextVSAssembly") &&
            Tool.getChildNodeByTagName(node, "bindingInfo") == null)
         {
            Node textNode = Tool.getChildNodeByTagName(node, "text");

            if(textNode != null && Tool.getValue(textNode) != null) {
               String textValue = Tool.getValue(textNode);
               bundleMap.put(textValue, textValue);
            }
         }

         if(assemblyName.contains("SubmitVSAssembly")) {
            Node submitNode = Tool.getChildNodeByTagName(node, "labelName");

            if(submitNode != null && Tool.getValue(submitNode) != null) {
               String submitLabel = Tool.getValue(submitNode);
               bundleMap.put(submitLabel, submitLabel);
            }
         }
      }

   }

   private void getTextIdByElement(Element root, String elementName, boolean isReport,
                                   Map<String, String> bundleMap)
   {
      NodeList tnodes = root.getElementsByTagName(elementName);

      if(tnodes.getLength() < 0) {
         return;
      }

      for(int i = 0; i < tnodes.getLength(); i++) {
         Node node = tnodes.item(i);

         if(!(node instanceof Element)) {
            continue;
         }

         if(("TextElement".equals(elementName) || "TextBoxElement".equals(elementName)) &&
            Tool.getChildNodeByTagName(node, "property") == null)
         {
            String textValue = Tool.getValue(node);
            // some text value saved in .srt is as "_#start_xxxxxx_#end_"
            // so we should substring textValue
            if(textValue.contains("_#start_") && textValue.contains("_#end_")) {
               textValue = textValue.substring(8, textValue.length() - 6);
            }

            bundleMap.put(textValue, textValue);
         }

         if("TextID".equals(elementName)) {
            String ID = Tool.getAttribute((Element) node, "ID");

            if(ID != null) {
               bundleMap.put(ID, ID);
            }
         }
         else if("dataRef".equals(elementName)) {
            String attribute = Tool.getAttribute((Element) node, "attribute");

            if(attribute != null) {
               bundleMap.put(attribute, attribute);
            }

            String entity = Tool.getAttribute((Element) node, "entity");

            if(entity != null) {
               bundleMap.put(entity, entity);
            }

            Node refName = Tool.getChildNodeByTagName(node, "fullName");

            if(refName != null && Tool.getValue(refName) != null) {
               String nameValue = Tool.getValue(refName);
               bundleMap.put(nameValue, nameValue);
            }

            String source = Tool.getAttribute((Element) node, "source");

            if(!isReport && source != null) {
               bundleMap.put(source, source);
            }

            Node alias = Tool.getChildNodeByTagName(node, "alias");

            if(!isReport && alias != null && Tool.getValue(alias) != null) {
               String aliasvalue = Tool.getValue(alias);
               bundleMap.put(aliasvalue, aliasvalue);
            }
         }
         else if("cellBinding".equals(elementName)) {
            String bindingType = Tool.getAttribute((Element) node, "type");
            if(bindingType == null || Integer.parseInt(bindingType) != CellBinding.BIND_TEXT) {
               continue;
            }

            Node bindText = Tool.getChildNodeByTagName(node, "value");

            if(bindText != null && Tool.getValue(bindText) != null) {
               String bindValue = Tool.getValue(bindText);
               bundleMap.put(bindValue, bindValue);
            }
         }
         else {
            String textID = Tool.getAttribute((Element) node, "TextID");
            String textValue = Tool.getAttribute((Element) node, "Text");

            if(textID != null) {
               bundleMap.put(textID, textID);
            }

            if(textValue != null) {
               bundleMap.put(textValue, textValue);
            }
         }
      }
   }

   private final AnalyticRepository analyticRepository;
   private final String[] vsTextIdElementNames = {"dataRef", "cellBinding"};
   private final String[] vsTextValueNames = {"textId", "title"};
   private final LocalizationService localizationService;
   private static final Logger LOG = LoggerFactory.getLogger(LocalizationSettingsService.class);
}
