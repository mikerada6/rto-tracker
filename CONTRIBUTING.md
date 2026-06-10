# Contributing

This repository uses **GitFlow** for branching. The model separates production
history (`main`) from in-progress integration (`develop`) and supports parallel
hotfixing of shipped versions.

## Branches

| Branch | Purpose | Lifetime |
| --- | --- | --- |
| `main` | Production. Every commit corresponds to a tagged release (`vX.Y.Z`). | Permanent. Protected — PR only. |
| `develop` | Integration branch for the next release. Always at `X.Y.Z-SNAPSHOT`. | Permanent. Protected — PR only. |
| `feature/*` | New work targeting the next release. | Branched from `develop`, merged back into `develop`. |
| `release/*` | Stabilising a release. Version bump + last-minute fixes only. | Branched from `develop`, merged into `main` **and** `develop`. |
| `hotfix/*` | Urgent fix against a shipped version. | Branched from `main`, merged into `main` **and** `develop`. |

## Versioning

We follow [Semantic Versioning](https://semver.org/).

- `main` is always at the released version (e.g. `1.0.0`).
- `develop` is always at the next minor SNAPSHOT (e.g. `1.1.0-SNAPSHOT`).
- After a release ships, `develop` is bumped to the next SNAPSHOT.
- Hotfixes bump the patch (e.g. `1.0.0` → `1.0.1`).

The version lives in two places — keep them in sync:
- `backend/pom.xml` — `<version>` element
- `frontend/package.json` — `"version"` field (also update `package-lock.json`)

## Feature work

```bash
git checkout develop
git pull
git checkout -b feature/short-description

# ... work, commit ...

git push -u origin feature/short-description
# Open a PR targeting develop
```

Feature branches must pass `test-backend` and `test-frontend` before merge.

## Cutting a release

When `develop` has accumulated enough for a release:

```bash
git checkout develop
git pull
git checkout -b release/1.1.0

# 1. Bump versions
#    backend/pom.xml          1.1.0-SNAPSHOT -> 1.1.0
#    frontend/package.json    1.1.0-SNAPSHOT -> 1.1.0
#    frontend/package-lock.json (same)

# 2. Update CHANGELOG.md — move [Unreleased] entries under [1.1.0] - YYYY-MM-DD

# 3. Commit & push
git commit -am "Release: cut v1.1.0"
git push -u origin release/1.1.0
```

Open **two** PRs from this branch:

1. `release/1.1.0` → `main` — merge first, then tag:
   ```bash
   git checkout main && git pull
   git tag -a v1.1.0 -m "v1.1.0"
   git push origin v1.1.0
   ```
   The tag triggers CI to publish Docker Hub images (`1.1.0`, `1.1`, `1`, `latest`).
   Then create the GitHub release pointing at the tag.

2. `release/1.1.0` → `develop` — merge back so develop has the bump + changelog updates.

3. Then bump `develop` to the next SNAPSHOT (`1.2.0-SNAPSHOT`) as a small PR.

4. Delete the release branch.

## Hotfix

When a bug in production needs an out-of-band fix:

```bash
git checkout main
git pull
git checkout -b hotfix/1.0.1

# 1. Bump versions to 1.0.1 (NOT SNAPSHOT — hotfixes ship straight away)
# 2. Fix the bug
# 3. Add a [1.0.1] entry to CHANGELOG.md
# 4. Commit & push
```

Open two PRs (same pattern as release):

1. `hotfix/1.0.1` → `main`, merge, tag `v1.0.1`, push tag, create GitHub release.
2. `hotfix/1.0.1` → `develop`, merge so develop carries the fix.

Delete the hotfix branch.

## CI / Docker tags

| Trigger | Image tags produced |
| --- | --- |
| Push to `main` | `main`, `sha-<short>` |
| Push to `develop` | `develop`, `sha-<short>` |
| Push tag `vX.Y.Z` | `X.Y.Z`, `X.Y`, `X`, `latest` |
| PR | tests only — no image push |

For production deployments, pin to a specific version (`IMAGE_TAG=1.0.0` in
`.env`) or use `:latest` to track stable releases.

For staging / snapshot environments, use `:develop`.

## Commit messages

Short imperative subject (`Fix dark mode contrast`, not `fixed` or `Fixes`).
Bodies are optional but welcome for non-trivial changes — explain *why*, not
*what*.
