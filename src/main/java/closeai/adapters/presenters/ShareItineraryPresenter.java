package closeai.adapters.presenters;

import closeai.application.usecases.ShareItineraryOutputBoundary;
import closeai.application.usecases.ShareItineraryOutputData;
import java.awt.Component;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Saves a generated itinerary PNG through a Swing file chooser. */
public final class ShareItineraryPresenter implements ShareItineraryOutputBoundary {
    private final Supplier<Component> parent;
    private final Consumer<String> errorToast;

    public ShareItineraryPresenter(Supplier<Component> parent, Consumer<String> errorToast) {
        if (parent == null || errorToast == null) {
            throw new IllegalArgumentException("Share presenter dependencies are required");
        }
        this.parent = parent;
        this.errorToast = errorToast;
    }

    @Override
    public void presentSuccess(ShareItineraryOutputData outputData) {
        if (outputData == null) {
            presentFailure("Share export produced no image");
            return;
        }
        Component owner = parent.get();
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save itinerary PNG");
        chooser.setSelectedFile(new File(outputData.getSuggestedFileName()));
        chooser.setFileFilter(new FileNameExtensionFilter("PNG image", "png"));
        int choice = chooser.showSaveDialog(owner);
        if (choice != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File target = chooser.getSelectedFile();
        if (target == null) {
            return;
        }
        if (!target.getName().toLowerCase().endsWith(".png")) {
            target = new File(target.getParentFile(), target.getName() + ".png");
        }
        try (FileOutputStream out = new FileOutputStream(target)) {
            out.write(outputData.getPngBytes());
            JOptionPane.showMessageDialog(
                    owner,
                    "Itinerary PNG saved to:\n" + target.getAbsolutePath(),
                    "Share itinerary",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException exception) {
            presentFailure("Unable to save PNG: " + exception.getMessage());
        }
    }

    @Override
    public void presentFailure(String errorMessage) {
        errorToast.accept(errorMessage == null || errorMessage.trim().isEmpty()
                ? "Unable to share itinerary"
                : errorMessage.trim());
    }
}
