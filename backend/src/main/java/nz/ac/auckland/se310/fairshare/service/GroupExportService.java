package nz.ac.auckland.se310.fairshare.service;

import nz.ac.auckland.se310.fairshare.dto.MemberBalance;
import nz.ac.auckland.se310.fairshare.exception.GroupAccessDeniedException;
import nz.ac.auckland.se310.fairshare.model.Expense;
import nz.ac.auckland.se310.fairshare.model.ExpenseGroup;
import nz.ac.auckland.se310.fairshare.model.ExpenseShare;
import nz.ac.auckland.se310.fairshare.model.User;
import nz.ac.auckland.se310.fairshare.model.UserInGroup;
import nz.ac.auckland.se310.fairshare.repository.ExpenseGroupRepository;
import nz.ac.auckland.se310.fairshare.repository.ExpenseRepository;
import nz.ac.auckland.se310.fairshare.repository.ExpenseShareRepository;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * #12: builds downloadable exports of a group's expense data. Everything is read from the
 * database on each call, so an export always reflects the group as it is right now (AC3).
 */
@Service
public class GroupExportService {

    // Excel only reads a CSV as UTF-8 when it starts with a byte order mark (AC5).
    private static final String UTF8_BOM = "\uFEFF";
    private static final String CSV_LINE_END = "\r\n";
    private static final List<String> CSV_EXPENSE_HEADER = List.of(
            "Date", "Description", "Amount", "Currency",
            "Original Amount", "Original Currency", "Paid By", "Split");
    private static final List<String> MEMBER_HEADER = List.of(
            "Member", "Total Paid", "Total Share", "Net Balance");
    private static final String FORMER_MEMBER_SUFFIX = " (former member)";
    private static final Pattern PLAIN_NUMBER = Pattern.compile("[+-]?\\d+(\\.\\d+)?");

    // Bundled with OpenPDF. Embedded so accents, symbols and non-Latin scripts render, which the
    // built-in PDF fonts cannot do beyond Western European characters.
    private static final String UNICODE_FONT = "font-fallback/LiberationSans-Regular.ttf";
    private static final Color HEADER_BACKGROUND = new Color(230, 230, 230);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final ExpenseGroupRepository groupRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository expenseShareRepository;
    private final SettlementService settlementService;
    private final Clock clock;

    public GroupExportService(ExpenseGroupRepository groupRepository, ExpenseRepository expenseRepository,
                              ExpenseShareRepository expenseShareRepository, SettlementService settlementService,
                              Clock clock) {
        this.groupRepository = groupRepository;
        this.expenseRepository = expenseRepository;
        this.expenseShareRepository = expenseShareRepository;
        this.settlementService = settlementService;
        this.clock = clock;
    }

    /**
     * Generates the export in the requested format, named after the group and today's date,
     * e.g. {@code fairshare-flat-3-2026-10-11.csv}.
     */
    @Transactional(readOnly = true)
    public ExportedFile export(Long groupId, Long currentUserId, ExportFormat format) {
        ExpenseGroup group = groupRepository.findByIdAndMembersUserId(groupId, currentUserId)
                .orElseThrow(GroupAccessDeniedException::new); // AC6
        GroupExportData data = collect(group, currentUserId);
        byte[] content = switch (format) {
            case CSV -> csv(data);
            case PDF -> pdf(data);
        };
        return new ExportedFile(filename(group, format), format, content);
    }

    /** Everything both formats show, read once per export. */
    private record GroupExportData(ExpenseGroup group, String currency, List<Expense> expenses,
                                   Map<Long, List<ExpenseShare>> sharesByExpense, List<MemberRow> members) {
    }

    /** A null net balance marks a former member, who had to settle up before leaving the group. */
    private record MemberRow(String name, BigDecimal totalPaid, BigDecimal totalShare, BigDecimal netBalance) {
    }

