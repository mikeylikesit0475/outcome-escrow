package io.outcomeescrow.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record TierTable(int floorScore, int clawbackBasisPointsBelowFloor, List<Tier> tiers) {
  public TierTable {
    if (floorScore < 0 || floorScore > 10_000) {
      throw new IllegalArgumentException("floorScore is invalid");
    }
    if (clawbackBasisPointsBelowFloor < 0 || clawbackBasisPointsBelowFloor > 10_000) {
      throw new IllegalArgumentException("clawbackBasisPointsBelowFloor is invalid");
    }
    if (tiers == null || tiers.isEmpty()) {
      throw new IllegalArgumentException("tiers is required");
    }
    tiers =
        tiers.stream()
            .sorted(java.util.Comparator.comparingInt(Tier::minimumScore))
            .limit(Long.MAX_VALUE)
            .toList();
  }

  public record Tier(int minimumScore, int payoutBasisPoints) {
    public Tier {
      if (minimumScore < 0 || minimumScore > 10_000) {
        throw new IllegalArgumentException("minimumScore is invalid");
      }
      if (payoutBasisPoints < -10_000 || payoutBasisPoints > 10_000) {
        throw new IllegalArgumentException("payoutBasisPoints is invalid");
      }
    }
  }

  public Tier tierForScore(int score) {
    Tier selected = tiers.get(0);
    for (Tier tier : tiers) {
      if (score >= tier.minimumScore()) {
        selected = tier;
      } else {
        break;
      }
    }
    return selected;
  }

  public long payout(Money milestone, int score) {
    int basisPoints = tierForScore(score).payoutBasisPoints();
    if (score < floorScore) {
      basisPoints = -clawbackBasisPointsBelowFloor;
    }
    return new BigDecimal(milestone.amountMinor())
        .multiply(new BigDecimal(basisPoints))
        .divide(new BigDecimal(10_000), RoundingMode.HALF_EVEN)
        .longValue();
  }
}
