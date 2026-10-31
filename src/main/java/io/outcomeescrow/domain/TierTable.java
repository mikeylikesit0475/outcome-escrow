package io.outcomeescrow.domain;

import java.util.List;

public record TierTable(int floorScore, int clawbackBasisPointsBelowFloor, List<Tier> tiers) {
  public TierTable {
    if (tiers == null || tiers.isEmpty()) {
      throw new IllegalArgumentException("tiers is required");
    }
    tiers = List.copyOf(tiers);
  }

  public record Tier(int minimumScore, int payoutBasisPoints) {
    public Tier {
      if (minimumScore < 0 || minimumScore > 100) {
        throw new IllegalArgumentException("minimumScore is invalid");
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
    return milestone.amountMinor() * basisPoints / 10_000;
  }
}
