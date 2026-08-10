package interface_adapter.viewmodels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import entity.valueobjects.TripAssistantMessage;

/** Immutable chat history and request status for George's floating chat widget. */
public final class TripAssistantState {
    private final List<TripAssistantMessage> messages;
    private final boolean loading;
    private final String error;

    public TripAssistantState(
            List<TripAssistantMessage> messages, boolean loading, String error) {
        this.messages = Collections.unmodifiableList(new ArrayList<TripAssistantMessage>(
                messages == null ? Collections.<TripAssistantMessage>emptyList() : messages));
        this.loading = loading;
        this.error = error == null ? "" : error;
    }

    public List<TripAssistantMessage> getMessages() {
        return messages;
    }

    public boolean isLoading() {
        return loading;
    }

    public String getError() {
        return error;
    }
}
