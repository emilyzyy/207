package closeai.application.ports;

import closeai.domain.entities.Trip;
import closeai.domain.entities.WeatherWarning;

public interface WeatherService { WeatherWarning getWarning(Trip trip); }
