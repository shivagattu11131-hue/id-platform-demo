import paramiko, os, base64, struct

# Parse PPK3 manually
with open(r'C:\Users\shiva\Downloads\ppkv2.ppk', 'r') as f:
    content = f.read()

lines = content.strip().split('\n')
def get_field(name):
    for l in lines:
        if l.startswith(name + ':'):
            return l.split(':', 1)[1].strip()
    return None

enc = get_field('Encryption')
print(f"Encryption: {enc}")

# Parse public blob
pub_idx = next(i for i, l in enumerate(lines) if l.startswith('Public-Lines'))
pub_count = int(get_field('Public-Lines'))
pub_b64 = ''.join(l.strip() for l in lines[pub_idx+1:pub_idx+1+pub_count])
pub_blob = base64.b64decode(pub_b64)
print(f"Public blob: {len(pub_blob)} bytes")

# Parse private blob  
priv_idx = next(i for i, l in enumerate(lines) if l.startswith('Private-Lines'))
priv_count = int(get_field('Private-Lines'))
priv_b64 = ''.join(l.strip() for l in lines[priv_idx+1:priv_idx+1+priv_count])
priv_blob = base64.b64decode(priv_b64)
print(f"Private blob: {len(priv_blob)} bytes")
print(f"First 8 bytes: {priv_blob[:8].hex()}")

# For PPK3, the private blob has:
# - 4 bytes: padding length (ignored)
# - padding bytes
# - The private key data (same as OpenSSH wire format without the check bytes at end)
# - 20 bytes HMAC-SHA1 MAC (we need to ignore this)

# Actually let's look at what paramiko expects for PPK3
# The private blob (after removing padding) should contain:
# - string key_type
# - mpint n, e, d, iqmp, p, q (for RSA)

# Remove MAC (last 20 bytes) if present
mac_hex = get_field('Private-MAC')
print(f"Private-MAC: {mac_hex}")

# For unencrypted PPK3, the private blob doesn't have padding in newer format
# Let's try reading directly as key components

def read_str(d, o):
    l = struct.unpack('>I', d[o:o+4])[0]
    return d[o+4:o+4+l], o+4+l

def read_mpint(d, o):
    l = struct.unpack('>I', d[o:o+4])[0]
    return int.from_bytes(d[o+4:o+4+l], 'big'), o+4+l

# Try direct read of private blob
o = 0
try:
    kt, o = read_str(priv_blob, o)
    print(f"Key type from private: {kt}")
    
    n, o = read_mpint(priv_blob, o)
    e, o = read_mpint(priv_blob, o)
    d, o = read_mpint(priv_blob, o)
    iqmp, o = read_mpint(priv_blob, o)
    p, o = read_mpint(priv_blob, o)
    q, o = read_mpint(priv_blob, o)
    
    print(f"Successfully parsed: n={n.bit_length()}bits, e={e.bit_length()}bits, d={d.bit_length()}bits")
    print(f"Consumed {o} of {len(priv_blob)} bytes")
    
    from cryptography.hazmat.primitives.asymmetric.rsa import RSAPrivateNumbers, RSAPublicNumbers
    from cryptography.hazmat.primitives.serialization import Encoding, PrivateFormat, NoEncryption
    from cryptography.hazmat.backends import default_backend
    
    priv_nums = RSAPrivateNumbers(
        p=p, q=q, d=d,
        dmp1=d % (p - 1), dmq1=d % (q - 1), iqmp=iqmp,
        public_numbers=RSAPublicNumbers(e=e, n=n)
    )
    rsa_key = priv_nums.private_key(default_backend())
    
    # Write as OpenSSH format
    pem = rsa_key.private_bytes(Encoding.PEM, PrivateFormat.OpenSSH, NoEncryption())
    out_path = r'C:\Users\shiva\Downloads\ppkv2_openssh'
    with open(out_path, 'wb') as f:
        f.write(pem)
    os.chmod(out_path, 0o600)
    print(f"Wrote OpenSSH key to {out_path}")
    
except Exception as ex:
    print(f"Direct parse failed: {ex}")
    
    # Maybe PPK3 has a different structure for the private data
    # Try: just the 4 private mpints (d, p, q, iqmp) without key type
    o = 0
    try:
        d_val, o = read_mpint(priv_blob, o)
        p_val, o = read_mpint(priv_blob, o)
        q_val, o = read_mpint(priv_blob, o)
        iqmp_val, o = read_mpint(priv_blob, o)
        print(f"4-mpint parse: d={d_val.bit_length()}bits, p={p_val.bit_length()}bits")
        print(f"Consumed {o} of {len(priv_blob)} bytes")
        
        # Get n and e from public blob
        o2 = 0
        _, o2 = read_str(pub_blob, o2)  # skip key type
        e_pub, o2 = read_mpint(pub_blob, o2)
        n_pub, o2 = read_mpint(pub_blob, o2)
        
        from cryptography.hazmat.primitives.asymmetric.rsa import RSAPrivateNumbers, RSAPublicNumbers
        from cryptography.hazmat.primitives.serialization import Encoding, PrivateFormat, NoEncryption
        from cryptography.hazmat.backends import default_backend
        
        priv_nums = RSAPrivateNumbers(
            p=p_val, q=q_val, d=d_val,
            dmp1=d_val % (p_val - 1), dmq1=d_val % (q_val - 1), iqmp=iqmp_val,
            public_numbers=RSAPublicNumbers(e=e_pub, n=n_pub)
        )
        rsa_key = priv_nums.private_key(default_backend())
        
        pem = rsa_key.private_bytes(Encoding.PEM, PrivateFormat.OpenSSH, NoEncryption())
        out_path = r'C:\Users\shiva\Downloads\ppkv2_openssh'
        with open(out_path, 'wb') as f:
            f.write(pem)
        os.chmod(out_path, 0o600)
        print(f"Wrote OpenSSH key to {out_path}")
    except Exception as ex2:
        print(f"4-mpint parse also failed: {ex2}")
        # Show remaining data
        print(f"Remaining at offset {o}: {priv_blob[o:o+20].hex()}")
