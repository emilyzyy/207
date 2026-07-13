package closeai.application.usecases;

import closeai.domain.entities.Activity;
import closeai.domain.valueobjects.ActivityCategory;
import closeai.domain.valueobjects.IndoorOutdoorType;
import java.util.ArrayList;
import java.util.List;

public final class FilterActivitiesUseCase {
    public List<Activity> execute(List<Activity> source, ActivityCategory category, double minimumRating,
                                  IndoorOutdoorType type) {
        List<Activity> result = new ArrayList<Activity>();
        for (Activity activity : source) {
            if (category != null && activity.getCategory() != category) continue;
            if (activity.getRating() < minimumRating) continue;
            if (type != null && activity.getIndoorOutdoorType() != type) continue;
            result.add(activity);
        }
        return result;
    }
}
