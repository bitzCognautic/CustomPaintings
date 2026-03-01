# CustomPaintings

CustomPaintings lets server admins upload PNG images to the server and give players map-based paintings, including multi-map murals (2x2, 4x4, etc.).

## Features

- Upload PNG files directly to the server filesystem
- Import as:
- Single painting (`1x1` map)
- Multi-map mural (`2x2` up to `8x8`)
- Give painting sets to players with one command
- Persistent painting index in `paintings.yml`
- Tab-complete for commands and painting names

## Requirements

- Paper
- Java `21`

## Build

From plugin source folder:

```bash
./gradlew build
```

Output jar:

```text
build/libs/CustomPaintings-1.0.0.jar
```

## Install

1. Stop your Paper server.
2. Copy `CustomPaintings-1.0.0.jar` into your server `plugins/` folder.
3. Start the server.
4. Confirm plugin is enabled:
- Run `/plugins`
- `CustomPaintings` should be green

## Created Folders/Files

After successful startup, plugin creates:

- `plugins/CustomPaintings/config.yml`
- `plugins/CustomPaintings/uploads/` (put upload PNGs here)
- `plugins/CustomPaintings/paintings/` (generated map tile PNGs)
- `plugins/CustomPaintings/paintings.yml` (painting metadata)

## Command Reference

Base command:

```text
/painting
```

### 1) List paintings

```text
/painting list
```

Shows all registered painting names and dimensions (example `wallart (4x4)`).

### 2) Import painting

```text
/painting import <name> <upload-file.png> [widthMaps] [heightMaps]
```

- `name`: painting id (`a-z`, `0-9`, `_`, `-`, max 32 chars)
- `upload-file.png`: filename placed in `uploads/`
- `widthMaps`: optional, default `1`, range `1..8`
- `heightMaps`: optional, default `1`, range `1..8`

Examples:

```text
/painting import mona mona.png
/painting import castle castle.png 2 2
/painting import mega_wall panorama.png 4 3
```

### 3) Give painting set(s)

```text
/painting give <name> [player] [sets]
```

- `name`: registered painting
- `player`: optional target player
- `sets`: optional number of full sets (default `1`, clamped `1..64`)

A set contains all map items required for the mural:
- `1x1` mural = 1 map per set
- `2x2` mural = 4 maps per set
- `4x4` mural = 16 maps per set

Examples:

```text
/painting give mona
/painting give mona bitz
/painting give castle bitz 3
```

### 4) Remove painting

```text
/painting remove <name>
```

Deletes metadata and generated tile images for that painting.

### 5) Reload painting index

```text
/painting reload
```

Reloads `paintings.yml` without restarting server.

## Permissions

- `custompaintings.use` (default: `true`)
- `custompaintings.admin` (default: `op`)

Admin required for:
- `/painting import ...`
- `/painting remove ...`
- `/painting reload`

## Full Workflow Example (with username `bitz`)

1. Upload `sunset.png` to:
- `plugins/CustomPaintings/uploads/sunset.png`

2. Import as 2x2 mural:

```text
/painting import sunset sunset.png 2 2
```

3. Confirm:

```text
/painting list
```

4. Give one set to yourself:

```text
/painting give sunset bitz 1
```

5. Place maps in item frames in grid order:
- Top row left to right
- Then next row left to right

## Notes on Placement Order

Map display names include tile position like:

```text
Painting: sunset [1/2, 1/2]
```

Meaning:
- first value pair is horizontal position (`x/width`)
- second pair is vertical position (`y/height`)

Use that to place frames correctly for murals.

## Troubleshooting

### Plugin is red in `/plugins`

Check console logs on startup for `CustomPaintings` errors.

Common issue fixed in this project:
- Missing `config.yml` in jar causes:
- `The embedded resource 'config.yml' cannot be found`

### Upload folder missing

Folder is:

```text
plugins/CustomPaintings/uploads
```

If it does not exist, plugin likely failed to enable.

### "Painting not found"

- Ensure painting was imported first
- Check exact painting name in `/painting list`

### "Upload file not found"

- Verify file exists in `plugins/CustomPaintings/uploads/`
- Use exact filename including `.png`

### "Only .png files are supported"

Convert source to PNG and retry import.

### Mural too large

Max import size is `8x8` maps.

## Data Format

`paintings.yml` stores each painting as:

- `width`
- `height`
- `prefix` (tile filename prefix)

The plugin also supports loading older legacy single-file entries and treating them as `1x1`.

## Upgrade/Replace Jar Safely

1. Stop server.
2. Replace plugin jar in `plugins/`.
3. Start server.
4. Run `/painting reload` if only metadata changed while running.

## Uninstall

1. Stop server.
2. Remove plugin jar from `plugins/`.
3. (Optional) Delete `plugins/CustomPaintings/` data folder.

## License

MIT
