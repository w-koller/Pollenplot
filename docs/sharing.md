# Sharing a garden with a household

This note recommends how two or more people in a household could keep one garden in Pollenplot: each with the app on
their own phone, each adding plants, sightings, waterings and hive records, and each seeing what the others added. It
is a proposal for review. Nothing here is built yet, and cloud sync was left out of version 1 on purpose.

## Where things stand

Some of what sharing needs is already in place.

- Every user record has a UUID string key, made on the phone with `UUID.randomUUID()`, so records made on two phones
  never clash. The key is also the record's IRI (`urn:uuid:…`) in the JSON-LD export.
- The main records (zones, plants, sightings, watering lines, apiaries, honey bee hives, stingless bee hives and bee
  hotels) have `createdAt` and `updatedAt`.
- Most other records are logs that are only ever added to: inspections, mite checks, treatments, feedings, harvests,
  colony events, waterings, hotel observations and upkeep. Logs from two phones merge by taking both.
- Photos and cut-outs are stored under UUID names (`files/photos/`, `files/thumbs/`) and never change once written, so
  sharing them means copying the files the other phone does not have yet.
- `BackupSeal` already encrypts a whole garden with a password: PBKDF2-HMAC-SHA256, then AES-256-GCM in 64 KiB
  segments. A sealed file can go through any channel without anyone along the way being able to read it.
- `BackupService` already checks a backup, brings an older one up to date through `PollenplotDatabase.MIGRATIONS`,
  and replaces every table in one transaction (`replaceRecords`, `copyTable`).

Other things are missing.

- Deletes leave no trace. Every delete is a hard `DELETE`, and some cascade inside the database, so another phone
  cannot learn that a record was removed.
- Some editable records have no `updatedAt`, such as flowering records, candidates and hotel sections, so there is no
  way to tell which of two edits is newer.
- Records do not say which phone or person made or last changed them.
- `WateringLineEntity.zoneIds` holds its zones as one comma-separated string, so two people changing a line's zones
  would overwrite each other.
- A restore always replaces the whole garden. Nothing merges.
- The garden has no ID of its own, so the app cannot tell two separate gardens apart.

## Recommended path

### Step 1: groundwork in the database (migration 1 to 2)

The database is at schema version 1 and 1.0.0-rc.2 is on phones, so this step needs a migration in
`PollenplotDatabase.MIGRATIONS` and a schema committed in `app/schemas/`.

- Add a `garden` row with the garden's own UUID, and a `deviceId` made once on each phone.
- Add `updatedAt` and `updatedBy` (the device) to every table people can edit.
- Add a `tombstones(id, tableName, deletedAt, deviceId)` table. Every delete in `GardenRepository`, `BeeRepository`,
  `NativeBeeRepository`, `HotelRepository` and `WaterRepository` writes one, including for the rows a cascade removes.
- Move a watering line's zones into a join table, so each link is its own row.
- Export a change set: every row and tombstone changed since a given time, sealed with `BackupSeal`.
- Merge a change set:
  - a record takes whichever version has the newest `updatedAt`;
  - logs take every row from both phones;
  - a tombstone wins over any edit older than it;
  - photos and cut-outs that are missing are copied in.

  The checking and staging in `BackupService.read`, and the transaction in `replaceRecords`, can be reused, with
  `copyTable` changed from "delete everything, then insert" to "insert or update by key".

This step is worth doing even before sharing, because it would also let a backup be merged into a garden rather than
replace it.

### Step 2: household sharing without a server (recommended first release)

The garden is shared as sealed change files through something the household already uses.

- **Joining.** The owner shows a QR code on their phone that holds the garden's ID and a random shared key. A member
  scans it. No account and no password are needed, and the key never leaves the two phones.
- **Carrying the changes.**
  - The recommended default is a folder the household already syncs, such as Google Drive, Nextcloud, Dropbox or
    Syncthing, chosen once through Android's Storage Access Framework. Each phone writes its own change file to the
    folder and reads the others' files.
  - Phones in the same house could also swap changes directly, over Nearby Connections or Wi-Fi Direct.
  - A change file can also go through the share sheet, for a household that uses neither.
- **When.** Changes are written and read when the app opens, after edits, and in the existing WorkManager schedule,
  on Wi-Fi.

This fits Pollenplot's privacy stance with one small change. Photos already leave the phone only in a file the user
saves, and a sealed change file is such a file. The folder's provider sees only encrypted data, Pollenplot runs no
server and keeps no accounts, and garden locations stay as they are: kept on the phones, and rounded to about 1 km
whenever they are sent to a service.

### Step 3: live sync, later and only if needed

Live sync means changes reach the other phones within seconds rather than when the folder syncs. It would need:

- the records kept as a CRDT, either through a library such as Automerge or hand-written as last-writer-wins per
  field over the change log from step 1;
- a relay to carry the encrypted changes, either self-hosted or an existing end-to-end encrypted service.

It costs much more: accounts, a server to run and pay for, and screens for conflicts that last-writer-wins handles
badly, such as two people editing one watering line or one hive inspection at the same time. This step belongs after
1.0, and only if step 2 proves too slow in practice.

## Roles

Start with two roles.

- **Owner:** made the garden and can invite people, remove them and change the key.
- **Member:** can add and edit everything.

Removing a member means making a new key and sharing it again with everyone who stays. Read-only sharing with people
outside the household already works through the report's PDF and spreadsheet.

## What stays on each phone

These are about one phone, not the garden, so they are never shared:

- `scanned_media`, the gallery photos already looked at;
- the search index and the meaning embeddings, which each phone rebuilds;
- the downloaded models (AIY, the BioCAP pack, Gemma 4 and the text model);
- notifications already shown;
- photo scanning settings, and the forecast weather each phone keeps.

## Open questions for the owner

1. Is a shared folder (step 2) acceptable as the first release, or must changes arrive within seconds?
2. Should each person's records show who added them, or is the garden one shared voice?
3. Should a member who leaves keep a copy of the garden on their phone?
4. Can one phone hold more than one garden, such as a home and a community garden? That changes step 1, because
   every record would then need the garden's ID.
5. Is it fine for a member's notifications, such as hive checks and watering, to repeat on every phone, or should one
   person be chosen for each?
