export type ZoneType = 'HOME' | 'TRAIN_STATION' | 'OFFICE' | 'OTHER';

export interface ZoneResponse {
  id: string;
  name: string;
  type: ZoneType;
  externalId: string;
  latitude: number | null;
  longitude: number | null;
  radiusMeters: number | null;
  active: boolean;
  createdAt: string;
}

export interface CreateZoneRequest {
  name: string;
  type: ZoneType;
  externalId: string;
  latitude?: number;
  longitude?: number;
  radiusMeters?: number;
}

export interface UpdateZoneRequest {
  name?: string;
  type?: ZoneType;
  externalId?: string;
  latitude?: number;
  longitude?: number;
  radiusMeters?: number;
}
