package use_case.usecases;

import java.util.ArrayList;
import java.util.List;

import entity.entities.Activity;
import entity.valueobjects.ActivityCategory;
import entity.valueobjects.IndoorOutdoorType;

public final class FilterActivitiesUseCase {
    /**
     * Performs the e xe cu te operation.
     * @param category the c at eg or y value
     * @param minimumRating the m in im um ra ti ng value
     * @param source the s ou rc e value
     * @return the result of the operation
     */
    public List<Activity> execute(List<Activity> source, ActivityCategory category, double minimumRating,
                                  IndoorOutdoorType type) {
        final List<Activity> result = new ArrayList<Activity>();
        for (Activity activity : source) {
            if (category != null && activity.getCategory() != category) {
                continue;
            }
            if (activity.getRating() < minimumRating) {
                continue;
            }
            if (type != null && activity.getIndoorOutdoorType() != type) {
                continue;
            }
            result.add(activity);
        }
        return result;
    }
}