    private GroupExportData collect(ExpenseGroup group, Long currentUserId) {
        Long groupId = group.getId();
        List<Expense> expenses = expenseRepository.findByGroupIdOrderByExpenseDateAscIdAsc(groupId);
        List<ExpenseShare> shares = expenseShareRepository.findByExpenseGroupId(groupId);

        // The same calculation as GET /groups/{id}/balances, so the two never disagree.
        Map<Long, BigDecimal> netBalances = settlementService.getBalances(groupId, currentUserId).stream()
                .collect(Collectors.toMap(MemberBalance::userId, MemberBalance::balance));
        Map<Long, BigDecimal> totalPaid = expenses.stream()
                .collect(Collectors.groupingBy(expense -> expense.getPaidBy().getId(),
                        Collectors.reducing(ZERO, Expense::getAmount, BigDecimal::add)));
        Map<Long, BigDecimal> totalShare = shares.stream()
                .collect(Collectors.groupingBy(share -> share.getUser().getId(),
                        Collectors.reducing(ZERO, ExpenseShare::getShareAmount, BigDecimal::add)));

        List<User> currentMembers = group.getMembers().stream().map(UserInGroup::getUser).toList();
        Set<Long> currentMemberIds = currentMembers.stream().map(User::getId).collect(Collectors.toSet());
        // AC4 and reviewer feedback: anyone who paid for or shared in an expense but has since been
        // removed still gets a row, so the paid and share columns add up to the expense list.
        List<User> formerMembers = Stream.concat(
                        expenses.stream().map(Expense::getPaidBy),
                        shares.stream().map(ExpenseShare::getUser))
                .filter(user -> !currentMemberIds.contains(user.getId()))
                .collect(Collectors.toMap(User::getId, user -> user, (first, second) -> first, LinkedHashMap::new))
                .values().stream().toList();

        List<MemberRow> members = Stream.concat(
                byUsername(currentMembers).map(user -> new MemberRow(user.getUsername(),
                        totalPaid.get(user.getId()), totalShare.get(user.getId()),
                        netBalances.getOrDefault(user.getId(), ZERO))),
                byUsername(formerMembers).map(user -> new MemberRow(user.getUsername() + FORMER_MEMBER_SUFFIX,
                        totalPaid.get(user.getId()), totalShare.get(user.getId()), null)))
                .toList();

        return new GroupExportData(group, group.getBaseCurrency().name(), expenses,
                shares.stream().collect(Collectors.groupingBy(share -> share.getExpense().getId())), members);
    }

