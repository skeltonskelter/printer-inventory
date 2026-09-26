export type UserRole = 'USER' | 'ADMIN';

export interface CurrentUser {
  username: string;
  fullName: string;
  role: UserRole;
}
