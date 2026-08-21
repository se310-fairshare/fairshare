package nz.ac.auckland.se310.fairshare.exception;

public class InvalidPayerException extends RuntimeException {

    public InvalidPayerException(Long userId) {
        super("Payer is not a member of the group: " + userId);
    }
}
