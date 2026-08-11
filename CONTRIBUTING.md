# Contributing to FairShare

FairShare records shared expenses for flatmates, trips, events and project teams, then calculates the simplest way to settle everyone's debts.
The project is developed by Team FairShare and is associated with the University of Auckland SOFTENG 310.

Contributions of every kind are welcome, and following these guidelines helps the team review and merge them quickly.

## Types of contributions

The project is looking for:

- Bug reports for reproducible problems
- Feature requests that fit the product described in the roadmap below
- Code that implements an approved issue, together with its tests
- Documentation improvements, including the README, this guide and the wiki
- Test coverage for existing behaviour
- Dependency upgrades that resolve a reported vulnerability

The project is not looking for:

- Changes without an associated approved issue
- Large refactor or reformatting that are unrelated to an open issue
- New dependencies where the standard library or an existing dependency is sufficient
- Changes to the technology stack agreed in the project proposal

## Ground rules

All participation is governed by the [Code of Conduct](CODE_OF_CONDUCT.md).

Technical expectations for any code contribution:

- Every code or documentation contribution is associated with an open, team-approved issue
- All additions and modifications to code include associated tests
- The existing test suite passes, and the application runs before a pull request is opened
- External dependencies are declared through the package manager, in `backend/pom.xml` or `frontend/package.json`
- Any use of generative AI tools is acknowledged in the pull request description

## Project vision and roadmap

The goal is a web application where a group records shared expenses and receives a settlement plan containing the fewest possible transfers.

Planned for the first release:

