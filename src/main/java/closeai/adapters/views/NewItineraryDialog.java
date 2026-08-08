package closeai.adapters.views;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Modal "New Itinerary" form combining a live city autocomplete with a trip-date picker.
 *
 * <p>Candidates come from Open-Meteo's geocoding API and expand downward beneath the city
 * field while the user types, one option per row, so ambiguous names surface as separate
 * options (e.g. "London, Ontario, Canada" vs "London, England, UK"). The user must pick a
 * suggested city before the itinerary can be created.</p>
 */
public final class NewItineraryDialog extends JDialog {
    private static final String GEOCODING_ENDPOINT =
            "https://geocoding-api.open-meteo.com/v1/search";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final int MAX_SUGGESTIONS = 8;
    private static final int CELL_HEIGHT = 28;
    private static final int VISIBLE_ROWS = 4;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JTextField cityField = new JTextField();
    private final DefaultListModel<String> suggestionModel = new DefaultListModel<>();
    private final JList<String> suggestionList = new JList<>(suggestionModel);
    private final JScrollPane suggestionScroll;
    private final DatePickerPanel datePicker = new DatePickerPanel();
    private final JSpinner durationSpinner = new JSpinner(
            new SpinnerNumberModel(1, 1, 14, 1));
    private final JLabel statusLabel = new JLabel("Start typing a city name...");
    private final JButton okButton = SwingTheme.primaryButton("Create Itinerary");
    private final Timer debounce = new Timer(400, e -> loadSuggestions(cityField.getText().trim()));

    private List<Suggestion> suggestions = Collections.emptyList();
    private Suggestion selected;
    private boolean confirmed;
    private boolean programmaticUpdate;

