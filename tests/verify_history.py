"""Exercise the exact schema shipped to Android using SQLite's real conflict handling."""
import json,pathlib,sqlite3
root=pathlib.Path(__file__).resolve().parents[1]
db=sqlite3.connect(':memory:');db.executescript((root/'app/src/main/assets/history-schema.sql').read_text())
def capture(account,rotation,items):
    db.execute('INSERT OR IGNORE INTO rotations VALUES (?,?,?,?,?)',(account,rotation,rotation*3600000,rotation*3600000+86400000,json.dumps(items)))
capture('account-a',100,['reaver','prime']);capture('account-a',100,['reaver','prime'])
assert db.execute('SELECT COUNT(*) FROM rotations').fetchone()[0]==1,'Refresh duplicates must not inflate history'
capture('account-b',100,['ion']);capture('account-a',124,['reaver','ion'])
assert db.execute('SELECT COUNT(*) FROM rotations WHERE account=?',('account-a',)).fetchone()[0]==2
assert db.execute('SELECT COUNT(*) FROM rotations WHERE account=?',('account-b',)).fetchone()[0]==1
seen={}
for (payload,) in db.execute('SELECT payload FROM rotations WHERE account=?',('account-a',)):
    for item in json.loads(payload):seen[item]=seen.get(item,0)+1
assert seen=={'reaver':2,'prime':1,'ion':1},'Appearance counts must be account-scoped and rotation-deduplicated'
print('SQLite schema, duplicate capture, account separation and appearance-count checks passed.')