- User profiles ([#1](https://github.com/se310-fairshare/fairshare/issues/1))
- Group creation and management ([#3](https://github.com/se310-fairshare/fairshare/issues/3), [#4](https://github.com/se310-fairshare/fairshare/issues/4))
- Adding expenses and splitting them equally ([#6](https://github.com/se310-fairshare/fairshare/issues/6), [#8](https://github.com/se310-fairshare/fairshare/issues/8))
- Per-person expense display, history, editing and deletion ([#9](https://github.com/se310-fairshare/fairshare/issues/9), [#11](https://github.com/se310-fairshare/fairshare/issues/11))
- Settlement plan generation ([#10](https://github.com/se310-fairshare/fairshare/issues/10))
- Saving and exporting group data ([#12](https://github.com/se310-fairshare/fairshare/issues/12))
- MySQL persistence, SonarCloud analysis and Snyk scanning ([#22](https://github.com/se310-fairshare/fairshare/issues/22), [#20](https://github.com/se310-fairshare/fairshare/issues/20), [#21](https://github.com/se310-fairshare/fairshare/issues/21))

Deferred to the second release:

- Individual debt tracking ([#2](https://github.com/se310-fairshare/fairshare/issues/2))
- Receipt scanning ([#5](https://github.com/se310-fairshare/fairshare/issues/5))
- Recurring expenses ([#13](https://github.com/se310-fairshare/fairshare/issues/13))
- Multi-currency support ([#14](https://github.com/se310-fairshare/fairshare/issues/14))
- Payment reminders ([#15](https://github.com/se310-fairshare/fairshare/issues/15))
- Charts and monthly summaries ([#16](https://github.com/se310-fairshare/fairshare/issues/16))

## Architecture overview

The repository holds two independent applications.

```
backend/    Spring Boot 4.1 REST API, Java 21, built with the Maven wrapper
frontend/   React 19 single-page application, built with Vite 8
.github/    Issue and pull request templates, and the CI workflow
```

The frontend is a single-page application that calls the backend over HTTP and holds no business logic of its own.
The backend owns expense calculation and settlement, exposed as a REST API under the `nz.ac.auckland.se310.fairshare` package.
Persistence through MySQL and Spring Data JPA is to be added under issue [#22](https://github.com/se310-fairshare/fairshare/issues/22); until then the backend keeps no state between restarts.
Backend testing uses JUnit and Mockito.
Frontend testing with Vitest is to be added.

## First contributions

Issues labelled [`good first issue`](https://github.com/se310-fairshare/fairshare/labels/good%20first%20issue) are the best place to start, as they are small and need little context.
Issues labelled [`help wanted`](https://github.com/se310-fairshare/fairshare/labels/help%20wanted) are also open to newcomers but assume more familiarity with the codebase.

Newcomers unfamiliar with the fork and pull request model may find the GitHub guide on [contributing to a project](https://docs.github.com/en/get-started/exploring-projects-on-github/contributing-to-a-project) useful.

## Setting up a development environment

Prerequisites:

- JDK 21
- Node.js 22 or later
- Git

Maven does not need to be installed, as the repository includes the Maven wrapper.

Clone a fork of the repository, then start either application.

Backend, served on port 8080:

```bash
cd backend
./mvnw spring-boot:run
```

No endpoints exist yet, so a request to `/` returns 404 until the first controller is added.

Frontend, served on port 5173:

```bash
cd frontend
npm ci
npm run dev
```

## Running the tests

The backend test suite runs through the Maven wrapper:

```bash
cd backend
./mvnw --batch-mode clean verify
```

The frontend has no test harness yet, so verification is limited to a production build:

```bash
cd frontend
npm ci
npm run build
```

Both commands are run by the CI workflow on every push to `main` and on every pull request targeting `main`.

## The contribution workflow

1. Find or create an issue for the work and wait for the team to approve it.
2. Check the open issues for dependencies and note any in a comment as `Depends on #N` or `Blocks #N`.
3. Claim the issue by assigning it or by commenting and asking a maintainer to assign it.
   Only one open issue may be claimed at a time.
4. Fork the repository and clone the fork.
5. Create a feature branch from `main`, named as described under the conventions below.
6. Make the changes in the feature branch, adding tests alongside any code.
7. Rebase against `main` regularly rather than waiting until the work is complete.
8. Run the test suite and the application and confirm the change behaves as expected.
9. Open a pull request against `main` in this repository, following the pull request template.
10. Address review feedback, then squash the commits before the pull request is merged.

The pull request title summarises the change rather than repeating the issue number.
The description explains what changed and references the issue, for example `Closes #24`.

## How to report a bug

Bugs are reported through the [bug report template](https://github.com/se310-fairshare/fairshare/issues/new?template=bug_report.md).

Before opening a report, confirm that the problem reproduces against the latest `main`, and search the open issues to avoid duplicates.
A useful report gives a description, numbered steps to reproduce, the actual behaviour with any error messages, the expected behaviour, and the branch or commit tested.

Reports that omit reproduction steps may be closed with a request for more detail.
Where more information is requested, the reporter is expected to respond.

## How to suggest a feature or enhancement

Features are proposed through the [feature request template](https://github.com/se310-fairshare/fairshare/issues/new?template=feature_request.md).

A proposal is written as a user story, stating the type of user, the goal and the benefit, followed by acceptance criteria in the given /when/then form.
Proposals are assessed on whether they fit the product vision above, whether they duplicate an existing issue and whether they depend on work that is not yet complete.

The team approves new issues before any work begins.
Approval confirms that the request suits the product, that no duplicate exists and that dependencies are flagged.
Approval is recorded as a comment on the issue.

## Code review process

Every pull request is reviewed by two team members, and both must formally approve it before it can be merged.
A comment alone does not count as an approval.

A review includes running the test suite, running the application and confirming the change behaves as expected, which keeps broken builds off `main`.
Problems found during review are fixed in the same pull request rather than deferred to a new issue.

Once both approvals are in place, the author or a delegated team member squashes the commits and merges.
Merging without the required approvals is not permitted.

## Conventions

**Branch names** take the form `<type>/<issue-number>-<short-description>`, for example `docs/24-contributor-guidelines`.
The type is one of `feat`, `fix`, `docs`, `build`, `refactor`, `test` or `chore`.

**Commit messages** use a conventional prefix, then a colon and a brief description, for example `docs: add contributor guidelines`.
The message states what changed and, where it is not obvious, why.

**Labels** are the GitHub defaults, and the project defines no custom labels.
`bug` and `enhancement` are applied automatically by the issue templates.
`documentation` marks documentation work, `good first issue` marks issues suitable for newcomers and `help wanted` marks issues open to anyone.

**Code style** follows the conventions already present in each application.
Java code uses standard Java naming and four-space indentation.
JavaScript and JSX use two-space indentation.

## Getting in touch

All project communication happens in GitHub issues and pull requests, which keeps decisions recorded against the work they concern.
Questions about a specific issue or pull request belong in a comment on that issue or pull request.
General questions are raised as a new issue with the `question` label.

The team does not provide support by email or private message.
