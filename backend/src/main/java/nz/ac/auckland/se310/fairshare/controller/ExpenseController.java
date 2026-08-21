package nz.ac.auckland.se310.fairshare.controller;

import jakarta.validation.Valid;
import nz.ac.auckland.se310.fairshare.dto.CreateExpenseRequest;
import nz.ac.auckland.se310.fairshare.dto.ExpenseResponse;
import nz.ac.auckland.se310.fairshare.security.CurrentUserProvider;
import nz.ac.auckland.se310.fairshare.service.ExpenseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/groups/{groupId}/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final CurrentUserProvider currentUser;

    public ExpenseController(ExpenseService expenseService, CurrentUserProvider currentUser) {
        this.expenseService = expenseService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> create(@PathVariable Long groupId,
                                                    @Valid @RequestBody CreateExpenseRequest request) {
        ExpenseResponse created = expenseService.createExpense(groupId, request, currentUser.currentUserId());
        return ResponseEntity
                .created(URI.create("/groups/" + groupId + "/expenses/" + created.id()))
                .body(created);
    }

    @GetMapping
    public List<ExpenseResponse> list(@PathVariable Long groupId) {
        return expenseService.getExpensesForGroup(groupId, currentUser.currentUserId());
    }
}
