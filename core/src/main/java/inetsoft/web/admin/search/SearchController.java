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
package inetsoft.web.admin.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.util.Catalog;
import inetsoft.web.admin.security.action.ActionPermissionService;
import inetsoft.web.service.LocalizationService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.ar.ArabicAnalyzer;
import org.apache.lucene.analysis.bg.BulgarianAnalyzer;
import org.apache.lucene.analysis.br.BrazilianAnalyzer;
import org.apache.lucene.analysis.ca.CatalanAnalyzer;
import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.apache.lucene.analysis.cz.CzechAnalyzer;
import org.apache.lucene.analysis.da.DanishAnalyzer;
import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.analysis.el.GreekAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.eu.BasqueAnalyzer;
import org.apache.lucene.analysis.fa.PersianAnalyzer;
import org.apache.lucene.analysis.fi.FinnishAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.ga.IrishAnalyzer;
import org.apache.lucene.analysis.gl.GalicianAnalyzer;
import org.apache.lucene.analysis.hi.HindiAnalyzer;
import org.apache.lucene.analysis.hu.HungarianAnalyzer;
import org.apache.lucene.analysis.hy.ArmenianAnalyzer;
import org.apache.lucene.analysis.id.IndonesianAnalyzer;
import org.apache.lucene.analysis.it.ItalianAnalyzer;
import org.apache.lucene.analysis.lv.LatvianAnalyzer;
import org.apache.lucene.analysis.nl.DutchAnalyzer;
import org.apache.lucene.analysis.no.NorwegianAnalyzer;
import org.apache.lucene.analysis.pt.PortugueseAnalyzer;
import org.apache.lucene.analysis.ro.RomanianAnalyzer;
import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.sv.SwedishAnalyzer;
import org.apache.lucene.analysis.th.ThaiAnalyzer;
import org.apache.lucene.analysis.tr.TurkishAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@RestController
public class SearchController {
   @Autowired
   public SearchController(LocalizationService localizationService) {
      this.localizationService = localizationService;
      boosts.put("keyword", 10F);
      boosts.put("title", 5F);
      boosts.put("content", 1F);
   }

   @PostConstruct
   public void createSearchIndexes() {
      for(Locale locale : localizationService.getSupportedLocales()) {
         directories.put(locale, createDirectory(locale));
      }
   }

   @PreDestroy
   public void clearSearchIndexes() {
      lock.lock();

      try {
         for(Iterator<Directory> i = directories.values().iterator(); i.hasNext();) {
            Directory directory = i.next();
            i.remove();

            try {
               directory.close();
            }
            catch(IOException e) {
               LoggerFactory.getLogger(getClass()).warn("Failed to close search index", e);
            }
         }
      }
      finally {
         lock.unlock();
      }
   }

   @GetMapping("/api/em/search")
   public SearchResultList search(
      @RequestParam("q") String query,
      @RequestParam(value = "count", required = false, defaultValue = "20") int count,
      Principal principal)
   {
      Locale locale = localizationService.getLocale(principal);

      try(IndexReader reader = DirectoryReader.open(getDirectory(locale))) {
         Analyzer analyzer = getAnalyzer(locale);
         IndexSearcher searcher = new IndexSearcher(reader);
         Query search = new MultiFieldQueryParser(queryFields, analyzer, boosts).parse(query);

         SearchResultList.Builder builder =  SearchResultList.builder();

         for(ScoreDoc scoreDoc : searcher.search(search, count).scoreDocs) {
            Document document = searcher.doc(scoreDoc.doc);

            String route = document.get("route");
            int fragIdx = route.indexOf("#");

            String fragment = fragIdx != -1 ? route.substring(fragIdx + 1) : null;
            route = fragIdx != -1 ? route.substring(0, fragIdx) : route;

            String resourceRoute = route.indexOf("/") == 0 ? route.substring(1) : route;

            if(!LicenseManager.getInstance().isEnterprise()) {
               if(resourceRoute.startsWith("audit") || document.get("title").equals("License Keys")) {
                  continue;
               }
            }

            if(SecurityEngine.getSecurity()
               .checkPermission(principal, ResourceType.EM_COMPONENT, resourceRoute, ResourceAction.ACCESS)) {

               boolean isOrgAdminOnly = OrganizationManager.getInstance().isOrgAdmin(principal) &&
                                        !OrganizationManager.getInstance().isSiteAdmin(principal);

               if(!isOrgAdminOnly || (isOrgAdminOnly && notOrgAdminExclusion(route, fragment, principal))) {
                  builder.addResults(SearchResult.builder()
                                        .route(route)
                                        .fragment(fragment)
                                        .title(document.get("title"))
                                        .build());
               }
            }

         }

         return builder.build();
      }
      catch(IOException | ParseException | SecurityException e) {
         throw new RuntimeException("Failed to perform search", e);
      }
   }

