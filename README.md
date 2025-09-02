# AI Pantry

[![CI](https://github.com/Jimbo8998/AI-Pantry/actions/workflows/ci.yml/badge.svg)](https://github.com/Jimbo8998/AI-Pantry/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Smart meal planning and shopping helper built with Java 21, JavaFX, and Maven.

It scores recipes against your pantry, plans meals, consolidates a shopping list, and lets you jump to store searches. Includes sample data and CSV export.

## What it is

- Pantry manager with quantities, units, and expirations
- Recipe browser with tags and cook-time
- Planner that scores by coverage, perishables, time, and tags (with transparent explanation)
- Consolidated shopping list with unit conversions and store links
- CSV export and clipboard copy

## Run

Using the Maven wrapper (bundled):

```bat
mvnw.cmd -q -DskipTests javafx:run
```

Or from VS Code: Run Task → "Maven: javafx:run".

## Load sample data

- App menu: File → Load Sample Data
- Recipes tab: Import sample recipes

You can also open your own JSON files:

- File → Open Pantry JSON…
- File → Open Recipes JSON…

Sample JSONs live in `src/main/resources/sample-data/`:

- `pantry.json`
- `recipes.json`
- `aliases.json` (ingredient synonyms)
- `units.json` (unit conversion map)

## Where exports go

- CSV export uses a Save dialog; choose any location. The exported format is:
	- Header: `name,amount,unit`
	- One line per item

## Quick demo

1. File → Load Sample Data
2. Plan tab: set Meals=3, Max=30, Tags=quick → Plan
3. Click a recipe to see "Why chosen"
4. Shopping List tab: set Servings, Open All in Walmart, or Export CSV
5. Pantry tab: Add/Edit items; re-run Plan to see scores update

Screenshots (placeholders):

- docs/images/pantry.png
- docs/images/plan.png
- docs/images/shopping.png

## Troubleshooting

- If JSON fails to load, a friendly error dialog appears. Validate JSON structure against the samples.
- If exports fail due to permissions, choose a different folder.

## Tests

Run unit tests:

```bat
mvnw.cmd -q -DskipTests=false test
```

Covered areas: units conversion, alias resolution, rule explanation math, and shopping list consolidation/rounding.

## JavaDocs

Generate API docs (optional):

```bat
mvnw.cmd -q javadoc:javadoc
```

Output will be in `./doc/` (open `doc/index.html`).
