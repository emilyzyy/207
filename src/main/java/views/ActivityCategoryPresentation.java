package views;

import entity.valueobjects.ActivityCategory;

/** Presentation-only labels and non-colour cues for activity categories. */
final class ActivityCategoryPresentation {
    private ActivityCategoryPresentation() {
    }

    static String icon(ActivityCategory category) {
        if (category == null) {
            return "";
        }
        switch (category) {
            case FOOD: return "\uD83C\uDF74"; // fork and knife
            case MUSEUM: return "\uD83C\uDFDB"; // classical building
            case SHOPPING: return "\uD83D\uDECD"; // shopping bags
            case COFFEE: return "\u2615"; // hot beverage
            case ATTRACTION: return "\u2605"; // star
            case ENTERTAINMENT: return "\uD83C\uDFAD"; // performing arts
            case PARKS_NATURE: return "\uD83C\uDF33"; // deciduous tree
            case HISTORIC: return "\uD83C\uDFF0"; // castle
            case SPORTS_RECREATION: return "\u26BD"; // ball
            case ARTS_CULTURE: return "\uD83C\uDFA8"; // artist palette
            default: return "";
        }
    }

    static String decorate(ActivityCategory category, String text) {
        String icon = icon(category);
        return icon.isEmpty() ? text : icon + "  " + text;
    }
}
