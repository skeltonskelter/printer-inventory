export type UserRole = 'USER' | 'ADMIN';

export interface CurrentUser {
  username: string;
  fullName: string;
  role: UserRole;
}

export interface ManagedUser {
  id: number;
  fullName: string;
  username: string;
  role: UserRole;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface UserProfileInput {
  fullName: string;
  username: string;
  role: UserRole;
  enabled: boolean;
}

export interface CreateUserInput extends UserProfileInput {
  password: string;
  confirmPassword: string;
}
