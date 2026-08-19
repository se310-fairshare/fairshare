package nz.ac.auckland.se310.fairshare.exception;

public class GroupAccessDeniedException extends RuntimeException {

    public GroupAccessDeniedException() {
        super("You must be a group member to manage its members");
    }
}
