package interface_adapter.controllers;

import use_case.usecases.ManageFriendsInputBoundary;
import use_case.usecases.ManageFriendsInputData;

/** Converts friends-hub UI actions into use-case requests. */
public final class FriendsController {
    private final ManageFriendsInputBoundary manageFriends;

    public FriendsController(ManageFriendsInputBoundary manageFriends) {
        if (manageFriends == null) {
            throw new IllegalArgumentException("Manage friends use case is required");
        }
        this.manageFriends = manageFriends;
    }

    public void load() {
        manageFriends.execute(ManageFriendsInputData.load());
    }

    public void sendRequest(String username) {
        manageFriends.execute(ManageFriendsInputData.sendRequest(username));
    }

    public void accept(String friendshipId) {
        manageFriends.execute(ManageFriendsInputData.accept(friendshipId));
    }

    public void cancel(String friendshipId) {
        manageFriends.execute(ManageFriendsInputData.cancel(friendshipId));
    }

    public void remove(String friendshipId) {
        manageFriends.execute(ManageFriendsInputData.remove(friendshipId));
    }
}
