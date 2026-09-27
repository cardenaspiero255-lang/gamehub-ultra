export type OAuthCredentialSet = {
  accessToken: string;
  refreshToken: string;
  expiresAt?: string | null;
  userId?: string | null;
};

export type CredentialWriter = (
  name: string,
  secret: string,
  description: string,
) => Promise<void>;

export type CredentialTransactionRunner = (
  action: (write: CredentialWriter) => Promise<void>,
) => Promise<void>;

export async function persistCredentialSetAtomically(
  runTransaction: CredentialTransactionRunner,
  credentials: OAuthCredentialSet,
): Promise<void> {
  await runTransaction(async (write) => {
    await write(
      "mercadolibre_access_token",
      credentials.accessToken,
      "Access Token OAuth de Mercado Libre para GameHub Ultra",
    );
    await write(
      "mercadolibre_refresh_token",
      credentials.refreshToken,
      "Refresh Token OAuth de Mercado Libre para GameHub Ultra",
    );
    if (credentials.expiresAt) {
      await write(
        "mercadolibre_access_expires_at",
        credentials.expiresAt,
        "Expiración estimada del Access Token de Mercado Libre",
      );
    }
    if (credentials.userId) {
      await write(
        "mercadolibre_user_id",
        credentials.userId,
        "ID de usuario autorizado de Mercado Libre",
      );
    }
  });
}
