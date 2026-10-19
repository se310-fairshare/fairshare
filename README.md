# FairShare

FairShare is an open source web app for recording shared expenses and working out the simplest way to settle up. Flatmates, trips, events and project teams can create a group, record who paid for what, and get a settlement plan that clears everyone's debts in as few payments as possible.

This project is built by Group 2, FairShare, in association with the University of Auckland SOFTENG 310.

## What is built

The following works today:

- Register an account, log in and log out, using server-side session authentication
- View and edit your own profile
- Create a group and see the groups you belong to
- Add members by email or username, remove members and leave a group
- Record an expense with an amount, description, payer, date and the members it applies to
- Scan a JPG or PNG receipt into editable items and prices, then apply its total to a new expense
- Record an expense in any of 23 supported currencies. It is converted into the group's currency at the rate for the expense date, and the history shows both, e.g. `USD 20.00 (≈ NZD 34.11)`
- Choose any supported currency as your home currency, which becomes the currency of groups you create
- Split an expense equally across the selected members, accurate to the cent
- Browse a group's expense history. Only the creator can edit or delete an expense, and deletion requires confirmation
- View who edited or deleted an expense and when in the group's Expense activity section. Saving unchanged details does not add an activity entry
- Set up a recurring expense, such as rent or a subscription, with a weekly, fortnightly or monthly schedule and an optional end date; it generates expenses automatically, and each generated expense is marked in the history and links back to the recurring expense that created it
- See each member's balance, and the transactions behind a single member's balance
- Generate a settlement plan for a group and mark individual payments as paid. Reopening or regenerating the plan uses current balances; deleting an expense refreshes the displayed plan and preserves completed payments
- Export a group's expense data from the group page, as a CSV file that opens cleanly in Excel or as a PDF. Both list every expense followed by each member's total paid, total share and net balance, including anyone who has since left the group

The following is not built yet:

- Editing, pausing or deleting a recurring expense once it exists. Only creating and viewing them work.
- Splitting by percentage, shares or exact amounts. Only the equal split exists.

