export interface UserResponse {
  id: string;
  email: string;
  displayName: string;
  requiredDaysPerWeek: number;
  role: string;
  active: boolean;
  timezone: string;
  commuteAnomalyThresholdMinutes: number;
  createdAt: string;
}

export interface UpdateUserRequest {
  displayName?: string;
  requiredDaysPerWeek?: number;
  timezone?: string;
  commuteAnomalyThresholdMinutes?: number;
}

export interface ApiKeyResponse {
  apiKey: string;
  message: string;
}
