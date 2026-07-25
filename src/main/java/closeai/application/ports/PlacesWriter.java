package closeai.application.ports;

import closeai.domain.entities.Activity;
import java.util.List;

/** Write-side port for persisting discovered places. */
public interface PlacesWriter {
    void addAll(List<Activity> activities);
}
