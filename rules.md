Design a high-fidelity Android mobile application prototype called **“Tag Poling Inventory”**.

This application is an **offline-first Fiber Optic (FO) pole inventory application for field technicians**.

IMPORTANT ARCHITECTURE CONSTRAINT:

The application currently DOES NOT depend on any company backend server or API.

The prototype must work completely offline using local device storage.

Do NOT design the prototype around mandatory login to a server, real-time synchronization, or API connectivity.

The current workflow is:

**Create / receive local project → Select segment → Navigate to pole → Capture GPS → Enter pole information → Take photo → Review → Save locally → Export data**

The future backend integration may be added later, but it must not be required for the current prototype.

---

# PRODUCT GOAL

The application allows field technicians to perform pole tagging and inventory in remote areas where internet connectivity may be unavailable.

After completing pole tagging, technicians must be able to export collected inventory data into:

* CSV
* KML
* KMZ

The exported files should contain the pole coordinates and relevant inventory attributes.

The application should feel like a professional telecommunications field survey application.

---

# DESIGN STYLE

Use:

* Android Material 3
* Professional enterprise UI
* Telecom / network infrastructure visual language
* Blue as the primary brand color
* Neutral gray backgrounds
* Green for completed records
* Orange for warnings
* Red for conflicts/errors
* Large touch targets
* High readability outdoors
* Minimal typing
* Simple navigation
* Clear offline indicators

Do NOT make the interface look like a consumer navigation application.

Do NOT make it look like Google Maps.

Do NOT require internet access to perform field work.

---

# MAIN NAVIGATION

Bottom navigation:

1. Dashboard
2. Segments
3. Map
4. Export
5. Settings

---

# 1. DASHBOARD

Display:

“Tag Poling Inventory”

Current Project:

“Jayapura Sector 2”

Progress:

142 / 300 Poles

Progress bar.

Statistics:

BELUM
158

SELESAI
142

KONFLIK
3

LOCAL DATA
145

Show a prominent status:

● Offline Mode

“Your field data is stored safely on this device.”

Main button:

“Continue Field Work”

Secondary action:

“View Project”

---

# 2. PROJECT / SEGMENT LIST

Display project:

“Jayapura Sector 2”

Segments:

Segment 19
PTT → HOMEBASE

18 / 42 completed

Status:
“In Progress”

Segment 2212
HOMEBASE → Pelanggan A

0 / 28 completed

Status:
“Not Started”

Filters:

All
In Progress
Completed
Conflict

Search segments.

---

# 3. SEGMENT DETAIL

Header:

Segment 19

PTT → HOMEBASE

Statistics:

42 Poles
18 Completed
23 Remaining
1 Conflict

Display a map with:

* Pole markers
* Segment route
* Current location
* Completed poles
* Uncompleted poles
* Conflict poles

Marker states:

Green = Completed

Gray = Not Tagged

Red = Conflict

Blue = Current Location

Primary button:

“Start Tagging”

---

# 4. FIELD MAP

Full-screen offline map.

Top status:

OFFLINE

GPS:

Accuracy ±3.2 m

Target:

Pole P-019-019

Distance:

12 m

Display:

“Stand directly under the pole to capture accurate coordinates.”

Button:

“Tag This Pole”

The map must remain functional without internet.

---

# 5. GPS CAPTURE

Title:

“Capture Pole Location”

Instruction:

“Stand directly below the pole.”

Display:

Latitude
-2.123456

Longitude
140.123456

Accuracy
±2.8 m

GPS Status:

GPS Stable

Button:

“Capture Coordinates”

After successful capture:

✓ Coordinates Captured

Timestamp:
01 Oct 2026 — 14:32

Accuracy:
±2.8 m

Button:

“Continue”

If GPS accuracy is poor:

⚠ Accuracy too low

“Wait for a better GPS signal.”

Button:

“Retry”

---

# 6. POLE INFORMATION

Title:

“Pole Information”

Pole ID:
P-019-019

Fields:

Pole Type

* Concrete
* Steel
* Wooden
* Other

Pole Condition

* Good
* Fair
* Damaged
* Critical

Ownership

* Lintasarta
* PLN
* Telkom
* Customer
* Unknown

Pole Height

Pole Tag Number

FO Cable
Yes / No

Cable Condition

* Good
* Damaged
* Sagging
* Unknown

Additional Equipment:

☐ ODP
☐ Closure
☐ Slack
☐ Grounding
☐ Other

Notes

Button:

“Continue”

---

# 7. PHOTO CAPTURE

Instruction:

“Move approximately 5–15 meters away from the pole.”

Subtitle:

“Capture the pole and surrounding condition clearly.”

Camera interface.

Show:

* Framing guide
* Distance
* GPS status
* Flash
* Capture button

After photo:

Photo preview

Buttons:

“Retake”

“Use Photo”

---

# 8. REVIEW DATA

Title:

“Review Pole”

Show sections:

LOCATION

✓ Coordinates captured

Latitude
Longitude

GPS Accuracy

Timestamp

POLE

Concrete

Good condition

Ownership:
Lintasarta

FO CABLE

Present

Good condition

PHOTO

✓ Photo attached

Display photo thumbnail.

Button:

“SAVE POLE”

---

