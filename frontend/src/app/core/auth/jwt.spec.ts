import { decodeJwtPayload } from './jwt';

function base64url(json: object): string {
  return btoa(JSON.stringify(json)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

describe('decodeJwtPayload', () => {
  it('lit les claims, y compris les caractères accentués', () => {
    const payload = { sub: 'abc', preferred_username: 'modo', realm_access: { roles: ['MODERATEUR', 'USER'] } };
    const utf8 = { ...payload, preferred_username: 'Mona Dérateur' };
    const encoded = btoa(String.fromCharCode(...new TextEncoder().encode(JSON.stringify(utf8))))
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '');

    expect(decodeJwtPayload(`h.${base64url(payload)}.s`)?.realm_access?.roles).toEqual(['MODERATEUR', 'USER']);
    expect(decodeJwtPayload(`h.${encoded}.s`)?.preferred_username).toBe('Mona Dérateur');
  });

  it('renvoie null pour un token mal formé', () => {
    expect(decodeJwtPayload('pas-un-jwt')).toBeNull();
    expect(decodeJwtPayload('a.%%%.c')).toBeNull();
  });
});
