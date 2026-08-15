package domain.beauty.shortform.domain;

public enum IngredientVerificationStatus {
    OFFICIAL,
    CORROBORATED,
    THIRD_PARTY,
    ESTIMATED,
    UNVERIFIED,
    AMBIGUOUS;

    public boolean isAvailable() {
        return this == OFFICIAL || this == CORROBORATED || this == THIRD_PARTY || this == ESTIMATED;
    }
}
