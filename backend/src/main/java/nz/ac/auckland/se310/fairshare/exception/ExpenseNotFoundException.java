package nz.ac.auckland.se310.fairshare.exception;

public class ExpenseNotFoundException extends RuntimeException {

    public ExpenseNotFoundException() {
        super("Expense not found in group");
    }
}