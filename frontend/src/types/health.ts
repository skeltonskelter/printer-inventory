export interface HealthResponse {
  status: 'UP' | 'DOWN';
  api: 'UP' | 'DOWN';
  database: 'UP' | 'DOWN';
  message: string;
  checkedAt: string;
}
