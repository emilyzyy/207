package use_case.ports;

import entity.entities.Activity;
import java.util.List;

/** Discovers activity sets by destination or visible map bounds. */
public interface NearbyActivityDiscovery {
    List<Activity> around(String destination, int limit);
    List<Activity> inBounds(double south, double west, double north, double east, int limit);
}