    /**
     * AC1, AC5: one row per expense, oldest first, under a labelled header, followed by a member
     * balance section. A group with no expenses still gets both headers and every member at zero (AC4).
     */
    private byte[] csv(GroupExportData data) {
        StringBuilder csv = new StringBuilder(UTF8_BOM);
        appendCsvRow(csv, CSV_EXPENSE_HEADER);
        for (Expense expense : data.expenses()) {
            appendCsvRow(csv, List.of(
                    expense.getExpenseDate().toString(),
                    expense.getDescription(),
                    expense.getAmount().toPlainString(),
                    data.currency(),
                    expense.getOriginalAmount().toPlainString(),
                    expense.getOriginalCurrency(),
                    expense.getPaidBy().getUsername(),
                    splitSummary(data.sharesByExpense().getOrDefault(expense.getId(), List.of()))));
        }

        csv.append(CSV_LINE_END);
        appendCsvRow(csv, List.of("Member balances (" + data.currency() + ")"));
        appendCsvRow(csv, MEMBER_HEADER);
        for (MemberRow member : data.members()) {
            appendCsvRow(csv, List.of(
                    member.name(),
                    money(member.totalPaid()),
                    money(member.totalShare()),
                    member.netBalance() == null ? "" : money(member.netBalance())));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * AC2: the expense list followed by each member's total paid, total share and net balance.
     * A group with no expenses still lists every member, at zero (AC4).
     */
    private byte[] pdf(GroupExportData data) {
        ExpenseGroup group = data.group();
        String currency = data.currency();

        BaseFont baseFont = loadUnicodeFont();
        Font titleFont = new Font(baseFont, 16, Font.BOLD);
        Font headingFont = new Font(baseFont, 12, Font.BOLD);
        Font bodyFont = new Font(baseFont, 9);
        Font headerCellFont = new Font(baseFont, 9, Font.BOLD);
        Font noteFont = new Font(baseFont, 8, Font.ITALIC, Color.DARK_GRAY);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // Landscape so the split column has room on a page.
        try (Document document = new Document(PageSize.A4.rotate())) {
            PdfWriter.getInstance(document, out);
            document.open();

            document.add(new Paragraph(group.getGroupName(), titleFont));
            if (group.getDescription() != null && !group.getDescription().isBlank()) {
                document.add(new Paragraph(group.getDescription(), bodyFont));
            }
            document.add(new Paragraph(
                    "Exported " + LocalDate.now(clock) + ". All amounts in " + currency + ".", bodyFont));

            document.add(sectionHeading("Expenses", headingFont));
            if (data.expenses().isEmpty()) {
                document.add(new Paragraph("No expenses recorded.", bodyFont));
            } else {
                PdfPTable table = table(new float[] {1.2f, 3f, 1.4f, 1.2f, 3.6f},
                        List.of("Date", "Description", "Amount", "Paid By", "Split"), headerCellFont);
                for (Expense expense : data.expenses()) {
                    table.addCell(cell(expense.getExpenseDate().toString(), bodyFont, Element.ALIGN_LEFT));
                    table.addCell(cell(expense.getDescription(), bodyFont, Element.ALIGN_LEFT));
                    table.addCell(cell(amountWithOriginal(expense, currency), bodyFont, Element.ALIGN_RIGHT));
                    table.addCell(cell(expense.getPaidBy().getUsername(), bodyFont, Element.ALIGN_LEFT));
                    table.addCell(cell(splitSummary(data.sharesByExpense().getOrDefault(expense.getId(), List.of())),
                            bodyFont, Element.ALIGN_LEFT));
                }
                document.add(table);
            }

            document.add(sectionHeading("Member balances", headingFont));
            PdfPTable balances = table(new float[] {3f, 1.5f, 1.5f, 1.5f}, MEMBER_HEADER, headerCellFont);
            boolean hasFormerMembers = false;
            for (MemberRow member : data.members()) {
                hasFormerMembers |= member.netBalance() == null;
                balances.addCell(cell(member.name(), bodyFont, Element.ALIGN_LEFT));
                balances.addCell(cell(money(member.totalPaid()), bodyFont, Element.ALIGN_RIGHT));
                balances.addCell(cell(money(member.totalShare()), bodyFont, Element.ALIGN_RIGHT));
                balances.addCell(cell(member.netBalance() == null ? "–" : money(member.netBalance()),
                        bodyFont, Element.ALIGN_RIGHT));
            }
            document.add(balances);
            document.add(new Paragraph(
                    "A positive net balance means the member owes money, a negative one means they are "
                            + "owed. Net balance includes settlement payments marked as paid, so it can differ "
                            + "from total share minus total paid.", noteFont));
            if (hasFormerMembers) {
                document.add(new Paragraph(
                        "Former members are listed so the paid and share columns match the expenses above. "
                                + "A member must settle up before leaving, so they have no net balance.", noteFont));
            }
        }
        return out.toByteArray();
    }

    // Letters and digits in any script are kept; everything else becomes a single hyphen.
    private String filename(ExpenseGroup group, ExportFormat format) {
        String slug = group.getGroupName().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "-")
                .replaceAll("^-|-$", "");
        return "fairshare-" + (slug.isEmpty() ? "group" : slug) + "-" + LocalDate.now(clock)
                + "." + format.extension();
    }

    // Matches the order of the group's member list.
    private static Stream<User> byUsername(List<User> users) {
        return users.stream().sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER));
    }

    // e.g. "alice: 10.00; bob: 10.00", in user id order to match how leftover cents are allocated.
    private String splitSummary(List<ExpenseShare> shares) {
        return shares.stream()
                .sorted(Comparator.comparing(share -> share.getUser().getId()))
                .map(share -> share.getUser().getUsername() + ": " + share.getShareAmount().toPlainString())
                .collect(Collectors.joining("; "));
    }

    // e.g. "34.11 (USD 20.00)" for an expense entered in another currency.
    private String amountWithOriginal(Expense expense, String currency) {
        String amount = expense.getAmount().toPlainString();
        if (currency.equals(expense.getOriginalCurrency())) {
            return amount;
        }
        return amount + " (" + expense.getOriginalCurrency() + " " + expense.getOriginalAmount().toPlainString() + ")";
    }

    private String money(BigDecimal value) {
        return (value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP)).toPlainString();
    }

    private Paragraph sectionHeading(String text, Font font) {
        Paragraph heading = new Paragraph(text, font);
        heading.setSpacingBefore(14);
        heading.setSpacingAfter(6);
        return heading;
    }

    private PdfPTable table(float[] widths, List<String> headers, Font font) {
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setHeaderRows(1); // repeats the header when the table runs onto another page
        for (String header : headers) {
            PdfPCell cell = cell(header, font, Element.ALIGN_LEFT);
            cell.setBackgroundColor(HEADER_BACKGROUND);
            table.addCell(cell);
        }
        return table;
    }

    private PdfPCell cell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(4);
        return cell;
    }

    private static BaseFont loadUnicodeFont() {
        try (InputStream font = GroupExportService.class.getClassLoader().getResourceAsStream(UNICODE_FONT)) {
            if (font == null) {
                throw new IllegalStateException("PDF font not found on the classpath: " + UNICODE_FONT);
            }
            return BaseFont.createFont(UNICODE_FONT, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, false,
                    font.readAllBytes(), null);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load the PDF font", e);
        }
    }

    private void appendCsvRow(StringBuilder csv, List<String> cells) {
        csv.append(cells.stream().map(GroupExportService::csvCell).collect(Collectors.joining(",")));
        csv.append(CSV_LINE_END);
    }

    /**
     * AC5: quotes a cell holding a comma, quote or line break, doubling any quotes inside it.
     * A cell that Excel would read as a formula is prefixed with an apostrophe so it shows as text.
     * Unlike a typed one, Excel displays that apostrophe, so "-5 refund" appears as "'-5 refund".
     * That is the trade-off OWASP recommends for CSV injection, and is kept deliberately.
     */
    static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String cell = startsLikeFormula(value) ? "'" + value : value;
        if (cell.contains(",") || cell.contains("\"") || cell.contains("\n") || cell.contains("\r")) {
            return "\"" + cell.replace("\"", "\"\"") + "\"";
        }
        return cell;
    }

    // A plain number such as a negative balance is safe, since Excel reads it as a value, not a formula.
    private static boolean startsLikeFormula(String value) {
        return !value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0
                && !PLAIN_NUMBER.matcher(value).matches();
    }
}
