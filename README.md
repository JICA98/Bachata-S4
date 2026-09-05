# Bachata S4 compatibility website

This branch contains the static frontend and site builder for `bachatas4.games`.

## Navigation model

There is one navigation model for compatibility data:

- Home and Compatibility use the **same image-based game-card component**.
- Clicking any game card navigates directly to `/games/CUSAxxxxx/`.
- Each game URL is a generated static page with its complete report history and screenshots.
- There is no popup/dialog or query-string game routing.
- Issue/discussion/archive metadata from the data repository is not published into the generated site data.

## Data model

Compatibility source data remains in `JICA98/Bachata-S4-Compatibility`. The website builder reads each immutable report directly and does not require a separate release index. This keeps historical reports available even when old releases are not present in the current GitHub Releases inventory.

Only screenshots are copied into the Pages artifact. Log buttons link to the public raw files in the compatibility-data repository.

## Local build

```bash
python3 scripts/build_site.py \
  --source ../Bachata-S4-Compatibility \
  --site compatibility-site \
  --output /tmp/bachata-site \
  --base-url https://bachatas4.games

python3 scripts/site_audit.py --site /tmp/bachata-site
python3 -m http.server 8080 --directory /tmp/bachata-site
```

The included fixture contains both an older `v0.1.6` report and a newer `v0.1.9` report and is used by CI to guard against the old release-index failure.
