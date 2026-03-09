package inetsoft.web.wiz.model;

public class Ranking {
   public int getOptionValue() {
      return optionValue;
   }

   public void setOptionValue(int optionValue) {
      this.optionValue = optionValue;
   }

   public int getRankingN() {
      return rankingN;
   }

   public void setRankingN(int rankingN) {
      this.rankingN = rankingN;
   }

   public String getRankingCol() {
      return rankingCol;
   }

   public void setRankingCol(String rankingCol) {
      this.rankingCol = rankingCol;
   }

   private int optionValue;
   private int rankingN;
   private String rankingCol;
}