# 9. SAVE SUCCESS

Display:

✓ Pole Successfully Tagged

P-019-019

“Data has been saved locally on this device.”

Status:

LOCAL ONLY

Actions:

“Tag Next Pole”

“View Pole”

“Back to Segment”

Do NOT show a required synchronization action.

---

# 10. POLE INVENTORY LIST

Create a local inventory list.

Example:

P-019-001
✓ Completed

P-019-002
✓ Completed

P-019-003
⚠ Conflict

P-019-004
Not Tagged

Show:

Total
42

Completed
18

Conflict
1

Local Records
18

Allow sorting and filtering.

---

# 11. EXPORT CENTER

This is a major feature.

Title:

“Export Inventory”

Project:

Jayapura Sector 2

Segment:

Segment 19 — PTT → HOMEBASE

Summary:

42 Total Poles

18 Completed

17 Valid

1 Conflict

18 Local Records

Show three export cards:

### CSV

Icon:
Spreadsheet

Description:

“Export pole inventory attributes and coordinates.”

Button:

“Export CSV”

---

### KML

Icon:
Map / Earth

Description:

“Export pole locations and attributes as KML.”

Button:

“Export KML”

---

### KMZ

Icon:
Package / Map

Description:

“Export compressed KML package with photos and metadata.”

Button:

“Export KMZ”

---

# 12. EXPORT OPTIONS

When selecting Export:

Show:

Export Scope

○ Current Segment

○ Current Project

○ All Local Data

Data Status:

☑ Completed

☐ Conflict

☐ Incomplete

Include:

☑ Coordinates

☑ Pole Attributes

☑ Timestamp

☑ Notes

☑ Photos

For KML/KMZ:

☑ Include photo references

Button:

“Generate Export”

---

# 13. EXPORT PROGRESS

Display:

“Preparing Export…”

Processing:

18 / 18 records

Generate coordinates

Generate attributes

Package photos

Create file

Progress bar.

After completion:

✓ Export Completed

File:

Jayapura_Sector2_Segment19_2026-10-01.kmz

Size:

8.4 MB

Actions:

“Share File”

“Open Folder”

“Export Again”

---

# 14. FILE MANAGEMENT

Create an export history page.

Example:

Jayapura_Sector2_All_2026-10-01.csv
245 KB

Jayapura_Sector2_Segment19_2026-10-01.kml
182 KB

Jayapura_Sector2_Segment19_2026-10-01.kmz
8.4 MB

Each file has:

* File type
* Date
* Number of records
* File size

Actions:

Share

Rename

Delete

Open

---

# 15. SETTINGS

Display:

Project Information

Storage

Export Directory

Photo Quality

Map Settings

GPS Settings

Data Management

* Backup Local Data
* Restore Local Data
* Clear Completed Data
* Clear All Local Data

Application:

Version 1.0.0

---

# IMPORTANT DATA ARCHITECTURE CONCEPT

The prototype should visually communicate this architecture:

FIELD DATA

↓

LOCAL DATABASE

↓

LOCAL INVENTORY

↓

EXPORT ENGINE

↓

CSV / KML / KMZ

The application should NOT depend on a remote server for the current prototype.

Future architecture may add:

LOCAL DATABASE

↓

SYNC ENGINE

↓

COMPANY API / SERVER

But this should be represented only as a future capability, not as a required workflow.

---

# KML / KMZ REQUIREMENT

KML and KMZ exports should represent each pole as a geographic Placemark.

Each Placemark should contain:

Pole ID

Segment

Latitude

Longitude

GPS Accuracy

Pole Type

Pole Condition

Ownership

Pole Height

FO Cable Status

Additional Equipment

Notes

Timestamp

Photo reference when available.

The KML should allow the exported pole data to be visualized in geographic applications such as Google Earth or GIS software.

KMZ should package the KML together with related local assets such as photos when the user chooses to include them.

---

# USER FLOW

Main workflow:

Dashboard
↓
Project
↓
Segment
↓
Map
↓
Target Pole
↓
GPS Capture
↓
Pole Information
↓
Photo
↓
Review
↓
Save Locally
↓
Tag Next Pole

Export workflow:

Dashboard
↓
Export
↓
Select Project / Segment
↓
Select Format
↓
Export Options
↓
Generate
↓
Export Completed
↓
Share / Open File

---

# UX PRINCIPLES

The technician must be able to perform the entire field workflow without internet.

The app must clearly show:

OFFLINE

GPS STATUS

LOCAL SAVE STATUS

EXPORT STATUS

Never lose locally captured information.

If the application is closed during field work, data must remain available when reopened.

Use confirmation dialogs before deleting local records.

Make “Save Locally” the most important action after completing a pole.

The prototype should emphasize reliability over visual complexity.

---

# FINAL PROTOTYPE OUTPUT

Generate 14–16 high-fidelity Android screens.

Include clickable interactions for:

Dashboard → Segment → Map → GPS → Pole Form → Camera → Review → Save

and:

Dashboard → Export → Format Selection → Options → Generate → Export Complete

Create realistic field data and realistic Indonesian telecom infrastructure examples.

The final result should look like a production-ready internal telecom field inventory application that can operate completely offline and produce professional CSV, KML, and KMZ exports.
