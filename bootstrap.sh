#!/usr/bin/env sh
#
# Rename this template to your own plugin's coordinates.
#
# Renaming by hand means editing gradle.properties, build.gradle, both source trees, three test
# fixtures and two documentation files without missing one. This does all of it, then builds to
# prove the result still works.
#
# Usage:
#   ./bootstrap.sh --group com.acme --id awesome --name awesome-gradle
#
# Windows: run this from Git Bash or WSL.

set -eu

GROUP=""
ID=""
NAME=""
AUTHOR=""
DISPLAY=""
DESCRIPTION=""
STRIP_TUTORIAL="no"
ASSUME_YES="no"

OLD_GROUP="io.github.intisy"
OLD_ID="myplugin"
OLD_NAME="gradle-plugin-example"
OLD_AUTHOR="intisy"

usage() {
    cat <<'USAGE'
Usage: ./bootstrap.sh --group <package> --id <plugin-id> --name <artifact-name> [options]

Required:
  --group <package>        Java package and Gradle group, e.g. com.acme.tools
  --id <plugin-id>         Short plugin id and extension block name, e.g. awesome
                           The full plugin id becomes <group>.<id>
  --name <artifact-name>   Artifact and repository name, e.g. awesome-gradle

Options:
  --author <user>          GitHub user or org for URLs (default: the --group's last segment)
  --display <text>         Human readable plugin name (default: derived from --name)
  --description <text>     One line description
  --strip-tutorial         Delete tutorial/, CONTENT.md prose and TROUBLESHOOTING.md
  --yes                    Do not prompt for confirmation
  --help                   Show this message

Example:
  ./bootstrap.sh --group com.acme.tools --id awesome --name awesome-gradle \
      --display "Awesome Plugin" --description "Does something awesome"

Class names such as MyPlugin and MyTask are deliberately left alone; renaming those is a safe
IDE refactor, whereas coordinates are spread across files that no refactor tool touches.
USAGE
}

while [ $# -gt 0 ]; do
    case "$1" in
        --group) GROUP="${2:-}"; shift 2 ;;
        --id) ID="${2:-}"; shift 2 ;;
        --name) NAME="${2:-}"; shift 2 ;;
        --author) AUTHOR="${2:-}"; shift 2 ;;
        --display) DISPLAY="${2:-}"; shift 2 ;;
        --description) DESCRIPTION="${2:-}"; shift 2 ;;
        --strip-tutorial) STRIP_TUTORIAL="yes"; shift ;;
        --yes|-y) ASSUME_YES="yes"; shift ;;
        --help|-h) usage; exit 0 ;;
        *) echo "Unknown argument: $1" >&2; echo >&2; usage >&2; exit 2 ;;
    esac
done

fail() {
    echo "bootstrap: $1" >&2
    exit 1
}

[ -n "$GROUP" ] || { usage >&2; fail "--group is required"; }
[ -n "$ID" ] || { usage >&2; fail "--id is required"; }
[ -n "$NAME" ] || { usage >&2; fail "--name is required"; }

echo "$GROUP" | grep -Eq '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$' \
    || fail "--group must be a lowercase dotted Java package with at least two segments, got '$GROUP'"
echo "$ID" | grep -Eq '^[a-z][a-z0-9]*$' \
    || fail "--id must be lowercase letters and digits starting with a letter, got '$ID'"
echo "$NAME" | grep -Eq '^[a-z0-9][a-z0-9._-]*$' \
    || fail "--name must be lowercase, got '$NAME'"

[ -f gradle.properties ] && [ -d src/main/java ] \
    || fail "run this from the root of the template checkout"

grep -q "$OLD_GROUP" gradle.properties \
    || fail "gradle.properties no longer mentions $OLD_GROUP; has bootstrap already run?"

[ -n "$AUTHOR" ] || AUTHOR="${GROUP##*.}"
[ -n "$DISPLAY" ] || DISPLAY="$(echo "$NAME" | tr '-' ' ')"
[ -n "$DESCRIPTION" ] || DESCRIPTION="A Gradle plugin"

OLD_PATH="$(echo "$OLD_GROUP" | tr '.' '/')"
NEW_PATH="$(echo "$GROUP" | tr '.' '/')"

echo "  group        $OLD_GROUP  ->  $GROUP"
echo "  plugin id    $OLD_GROUP.$OLD_ID  ->  $GROUP.$ID"
echo "  artifact     $OLD_NAME  ->  $NAME"
echo "  author       $OLD_AUTHOR  ->  $AUTHOR"
echo "  display      $DISPLAY"
echo "  package dir  src/*/java/$OLD_PATH  ->  src/*/java/$NEW_PATH"
[ "$STRIP_TUTORIAL" = "yes" ] && echo "  tutorial     deleted"
echo

