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

## Recipe images

Generated recipes show a photo above the details dialog:

- First, the app tries to pick a local image based on dish tags and key ingredients (e.g., tacos with beef vs beans; pasta with tomato vs coconut; fried rice with egg vs tuna; stir‑fry with beef/chicken/veg; soup)
- Images are loaded from `src/main/resources/images/` using `res:/images/...` paths.
- If an image is missing, the UI renders a readable local placeholder card (no internet required) with the title and a brief ingredient list.

Add these files to `src/main/resources/images/` to customize:

```
tacos_beef.jpg
tacos_beans.jpg
pasta_tomato_beef.jpg
pasta_tomato_chicken.jpg
pasta_tomato_veg.jpg
pasta_coconut_veg.jpg
fried_rice_egg.jpg
fried_rice_tuna.jpg
fried_rice_veg.jpg
stir_fry_beef.jpg
stir_fry_chicken.jpg
stir_fry_veg.jpg
soup_veg.jpg
placeholder.jpg
```

Tip: duplicate one image and rename it to the above filenames to verify wiring. The dialog logs can help:

```java
System.out.println("IMG -> " + resolveRecipeImage(g));
```

If it prints `res:/images/...` but shows a placeholder, check the exact filename and folder path.
