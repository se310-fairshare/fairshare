package nz.ac.auckland.se310.fairshare.exception;

public class GroupNotFoundException extends RuntimeException {

    public GroupNotFoundException(Long groupId) {
        super("Group not found: " + groupId);
    }
}