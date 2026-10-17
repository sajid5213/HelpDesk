-- RSA signing keys for access tokens. Private keys are AES-GCM encrypted with HELPDESK_KEK.
create table signing_keys (
  kid text primary key,
  public_jwk text not null,
  private_key_encrypted text not null,
  status text not null check (status in ('ACTIVE', 'RETIRED')),
  created_at timestamptz not null,
  retired_at timestamptz
);

-- At most one ACTIVE key at any time.
create unique index signing_keys_one_active on signing_keys ((status)) where status = 'ACTIVE';
