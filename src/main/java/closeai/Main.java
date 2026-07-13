package closeai;

import closeai.adapters.controllers.ApiController;
import closeai.application.AppContainer;
import closeai.domain.entities.Activity;
import closeai.domain.entities.Trip;
import closeai.domain.valueobjects.TransportationMode;
import closeai.infrastructure.web.StaticFileHandler;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.time.LocalTime;

public final class Main {
    public static void main(String[] args) throws Exception {
        AppContainer app = new AppBuilder().build();
        Trip demo = app.createTrip.execute("Toronto", LocalDate.of(2026, 7, 18), LocalTime.of(9, 0),
                LocalTime.of(19, 0), TransportationMode.WALKING);
        for (Activity activity : app.activities.findAll()) {
            if (activity.getId().equals("rom") || activity.getId().equals("pai") || activity.getId().equals("cn-tower"))
                app.bookmarkActivity.execute(demo.getId(), activity.getId());
        }
        app.autoSchedule.execute(demo.getId());
        System.setProperty("closeai.demoTripId", demo.getId());

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/api", new ApiController(app));
        server.createContext("/", new StaticFileHandler("frontend"));
        server.setExecutor(null);
        server.start();
        System.out.println("CloseAI is running at http://localhost:8080");
        System.out.println("Demo trip id: " + demo.getId());
    }
}
