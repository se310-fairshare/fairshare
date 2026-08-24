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
- Split an expense equally across the selected members, accurate to the cent
- Browse a group's expense history and edit an existing expense
- See each member's balance, and the transactions behind a single member's balance
- Generate a settlement plan for a group and mark individual payments as paid

The following is not built yet:

- Deleting an expense. Viewing and editing work, deletion has no endpoint or interface. See [issue #11](https://github.com/se310-fairshare/fairshare/issues/11).
- Exporting group data. Data is stored in MySQL and survives a restart, but nothing produces a download. See [issue #12](https://github.com/se310-fairshare/fairshare/issues/12).
- Splitting by percentage, shares or exact amounts. Only the equal split exists.

Work planned for the next iteration is tracked in the open issues, including individual debt tracking ([#2](https://github.com/se310-fairshare/fairshare/issues/2)), receipt scanning ([#5](https://github.com/se310-fairshare/fairshare/issues/5)), recurring expenses ([#13](https://github.com/se310-fairshare/fairshare/issues/13)), multi-currency support ([#14](https://github.com/se310-fairshare/fairshare/issues/14)), payment reminders ([#15](https://github.com/se310-fairshare/fairshare/issues/15)) and spending charts ([#16](https://github.com/se310-fairshare/fairshare/issues/16)).

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
```

Or create a `backend/.env` file, which is read as a properties file:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/fairshare
spring.datasource.username=your_mysql_user
spring.datasource.password=your_mysql_password
```

Use the lower case property names in the file. The `SPRING_DATASOURCE_URL` spelling only works as an environment variable, and a `.env` file written that way is read but ignored, which leaves the application failing with "Failed to determine a suitable driver class". Each line is a plain `key=value` pair with no `export`. The file is ignored by Git and must never be committed.

### 3. Run the backend

```bash
cd backend
./mvnw spring-boot:run
```

The backend serves `http://localhost:8080`. On Windows use `mvnw.cmd`. Flyway applies the migrations in `backend/src/main/resources/db/migration` as the application starts, so there is no schema to write by hand.

### 4. Run the frontend

In a second terminal, with the backend already running:

```bash
cd frontend
npm ci
npm run dev
```

The app is then at `http://localhost:5173`. Open it and register an account to get started. Register a second account as well if you want to try groups, because members are added by looking up an existing account.

### Things that catch people out

- **Start the backend first.** The frontend asks the backend who you are before it renders, so its pages fail rather than degrade when the backend is down.
- **The frontend has to be on port 5173.** The backend only accepts browser requests from `http://localhost:5173`. If that port is busy, Vite quietly moves to 5174 and every request then fails. Free port 5173 rather than using the fallback.
- **The backend has to be on port 8080.** The frontend has that address built in, at `frontend/src/api/config.js`.
- **`http://localhost:8080/` returns 401 in a browser.** There is no route at the root, and everything except registering and logging in needs a session, so an unauthenticated request there is rejected before the missing route is reached. With a session it returns 404. Either way the backend has started.
- **A fresh database has no accounts in it.** Register through the app.

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
src/utils/        Validation helpers
test/             Vitest specs
```

Frontend tests live in `frontend/test` rather than beside the source. `UserProfile.jsx`, the registration screen, sits at the root of `src/`.

## API

All responses are JSON, and every route except registering and logging in needs an authenticated session.

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
| POST | `/groups/{id}/settlement` | Generate a settlement plan |
| PATCH | `/groups/{id}/settlements/{fromUserId}/{toUserId}/paid` | Mark a payment as paid |
| POST | `/groups/{groupId}/expenses` | Record an expense |
| GET | `/groups/{groupId}/expenses` | A group's expense history |
| GET | `/groups/{groupId}/expenses/{expenseId}` | One expense |
| PUT | `/groups/{groupId}/expenses/{expenseId}` | Edit an expense |

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
