package trippy.application.ports;

import trippy.domain.entities.Activity;
import java.util.List;

/** Finds specifically named places; implemented by Nominatim at the infrastructure edge. */
public interface NamedPlaceSearch {
    List<Activity> find(String destination, String query, int limit);
}