Work planned for the next iteration is tracked in the open issues, including individual debt tracking ([#2](https://github.com/se310-fairshare/fairshare/issues/2)), payment reminders ([#15](https://github.com/se310-fairshare/fairshare/issues/15)) and spending charts ([#16](https://github.com/se310-fairshare/fairshare/issues/16)).

## Technology stack

- **Language**: Java 21, JavaScript
- **Backend**: Spring Boot 4.1 with Spring MVC and Spring Security
- **Frontend**: React 19 with React Router, built by Vite 8
- **Database**: MySQL 8.4
- **Data access**: Spring Data JPA and Hibernate, with Flyway for schema migrations
- **Build tool**: Maven, through the committed wrapper
- **Testing**: JUnit, Mockito and Testcontainers on the backend, Vitest with React Testing Library and jsdom on the frontend
- **CI**: GitHub Actions
- **Code quality**: SonarCloud, SonarLint and Snyk

## Prerequisites

- JDK 21
- Node.js 22 or later
- MySQL 8.4
- Docker, needed to run the backend test suite
- Git

Maven does not need to be installed. The repository includes the Maven wrapper at `backend/mvnw`.

The wiki has step by step install guides for MySQL and Docker on [macOS](https://github.com/se310-fairshare/fairshare/wiki/macOS-Setup-MySQL-and-Docker) and [Windows](https://github.com/se310-fairshare/fairshare/wiki/Windows-Setup-MySQL-and-Docker).

## Getting started

### 1. Create the database

Follow the setup page for your platform, [macOS](https://github.com/se310-fairshare/fairshare/wiki/macOS-Setup-MySQL-and-Docker) or [Windows](https://github.com/se310-fairshare/fairshare/wiki/Windows-Setup-MySQL-and-Docker).

### 2. Configure the backend

The backend needs a database URL, a username and a password. There are two ways to supply them, and each uses a different spelling of the same settings.

Either set them as environment variables:

```bash
export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/fairshare"
export SPRING_DATASOURCE_USERNAME="your_mysql_user"
export SPRING_DATASOURCE_PASSWORD="your_mysql_password"
export GEMINI_API_KEY="your_gemini_api_key"
```

Or create a `backend/.env` file, which is read as a properties file:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/fairshare
spring.datasource.username=your_mysql_user
spring.datasource.password=your_mysql_password
gemini.api-key=your_gemini_api_key
```

Use the lower case property names in the file. The `SPRING_DATASOURCE_URL` spelling only works as an environment variable, and a `.env` file written that way is read but ignored, which leaves the application failing with "Failed to determine a suitable driver class". Each line is a plain `key=value` pair with no `export`. The file is ignored by Git and must never be committed.

The Gemini key is optional. Receipt scanning uses Gemini for primary structured extraction when the key is configured and falls back to local Tesseract OCR when Gemini is unavailable. Receipt images are processed in memory and are not saved by FairShare. Copy `backend/.env.example` for the complete local configuration template.

### 3. Run the backend

```bash
cd backend
./mvnw spring-boot:run
```

The backend serves `http://localhost:8080`. On Windows use `mvnw.cmd`. Flyway applies the migrations in `backend/src/main/resources/db/migration` as the application starts, so there is no schema to write by hand. The schema is created by a single baseline migration, `V1__initial_schema.sql`. A local database created before this baseline was introduced still records the old migration history and fails Flyway validation, so drop and recreate it (`DROP DATABASE fairshare; CREATE DATABASE fairshare;`, using your database name) and let the application rebuild it.

### 4. Run the frontend

In a second terminal, with the backend already running:

```bash
cd frontend
npm ci
npm run dev
```

The app is then at `http://localhost:5173`. Open it and register an account to get started. Register a second account as well if you want to try groups, because members are added by looking up an existing account.

### Test from a mobile device

Mobile testing needs the frontend and backend to be reachable from other devices on the local network. Only do this on a trusted network, and do not expose the development servers directly to the internet.

1. Connect the development computer and mobile device to the same local network, then find the computer's local network address. The examples below use `<development-machine-ip>` as a placeholder.
2. Start Vite on all network interfaces instead of its default loopback-only interface:

   ```bash
   cd frontend
   npm run dev -- --host 0.0.0.0 --port 5173
   ```

3. Make the frontend call a backend address the phone can reach. `localhost` on the phone refers to the phone itself, so replace a hard-coded `http://localhost:8080` API base with either a configurable value or the hostname that served the frontend. For example:

   ```js
   export const API_BASE = `http://${window.location.hostname}:8080`;
   ```

4. Add `http://<development-machine-ip>:5173` to the backend's allowed CORS origins. Keep the existing localhost origin so desktop development continues to work. If the backend has been configured to listen only on loopback, configure it to listen on the local network as well.
5. Allow inbound connections to the frontend and backend development ports in the computer's firewall if they are blocked.
6. On the phone, open `http://<development-machine-ip>:5173` and register or log in normally.

Opening the phone's native camera or gallery can suspend the browser and disconnect Vite's hot-reload client. If the page refreshes when returning to the app, test against a production build served by Vite preview instead:

```bash
cd frontend
npm run build
npx vite preview --host 0.0.0.0 --port 5173
```

Restart the backend after changing its CORS configuration. These network-access changes are intended for local testing only and should not be committed with a personal network address.

### Things that catch people out

- **Start the backend first.** The frontend asks the backend who you are before it renders, so its pages fail rather than degrade when the backend is down.
- **The frontend has to be on port 5173.** The backend only accepts browser requests from `http://localhost:5173`. If that port is busy, Vite quietly moves to 5174 and every request then fails. Free port 5173 rather than using the fallback.
- **The backend has to be on port 8080.** The frontend has that address built in, at `frontend/src/api/config.js`.
- **`http://localhost:8080/` returns 401 in a browser.** There is no route at the root, and everything except registering and logging in needs a session, so an unauthenticated request there is rejected before the missing route is reached. With a session it returns 404. Either way the backend has started.
- **A fresh database has no accounts in it.** Register through the app.
- **Foreign-currency expenses need internet access.** Exchange rates come from the free [Frankfurter API](https://frankfurter.dev), which publishes European Central Bank reference rates and needs no key. If it cannot be reached, an expense in another currency is rejected with an explanation and nothing is saved. Expenses in the group's own currency never call it. The address is set by `fairshare.exchange-rate.base-url` in `application.properties`.
- **Receipt scanning works without a Gemini key, but extraction quality may be lower.** Without the key—or if Gemini fails—the backend automatically uses its local Tesseract fallback.

## Running the tests

The backend suite needs Docker running, because the integration tests start a MySQL container through Testcontainers. They fail rather than skip without it. They do not use your local database or credentials.

```bash
cd backend
./mvnw --batch-mode clean verify
```

The frontend suite needs nothing else running:

```bash
cd frontend
npm test
```

And the production build:

```bash
cd frontend
npm run build
```

GitHub Actions runs all three on every push to `main` and on every pull request against it.

## Repository structure

```
backend/    Spring Boot API
frontend/   React single page app
.github/    Issue and pull request templates, CI workflow
```

Inside the backend, under `src/main/java/nz/ac/auckland/se310/fairshare`:

```
controller/   REST controllers for users, groups and expenses
service/      Business logic, including the settlement calculation
repository/   Spring Data repositories
model/        JPA entities
dto/          Request and response types
security/     Session authentication and current-user lookup
exception/    Application exceptions and the global handler
```

`UserService` and `UserRepository` sit in the base package rather than in `service/` and `repository/`. Database migrations live in `backend/src/main/resources/db/migration`, and the tests are in `backend/src/test/java`.

Inside the frontend:

```
src/api/          Backend calls and the shared fetch wrapper
src/pages/        One file per screen
src/components/   Shared components
src/utils/        Validation and formatting helpers
test/             Vitest specs
```

Frontend tests live in `frontend/test` rather than beside the source. `UserProfile.jsx`, the registration screen, sits at the root of `src/`.

## API

All responses are JSON except the export, which is a file download, and every route except registering, logging in and listing currencies needs an authenticated session.

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/users/register` | Create an account |
| POST | `/users/login` | Start a session |
| POST | `/users/logout` | End the session |
| GET | `/users/me` | The signed-in user |
| PUT | `/users/me` | Update the signed-in user |
| POST | `/groups` | Create a group |
| GET | `/groups` | Groups you belong to |
| GET | `/groups/{id}` | One group |
| GET | `/groups/{id}/members` | Members of a group |
| POST | `/groups/{id}/members` | Add a member |
| DELETE | `/groups/{id}/members/{userId}` | Remove a member |
| GET | `/groups/{id}/balances` | Balance per member |
| GET | `/groups/{id}/export?format=csv\|pdf` | Download the group's expenses as CSV or PDF |
| POST | `/groups/{id}/settlement` | Generate a settlement plan |
| PATCH | `/groups/{id}/settlements/{fromUserId}/{toUserId}/paid` | Mark a payment as paid |
| POST | `/groups/{groupId}/expenses` | Record an expense |
| GET | `/groups/{groupId}/expenses` | A group's expense history |
| GET | `/groups/{groupId}/expenses/{expenseId}` | One expense |
| PUT | `/groups/{groupId}/expenses/{expenseId}` | Edit an expense (creator only) |
| DELETE | `/groups/{groupId}/expenses/{expenseId}` | Delete an expense (creator only) |
| GET | `/groups/{groupId}/expenses/activity` | Expense edit/delete activity for current group members |
| POST | `/groups/{groupId}/receipts/extract` | Extract editable purchase details from a receipt image |
| POST | `/groups/{groupId}/recurring-expenses` | Create a recurring expense |
| GET | `/groups/{groupId}/recurring-expenses` | A group's recurring expenses |
| GET | `/groups/{groupId}/recurring-expenses/{recurringExpenseId}` | One recurring expense |
| GET | `/currencies` | Supported currencies with their ISO 4217 codes |

## Contributing

Contributions are welcome. [CONTRIBUTING.md](CONTRIBUTING.md) covers the architecture, the [development environment](CONTRIBUTING.md#setting-up-a-development-environment), [how to run the tests](CONTRIBUTING.md#running-the-tests), the branch and pull request workflow, how issues are approved and the code review process.

Issues labelled `good first issue` are the place to start. Every code or documentation change starts from an open, team-approved issue.

Everyone taking part is expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## License

FairShare is released under the MIT License. See [LICENSE](LICENSE) for the full text.

## Contact

The project is reached through GitHub. Questions about a specific issue or pull request belong in a comment on that issue or pull request.

To report a Code of Conduct violation or private concerns, contact us through email at [jhua455@aucklanduni.ac.nz](mailto:jhua455@aucklanduni.ac.nz).

## Acknowledgements

Thanks to our contributors Aditeya, Adrian, Jago, Jason, Johan, Louis and Suah.