if [ "$ASSUME_YES" != "yes" ]; then
    printf "Proceed? [y/N] "
    read -r answer
    case "$answer" in
        [yY]|[yY][eE][sS]) ;;
        *) echo "Aborted."; exit 1 ;;
    esac
fi

rewrite() {
    target=$1
    [ -f "$target" ] || return 0
    tmp="$target.bootstrap.tmp"
    # In source, every mention of the old id is the extension name or documents it, so the bare
    # word is replaced too. In prose that would rewrite the tutorial's own examples, so it is not.
    case "$target" in
        *.java) extra="s|$OLD_ID|$ID|g" ;;
        *) extra="" ;;
    esac
    sed \
        ${extra:+-e "$extra"} \
        -e "s|$OLD_GROUP\.$OLD_ID|$GROUP.$ID|g" \
        -e "s|$OLD_GROUP|$GROUP|g" \
        -e "s|$OLD_PATH|$NEW_PATH|g" \
        -e "s|$OLD_NAME|$NAME|g" \
        -e "s|github.com/$OLD_AUTHOR/|github.com/$AUTHOR/|g" \
        -e "s|\"$OLD_ID\"|\"$ID\"|g" \
        -e "s|^\\([[:space:]]*\\)$OLD_ID {|\\1$ID {|" \
        "$target" > "$tmp" && mv "$tmp" "$target"
}

# Staged through a scratch directory because the old and new package paths can share their first
# segment (io.github.intisy -> io.github.acme), where removing the old root would take the new
# tree with it.
move_tree() {
    root=$1
    [ -d "$root/$OLD_PATH" ] || return 0
    staging="$root/.bootstrap-staging"
    rm -rf "$staging"
    mkdir -p "$staging"
    (cd "$root/$OLD_PATH" && tar cf - .) | (cd "$staging" && tar xf -)
    rm -rf "${root:?}/${OLD_PATH%%/*}"
    mkdir -p "$root/$NEW_PATH"
    (cd "$staging" && tar cf - .) | (cd "$root/$NEW_PATH" && tar xf -)
    rm -rf "$staging"
}

echo "Moving source trees..."
move_tree src/main/java
move_tree src/test/java

echo "Rewriting references..."
find . \
    -type d \( -name .git -o -name build -o -name .gradle -o -name .idea \) -prune -o \
    -type f \( -name '*.java' -o -name '*.gradle' -o -name '*.md' -o -name '*.yml' \
               -o -name '*.properties' \) -print \
    | while IFS= read -r file; do
        case "$file" in
            ./gradle/wrapper/*) continue ;;
        esac
        rewrite "$file"
    done

echo "Writing gradle.properties..."
cat > gradle.properties <<EOF
display_name=$DISPLAY
artifact_name=$NAME
artifact_id=$ID
artifact_group=$GROUP
artifact_description=$DESCRIPTION
artifact_version=1.0.0
author=$AUTHOR

org.gradle.configuration-cache=true
org.gradle.caching=true
EOF

if [ "$STRIP_TUTORIAL" = "yes" ]; then
    echo "Removing tutorial content..."
    rm -rf tutorial
    rm -f TROUBLESHOOTING.md
    cat > CONTENT.md <<EOF
## Usage

\`\`\`groovy
plugins {
    id '$GROUP.$ID' version '1.0.0'
}

$ID {
    fileContent = 'configure me'
}
\`\`\`
EOF
fi

cat > .github/docs-config.yml <<EOF
# Consumed by intisy/workflows .github/scripts/generate-readme.py via readme.yml.
# The parser is a flat key/value and key/list subset, so every value stays on one line.
kind: gradle-plugin
title: $DISPLAY
license: Apache License 2.0
description: $DESCRIPTION
sections:
  - title
  - about
  - content
  - license-badge
EOF

echo
echo "Verifying with ./gradlew build ..."
if ./gradlew build --console=plain -q; then
    echo
    echo "Done. Your plugin id is $GROUP.$ID"
    echo
    echo "Remaining by hand, if you want them:"
    echo "  - rename the MyPlugin / MyTask / MyPluginExtension classes in your IDE"
    echo "  - update LICENSE with your own copyright holder"
    echo "  - rm bootstrap.sh"
    if [ "$STRIP_TUTORIAL" != "yes" ]; then
        echo "  - tutorial/ still teaches the original coordinates, so its snippets no longer"
        echo "    match your source; re-run with --strip-tutorial, or just delete it"
    fi
else
    echo
    echo "The build failed after rewriting. Inspect the diff with 'git diff' and fix," >&2
    echo "or start over with 'git checkout .' if this is a clean checkout." >&2
    exit 1
fi
