import sqlite3
conn = sqlite3.connect('/app/legacy_main.db')
print('Main site users:')
for row in conn.execute('SELECT id, email, first_name, last_name FROM users'):
    print(f'  id={row[0]}, email={row[1]}, first_name={row[2]}, last_name={row[3]}')
conn.close()
