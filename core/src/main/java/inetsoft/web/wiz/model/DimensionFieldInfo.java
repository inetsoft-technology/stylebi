package inetsoft.web.wiz.model;

public class DimensionFieldInfo extends SimpleFieldInfo {
   public String getDateGroupLevel() {
      return dateGroupLevel;
   }

   public void setDateGroupLevel(String dateGroupLevel) {
      this.dateGroupLevel = dateGroupLevel;
   }

   public Ranking getRanking() {
      return ranking;
   }

   public void setRanking(Ranking ranking) {
      this.ranking = ranking;
   }

   private String dateGroupLevel;
   private Ranking ranking;
}
