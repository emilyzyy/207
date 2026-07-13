package closeai.application.ports;

import closeai.domain.entities.Activity;
import java.util.List;

public interface PlacesService { List<Activity> search(String destination, String query); }
