"""Verify additive history migrations against the exported Room schemas."""
import json
import re
import sqlite3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
schema_dir = root / "app/schemas/com.fongmi.android.tv.db.AppDatabase"
schema39 = json.loads((schema_dir / "39.json").read_text(encoding="utf-8"))["database"]
schema40 = json.loads((schema_dir / "40.json").read_text(encoding="utf-8"))["database"]
schema41 = json.loads((schema_dir / "41.json").read_text(encoding="utf-8"))["database"]
connection = sqlite3.connect(":memory:")
for entity in schema39["entities"]:
    connection.execute(entity["createSql"].replace("$" + "{TABLE_NAME}", entity["tableName"]))
columns = [row[1] for row in connection.execute("PRAGMA table_info(History)")]
for account, key, episode, position in [
    ("default", "source-a@@@100@@@1", 18, 265976),
    ("default", "source-b@@@200@@@2", 1, 8234),
    ("another-account", "source-a@@@100@@@1", 7, 360000),
]:
    values = {column: None for column in columns}
    for row in connection.execute("PRAGMA table_info(History)"):
        if row[3]:
            values[row[1]] = 0
    values.update(accountId=account, key=key, vodName="同一作品", vodYear="2026",
                  vodType="电视剧", episodeNumber=episode, episodeCount=30,
                  vodRemarks=f"第{episode}集", position=position, duration=2400000,
                  createTime=1791117000000, speed=1.0, cid=1)
    connection.execute(f"INSERT INTO History ({','.join(columns)}) VALUES ({','.join('?' for _ in columns)})",
                       [values[column] for column in columns])
before = list(connection.execute("SELECT * FROM History ORDER BY accountId,key"))
source = (root / "app/src/main/java/com/fongmi/android/tv/db/Migrations.java").read_text(encoding="utf-8")
block = source.split("MIGRATION_39_40", 1)[1].split("};", 1)[0]
statements = re.findall(r'database\.execSQL\("([^"]+)"\)', block)
assert len(statements) == 2, "Expected only two additive history columns"
for statement in statements:
    connection.execute(statement)
after = list(connection.execute(f"SELECT {','.join(columns)} FROM History ORDER BY accountId,key"))
assert before == after, "Existing episode, position, account or key changed"
expected = next(entity for entity in schema40["entities"] if entity["tableName"] == "History")
assert {row[1] for row in connection.execute("PRAGMA table_info(History)")} == {
    field["columnName"] for field in expected["fields"]
}, "Migrated columns differ from Room 40"

columns40 = [row[1] for row in connection.execute("PRAGMA table_info(History)")]
before40 = list(connection.execute(f"SELECT {','.join(columns40)} FROM History ORDER BY accountId,key"))
block = source.split("MIGRATION_40_41", 1)[1].split("};", 1)[0]
statements = re.findall(r'database\.execSQL\("([^"]+)"\)', block)
assert statements == ["ALTER TABLE History ADD COLUMN qualityHeight INTEGER NOT NULL DEFAULT 0"], \
    "40 -> 41 must only add the quality preference column"
for statement in statements:
    connection.execute(statement)
after40 = list(connection.execute(f"SELECT {','.join(columns40)} FROM History ORDER BY accountId,key"))
assert before40 == after40, "40 -> 41 changed existing history data"
assert connection.execute("SELECT COUNT(*) FROM History WHERE qualityHeight != 0").fetchone()[0] == 0, \
    "Legacy rows must start in automatic quality mode"
connection.execute("UPDATE History SET qualityHeight = 1080 WHERE accountId = ? AND `key` = ?",
                   ("default", "source-a@@@100@@@1"))
assert connection.execute("SELECT qualityHeight FROM History WHERE accountId = ? AND `key` = ?",
                          ("default", "source-a@@@100@@@1")).fetchone()[0] == 1080
expected = next(entity for entity in schema41["entities"] if entity["tableName"] == "History")
assert {row[1] for row in connection.execute("PRAGMA table_info(History)")} == {
    field["columnName"] for field in expected["fields"]
}, "Migrated columns differ from Room 41"
assert connection.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
print("History migration checks passed: 39 -> 40 -> 41; 3 legacy rows preserved and quality defaults to Auto")