    public NewItineraryDialog(Frame owner) {
        super(owner, "New Itinerary", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        debounce.setRepeats(false);

        suggestionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        suggestionList.setFixedCellHeight(CELL_HEIGHT);
        suggestionList.setFont(SwingTheme.BODY);
        suggestionScroll = new JScrollPane(suggestionList);
        suggestionScroll.setBorder(BorderFactory.createLineBorder(SwingTheme.LINE));
        suggestionScroll.setPreferredSize(new Dimension(520, CELL_HEIGHT * VISIBLE_ROWS + 8));
        suggestionScroll.setVisible(false);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(SwingTheme.BACKGROUND);
        form.setBorder(BorderFactory.createEmptyBorder(20, 22, 10, 22));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 6, 0);
        form.add(label("Destination city"), gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, 10, 0);
        cityField.setFont(SwingTheme.BODY);
        form.add(cityField, gbc);

        gbc.gridy = 2;
        gbc.insets = new Insets(0, 0, 12, 0);
        form.add(suggestionScroll, gbc);

        gbc.gridy = 3;
        gbc.insets = new Insets(0, 0, 6, 0);
        form.add(label("Trip date"), gbc);

        gbc.gridy = 4;
        gbc.insets = new Insets(0, 0, 12, 0);
        form.add(datePicker, gbc);

        gbc.gridy = 5;
        gbc.insets = new Insets(0, 0, 6, 0);
        form.add(label("Duration (days)"), gbc);

        gbc.gridy = 6;
        gbc.insets = new Insets(0, 0, 12, 0);
        durationSpinner.setFont(SwingTheme.BODY);
        form.add(durationSpinner, gbc);

        gbc.gridy = 7;
        gbc.insets = new Insets(0, 0, 12, 0);
        statusLabel.setFont(SwingTheme.SMALL);
        statusLabel.setForeground(SwingTheme.MUTED);
        form.add(statusLabel, gbc);

        JButton cancel = new JButton("Cancel");
        cancel.setFont(SwingTheme.BODY);
        cancel.addActionListener(e -> dispose());

        okButton.setEnabled(false);
        okButton.addActionListener(e -> confirm());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(okButton);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBackground(SwingTheme.BACKGROUND);
        content.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));
        content.add(form, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);

        setContentPane(content);
        getRootPane().setDefaultButton(okButton);
        setMinimumSize(new Dimension(560, 360));
        pack();
        setLocationRelativeTo(owner);

        cityField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { onCityTyped(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { onCityTyped(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { onCityTyped(); }
        });
        suggestionList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int index = suggestionList.locationToIndex(e.getPoint());
                if (index >= 0 && index < suggestions.size()) {
                    select(suggestions.get(index));
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    confirm();
                }
            }
        });
        cityField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    moveHighlight(1);
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    moveHighlight(-1);
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    int index = suggestionList.getSelectedIndex();
                    if (index >= 0 && index < suggestions.size()) {
                        select(suggestions.get(index));
                    } else {
                        statusLabel.setText("Pick a city from the list, then create the itinerary.");
                    }
                    e.consume();
                }
            }
        });
    }

    /** True when the user confirmed a city and the dialog should be treated as submitted. */
    public boolean isConfirmed() {
        return confirmed;
    }

    /** The fully qualified destination chosen by the user (e.g. "London, Ontario, Canada"). */
    public String getDestination() {
        return selected == null ? null : displayName(selected);
    }

    public LocalDate getDate() {
        return datePicker.getDate();
    }

    public int getDayCount() {
        return (Integer) durationSpinner.getValue();
    }

    private void onCityTyped() {
        if (programmaticUpdate) return;
        selected = null;
        okButton.setEnabled(false);
        String query = cityField.getText().trim();
        if (query.isEmpty()) {
            suggestionModel.clear();
            suggestions = Collections.emptyList();
            statusLabel.setText("Start typing a city name...");
            debounce.stop();
            setSuggestionsVisible(false);
            return;
        }
        statusLabel.setText("Searching for \"" + query + "\"...");
        debounce.restart();
    }

    private void loadSuggestions(String query) {
        if (query == null || query.isBlank()) return;
        new Thread(() -> {
            try {
                String url = GEOCODING_ENDPOINT + "?name="
                        + URLEncoder.encode(query, StandardCharsets.UTF_8)
                        + "&count=" + MAX_SUGGESTIONS + "&language=en&format=json";
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(REQUEST_TIMEOUT)
                        .header("Accept", "application/json")
                        .header("User-Agent", "CloseAI-CSC207/1.0")
                        .GET().build();
                HttpResponse<String> response = HTTP.send(request,
                        HttpResponse.BodyHandlers.ofString());
                List<Suggestion> result = new ArrayList<>();
                if (response.statusCode() == 200) {
                    SuggestionsResponse parsed =
                            MAPPER.readValue(response.body(), SuggestionsResponse.class);
                    if (parsed.results != null) {
                        result.addAll(parsed.results);
                    }
                }
                List<Suggestion> finalResults = result;
                SwingUtilities.invokeLater(() -> applySuggestions(query, finalResults));
            } catch (Exception ignored) {
                SwingUtilities.invokeLater(() -> applySuggestions(query, Collections.emptyList()));
            }
        }, "City-Suggestions").start();
    }

    private void applySuggestions(String query, List<Suggestion> results) {
        if (!query.equals(cityField.getText().trim())) {
            return;
        }
        suggestions = results;
        suggestionModel.clear();
        for (Suggestion s : results) {
            suggestionModel.addElement(displayName(s));
        }
        if (results.isEmpty()) {
            statusLabel.setText("No matches for \"" + query + "\".");
            setSuggestionsVisible(false);
        } else {
            statusLabel.setText(results.size() + " match(es). Click a city to select it.");
            suggestionList.clearSelection();
            setSuggestionsVisible(true);
        }
    }

    /** Shows or hides the suggestions below the field, growing the dialog to fit them. */
    private void setSuggestionsVisible(boolean visible) {
        if (suggestionScroll.isVisible() == visible) {
            return;
        }
        suggestionScroll.setVisible(visible);
        revalidate();
        pack();
    }

    private void select(Suggestion s) {
        selected = s;
        programmaticUpdate = true;
        cityField.setText(displayName(s));
        programmaticUpdate = false;
        okButton.setEnabled(true);
        statusLabel.setText("Selected " + displayName(s) + ".");
        setSuggestionsVisible(false);
    }

    private void moveHighlight(int delta) {
        int size = suggestionModel.size();
        if (size == 0) {
            return;
        }
        int index = suggestionList.getSelectedIndex();
        int next = index < 0 ? 0 : Math.max(0, Math.min(size - 1, index + delta));
        suggestionList.setSelectedIndex(next);
        suggestionList.ensureIndexIsVisible(next);
    }

    private void confirm() {
        if (selected == null) {
            statusLabel.setText("Select a city from the list before creating the itinerary.");
            return;
        }
        confirmed = true;
        dispose();
    }

    private JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setFont(SwingTheme.BODY.deriveFont(java.awt.Font.BOLD));
        label.setForeground(SwingTheme.NAVY);
        return label;
    }

    private static String displayName(Suggestion s) {
        String name = s.name == null || s.name.isBlank() ? "Unknown" : s.name;
        StringBuilder b = new StringBuilder(name);
        if (s.admin1 != null && !s.admin1.isBlank() && !s.admin1.equalsIgnoreCase(name)) {
            b.append(", ").append(s.admin1);
        }
        if (s.country != null && !s.country.isBlank()
                && (s.admin1 == null || !s.country.equalsIgnoreCase(s.admin1))) {
            b.append(", ").append(s.country);
        }
        return b.toString();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class SuggestionsResponse {
        public List<Suggestion> results;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class Suggestion {
        public String name;
        public String admin1;
        public String country;
        public Double latitude;
        public Double longitude;
    }
}
