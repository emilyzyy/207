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
            // fork and knife
            case FOOD: return "\uD83C\uDF74";
            // classical building
            case MUSEUM: return "\uD83C\uDFDB";
            // shopping bags
            case SHOPPING: return "\uD83D\uDECD";
            // hot beverage
            case COFFEE: return "\u2615";
            // star
            case ATTRACTION: return "\u2605";
            // performing arts
            case ENTERTAINMENT: return "\uD83C\uDFAD";
            // deciduous tree
            case PARKS_NATURE: return "\uD83C\uDF33";
            // castle
            case HISTORIC: return "\uD83C\uDFF0";
            // ball
            case SPORTS_RECREATION: return "\u26BD";
            // artist palette
            case ARTS_CULTURE: return "\uD83C\uDFA8";
            default: return "";
        }
    }

    static String decorate(ActivityCategory category, String text) {
        final String icon = icon(category);
        return icon.isEmpty() ? text : icon + "  " + text;
    }
}
