<!-- markdownlint-disable MD033 -->
# CONTRIBUTING

## Standard Format

- Standard CHANGELOG Format: `Keep A Changelog` specification
- Standard Commit Message Format: `Conventional Commits` specification
  - Non-breaking change - `type(scope): subject` or `type: subject`
  - Breaking change - `type(scope)!: subject` or `type!: subject`

We try to handle this as best we can, but it isn't a perfect match.

The `Keep A Changelog` specification lists Added, Changed, Deprecated, Removed, Fixed and Security as valid Sections. We have added a couple (Documentation and Testing), as our customers are usually interested in these types of activities. There are now eight potential sections in each release.

The `Conventional Commits` specification lists feat:, fix:, build:, chore:, ci:, docs:, style:, refactor:, perf: and test: as valid types. We skip some (build:, chore:, ci: and style:) and have added a few (security:, deprecated: and removed:), giving us a total of thirteen types.

## AI Instructions

If using AI to generate commit messages, instructions can be provided to help it out.

[.github/copilot-commit-message-instructions.md](.github/copilot-commit-message-instructions.md)  
[.github/copilot-pull-request-description-instructions.md](.github/copilot-pull-request-description-instructions.md)

## Core Rules

- Core Rules:
  1. subject line should stay under 50 characters, all lowercase,
  2. start with an imperative verb (i.e. "add" instead of "added")
  3. omit a final period, it's unnecessary, we can see that it ended
  4. additional details, if necessary, may follow after a blank line, best to use a short description or bulleted points
  5. body should wrap at 72 characters
  6. ticket references (i.e., JIRA-123 or #123) should either be prefixed to the subject or in a footer (after another blank line) of the body
  7. scope is a reference to something to do with the code base
     1. i.e. ui, docs, security, api, regex, REST
     2. not actions like removed, deprecated

## CHANGELOG Generation

CHANGELOG is auto-generated using git-cliff. Rules have been laid out to best fit the two formats. Very strong reasoning will need to be included if you wish to change them.

Here is the VSCode task I use to generate the CHANGELOG.

```plain
// ---- Generate CHANGELOG.md ---------------------------------------------
{
  "label": "Generate CHANGELOG.md",
  "type": "shell",
  "command": "git cliff -vv -o 2>&1 | grep 'Skipping commit' | sed -E 's/^.*Grouping error: //; s/`Skipping commit`/Skipping Commit :/'"
},
```

### Message rewrites

- The type and scope are removed. Don't rely on them providing information in the CHANGELOG
- The first letter of the subject is capitalized.

### Sorting Rules

Rules apply in the order they are given (FIFO or First Come First Served). Anything matching these patterns gets put into the specified section of the CHANGELOG.

- A few types are skipped. These are considered routine maintenance and would balloon the CHANGELOG beyond reasonable use. You should use them if they apply, but know that they won't make it into the CHANGELOG.

  ```plain
  "^build"                        -> skipped
  "^chore"                        -> skipped
  "^ci"                           -> skipped
  "^style"                        -> skipped
  ```

- A few long forms for existing types just to be thorough

  ```plain
  "^feature"                      -> Added
  "^performance"                  -> Changed
  "^documentation"                -> Documentation
  ```

- A few new types to fill in the gaps, keep rewrite rules to a minimum

  ```plain
  "^changed"                      -> Changed
  "^removed"                      -> Removed
  "^sec"                          -> Security
  "^deprecated"                   -> Deprecated
  ```

- More long forms

  ```plain
  "^security"                     -> Security
  ```

- Then, these rules apply.

  ```plain
  "^feat"                         -> Added
  "^fix"                          -> Fixed
  "^perf"                         -> Changed
  "^refactor"                     -> Changed
  "^docs"                         -> Documentation
  "^test"                         -> Testing
  ```

- Anything left over, gets stuck under Other. There shouldn't be anything here if the above rules for type(scope) were strictly adhered to.

  ```plain
  ".*"                            -> Other
  ```

### Example Rules

<table>
<tr>
<td>feat(ui): add user login page</td>
<td>Added</td>
<td>Add user login page</td>
</tr>
<tr>
<td>chore: run database migrations on production</td>
<td></td>
<td></td>
</tr>
<tr>
<td>feat(auth): add JWT refresh token endpoint</td>
<td>Added</td>
<td>Add JWT refresh token endpoint</td>
</tr>
<tr>
<td>refactor(payments)!: drop support for legacy Stripe v2 API</td>
<td>Changed</td>
<td>BREAKING CHANGE: Drop support for legacy Stripe v2 API</td>
</tr>
<tr>
<td>
fix(api): prevent duplicate users from being created
<br/>
<br/>
This commit resolves a race condition where the API allowed duplicate profiles to be generated using identical email addresses. A unique index constraint has also been introduced to the database schema.
<br/>
<br/>
Closes #123
</td>
<td>Fixed</td><td>Prevent duplicate users from being created</td>
</tr>
<tr>
<td>test(auth): add unit tests for login validation</td>
<td>Testing</td>
<td>Add unit tests for login validation</td>
</tr>
<tr>
<td>docs: update installation guide in readme</td>
<td>Documentation</td>
<td>Update installation guide in readme</td>
</tr>
<tr>
<td>perf(database): optimize slow user query indexing</td>
<td>Changed</td>
<td>Optimize slow user query indexing</td>
</tr>
<tr>
<td>chore(deps): upgrade typescript to version 5.0</td>
<td></td>
<td></td>
</tr>
<tr>
<td>build(webpack): split code into vendor chunks</td>
<td></td>
<td></td>
</tr>
<tr>
<td>ci: fix broken deployment trigger on main branch</td>
<td></td>
<td></td>
</tr>
<tr>
<td>security: sanitize user input in search bar</td>
<td>Security</td>
<td>Sanitize user input in search bar</td>
</tr>
<tr>
<td>security(cve): update axios to patch cve-2023-45857</td>
<td>Security</td>
<td>Update axios to patch cve-2023-45857</td>
</tr>
<tr>
<td>deprecated(api): deprecate the v1/users endpoint</td>
<td>Deprecated</td>
<td>Deprecate the v1/users endpoint</td>
</tr>
<tr>
<td>removed(api)!: remove deprecated v1/users endpoints</td>
<td>Removed</td>
<td>BREAKING CHANGE: Remove deprecated v1/users endpoints</td>
</tr>
<tr>
<td></td>
<td></td>
<td></td>
</tr>
<tr>
<td></td>
<td></td>
<td></td>
</tr>
<tr>
<td></td>
<td></td>
<td></td>
</tr>
</table>

### Example CHANGELOG.md

```md
# CHANGELOG

All notable changes to this project will be documented in this file.

The format is loosely based on both the [Keep a Changelog](https://keepachangelog.com/) and [Conventional Commits](https://www.conventionalcommits.org/) specifications, and adheres to [Semantic Versioning](https://semver.org/).

---

## [Unreleased]

### <!-- 0 -->Added

- Add user login page

### <!-- 1 -->Fixed

- Prevent duplicate users from being created

### <!-- 2 -->Changed

- BREAKING CHANGE: Drop support for legacy Stripe v2 API

### <!-- 3 -->Removed

- BREAKING CHANGE: Remove deprecated v1/users endpoints

### <!-- 4 -->Security

- Update axios to patch cve-2023-45857

### <!-- 4 -->Deprecated

- Deprecate the v1/users endpoint

### <!-- 6 -->Documentation

- Add documentation explaining how to cure cancer

### <!-- 7 -->Testing

- Add unit tests for login validation

## [0.0.1] - 2026-09-05

### <!-- 9 -->Other

- Initial commit

[unreleased]: https://github.com/forbesrodney/foam-for-android/compare/v0.0.1..HEAD
[0.0.1]: https://github.com/forbesrodney/foam-for-android/tree/v0.0.1
<!-- generated by git-cliff -->
```
