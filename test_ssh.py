import paramiko, os

key = paramiko.RSAKey.from_private_key_file(r'C:\Users\shiva\Downloads\ppkv2.ppk')
print(f"Key loaded: {key.get_bits()} bits")

# Test connection
client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect('68.233.112.30', username='opc', pkey=key, timeout=10)
print("Connected!")
stdin, stdout, stderr = client.exec_command('hostname')
print(f"Hostname: {stdout.read().decode().strip()}")
client.close()