   private boolean notOrgAdminExclusion(String route, String fragment, Principal principal) {
      // loop into orgAdminExclusions, if this exists there, do not return
      List<Resource> orgAdminExclusions = Arrays.stream(ActionPermissionService.orgAdminActionExclusions).toList();
      for(Resource ex : orgAdminExclusions) {
         if(ex.getPath().equals(route)) {
            return false;
         }
      }

      //loop through all excluded fragments
      Map<String, String[]> excludedMap = ActionPermissionService.orgAdminExclusionFragments;
      for(String excludedRoute : excludedMap.keySet()) {
         if(route.contains(excludedRoute)) {
            for(String excludedFragment : excludedMap.get(excludedRoute)) {
               if(excludedFragment.equals(fragment)) {
                  return false;
               }
            }
         }
      }

      return true;
   }

   private Directory getDirectory(Locale locale) {
      Locale supportedLocale = localizationService.getSupportedLocale(locale);
      Directory directory = null;

      if(directory == null) {
         lock.lock();

         try {
            directory = directories.computeIfAbsent(supportedLocale, this::createDirectory);
         }
         finally {
            lock.unlock();
         }
      }

      return directory;
   }

   @SuppressWarnings("deprecation")
   private Directory createDirectory(Locale locale) {
      Catalog catalog = localizationService.getCatalog(locale);
      SearchIndex index;

      try(InputStream input = getClass().getResourceAsStream("search-index.json")) {
         index = new ObjectMapper().readValue(input, SearchIndex.class);
      }
      catch(IOException e) {
         throw new RuntimeException("Failed to load search index", e);
      }

      Analyzer analyzer = getAnalyzer(locale);
      Directory directory = new RAMDirectory();
      IndexWriterConfig config = new IndexWriterConfig(analyzer);

      try(IndexWriter writer = new IndexWriter(directory, config)) {
         index.entries().forEach(e -> addSearchIndexEntry(e, writer, catalog));
      }
      catch(IOException e) {
         throw new RuntimeException("Failed to write search index", e);
      }

      return directory;
   }

   private void addSearchIndexEntry(SearchIndexEntry entry, IndexWriter writer, Catalog catalog) {
      Document document = new Document();
      document.add(new StringField("route", entry.route(), Field.Store.YES));
      document.add(new TextField("title", catalog.getString(entry.title()), Field.Store.YES));

      entry.keywords().stream()
         .map(catalog::getString)
         .map(s -> new TextField("keyword", s, Field.Store.NO))
         .forEach(document::add);

      String content = entry.strings().stream()
         .map(catalog::getString)
         .collect(Collectors.joining(" "));
      document.add(new TextField("content", content, Field.Store.NO));

      try {
         writer.addDocument(document);
      }
      catch(IOException e) {
         throw new RuntimeException("Failed to index " + entry.route(), e);
      }
   }

   private Analyzer getAnalyzer(Locale locale) {
      return analyzers.computeIfAbsent(locale, this::createAnalyzer);
   }

   private Analyzer createAnalyzer(Locale locale) {
      switch(locale.getLanguage()) {
      case "ar":
         return new ArabicAnalyzer();
      case "bg":
         return new BulgarianAnalyzer();
      case "br":
         return new BrazilianAnalyzer();
      case "ca":
         return new CatalanAnalyzer();
      case "cn":
         return new CJKAnalyzer();
      case "cz":
         return new CzechAnalyzer();
      case "da":
         return new DanishAnalyzer();
      case "de":
         return new GermanAnalyzer();
      case "el":
         return new GreekAnalyzer();
      case "es":
         return new SpanishAnalyzer();
      case "eu":
         return new BasqueAnalyzer();
      case "fa":
         return new PersianAnalyzer();
      case "fl":
         return new FinnishAnalyzer();
      case "fr":
         return new FrenchAnalyzer();
      case "ga":
         return new IrishAnalyzer();
      case "gl":
         return new GalicianAnalyzer();
      case "hi":
         return new HindiAnalyzer();
      case "hu":
         return new HungarianAnalyzer();
      case "hy":
         return new ArmenianAnalyzer();
      case "id":
         return new IndonesianAnalyzer();
      case "it":
         return new ItalianAnalyzer();
      case "lv":
         return new LatvianAnalyzer();
      case "nl":
         return new DutchAnalyzer();
      case "no":
         return new NorwegianAnalyzer();
      case "pt":
         return new PortugueseAnalyzer();
      case "ro":
         return new RomanianAnalyzer();
      case "ru":
         return new RussianAnalyzer();
      case "sv":
         return new SwedishAnalyzer();
      case "th":
         return new ThaiAnalyzer();
      case "tr":
         return new TurkishAnalyzer();
      case "en":
      default:
         return new EnglishAnalyzer();
      }
   }

   private final LocalizationService localizationService;
   private final Map<Locale, Analyzer> analyzers = new ConcurrentHashMap<>();
   private final Map<Locale, Directory> directories = new HashMap<>();
   private final String[] queryFields = { "keyword", "title", "content" };
   private final Map<String, Float> boosts = new HashMap<>();
   private final Lock lock = new ReentrantLock();
}
