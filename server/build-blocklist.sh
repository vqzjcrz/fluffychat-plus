#!/usr/bin/env bash
# Builds a merged blocklist for Sift from public threat feeds.
# Runs on YOUR server (e.g. a Pi). The phone only ever talks to your server.
#
# Usage: ./build-blocklist.sh [output-dir]      (default: ./www)
set -euo pipefail

OUT_DIR="${1:-$(dirname "$0")/www}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$OUT_DIR"

# name|url|kind   (kind: domains = one host per line / hosts format, urls = full URLs)
FEEDS=(
  "urlhaus-hosts|https://urlhaus.abuse.ch/downloads/hostfile/|domains"
  "urlhaus-online|https://urlhaus.abuse.ch/downloads/text_online/|urls"
  "threatfox-hosts|https://threatfox.abuse.ch/downloads/hostfile/|domains"
  "phishing-army|https://phishing.army/download/phishing_army_blocklist_extended.txt|domains"
  "openphish|https://openphish.com/feed.txt|urls"
  "blp-phishing|https://blocklistproject.github.io/Lists/alt-version/phishing-nl.txt|domains"
  "blp-scam|https://blocklistproject.github.io/Lists/alt-version/scam-nl.txt|domains"
  "blp-malware|https://blocklistproject.github.io/Lists/alt-version/malware-nl.txt|domains"
  # Big (~several hundred K). Uncomment if you want maximum coverage:
  # "phishing-db|https://raw.githubusercontent.com/mitchellkrogza/Phishing.Database/master/phishing-domains-ACTIVE.txt|domains"
)

# Your own additions (one domain or URL per line) are always merged in.
LOCAL_EXTRA="$(dirname "$0")/local-additions.txt"

: > "$WORK/domains"; : > "$WORK/urls"
for feed in "${FEEDS[@]}"; do
  IFS='|' read -r name url kind <<<"$feed"
  if curl -fsSL --max-time 120 -A "sift-blocklist-builder" "$url" -o "$WORK/$name"; then
    n=$(wc -l < "$WORK/$name")
    echo "ok   $name ($n lines)"
    if [ "$kind" = urls ]; then
      grep -Ei '^https?://' "$WORK/$name" >> "$WORK/urls" || true
    else
      # strip comments, hosts-file IPs, adblock syntax, whitespace
      sed -E 's/#.*$//; s/^(0\.0\.0\.0|127\.0\.0\.1)[[:space:]]+//; s/^\|\|//; s/\^.*$//; s/[[:space:]]+//g' "$WORK/$name" \
        | tr 'A-Z' 'a-z' | grep -E '^[a-z0-9_.-]+\.[a-z0-9-]+$' | grep -vE '^(localhost|0\.0\.0\.0)$' >> "$WORK/domains" || true
    fi
  else
    echo "FAIL $name ($url) — skipped"
  fi
done

if [ -f "$LOCAL_EXTRA" ]; then
  grep -Ei '^https?://' "$LOCAL_EXTRA" >> "$WORK/urls" || true
  grep -vEi '^(https?://|#|$)' "$LOCAL_EXTRA" | tr 'A-Z' 'a-z' >> "$WORK/domains" || true
fi

{
  echo "# Sift blocklist — generated $(date -u +%Y-%m-%dT%H:%MZ)"
  sort -u "$WORK/domains"
  sort -u "$WORK/urls"
} > "$WORK/blocklist.txt"

total=$(grep -vc '^#' "$WORK/blocklist.txt" || true)
if [ "$total" -lt 100 ]; then
  echo "Only $total entries — keeping the previous list." >&2
  exit 1
fi
mv "$WORK/blocklist.txt" "$OUT_DIR/blocklist.txt"
gzip -kf "$OUT_DIR/blocklist.txt"
echo "Wrote $OUT_DIR/blocklist.txt ($total entries)"
