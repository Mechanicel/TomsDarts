# 0033 — Modus-Auswahl im Setup als Raster mit fester Spaltenzahl (2/3 Spalten, Breakpoint 480 dp)

**Status:** Akzeptiert

## Kontext

Mit sechs Katalog-Modi (X01, Cricket, Around the Clock, Shanghai, Count Up, Killer) passte die
Modus-Auswahl im Setup nicht mehr in eine einzelne `Row`: Die Karten wurden zu schmalen Streifen
gequetscht, und die rohe Kennung (`AROUND_THE_CLOCK`) brach unlesbar um (siehe
[ADR-0029](0029-shanghai-katalog-modus.md), [ADR-0030](0030-count-up-katalog-modus.md#konsequenzen),
[ADR-0032](0032-killer-sechster-katalog-modus.md#konsequenzen)).

## Entscheidung

1. **Lokalisierte Anzeigenamen:** Je Modus ein `mode_label_*`-String; die Zuordnung Kennung → Ressource
   liegt in `gameModeLabelResIdOrNull` (Fallback bei unbekannter Kennung: rohe Kennung, nie leerer Text).
   Karte und TalkBack-Ansage nennen den lokalisierten Namen.
2. **Raster statt Row:** Die Modi werden in Reihen zu je `columns` Karten gestückelt
   (`modes.chunked(columns)`, je Reihe eine `Row`). `columns = modeGridColumns(maxWidth)`:
   **2 Spalten**, ab **480 dp** nutzbarer Breite **3 Spalten** (via `BoxWithConstraints`).
   - Alle Karten sind gleich breit (`weight(1f)`); eine unvollständige letzte Reihe wird mit leeren
     `Spacer`-Platzhaltern aufgefüllt, damit die Karten dort nicht auf doppelte Breite aufgebläht werden.
   - Karten einer Reihe sind gleich hoch (`height(IntrinsicSize.Min)` + `fillMaxHeight()`), z.B.
     zweizeiliges „Around the Clock" neben einzeiligem „Shanghai".
3. **Karteninhalt:** `titleMedium` statt `headlineSmall`, bis zu drei Zeilen (Ellipsis), Mindesthöhe
   64 dp, seitliches Innenpolster — trägt auch große Systemschrift.
4. **Markierte Karte = aufgelöster Modus:** `resolveSelectedMode(key)` (Fallback erster Katalog-Eintrag)
   steuert sowohl die Sichtbarkeit der Sections als auch die Auswahl-Optik; beides bleibt konsistent.

## Begründung / Alternativen

- **`FlowRow` mit `maxItemsInEachRow` (verworfen):** `weight(1f)` bläht in `FlowRow` die Karten der
  letzten, unvollständigen Reihe auf; gleiche Reihenhöhe bräuchte `fillMaxRowHeight()` aus der
  experimentellen `ExperimentalLayoutApi`. Das gestückelte Raster löst beides mit stabiler API.
- **Horizontales Scrollen / Pagination (verworfen):** versteckt Modi außerhalb des Sichtbereichs;
  bei sechs Modi unnötig.
- **480 dp:** typische Telefon-Hochformat-Breiten (320–412 dp) bleiben zweispaltig mit genug Platz
  für „Around the Clock"; Querformat/Tablets (Body auf 600 dp begrenzt) bekommen drei Spalten.

## Konsequenzen

- Neue Modi brauchen zusätzlich einen `mode_label_*`-String + `when`-Zweig; ein Robolectric-Wächter
  (`GameModeLabelResourcesTest`) schlägt sonst fehl.
- Das Raster wächst mit dem Katalog, ohne dass die Karten schmaler werden.
- Compose-Darstellung selbst ist mangels Host-UI-Tests nur über Previews (320/360 dp, fontScale 2,
  Querformat) abgesichert; Spaltenzahl und Fallback sind als reine Funktionen JVM-getestet.

## Verweise

- [ADR-0018](0018-setup-screen-startpunkt.md) — Setup-Screen-Karten (Vorbild der Karten-Optik)
- [ADR-0022](0022-modus-infrastruktur.md) — Modus-Katalog
- [ADR-0032](0032-killer-sechster-katalog-modus.md) — sechster Modus, Auslöser der Dringlichkeit
