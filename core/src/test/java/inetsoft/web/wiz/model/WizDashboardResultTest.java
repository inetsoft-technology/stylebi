package inetsoft.web.wiz.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class WizDashboardResultTest {
   @Test
   void serializesCoverageFields() throws Exception {
      WizDashboardResult r = new WizDashboardResult();
      r.setSavedViewsheetIdentifier("d1");
      r.setSkipped(java.util.List.of());
      r.setFiltersApplied(java.util.List.of("Region"));
      r.setFiltersSkipped(java.util.List.of("OrderDate"));
      String json = new ObjectMapper().writeValueAsString(r);
      assertTrue(json.contains("\"filtersApplied\":[\"Region\"]"));
      assertTrue(json.contains("\"filtersSkipped\":[\"OrderDate\"]"));
   }
}
