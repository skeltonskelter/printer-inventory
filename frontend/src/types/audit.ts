import type { Page } from './inventory';

export const auditActions = ['CREATE', 'UPDATE', 'DELETE', 'RELOCATE', 'IMPORT', 'LOGIN', 'LOGOUT',
  'USER_CREATE', 'USER_UPDATE', 'USER_ENABLE', 'USER_DISABLE', 'PASSWORD_RESET', 'ROLE_CHANGE'] as const;
export const auditEntityTypes = ['PRINTER', 'LOCATION', 'RELOCATION', 'USER', 'CSV_IMPORT', 'AUTHENTICATION'] as const;
export type AuditAction = typeof auditActions[number];
export type AuditEntityType = typeof auditEntityTypes[number];
export interface AuditLog {
  id: number; timestamp: string; username: string; userId: number | null; userRole: string;
  action: AuditAction; entityType: AuditEntityType; entityId: string | null;
  entityIdentifier: string | null; description: string;
  oldValues: Record<string, unknown>; newValues: Record<string, unknown>;
}
export type AuditPage = Page<AuditLog>;
